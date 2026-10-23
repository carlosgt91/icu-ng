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
 * persisted settings; long press opens the pane. While live, the ring
 * turns red and the dot blinks, like a camera's REC light.
 */
public class RecButtonWidget extends MarkerIconWidget implements
        MapWidget.OnClickListener, MapWidget.OnLongPressListener,
        StreamController.Listener {

    private static final float SIZE_DP = 72f;
    private static final long BLINK_MS = 500L;

    private final MapView mapView;
    private final StreamController controller;
    private final Runnable openPane;
    private final LinearLayoutWidget layout;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final int sizePx;

    private final String iconIdle;
    private final String iconConnecting;
    private final String iconLiveOn;
    private final String iconLiveOff;

    private StreamController.State state = StreamController.State.IDLE;
    private boolean blinkOn;

    private final Runnable blinkTick = new Runnable() {
        @Override
        public void run() {
            if (state != StreamController.State.STREAMING) return;
            blinkOn = !blinkOn;
            setIconUri(blinkOn ? iconLiveOn : iconLiveOff);
            mainHandler.postDelayed(this, BLINK_MS);
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
        iconLiveOn = RecIcons.button(sizePx, RecIcons.COLOR_LIVE, RecIcons.COLOR_LIVE, false, 255);
        iconLiveOff = RecIcons.button(sizePx, RecIcons.COLOR_LIVE, RecIcons.COLOR_LIVE, false, 0);

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
        mainHandler.removeCallbacks(blinkTick);
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
        mainHandler.removeCallbacks(blinkTick);
        switch (newState) {
            case CONNECTING:
                setIconUri(iconConnecting);
                break;
            case STREAMING:
                blinkOn = true;
                setIconUri(iconLiveOn);
                mainHandler.postDelayed(blinkTick, BLINK_MS);
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
        // Icon sizes are in ATAK's density-independent units (it scales them
        // itself); the bitmap is rendered at sizePx so it stays sharp.
        builder.setSize((int) SIZE_DP, (int) SIZE_DP);
        builder.setImageUri(Icon.STATE_DEFAULT, uri);
        setIcon(builder.build());
    }
}
