package com.carlosgt.atak.icung.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import gov.tak.api.cot.event.CotDetail;
import gov.tak.api.video.ConnectionEntry;

import org.junit.Test;

public class SelfVideoDetailHandlerTest {

    @Test
    public void buildPlaybackUrlAddsTcpTransportForRtsp() {
        ConnectionEntry entry = new ConnectionEntry.Builder()
                .setAddress("video.example.com")
                .setPort(8554)
                .setPath("/live/testcam")
                .setProtocol(ConnectionEntry.Protocol.RTSP)
                .build();

        String url = SelfVideoDetailHandler.buildPlaybackUrl(entry);
        assertEquals("rtsp://video.example.com:8554/live/testcam?tcp", url);
    }

    @Test
    public void buildPlaybackUrlHandlesQueryStringInPath() {
        ConnectionEntry entry = new ConnectionEntry.Builder()
                .setAddress("video.example.com")
                .setPort(8554)
                .setPath("/live/testcam?res=high")
                .setProtocol(ConnectionEntry.Protocol.RTSP)
                .build();

        String url = SelfVideoDetailHandler.buildPlaybackUrl(entry);
        assertEquals("rtsp://video.example.com:8554/live/testcam?res=high&tcp", url);
    }

    @Test
    public void buildPlaybackUrlSupportsSrtProtocol() {
        ConnectionEntry entry = new ConnectionEntry.Builder()
                .setAddress("video.example.com")
                .setPort(8890)
                .setPath("/live/srtcam")
                .setProtocol(ConnectionEntry.Protocol.SRT)
                .build();

        String url = SelfVideoDetailHandler.buildPlaybackUrl(entry);
        assertEquals("srt://video.example.com:8890/live/srtcam", url);
    }

    @Test
    public void toCotDetailProducesVideoTagWithChildConnectionEntry() {
        ConnectionEntry entry = new ConnectionEntry.Builder()
                .setUID("com.carlosgt.atak.icung.12345")
                .setAlias("testcallsign")
                .setAddress("video.example.com")
                .setPort(8554)
                .setPath("/live/testcam")
                .setProtocol(ConnectionEntry.Protocol.RTSP)
                .setRtspReliable(1)
                .build();

        SelfVideoDetailHandler handler = new SelfVideoDetailHandler(
                "SELF_UID",
                () -> true,
                () -> entry);

        CotDetail rootDetail = new CotDetail("detail");
        boolean handled = handler.toCotDetail(null, null, null, rootDetail);

        assertTrue(handled);
        assertEquals(1, rootDetail.childCount());

        CotDetail videoDetail = rootDetail.getChild(0);
        assertNotNull(videoDetail);
        assertEquals("__video", videoDetail.getElementName());
        assertEquals("rtsp://video.example.com:8554/live/testcam?tcp", videoDetail.getAttribute("url"));
        assertEquals("com.carlosgt.atak.icung.12345", videoDetail.getAttribute("uid"));
        assertEquals("testcallsign", videoDetail.getAttribute("sensor"));

        assertEquals(1, videoDetail.childCount());
        CotDetail ceDetail = videoDetail.getChild(0);
        assertNotNull(ceDetail);
        assertEquals("ConnectionEntry", ceDetail.getElementName());
        assertEquals("1", ceDetail.getAttribute("rtspReliable"));
        assertEquals("video.example.com", ceDetail.getAttribute("address"));
        assertEquals("8554", ceDetail.getAttribute("port"));
    }

    @Test
    public void toCotDetailReturnsFalseWhenEntryIsNull() {
        SelfVideoDetailHandler handler = new SelfVideoDetailHandler(
                "SELF_UID",
                () -> true,
                () -> null);

        CotDetail rootDetail = new CotDetail("detail");
        boolean handled = handler.toCotDetail(null, null, null, rootDetail);

        assertFalse(handled);
        assertEquals(0, rootDetail.childCount());
    }
}
