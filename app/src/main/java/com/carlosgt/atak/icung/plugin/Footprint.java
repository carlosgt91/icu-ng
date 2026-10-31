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

    /**
     * Twist of the picture about the camera axis, for ST 0601 tag 20: 0 with
     * the image's right edge level, positive turning clockwise as seen from
     * behind the camera (the right edge dips). Measured against the level
     * reference frame of the camera axis itself (right = axis x world up,
     * up = right x axis), so it holds however steeply the camera points,
     * down to a few degrees from straight down or up.
     *
     * @return degrees in (-180, 180], or NaN with the camera within about a
     *         degree of vertical, where no level right direction exists
     */
    public static double roll(double[] axis, double[] right) {
        // Level right: axis x (0, 0, 1) = (axis.n, -axis.e, 0)
        double r0e = axis[1];
        double r0n = -axis[0];
        double len = Math.hypot(r0e, r0n);
        if (len < 0.02) return Double.NaN;
        r0e /= len;
        r0n /= len;
        // Level up: right0 x axis
        double u0e = r0n * axis[2];
        double u0n = -r0e * axis[2];
        double u0z = r0e * axis[1] - r0n * axis[0];
        double alongRight = right[0] * r0e + right[1] * r0n;
        double alongUp = right[0] * u0e + right[1] * u0n + right[2] * u0z;
        return Math.toDegrees(Math.atan2(-alongUp, alongRight));
    }
}
