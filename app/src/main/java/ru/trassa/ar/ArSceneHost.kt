package ru.trassa.ar

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.google.ar.core.Config
import com.google.ar.core.Plane
import com.google.ar.core.TrackingState
import io.github.sceneview.ar.ARSceneView
import io.github.sceneview.capture.SceneRecorder
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader

/** Состояние AR для интерфейса (обновляется ~15 раз в секунду) */
class ArState {
    var hit by mutableStateOf<WallHit?>(null)
    var hint by mutableStateOf(TrackingHint.make(null, hasWall = false, verticalPlanes = 0))
    var isTracking by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
}

/**
 * Общий AR-экран для записи и просмотра.
 * - ищет только вертикальные плоскости (трассы идут по стенам);
 * - включает Depth API, если телефон его поддерживает (SceneView сам отключит на остальных) -
 *   так прицел «цепляется» и за гладкие стены, где плоскость ещё не найдена;
 * - каждый кадр делает hit test из центра экрана, где нарисован прицел.
 */
@Composable
fun ArSceneHost(
    state: ArState,
    recorder: SceneRecorder,
    showPlanes: Boolean,
    onSceneReady: (RouteScene) -> Unit,
    onFrame: () -> Unit,
) {
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val routeScene = remember(engine) { RouteScene(engine, materialLoader) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val counter = remember { intArrayOf(0) }

    ARSceneView(
        modifier = Modifier.fillMaxSize().onSizeChanged { size = it },
        engine = engine,
        materialLoader = materialLoader,
        planeFindingMode = Config.PlaneFindingMode.VERTICAL,
        depthMode = Config.DepthMode.AUTOMATIC,
        planeRenderer = showPlanes,
        surfaceMirrorer = recorder.surfaceMirrorer,
        onSessionFailed = { e -> state.error = "Ошибка AR: ${e.message ?: e.javaClass.simpleName}" },
        onSessionUpdated = { session, frame ->
            // Интерфейсу хватает ~15 обновлений в секунду
            counter[0]++
            if (counter[0] % 4 == 0 && size.width > 0) {
                val tracking = frame.camera.trackingState == TrackingState.TRACKING
                val hit = if (tracking) hitWall(frame, size.width / 2f, size.height / 2f) else null
                val planes = session.getAllTrackables(Plane::class.java)
                    .count { it.type == Plane.Type.VERTICAL && it.trackingState == TrackingState.TRACKING && it.subsumedBy == null }
                state.isTracking = tracking
                state.hit = hit
                state.hint = TrackingHint.make(frame.camera, hit != null, planes)
                onFrame()
            }
        },
    ) {
        Node(apply = { routeScene.attachSaved(this) })
        Node(apply = { routeScene.attachDraft(this) })
        Node(apply = { routeScene.attachPreview(this) })
        Node(apply = { routeScene.attachMarkers(this); onSceneReady(routeScene) })
    }
}
