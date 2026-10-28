package com.carlosgt.atak.icung.plugin;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class KlvEncoderTest {

    private static KlvEncoder.Telemetry sample() {
        KlvEncoder.Telemetry t = new KlvEncoder.Telemetry();
        t.timeMicros = 1_761_150_000_123_456L;
        t.latitude = 40.4168;
        t.longitude = -3.7038;
        t.altitudeMsl = 657;
        t.azimuthDeg = 270.5;
        t.elevationDeg = -12.25;
        t.hfovDeg = 66.5;
        t.vfovDeg = 51.0;
        t.callsign = "ALFA-1";
        t.sensorName = "ICU NG trasera";
        return t;
    }

    @Test
    public void keyLengthAndChecksumAreConsistent() {
        byte[] p = KlvEncoder.encode(sample());
        assertArrayEquals(KlvEncoder.UAS_LS_KEY, Arrays.copyOf(p, 16));
        int len = p[16] & 0xFF;
        assertTrue("short-form BER expected for this size", len < 128);
        assertEquals(17 + len, p.length);

        // last item is the checksum: tag 1, length 2
        assertEquals(1, p[p.length - 4]);
        assertEquals(2, p[p.length - 3]);
        int stored = ((p[p.length - 2] & 0xFF) << 8) | (p[p.length - 1] & 0xFF);
        assertEquals(KlvEncoder.checksum(p, p.length - 2), stored);
    }

    @Test
    public void valuesRoundTrip() {
        Map<Integer, byte[]> items = decode(KlvEncoder.encode(sample()));

        assertEquals(1_761_150_000_123_456L, unsigned(items.get(2)));
        assertEquals(270.5, unsigned(items.get(5)) * 360.0 / 0xFFFF, 0.01);
        assertEquals("ALFA-1", new String(items.get(10), StandardCharsets.UTF_8));
        assertEquals("ICU NG trasera", new String(items.get(11), StandardCharsets.UTF_8));
        assertEquals(40.4168, signed(items.get(13)) * 90.0 / 0x7FFFFFFF, 1e-6);
        assertEquals(-3.7038, signed(items.get(14)) * 180.0 / 0x7FFFFFFF, 1e-6);
        assertEquals(657, unsigned(items.get(15)) * 19900.0 / 0xFFFF - 900, 0.5);
        assertEquals(66.5, unsigned(items.get(16)) * 180.0 / 0xFFFF, 0.01);
        assertEquals(51.0, unsigned(items.get(17)) * 180.0 / 0xFFFF, 0.01);
        assertEquals(-12.25, signed(items.get(19)) * 180.0 / 0x7FFFFFFF, 1e-6);
        assertEquals(17, unsigned(items.get(65)));
    }

    @Test
    public void unknownValuesAreLeftOut() {
        KlvEncoder.Telemetry t = sample();
        t.altitudeMsl = Double.NaN;
        t.azimuthDeg = Double.NaN;
        t.elevationDeg = Double.NaN;
        Map<Integer, byte[]> items = decode(KlvEncoder.encode(t));
        assertFalse(items.containsKey(5));
        assertFalse(items.containsKey(15));
        assertFalse(items.containsKey(19));
        assertTrue(items.containsKey(13));
    }

    @Test
    public void negativeAzimuthIsNormalised() {
        KlvEncoder.Telemetry t = sample();
        t.azimuthDeg = -90;
        Map<Integer, byte[]> items = decode(KlvEncoder.encode(t));
        assertEquals(270.0, unsigned(items.get(5)) * 360.0 / 0xFFFF, 0.01);
    }

    @Test
    public void frameCenterTagsRoundTrip() {
        KlvEncoder.Telemetry t = sample();
        t.frameCenterLat = 40.4170;
        t.frameCenterLon = -3.7041;
        t.frameCenterMsl = 650;
        t.slantRange = 42.5;
        Map<Integer, byte[]> items = decode(KlvEncoder.encode(t));
        assertEquals(40.4170, signed(items.get(23)) * 90.0 / 0x7FFFFFFF, 1e-6);
        assertEquals(-3.7041, signed(items.get(24)) * 180.0 / 0x7FFFFFFF, 1e-6);
        assertEquals(650, unsigned(items.get(25)) * 19900.0 / 0xFFFF - 900, 0.5);
        assertEquals(42.5, unsigned(items.get(21)) * 5_000_000.0 / 0xFFFFFFFFL, 0.01);
    }

    @Test
    public void cornerTagsRoundTrip() {
        KlvEncoder.Telemetry t = sample();
        t.frameCenterLat = 40.4170;
        t.frameCenterLon = -3.7041;
        t.cornerLat = new double[] { 40.4172, 40.4172, 40.4168, 40.4168 };
        t.cornerLon = new double[] { -3.7044, -3.7038, -3.7038, -3.7044 };
        byte[] p = KlvEncoder.encode(t);
        assertEquals(KlvEncoder.checksum(p, p.length - 2),
                ((p[p.length - 2] & 0xFF) << 8) | (p[p.length - 1] & 0xFF));
        Map<Integer, byte[]> items = decode(p);
        for (int i = 0; i < 4; i++) {
            assertEquals(2, items.get(26 + 2 * i).length);
            assertEquals(t.cornerLat[i] - t.frameCenterLat,
                    signed(items.get(26 + 2 * i)) * 0.075 / 0x7FFF, 3e-6);
            assertEquals(t.cornerLon[i] - t.frameCenterLon,
                    signed(items.get(27 + 2 * i)) * 0.075 / 0x7FFF, 3e-6);
            assertEquals(t.cornerLat[i], signed(items.get(82 + 2 * i)) * 90.0 / 0x7FFFFFFF, 1e-6);
            assertEquals(t.cornerLon[i], signed(items.get(83 + 2 * i)) * 180.0 / 0x7FFFFFFF, 1e-6);
        }
    }

    @Test
    public void farCornersOnlyGoInFullForm() {
        KlvEncoder.Telemetry t = sample();
        t.frameCenterLat = 40.0;
        t.frameCenterLon = -3.0;
        t.cornerLat = new double[] { 40.1, 40.1, 39.9, 39.9 };
        t.cornerLon = new double[] { -3.1, -2.9, -2.9, -3.1 };
        Map<Integer, byte[]> items = decode(KlvEncoder.encode(t));
        assertFalse(items.containsKey(26));
        assertTrue(items.containsKey(82));
    }

    @Test
    public void cornerOffsetsWrapAcrossTheAntimeridian() {
        KlvEncoder.Telemetry t = sample();
        t.frameCenterLat = 10.0;
        t.frameCenterLon = 179.999;
        t.cornerLat = new double[] { 10.001, 10.001, 9.999, 9.999 };
        t.cornerLon = new double[] { 179.998, -179.999, -179.999, 179.998 };
        Map<Integer, byte[]> items = decode(KlvEncoder.encode(t));
        assertEquals(0.002, signed(items.get(29)) * 0.075 / 0x7FFF, 3e-6);
        assertEquals(-0.001, signed(items.get(33)) * 0.075 / 0x7FFF, 3e-6);
    }

    @Test
    public void noCornersWithoutFootprint() {
        Map<Integer, byte[]> items = decode(KlvEncoder.encode(sample()));
        for (int tag = 26; tag <= 33; tag++) assertFalse(items.containsKey(tag));
        for (int tag = 82; tag <= 89; tag++) assertFalse(items.containsKey(tag));
    }

    @Test
    public void destinationMatchesKnownDistances() {
        // 1 km due north: +0.008993 deg latitude, same longitude
        double[] n = KlvEncoder.destination(40.0, -3.0, 0, 1000);
        assertEquals(40.0 + 1000 / 111195.08, n[0], 1e-6);
        assertEquals(-3.0, n[1], 1e-9);
        // 1 km due east at 40N: longitude grows by 1000 / (111195 * cos 40)
        double[] e = KlvEncoder.destination(40.0, -3.0, 90, 1000);
        assertEquals(-3.0 + 1000 / (111195.08 * Math.cos(Math.toRadians(40))), e[1], 1e-5);
        assertEquals(40.0, e[0], 1e-4);
    }

    private static Map<Integer, byte[]> decode(byte[] p) {
        Map<Integer, byte[]> items = new LinkedHashMap<>();
        // BER length after the 16-byte key: short form, or 0x81/0x82 + bytes.
        int first = p[16] & 0xFF;
        int i = first < 128 ? 17 : 17 + (first & 0x7F);
        while (i < p.length) {
            int tag = p[i++] & 0xFF;
            int len = p[i++] & 0xFF;
            items.put(tag, Arrays.copyOfRange(p, i, i + len));
            i += len;
        }
        return items;
    }

    private static long unsigned(byte[] b) {
        long v = 0;
        for (byte x : b) v = (v << 8) | (x & 0xFF);
        return v;
    }

    private static long signed(byte[] b) {
        long v = unsigned(b);
        int bits = 8 * b.length;
        return (v & (1L << (bits - 1))) != 0 ? v - (1L << bits) : v;
    }
}
