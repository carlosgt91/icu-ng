package com.carlosgt.atak.icung.plugin;

import com.atakmap.android.cot.detail.SensorDetailHandler;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.Marker;
import com.atakmap.android.maps.PointMapItem;
import com.atakmap.android.maps.SensorFOV;

/**
 * Owns the <sensor> FOV cone on ICU NG's own self marker. Not a CoT detail
 * handler - SensorDetailHandler (built into ATAK, already registered) does
 * that on its own once this map item exists.
 *
 * Parameter order for addFovToMap/setMetrics is (azimuth, fov, range) -
 * verified against the SDK's own bytecode (LocalVariableTable), not assumed:
 * the obvious-looking (fov, azimuth, range) reading of the plan draft was
 * wrong.
 */
public class SelfSensorFovController implements PointMapItem.OnPointChangedListener {

    private static final double FOV_DEGREES = 60d;
    private static final double RANGE_METERS = 200d;
    private static final float[] COLOR = { 0f, 1f, 1f, 0.4f }; // r, g, b, alpha

    private final Marker selfMarker;
    private boolean streaming;
    private SensorFOV fov;

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
        update();
    }

    @Override
    public void onPointChanged(PointMapItem item) {
        update();
    }

    private void update() {
        if (streaming && selfMarker.getPoint().isValid()) {
            double azimuth = selfMarker.getTrackHeading();
            if (fov == null) {
                fov = SensorDetailHandler.addFovToMap(selfMarker, azimuth, FOV_DEGREES,
                        RANGE_METERS, COLOR, true);
            } else {
                fov.setMetrics((float) azimuth, (float) FOV_DEGREES, (float) RANGE_METERS);
            }
        } else {
            removeFov();
        }
    }

    private void removeFov() {
        if (fov != null) {
            fov.removeFromGroup();
            fov = null;
        }
    }
}
