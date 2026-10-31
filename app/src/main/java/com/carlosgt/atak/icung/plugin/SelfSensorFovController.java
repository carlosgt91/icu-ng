package com.carlosgt.atak.icung.plugin;

import android.os.SystemClock;

import com.atakmap.android.cot.detail.SensorDetailHandler;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.SensorFOV;

/**
 * Owns the <sensor> FOV cone on ICU NG's own self marker while streaming.
 * Not a CoT detail handler - SensorDetailHandler (built into ATAK, already
 * registered) writes the <sensor> detail into the self marker's SA, but only
 * for a marker carrying the {@link SensorDetailHandler#SENSOR_FOV} meta
 * value, and only from the azimuth/fov/range meta values that addFovToMap
 * (not SensorFOV.setMetrics) keeps up to date. So the meta value is set
 * while streaming and every update goes through addFovToMap, which reuses
 * the existing cone.
 *
 * The cone follows the camera (azimuth true, lens field of view, ground
 * distance to the frame center) from {@link KlvTelemetry}. Without KLV
 * pointing (SRT, USB camera, KLV off, no attitude sensor) it falls back to
 * the direction of travel with a default width and range.
 *
 * Parameter order for addFovToMap is (azimuth, fov, range) - verified
 * against the SDK's own bytecode (LocalVariableTable), not assumed: the
 * obvious-looking (fov, azimuth, range) reading of the plan draft was wrong.
 */
public class SelfSensorFovController implements PointMapItem.OnPointChangedListener,
        KlvTelemetry.PointingListener {

    private static final double FOV_DEGREES = 60d;
    private static final double RANGE_METERS = 200d;
    // Shortest cone drawn, so a camera pointing at its own feet still shows.
    private static final double MIN_RANGE_METERS = 10d;
    // Camera pointing older than this (two missed KLV ticks) is not trusted.
    private static final long POINTING_MAX_AGE_MS = 2500;
    private static final float[] COLOR = { 0f, 1f, 1f, 0.4f }; // r, g, b, alpha

    private final Marker selfMarker;
    private boolean streaming;
    private boolean frontCamera;
    private SensorFOV fov;

    private double pointingAzimuth = Double.NaN;
    private double pointingFov;
    private double pointingRange;
    private long pointingTime;

    public SelfSensorFovController() {
        this.selfMarker = MapView.getMapView().getSelfMarker();
    }

    public void start() {
        selfMarker.addOnPointChangedListener(this);
    }

    public void stop() {
        selfMarker.removeOnPointChangedListener(this);
        removeFov();
    }

    public void refresh(boolean streaming) {
        this.streaming = streaming;
        if (!streaming) pointingAzimuth = Double.NaN;
        update();
    }

    /** The front camera looks back from where the phone faces: the cone turns 180 degrees. */
    public void refresh(boolean streaming, int camera) {
        this.frontCamera = camera == StreamSettings.CAMERA_FRONT;
        refresh(streaming);
    }

    @Override
    public void onPointChanged(PointMapItem item) {
        update();
    }

    @Override
    public void onPointing(double azimuthTrueDeg, double hfovDeg, double groundRangeM) {
        pointingAzimuth = azimuthTrueDeg;
        pointingFov = hfovDeg;
        pointingRange = Math.max(MIN_RANGE_METERS, groundRangeM);
        pointingTime = SystemClock.elapsedRealtime();
        update();
    }

    private void update() {
        if (!streaming || !selfMarker.getPoint().isValid()) {
            removeFov();
            return;
        }
        double azimuth;
        double width;
        double range;
        if (!Double.isNaN(pointingAzimuth)
                && SystemClock.elapsedRealtime() - pointingTime <= POINTING_MAX_AGE_MS) {
            azimuth = pointingAzimuth;
            width = pointingFov;
            range = pointingRange;
        } else {
            azimuth = selfMarker.getTrackHeading();
            if (frontCamera) azimuth += 180d;
            width = FOV_DEGREES;
            range = RANGE_METERS;
        }
        azimuth = ((azimuth % 360d) + 360d) % 360d;
        selfMarker.setMetaBoolean(SensorDetailHandler.SENSOR_FOV, true);
        fov = SensorDetailHandler.addFovToMap(selfMarker, azimuth, width, range, COLOR, true);
    }

    private void removeFov() {
        selfMarker.removeMetaData(SensorDetailHandler.SENSOR_FOV);
        if (fov != null) {
            fov.removeFromGroup();
            fov = null;
        }
    }
}
