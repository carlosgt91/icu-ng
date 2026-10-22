package com.carlosgt.atak.icung.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * Payload shapes taken from the restreamer's stream-qr.js (buildUrl).
 */
public class QrStreamUrlTest {

    @Test
    public void rtspWithoutCredentials() {
        QrStreamUrl.Result r = QrStreamUrl.parse("rtsp://10.0.0.5:8554/camara1");
        assertEquals(StreamSettings.PROTOCOL_RTSP, r.protocol);
        assertEquals("rtsp://10.0.0.5:8554/camara1", r.url);
        assertNull(r.srtPassphrase);
        assertNull(r.name);
    }

    @Test
    public void rtspKeepsEncodedCredentialsAndDropsName() {
        QrStreamUrl.Result r = QrStreamUrl.parse(
                "rtsp://publish:p%40ss%3Aword@10.0.0.5:8554/camara1?name=Puesto%201");
        assertEquals("rtsp://publish:p%40ss%3Aword@10.0.0.5:8554/camara1", r.url);
        assertEquals("Puesto 1", r.name);
    }

    @Test
    public void rtspsUsesItsOwnProtocol() {
        QrStreamUrl.Result r = QrStreamUrl.parse("rtsps://publish:x@srv.example:8555/cam");
        assertEquals(StreamSettings.PROTOCOL_RTSPS, r.protocol);
        assertEquals("rtsps://publish:x@srv.example:8555/cam", r.url);
    }

    @Test
    public void ipv6HostKeepsBrackets() {
        QrStreamUrl.Result r = QrStreamUrl.parse("rtsp://[fd00::5]:8554/cam");
        assertEquals("rtsp://[fd00::5]:8554/cam", r.url);
    }

    @Test
    public void srtReadBecomesPublishAndCredentialsAreDecodedPerPart() {
        QrStreamUrl.Result r = QrStreamUrl.parse(
                "srt://10.0.0.5:8890?streamid=read:camara1:publish:p%3Ass%40x&name=Cam");
        assertEquals(StreamSettings.PROTOCOL_SRT, r.protocol);
        assertEquals("srt://10.0.0.5:8890?streamid=publish:camara1:publish:p:ss@x", r.url);
        assertEquals("Cam", r.name);
    }

    @Test
    public void srtPassphraseMovesOutOfTheUrl() {
        QrStreamUrl.Result r = QrStreamUrl.parse(
                "srt://10.0.0.5:8890?streamid=publish:camara1&passphrase=0123456789abc");
        assertEquals("srt://10.0.0.5:8890?streamid=publish:camara1", r.url);
        assertEquals("0123456789abc", r.srtPassphrase);
    }

    @Test
    public void srtPassphraseTooShortIsRejected() {
        expectRejected("srt://10.0.0.5:8890?streamid=publish:c&passphrase=short");
    }

    @Test
    public void rtmpIsRejected() {
        expectRejected("rtmp://10.0.0.5:1935/camara1");
    }

    @Test
    public void garbageIsRejected() {
        expectRejected("hola mundo");
        expectRejected("");
    }

    private static void expectRejected(String payload) {
        try {
            QrStreamUrl.parse(payload);
            fail("expected rejection: " + payload);
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
