package ru.trassa.geometry

import kotlin.math.sqrt

/** Точка или вектор в мировых координатах ARCore (метры). Y направлена вверх по гравитации. */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(k: Float) = Vec3(x * k, y * k, z * k)
    operator fun div(k: Float) = Vec3(x / k, y / k, z / k)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    val lengthSquared get() = this dot this
    val length get() = sqrt(lengthSquared)
    fun normalized(): Vec3 = if (length > 1e-6f) this / length else this
    fun distanceTo(o: Vec3) = (this - o).length

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        val UP = Vec3(0f, 1f, 0f)
    }
}

/** Чистая геометрия без зависимостей от ARCore - легко тестируется. */
object Geometry {

    /**
     * Ближайшая к [p] точка на отрезке AB.
     *
     * Отрезок параметризуем как A + t·(B − A), t ∈ [0, 1].
     * Проекция P на прямую AB даёт t = ((P − A)·(B − A)) / |B − A|².
     * Ограничение t отрезком [0, 1] превращает «расстояние до прямой»
     * в «расстояние до отрезка»: при t < 0 ближайшая точка - A, при t > 1 - B.
     */
    fun closestPointOnSegment(p: Vec3, a: Vec3, b: Vec3): Vec3 {
        val ab = b - a
        val len2 = ab.lengthSquared
        if (len2 < 1e-12f) return a // вырожденный отрезок - это точка
        val t = ((p - a) dot ab / len2).coerceIn(0f, 1f)
        return a + ab * t
    }

    fun distancePointToSegment(p: Vec3, a: Vec3, b: Vec3): Float =
        p.distanceTo(closestPointOnSegment(p, a, b))

    /** Расстояние от точки до ломаной и ближайшая точка на ней. null - точек нет. */
    fun distancePointToPolyline(p: Vec3, points: List<Vec3>): Pair<Float, Vec3>? {
        if (points.isEmpty()) return null
        if (points.size == 1) return p.distanceTo(points[0]) to points[0]
        var best = Float.MAX_VALUE to points[0]
        for (i in 0 until points.size - 1) {
            val c = closestPointOnSegment(p, points[i], points[i + 1])
            val d = p.distanceTo(c)
            if (d < best.first) best = d to c
        }
        return best
    }

    fun polylineLength(points: List<Vec3>): Float =
        points.zipWithNext { a, b -> a.distanceTo(b) }.sum()
}

enum class RouteType { ELECTRIC, WATER, HEATING, LOW_VOLTAGE }

/** Результат проверки «Можно ли тут сверлить?» */
data class DrillCheckResult(
    val distance: Float?,
    val nearestType: RouteType?,
    val nearestPoint: Vec3?,
    val isDangerous: Boolean,
)

object DrillCheck {
    /** Радиус запретной зоны: ±15 см от трассы */
    const val DANGER_RADIUS = 0.15f

    data class Polyline(val type: RouteType, val points: List<Vec3>)

    /** Ищет ближайший отрезок среди всех трасс комнаты. Опасно - строго меньше 15 см. */
    fun evaluate(point: Vec3, routes: List<Polyline>, radius: Float = DANGER_RADIUS): DrillCheckResult {
        var bestDistance = Float.MAX_VALUE
        var bestType: RouteType? = null
        var bestPoint: Vec3? = null
        for (route in routes) {
            val hit = Geometry.distancePointToPolyline(point, route.points) ?: continue
            if (hit.first < bestDistance) {
                bestDistance = hit.first
                bestType = route.type
                bestPoint = hit.second
            }
        }
        if (bestType == null) return DrillCheckResult(null, null, null, false)
        return DrillCheckResult(bestDistance, bestType, bestPoint, bestDistance < radius)
    }
}
