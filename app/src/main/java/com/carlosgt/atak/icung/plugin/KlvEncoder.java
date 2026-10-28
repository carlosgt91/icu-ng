package com.carlosgt.atak.icung.plugin;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Builds MISB ST 0601 (UAS Datalink Local Set) KLV packets from a handheld
 * camera's telemetry. Pure Java and stateless, so it is unit-tested on the
 * JVM.
 *
 * Mapping for a phone (the "platform" is the phone, the "sensor" its camera,
 * fixed to it, so the sensor-relative angles are zero except elevation):
 * <pre>
 *   2  Precision Time Stamp        uint64  µs since the Unix epoch
 *   5  Platform Heading            uint16  0..360        camera azimuth (true)
 *  10  Platform Designation        string  ATAK callsign
 *  11  Image Source Sensor         string  e.g. "ICU NG trasera"
 *  12  Image Coordinate System     string  "WGS-84"
 *  13  Sensor Latitude             int32   ±90
 *  14  Sensor Longitude            int32   ±180
 *  15  Sensor True Altitude        uint16  -900..19000 m (MSL)
 *  16  Sensor Horizontal FOV       uint16  0..180
 *  17  Sensor Vertical FOV         uint16  0..180
 *  18  Sensor Relative Azimuth     uint32  0..360        always 0
 *  19  Sensor Relative Elevation   int32   ±180          camera pitch
 *  21  Slant Range                 uint32  0..5e6 m      sensor to frame center
 *  23  Frame Center Latitude       int32   ±90           where the camera looks
 *  24  Frame Center Longitude      int32   ±180
 *  25  Frame Center Elevation      uint16  -900..19000 m (MSL)
 * 26-33 Offset Corner Lat/Lon 1-4  int16   ±0.075        from the frame center
 *  65  UAS Datalink LS Version     uint8   17
 * 82-89 Corner Lat/Lon 1-4 (Full)  int32   ±90 / ±180    same corners, absolute
 *   1  Checksum                    uint16  last, see checksum()
 * </pre>
 * The corners (upper-left, upper-right, lower-right, lower-left of the
 * image, see Footprint) are what lets a receiver drape the video on its
 * map. Both forms are sent: older players only read the offset one.
 */
public final class KlvEncoder {

    static final byte[] UAS_LS_KEY = {
            0x06, 0x0E, 0x2B, 0x34, 0x02, 0x0B, 0x01, 0x01,
            0x0E, 0x01, 0x03, 0x01, 0x01, 0x00, 0x00, 0x00
    };

    private static final int LS_VERSION = 17;
    // Range of tags 26-33, in degrees: about 8 km of latitude.
    private static final double CORNER_OFFSET_RANGE = 0.075;

    public static final class Telemetry {
        public long timeMicros;
        public double latitude;
        public double longitude;
        /** metres above mean sea level; NaN if unknown (tag left out) */
        public double altitudeMsl = Double.NaN;
        /** true-north azimuth of the camera axis; NaN if unknown */
        public double azimuthDeg = Double.NaN;
        /** camera axis above (+) / below (-) the horizon; NaN if unknown */
        public double elevationDeg = Double.NaN;
        public double hfovDeg;
        public double vfovDeg;
        public String callsign;
        public String sensorName;
        /** where the camera axis meets the ground; NaN if unknown (tags left out) */
        public double frameCenterLat = Double.NaN;
        public double frameCenterLon = Double.NaN;
        public double frameCenterMsl = Double.NaN;
        public double slantRange = Double.NaN;
        /** frame corners on the ground, ST 0601 order; null if unknown (tags left out) */
        public double[] cornerLat;
        public double[] cornerLon;
    }

    private KlvEncoder() {
    }

