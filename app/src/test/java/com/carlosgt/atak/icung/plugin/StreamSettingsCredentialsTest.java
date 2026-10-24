package com.carlosgt.atak.icung.plugin;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StreamSettingsCredentialsTest {

    @Test
    public void userinfoBeforeTheHostCounts() {
        assertTrue(StreamSettings.urlHasCredentials("rtsp://publish:" + "x" + "@video.example.com:8554/cam"));
        assertTrue(StreamSettings.urlHasCredentials("rtsps://u@host/cam"));
    }

    @Test
    public void noUserinfoOrAnAtSignAfterTheHostDoesNot() {
        assertFalse(StreamSettings.urlHasCredentials("rtsp://video.example.com:8554/cam"));
        assertFalse(StreamSettings.urlHasCredentials("rtsp://video.example.com:8554/a@b"));
        assertFalse(StreamSettings.urlHasCredentials("rtsp://host?x=a@b"));
        assertFalse(StreamSettings.urlHasCredentials(""));
        assertFalse(StreamSettings.urlHasCredentials(null));
    }
}
