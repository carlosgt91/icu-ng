package com.carlosgt.atak.icung.plugin;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceView;
import android.view.WindowManager;

import com.atakmap.android.maps.MapView;
import com.pedro.common.ConnectChecker;
import com.pedro.encoder.input.sources.audio.MicrophoneSource;
import com.pedro.encoder.input.sources.video.Camera2Source;
import com.pedro.encoder.input.sources.video.VideoSource;
import com.pedro.encoder.input.video.CameraHelper;
import com.pedro.library.base.StreamBase;
import com.pedro.library.rtsp.RtspStream;
import com.pedro.library.srt.SrtStream;
import com.pedro.library.util.streamclient.RtspStreamClient;
import com.pedro.library.util.streamclient.SrtStreamClient;

import java.security.cert.X509Certificate;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import gov.tak.api.video.ConnectionEntry;
import gov.tak.api.video.IVideoConnectionManager;

/**
 * Single owner of the camera/encoder/stream, independent of any UI. The
 * pane, the REC map button and the self-marker radial button are all just
 * views onto this - none of them owns the stream, so it can be started from
 * the map without the pane ever being opened, and closing the pane never
 * affects a running stream.
 *
 * Uses RootEncoder's StreamBase API (RtspStream/SrtStream) instead of the
 * older Camera2Base: StreamBase streams with or without a preview attached
 * and lets the video source be swapped (Camera2Source / UVC), which the
 * Camera2Base classes cannot.
 *
 * All listener callbacks are delivered on the main thread - RootEncoder's
 * ConnectChecker fires from its network threads.
 *
 * Screen off / app switch: the plugin runs inside ATAK, whose only
 * foreground service is of type "location", so Android revokes the camera
 * whenever ATAK leaves the foreground. While live the screen is kept on so
 * it never times out by itself; when ATAK comes back after the power button
 * or another app, the camera is reopened, and a connection the server
 * dropped meanwhile is re-established.
 *
 * With the ICU NG Capture app installed, the stream runs in that app
 * instead (see CaptureLink): its own camera foreground service keeps
 * streaming with the screen off. This class then drives it, keeps the
 * CoT/video bookkeeping, works out the KLV and hands the pane's preview
 * surface over. While idle the pane preview is still drawn here.
 */
public class StreamController implements ConnectChecker {

    private static final String TAG = "IcuNgStream";

    public enum State { IDLE, CONNECTING, STREAMING, ERROR }

    public interface Listener {
        /** @param message user-facing text for ERROR, null otherwise */
        void onStreamStateChanged(State state, String message);
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

    private static final int VIDEO_WIDTH = 640;
    private static final int VIDEO_HEIGHT = 480;
    private static final int VIDEO_BITRATE = 1200 * 1024;
    private static final int VIDEO_FPS = 30;
    private static final int AUDIO_SAMPLE_RATE = 32000;
    private static final int AUDIO_BITRATE = 64 * 1024;

    private final Context atakContext;
    private final Context pluginContext;
    private final IVideoConnectionManager videoConnectionManager;
    private final SelfSensorFovController sensorFovController;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private StreamBase stream;
    private boolean streamIsSrt;
    private SurfaceView previewSurface;
    private State state = State.IDLE;
    private String stateMessage;
    private ConnectionEntry connectionEntry;
    private StreamSettings activeSettings;
    private KlvTelemetry klvTelemetry;
    private boolean atakStopped;
    private boolean cameraLostInBackground;
    private boolean reconnectOnForeground;
    // Link to the ICU NG Capture app, made once it is seen installed.
    private CaptureLink captureLink;
    /** a stream is live in the Capture app */
    private boolean remoteLive;
    // Until the app first reports the new stream live, an IDLE from it is
    // its state from before this start, not the end of this stream.
    private boolean remoteConfirmed;
    // A status in flight when the user stops must not bring it back.
    private long ignoreRemoteUntil;
    // After a stop the app holds the camera until it reports IDLE; only
    // then can the idle pane preview open it here again.
    private boolean awaitingRemoteRelease;

    private static final long REMOTE_START_TIMEOUT_MS = 15000;
    private static final long REMOTE_RELEASE_TIMEOUT_MS = 3000;

    private final Runnable remoteStartTimeout = new Runnable() {
        @Override
        public void run() {
            if (!remoteLive || remoteConfirmed) return;
            Log.w(TAG, "ICU NG Capture no ha confirmado la emision");
            captureLink.stop();
            endRemote();
            setState(State.ERROR, pluginContext.getString(R.string.status_error_capture_app));
        }
    };

