package ru.trassa.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeometryTest {
    private val eps = 1e-5f
    private val a = Vec3(0f, 0f, 0f)
    private val b = Vec3(0f, 1f, 0f)

    @Test fun pointProjectsInsideSegment() {
        assertEquals(0.10f, Geometry.distancePointToSegment(Vec3(0.10f, 0.5f, 0f), a, b), eps)
    }

    @Test fun pointBeyondEndClampsToEndpoint() {
        // √(0.3² + 0.4²) = 0.5
        assertEquals(0.5f, Geometry.distancePointToSegment(Vec3(0.4f, 1.3f, 0f), a, b), eps)
        assertEquals(b, Geometry.closestPointOnSegment(Vec3(0.4f, 1.3f, 0f), a, b))
    }

    @Test fun pointBeforeStartClampsToStart() {
        assertEquals(0.2f, Geometry.distancePointToSegment(Vec3(0f, -0.2f, 0f), a, b), eps)
    }

    @Test fun degenerateSegmentIsPoint() {
        assertEquals(5f, Geometry.distancePointToSegment(Vec3(3f, 4f, 0f), a, a), eps)
    }

    @Test fun polylineLengthAndDistance() {
        val pts = listOf(a, b, Vec3(0.5f, 1f, 0f))
        assertEquals(1.5f, Geometry.polylineLength(pts), eps)
        assertEquals(0.08f, Geometry.distancePointToPolyline(Vec3(0.3f, 0.92f, 0f), pts)!!.first, eps)
    }

    @Test fun drillCheckDangerBelow15cm() {
        val routes = listOf(
            DrillCheck.Polyline(RouteType.WATER, listOf(Vec3(1f, 0f, 0f), Vec3(1f, 1f, 0f))),
            DrillCheck.Polyline(RouteType.ELECTRIC, listOf(a, b)),
        )
        val r = DrillCheck.evaluate(Vec3(0.08f, 0.5f, 0f), routes)
        assertTrue(r.isDangerous)
        assertEquals(RouteType.ELECTRIC, r.nearestType)
        assertEquals(0.08f, r.distance!!, eps)
    }

    @Test fun drillCheckSafeAt15cm() {
        val routes = listOf(DrillCheck.Polyline(RouteType.HEATING, listOf(a, b)))
        assertFalse(DrillCheck.evaluate(Vec3(0.15f, 0.5f, 0f), routes).isDangerous)
    }

    @Test fun drillCheckWithoutRoutes() {
        val r = DrillCheck.evaluate(Vec3.ZERO, emptyList())
        assertFalse(r.isDangerous)
        assertNull(r.distance)
    }
}
