package com.carlosgt.atak.icung.plugin;

import android.content.Context;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SizeF;

import java.util.Arrays;

import com.atakmap.android.maps.MapView;
import com.atakmap.coremap.maps.conversion.EGM96;
import com.atakmap.coremap.maps.coords.GeoPoint;
import com.atakmap.map.elevation.ElevationManager;
import com.pedro.rtsp.rtsp.klv.KlvMetadata;

/**
 * Feeds MISB ST 0601 KLV to the RTSP client while streaming: where the
 * phone is (ATAK's self marker) and where its camera points (rotation
 * vector sensor), at 1 Hz.
 *
 * The pointing comes from the device attitude, not the GPS track: a person
 * filming while standing still, or walking one way and filming another, is
 * the normal case for a handheld camera. The back camera looks along the
 * device's -Z axis; that vector rotated into the world frame gives azimuth
 * and elevation in any phone orientation. The front camera looks the other
 * way. Not used for USB cameras (see StreamController.startKlv).
 *
 * The same rotation also gives the image's right and up directions, and
 * from them the frame's four corners on the ground (see Footprint), which
 * a receiving ATAK needs to drape the video on its map. Which device axes
 * those are depends on how the stream was laid out: RootEncoder fixes the
 * picture's orientation when the encoder is prepared, from the screen
 * rotation then, and does not follow the phone afterwards.
 *
 * When the ICU NG Capture app streams, the packets are handed to it
 * instead (see {@link PacketSink}): the phone's position and attitude are
 * still worked out here, where ATAK's self marker and elevation data are.
 *
 * Sensor callbacks run on the main thread (where the self marker may be
 * read); nextPacket() runs on RootEncoder's sender thread and only reads
 * the volatile snapshot.
 */
public class KlvTelemetry implements SensorEventListener, KlvMetadata.KlvSupplier {

    private static final String TAG = "IcuNgKlv";

    /** Receives each packet, once per second, on the main thread. */
    public interface PacketSink {
        void onKlvPacket(byte[] packet);
    }

    /**
     * Where the camera points, once per second on the main thread, for the
     * map's sensor cone: azimuth in degrees true, horizontal field of view
     * in degrees, and the ground distance to the frame center in meters.
     */
    public interface PointingListener {
        void onPointing(double azimuthTrueDeg, double hfovDeg, double groundRangeM);
    }

    private static final long INTERVAL_MS = 1000;
    private static final double DEFAULT_HFOV = 60;
    private static final double DEFAULT_VFOV = 45;
    // Camera height above the ground when terrain data can't tell: a phone
    // held at chest height.
    private static final double HANDHELD_HEIGHT_M = 1.5;
    // Farthest the frame center is placed, also used when the camera looks
    // at or above the horizon (its axis never meets the ground then).
    private static final double MAX_FRAME_CENTER_M = 500;

    private final Context atakContext;
    private final String sensorName;
    private final boolean frontCamera;
    private final double hfovDeg;
    private final double vfovDeg;
    // Image right and up in the device frame (x, y, z): +1/-1 on one axis.
    private final int[] imageRight;
    private final int[] imageUp;

    private SensorManager sensorManager;
    private final float[] rotation = new float[9];
    private GeomagneticField geoField;
    private long geoFieldTime;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private double magneticAzimuth = Double.NaN;
    private double elevation = Double.NaN;
    private double roll = Double.NaN;
    private final byte[] miisCoreId;
    // Camera axis, image right and image up in the world frame (east,
    // north, up; magnetic north); null until the first sensor event.
    private double[] worldAxis;
    private double[] worldRight;
    private double[] worldUp;
    private boolean cornersSent;
    private volatile KlvEncoder.Telemetry snapshot;
    private long lastSentMs;
    private PacketSink sink;
    private PointingListener pointingListener;
    private double groundRange = Double.NaN;
    private int classification = KlvEncoder.UNCLASSIFIED;
    private String country;
    private String missionId;

