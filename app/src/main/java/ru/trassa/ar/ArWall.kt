package ru.trassa.ar

import com.google.ar.core.Camera
import com.google.ar.core.DepthPoint
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Point
import com.google.ar.core.Pose
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import ru.trassa.geometry.Vec3
import kotlin.math.abs

/** Точка на стене под прицелом */
data class WallHit(val position: Vec3, val normal: Vec3)

fun Pose.position() = Vec3(tx(), ty(), tz())
private fun FloatArray.toVec3() = Vec3(this[0], this[1], this[2])

/**
 * Hit test из точки прицела (центр экрана) в стену.
 *
 * Порядок приоритета:
 * 1. Найденная вертикальная плоскость - самый стабильный результат.
 * 2. Точка глубины (Depth API) с вертикальной нормалью - работает на гладких стенах,
 *    где плоскость ещё не найдена.
 * 3. Точка облака с оценённой нормалью поверхности.
 * У ARCore ось Y позы попадания направлена по нормали поверхности.
 */
fun hitWall(frame: Frame, x: Float, y: Float): WallHit? {
    val hits = frame.hitTest(x, y)
    val camera = frame.camera.pose.position()

    hits.firstOrNull { h ->
        val t = h.trackable
        t is Plane && t.type == Plane.Type.VERTICAL && t.subsumedBy == null && t.isPoseInPolygon(h.hitPose)
    }?.let { return makeHit(it, camera) }

    hits.firstOrNull { h -> h.trackable is DepthPoint && isWallNormal(h) }?.let { return makeHit(it, camera) }

    hits.firstOrNull { h ->
        val t = h.trackable
        t is Point && t.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL && isWallNormal(h)
    }?.let { return makeHit(it, camera) }

    return null
}

/** Нормаль почти горизонтальна - значит, поверхность вертикальная (стена) */
private fun isWallNormal(h: HitResult) = abs(h.hitPose.yAxis[1]) < 0.5f

private fun makeHit(h: HitResult, camera: Vec3): WallHit {
    val p = h.hitPose.position()
    var n = h.hitPose.yAxis.toVec3().normalized()
    // Нормаль должна смотреть на пользователя, иначе линии окажутся «внутри» стены
    if ((n dot (camera - p)) < 0f) n = n * -1f
    return WallHit(p, n)
}

/** Понятная пользователю интерпретация состояния ARCore */
data class TrackingHint(val level: Int, val quality: String, val message: String, val canPlace: Boolean) {
    companion object {
        fun make(camera: Camera?, hasWall: Boolean, verticalPlanes: Int): TrackingHint {
            if (camera == null) return TrackingHint(0, "Качество: нет трекинга", "Запуск камеры…", false)
            if (camera.trackingState != TrackingState.TRACKING) {
                val message = when (camera.trackingFailureReason) {
                    TrackingFailureReason.EXCESSIVE_MOTION -> "Слишком быстро. Двигайте телефон медленнее"
                    TrackingFailureReason.INSUFFICIENT_LIGHT -> "Темно. Включите свет"
                    TrackingFailureReason.INSUFFICIENT_FEATURES -> "Мало деталей. Наведите на участок с текстурой или углом"
                    TrackingFailureReason.CAMERA_UNAVAILABLE -> "Камера занята другим приложением"
                    TrackingFailureReason.BAD_STATE -> "Ошибка AR. Закройте экран и откройте снова"
                    else -> "Запуск AR… Медленно поводите телефоном по комнате"
                }
                return TrackingHint(1, "Качество: плохое", message, false)
            }
            val (level, quality) = when {
                verticalPlanes >= 2 -> 4 to "Качество: отличное"
                verticalPlanes == 1 -> 3 to "Качество: хорошее"
                else -> 2 to "Качество: среднее"
            }
            val message = if (hasWall) "Медленно ведите телефоном\nпо стене" else "Наведите прицел на стену.\nСтена должна быть в 0,3-3 м"
            return TrackingHint(level, quality, message, hasWall)
        }
    }
}
