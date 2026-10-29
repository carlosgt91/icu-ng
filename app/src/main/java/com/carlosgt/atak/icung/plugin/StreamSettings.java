package com.carlosgt.atak.icung.plugin;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.Locale;

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

    // mismo orden que R.array.codec_entries
    public static final int CODEC_H264 = 0;
    public static final int CODEC_H265 = 1;

    // mismo orden que R.array.camera_entries
    public static final int CAMERA_BACK = 0;
    public static final int CAMERA_FRONT = 1;
    public static final int CAMERA_USB = 2;

    // mismo orden que R.array.resolution_entries
    static final int[][] RESOLUTIONS = { { 640, 480 }, { 1280, 720 }, { 1920, 1080 } };
    // mismo orden que R.array.fps_entries
    static final int[] FPS_VALUES = { 15, 24, 30 };
    // mismo orden que R.array.bitrate_entries; 0 = automatico segun resolucion
    static final int[] BITRATES_KBPS = { 0, 250, 500, 1000, 2000, 4000, 6000 };
    private static final int[] AUTO_BITRATE_KBPS = { 1200, 2500, 4500 };

    static final EncryptionType[] SRT_ENCRYPTION_TYPES = {
            EncryptionType.AES128, EncryptionType.AES192, EncryptionType.AES256
    };

    private static final String KEY_PROTOCOL = "icung.protocol";
    private static final String KEY_CODEC = "icung.codec";
    private static final String KEY_URL = "icung.url";
    private static final String KEY_TRUST_ALL = "icung.trust_all_certs";
    private static final String KEY_SRT_PASSPHRASE = "icung.srt_passphrase";
    private static final String KEY_SRT_ENCRYPTION = "icung.srt_encryption";
    private static final String KEY_CAMERA = "icung.camera";
    private static final String KEY_KLV = "icung.klv";
    private static final String KEY_RESOLUTION = "icung.resolution";
    private static final String KEY_FPS = "icung.fps";
    private static final String KEY_BITRATE = "icung.bitrate";
    private static final String KEY_ADAPTIVE = "icung.adaptive_bitrate";
    private static final String KEY_MICROPHONE = "icung.microphone";
    private static final String KEY_CLASSIFICATION = "icung.klv_classification";
    private static final String KEY_COUNTRY = "icung.klv_country";
    private static final String KEY_MISSION = "icung.klv_mission";

    // Empty until the operator types one or reads a QR: there is no server
    // address that could be right by default.
    private static final String DEFAULT_URL = "";

    public int protocol = PROTOCOL_RTSP;
    public int codec = CODEC_H264;
    public String url = DEFAULT_URL;
    public boolean trustAllCerts;
    public String srtPassphrase = "";
    public int srtEncryptionIndex;
    public int camera = CAMERA_BACK;
    /** MISB ST 0601 telemetry track; RTSP/RTSPS only for now */
    public boolean klv = true;
    public int resolutionIndex;
    public int fpsIndex = FPS_VALUES.length - 1;
    public int bitrateIndex;
    /** lower the bitrate while the network cannot keep up (VPN, mobile data) */
    public boolean adaptiveBitrate = true;
    /** false sends silence: the audio track stays, so it can be turned back on live */
    public boolean microphone = true;
    /** STANAG 4609 marking in the KLV: index into R.array.classification_entries */
    public int classificationIndex;
    /** GENC three-letter code of the classifying country */
    public String country = "";
    public String missionId = "";

    public static StreamSettings load(Context atakContext) {
        SharedPreferences prefs = prefs(atakContext);
        StreamSettings s = new StreamSettings();
        s.protocol = clamp(prefs.getInt(KEY_PROTOCOL, PROTOCOL_RTSP), 0, 2);
        s.codec = clamp(prefs.getInt(KEY_CODEC, CODEC_H264), 0, 1);
        s.url = prefs.getString(KEY_URL, DEFAULT_URL);
        s.trustAllCerts = prefs.getBoolean(KEY_TRUST_ALL, false);
        s.srtPassphrase = prefs.getString(KEY_SRT_PASSPHRASE, "");
        s.srtEncryptionIndex = clamp(prefs.getInt(KEY_SRT_ENCRYPTION, 0),
                0, SRT_ENCRYPTION_TYPES.length - 1);
        s.camera = clamp(prefs.getInt(KEY_CAMERA, CAMERA_BACK), 0, 2);
        s.klv = prefs.getBoolean(KEY_KLV, true);
        s.resolutionIndex = clamp(prefs.getInt(KEY_RESOLUTION, 0), 0, RESOLUTIONS.length - 1);
        s.fpsIndex = clamp(prefs.getInt(KEY_FPS, FPS_VALUES.length - 1), 0, FPS_VALUES.length - 1);
        s.bitrateIndex = clamp(prefs.getInt(KEY_BITRATE, 0), 0, BITRATES_KBPS.length - 1);
        s.adaptiveBitrate = prefs.getBoolean(KEY_ADAPTIVE, true);
        s.microphone = prefs.getBoolean(KEY_MICROPHONE, true);
        s.classificationIndex = clamp(prefs.getInt(KEY_CLASSIFICATION, 0), 0,
                KlvEncoder.TOP_SECRET - KlvEncoder.UNCLASSIFIED);
        s.country = prefs.getString(KEY_COUNTRY, defaultCountry());
        s.missionId = prefs.getString(KEY_MISSION, "");
        return s;
    }

    public void save(Context atakContext) {
        prefs(atakContext).edit()
                .putInt(KEY_PROTOCOL, protocol)
                .putInt(KEY_CODEC, codec)
                .putString(KEY_URL, url)
                .putBoolean(KEY_TRUST_ALL, trustAllCerts)
                .putString(KEY_SRT_PASSPHRASE, srtPassphrase)
                .putInt(KEY_SRT_ENCRYPTION, srtEncryptionIndex)
                .putInt(KEY_CAMERA, camera)
                .putBoolean(KEY_KLV, klv)
                .putInt(KEY_RESOLUTION, resolutionIndex)
                .putInt(KEY_FPS, fpsIndex)
                .putInt(KEY_BITRATE, bitrateIndex)
                .putBoolean(KEY_ADAPTIVE, adaptiveBitrate)
                .putBoolean(KEY_MICROPHONE, microphone)
                .putInt(KEY_CLASSIFICATION, classificationIndex)
                .putString(KEY_COUNTRY, country)
                .putString(KEY_MISSION, missionId)
                .apply();
    }

    public int videoWidth() {
        return RESOLUTIONS[resolutionIndex][0];
    }

    public int videoHeight() {
        return RESOLUTIONS[resolutionIndex][1];
    }

    public int fps() {
        return FPS_VALUES[fpsIndex];
    }

    /** Target video bitrate in bits/s; also the ceiling for the adaptive one. */
    public int videoBitrate() {
        int kbps = BITRATES_KBPS[bitrateIndex];
        return (kbps == 0 ? AUTO_BITRATE_KBPS[resolutionIndex] : kbps) * 1024;
    }

    /** What the encoder is built with: a change before starting needs a new stream. */
    String encoderKey() {
        return videoWidth() + "x" + videoHeight() + "@" + fps() + "/" + videoBitrate() + ":" + codec;
    }

    /** ST 0102 classification value for the KLV security set */
    public int classification() {
        return KlvEncoder.UNCLASSIFIED + classificationIndex;
    }

    // GENC three-letter codes match ISO 3166 alpha-3 for nearly every
    // country, so the phone's region is a sensible first value.
    private static String defaultCountry() {
        try {
            return Locale.getDefault().getISO3Country().toUpperCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return "";
        }
    }

    static boolean isCountryCode(String code) {
        return code != null && code.matches("[A-Z]{3}");
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
        if (klv && protocol != PROTOCOL_SRT && !isCountryCode(country)) {
            return R.string.status_error_klv_country;
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

    // true when the URL carries userinfo (name and secret before the @), the only place
    // RootEncoder takes RTSP credentials from
    static boolean urlHasCredentials(String url) {
        if (url == null) return false;
        int start = url.indexOf("://");
        if (start < 0) return false;
        int end = start + 3;
        while (end < url.length() && "/?#".indexOf(url.charAt(end)) < 0) end++;
        return url.substring(start + 3, end).indexOf('@') >= 0;
    }

    private static SharedPreferences prefs(Context atakContext) {
        return PreferenceManager.getDefaultSharedPreferences(atakContext);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
