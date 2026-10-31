package com.carlosgt.atak.icung.plugin;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Builds MISB ST 0601 (UAS Datalink Local Set) KLV packets from a handheld
 * camera's telemetry. Pure Java and stateless, so it is unit-tested on the
 * JVM.
 *
 * Mapping for a handheld phone: the "platform" is the operator, taken as
 * level and facing where the camera points; the "sensor" is the phone's
 * camera, so its pitch and twist are the sensor-relative angles. Every
 * item of the MISB ST 0902 minimum set (MISMMS) is sent:
 * <pre>
 *   2  Precision Time Stamp        uint64  µs since the Unix epoch
 *   3  Mission ID                  string  operator's choice, left out if empty
 *   5  Platform Heading            uint16  0..360        camera azimuth (true)
 *   6  Platform Pitch              int16   ±20           always 0 (operator level)
 *   7  Platform Roll               int16   ±50           always 0
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
 *  20  Sensor Relative Roll        uint32  0..360        image twist, clockwise from behind
 *  21  Slant Range                 uint32  0..5e6 m      sensor to frame center
 *  22  Target Width                uint16  0..10000 m    frame width at the frame center
 *  23  Frame Center Latitude       int32   ±90           where the camera looks
 *  24  Frame Center Longitude      int32   ±180
 *  25  Frame Center Elevation      uint16  -900..19000 m (MSL)
 * 26-33 Offset Corner Lat/Lon 1-4  int16   ±0.075        from the frame center
 *  48  Security Local Set          set     MISB ST 0102, see security()
 *  65  UAS Datalink LS Version     uint8   17
 * 82-89 Corner Lat/Lon 1-4 (Full)  int32   ±90 / ±180    same corners, absolute
 *  94  MIIS Core Identifier        bytes   MISB ST 1204, see miisCoreId()
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
    private static final int SECURITY_VERSION = 12;
    // ST 0102 country coding method: GENC three letter (ESP, FRA...)
    private static final int GENC_THREE_LETTER = 14;
    /** ST 0102 tag 1 values, UNCLASSIFIED (1) to TOP SECRET (5) */
    public static final int UNCLASSIFIED = 1;
    public static final int TOP_SECRET = 5;

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
        public String missionId;
        /** ST 0102 classification, UNCLASSIFIED..TOP_SECRET */
        public int classification = UNCLASSIFIED;
        /** GENC three-letter code of the classifying (and object) country */
        public String country;
        /** image twist about the camera axis, clockwise seen from behind; NaN if unknown */
        public double rollDeg = Double.NaN;
        /** width of the frame on the ground at the frame center; NaN if unknown */
        public double targetWidthM = Double.NaN;
        /** MISB ST 1204 MIIS Core Identifier, see miisCoreId(); null to leave out */
        public byte[] miisCoreId;
    }

    private KlvEncoder() {
    }

    public static byte[] encode(Telemetry t) {
        ByteArrayOutputStream value = new ByteArrayOutputStream();
        putItem(value, 2, uint(t.timeMicros, 8));
        if (t.missionId != null && !t.missionId.isEmpty()) {
            putItem(value, 3, utf8(t.missionId));
        }
        if (!Double.isNaN(t.azimuthDeg)) {
            putItem(value, 5, uint(scaleUnsigned(normalize360(t.azimuthDeg), 360, 0xFFFFL), 2));
        }
        putItem(value, 6, uint(0, 2));
        putItem(value, 7, uint(0, 2));
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
        if (!Double.isNaN(t.rollDeg)) {
            putItem(value, 20, uint(scaleUnsigned(normalize360(t.rollDeg), 360, 0xFFFFFFFFL), 4));
        }
        if (!Double.isNaN(t.slantRange)) {
            putItem(value, 21, uint(scaleUnsigned(clamp(t.slantRange, 0, 5_000_000),
                    5_000_000, 0xFFFFFFFFL), 4));
        }
        if (!Double.isNaN(t.targetWidthM)) {
            putItem(value, 22, uint(scaleUnsigned(clamp(t.targetWidthM, 0, 10_000),
                    10_000, 0xFFFFL), 2));
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
        if (t.country != null && !t.country.isEmpty()) {
            putItem(value, 48, security(t));
        }
        putItem(value, 65, uint(LS_VERSION, 1));
        if (hasCorners(t)) {
            for (int i = 0; i < 4; i++) {
                putItem(value, 82 + 2 * i, uint(scaleSigned(t.cornerLat[i], 90), 4));
                putItem(value, 83 + 2 * i, uint(scaleSigned(t.cornerLon[i], 180), 4));
            }
        }
        if (t.miisCoreId != null) {
            putItem(value, 94, t.miisCoreId);
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
     * MISB ST 0102 Security Local Set, embedded as tag 48: the items it
     * makes mandatory and nothing else. STANAG 4609 needs every stream
     * marked; the object country (tag 13, UTF-16) is taken to be the
     * classifying one - a handheld camera films where its operator is.
     */
    static byte[] security(Telemetry t) {
        ByteArrayOutputStream ls = new ByteArrayOutputStream();
        int classification = Math.max(UNCLASSIFIED, Math.min(TOP_SECRET, t.classification));
        putItem(ls, 1, uint(classification, 1));
        putItem(ls, 2, uint(GENC_THREE_LETTER, 1));
        putItem(ls, 3, utf8("//" + t.country));
        putItem(ls, 12, uint(GENC_THREE_LETTER, 1));
        putItem(ls, 13, t.country.getBytes(StandardCharsets.UTF_16BE));
        putItem(ls, 22, uint(SECURITY_VERSION, 2));
        return ls.toByteArray();
    }

    /**
     * MISB ST 1204 MIIS Core Identifier in its binary form: version 1, the
     * usage byte (sensor and platform ID types, no window or minor ID),
     * then the sensor and platform UUIDs. Both are "virtual" IDs (type 2):
     * generated, not read from hardware, but stable - name-based (version
     * 5) UUIDs of the ATAK device UID, the platform, and of that UID plus
     * the camera, the sensor - so the same phone and camera always carry
     * the same identifier.
     */
    public static byte[] miisCoreId(String deviceUid, String cameraName) {
        final int virtual = 2;
        byte[] out = new byte[34];
        out[0] = 1;
        out[1] = (byte) ((virtual << 5) | (virtual << 3));
        System.arraycopy(nameUuid("icung-sensor:" + deviceUid + ":" + cameraName), 0, out, 2, 16);
        System.arraycopy(nameUuid("icung-platform:" + deviceUid), 0, out, 18, 16);
        return out;
    }

    // RFC 4122 version 5 (SHA-1, name-based) UUID bytes in the URL namespace.
    static byte[] nameUuid(String name) {
        final byte[] urlNamespace = {
                0x6b, (byte) 0xa7, (byte) 0xb8, 0x11, (byte) 0x9d, (byte) 0xad, 0x11, (byte) 0xd1,
                (byte) 0x80, (byte) 0xb4, 0x00, (byte) 0xc0, 0x4f, (byte) 0xd4, 0x30, (byte) 0xc8 };
        try {
            java.security.MessageDigest sha1 = java.security.MessageDigest.getInstance("SHA-1");
            sha1.update(urlNamespace);
            sha1.update(name.getBytes(StandardCharsets.UTF_8));
            byte[] h = sha1.digest();
            byte[] uuid = new byte[16];
            System.arraycopy(h, 0, uuid, 0, 16);
            uuid[6] = (byte) ((uuid[6] & 0x0F) | 0x50);
            uuid[8] = (byte) ((uuid[8] & 0x3F) | 0x80);
            return uuid;
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

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

    /**
     * ST 0601 checksum: a running 16-bit sum of every byte from the start
     * of the key through the checksum item's length byte, even-indexed
     * bytes weighted as the high byte of a big-endian word. Not a CRC.
     */
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
