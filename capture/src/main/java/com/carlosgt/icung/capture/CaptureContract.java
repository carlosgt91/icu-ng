package com.carlosgt.icung.capture;

/**
 * Interface between this app and the ICU NG plugin inside ATAK. The plugin
 * keeps an identical copy of these constants (CaptureLink) and of the AIDL
 * files; change both together.
 *
 * Start/stop: an activity launch of {@link #CONTROL_ACTIVITY} with
 * {@link #ACTION_START} or {@link #ACTION_STOP}, made with
 * startActivityForResult so the app can check who is calling. An activity,
 * not a service call: Android only lets an app start a camera foreground
 * service while it is visible, and the brief transparent activity is what
 * makes it visible.
 *
 * Everything else - status, the pane preview, KLV - goes through
 * {@link ICaptureService}, which the plugin binds with {@link #ACTION_BIND}.
 */
public final class CaptureContract {

    public static final String PACKAGE = "com.carlosgt.icung.capture";
    public static final String CONTROL_ACTIVITY = PACKAGE + ".ControlActivity";

    public static final String ACTION_START = PACKAGE + ".START";
    public static final String ACTION_STOP = PACKAGE + ".STOP";
    public static final String ACTION_BIND = PACKAGE + ".BIND";

    public static final String EXTRA_URL = "url";
    /** 0 RTSP, 1 RTSPS, 2 SRT - as the plugin's StreamSettings */
    public static final String EXTRA_PROTOCOL = "protocol";
    /** 0 back, 1 front, 2 USB */
    public static final String EXTRA_CAMERA = "camera";
    public static final String EXTRA_TRUST_ALL_CERTS = "trust_all_certs";
    public static final String EXTRA_SRT_PASSPHRASE = "srt_passphrase";
    /** 0 AES-128, 1 AES-192, 2 AES-256 */
    public static final String EXTRA_SRT_ENCRYPTION = "srt_encryption";
    /** announce a KLV track (RTSP/RTSPS); the packets come via klv() */
    public static final String EXTRA_KLV = "klv";
    /** display rotation in degrees, for the encoder orientation */
    public static final String EXTRA_ROTATION = "rotation";
    public static final String EXTRA_WIDTH = "width";
    public static final String EXTRA_HEIGHT = "height";
    public static final String EXTRA_FPS = "fps";
    /** video bits/s; the ceiling when adaptive */
    public static final String EXTRA_BITRATE = "bitrate";
    /** lower the bitrate while the network is congested */
    public static final String EXTRA_ADAPTIVE_BITRATE = "adaptive_bitrate";
    /** send silence instead of the microphone */
    public static final String EXTRA_MUTED = "muted";

    public static final int CAMERA_BACK = 0;
    public static final int CAMERA_FRONT = 1;
    public static final int CAMERA_USB = 2;

    /** ATAK packages allowed to control this app. */
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
