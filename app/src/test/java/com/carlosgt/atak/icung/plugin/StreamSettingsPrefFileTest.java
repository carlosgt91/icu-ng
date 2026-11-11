package com.carlosgt.atak.icung.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

/** Values as a .pref file may leave them in ATAK's preferences. */
public class StreamSettingsPrefFileTest {

    @Test
    public void readsValuesWrittenWithTheRightClass() {
        Map<String, Object> p = new HashMap<>();
        p.put("icung.protocol", 2);
        p.put("icung.url", " srt://video.example.org:8890?streamid=publish:alpha ");
        p.put("icung.klv", false);
        p.put("icung.klv_country", "esp");
        StreamSettings s = StreamSettings.load(p);
        assertEquals(StreamSettings.PROTOCOL_SRT, s.protocol);
        assertEquals("srt://video.example.org:8890?streamid=publish:alpha", s.url);
        assertFalse(s.klv);
        assertEquals("ESP", s.country);
    }

    @Test
    public void acceptsNumbersAndBooleansWrittenAsStrings() {
        Map<String, Object> p = new HashMap<>();
        p.put("icung.resolution", "2");
        p.put("icung.microphone", "false");
        p.put("icung.trust_all_certs", "1");
        StreamSettings s = StreamSettings.load(p);
        assertEquals(2, s.resolutionIndex);
        assertFalse(s.microphone);
        assertTrue(s.trustAllCerts);
    }

    @Test
    public void aBadValueOnlyResetsThatSetting() {
        Map<String, Object> p = new HashMap<>();
        p.put("icung.fps", "fast");
        p.put("icung.camera", 9);
        p.put("icung.bitrate", 3);
        StreamSettings s = StreamSettings.load(p);
        assertEquals(StreamSettings.FPS_VALUES.length - 1, s.fpsIndex);
        assertEquals(StreamSettings.CAMERA_USB, s.camera); // clamped
        assertEquals(3, s.bitrateIndex);
    }

    @Test
    public void sameAsTellsLiveOptionsApart() {
        StreamSettings a = StreamSettings.load(new HashMap<String, Object>());
        StreamSettings b = StreamSettings.load(new HashMap<String, Object>());
        assertTrue(a.sameAs(b));
        b.microphone = false;
        assertFalse(a.sameAs(b));
        assertFalse(a.sameLiveOptions(b));
        b.microphone = true;
        b.url = "rtsp://x/y";
        assertFalse(a.sameAs(b));
        assertTrue(a.sameLiveOptions(b));
    }

    @Test
    public void ownKeysAreTheIcungOnes() {
        assertTrue(StreamSettings.isOwnKey("icung.url"));
        assertFalse(StreamSettings.isOwnKey("locationCallsign"));
        assertFalse(StreamSettings.isOwnKey(null));
    }
}
