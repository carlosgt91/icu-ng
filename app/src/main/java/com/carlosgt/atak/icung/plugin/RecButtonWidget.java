package com.carlosgt.atak.icung.plugin;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.HapticFeedbackConstants;
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
 * persisted settings; long press opens the pane, and long press then
 * drag moves the button up or down the right edge (remembered). While
 * live, the ring turns red and the dot blinks, like a camera's REC light.
 */
public class RecButtonWidget extends MarkerIconWidget implements
        MapWidget.OnClickListener, MapWidget.OnLongPressListener,
        MapWidget.OnPressListener, MapWidget.OnMoveListener,
        MapWidget.OnUnpressListener, StreamController.Listener {

    private static final float SIZE_DP = 72f;
    private static final long BLINK_MS = 500L;
    private static final float RIGHT_MARGIN_DP = 16f;
    // Finger travel after the long press that makes it a drag, not a tap.
    private static final float DRAG_SLOP_DP = 8f;
    private static final String KEY_OFFSET = "icung.rec_offset_dp";

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

    private final SharedPreferences prefs;
    private final float density;
    /** dp below (+) or above (-) the button's default place */
    private float offsetDp;
    private float pressRawY;
    private float pressOffsetDp;
    private boolean longPressed;
    private boolean dragging;

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

        density = mapView.getContext().getResources().getDisplayMetrics().density;
        sizePx = (int) (SIZE_DP * density);
        prefs = PreferenceManager.getDefaultSharedPreferences(mapView.getContext());
        offsetDp = prefs.getFloat(KEY_OFFSET, 0f);
        iconIdle = RecIcons.button(sizePx, RecIcons.COLOR_IDLE, false, 255);
        iconConnecting = RecIcons.button(sizePx, RecIcons.COLOR_CONNECTING, false, 255);
        iconLiveOn = RecIcons.button(sizePx, RecIcons.COLOR_LIVE, RecIcons.COLOR_LIVE, false, 255);
        iconLiveOff = RecIcons.button(sizePx, RecIcons.COLOR_LIVE, RecIcons.COLOR_LIVE, false, 0);

        RootLayoutWidget root = (RootLayoutWidget) mapView.getComponentExtra("rootLayoutWidget");
        layout = root.getLayout(RootLayoutWidget.RIGHT_EDGE)
                .getOrCreateLayout("RE_V/IcuNg_V/IcuNg_H");
        setMargins(0f, 0f, RIGHT_MARGIN_DP, 0f);
        setIconUri(iconIdle);

        addOnClickListener(this);
        addOnLongPressListener(this);
        addOnPressListener(this);
        addOnMoveListener(this);
        addOnUnpressListener(this);
    }

    // The right-edge column is centred vertically and grows around its
    // contents, so a margin moves the button by half of it - and, measured
    // on ATAK 5.8, away from the side it is on: a bottom margin moves it
    // down. Margins are in pixels.
    //
    private void applyOffset() {
        applyOffset(0f);
    }

    // At start the margin is only set after ATAK has settled its widget
    // layout: one set as the button is added - or soon after - is kept but
    // never laid out, and spoils the later ones too (measured on 5.8: no
    // early set and one at 2 s works every time). Set twice in case ATAK
    // is slower, a sub-pixel apart: setting the margins it already has is
    // a no-op.
    private void applyOffset(float nudgePx) {
        float shift = 2f * offsetDp * density;
        setMargins(0f, Math.max(0f, -shift) + nudgePx, RIGHT_MARGIN_DP,
                Math.max(0f, shift) + nudgePx);
    }

    // Startup re-applies: see applyOffset().
    private static final long[] SETTLE_MS = { 2000, 5000 };

    // Keeps the whole button on screen.
    private float clampOffset(float dp) {
        float half = mapView.getHeight() / density / 2f - SIZE_DP;
        if (half <= 0) return 0f;
        return Math.max(-half, Math.min(half, dp));
    }

    public void show() {
        layout.addWidget(this);
        for (int i = 0; i < SETTLE_MS.length; i++) {
            final float nudge = 0.01f * (i + 1);
            mainHandler.postDelayed(() -> applyOffset(nudge), SETTLE_MS[i]);
        }
        controller.addListener(this);
    }

    public void dispose() {
        controller.removeListener(this);
        mainHandler.removeCallbacksAndMessages(null);
        removeOnClickListener(this);
        removeOnLongPressListener(this);
        removeOnPressListener(this);
        removeOnMoveListener(this);
        removeOnUnpressListener(this);
        layout.removeWidget(this);
    }

    @Override
    public void onMapWidgetPress(MapWidget widget, MotionEvent event) {
        pressRawY = event.getRawY();
        pressOffsetDp = offsetDp;
        longPressed = false;
        dragging = false;
    }

    @Override
    public void onMapWidgetClick(MapWidget widget, MotionEvent event) {
        if (longPressed) return; // the release of a long press or a drag
        mainHandler.post(controller::toggle);
    }

    // Opening the pane waits for the release: if the finger moves first,
    // the long press was the start of a drag.
    @Override
    public void onMapWidgetLongPress(MapWidget widget) {
        longPressed = true;
        mapView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    @Override
    public boolean onMapWidgetMove(MapWidget widget, MotionEvent event) {
        if (!longPressed) return false;
        float dyDp = (event.getRawY() - pressRawY) / density;
        if (!dragging && Math.abs(dyDp) < DRAG_SLOP_DP) return true;
        dragging = true;
        offsetDp = clampOffset(pressOffsetDp + dyDp);
        applyOffset();
        return true;
    }

    @Override
    public void onMapWidgetUnpress(MapWidget widget, MotionEvent event) {
        if (!longPressed) return;
        if (dragging) {
            prefs.edit().putFloat(KEY_OFFSET, offsetDp).apply();
        } else {
            mainHandler.post(openPane);
        }
        dragging = false;
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
