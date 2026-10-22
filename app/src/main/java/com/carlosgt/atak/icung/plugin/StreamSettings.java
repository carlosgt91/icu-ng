package com.carlosgt.atak.icung.plugin;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.pedro.srt.srt.packets.control.handshake.EncryptionType;

/**
 * Persisted stream configuration. Lives in ATAK's own default preferences
 * (not a plugin-private file) so it survives plugin reloads and ATAK
 * restarts, and so the REC map button can start a stream without the pane
 * ever having been opened in this session.
 */
public final class StreamSettings {

    public static final int PROTOCOL_RTSP = 0;
    public static final int PROTOCOL_RTSPS = 1;
    public static final int PROTOCOL_SRT = 2;

    // mismo orden que R.array.camera_entries
    public static final int CAMERA_BACK = 0;
    public static final int CAMERA_FRONT = 1;
    public static final int CAMERA_USB = 2;

    static final EncryptionType[] SRT_ENCRYPTION_TYPES = {
            EncryptionType.AES128, EncryptionType.AES192, EncryptionType.AES256
    };

    private static final String KEY_PROTOCOL = "icung.protocol";
    private static final String KEY_URL = "icung.url";
    private static final String KEY_TRUST_ALL = "icung.trust_all_certs";
    private static final String KEY_SRT_PASSPHRASE = "icung.srt_passphrase";
    private static final String KEY_SRT_ENCRYPTION = "icung.srt_encryption";
    private static final String KEY_CAMERA = "icung.camera";
    private static final String KEY_KLV = "icung.klv";

    private static final String DEFAULT_URL = "rtsp://10.0.2.2:8554/icung";

    public int protocol = PROTOCOL_RTSP;
    public String url = DEFAULT_URL;
    public boolean trustAllCerts;
    public String srtPassphrase = "";
    public int srtEncryptionIndex;
    public int camera = CAMERA_BACK;
    /** MISB ST 0601 telemetry track; RTSP/RTSPS only for now */
    public boolean klv = true;

    public static StreamSettings load(Context atakContext) {
        SharedPreferences prefs = prefs(atakContext);
        StreamSettings s = new StreamSettings();
        s.protocol = clamp(prefs.getInt(KEY_PROTOCOL, PROTOCOL_RTSP), 0, 2);
        s.url = prefs.getString(KEY_URL, DEFAULT_URL);
        s.trustAllCerts = prefs.getBoolean(KEY_TRUST_ALL, false);
        s.srtPassphrase = prefs.getString(KEY_SRT_PASSPHRASE, "");
        s.srtEncryptionIndex = clamp(prefs.getInt(KEY_SRT_ENCRYPTION, 0),
                0, SRT_ENCRYPTION_TYPES.length - 1);
        s.camera = clamp(prefs.getInt(KEY_CAMERA, CAMERA_BACK), 0, 2);
        s.klv = prefs.getBoolean(KEY_KLV, true);
        return s;
    }

    public void save(Context atakContext) {
        prefs(atakContext).edit()
                .putInt(KEY_PROTOCOL, protocol)
                .putString(KEY_URL, url)
                .putBoolean(KEY_TRUST_ALL, trustAllCerts)
                .putString(KEY_SRT_PASSPHRASE, srtPassphrase)
                .putInt(KEY_SRT_ENCRYPTION, srtEncryptionIndex)
                .putInt(KEY_CAMERA, camera)
                .putBoolean(KEY_KLV, klv)
                .apply();
    }

    public EncryptionType srtEncryptionType() {
        return SRT_ENCRYPTION_TYPES[srtEncryptionIndex];
    }

    public String expectedScheme() {
        return protocol == PROTOCOL_RTSPS ? "rtsps" : protocol == PROTOCOL_SRT ? "srt" : "rtsp";
    }

    /** @return null if valid, otherwise the string resource id of the error. */
    public Integer validate() {
        if (!expectedScheme().equals(parseUrlScheme(url))) {
            return R.string.status_error_scheme_format;
        }
        if (protocol == PROTOCOL_SRT && !srtPassphrase.isEmpty()
                && (srtPassphrase.length() < 10 || srtPassphrase.length() > 79)) {
            return R.string.status_error_srt_passphrase_length;
        }
        return null;
    }

    static String parseUrlScheme(String url) {
        int schemeEnd = url.indexOf("://");
        return schemeEnd >= 0 ? url.substring(0, schemeEnd) : "";
    }

    // position: 0=RTSP, 1=RTSPS, 2=SRT (mismo orden que R.array.protocol_entries)
    static String rewriteUrlScheme(String url, int position) {
        String scheme = position == PROTOCOL_RTSPS ? "rtsps"
                : position == PROTOCOL_SRT ? "srt" : "rtsp";
        int schemeEnd = url.indexOf("://");
        String rest = schemeEnd >= 0 ? url.substring(schemeEnd + 3) : url;
        return scheme + "://" + rest;
    }

    private static SharedPreferences prefs(Context atakContext) {
        return PreferenceManager.getDefaultSharedPreferences(atakContext);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
