package com.carlosgt.atak.icung.plugin;

import android.util.Log;

import com.atakmap.android.maps.MapDataRef;
import com.atakmap.android.maps.MapItem;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.maps.assets.MapAssets;
import com.atakmap.android.menu.MapMenuButtonWidget;
import com.atakmap.android.menu.MapMenuFactory;
import com.atakmap.android.menu.MapMenuReceiver;
import com.atakmap.android.menu.MapMenuWidget;
import com.atakmap.android.menu.MenuMapAdapter;
import com.atakmap.android.menu.MenuResourceFactory;
import com.atakmap.android.widgets.WidgetIcon;

import java.io.IOException;

import gov.tak.api.widgets.IMapMenuButtonWidget;
import gov.tak.api.widgets.IMapWidget;

/**
 * Adds an ICU NG button to the radial menu of the user's own self marker:
 * one tap starts or stops the stream, same as the REC map button. Other
 * markers get ATAK's normal menu untouched (create() returns null so the
 * next factory / the default one handles them).
 *
 * ATAK builds a fresh menu every time the radial opens, so the icon always
 * reflects the stream state at that moment.
 */
public class SelfMarkerRadialMenu implements MapMenuFactory {

    private static final String TAG = "IcuNgRadial";
    private static final int ICON_PX = 32;

    private final MapView mapView;
    private final StreamController controller;
    private final MenuResourceFactory defaultFactory;
    private final String iconIdle;
    private final String iconLive;
    private boolean registered;

    public SelfMarkerRadialMenu(MapView mapView, StreamController controller) {
        this.mapView = mapView;
        this.controller = controller;

        // ATAK's own assets/menu filters, hence the ATAK context
        MapAssets mapAssets = new MapAssets(mapView.getContext());
        MenuMapAdapter adapter = new MenuMapAdapter();
        try {
            adapter.loadMenuFilters(mapAssets, "filters/menu_filters.xml");
        } catch (IOException e) {
            Log.w(TAG, "menu filters not loaded, using defaults", e);
        }
        defaultFactory = new MenuResourceFactory(mapView, mapView.getMapData(),
                mapAssets, adapter);

        iconIdle = RecIcons.button(ICON_PX * 2, RecIcons.COLOR_IDLE, false, 255);
        iconLive = RecIcons.button(ICON_PX * 2, RecIcons.COLOR_LIVE, true, 255);
    }

    public void register() {
        MapMenuReceiver receiver = MapMenuReceiver.getInstance();
        if (receiver != null && !registered) {
            registered = receiver.registerMapMenuFactory(this);
        }
    }

    public void dispose() {
        MapMenuReceiver receiver = MapMenuReceiver.getInstance();
        if (receiver != null && registered) {
            receiver.unregisterMapMenuFactory(this);
            registered = false;
        }
    }

    @Override
    public MapMenuWidget create(MapItem item) {
        if (item == null || item != mapView.getSelfMarker()) return null;

        MapMenuWidget menu = defaultFactory.create(item);
        if (menu == null) return null;
        menu.addChildWidget(buildButton(menu));
        return menu;
    }

    private MapMenuButtonWidget buildButton(MapMenuWidget menu) {
        MapMenuButtonWidget button = new MapMenuButtonWidget(mapView.getContext());
        // Same geometry as the sibling buttons, so ATAK lays the ring out
        // with ours as one more slice instead of floating on top.
        button.setOrientation(button.getOrientationAngle(), menu.getInnerRadius());
        button.setButtonSize(button.getButtonSpan(), menu.getButtonWidth());
        button.setLayoutWeight(averageLayoutWeight(menu));

        button.setIcon(new WidgetIcon.Builder()
                .setAnchor(ICON_PX / 2, ICON_PX / 2)
                .setSize(ICON_PX, ICON_PX)
                .setImageRef(0, MapDataRef.parseUri(
                        controller.isStreaming() ? iconLive : iconIdle))
                .build());

        button.setOnButtonClickHandler(new IMapMenuButtonWidget.OnButtonClickHandler() {
            @Override
            public boolean isSupported(Object opaque) {
                return true;
            }

            @Override
            public void performAction(Object opaque) {
                mapView.post(controller::toggle);
            }
        });
        return button;
    }

    private static float averageLayoutWeight(MapMenuWidget menu) {
        float sum = 0f;
        int count = 0;
        for (IMapWidget child : menu.getChildren()) {
            if (child instanceof MapMenuButtonWidget) {
                sum += ((MapMenuButtonWidget) child).getLayoutWeight();
                count++;
            }
        }
        return count == 0 ? 1f : sum / count;
    }
}
