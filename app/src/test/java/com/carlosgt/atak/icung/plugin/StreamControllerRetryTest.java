package com.carlosgt.atak.icung.plugin;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StreamControllerRetryTest {

    @Test
    public void backoffDoublesThenCapsAtThirtySeconds() {
        assertEquals(2000, StreamController.retryDelayMs(0));
        assertEquals(4000, StreamController.retryDelayMs(1));
        assertEquals(8000, StreamController.retryDelayMs(2));
        assertEquals(16000, StreamController.retryDelayMs(3));
        assertEquals(30000, StreamController.retryDelayMs(4));
        assertEquals(30000, StreamController.retryDelayMs(50));
    }
}
