package ru.trassa.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.sceneview.capture.rememberSceneRecorder
import ru.trassa.R
import ru.trassa.ar.ArSceneHost
import ru.trassa.ar.ArState
import ru.trassa.ar.RouteScene
import ru.trassa.data.RoomDetails
import ru.trassa.geometry.Alignment as RefAlignment
import ru.trassa.geometry.AlignmentResult
import ru.trassa.geometry.DrillCheck
import ru.trassa.geometry.DrillCheckResult
import ru.trassa.geometry.RigidTransformY
import ru.trassa.geometry.Vec3
import ru.trassa.theme.TrassaColors
import ru.trassa.theme.TrassaDimens
import ru.trassa.theme.TrassaType
import ru.trassa.theme.color
import ru.trassa.theme.objectNoun
import ru.trassa.theme.title
import ru.trassa.ui.LocalRepository
import ru.trassa.ui.components.CornerBrackets
import ru.trassa.ui.components.DrillResultCard
import ru.trassa.ui.components.IconSquareButton
import ru.trassa.ui.components.PrimaryButton
import ru.trassa.ui.components.Reticle
import ru.trassa.ui.components.SecondaryButton
import ru.trassa.util.Formatters
import ru.trassa.util.Haptics

private enum class ViewPhase { ALIGN, OVERVIEW, CHECK }

