package com.carlosgt.icung.capture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import com.pedro.common.ConnectChecker;
import com.pedro.encoder.input.sources.audio.MicrophoneSource;
import com.pedro.encoder.input.sources.video.Camera2Source;
import com.pedro.library.base.StreamBase;
import com.pedro.library.rtsp.RtspStream;
import com.pedro.library.srt.SrtStream;
import com.pedro.library.util.streamclient.RtspStreamClient;
import com.pedro.library.util.streamclient.SrtStreamClient;
import com.pedro.srt.srt.packets.control.handshake.EncryptionType;

import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Owns the camera, microphone and encoder while a stream runs, as a
 * foreground service of type camera|microphone: unlike the plugin inside
 * ATAK (whose only foreground service is of type location), it keeps both
 * with the screen off or another app in front.
 *
 * A dropped connection is retried every {@link #RETRY_MS} until stopped -
 * nobody may be looking at the phone to press Emitir again. Only an
 * authentication error ends the stream by itself.
 *
 * Everything runs on the main thread; RootEncoder's ConnectChecker
 * callbacks are posted there.
 */
public class CaptureService extends Service implements ConnectChecker {

    private static final String TAG = "IcuNgCapture";

    public enum State { IDLE, CONNECTING, STREAMING, ERROR }

    private static final String CHANNEL_ID = "stream";
    private static final int NOTIFICATION_ID = 1;
    private static final long RETRY_MS = 3000;

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

    // Last state, also answered when the service is not running (IDLE).
    private static State state = State.IDLE;
    private static String stateMessage;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private StreamBase stream;
    private Intent config;
    private PowerManager.WakeLock wakeLock;

    private final Runnable heartbeat = new Runnable() {
        @Override
        public void run() {
            broadcastStatus(CaptureService.this, state, stateMessage);
            mainHandler.postDelayed(this, CaptureContract.HEARTBEAT_MS);
        }
    };

    private final Runnable retry = this::connect;

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
        if (config != null && sameStream(config, intent)) {
            broadcastStatus(this, state, stateMessage); // already on it
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
        mainHandler.removeCallbacks(heartbeat);
        mainHandler.post(heartbeat);
        connect();
    }

    // A camera change while live comes as a new START: rebuilt, viewers
    // see a short reconnect.
    private static boolean sameStream(Intent a, Intent b) {
        String url = a.getStringExtra(CaptureContract.EXTRA_URL);
        return url != null && url.equals(b.getStringExtra(CaptureContract.EXTRA_URL))
                && a.getIntExtra(CaptureContract.EXTRA_CAMERA, 0)
                        == b.getIntExtra(CaptureContract.EXTRA_CAMERA, 0);
    }

    // (Re)builds the whole stream: after a failure RootEncoder's objects are
    // not reliably reusable, and a fresh camera also recovers one that the
    // system took away.
    private void connect() {
        mainHandler.removeCallbacks(retry);
        releaseStream();
        int protocol = config.getIntExtra(CaptureContract.EXTRA_PROTOCOL, 0);
        try {
            Camera2Source camera = new Camera2Source(this);
            if (config.getIntExtra(CaptureContract.EXTRA_CAMERA, 0) == 1) camera.switchCamera();
            stream = protocol == 2
                    ? new SrtStream(this, this, camera, new MicrophoneSource())
                    : new RtspStream(this, this, camera, new MicrophoneSource());
            stream.prepareVideo(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_BITRATE, VIDEO_FPS, 2,
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

    private void finish(State finalState, String message) {
        mainHandler.removeCallbacks(retry);
        mainHandler.removeCallbacks(heartbeat);
        releaseStream();
        config = null;
        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }
        setState(finalState, message);
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void releaseStream() {
        if (stream == null) return;
        try {
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
        broadcastStatus(this, newState, message);
        if (stream != null || newState == State.CONNECTING) {
            getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, buildNotification());
        }
    }

    static void broadcastStatus(Context context, State s, String message) {
        for (String pkg : CaptureContract.ATAK_PACKAGES) {
            Intent status = new Intent(CaptureContract.ACTION_STATUS)
                    .setPackage(pkg)
                    .putExtra(CaptureContract.EXTRA_STATE, s.name());
            if (message != null) status.putExtra(CaptureContract.EXTRA_MESSAGE, message);
            context.sendBroadcast(status);
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
        return null;
    }

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
            releaseStream();
            finish(State.ERROR, getString(R.string.error_auth));
        });
    }

    @Override
    public void onAuthSuccess() {
    }
}