    private final Runnable remoteReleaseTimeout = new Runnable() {
        @Override
        public void run() {
            if (!awaitingRemoteRelease) return;
            awaitingRemoteRelease = false;
            reattachLocalPreview();
        }
    };

    public StreamController(Context atakContext, Context pluginContext,
            IVideoConnectionManager videoConnectionManager,
            SelfSensorFovController sensorFovController) {
        this.atakContext = atakContext;
        this.pluginContext = pluginContext;
        this.videoConnectionManager = videoConnectionManager;
        this.sensorFovController = sensorFovController;
        ((Application) atakContext.getApplicationContext())
                .registerActivityLifecycleCallbacks(lifecycle);
        usesCaptureApp();
    }

    /**
     * Whether streams go through the ICU NG Capture app. Looked up again
     * while it is not found: it may be installed while ATAK runs.
     */
    public boolean usesCaptureApp() {
        if (captureLink == null && CaptureLink.isInstalled(atakContext)) {
            Log.i(TAG, "ICU NG Capture instalada: la emision se hace desde ella");
            captureLink = new CaptureLink(atakContext, this::onCaptureState);
            captureLink.bind();
        }
        return captureLink != null;
    }

    public void addListener(Listener l) {
        listeners.add(l);
        l.onStreamStateChanged(state, stateMessage);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public State getState() {
        return state;
    }

    public boolean isStreaming() {
        return remoteLive || (stream != null && stream.isStreaming());
    }

    public ConnectionEntry getConnectionEntry() {
        return connectionEntry;
    }

    /** Settings of the stream currently running, or null when stopped. */
    public StreamSettings getActiveSettings() {
        return isStreaming() ? activeSettings : null;
    }

    /** Starts streaming with the persisted settings. Must be called on the main thread. */
    public void start() {
        if (isStreaming()) return;

        StreamSettings settings = StreamSettings.load(atakContext);
        Integer error = settings.validate();
        if (error != null) {
            setState(State.ERROR, error == R.string.status_error_scheme_format
                    ? pluginContext.getString(error, settings.expectedScheme())
                    : pluginContext.getString(error));
            return;
        }

        if (usesCaptureApp()) {
            startRemote(settings);
            return;
        }

        try {
            startStream(settings);
        } catch (RuntimeException e) {
            // Whatever goes wrong inside the camera/GL/encoder stack, it must
            // end as an error on screen, never as an exception that takes
            // ATAK down with it.
            Log.e(TAG, "no se pudo iniciar la emision", e);
            failCamera();
        }
    }

    private void startStream(StreamSettings settings) {
        ensureStream(settings.protocol == StreamSettings.PROTOCOL_SRT);
        if (settings.protocol == StreamSettings.PROTOCOL_RTSPS) {
            ((RtspStreamClient) stream.getStreamClient())
                    .addCertificates(settings.trustAllCerts ? TRUST_ALL_CERTS : null);
        } else if (settings.protocol == StreamSettings.PROTOCOL_SRT
                && !settings.srtPassphrase.isEmpty()) {
            // SrtClient.setPassphrase() throws on anything outside 10-79
            // chars, empty included - empty means "no encryption".
            ((SrtStreamClient) stream.getStreamClient()).setPassphrase(
                    settings.srtPassphrase, settings.srtEncryptionType());
        }

        activeSettings = settings;
        // Before startStream(): the RTSP client decides whether to announce
        // the KLV track when it connects, from whether a supplier is set.
        startKlv(settings.camera);
        setState(State.CONNECTING, null);
        stream.startStream(settings.url);

        // RTSPS (protocol == 1) nunca se anuncia por CoT/IVideoConnectionManager:
        // verificado con javap que ConnectionEntryBase.Protocol no tiene una
        // constante RTSPS (solo RTSP/RTMP/RTMPS/SRT/...), y que Protocol.RTSP.toURL()
        // siempre construye literalmente "rtsp://" - anunciar RTSPS como
        // Protocol.RTSP le daria a otros ATAK una URL rota que su cliente de
        // Video no podria abrir contra un servidor que exige TLS. El push RTSPS
        // en si sigue funcionando igual - solo se deja de anunciar en el mapa.
        connectionEntry = settings.protocol == StreamSettings.PROTOCOL_RTSPS
                ? null : buildConnectionEntry(settings);
        if (connectionEntry != null) {
            videoConnectionManager.addConnectionEntry(connectionEntry);
        }
        sensorFovController.refresh(true);
    }

    private void startRemote(StreamSettings settings) {
        // The pane preview may be holding the camera the app is about to
        // open; the preview surface itself is kept, for the app to draw in.
        releaseLocalCamera();
        mainHandler.removeCallbacks(remoteReleaseTimeout);
        awaitingRemoteRelease = false;
        // The track is announced whatever the camera, so that switching
        // from USB to a phone camera mid-stream still gets KLV.
        boolean klvTrack = settings.klv && settings.protocol != StreamSettings.PROTOCOL_SRT;
        int rotation = CameraHelper.getCameraOrientation(atakContext);
        if (!captureLink.start(settings, klvTrack, rotation)) {
            setState(State.ERROR, pluginContext.getString(R.string.status_error_capture_app));
            return;
        }
        remoteConfirmed = false;
        mainHandler.postDelayed(remoteStartTimeout, REMOTE_START_TIMEOUT_MS);
        beginRemote(settings);
        setState(State.CONNECTING, null);
    }

    private void beginRemote(StreamSettings settings) {
        remoteLive = true;
        activeSettings = settings;
        connectionEntry = settings.protocol == StreamSettings.PROTOCOL_RTSPS
                ? null : buildConnectionEntry(settings);
        if (connectionEntry != null) {
            videoConnectionManager.addConnectionEntry(connectionEntry);
        }
        sensorFovController.refresh(true);
        startKlv(settings.camera);
        sendPreviewToApp();
    }

    private void endRemote() {
        mainHandler.removeCallbacks(remoteStartTimeout);
        remoteLive = false;
        stopKlv();
        if (connectionEntry != null) {
            videoConnectionManager.removeConnectionEntry(connectionEntry.getUID());
            connectionEntry = null;
        }
        activeSettings = null;
        sensorFovController.refresh(false);
    }

    private void sendPreviewToApp() {
        SurfaceView v = previewSurface;
        if (v == null || !v.getHolder().getSurface().isValid()) return;
        captureLink.setPreview(v.getHolder().getSurface(), v.getWidth(), v.getHeight());
    }

    private void releaseLocalCamera() {
        if (stream == null) return;
        if (stream.isOnPreview()) stream.stopPreview();
        releaseStream();
    }

    // The idle preview, back here once the app has let go of the camera.
    private void reattachLocalPreview() {
        SurfaceView v = previewSurface;
        if (v == null || isStreaming() || awaitingRemoteRelease || stream != null
                || !v.getHolder().getSurface().isValid()) return;
        attachPreview(v);
    }

    // Status from the Capture app, on the main thread. camera is -1 when
    // the app has gone away.
    private void onCaptureState(State remoteState, String message, int camera) {
        boolean live = remoteState == State.CONNECTING || remoteState == State.STREAMING;
        if (live) {
            if (!remoteLive) {
                // Running without us knowing: ATAK (or the plugin) restarted
                // while the app kept streaming. Take it over.
                if (System.currentTimeMillis() < ignoreRemoteUntil
                        || (stream != null && stream.isStreaming())) return;
                Log.i(TAG, "ICU NG Capture ya estaba emitiendo, se retoma");
                StreamSettings s = StreamSettings.load(atakContext);
                if (camera >= 0) s.camera = camera;
                releaseLocalCamera();
                beginRemote(s);
            }
            remoteConfirmed = true;
            mainHandler.removeCallbacks(remoteStartTimeout);
            if (remoteState != state || !java.util.Objects.equals(message, stateMessage)) {
                setState(remoteState, message);
            }
            return;
        }
        if (remoteLive && !remoteConfirmed && remoteState == State.IDLE && camera >= 0) {
            return; // the app's state from before this start
        }
        if (awaitingRemoteRelease) {
            awaitingRemoteRelease = false;
            mainHandler.removeCallbacks(remoteReleaseTimeout);
        }
        if (remoteLive) {
            endRemote();
            setState(remoteState, message);
        } else if (remoteState == State.ERROR) {
            setState(State.ERROR, message);
        }
        reattachLocalPreview();
    }

    /** Manual stop. Must be called on the main thread. */
    public void stop() {
        if (remoteLive) {
            ignoreRemoteUntil = System.currentTimeMillis() + 2000;
            awaitingRemoteRelease = true;
            mainHandler.postDelayed(remoteReleaseTimeout, REMOTE_RELEASE_TIMEOUT_MS);
            captureLink.stop();
            endRemote();
            setState(State.IDLE, null);
            return;
        }
        reconnectOnForeground = false;
        stopStreamOnly();
        setState(State.IDLE, null);
    }

    public void toggle() {
        if (isStreaming()) stop();
        else start();
    }

    /**
     * Attaches the pane's preview. Builds the stream for the persisted
     * protocol if there is none yet, so the user sees the camera as soon as
     * the pane opens.
     */
    public void attachPreview(SurfaceView surfaceView) {
        if (remoteLive || awaitingRemoteRelease) {
            // The app owns the camera: it draws into this surface.
            previewSurface = surfaceView;
            if (remoteLive) sendPreviewToApp();
            return;
        }
        if (stream == null) {
            ensureStream(StreamSettings.load(atakContext).protocol
                    == StreamSettings.PROTOCOL_SRT);
        }
        previewSurface = surfaceView;
        try {
            if (stream.isOnPreview()) stream.stopPreview();
            stream.startPreview(surfaceView);
        } catch (RuntimeException e) {
            Log.e(TAG, "no se pudo iniciar la vista previa", e);
            previewSurface = null;
            failCamera();
        }
    }

    // Tears everything down after a camera/encoder failure and reports it.
    private void failCamera() {
        try {
            stopStreamOnly();
        } catch (RuntimeException ignored) {
            // already broken - only cleanup left
        }
        try {
            releaseStream();
        } catch (RuntimeException ignored) {
            stream = null;
        }
        setState(State.ERROR, pluginContext.getString(R.string.status_error_camera));
    }

    /**
     * Detaches the pane's preview. If nothing is streaming, the camera is
     * released entirely - holding it open with no preview and no stream
     * would only drain battery and block other camera users.
     */
    public void detachPreview(SurfaceView surfaceView) {
        if (previewSurface != surfaceView) return;
        // Returns once the app no longer draws into it, so the surface may
        // then be destroyed.
        if (captureLink != null) captureLink.setPreview(null, 0, 0);
        if (stream == null) {
            previewSurface = null;
            return;
        }
        previewSurface = null;
        if (stream.isOnPreview()) stream.stopPreview();
        if (!stream.isStreaming()) releaseStream();
    }

    /**
     * Called when the user picks another protocol in the pane while idle:
     * RTSP and SRT need different StreamBase classes, so the preview is
     * moved onto a fresh instance of the right one.
     *
     * @return false if a stream is running (protocol cannot change then)
     */
    public boolean onProtocolChanged(int protocol) {
        boolean wantSrt = protocol == StreamSettings.PROTOCOL_SRT;
        if (stream == null || streamIsSrt == wantSrt) return true;
        if (stream.isStreaming()) return false;
        ensureStream(wantSrt); // carries the preview over to the new instance
        return true;
    }

    /**
     * Switches the video source (back / front / USB). Works mid-stream:
     * RootEncoder swaps the source under the running encoder, so viewers
     * see the new camera without the stream reconnecting.
     */
    public void setCamera(int camera) {
        if (remoteLive) {
            if (activeSettings == null) return;
            activeSettings.camera = camera;
            captureLink.setCamera(camera);
            startKlv(camera); // stops it for USB
            return;
        }
        if (stream == null) return; // picked up from the settings on next build
        stream.changeVideoSource(createVideoSource(camera));
        // The KLV track is already negotiated; just describe the new camera.
        if (klvTelemetry != null) startKlv(camera);
    }

    public void dispose() {
        ((Application) atakContext.getApplicationContext())
                .unregisterActivityLifecycleCallbacks(lifecycle);
        setKeepScreenOn(false);
        // A stream in the Capture app is left running: that is the point
        // of it. Its notification stops it.
        mainHandler.removeCallbacks(remoteStartTimeout);
        mainHandler.removeCallbacks(remoteReleaseTimeout);
        if (captureLink != null) captureLink.dispose();
        reconnectOnForeground = false;
        stopStreamOnly();
        releaseStream();
        listeners.clear();
    }

    private void ensureStream(boolean srt) {
        if (stream != null && streamIsSrt == srt) return;
        SurfaceView preview = previewSurface;
        if (stream != null) releaseStream();

        VideoSource videoSource = createVideoSource(StreamSettings.load(atakContext).camera);
        // The plugin context, not ATAK's: RootEncoder's GL pipeline loads its
        // shaders from its own raw resources, which live in the plugin APK.
        // With ATAK's context they don't resolve, GL init fails silently and
        // every camera ends up with no surface to draw into.
        stream = srt
                ? new SrtStream(pluginContext, this, videoSource, new MicrophoneSource())
                : new RtspStream(pluginContext, this, videoSource, new MicrophoneSource());
        streamIsSrt = srt;
        int rotation = CameraHelper.getCameraOrientation(atakContext);
        stream.prepareVideo(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_BITRATE, VIDEO_FPS, 2, rotation);
        stream.prepareAudio(AUDIO_SAMPLE_RATE, true, AUDIO_BITRATE);

        if (preview != null) {
            previewSurface = preview;
            stream.startPreview(preview);
        }
    }

    private void startKlv(int camera) {
        stopKlv();
        StreamSettings s = activeSettings;
        // KLV rides on the RTSP client only (RootEncoder's SRT muxer has no
        // data stream). Never for a USB camera: its pointing is unknown, and
        // the phone's attitude would describe the wrong camera.
        if (s == null || !s.klv || s.protocol == StreamSettings.PROTOCOL_SRT
                || camera == StreamSettings.CAMERA_USB) return;
        String[] cameras = pluginContext.getResources().getStringArray(R.array.camera_entries);
        klvTelemetry = new KlvTelemetry(atakContext, camera,
                pluginContext.getString(R.string.app_name) + " " + cameras[camera].toLowerCase());
        if (remoteLive) klvTelemetry.start(captureLink::sendKlv);
        else klvTelemetry.start();
    }

    private void stopKlv() {
        if (klvTelemetry != null) {
            klvTelemetry.stop();
            klvTelemetry = null;
        }
    }

    private VideoSource createVideoSource(int camera) {
        if (camera == StreamSettings.CAMERA_USB) {
            return new UsbCameraSource();
        }
        Camera2Source source = new Camera2Source(atakContext);
        // Not running yet, so this only flips which camera start() opens.
        if (camera == StreamSettings.CAMERA_FRONT) source.switchCamera();
        return source;
    }

    private void releaseStream() {
        if (stream == null) return;
        stream.release();
        stream = null;
    }

    // Everything that undoes start() - shared by the manual stop and by any
    // connection failure, so CoT/video bookkeeping never falls out of sync
    // with the actual stream state.
    private void stopStreamOnly() {
        if (stream != null && stream.isStreaming()) {
            stream.stopStream();
        }
        if (connectionEntry != null) {
            videoConnectionManager.removeConnectionEntry(connectionEntry.getUID());
            connectionEntry = null;
        }
        activeSettings = null;
        stopKlv();
        sensorFovController.refresh(false);
        // Nothing left to show the camera for.
        if (stream != null && previewSurface == null) releaseStream();
    }

    private ConnectionEntry buildConnectionEntry(StreamSettings settings) {
        android.net.Uri parsed = android.net.Uri.parse(settings.url);
        boolean srt = settings.protocol == StreamSettings.PROTOCOL_SRT;
        ConnectionEntry.Protocol cotProtocol = srt
                ? ConnectionEntry.Protocol.SRT
                : ConnectionEntry.Protocol.RTSP;

        String path = parsed.getQueryParameter("streamid");
        if (path == null || path.isEmpty()) {
            path = parsed.getPath();
        }

        ConnectionEntry.Builder builder = new ConnectionEntry.Builder()
                .setUID(pluginContext.getPackageName() + "." + System.currentTimeMillis())
                .setAlias(streamAlias())
                .setAddress(parsed.getHost())
                .setPort(parsed.getPort() > 0 ? parsed.getPort() : cotProtocol.getDefaultPort())
                .setPath(path)
                .setProtocol(cotProtocol)
                .setSource(ConnectionEntry.Source.EXTERNAL);
        if (srt && !settings.srtPassphrase.isEmpty()) {
            builder.setPassphrase(settings.srtPassphrase);
        }
        return builder.build();
    }

    // El callsign del usuario en ATAK, para que en la lista de Video de los
    // demas se vea de quien es cada emision (antes era siempre "ICU NG" y
    // dos moviles emitiendo a la vez salian con el mismo nombre).
    private String streamAlias() {
        String callsign = MapView.getMapView().getDeviceCallsign();
        return callsign == null || callsign.isEmpty()
                ? pluginContext.getString(R.string.app_name) : callsign;
    }

    private void setState(State newState, String message) {
        state = newState;
        stateMessage = message;
        // Not needed when the Capture app streams: it keeps going with the
        // screen off.
        setKeepScreenOn(!remoteLive
                && (newState == State.CONNECTING || newState == State.STREAMING));
        for (Listener l : listeners) {
            l.onStreamStateChanged(newState, message);
        }
    }

    private void setKeepScreenOn(boolean on) {
        Context c = MapView.getMapView() == null ? null : MapView.getMapView().getContext();
        if (!(c instanceof Activity)) return;
        if (on) {
            ((Activity) c).getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            ((Activity) c).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private boolean inBackground() {
        return atakStopped;
    }

    // ATAK's map activity - the one whose leaving the screen takes the camera.
    private static boolean isMapActivity(Activity activity) {
        return MapView.getMapView() != null && MapView.getMapView().getContext() == activity;
    }

    // A connection lost while ATAK is in the background is not reported as
    // an error: the camera is gone anyway, so it is re-established as soon
    // as ATAK is back in front ("Conectando..." meanwhile).
    private void onLostConnection(State reportedState, String message) {
        stopStreamOnly();
        if (inBackground()) {
            Log.i(TAG, "conexion perdida en segundo plano, se reconecta al volver");
            reconnectOnForeground = true;
            setState(State.CONNECTING, null);
        } else {
            setState(reportedState, message);
        }
    }

    private final Application.ActivityLifecycleCallbacks lifecycle =
            new Application.ActivityLifecycleCallbacks() {
        @Override
        public void onActivityStarted(Activity activity) {
            if (isMapActivity(activity)) atakStopped = false;
        }

        @Override
        public void onActivityStopped(Activity activity) {
            if (!isMapActivity(activity)) return;
            atakStopped = true;
            if (stream != null && stream.isStreaming()) cameraLostInBackground = true;
        }

        @Override
        public void onActivityResumed(Activity activity) {
            if (!isMapActivity(activity)) return;
            if (reconnectOnForeground) {
                reconnectOnForeground = false;
                cameraLostInBackground = false;
                Log.i(TAG, "ATAK en primer plano: reconectando");
                start();
            } else if (cameraLostInBackground) {
                cameraLostInBackground = false;
                reopenCamera();
            }
        }

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        }

        @Override
        public void onActivityPaused(Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
        }
    };

    // Android closed the phone camera while ATAK was in the background; the
    // encoder is still running, so a fresh source brings the picture back
    // without the viewers' stream reconnecting. A USB camera is not affected
    // (UVC goes through USB host, not the camera service).
    private void reopenCamera() {
        if (!isStreaming() || activeSettings == null
                || activeSettings.camera == StreamSettings.CAMERA_USB) return;
        Log.i(TAG, "ATAK en primer plano: reabriendo la camara");
        try {
            stream.changeVideoSource(createVideoSource(activeSettings.camera));
        } catch (RuntimeException e) {
            Log.e(TAG, "no se pudo reabrir la camara", e);
            failCamera();
        }
    }

    private void onMain(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) r.run();
        else mainHandler.post(r);
    }

    // Callbacks from the RootEncoder connection that is currently streaming
    // are the only ones that can change state: once stop() has run, a late
    // onDisconnect() from the cleanup of that stream (verified in logcat to
    // arrive shortly after stopStream()) must not overwrite the error or
    // idle state the user is looking at.

    @Override
    public void onConnectionStarted(String url) {
        onMain(() -> {
            if (isStreaming()) setState(State.CONNECTING, null);
        });
    }

    @Override
    public void onConnectionSuccess() {
        onMain(() -> {
            if (isStreaming()) setState(State.STREAMING, null);
        });
    }

    @Override
    public void onConnectionFailed(String reason) {
        Log.w(TAG, "Conexion fallida (detalle interno de RootEncoder): " + reason);
        onMain(() -> {
            if (!isStreaming()) return;
            onLostConnection(State.ERROR, pluginContext.getString(R.string.status_error_connection));
        });
    }

    @Override
    public void onDisconnect() {
        onMain(() -> {
            if (!isStreaming()) return;
            onLostConnection(State.IDLE, null);
        });
    }

    @Override
    public void onAuthError() {
        Log.w(TAG, "Fallo de autenticacion reportado por RootEncoder");
        onMain(() -> {
            if (!isStreaming()) return;
            stopStreamOnly();
            setState(State.ERROR, pluginContext.getString(R.string.status_error_auth));
        });
    }

    @Override
    public void onAuthSuccess() {
        onMain(() -> {
            if (isStreaming()) setState(State.STREAMING, null);
        });
    }
}
