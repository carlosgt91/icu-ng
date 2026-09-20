
package com.carlosgt.atak.icung.plugin;

import android.content.Context;
import android.view.SurfaceHolder;
import android.view.View;

import com.atak.plugins.impl.PluginContextProvider;
import com.atak.plugins.impl.PluginLayoutInflater;
import com.pedro.common.ConnectChecker;
import com.pedro.encoder.input.video.CameraHelper;
import com.pedro.library.rtsp.RtspCamera2;
import com.pedro.library.view.OpenGlView;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.Pane;
import gov.tak.api.ui.PaneBuilder;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.platform.marshal.MarshalManager;

public class IcuNgPlugin implements IPlugin, ConnectChecker {

    IServiceController serviceController;
    Context pluginContext;
    IHostUIService uiService;
    ToolbarItem toolbarItem;
    Pane templatePane;
    RtspCamera2 camera;

    public IcuNgPlugin(IServiceController serviceController) {
        this.serviceController = serviceController;
        final PluginContextProvider ctxProvider = serviceController
                .getService(PluginContextProvider.class);
        if (ctxProvider != null) {
            pluginContext = ctxProvider.getPluginContext();
            pluginContext.setTheme(R.style.ATAKPluginTheme);
        }

        // obtain the UI service
        uiService = serviceController.getService(IHostUIService.class);

        // initialize the toolbar button for the plugin

        // create the button and set the identifier to be well known
        // if you fail to do this, the toolbar configuration will never
        // be able to find it again after the user moves the icon.
        toolbarItem = new ToolbarItem.Builder(
                pluginContext.getString(R.string.app_name),
                MarshalManager.marshal(
                        pluginContext.getResources().getDrawable(R.drawable.ic_launcher),
                        android.graphics.drawable.Drawable.class,
                        gov.tak.api.commons.graphics.Bitmap.class))
                .setListener(new ToolbarItemAdapter() {
                    @Override
                    public void onClick(ToolbarItem item) {
                        showPane();
                    }
                }).setIdentifier(pluginContext.getPackageName())
                .build();
    }

    @Override
    public void onStart() {
        // the plugin is starting, add the button to the toolbar
        if (uiService == null)
            return;

        uiService.addToolbarItem(toolbarItem);
    }

    @Override
    public void onStop() {
        // the plugin is stopping, remove the button from the toolbar
        if (uiService == null)
            return;

        uiService.removeToolbarItem(toolbarItem);

        if (camera != null && camera.isOnPreview()) {
            camera.stopPreview();
        }
    }

    private void showPane() {
        // instantiate the plugin view if necessary
        if(templatePane == null) {
            // Remember to use the PluginLayoutInflator if you are actually inflating a custom view
            // In this case, using it is not necessary - but I am putting it here to remind
            // developers to look at this Inflator

            View paneView = PluginLayoutInflater.inflate(pluginContext,
                    R.layout.main_layout, null);

            OpenGlView previewView = paneView.findViewById(R.id.preview_view);
            camera = new RtspCamera2(previewView, this);

            // RootEncoder needs the SurfaceView's real Surface to exist before
            // startPreview() can touch it - calling it right after inflate()
            // crashes with NullPointerException: getSurfaceTexture(...) must
            // not be null, because Android creates the Surface asynchronously
            // on a later layout pass, not synchronously when the view is
            // inflated. Wait for surfaceCreated(), same pattern RootEncoder's
            // own demo app uses for TextureView's onSurfaceTextureAvailable.
            previewView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    if (!camera.isOnPreview()) {
                        camera.startPreview(CameraHelper.Facing.BACK);
                    }
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                    // no-op
                }

                @Override
                public void surfaceDestroyed(SurfaceHolder holder) {
                    // no-op
                }
            });

            templatePane = new PaneBuilder(paneView)
                    // relative location is set to default; pane will switch location dependent on
                    // current orientation of device screen
                    .setMetaValue(Pane.RELATIVE_LOCATION, Pane.Location.Default)
                    // pane will take up 50% of screen width in landscape mode
                    .setMetaValue(Pane.PREFERRED_WIDTH_RATIO, 0.5D)
                    // pane will take up 50% of screen height in portrait mode
                    .setMetaValue(Pane.PREFERRED_HEIGHT_RATIO, 0.5D)
                    .build();
        }

        // if the plugin pane is not visible, show it!
        if(!uiService.isPaneVisible(templatePane)) {
            uiService.showPane(templatePane, null);
        }
    }

    @Override
    public void onConnectionStarted(String url) {
        // Sin uso hasta la Fase 2 (streaming RTSP)
    }

    @Override
    public void onConnectionSuccess() {
        // Sin uso hasta la Fase 2 (streaming RTSP)
    }

    @Override
    public void onConnectionFailed(String reason) {
        // Sin uso hasta la Fase 2 (streaming RTSP)
    }

    @Override
    public void onDisconnect() {
        // Sin uso hasta la Fase 2 (streaming RTSP)
    }

    @Override
    public void onAuthError() {
        // Sin uso hasta la Fase 2 (streaming RTSP)
    }

    @Override
    public void onAuthSuccess() {
        // Sin uso hasta la Fase 2 (streaming RTSP)
    }
}