/** Экраны 10–13 · Совмещение по ориентирам, просмотр трасс, проверка сверления */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScreen(nav: NavHostController, roomId: Long, focusRouteId: Long?) {
    val repo = LocalRepository.current
    val context = LocalContext.current
    val haptics = remember { Haptics(context) }
    val ar = remember { ArState() }
    val recorder = rememberSceneRecorder()
    var scene by remember { mutableStateOf<RouteScene?>(null) }
    var room by remember { mutableStateOf<RoomDetails?>(null) }
    var phase by remember { mutableStateOf(ViewPhase.ALIGN) }
    val alignPoints = remember { mutableStateListOf<Vec3>() }
    var transform by remember { mutableStateOf(RigidTransformY.IDENTITY) }
    var alignError by remember { mutableStateOf<Float?>(null) }
    var drill by remember { mutableStateOf<DrillCheckResult?>(null) }
    val wasDangerous = remember { booleanArrayOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var showList by remember { mutableStateOf(false) }
    var checkTip by remember { mutableStateOf(false) }

    LaunchedEffect(roomId) {
        room = repo.dao.room(roomId)
        if (room?.hasReferences != true) message = "У этой комнаты нет ориентиров, совместить её в AR нельзя. Откройте схему в PDF или запишите комнату заново."
    }
    // Трассы показываем только после совмещения: до него координаты не совпадают с комнатой
    LaunchedEffect(scene, room, transform, phase) {
        val r = room ?: return@LaunchedEffect
        scene?.setSaved(r.renderRoutes(transform), showZones = true)
        scene?.setSavedVisible(phase != ViewPhase.ALIGN)
        scene?.setMarkers(if (phase == ViewPhase.ALIGN) alignPoints.toList() else emptyList())
    }
    LaunchedEffect(scene, alignPoints.toList()) { if (phase == ViewPhase.ALIGN) scene?.setMarkers(alignPoints.toList()) }

    val polylines = remember(room, transform) {
        room?.routes?.map { r -> DrillCheck.Polyline(r.route.type, r.points.map { transform.apply(it.position) }) } ?: emptyList()
    }

    BackHandler { if (phase == ViewPhase.CHECK) phase = ViewPhase.OVERVIEW else nav.popBackStack() }

    fun markAlign() {
        val r = room ?: return
        val hit = ar.hit ?: return
        alignPoints.add(hit.position)
        haptics.tap()
        if (alignPoints.size < r.references.size) return
        when (val res = RefAlignment.solve(r.references.map { it.position }, alignPoints.toList())) {
            is AlignmentResult.Success -> if (res.isReliable) {
                transform = res.transform
                alignError = res.maxError
                phase = ViewPhase.OVERVIEW
                haptics.success()
            } else {
                message = "Ориентиры не совпали: погрешность ${Formatters.centimeters(res.maxError)}. Отметьте их заново, точнее наводя прицел. Возможно, один ориентир перепутан."
                alignPoints.clear()
            }
            else -> { message = "Не удалось совместить. Отметьте ориентиры заново."; alignPoints.clear() }
        }
    }

    Box(Modifier.fillMaxSize().background(TrassaColors.Background)) {
        ArSceneHost(ar, recorder, showPlanes = phase == ViewPhase.ALIGN, onSceneReady = { scene = it }) {
            if (phase != ViewPhase.CHECK) return@ArSceneHost
            val hit = ar.hit
            if (hit == null) {
                drill = null
                scene?.setPreview(null, null, Vec3.ZERO, TrassaColors.Safe)
                return@ArSceneHost
            }
            // Расстояние от точки прицела на стене до ближайшего отрезка трассы
            val result = DrillCheck.evaluate(hit.position, polylines)
            drill = result
            // Вибрация - только в момент входа в опасную зону, а не каждый кадр
            if (result.isDangerous && !wasDangerous[0]) haptics.danger()
            wasDangerous[0] = result.isDangerous
            scene?.setPreview(hit.position, result.nearestPoint, hit.normal, if (result.isDangerous) TrassaColors.Danger else TrassaColors.Safe)
        }
        Box(Modifier.fillMaxSize().background(TrassaColors.CameraDim))

        // Центр экрана: прицел (или рамка поиска до совмещения)
        Box(Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) {
            if (phase == ViewPhase.ALIGN && alignPoints.isEmpty() && ar.hit == null) {
                val pulse by rememberInfiniteTransition(label = "brackets").animateFloat(0.95f, 1.05f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "s")
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    CornerBrackets(Modifier.size(200.dp, 180.dp).scale(pulse))
                    Text("ПОИСК СТЕНЫ", style = TrassaType.Label, color = TrassaColors.TextPrimary)
                }
            } else {
                val color = when {
                    phase != ViewPhase.CHECK -> if (ar.hit != null) TrassaColors.Accent else TrassaColors.TextMuted
                    drill?.isDangerous == true -> TrassaColors.Danger
                    drill != null -> TrassaColors.Safe
                    else -> TrassaColors.TextPrimary
                }
                Reticle(color)
                if (phase == ViewPhase.CHECK) {
                    val d = drill
                    val (text, c) = when {
                        d == null -> "Наведите прицел на стену" to TrassaColors.TextSecondary
                        d.distance == null || d.nearestType == null -> "Трасс поблизости нет" to TrassaColors.Safe
                        d.isDangerous -> "${Formatters.centimeters(d.distance)} до ${d.nearestType.objectNoun}" to TrassaColors.Danger
                        else -> "Ближайшая трасса: ${Formatters.centimeters(d.distance)}" to TrassaColors.Safe
                    }
                    Text(text, style = TrassaType.CaptionBold, color = c, modifier = Modifier.offset(y = 62.dp)
                        .clip(RoundedCornerShape(8.dp)).background(TrassaColors.Background).padding(horizontal = 12.dp, vertical = 12.dp))
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            ArTopBar(room?.room?.name ?: "", "AR / ПРОСМОТР", TrassaColors.Safe, { nav.popBackStack() }) {
                IconSquareButton(painterResource(R.drawable.ic_list), "Список трасс", { showList = true })
            }
            Box(Modifier.padding(horizontal = TrassaDimens.ScreenPadding).padding(top = 16.dp)) {
                when (phase) {
                    ViewPhase.ALIGN -> Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(TrassaColors.Background).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Найдите ориентиры…", style = TrassaType.Headline, color = TrassaColors.TextPrimary)
                        Text(if (ar.hint.level <= 1) ar.hint.message else "Медленно поводите телефоном, чтобы приложение нашло стену.",
                            style = TrassaType.FootnoteRegular, color = TrassaColors.TextSecondary)
                    }
                    ViewPhase.OVERVIEW -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("●  Стена распознана · ±${Formatters.centimeters(alignError ?: 0f)}", style = TrassaType.Footnote, color = TrassaColors.Safe,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(TrassaColors.Background).padding(horizontal = 14.dp, vertical = 12.dp))
                        val focus = room?.routes?.firstOrNull { it.route.id == focusRouteId }
                        if (focus != null) Text("${focus.route.type.title} · ${Formatters.meters(focus.length)}", style = TrassaType.CaptionBold, color = focus.route.type.color,
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(TrassaColors.Background).padding(horizontal = 12.dp, vertical = 10.dp))
                    }
                    ViewPhase.CHECK -> Text("ПРОВЕРКА МЕСТА СВЕРЛЕНИЯ  ✕", style = TrassaType.Label, color = TrassaColors.TextPrimary,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(TrassaColors.Background)
                            .combinedClickableCompat { phase = ViewPhase.OVERVIEW; drill = null; scene?.setPreview(null, null, Vec3.ZERO, TrassaColors.Safe) }
                            .padding(horizontal = 12.dp, vertical = 13.dp))
                }
            }
            Spacer(Modifier.weight(1f))

            val r = room
            Box(Modifier.navigationBarsPadding()) {
                when (phase) {
                    ViewPhase.ALIGN -> if (r != null && r.hasReferences) {
                        AlignPanel(r.references, alignPoints.size.coerceAtMost(r.references.lastIndex), ar.hit != null, ::markAlign) { nav.popBackStack() }
                    }
                    ViewPhase.OVERVIEW -> Column(Modifier.fillMaxWidth().background(TrassaColors.Background).padding(horizontal = TrassaDimens.ScreenPadding).padding(top = 20.dp, bottom = 8.dp)) {
                        Text("Трассы под отделкой", style = TrassaType.Title2, color = TrassaColors.TextPrimary)
                        Text("${Formatters.routes(r?.routes?.size ?: 0)} · обновлено ${Formatters.dayMonth(r?.room?.updatedAt ?: 0)}", style = TrassaType.FootnoteRegular,
                            color = TrassaColors.TextSecondary, modifier = Modifier.padding(top = 7.dp))
                        Row(Modifier.padding(top = 16.dp).fillMaxWidth().height(48.dp).clip(RoundedCornerShape(8.dp)).background(TrassaColors.Surface).padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(16.dp).clip(RoundedCornerShape(3.dp)).background(TrassaColors.Danger.copy(alpha = 0.75f)))
                            Text("Красная зона: не сверлить ±15 см", style = TrassaType.FootnoteRegular, color = TrassaColors.TextPrimary, modifier = Modifier.weight(1f))
                            Text("Заново", style = TrassaType.CaptionBold, color = TrassaColors.Accent,
                                modifier = Modifier.combinedClickableCompat { alignPoints.clear(); phase = ViewPhase.ALIGN })
                        }
                        PrimaryButton("Можно ли тут сверлить?", { wasDangerous[0] = false; checkTip = false; phase = ViewPhase.CHECK }, Modifier.padding(top = 19.dp))
                    }
                    ViewPhase.CHECK -> Column(Modifier.fillMaxWidth().background(TrassaColors.Background).padding(horizontal = TrassaDimens.ScreenPadding).padding(top = 20.dp, bottom = 8.dp)) {
                        val d = drill
                        when {
                            d != null && d.isDangerous && d.distance != null && d.nearestType != null ->
                                DrillResultCard(true, "Трасса в ${Formatters.centimeters(d.distance)} - ${d.nearestType.title.lowercase()}", "Не сверлите в этой зоне")
                            d != null -> DrillResultCard(false, "Безопасно", "По сохранённой схеме трасс нет")
                            else -> DrillResultCard(false, "Наведите на стену", "Прицел должен попасть на стену, чтобы проверить место")
                        }
                        Text(if (checkTip) "Медленно сдвигайте прицел - результат обновляется сразу." else "Оценка по сохранённой схеме.\nПеред сверлением проверьте детектором.",
                            style = TrassaType.FootnoteRegular, color = TrassaColors.TextSecondary, modifier = Modifier.padding(top = 14.dp))
                        SecondaryButton(if (d?.isDangerous == true) "Найти безопасное место" else "Проверить рядом", { checkTip = true }, Modifier.padding(top = 22.dp))
                    }
                }
            }
        }
    }

    if (showList) {
        ModalBottomSheet(onDismissRequest = { showList = false }, containerColor = TrassaColors.Background) {
            LazyColumn(Modifier.padding(horizontal = TrassaDimens.ScreenPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("Трассы · ${room?.room?.name ?: ""}", style = TrassaType.Headline, color = TrassaColors.TextPrimary) }
                items(room?.routes ?: emptyList()) { RouteCard(it) }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    message?.let { m -> InfoDialog(m) { message = null; if (room?.hasReferences != true) nav.popBackStack() } }
    ar.error?.let { e -> InfoDialog(e) { ar.error = null; nav.popBackStack() } }
}
