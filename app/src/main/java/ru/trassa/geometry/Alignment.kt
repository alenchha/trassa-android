package ru.trassa.geometry

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Преобразование из системы координат записи в текущую AR-сессию:
 * поворот вокруг вертикали на угол [yaw] (радианы) и сдвиг [translation].
 *
 * Почему только вокруг вертикали: ARCore в каждой сессии направляет ось Y
 * против гравитации, поэтому «верх» в обеих сессиях совпадает. Отличаются
 * только начало координат и направление «вперёд» - это 4 степени свободы.
 */
data class RigidTransformY(val yaw: Float, val translation: Vec3) {

    /** Применяет преобразование: R(yaw)·p + t */
    fun apply(p: Vec3): Vec3 = rotate(p) + translation

    /** Только поворот - для нормалей стены */
    fun rotate(v: Vec3): Vec3 {
        val c = cos(yaw)
        val s = sin(yaw)
        // Поворот вокруг оси Y (правая система координат):
        // x' =  x·cos + z·sin,  z' = −x·sin + z·cos
        return Vec3(v.x * c + v.z * s, v.y, -v.x * s + v.z * c)
    }

    companion object {
        val IDENTITY = RigidTransformY(0f, Vec3.ZERO)
    }
}

/** Итог совмещения ориентиров */
sealed interface AlignmentResult {
    /** Совмещение найдено. [rmsError] и [maxError] - погрешность по ориентирам, в метрах. */
    data class Success(val transform: RigidTransformY, val rmsError: Float, val maxError: Float) : AlignmentResult {
        /** Погрешность больше 5 см - лучше отметить ориентиры заново */
        val isReliable get() = maxError <= MAX_RELIABLE_ERROR
    }

    /** Нужно минимум 2 пары точек */
    data object NotEnoughPoints : AlignmentResult

    /** Ориентиры почти на одной вертикали - поворот не определить */
    data object PointsTooClose : AlignmentResult

    companion object {
        const val MAX_RELIABLE_ERROR = 0.05f
    }
}

/**
 * Совмещение по реперным точкам («ориентирам»).
 *
 * Дано: ориентиры в координатах записи oldᵢ и те же ориентиры, отмеченные
 * в новой сессии, newᵢ. Ищем yaw и t, минимизирующие Σ |R(yaw)·oldᵢ + t − newᵢ|².
 *
 * Решение (вариант метода Кабша для поворота вокруг одной оси):
 * 1. Центрируем: aᵢ = oldᵢ − ō, bᵢ = newᵢ − n̄ (ō, n̄ - центры масс).
 *    Оптимальный сдвиг выражается через поворот, поэтому остаётся найти только угол.
 * 2. В горизонтальной плоскости XZ сумма Σ bᵢ·R·aᵢ = cos·C + sin·S, где
 *    C = Σ (aₓbₓ + a_z b_z),  S = Σ (a_z bₓ − aₓ b_z).
 *    Максимум этого выражения достигается при yaw = atan2(S, C).
 * 3. Сдвиг: t = n̄ − R·ō.
 * Вертикальная координата в поворот не входит и компенсируется сдвигом по Y.
 */
object Alignment {

    /** Минимальный горизонтальный разброс ориентиров (м), при котором угол надёжно определяется */
    const val MIN_HORIZONTAL_SPREAD = 0.2f

    fun solve(old: List<Vec3>, new: List<Vec3>): AlignmentResult {
        require(old.size == new.size) { "Число ориентиров должно совпадать" }
        if (old.size < 2) return AlignmentResult.NotEnoughPoints

        val n = old.size.toFloat()
        val oldCenter = old.fold(Vec3.ZERO) { acc, p -> acc + p } / n
        val newCenter = new.fold(Vec3.ZERO) { acc, p -> acc + p } / n

        var c = 0f
        var s = 0f
        var spread = 0f
        for (i in old.indices) {
            val a = old[i] - oldCenter
            val b = new[i] - newCenter
            c += a.x * b.x + a.z * b.z
            s += a.z * b.x - a.x * b.z
            spread = max(spread, sqrt(a.x * a.x + a.z * a.z))
        }
        // Если все ориентиры на одной вертикали (например, верх и низ одного угла),
        // поворот вокруг этой вертикали ничем не ограничен.
        if (spread < MIN_HORIZONTAL_SPREAD / 2) return AlignmentResult.PointsTooClose

        val yaw = atan2(s, c)
        val rotationOnly = RigidTransformY(yaw, Vec3.ZERO)
        val transform = RigidTransformY(yaw, newCenter - rotationOnly.apply(oldCenter))

        // Погрешность: насколько преобразованные старые ориентиры не попали в новые
        val errors = old.indices.map { transform.apply(old[it]).distanceTo(new[it]) }
        val rms = sqrt(errors.sumOf { (it * it).toDouble() }.toFloat() / n)
        return AlignmentResult.Success(transform, rms, errors.max())
    }
}
