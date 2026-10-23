package com.carlosgt.atak.icung.plugin;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.atakmap.android.maps.MapView;

/**
 * Plugin side of the link to the ICU NG Capture app, which streams the
 * phone cameras from its own camera foreground service so the stream
 * survives the screen going off. Mirrors the app's CaptureContract - change
 * both together.
 *
 * Commands go as activity launches from ATAK's map activity, with
 * startActivityForResult so the app can verify they come from ATAK. Status
 * comes back as broadcasts, with a heartbeat while live: if it stops (the
 * app was killed) the stream is taken as ended.
 */
public class CaptureLink {

    private static final String TAG = "IcuNgCaptureLink";

    static final String PACKAGE = "com.carlosgt.icung.capture";
    private static final String CONTROL_ACTIVITY = PACKAGE + ".ControlActivity";
    private static final String ACTION_START = PACKAGE + ".START";
    private static final String ACTION_STOP = PACKAGE + ".STOP";
    private static final String ACTION_STATUS = PACKAGE + ".STATUS";

    private static final String EXTRA_URL = "url";
    private static final String EXTRA_PROTOCOL = "protocol";
    private static final String EXTRA_CAMERA = "camera";
    private static final String EXTRA_TRUST_ALL_CERTS = "trust_all_certs";
    private static final String EXTRA_SRT_PASSPHRASE = "srt_passphrase";
    private static final String EXTRA_SRT_ENCRYPTION = "srt_encryption";
    private static final String EXTRA_ROTATION = "rotation";
    private static final String EXTRA_STATE = "state";
    private static final String EXTRA_MESSAGE = "message";

    private static final long HEARTBEAT_MS = 5000;
    private static final long HEARTBEAT_TIMEOUT_MS = 3 * HEARTBEAT_MS;
    private static final int REQUEST_CODE = 0x1C0;

    public interface Listener {
        void onCaptureState(StreamController.State state, String message);
    }

    private final Context atakContext;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean watching;

    private final BroadcastReceiver statusReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            StreamController.State state;
            try {
                state = StreamController.State.valueOf(intent.getStringExtra(EXTRA_STATE));
            } catch (RuntimeException e) {
                return;
            }
            boolean live = state == StreamController.State.CONNECTING
                    || state == StreamController.State.STREAMING;
            mainHandler.removeCallbacks(heartbeatLost);
            if (live) mainHandler.postDelayed(heartbeatLost, HEARTBEAT_TIMEOUT_MS);
            listener.onCaptureState(state, intent.getStringExtra(EXTRA_MESSAGE));
        }
    };

    private final Runnable heartbeatLost = new Runnable() {
        @Override
        public void run() {
            Log.w(TAG, "ICU NG Capture dejo de dar senales");
            listener.onCaptureState(StreamController.State.IDLE, null);
        }
    };

    public CaptureLink(Context atakContext, Listener listener) {
        this.atakContext = atakContext;
        this.listener = listener;
    }

    /** Whether the Capture app is installed (and visible to ATAK). */
    public static boolean isInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo(PACKAGE, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    public void watch() {
        if (watching) return;
        IntentFilter filter = new IntentFilter(ACTION_STATUS);
        // Sent by another app, so the receiver has to be exported.
        if (Build.VERSION.SDK_INT >= 33) {
            atakContext.registerReceiver(statusReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            atakContext.registerReceiver(statusReceiver, filter);
        }
        watching = true;
    }

    public void dispose() {
        mainHandler.removeCallbacks(heartbeatLost);
        if (watching) atakContext.unregisterReceiver(statusReceiver);
        watching = false;
    }

    /** @return false if the app could not be reached */
    public boolean start(StreamSettings s, int rotation) {
        Intent intent = command(ACTION_START)
                .putExtra(EXTRA_URL, s.url)
                .putExtra(EXTRA_PROTOCOL, s.protocol)
                .putExtra(EXTRA_CAMERA, s.camera)
                .putExtra(EXTRA_TRUST_ALL_CERTS, s.trustAllCerts)
                .putExtra(EXTRA_SRT_PASSPHRASE, s.srtPassphrase)
                .putExtra(EXTRA_SRT_ENCRYPTION, s.srtEncryptionIndex)
                .putExtra(EXTRA_ROTATION, rotation);
        boolean sent = send(intent);
        if (sent) mainHandler.postDelayed(heartbeatLost, HEARTBEAT_TIMEOUT_MS);
        return sent;
    }

    public void stop() {
        mainHandler.removeCallbacks(heartbeatLost);
        send(command(ACTION_STOP));
    }

    private static Intent command(String action) {
        return new Intent(action).setComponent(new ComponentName(PACKAGE, CONTROL_ACTIVITY));
    }

    private boolean send(Intent intent) {
        Context c = MapView.getMapView() == null ? null : MapView.getMapView().getContext();
        if (!(c instanceof Activity)) return false;
        try {
            ((Activity) c).startActivityForResult(intent, REQUEST_CODE);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            Log.e(TAG, "no se pudo abrir ICU NG Capture", e);
            return false;
        }
    }
}
