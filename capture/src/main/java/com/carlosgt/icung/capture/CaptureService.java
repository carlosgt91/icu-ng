package com.carlosgt.icung.capture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.util.Log;
import android.view.Surface;

import com.pedro.common.ConnectChecker;
import com.pedro.common.VideoCodec;
import com.pedro.encoder.input.sources.audio.MicrophoneSource;
import com.pedro.encoder.input.sources.video.Camera2Source;
import com.pedro.encoder.input.sources.video.VideoSource;
import com.pedro.library.base.StreamBase;
import com.pedro.library.rtsp.RtspStream;
import com.pedro.library.srt.SrtStream;
import com.pedro.library.util.BitrateAdapter;
import com.pedro.library.util.streamclient.RtspStreamClient;
import com.pedro.library.util.streamclient.SrtStreamClient;
import com.pedro.rtsp.rtsp.klv.KlvMetadata;
import com.pedro.srt.srt.packets.control.handshake.EncryptionType;

import java.security.cert.X509Certificate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Owns the camera, microphone and encoder while a stream runs, as a
 * foreground service of type camera|microphone: unlike the plugin inside
 * ATAK (whose only foreground service is of type location), it keeps both
 * with the screen off or another app in front.
 *
 * Started (by ControlActivity) for a stream, bound (by the plugin) for
 * everything else: status, the pane preview and KLV packets. Every binder
 * call is checked to come from ATAK.
 *
 * A dropped connection is retried every {@link #RETRY_MS} until stopped -
 * nobody may be looking at the phone to press Emitir again. Only an
 * authentication error ends the stream by itself.
 *
 * Everything runs on the main thread: binder calls and RootEncoder's
 * ConnectChecker callbacks are posted there.
 */
public class CaptureService extends Service implements ConnectChecker {

    private static final String TAG = "IcuNgCapture";

    public enum State { IDLE, CONNECTING, STREAMING, ERROR }

    private static final String CHANNEL_ID = "stream";
    private static final int NOTIFICATION_ID = 1;
    private static final long RETRY_MS = 3000;
    // A packet older than this describes where the phone was, not is.
    private static final long KLV_MAX_AGE_MS = 3000;

    // Defaults when the plugin sends no quality (older plugin).
    private static final int VIDEO_WIDTH = 640;
    private static final int VIDEO_HEIGHT = 480;
    private static final int VIDEO_BITRATE = 1200 * 1024;
    private static final int VIDEO_FPS = 30;
    private static final int AUDIO_SAMPLE_RATE = 32000;
    private static final int AUDIO_BITRATE = 64 * 1024;

    private static final EncryptionType[] SRT_ENCRYPTION_TYPES = {
            EncryptionType.AES128, EncryptionType.AES192, EncryptionType.AES256
    };

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

    // Process-wide so ControlActivity can report too (a permission refused
    // before the service ever started).
    private static final RemoteCallbackList<ICaptureListener> listeners =
            new RemoteCallbackList<>();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static State state = State.IDLE;
    private static String stateMessage;
    private static int stateCamera;

    private StreamBase stream;
    private MicrophoneSource microphone;
    private BitrateAdapter bitrateAdapter;
    private volatile boolean adaptiveBitrate;
    private Intent config;
    private PowerManager.WakeLock wakeLock;
    private Surface previewSurface;
    private int previewWidth;
    private int previewHeight;

    private volatile byte[] klvPacket;
    private volatile long klvPacketAt;

    private final Runnable retry = this::connect;

    // Hands the plugin's latest packet to the RTSP sender once. Called on
    // the sender thread.
    private final KlvMetadata.KlvSupplier klvSupplier = () -> {
        byte[] p = klvPacket;
        if (p == null) return null;
        klvPacket = null;
        return System.currentTimeMillis() - klvPacketAt > KLV_MAX_AGE_MS ? null : p;
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (CaptureContract.ACTION_START.equals(action)) {
            start(intent);
        } else {
            // STOP, or a restart by the system after a crash (null intent):
            // a stream is never resumed without someone asking for it.
            stop();
        }
        return START_NOT_STICKY;
    }

    private void start(Intent intent) {
        goForeground();
        if (config != null && sameConnection(config, intent)) {
            int camera = camera(intent);
            if (camera != camera(config)) switchCamera(intent);
            else notifyListeners();
            return;
        }
        releaseStream();
        config = intent;
        if (wakeLock == null) {
            // The encoder and the socket must keep running with the screen
            // off; the foreground service alone does not keep the CPU awake.
            wakeLock = ((PowerManager) getSystemService(POWER_SERVICE))
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "icung:capture");
            wakeLock.acquire();
        }
        connect();
    }

    private static boolean sameConnection(Intent a, Intent b) {
        String url = a.getStringExtra(CaptureContract.EXTRA_URL);
        return url != null && url.equals(b.getStringExtra(CaptureContract.EXTRA_URL))
                && a.getIntExtra(CaptureContract.EXTRA_PROTOCOL, 0)
                        == b.getIntExtra(CaptureContract.EXTRA_PROTOCOL, 0)
                && a.getIntExtra(CaptureContract.EXTRA_VIDEO_CODEC, 0)
                        == b.getIntExtra(CaptureContract.EXTRA_VIDEO_CODEC, 0);
    }

    // true when the URL carries userinfo (user:password@host), the only place
    // RootEncoder takes RTSP credentials from
    private static boolean urlHasCredentials(String url) {
        if (url == null) return false;
        int start = url.indexOf("://");
        if (start < 0) return false;
        int end = start + 3;
        while (end < url.length() && "/?#".indexOf(url.charAt(end)) < 0) end++;
        return url.substring(start + 3, end).indexOf('@') >= 0;
    }

    private static int camera(Intent i) {
        return i.getIntExtra(CaptureContract.EXTRA_CAMERA, CaptureContract.CAMERA_BACK);
    }

    // Swaps the source under the running encoder: viewers see the new
    // camera without the stream reconnecting.
    private void switchCamera(Intent intent) {
        config = intent;
        if (stream == null) return; // waiting to retry: connect() uses config
        try {
            stream.changeVideoSource(createVideoSource(camera(intent)));
            setState(state, stateMessage);
        } catch (RuntimeException e) {
            Log.e(TAG, "no se pudo cambiar de camara", e);
            finish(State.ERROR, getString(R.string.error_camera));
        }
    }

    private VideoSource createVideoSource(int camera) {
        if (camera == CaptureContract.CAMERA_USB) return new UsbCameraSource();
        Camera2Source source = new Camera2Source(this);
        // Not running yet, so this only flips which camera start() opens.
        if (camera == CaptureContract.CAMERA_FRONT) source.switchCamera();
        return source;
    }

    // (Re)builds the whole stream: after a failure RootEncoder's objects are
    // not reliably reusable, and a fresh camera also recovers one that the
    // system took away.
    private void connect() {
        mainHandler.removeCallbacks(retry);
        releaseStream();
        int protocol = config.getIntExtra(CaptureContract.EXTRA_PROTOCOL, 0);
        try {
            VideoSource camera = createVideoSource(camera(config));
            microphone = new MicrophoneSource();
            stream = protocol == 2
                    ? new SrtStream(this, this, camera, microphone)
                    : new RtspStream(this, this, camera, microphone);
            int codec = config.getIntExtra(CaptureContract.EXTRA_VIDEO_CODEC, CaptureContract.CODEC_H264);
            if (codec == CaptureContract.CODEC_H265) {
                stream.setVideoCodec(VideoCodec.H265);
            } else {
                stream.setVideoCodec(VideoCodec.H264);
            }
            int bitrate = config.getIntExtra(CaptureContract.EXTRA_BITRATE, VIDEO_BITRATE);
            stream.prepareVideo(config.getIntExtra(CaptureContract.EXTRA_WIDTH, VIDEO_WIDTH),
                    config.getIntExtra(CaptureContract.EXTRA_HEIGHT, VIDEO_HEIGHT), bitrate,
                    config.getIntExtra(CaptureContract.EXTRA_FPS, VIDEO_FPS), 2,
                    config.getIntExtra(CaptureContract.EXTRA_ROTATION, 0));
            stream.prepareAudio(AUDIO_SAMPLE_RATE, true, AUDIO_BITRATE);

            if (protocol == 1) {
                ((RtspStreamClient) stream.getStreamClient()).addCertificates(
                        config.getBooleanExtra(CaptureContract.EXTRA_TRUST_ALL_CERTS, false)
                                ? TRUST_ALL_CERTS : null);
            } else if (protocol == 2) {
                String passphrase = config.getStringExtra(CaptureContract.EXTRA_SRT_PASSPHRASE);
                if (passphrase != null && !passphrase.isEmpty()) {
                    int enc = Math.max(0, Math.min(SRT_ENCRYPTION_TYPES.length - 1,
                            config.getIntExtra(CaptureContract.EXTRA_SRT_ENCRYPTION, 0)));
                    ((SrtStreamClient) stream.getStreamClient())
                            .setPassphrase(passphrase, SRT_ENCRYPTION_TYPES[enc]);
                }
            }
            // Before startStream(): the RTSP client decides whether to
            // announce the KLV track when it connects, from whether a
            // supplier is set. RootEncoder's SRT muxer has no data stream.
            boolean klv = protocol != 2
                    && config.getBooleanExtra(CaptureContract.EXTRA_KLV, false);
            KlvMetadata.setSupplier(klv ? klvSupplier : null);

            if (previewSurface != null && previewSurface.isValid()) {
                stream.startPreview(previewSurface, previewWidth, previewHeight);
                Log.i(TAG, "vista previa en el panel de ATAK " + previewWidth + "x" + previewHeight);
            }
            applyLiveOptions(bitrate,
                    config.getBooleanExtra(CaptureContract.EXTRA_ADAPTIVE_BITRATE, true),
                    config.getBooleanExtra(CaptureContract.EXTRA_MUTED, false));
            setState(State.CONNECTING, null);
            stream.startStream(config.getStringExtra(CaptureContract.EXTRA_URL));
        } catch (RuntimeException e) {
            // Camera/GL/encoder failures must end as an error, not a crash.
            Log.e(TAG, "no se pudo iniciar la emision", e);
            releaseStream();
            finish(State.ERROR, getString(R.string.error_camera));
        }
    }

    private void stop() {
        finish(State.IDLE, null);
    }

    // On the main thread. Also remembered in config, for the rebuild after
    // a dropped connection.
    private void applyLiveOptions(int bitrate, boolean adaptive, boolean muted) {
        if (config != null) {
            config.putExtra(CaptureContract.EXTRA_BITRATE, bitrate)
                    .putExtra(CaptureContract.EXTRA_ADAPTIVE_BITRATE, adaptive)
                    .putExtra(CaptureContract.EXTRA_MUTED, muted);
        }
        if (stream == null) return;
        Log.i(TAG, "opciones: " + bitrate / 1024 + " kbps, adaptativo " + adaptive
                + ", microfono " + (muted ? "silenciado" : "activo"));
        if (muted) microphone.mute();
        else microphone.unMute();
        adaptiveBitrate = adaptive;
        bitrateAdapter = new BitrateAdapter(b -> {
            StreamBase st = stream;
            if (st != null) st.setVideoBitrateOnFly(b);
        });
        bitrateAdapter.setMaxBitrate(bitrate);
        if (stream.isStreaming()) stream.setVideoBitrateOnFly(bitrate);
    }

    // RootEncoder's measured upload rate, about once a second, on its
    // network thread: the adapter lowers the encoder bitrate while the link
    // is congested (VPN, mobile data) and raises it back towards the chosen one.
    @Override
    public void onNewBitrate(long bitrate) {
        BitrateAdapter adapter = bitrateAdapter;
        StreamBase st = stream;
        if (!adaptiveBitrate || adapter == null || st == null) return;
        adapter.adaptBitrate(bitrate, st.getStreamClient().hasCongestion());
    }

    private void finish(State finalState, String message) {
        mainHandler.removeCallbacks(retry);
        releaseStream();
        config = null;
        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }
        setState(finalState, message);
        forgetError();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void releaseStream() {
        if (KlvMetadata.getSupplier() == klvSupplier) KlvMetadata.setSupplier(null);
        if (stream == null) return;
        try {
            if (stream.isOnPreview()) stream.stopPreview();
            if (stream.isStreaming()) stream.stopStream();
            stream.release();
        } catch (RuntimeException e) {
            Log.w(TAG, "error liberando la emision", e);
        }
        stream = null;
    }

    private void retryLater(String message) {
        releaseStream();
        setState(State.CONNECTING, message);
        mainHandler.removeCallbacks(retry);
        mainHandler.postDelayed(retry, RETRY_MS);
    }

    private void setState(State newState, String message) {
        state = newState;
        stateMessage = message;
        stateCamera = config != null ? camera(config) : stateCamera;
        notifyListeners();
        if (config != null) {
            getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, buildNotification());
        }
    }

    /** For ControlActivity: a failure before any stream existed. */
    static void reportError(String message) {
        state = State.ERROR;
        stateMessage = message;
        notifyListeners();
        forgetError();
    }

    // An error is told once, to whoever is listening; a plugin that
    // registers later (ATAK restarted) must not show it as current.
    private static void forgetError() {
        if (state != State.ERROR) return;
        state = State.IDLE;
        stateMessage = null;
    }

    private static void notifyListeners() {
        int n = listeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                listeners.getBroadcastItem(i).onStatus(state.name(), stateMessage, stateCamera);
            } catch (RemoteException ignored) {
                // gone; RemoteCallbackList drops it
            }
        }
        listeners.finishBroadcast();
    }

    // Preview changes, on the main thread.
    private void applyPreview(Surface surface, int width, int height) {
        if (stream != null && stream.isOnPreview()) stream.stopPreview();
        previewSurface = surface;
        previewWidth = width;
        previewHeight = height;
        if (stream != null && surface != null && surface.isValid()) {
            try {
                stream.startPreview(surface, width, height);
                Log.i(TAG, "vista previa en el panel de ATAK " + width + "x" + height);
            } catch (RuntimeException e) {
                Log.w(TAG, "no se pudo mostrar la vista previa", e);
            }
        }
    }

    private void goForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        // Default importance, not low: a live camera and microphone must be
        // in plain view, not folded away with the silent notifications.
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT);
        channel.setSound(null, null);
        channel.enableVibration(false);
        nm.createNotificationChannel(channel);
        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                    | ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    private Notification buildNotification() {
        PendingIntent stopIntent = PendingIntent.getService(this, 0,
                new Intent(this, CaptureService.class).setAction(CaptureContract.ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE);
        String text = state == State.STREAMING ? getString(R.string.notif_streaming)
                : stateMessage != null ? stateMessage : getString(R.string.notif_connecting);
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_capture)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null,
                        getString(R.string.notif_stop), stopIntent).build())
                .build();
    }

    @Override
    public void onDestroy() {
        if (stream != null || config != null) finish(State.IDLE, null);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // ATAK went away: nothing left to draw the preview into.
        mainHandler.post(() -> applyPreview(null, 0, 0));
        return false;
    }

    // Only ATAK may use the binder: anything else could watch the camera
    // through setPreview or feed false positions into the KLV.
    private void checkCaller() {
        String[] pkgs = getPackageManager().getPackagesForUid(Binder.getCallingUid());
        if (pkgs != null) {
            for (String p : pkgs) {
                if (CaptureContract.isAtak(p)) return;
            }
        }
        throw new SecurityException("ICU NG Capture solo acepta llamadas de ATAK");
    }

    private final ICaptureService.Stub binder = new ICaptureService.Stub() {
        @Override
        public void register(ICaptureListener listener) {
            checkCaller();
            listeners.register(listener);
            mainHandler.post(CaptureService::notifyListeners);
        }

        @Override
        public void unregister(ICaptureListener listener) {
            checkCaller();
            listeners.unregister(listener);
        }

        @Override
        public void setPreview(Surface surface, int width, int height) {
            checkCaller();
            CountDownLatch done = new CountDownLatch(1);
            mainHandler.post(() -> {
                applyPreview(surface, width, height);
                done.countDown();
            });
            try {
                done.await(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void setCamera(int camera) {
            checkCaller();
            mainHandler.post(() -> {
                if (config == null || camera == camera(config)) return;
                switchCamera(new Intent(config).putExtra(CaptureContract.EXTRA_CAMERA, camera));
            });
        }

        @Override
        public void setLiveOptions(int bitrate, boolean adaptive, boolean muted) {
            checkCaller();
            mainHandler.post(() -> applyLiveOptions(bitrate, adaptive, muted));
        }

        @Override
        public void klv(byte[] packet) {
            checkCaller();
            klvPacketAt = System.currentTimeMillis();
            klvPacket = packet;
        }
    };

    // Callbacks from a stream that has since been replaced or released are
    // dropped: RootEncoder reports the disconnect of its own cleanup.

    private void onMain(StreamBase from, Runnable r) {
        mainHandler.post(() -> {
            if (from == stream && stream != null) r.run();
        });
    }

    @Override
    public void onConnectionStarted(String url) {
    }

    @Override
    public void onConnectionSuccess() {
        StreamBase from = stream;
        onMain(from, () -> setState(State.STREAMING, null));
    }

    @Override
    public void onConnectionFailed(String reason) {
        Log.w(TAG, "conexion fallida: " + reason);
        StreamBase from = stream;
        onMain(from, () -> retryLater(getString(R.string.status_retrying)));
    }

    @Override
    public void onDisconnect() {
        StreamBase from = stream;
        onMain(from, () -> retryLater(getString(R.string.status_retrying)));
    }

    @Override
    public void onAuthError() {
        StreamBase from = stream;
        onMain(from, () -> {
            boolean missing = config != null && !urlHasCredentials(
                    config.getStringExtra(CaptureContract.EXTRA_URL));
            finish(State.ERROR, getString(missing
                    ? R.string.error_auth_missing : R.string.error_auth));
        });
    }

    @Override
    public void onAuthSuccess() {
    }
}
