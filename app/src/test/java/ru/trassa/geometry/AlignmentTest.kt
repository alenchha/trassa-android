package ru.trassa.geometry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.random.Random

class AlignmentTest {
    private val eps = 1e-4f

    // Три ориентира: два угла окна и угол комнаты
    private val refs = listOf(Vec3(0f, 1f, 0f), Vec3(1.2f, 1f, 0f), Vec3(1.2f, 2.4f, -0.8f))

    private fun success(r: AlignmentResult) = r as AlignmentResult.Success

    @Test fun identityWhenSessionsMatch() {
        val r = success(Alignment.solve(refs, refs))
        assertEquals(0f, r.transform.yaw, eps)
        assertEquals(0f, r.maxError, eps)
    }

    @Test fun recoversRotationAndTranslation() {
        val truth = RigidTransformY((PI / 3).toFloat(), Vec3(2f, -0.5f, 3f))
        val moved = refs.map(truth::apply)
        val r = success(Alignment.solve(refs, moved))
        assertEquals(truth.yaw, r.transform.yaw, eps)
        assertEquals(truth.translation.x, r.transform.translation.x, eps)
        assertEquals(truth.translation.y, r.transform.translation.y, eps)
        assertEquals(truth.translation.z, r.transform.translation.z, eps)
        assertTrue(r.isReliable)
    }

    @Test fun rotationBy90DegreesMapsXToMinusZ() {
        val t = RigidTransformY((PI / 2).toFloat(), Vec3.ZERO)
        val p = t.apply(Vec3(1f, 0f, 0f))
        assertEquals(0f, p.x, eps)
        assertEquals(-1f, p.z, eps)
    }

    @Test fun routePointFollowsReferences() {
        // Точка трассы, записанная в старой сессии, должна попасть на то же место
        val truth = RigidTransformY(-2.1f, Vec3(-1f, 0.2f, 0.7f))
        val r = success(Alignment.solve(refs, refs.map(truth::apply)))
        val routePoint = Vec3(0.6f, 1.4f, 0f)
        assertEquals(0f, r.transform.apply(routePoint).distanceTo(truth.apply(routePoint)), eps)
    }

    @Test fun noisyTapsGiveCentimeterError() {
        // Пользователь отмечает ориентиры с ошибкой до 1 см
        val truth = RigidTransformY(0.7f, Vec3(0.3f, 0f, -1.5f))
        val rnd = Random(42)
        val noisy = refs.map { truth.apply(it) + Vec3(rnd.nextFloat() * 0.02f - 0.01f, 0f, rnd.nextFloat() * 0.02f - 0.01f) }
        val r = success(Alignment.solve(refs, noisy))
        assertTrue("погрешность ${r.maxError}", r.maxError < 0.02f)
        assertTrue(r.isReliable)
    }

    @Test fun wrongReferenceIsUnreliable() {
        // Третий ориентир отмечен на 30 см не там
        val moved = refs.mapIndexed { i, p -> if (i == 2) p + Vec3(0.3f, 0f, 0f) else p }
        assertFalse(success(Alignment.solve(refs, moved)).isReliable)
    }

    @Test fun needsAtLeastTwoPoints() {
        assertEquals(AlignmentResult.NotEnoughPoints, Alignment.solve(refs.take(1), refs.take(1)))
    }

    @Test fun verticalOnlyPointsAreRejected() {
        // Верх и низ одного угла - поворот вокруг этой вертикали не определить
        val vertical = listOf(Vec3(1f, 0f, 1f), Vec3(1f, 2.5f, 1f))
        assertEquals(AlignmentResult.PointsTooClose, Alignment.solve(vertical, vertical))
    }
}
