package com.carlosgt.atak.icung.plugin;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.view.Surface;

import com.atakmap.android.maps.MapView;
import com.carlosgt.icung.capture.ICaptureListener;
import com.carlosgt.icung.capture.ICaptureService;

/**
 * Plugin side of the link to the ICU NG Capture app, which streams from its
 * own camera foreground service so the stream survives the screen going
 * off. Mirrors the app's CaptureContract and AIDL files - change both
 * together.
 *
 * Start/stop go as activity launches from ATAK's map activity (the app may
 * only start a camera service while visible), with startActivityForResult
 * so the app can verify they come from ATAK. Status, the pane preview and
 * KLV go through the bound ICaptureService; if the app dies the binding
 * drops and the stream is taken as ended.
 */
public class CaptureLink {

    private static final String TAG = "IcuNgCaptureLink";

    static final String PACKAGE = "com.carlosgt.icung.capture";
    private static final String CONTROL_ACTIVITY = PACKAGE + ".ControlActivity";
    private static final String SERVICE = PACKAGE + ".CaptureService";
    private static final String ACTION_START = PACKAGE + ".START";
    private static final String ACTION_STOP = PACKAGE + ".STOP";
    private static final String ACTION_BIND = PACKAGE + ".BIND";

    private static final String EXTRA_URL = "url";
    private static final String EXTRA_PROTOCOL = "protocol";
    private static final String EXTRA_VIDEO_CODEC = "video_codec";
    private static final String EXTRA_CAMERA = "camera";
    private static final String EXTRA_TRUST_ALL_CERTS = "trust_all_certs";
    private static final String EXTRA_SRT_PASSPHRASE = "srt_passphrase";
    private static final String EXTRA_SRT_ENCRYPTION = "srt_encryption";
    private static final String EXTRA_KLV = "klv";
    private static final String EXTRA_ROTATION = "rotation";
    private static final String EXTRA_WIDTH = "width";
    private static final String EXTRA_HEIGHT = "height";
    private static final String EXTRA_FPS = "fps";
    private static final String EXTRA_BITRATE = "bitrate";
    private static final String EXTRA_ADAPTIVE_BITRATE = "adaptive_bitrate";
    private static final String EXTRA_MUTED = "muted";

    private static final int REQUEST_CODE = 0x1C0;

    public interface Listener {
        void onCaptureState(StreamController.State state, String message, int camera);
    }

    private final Context atakContext;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ICaptureService service;
    private boolean bound;
    // Preview asked for before the binding came up.
    private Surface pendingSurface;
    private int pendingWidth;
    private int pendingHeight;

    private final ICaptureListener.Stub statusListener = new ICaptureListener.Stub() {
        @Override
        public void onStatus(String state, String message, int camera) {
            StreamController.State s;
            try {
                s = StreamController.State.valueOf(state);
            } catch (RuntimeException e) {
                return;
            }
            mainHandler.post(() -> listener.onCaptureState(s, message, camera));
        }
    };

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = ICaptureService.Stub.asInterface(binder);
            try {
                service.register(statusListener);
                if (pendingSurface != null) {
                    service.setPreview(pendingSurface, pendingWidth, pendingHeight);
                }
            } catch (RemoteException | SecurityException e) {
                Log.e(TAG, "ICU NG Capture rechazo la conexion", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // The app died; Android reconnects when it comes back.
            Log.w(TAG, "ICU NG Capture se ha cerrado");
            service = null;
            listener.onCaptureState(StreamController.State.IDLE, null, -1);
        }

        @Override
        public void onBindingDied(ComponentName name) {
            // The app was updated or reinstalled: this binding never comes
            // back by itself.
            Log.w(TAG, "enlace con ICU NG Capture perdido, se rehace");
            service = null;
            if (bound) atakContext.unbindService(this);
            bound = false;
            listener.onCaptureState(StreamController.State.IDLE, null, -1);
            bind();
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

    public void bind() {
        if (bound) return;
        Intent intent = new Intent(ACTION_BIND).setComponent(new ComponentName(PACKAGE, SERVICE));
        try {
            bound = atakContext.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        } catch (SecurityException e) {
            Log.e(TAG, "no se pudo enlazar con ICU NG Capture", e);
        }
        if (!bound) Log.w(TAG, "ICU NG Capture no acepta el enlace");
    }

    public void dispose() {
        if (service != null) {
            try {
                service.unregister(statusListener);
            } catch (RemoteException | SecurityException ignored) {
                // going away anyway
            }
        }
        if (bound) atakContext.unbindService(connection);
        bound = false;
        service = null;
    }

    /** @return false if the app could not be reached */
    public boolean start(StreamSettings s, boolean klv, int rotation) {
        bind();
        Intent intent = command(ACTION_START)
                .putExtra(EXTRA_URL, s.url)
                .putExtra(EXTRA_PROTOCOL, s.protocol)
                .putExtra(EXTRA_VIDEO_CODEC, s.codec)
                .putExtra(EXTRA_CAMERA, s.camera)
                .putExtra(EXTRA_TRUST_ALL_CERTS, s.trustAllCerts)
                .putExtra(EXTRA_SRT_PASSPHRASE, s.srtPassphrase)
                .putExtra(EXTRA_SRT_ENCRYPTION, s.srtEncryptionIndex)
                .putExtra(EXTRA_KLV, klv)
                .putExtra(EXTRA_ROTATION, rotation)
                .putExtra(EXTRA_WIDTH, s.videoWidth())
                .putExtra(EXTRA_HEIGHT, s.videoHeight())
                .putExtra(EXTRA_FPS, s.fps())
                .putExtra(EXTRA_BITRATE, s.videoBitrate())
                .putExtra(EXTRA_ADAPTIVE_BITRATE, s.adaptiveBitrate)
                .putExtra(EXTRA_MUTED, !s.microphone);
        return send(intent);
    }

    public void stop() {
        send(command(ACTION_STOP));
    }

    /**
     * Where the app draws the camera while live; null to stop. Returns
     * once the app has let go of the previous surface.
     */
    public void setPreview(Surface surface, int width, int height) {
        pendingSurface = surface;
        pendingWidth = width;
        pendingHeight = height;
        if (service == null) {
            Log.i(TAG, "vista previa pendiente: aun sin enlace con ICU NG Capture");
            return;
        }
        try {
            service.setPreview(surface, width, height);
        } catch (RemoteException | SecurityException e) {
            Log.w(TAG, "no se pudo pasar la vista previa", e);
        }
    }

    /** Bitrate (bits/s), adaptive bitrate and microphone of the running stream. */
    public void setLiveOptions(int bitrate, boolean adaptive, boolean muted) {
        if (service == null) return;
        try {
            service.setLiveOptions(bitrate, adaptive, muted);
        } catch (RemoteException | SecurityException e) {
            Log.w(TAG, "no se pudieron cambiar las opciones", e);
        }
    }

    /** Swaps the camera of the running stream without reconnecting. */
    public void setCamera(int camera) {
        if (service == null) return;
        try {
            service.setCamera(camera);
        } catch (RemoteException | SecurityException e) {
            Log.w(TAG, "no se pudo cambiar de camara", e);
        }
    }

    public void sendKlv(byte[] packet) {
        if (service == null) return;
        try {
            service.klv(packet);
        } catch (RemoteException | SecurityException e) {
            Log.w(TAG, "no se pudo pasar el KLV", e);
        }
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
