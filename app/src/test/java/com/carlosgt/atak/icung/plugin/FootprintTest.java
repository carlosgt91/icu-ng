package com.carlosgt.atak.icung.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class FootprintTest {

    private static final double[] EAST = { 1, 0, 0 };
    private static final double[] NORTH = { 0, 1, 0 };
    private static final double[] DOWN = { 0, 0, -1 };

    @Test
    public void straightDownWithNorthUpIsARectangleAroundTheCamera() {
        // 90 x 90 deg from 10 m: each corner 10 m east/west and north/south.
        double[][] c = Footprint.corners(DOWN, EAST, NORTH, 90, 90, 10, 500);
        assertNotNull(c);
        double d = Math.hypot(10, 10);
        assertCorner(c[0], 315, d); // upper-left: north-west
        assertCorner(c[1], 45, d);  // upper-right: north-east
        assertCorner(c[2], 135, d); // lower-right: south-east
        assertCorner(c[3], 225, d); // lower-left: south-west
    }

    @Test
    public void tiltedNorthPutsTheTopEdgeFarther() {
        // Axis 45 deg below the horizon, to the north; image up is the
        // axis rotated 90 deg up (north and up), right is east.
        double s = Math.sqrt(0.5);
        double[] axis = { 0, s, -s };
        double[] up = { 0, s, s };
        double[][] c = Footprint.corners(axis, EAST, up, 40, 30, 20, 500);
        assertNotNull(c);
        // Top corners are the far ones, bottom ones the near ones.
        assertEquals(true, c[0][1] > c[3][1]);
        assertEquals(true, c[1][1] > c[2][1]);
        // Left/right symmetric about north.
        assertEquals(c[0][1], c[1][1], 1e-9);
        assertEquals(-c[0][0], c[1][0] > 180 ? c[1][0] - 360 : c[1][0], 1e-9);
        // Bottom edge: 45 + 15 deg below the horizon.
        double nearAlongAxis = 20 / Math.tan(Math.toRadians(60));
        assertEquals(nearAlongAxis, c[3][1] * Math.cos(Math.toRadians(Math.abs(c[3][0]))), 0.5);
    }

    @Test
    public void levelCameraHasNoFootprint() {
        double[] up = { 0, 0, 1 };
        assertNull(Footprint.corners(NORTH, EAST, up, 60, 45, 1.5, 500));
    }

    @Test
    public void footprintBeyondMaxDistanceIsLeftOut() {
        // 10 deg below the horizon with a 30 deg vertical FOV: the top edge
        // is above it.
        double a = Math.toRadians(-10);
        double[] axis = { 0, Math.cos(a), Math.sin(a) };
        double[] up = { 0, -Math.sin(a), Math.cos(a) };
        assertNull(Footprint.corners(axis, EAST, up, 40, 30, 1.5, 500));
        // Pointing steeply down it fits, but not under a tiny limit.
        assertNotNull(Footprint.corners(DOWN, EAST, NORTH, 60, 45, 1.5, 500));
        assertNull(Footprint.corners(DOWN, EAST, NORTH, 60, 45, 1.5, 0.5));
    }

    @Test
    public void rollIsZeroWithTheImageLevel() {
        assertEquals(0, Footprint.roll(new double[] { 0, 1, 0 }, new double[] { 1, 0, 0 }), 1e-9);
    }

    @Test
    public void rollIsPositiveClockwiseFromBehind() {
        double c = Math.cos(Math.toRadians(30));
        double s = Math.sin(Math.toRadians(30));
        // right edge dips: clockwise seen from behind the camera
        assertEquals(30, Footprint.roll(new double[] { 0, 1, 0 }, new double[] { c, 0, -s }), 1e-9);
        assertEquals(-30, Footprint.roll(new double[] { 0, 1, 0 }, new double[] { c, 0, s }), 1e-9);
    }

    @Test
    public void rollHoldsLookingSteeplyDown() {
        // facing north, 85 degrees below the horizon (the phone over the ground)
        double ca = Math.cos(Math.toRadians(85));
        double sa = Math.sin(Math.toRadians(85));
        double[] axis = { 0, ca, -sa };
        double[] levelRight = { 1, 0, 0 };
        double[] levelUp = { 0, sa, ca }; // levelRight x axis
        assertEquals(0, Footprint.roll(axis, levelRight), 1e-9);
        double c = Math.cos(Math.toRadians(20));
        double s = Math.sin(Math.toRadians(20));
        double[] twisted = { c * levelRight[0] - s * levelUp[0], c * levelRight[1] - s * levelUp[1],
                c * levelRight[2] - s * levelUp[2] };
        assertEquals(20, Footprint.roll(axis, twisted), 1e-9);
    }

    @Test
    public void rollIsUndefinedStraightDown() {
        assertEquals(Double.NaN, Footprint.roll(new double[] { 0, 0, -1 }, new double[] { 1, 0, 0 }), 0);
    }

    private static void assertCorner(double[] c, double azimuth, double distance) {
        assertEquals(azimuth, (c[0] + 360) % 360, 1e-6);
        assertEquals(distance, c[1], 1e-6);
    }
}
