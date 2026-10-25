package com.carlosgt.atak.icung.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import gov.tak.api.video.ConnectionEntry;

import org.junit.Test;

public class StreamControllerConnectionEntryTest {

    @Test
    public void rtspsMapsToRtspPlaybackOnPort8554() {
        StreamSettings s = new StreamSettings();
        s.protocol = StreamSettings.PROTOCOL_RTSPS;
        s.url = "rtsps://publish:password@100.95.156.126:8555/live/s21carlosgt";

        ConnectionEntry entry = StreamController.buildConnectionEntry(s, "com.carlosgt.atak.icung", "carlosgts21");
        assertNotNull(entry);
        assertEquals(ConnectionEntry.Protocol.RTSP, entry.getProtocol());
        assertEquals("100.95.156.126", entry.getAddress());
        assertEquals(8554, entry.getPort());
        assertEquals("/live/s21carlosgt", entry.getPath());
        assertEquals("carlosgts21", entry.getAlias());
    }

    @Test
    public void rtspsDefaultPortMapsTo8554() {
        StreamSettings s = new StreamSettings();
        s.protocol = StreamSettings.PROTOCOL_RTSPS;
        s.url = "rtsps://publish:password@video.example.com/live/cam1";

        ConnectionEntry entry = StreamController.buildConnectionEntry(s, "com.carlosgt.atak.icung", "cam1");
        assertNotNull(entry);
        assertEquals(ConnectionEntry.Protocol.RTSP, entry.getProtocol());
        assertEquals("video.example.com", entry.getAddress());
        assertEquals(8554, entry.getPort());
        assertEquals("/live/cam1", entry.getPath());
    }

    @Test
    public void rtspKeepsPortAndProtocol() {
        StreamSettings s = new StreamSettings();
        s.protocol = StreamSettings.PROTOCOL_RTSP;
        s.url = "rtsp://100.95.156.126:8554/live/stream1";

        ConnectionEntry entry = StreamController.buildConnectionEntry(s, "com.carlosgt.atak.icung", "stream1");
        assertNotNull(entry);
        assertEquals(ConnectionEntry.Protocol.RTSP, entry.getProtocol());
        assertEquals("100.95.156.126", entry.getAddress());
        assertEquals(8554, entry.getPort());
        assertEquals("/live/stream1", entry.getPath());
    }
}
