package com.carlosgt.icung.capture;

/**
 * Interface between this app and the ICU NG plugin inside ATAK. The plugin
 * keeps an identical copy (CaptureLink); change both together.
 *
 * Plugin -> app: an activity launch of {@link #CONTROL_ACTIVITY} with
 * {@link #ACTION_START} or {@link #ACTION_STOP}, made with
 * startActivityForResult so the app can check who is calling. An activity,
 * not a broadcast or service: Android only lets an app start a camera
 * foreground service while it is visible, and the brief transparent
 * activity is what makes it visible.
 *
 * App -> plugin: {@link #ACTION_STATUS} broadcasts sent to ATAK's package
 * on every change and every {@link #HEARTBEAT_MS} while live, so a plugin
 * that starts after the stream (ATAK restarted) picks it up.
 */
public final class CaptureContract {

    public static final String PACKAGE = "com.carlosgt.icung.capture";
    public static final String CONTROL_ACTIVITY = PACKAGE + ".ControlActivity";

    public static final String ACTION_START = PACKAGE + ".START";
    public static final String ACTION_STOP = PACKAGE + ".STOP";
    public static final String ACTION_STATUS = PACKAGE + ".STATUS";

    public static final String EXTRA_URL = "url";
    /** 0 RTSP, 1 RTSPS, 2 SRT - as the plugin's StreamSettings */
    public static final String EXTRA_PROTOCOL = "protocol";
    /** 0 back, 1 front */
    public static final String EXTRA_CAMERA = "camera";
    public static final String EXTRA_TRUST_ALL_CERTS = "trust_all_certs";
    public static final String EXTRA_SRT_PASSPHRASE = "srt_passphrase";
    /** 0 AES-128, 1 AES-192, 2 AES-256 */
    public static final String EXTRA_SRT_ENCRYPTION = "srt_encryption";
    /** display rotation in degrees, for the encoder orientation */
    public static final String EXTRA_ROTATION = "rotation";

    /** IDLE, CONNECTING, STREAMING or ERROR */
    public static final String EXTRA_STATE = "state";
    /** user-facing text for ERROR / retries, may be absent */
    public static final String EXTRA_MESSAGE = "message";

    public static final long HEARTBEAT_MS = 5000;

    /** ATAK packages allowed to control this app and told its status. */
    static final String[] ATAK_PACKAGES = { "com.atakmap.app.civ", "com.atakmap.app.mil" };

    private CaptureContract() {
    }

    static boolean isAtak(String pkg) {
        for (String p : ATAK_PACKAGES) {
            if (p.equals(pkg)) return true;
        }
        return false;
    }
}
