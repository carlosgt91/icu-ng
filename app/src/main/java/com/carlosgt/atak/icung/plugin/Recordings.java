package com.carlosgt.atak.icung.plugin;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Name of the MP4 that ICU NG Capture records. The recording itself is done
 * by the app (its Recordings class): the plugin cannot record inside ATAK,
 * see StreamController. Keep fileName() identical in both.
 */
final class Recordings {

    private Recordings() {
    }

    /** ICUNG_callsign_20261111_153000Z.mp4, safe as a file name. */
    static String fileName(String callsign, long timeMs) {
        SimpleDateFormat f = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        String who = callsign == null ? "" : callsign.replaceAll("[^A-Za-z0-9_-]+", "-");
        who = who.replaceAll("^-+|-+$", "");
        return "ICUNG_" + (who.isEmpty() ? "" : who + "_") + f.format(new Date(timeMs)) + "Z.mp4";
    }
}
