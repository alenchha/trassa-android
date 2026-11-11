package ru.trassa.ar

import androidx.compose.ui.graphics.Color
import com.google.android.filament.Engine
import com.google.android.filament.MaterialInstance
import dev.romainguy.kotlin.math.Float4
import dev.romainguy.kotlin.math.Mat4
import io.github.sceneview.loaders.MaterialLoader
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.Node
import io.github.sceneview.node.SphereNode
import ru.trassa.geometry.DrillCheck
import ru.trassa.geometry.RouteType
import ru.trassa.geometry.Vec3
import ru.trassa.theme.TrassaColors
import ru.trassa.theme.color
import kotlin.math.abs

/** Точка трассы для отрисовки */
data class RenderPoint(val position: Vec3, val normal: Vec3)
data class RenderRoute(val type: RouteType, val points: List<RenderPoint>)

/**
 * Рисует трассы в AR-сцене: линии, точки, красная зона ±15 см, ориентиры.
 *
 * Узлы создаются вручную внутри корневых узлов (а не Compose-функциями SceneView),
 * чтобы точные матрицы трансформации не перезаписывались при рекомпозиции.
 * Все примитивы - единичные куб и сфера, растянутые матрицей.
 */
class RouteScene(private val engine: Engine, private val materials: MaterialLoader) {

    private var savedRoot: Node? = null
    private var draftRoot: Node? = null
    private var previewRoot: Node? = null
    private var markersRoot: Node? = null

    private val materialCache = HashMap<Color, MaterialInstance>()

    /** Корневые узлы подключаются из ARSceneView { Node(apply = …) } */
    fun attachSaved(node: Node) { savedRoot = node }
    fun attachDraft(node: Node) { draftRoot = node }
    fun attachPreview(node: Node) { previewRoot = node }
    fun attachMarkers(node: Node) { markersRoot = node }

    fun setSaved(routes: List<RenderRoute>, showZones: Boolean) =
        rebuild(savedRoot) { root -> routes.forEach { addRoute(root, it, showZones, showPoints = false) } }

    fun setDrafts(routes: List<RenderRoute>) =
        rebuild(draftRoot) { root -> routes.forEach { addRoute(root, it, showZone = false, showPoints = true) } }

    /** Тонкая линия-подсказка: от последней точки к прицелу или от прицела к трассе */
    fun setPreview(a: Vec3?, b: Vec3?, normal: Vec3, color: Color) = rebuild(previewRoot) { root ->
        if (a == null || b == null) return@rebuild
        segmentTransform(a, b, normal, width = 0.006f, depth = 0.002f, offset = 0.004f)?.let {
            root.addChildNode(cube(material(color.copy(alpha = 0.7f)), it))
        }
    }

    /** Флажки ориентиров */
    fun setMarkers(points: List<Vec3>) = rebuild(markersRoot) { root ->
        points.forEach { root.addChildNode(sphere(material(TrassaColors.Accent), it, 0.03f)) }
    }

    fun setSavedVisible(visible: Boolean) { savedRoot?.isVisible = visible }

    private fun addRoute(root: Node, route: RenderRoute, showZone: Boolean, showPoints: Boolean) {
        val line = material(route.type.color)
        val zone = material(TrassaColors.Danger.copy(alpha = 0.24f))
        val pts = route.points
        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            val n = (a.normal + b.normal).normalized()
            segmentTransform(a.position, b.position, n, LINE_WIDTH, LINE_DEPTH, LINE_OFFSET)?.let { root.addChildNode(cube(line, it)) }
            if (showZone) {
                segmentTransform(a.position, b.position, n, DrillCheck.DANGER_RADIUS * 2, ZONE_DEPTH, ZONE_OFFSET)
                    ?.let { root.addChildNode(cube(zone, it)) }
            }
        }
        for (p in pts) {
            if (showPoints) root.addChildNode(sphere(line, p.position + p.normal * LINE_OFFSET, POINT_RADIUS))
            // Скругление зоны на концах и изломах - плоский диск радиусом 15 см
            if (showZone) root.addChildNode(cube(zone, discTransform(p.position, p.normal)).also { it.isVisible = true })
        }
    }

    private fun rebuild(root: Node?, build: (Node) -> Unit) {
        root ?: return
        root.childNodes.toList().forEach { it.destroy() }
        root.clearChildNodes()
        build(root)
    }

    private fun material(color: Color): MaterialInstance =
        materialCache.getOrPut(color) { materials.createColorInstance(color, metallic = 0f, roughness = 1f, reflectance = 0f) }

    private fun cube(material: MaterialInstance, transform: Mat4): Node =
        CubeNode(engine = engine, size = Size(1f), center = Position(0f), materialInstance = material).also { it.transform = transform }

    private fun sphere(material: MaterialInstance, at: Vec3, radius: Float): Node =
        SphereNode(engine = engine, radius = radius, center = Position(0f), materialInstance = material).also {
            it.position = Position(at.x, at.y, at.z)
        }

    companion object {
        const val LINE_WIDTH = 0.012f
        const val LINE_DEPTH = 0.003f
        const val POINT_RADIUS = 0.014f
        const val ZONE_DEPTH = 0.001f
        /** Линия приподнята над стеной на 3 мм, зона - на 1 мм, чтобы не мерцали со стеной */
        const val LINE_OFFSET = 0.003f
        const val ZONE_OFFSET = 0.001f

        /**
         * Матрица, превращающая единичный куб в «полоску» от A до B на стене.
         *
         * Ортонормированный базис: y - вдоль отрезка (длина), z - нормаль стены,
         * ортогонализованная к y (толщина), x = y × z - поперёк по стене (ширина).
         * Столбцы матрицы - оси, умноженные на размеры, последний столбец - центр.
         */
        fun segmentTransform(a: Vec3, b: Vec3, normal: Vec3, width: Float, depth: Float, offset: Float): Mat4? {
            val d = b - a
            val length = d.length
            if (length < 1e-4f) return null
            val y = d / length
            // Грам - Шмидт: убираем из нормали составляющую вдоль отрезка
            var z = normal - y * (normal dot y)
            if (z.length < 1e-3f) z = if (abs(y.y) < 0.9f) y cross Vec3.UP else y cross Vec3(1f, 0f, 0f)
            z = z.normalized()
            val x = (y cross z).normalized()
            val c = (a + b) / 2f + z * offset
            return Mat4(
                Float4(x.x * width, x.y * width, x.z * width, 0f),
                Float4(y.x * length, y.y * length, y.z * length, 0f),
                Float4(z.x * depth, z.y * depth, z.z * depth, 0f),
                Float4(c.x, c.y, c.z, 1f),
            )
        }

        /** Плоский квадрат 30×30 см вдоль стены - «шляпка» зоны в точке */
        fun discTransform(p: Vec3, normal: Vec3): Mat4 {
            val n = normal.normalized()
            var x = Vec3.UP cross n
            if (x.length < 1e-3f) x = Vec3(1f, 0f, 0f)
            x = x.normalized()
            val y = n cross x
            val s = DrillCheck.DANGER_RADIUS * 2
            val c = p + n * ZONE_OFFSET
            return Mat4(
                Float4(x.x * s, x.y * s, x.z * s, 0f),
                Float4(y.x * s, y.y * s, y.z * s, 0f),
                Float4(n.x * ZONE_DEPTH, n.y * ZONE_DEPTH, n.z * ZONE_DEPTH, 0f),
                Float4(c.x, c.y, c.z, 1f),
            )
        }
    }
}
