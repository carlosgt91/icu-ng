package com.carlosgt.atak.icung.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ServerVideoFeedTest {

    @Test
    public void feedUidIsFixedPerPhone() {
        assertEquals("icung-ANDROID-abc", ServerVideoFeed.feedUid("ANDROID-abc"));
    }
}