    public static byte[] encode(Telemetry t) {
        ByteArrayOutputStream value = new ByteArrayOutputStream();
        putItem(value, 2, uint(t.timeMicros, 8));
        if (!Double.isNaN(t.azimuthDeg)) {
            putItem(value, 5, uint(scaleUnsigned(normalize360(t.azimuthDeg), 360, 0xFFFFL), 2));
        }
        if (t.callsign != null && !t.callsign.isEmpty()) {
            putItem(value, 10, utf8(t.callsign));
        }
        if (t.sensorName != null && !t.sensorName.isEmpty()) {
            putItem(value, 11, utf8(t.sensorName));
        }
        putItem(value, 12, utf8("WGS-84"));
        putItem(value, 13, uint(scaleSigned(t.latitude, 90), 4));
        putItem(value, 14, uint(scaleSigned(t.longitude, 180), 4));
        if (!Double.isNaN(t.altitudeMsl)) {
            putItem(value, 15, uint(altitude(t.altitudeMsl), 2));
        }
        putItem(value, 16, uint(scaleUnsigned(clamp(t.hfovDeg, 0, 180), 180, 0xFFFFL), 2));
        putItem(value, 17, uint(scaleUnsigned(clamp(t.vfovDeg, 0, 180), 180, 0xFFFFL), 2));
        putItem(value, 18, uint(0, 4));
        if (!Double.isNaN(t.elevationDeg)) {
            putItem(value, 19, uint(scaleSigned(t.elevationDeg, 180), 4));
        }
        if (!Double.isNaN(t.slantRange)) {
            putItem(value, 21, uint(scaleUnsigned(clamp(t.slantRange, 0, 5_000_000),
                    5_000_000, 0xFFFFFFFFL), 4));
        }
        // ATAK's player fills its "TGT" line (and the target on the map)
        // from the frame center; without it only the sensor is known.
        if (!Double.isNaN(t.frameCenterLat) && !Double.isNaN(t.frameCenterLon)) {
            putItem(value, 23, uint(scaleSigned(t.frameCenterLat, 90), 4));
            putItem(value, 24, uint(scaleSigned(t.frameCenterLon, 180), 4));
            if (!Double.isNaN(t.frameCenterMsl)) {
                putItem(value, 25, uint(altitude(t.frameCenterMsl), 2));
            }
            if (hasCorners(t) && cornersFitOffsets(t)) {
                for (int i = 0; i < 4; i++) {
                    putItem(value, 26 + 2 * i, uint(scaleSigned(t.cornerLat[i] - t.frameCenterLat,
                            CORNER_OFFSET_RANGE, 0x7FFF), 2));
                    putItem(value, 27 + 2 * i, uint(scaleSigned(lonOffset(t, i),
                            CORNER_OFFSET_RANGE, 0x7FFF), 2));
                }
            }
        }
        putItem(value, 65, uint(LS_VERSION, 1));
        if (hasCorners(t)) {
            for (int i = 0; i < 4; i++) {
                putItem(value, 82 + 2 * i, uint(scaleSigned(t.cornerLat[i], 90), 4));
                putItem(value, 83 + 2 * i, uint(scaleSigned(t.cornerLon[i], 180), 4));
            }
        }

        // The checksum item's own tag and length are part of the summed
        // bytes; only its 2-byte value is not.
        value.write(1);
        value.write(2);

        ByteArrayOutputStream packet = new ByteArrayOutputStream();
        packet.write(UAS_LS_KEY, 0, UAS_LS_KEY.length);
        byte[] length = berLength(value.size() + 2);
        packet.write(length, 0, length.length);
        byte[] body = value.toByteArray();
        packet.write(body, 0, body.length);

        byte[] partial = packet.toByteArray();
        int sum = checksum(partial, partial.length);
        packet.write((sum >> 8) & 0xFF);
        packet.write(sum & 0xFF);
        return packet.toByteArray();
    }

