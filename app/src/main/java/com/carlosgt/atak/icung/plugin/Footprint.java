package com.carlosgt.atak.icung.plugin;

/**
 * Where the four corners of the video frame meet the ground, for MISB ST
 * 0601's corner points: what lets a receiving ATAK drape the video on its
 * map. Pure Java, so it is unit-tested on the JVM.
 *
 * The camera is given by three world-frame unit vectors (x east, y north,
 * z up): its axis and the image's right and up directions. Each corner's
 * ray is the axis tilted by half the field of view along both, and is cut
 * with flat ground the given height below the camera - the same model as
 * the frame center in KlvTelemetry.
 */
public final class Footprint {

    // A corner ray must point at least this far below the horizon: closer
    // to it, the ground point runs off to infinity with the slightest tilt
    // and the draped frame would be a sliver to the horizon.
    private static final double MIN_DEPRESSION_DEG = 1.0;

    private Footprint() {
    }

    /**
     * Corners in ST 0601 order: 1 upper-left, 2 upper-right, 3 lower-right,
     * 4 lower-left (as the image is seen).
     *
     * @return {azimuthDeg, groundDistanceM} per corner, azimuth in the same
     *         north as the input vectors; null if any corner ray misses the
     *         ground within maxDistanceM
     */
    public static double[][] corners(double[] axis, double[] right, double[] up,
            double hfovDeg, double vfovDeg, double heightM, double maxDistanceM) {
        double tx = Math.tan(Math.toRadians(hfovDeg / 2));
        double ty = Math.tan(Math.toRadians(vfovDeg / 2));
        double[][] signs = { { -1, 1 }, { 1, 1 }, { 1, -1 }, { -1, -1 } };
        double[][] out = new double[4][];
        for (int i = 0; i < 4; i++) {
            double sx = signs[i][0] * tx;
            double sy = signs[i][1] * ty;
            double e = axis[0] + sx * right[0] + sy * up[0];
            double n = axis[1] + sx * right[1] + sy * up[1];
            double z = axis[2] + sx * right[2] + sy * up[2];
            double horizontal = Math.hypot(e, n);
            if (Math.toDegrees(Math.atan2(-z, horizontal)) < MIN_DEPRESSION_DEG) return null;
            double distance = heightM * horizontal / -z;
            if (distance > maxDistanceM) return null;
            out[i] = new double[] { Math.toDegrees(Math.atan2(e, n)), distance };
        }
        return out;
    }
}
