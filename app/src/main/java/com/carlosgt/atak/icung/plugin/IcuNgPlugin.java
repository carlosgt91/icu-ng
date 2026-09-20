
package com.carlosgt.atak.icung.plugin;

import android.content.Context;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.atak.plugins.impl.PluginContextProvider;
import com.atak.plugins.impl.PluginLayoutInflater;
import com.pedro.common.ConnectChecker;
import com.pedro.encoder.input.video.CameraHelper;
import com.pedro.library.base.Camera2Base;
import com.pedro.library.rtsp.RtspCamera2;
import com.pedro.library.srt.SrtCamera2;
import com.pedro.library.util.streamclient.RtspStreamClient;
import com.pedro.library.util.streamclient.SrtStreamClient;
import com.pedro.library.view.OpenGlView;
import com.pedro.srt.srt.packets.control.handshake.EncryptionType;

import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import gov.tak.api.plugin.IPlugin;
import gov.tak.api.plugin.IServiceController;
import gov.tak.api.ui.IHostUIService;
import gov.tak.api.ui.Pane;
import gov.tak.api.ui.PaneBuilder;
import gov.tak.api.ui.ToolbarItem;
import gov.tak.api.ui.ToolbarItemAdapter;
import gov.tak.api.video.ConnectionEntry;
import gov.tak.api.video.IVideoConnectionManager;
import gov.tak.platform.marshal.MarshalManager;

import com.atakmap.android.cot.detail.CotDetailManager;
import com.atakmap.android.maps.MapView;
import com.atakmap.android.video.manager.VideoManager;

public class IcuNgPlugin implements IPlugin, ConnectChecker {

    IServiceController serviceController;
    Context pluginContext;
    IHostUIService uiService;
    ToolbarItem toolbarItem;
    Pane templatePane;
    Camera2Base camera;
    OpenGlView previewView;
    EditText rtspUrlInput;
    Button streamToggleButton;
    Spinner protocolSpinner;
    CheckBox trustAllCertsCheckbox;
    View srtEncryptionRow;
    EditText srtPassphraseInput;
    Spinner srtEncryptionSpinner;
    TextView statusText;
    boolean suppressProtocolListener;
    IVideoConnectionManager videoConnectionManager;
    SelfVideoDetailHandler videoDetailHandler;
    ConnectionEntry connectionEntry;
    SelfSensorFovController sensorFovController;
    String activeStreamUrl;
    int activeProtocol; // 0=RTSP, 1=RTSPS, 2=SRT - mismo orden que R.array.protocol_entries
    boolean activeTrustAllCerts;
    String activeSrtPassphrase = "";
    EncryptionType activeSrtEncryptionType = EncryptionType.AES128;
    int lastValidProtocolPosition;

    private static final EncryptionType[] SRT_ENCRYPTION_TYPES = {
            EncryptionType.AES128, EncryptionType.AES192, EncryptionType.AES256
    };

    private static int indexOfEncryptionType(EncryptionType type) {
        for (int i = 0; i < SRT_ENCRYPTION_TYPES.length; i++) {
            if (SRT_ENCRYPTION_TYPES[i] == type) return i;
        }
        return 0;
    }

    private static final TrustManager TRUST_ALL_CERTS = new X509TrustManager() {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    };

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

        videoConnectionManager = VideoManager.getInstance();

        String selfUid = MapView.getMapView().getSelfMarker().getUID();
        videoDetailHandler = new SelfVideoDetailHandler(
                selfUid,
                this::isStreamingWithFix,
                () -> connectionEntry);
        CotDetailManager.getInstance().registerHandler(videoDetailHandler);