    // Position is sampled on this tick (main thread) rather than from the
    // sensor callback, so KLV keeps flowing on devices with no attitude
    // sensor at all.
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateSnapshot();
            KlvEncoder.Telemetry t = snapshot;
            if (sink != null && t != null) {
                t.timeMicros = System.currentTimeMillis() * 1000L;
                sink.onKlvPacket(KlvEncoder.encode(t));
            }
            if (pointingListener != null && t != null && !Double.isNaN(t.azimuthDeg)
                    && !Double.isNaN(groundRange)) {
                pointingListener.onPointing(t.azimuthDeg, hfovDeg, groundRange);
            }
            mainHandler.postDelayed(this, INTERVAL_MS);
        }
    };

    /**
     * @param streamRotation the rotation the encoder was prepared with
     *        (RootEncoder's CameraHelper.getCameraOrientation: 90 portrait,
     *        0 landscape, 270 and 180 the same upside down)
     */
    public KlvTelemetry(Context atakContext, int camera, String sensorName, int streamRotation) {
        this.atakContext = atakContext;
        this.sensorName = sensorName;
        this.frontCamera = camera == StreamSettings.CAMERA_FRONT;
        boolean portrait = streamRotation == 90 || streamRotation == 270;
        double[] fov = cameraFov(atakContext, frontCamera);
        double longFov = fov != null ? fov[0] : DEFAULT_HFOV;
        double shortFov = fov != null ? fov[1] : DEFAULT_VFOV;
        // A portrait stream has the sensor's long side vertical.
        this.hfovDeg = portrait ? shortFov : longFov;
        this.vfovDeg = portrait ? longFov : shortFov;
        // Device axes: x to the right and y to the top of the phone held
        // upright, z out of the screen. Turned counter-clockwise into
        // landscape (rotation 0), the phone's right edge is the image's top.
        switch (streamRotation) {
            case 0:
                imageUp = new int[] { 1, 0, 0 };
                imageRight = new int[] { 0, -1, 0 };
                break;
            case 270:
                imageUp = new int[] { 0, -1, 0 };
                imageRight = new int[] { -1, 0, 0 };
                break;
            case 180:
                imageUp = new int[] { -1, 0, 0 };
                imageRight = new int[] { 0, 1, 0 };
                break;
            default:
                imageUp = new int[] { 0, 1, 0 };
                imageRight = new int[] { 1, 0, 0 };
                break;
        }
        // The front camera faces the other way, so its picture (not
        // mirrored in the stream) has right and left swapped.
        if (frontCamera) {
            for (int i = 0; i < 3; i++) imageRight[i] = -imageRight[i];
        }
        this.miisCoreId = KlvEncoder.miisCoreId(
                MapView.getMapView().getSelfMarker().getUID(), sensorName);
    }

    /** STANAG 4609 marking carried in every packet (ST 0102 set and mission). */
    public void setMarking(int classification, String country, String missionId) {
        this.classification = classification;
        this.country = country;
        this.missionId = missionId;
    }

    /** Set before start(). */
    public void setPointingListener(PointingListener listener) {
        this.pointingListener = listener;
    }

    /** Packets go to the sink instead of this process's RTSP client. */
    public void start(PacketSink sink) {
        this.sink = sink;
        start();
    }

    public void start() {
        sensorManager = (SensorManager) atakContext.getSystemService(Context.SENSOR_SERVICE);
        Sensor sensor = sensorManager == null ? null
                : sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI);
        } else {
            Log.w(TAG, "no rotation vector sensor: KLV without camera pointing");
            sensorManager = null;
        }
        mainHandler.post(tick);
        if (sink == null) KlvMetadata.setSupplier(this);
    }

    public void stop() {
        if (KlvMetadata.getSupplier() == this) KlvMetadata.setSupplier(null);
        mainHandler.removeCallbacks(tick);
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
            sensorManager = null;
        }
        snapshot = null;
    }

    @Override
    public byte[] nextPacket() {
        long now = System.currentTimeMillis();
        KlvEncoder.Telemetry t = snapshot;
        if (t == null || now - lastSentMs < INTERVAL_MS) return null;
        lastSentMs = now;
        t.timeMicros = now * 1000L;
        return KlvEncoder.encode(t);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        SensorManager.getRotationMatrixFromVector(rotation, event.values);
        // World frame: x east, y north, z up. Camera axis in device frame
        // is (0,0,-1) for the back camera, (0,0,1) for the front one, so
        // its world vector is minus (or plus) the third column.
        double sign = frontCamera ? 1 : -1;
        double east = sign * rotation[2];
        double north = sign * rotation[5];
        double up = sign * rotation[8];
        magneticAzimuth = Math.toDegrees(Math.atan2(east, north));
        elevation = Math.toDegrees(Math.asin(Math.max(-1, Math.min(1, up))));
        worldAxis = new double[] { east, north, up };
        worldRight = toWorld(imageRight);
        worldUp = toWorld(imageUp);
        // Twist of the picture about the camera axis: 0 with the image's
        // right edge level, positive turning clockwise seen from behind
        // (the right edge dips). Undefined looking straight up or down.
        double rz = worldRight[2];
        double uz = worldUp[2];
        roll = Math.hypot(rz, uz) < 0.1 ? Double.NaN : Math.toDegrees(Math.atan2(-rz, uz));
    }

    // Row-major rotation matrix: a device axis in the world frame is the
    // matching column (sum, since only one component is non-zero).
    private double[] toWorld(int[] device) {
        double[] w = new double[3];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                w[row] += rotation[3 * row + col] * device[col];
            }
        }
        return w;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void updateSnapshot() {
        GeoPoint p = MapView.getMapView().getSelfMarker().getPoint();
        if (p == null || !p.isValid()) {
            // No fix: no position to report, so no KLV at all - a packet
            // claiming 0,0 would put the video in the Gulf of Guinea.
            snapshot = null;
            return;
        }
        KlvEncoder.Telemetry t = new KlvEncoder.Telemetry();
        t.latitude = p.getLatitude();
        t.longitude = p.getLongitude();
        t.altitudeMsl = p.isAltitudeValid() ? EGM96.getMSL(p) : Double.NaN;
        t.azimuthDeg = Double.isNaN(magneticAzimuth) ? Double.NaN
                : magneticAzimuth + declination(p);
        t.elevationDeg = elevation;
        t.hfovDeg = hfovDeg;
        t.vfovDeg = vfovDeg;
        t.callsign = MapView.getMapView().getDeviceCallsign();
        t.sensorName = sensorName;
        t.classification = classification;
        t.country = country;
        t.missionId = missionId;
        t.rollDeg = roll;
        t.miisCoreId = miisCoreId;
        groundRange = Double.NaN;
        if (!Double.isNaN(t.azimuthDeg) && !Double.isNaN(elevation)) {
            double height = cameraHeight(p);
            groundRange = frameCenter(t, p, elevation, height);
            t.targetWidthM = 2 * t.slantRange * Math.tan(Math.toRadians(hfovDeg / 2));
            if (worldAxis != null) {
                corners(t, height, t.azimuthDeg - magneticAzimuth);
            }
        }
        snapshot = t;
    }

    /**
     * The frame's corners on the same flat ground as the frame center.
     * None when any corner ray misses it within MAX_FRAME_CENTER_M - the
     * camera held level, for one: then there is nothing to drape.
     *
     * ATAK (VideoMetadata.update, 5.8) keeps the last corners it got and
     * moves them with the frame center while packets carry none, so just
     * leaving them out would keep a stale drape on the receivers' maps.
     * Once corners have been sent, a lost footprint is sent as all four
     * on the frame center instead: a quad with no area, nothing drawn.
     */
    private void corners(KlvEncoder.Telemetry t, double height, double declination) {
        double[][] c = Footprint.corners(worldAxis, worldRight, worldUp,
                hfovDeg, vfovDeg, height, MAX_FRAME_CENTER_M);
        if (c == null) {
            if (cornersSent) {
                t.cornerLat = new double[4];
                t.cornerLon = new double[4];
                Arrays.fill(t.cornerLat, t.frameCenterLat);
                Arrays.fill(t.cornerLon, t.frameCenterLon);
            }
            return;
        }
        cornersSent = true;
        t.cornerLat = new double[4];
        t.cornerLon = new double[4];
        for (int i = 0; i < 4; i++) {
            double[] ll = KlvEncoder.destination(t.latitude, t.longitude,
                    c[i][0] + declination, c[i][1]);
            t.cornerLat[i] = ll[0];
            t.cornerLon[i] = ll[1];
        }
    }

    // The camera's height over the terrain: ATAK's elevation data, or a
    // handheld default.
    private static double cameraHeight(GeoPoint p) {
        double groundHae = ElevationManager.getElevation(p.getLatitude(), p.getLongitude(), null);
        double height = HANDHELD_HEIGHT_M;
        if (p.isAltitudeValid() && !Double.isNaN(groundHae)) {
            double h = p.getAltitude() - groundHae;
            // GPS altitude is noisy; only trust a plausible height.
            if (h > 0.5 && h < 3000) height = h;
        }
        return height;
    }

    /**
     * Where the camera axis meets the ground: from the camera's height over
     * the terrain and how far below the horizon it points. ATAK shows this
     * as the video's target. Returns its ground distance from the camera.
     */
    private static double frameCenter(KlvEncoder.Telemetry t, GeoPoint p, double elevationDeg,
            double height) {
        double distance = elevationDeg < -0.5
                ? Math.min(height / Math.tan(Math.toRadians(-elevationDeg)), MAX_FRAME_CENTER_M)
                : MAX_FRAME_CENTER_M;
        double[] fc = KlvEncoder.destination(t.latitude, t.longitude, t.azimuthDeg, distance);
        t.frameCenterLat = fc[0];
        t.frameCenterLon = fc[1];
        t.slantRange = Math.hypot(distance, height);
        double fcGroundHae = ElevationManager.getElevation(fc[0], fc[1], null);
        if (!Double.isNaN(fcGroundHae)) {
            t.frameCenterMsl = EGM96.getMSL(fc[0], fc[1], fcGroundHae);
        } else if (!Double.isNaN(t.altitudeMsl)) {
            t.frameCenterMsl = t.altitudeMsl - height;
        }
        return distance;
    }

    // Magnetic -> true north. Recomputed every 10 min; it barely moves.
    private double declination(GeoPoint p) {
        long now = System.currentTimeMillis();
        if (geoField == null || now - geoFieldTime > 10 * 60 * 1000) {
            geoField = new GeomagneticField((float) p.getLatitude(), (float) p.getLongitude(),
                    p.isAltitudeValid() ? (float) p.getAltitude() : 0f, now);
            geoFieldTime = now;
        }
        return geoField.getDeclination();
    }

    /**
     * Field of view of the phone's own camera from its optics:
     * fov = 2 * atan(sensorSize / (2 * focalLength)). Returns {hfov, vfov}
     * with the long sensor side as horizontal, or null if unavailable.
     */
    private static double[] cameraFov(Context ctx, boolean front) {
        try {
            CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            int wanted = front ? CameraCharacteristics.LENS_FACING_FRONT
                    : CameraCharacteristics.LENS_FACING_BACK;
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics c = cm.getCameraCharacteristics(id);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (facing == null || facing != wanted) continue;
                SizeF size = c.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE);
                float[] focals = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                if (size == null || focals == null || focals.length == 0) return null;
                double f = focals[0];
                double w = Math.max(size.getWidth(), size.getHeight());
                double h = Math.min(size.getWidth(), size.getHeight());
                return new double[] {
                        Math.toDegrees(2 * Math.atan(w / (2 * f))),
                        Math.toDegrees(2 * Math.atan(h / (2 * f)))
                };
            }
        } catch (Exception e) {
            Log.w(TAG, "camera FOV unavailable", e);
        }
        return null;
    }
}
