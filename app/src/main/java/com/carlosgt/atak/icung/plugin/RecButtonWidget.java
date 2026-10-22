package com.carlosgt.atak.icung.plugin;

import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.widget.Toast;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.widgets.LinearLayoutWidget;
import com.atakmap.android.widgets.MapWidget;
import com.atakmap.android.widgets.MarkerIconWidget;
import com.atakmap.android.widgets.RootLayoutWidget;
import com.atakmap.coremap.maps.assets.Icon;

/**
 * Red REC button floating on the map, so a stream can be started and
 * stopped without opening the pane. Tap toggles the stream with the
 * persisted settings; long press opens the pane. While live, the button
 * turns into a pulsing stop square.
 */
public class RecButtonWidget extends MarkerIconWidget implements
        MapWidget.OnClickListener, MapWidget.OnLongPressListener,
        StreamController.Listener {

    private static final float SIZE_DP = 56f;
    private static final long PULSE_PERIOD_MS = 1200L;
    private static final long PULSE_FRAME_MS = 100L;
    private static final int PULSE_MIN_ALPHA = 90;

    private final MapView mapView;
    private final StreamController controller;
    private final Runnable openPane;
    private final LinearLayoutWidget layout;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final int sizePx;

    private final String iconIdle;
    private final String iconConnecting;
    private final String[] iconLiveFrames;

    private StreamController.State state = StreamController.State.IDLE;
    private int pulseFrame;

    private final Runnable pulseTick = new Runnable() {
        @Override
        public void run() {
            if (state != StreamController.State.STREAMING) return;
            pulseFrame = (pulseFrame + 1) % iconLiveFrames.length;
            setIconUri(iconLiveFrames[pulseFrame]);
            mainHandler.postDelayed(this, PULSE_FRAME_MS);
        }
    };

    public RecButtonWidget(MapView mapView, StreamController controller, Runnable openPane) {
        this.mapView = mapView;
        this.controller = controller;
        this.openPane = openPane;
        setName("ICU NG REC");

        sizePx = (int) (SIZE_DP * mapView.getContext().getResources()
                .getDisplayMetrics().density);
        iconIdle = RecIcons.button(sizePx, RecIcons.COLOR_IDLE, false, 255);
        iconConnecting = RecIcons.button(sizePx, RecIcons.COLOR_CONNECTING, false, 255);

        // Pre-render one pulse cycle (raised cosine on the stop square's
        // alpha) instead of re-encoding a PNG every frame.
        int frames = (int) (PULSE_PERIOD_MS / PULSE_FRAME_MS);
        iconLiveFrames = new String[frames];
        for (int i = 0; i < frames; i++) {
            double wave = 0.5 + 0.5 * Math.cos(2 * Math.PI * i / frames);
            int alpha = (int) (PULSE_MIN_ALPHA + wave * (255 - PULSE_MIN_ALPHA));
            iconLiveFrames[i] = RecIcons.button(sizePx, RecIcons.COLOR_LIVE, true, alpha);
        }

        RootLayoutWidget root = (RootLayoutWidget) mapView.getComponentExtra("rootLayoutWidget");
        layout = root.getLayout(RootLayoutWidget.RIGHT_EDGE)
                .getOrCreateLayout("RE_V/IcuNg_V/IcuNg_H");
        setMargins(0f, 0f, 16f, 0f);
        setIconUri(iconIdle);

        addOnClickListener(this);
        addOnLongPressListener(this);
    }

    public void show() {
        layout.addWidget(this);
        controller.addListener(this);
    }

    public void dispose() {
        controller.removeListener(this);
        mainHandler.removeCallbacks(pulseTick);
        removeOnClickListener(this);
        layout.removeWidget(this);
    }

    @Override
    public void onMapWidgetClick(MapWidget widget, MotionEvent event) {
        mainHandler.post(controller::toggle);
    }

    @Override
    public void onMapWidgetLongPress(MapWidget widget) {
        mainHandler.post(openPane);
    }

    @Override
    public void onStreamStateChanged(StreamController.State newState, String message) {
        state = newState;
        mainHandler.removeCallbacks(pulseTick);
        switch (newState) {
            case CONNECTING:
                setIconUri(iconConnecting);
                break;
            case STREAMING:
                pulseFrame = 0;
                setIconUri(iconLiveFrames[0]);
                mainHandler.postDelayed(pulseTick, PULSE_FRAME_MS);
                break;
            case ERROR:
                setIconUri(iconIdle);
                // The pane may well be closed when REC is tapped - without
                // this, a bad URL or a refused connection would look like
                // the button simply did nothing.
                if (message != null) {
                    Toast.makeText(mapView.getContext(), message, Toast.LENGTH_LONG).show();
                }
                break;
            default:
                setIconUri(iconIdle);
                break;
        }
    }

    private void setIconUri(String uri) {
        Icon.Builder builder = new Icon.Builder();
        builder.setAnchor(0, 0);
        builder.setColor(Icon.STATE_DEFAULT, Color.WHITE);
        builder.setSize(sizePx, sizePx);
        builder.setImageUri(Icon.STATE_DEFAULT, uri);
        setIcon(builder.build());
    }
}