        sensorFovController = new SelfSensorFovController();
        sensorFovController.start();
    }

    private boolean isStreamingWithFix() {
        return camera != null && camera.isStreaming()
                && MapView.getMapView().getSelfMarker().getPoint().isValid();
    }

    @Override
    public void onStop() {
        // the plugin is stopping, remove the button from the toolbar
        if (uiService == null)
            return;

        uiService.removeToolbarItem(toolbarItem);

        stopCameraAndStream();

        if (videoDetailHandler != null) {
            CotDetailManager.getInstance().unregisterHandler(videoDetailHandler);
        }

        if (sensorFovController != null) {
            sensorFovController.stop();
        }
    }

    private void stopCameraAndStream() {
        if (camera != null && camera.isStreaming()) {
            stopStreamOnly();
        }

        if (camera != null && camera.isOnPreview()) {
            camera.stopPreview();
        }
    }

    // Everything that undoes toggleStream()'s "start" branch - shared by the
    // direct "Detener" click and any indirect stop (hiding/closing the panel),
    // so the button text and CoT/video bookkeeping never fall out of sync
    // with the actual RtspCamera2 state.
    private void stopStreamOnly() {
        camera.stopStream();
        if (connectionEntry != null) {
            videoConnectionManager.removeConnectionEntry(connectionEntry.getUID());
            connectionEntry = null;
        }
        if (sensorFovController != null) {
            sensorFovController.refresh(false);
        }
        if (streamToggleButton != null) {
            streamToggleButton.setText(R.string.start_stream);
        }
    }

    private void setStatus(String text) {
        if (statusText != null) {
            statusText.setText(text);
        }
    }

    private void onProtocolSelected(int position) {
        boolean crossingSrtBoundary = camera != null
                && (position == 2) != (camera instanceof SrtCamera2);
        if (crossingSrtBoundary) {
            // La cámara ya existe siempre en cuanto se abre el panel (el
            // preview arranca solo, antes de que el usuario toque nada), así
            // que "esperar a que camera sea null" nunca ocurre en la práctica
            // - dejaba SRT inalcanzable desde la UI. Mientras no se esté
            // emitiendo, reconstruir aquí es seguro: es exactamente el mismo
            // patrón (stopPreview() -> nueva instancia -> prepareVideo/Audio
            // -> startPreview) que showPane() ya hace al abrir el panel desde
            // cero, solo que sin esperar a un nuevo surfaceCreated() porque
            // la Surface de previewView ya existe y sigue siendo válida.
            if (camera.isStreaming()) {
                revertProtocolSelection();
                setStatus(pluginContext.getString(R.string.status_error_format,
                        "detén la emisión antes de cambiar a/desde SRT"));
                return;
            }
            camera.stopPreview();
            camera = position == 2
                    ? new SrtCamera2(previewView, this)
                    : new RtspCamera2(previewView, this);
            camera.prepareVideo();
            camera.prepareAudio();
            camera.startPreview(CameraHelper.Facing.BACK);
        }
        lastValidProtocolPosition = position;
        trustAllCertsCheckbox.setVisibility(position == 1 ? View.VISIBLE : View.GONE);
        srtEncryptionRow.setVisibility(position == 2 ? View.VISIBLE : View.GONE);
        rtspUrlInput.setText(rewriteUrlScheme(rtspUrlInput.getText().toString(), position));
    }

    private void revertProtocolSelection() {
        suppressProtocolListener = true;
        protocolSpinner.setSelection(lastValidProtocolPosition);
        suppressProtocolListener = false;
    }

    // position: 0=RTSP, 1=RTSPS, 2=SRT (mismo orden que R.array.protocol_entries)
    private static String rewriteUrlScheme(String url, int position) {
        String scheme = position == 1 ? "rtsps" : position == 2 ? "srt" : "rtsp";
        int schemeEnd = url.indexOf("://");
        String rest = schemeEnd >= 0 ? url.substring(schemeEnd + 3) : url;
        return scheme + "://" + rest;
    }

    private static String parseUrlScheme(String url) {
        int schemeEnd = url.indexOf("://");
        return schemeEnd >= 0 ? url.substring(0, schemeEnd) : "";
    }

    // Called whenever the panel stops being visible for any reason (hidden
    // or closed). If ICU NG is actively streaming, keeps it alive headless
    // (Fase 4) instead of stopping it - replaceView(Context) only swaps the
    // local camera->encoder render target (verified: it never touches
    // startStream()/stopStream()), so the RTSP push, and the <__video>/
    // <sensor> from Fase 3 that key off isStreaming(), stay up. If it was
    // only previewing (never pressed Emitir), there's nothing worth keeping
    // alive - same full stop as before, and templatePane/paneView/
    // previewView always get rebuilt from scratch on the next open either
    // way (that part of the Fase 3 fix stands - only camera is ever reused).
    private void tearDownPane() {
        templatePane = null;

        if (camera == null) {
            return;
        }

        if (camera.isStreaming()) {
            camera.replaceView(pluginContext);
        } else {
            stopCameraAndStream();
            camera = null;
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

            previewView = paneView.findViewById(R.id.preview_view);

            rtspUrlInput = paneView.findViewById(R.id.rtsp_url_input);
            streamToggleButton = paneView.findViewById(R.id.stream_toggle_button);
            streamToggleButton.setOnClickListener(v -> toggleStream());

            protocolSpinner = paneView.findViewById(R.id.protocol_spinner);
            trustAllCertsCheckbox = paneView.findViewById(R.id.trust_all_certs_checkbox);
            srtEncryptionRow = paneView.findViewById(R.id.srt_encryption_row);
            srtPassphraseInput = paneView.findViewById(R.id.srt_passphrase_input);
            srtEncryptionSpinner = paneView.findViewById(R.id.srt_encryption_spinner);
            statusText = paneView.findViewById(R.id.status_text);

            ArrayAdapter<CharSequence> protocolAdapter = ArrayAdapter.createFromResource(
                    pluginContext, R.array.protocol_entries, android.R.layout.simple_spinner_item);
            protocolAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            protocolSpinner.setAdapter(protocolAdapter);

            ArrayAdapter<CharSequence> encryptionAdapter = ArrayAdapter.createFromResource(
                    pluginContext, R.array.srt_encryption_entries, android.R.layout.simple_spinner_item);
            encryptionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            srtEncryptionSpinner.setAdapter(encryptionAdapter);

            protocolSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (suppressProtocolListener) return;
                    onProtocolSelected(position);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    // no-op
                }
            });

            // Fase 4: if camera already exists, it's one tearDownPane() left
            // alive in the background - reattach it instead of building a
            // new one, and restore the UI (URL, boton, protocolo, cifrado)
            // to reflect that it's still streaming.
            final boolean resumingBackgroundCamera = camera != null;
            if (resumingBackgroundCamera) {
                rtspUrlInput.setText(activeStreamUrl);
                streamToggleButton.setText(R.string.stop_stream);
                setStatus(pluginContext.getString(R.string.status_streaming));

                suppressProtocolListener = true;
                protocolSpinner.setSelection(activeProtocol);
                suppressProtocolListener = false;
                lastValidProtocolPosition = activeProtocol;
                trustAllCertsCheckbox.setVisibility(activeProtocol == 1 ? View.VISIBLE : View.GONE);
                trustAllCertsCheckbox.setChecked(activeTrustAllCerts);
                srtEncryptionRow.setVisibility(activeProtocol == 2 ? View.VISIBLE : View.GONE);
                srtPassphraseInput.setText(activeSrtPassphrase);
                srtEncryptionSpinner.setSelection(indexOfEncryptionType(activeSrtEncryptionType));
            } else {
                lastValidProtocolPosition = protocolSpinner.getSelectedItemPosition();
                setStatus(pluginContext.getString(R.string.status_idle));
                camera = protocolSpinner.getSelectedItemPosition() == 2
                        ? new SrtCamera2(previewView, this)
                        : new RtspCamera2(previewView, this);
                camera.prepareVideo();
                camera.prepareAudio();
            }

            // RootEncoder needs the SurfaceView's real Surface to exist before
            // startPreview()/replaceView() can touch it - calling it right
            // after inflate() crashes with NullPointerException:
            // getSurfaceTexture(...) must not be null, because Android
            // creates the Surface asynchronously on a later layout pass, not
            // synchronously when the view is inflated. Wait for
            // surfaceCreated(), same pattern RootEncoder's own demo app uses
            // for TextureView's onSurfaceTextureAvailable. This is the only
            // place startPreview()/replaceView() are ever called - see
            // tearDownPane() for why nothing tries to resume across a full
            // rebuild of paneView/previewView themselves, only camera.
            previewView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(SurfaceHolder holder) {
                    if (resumingBackgroundCamera) {
                        camera.replaceView(previewView);
                    } else if (!camera.isOnPreview()) {
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
            uiService.showPane(templatePane, new IHostUIService.IPaneLifecycleListener() {
                @Override
                public void onPaneVisible(boolean visible) {
                    if (!visible) {
                        tearDownPane();
                    }
                }

                @Override
                public void onPaneClose() {
                    tearDownPane();
                }
            });
        }
    }

    private void toggleStream() {
        if (camera.isStreaming()) {
            stopStreamOnly();
        } else {
            String url = rtspUrlInput.getText().toString();
            int protocol = protocolSpinner.getSelectedItemPosition();

            String expectedScheme = protocol == 1 ? "rtsps" : protocol == 2 ? "srt" : "rtsp";
            if (!expectedScheme.equals(parseUrlScheme(url))) {
                setStatus(pluginContext.getString(R.string.status_error_format,
                        "la URL debe empezar por " + expectedScheme + "://"));
                return;
            }

            String passphrase = srtPassphraseInput.getText().toString();
            if (protocol == 2 && !passphrase.isEmpty()
                    && (passphrase.length() < 10 || passphrase.length() > 79)) {
                setStatus(pluginContext.getString(R.string.status_error_format,
                        "el passphrase SRT debe tener entre 10 y 79 caracteres"));
                return;
            }

            EncryptionType encryptionType = SRT_ENCRYPTION_TYPES[srtEncryptionSpinner.getSelectedItemPosition()];
            if (protocol == 1) {
                ((RtspStreamClient) camera.getStreamClient())
                        .addCertificates(trustAllCertsCheckbox.isChecked() ? TRUST_ALL_CERTS : null);
            } else if (protocol == 2 && !passphrase.isEmpty()) {
                ((SrtStreamClient) camera.getStreamClient()).setPassphrase(passphrase, encryptionType);
            }

            activeStreamUrl = url;
            activeProtocol = protocol;
            activeTrustAllCerts = trustAllCertsCheckbox.isChecked();
            activeSrtPassphrase = passphrase;
            activeSrtEncryptionType = encryptionType;

            // Verificado en BaseEncoder: stop() pone prepared=false, y start()
            // exige prepared=true - sin volver a preparar aqui, un segundo
            // intento de emitir sobre la misma camara (tras "Detener" o tras
            // un fallo de conexion que ahora dispara stopStreamOnly(), Task 4)
            // crashea con IllegalStateException: VideoEncoder not prepared yet.
            camera.prepareVideo();
            camera.prepareAudio();

            camera.startStream(url);
            streamToggleButton.setText(R.string.stop_stream);

            // RTSPS (protocol == 1) nunca se anuncia por CoT/IVideoConnectionManager:
            // verificado con javap que ConnectionEntryBase.Protocol no tiene una
            // constante RTSPS (solo RTSP/RTMP/RTMPS/SRT/...), y que Protocol.RTSP.toURL()
            // siempre construye literalmente "rtsp://" - anunciar RTSPS como
            // Protocol.RTSP le daria a otros ATAK una URL rota que su cliente de
            // Video no podria abrir contra un servidor que exige TLS. El push RTSPS
            // en si sigue funcionando igual - solo se deja de anunciar en el mapa.
            connectionEntry = protocol == 1 ? null : buildConnectionEntry(url, protocol, passphrase);
            if (connectionEntry != null) {
                videoConnectionManager.addConnectionEntry(connectionEntry);
            }
            sensorFovController.refresh(true);
        }
    }

    private ConnectionEntry buildConnectionEntry(String url, int protocol, String passphrase) {
        android.net.Uri parsed = android.net.Uri.parse(url);
        ConnectionEntry.Protocol cotProtocol = protocol == 2
                ? ConnectionEntry.Protocol.SRT
                : ConnectionEntry.Protocol.RTSP;

        String path = parsed.getQueryParameter("streamid");
        if (path == null || path.isEmpty()) {
            path = parsed.getPath();
        }

        ConnectionEntry.Builder builder = new ConnectionEntry.Builder()
                .setUID(pluginContext.getPackageName() + "." + System.currentTimeMillis())
                .setAlias("ICU NG")
                .setAddress(parsed.getHost())
                .setPort(parsed.getPort() > 0 ? parsed.getPort() : cotProtocol.getDefaultPort())
                .setPath(path)
                .setProtocol(cotProtocol)
                .setSource(ConnectionEntry.Source.EXTERNAL);
        if (protocol == 2 && !passphrase.isEmpty()) {
            builder.setPassphrase(passphrase);
        }
        return builder.build();
    }

    @Override
    public void onConnectionStarted(String url) {
        setStatus(pluginContext.getString(R.string.status_connecting));
    }

    @Override
    public void onConnectionSuccess() {
        setStatus(pluginContext.getString(R.string.status_streaming));
    }

    @Override
    public void onConnectionFailed(String reason) {
        setStatus(pluginContext.getString(R.string.status_error_format, reason));
        stopStreamOnly();
    }

    @Override
    public void onDisconnect() {
        setStatus(pluginContext.getString(R.string.status_disconnected));
        stopStreamOnly();
    }

    @Override
    public void onAuthError() {
        setStatus(pluginContext.getString(R.string.status_error_format, "autenticacion fallida"));
        stopStreamOnly();
    }

    @Override
    public void onAuthSuccess() {
        setStatus(pluginContext.getString(R.string.status_streaming));
    }
}