    /**
     * ST 0601 checksum: a running 16-bit sum of every byte from the start
     * of the key through the checksum item's length byte, even-indexed
     * bytes weighted as the high byte of a big-endian word. Not a CRC.
     */
    /**
     * Point at the given true-north azimuth and ground distance from a start
     * point, on a sphere. Good to well under a metre over the few hundred
     * metres a handheld camera's frame center is ever placed at.
     *
     * @return {lat, lon} in degrees
     */
    public static double[] destination(double latDeg, double lonDeg,
            double azimuthDeg, double distanceM) {
        final double r = 6371008.8;
        double lat1 = Math.toRadians(latDeg);
        double lon1 = Math.toRadians(lonDeg);
        double az = Math.toRadians(azimuthDeg);
        double d = distanceM / r;
        double lat2 = Math.asin(Math.sin(lat1) * Math.cos(d)
                + Math.cos(lat1) * Math.sin(d) * Math.cos(az));
        double lon2 = lon1 + Math.atan2(Math.sin(az) * Math.sin(d) * Math.cos(lat1),
                Math.cos(d) - Math.sin(lat1) * Math.sin(lat2));
        double lonDeg2 = (Math.toDegrees(lon2) + 540) % 360 - 180;
        return new double[] { Math.toDegrees(lat2), lonDeg2 };
    }

    private static boolean hasCorners(Telemetry t) {
        return t.cornerLat != null && t.cornerLon != null
                && t.cornerLat.length == 4 && t.cornerLon.length == 4;
    }

    private static boolean cornersFitOffsets(Telemetry t) {
        for (int i = 0; i < 4; i++) {
            if (Math.abs(t.cornerLat[i] - t.frameCenterLat) > CORNER_OFFSET_RANGE) return false;
            if (Math.abs(lonOffset(t, i)) > CORNER_OFFSET_RANGE) return false;
        }
        return true;
    }

    // Across the antimeridian the plain difference is off by 360.
    private static double lonOffset(Telemetry t, int i) {
        double d = t.cornerLon[i] - t.frameCenterLon;
        if (d > 180) d -= 360;
        if (d < -180) d += 360;
        return d;
    }

    // uint16 over -900..19000 m, shared by tags 15 and 25
    private static long altitude(double metresMsl) {
        return Math.round((clamp(metresMsl, -900, 19000) + 900) / 19900.0 * 0xFFFF);
    }

    static int checksum(byte[] data, int len) {
        int bcc = 0;
        for (int i = 0; i < len; i++) {
            bcc += (data[i] & 0xFF) << (8 * ((i + 1) % 2));
        }
        return bcc & 0xFFFF;
    }

    private static void putItem(ByteArrayOutputStream out, int tag, byte[] value) {
        // All tags used here are < 128, so the BER-OID tag is one byte.
        out.write(tag);
        byte[] len = berLength(value.length);
        out.write(len, 0, len.length);
        out.write(value, 0, value.length);
    }

    static byte[] berLength(int len) {
        if (len < 128) return new byte[] { (byte) len };
        if (len < 256) return new byte[] { (byte) 0x81, (byte) len };
        return new byte[] { (byte) 0x82, (byte) (len >> 8), (byte) len };
    }

    private static byte[] uint(long v, int bytes) {
        byte[] out = new byte[bytes];
        for (int i = 0; i < bytes; i++) {
            out[i] = (byte) (v >> (8 * (bytes - 1 - i)));
        }
        return out;
    }

    private static byte[] utf8(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        if (b.length <= 127) return b;
        byte[] cut = new byte[127];
        System.arraycopy(b, 0, cut, 0, 127);
        return cut;
    }

    // Signed angle mapped onto the full int32 (or int16) range minus the
    // reserved 0x80000000 (0x8000) "error" value: ±range -> ±(2^31 - 1).
    private static long scaleSigned(double v, double range) {
        return scaleSigned(v, range, 0x7FFFFFFFL);
    }

    private static long scaleSigned(double v, double range, long max) {
        return Math.round(clamp(v, -range, range) / range * max);
    }

    private static long scaleUnsigned(double v, double range, long max) {
        return Math.round(v / range * max);
    }

    private static double normalize360(double deg) {
        double d = deg % 360.0;
        if (d < 0) d += 360.0;
        return d;
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
