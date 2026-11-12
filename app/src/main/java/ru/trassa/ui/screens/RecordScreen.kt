package ru.trassa.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.sceneview.capture.rememberSceneRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.trassa.R
import ru.trassa.ar.ArSceneHost
import ru.trassa.ar.ArState
import ru.trassa.ar.RenderPoint
import ru.trassa.ar.RenderRoute
import ru.trassa.ar.RouteScene
import ru.trassa.data.DraftPoint
import ru.trassa.data.DraftReference
import ru.trassa.data.DraftRoute
import ru.trassa.data.ProjectEntity
import ru.trassa.data.RoomDetails
import ru.trassa.geometry.Alignment as RefAlignment
import ru.trassa.geometry.AlignmentResult
import ru.trassa.geometry.RigidTransformY
import ru.trassa.geometry.RouteType
import ru.trassa.geometry.Vec3
import ru.trassa.theme.TrassaColors
import ru.trassa.theme.TrassaDimens
import ru.trassa.theme.TrassaType
import ru.trassa.theme.color
import ru.trassa.theme.title
import ru.trassa.ui.LocalRepository
import ru.trassa.ui.components.ConfirmDialog
import ru.trassa.ui.components.InfoCard
import ru.trassa.ui.components.LabeledField
import ru.trassa.ui.components.NavHeader
import ru.trassa.ui.components.PrimaryButton
import ru.trassa.ui.components.Reticle
import ru.trassa.ui.components.SecondaryButton
import ru.trassa.ui.components.TagLabel
import ru.trassa.util.Formatters
import ru.trassa.util.Haptics

private enum class RecordPhase { ALIGN, RECORD, REFERENCES, SAVE }

/** Сколько ориентиров просим отметить */
const val REFERENCES_NEEDED = 3

/** Трассы комнаты в координатах текущей сессии */
fun RoomDetails.renderRoutes(t: RigidTransformY): List<RenderRoute> = routes.map { r ->
    RenderRoute(r.route.type, r.points.map { RenderPoint(t.apply(it.position), t.rotate(it.normal)) })
}

/** Экраны 07–09, 17–22, 26 · AR-запись трасс + отметка ориентиров + сохранение */
@Composable
fun RecordScreen(nav: NavHostController, projectId: Long, roomId: Long?) {
    val repo = LocalRepository.current
    val context = LocalContext.current
    val haptics = remember { Haptics(context) }
    val scope = rememberCoroutineScope()
    val ar = remember { ArState() }
    val recorder = rememberSceneRecorder()
    var scene by remember { mutableStateOf<RouteScene?>(null) }

    var project by remember { mutableStateOf<ProjectEntity?>(null) }
    var existing by remember { mutableStateOf<RoomDetails?>(null) }
    var phase by remember { mutableStateOf(if (roomId != null) RecordPhase.ALIGN else RecordPhase.RECORD) }

    // Запись
    var selectedType by remember { mutableStateOf(RouteType.ELECTRIC) }
    val drafts = remember { mutableStateListOf<DraftRoute>() }
    var current by remember { mutableStateOf(DraftRoute(RouteType.ELECTRIC)) }

    // Совмещение (дописывание в существующую комнату)
    val alignPoints = remember { mutableStateListOf<Vec3>() }
    var toSession by remember { mutableStateOf(RigidTransformY.IDENTITY) }

    // Ориентиры новой комнаты
    val references = remember { mutableStateListOf<DraftReference>() }

    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmClose by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(projectId, roomId) {
        project = repo.dao.project(projectId)
        if (roomId != null) existing = repo.dao.room(roomId)
    }

    // Перерисовка черновиков, старых трасс и ориентиров
    LaunchedEffect(scene, drafts.toList(), current) {
        scene?.setDrafts((drafts + current).map { d -> RenderRoute(d.type, d.points.map { RenderPoint(it.position, it.normal) }) })
    }
    LaunchedEffect(scene, existing, toSession, phase) {
        val e = existing ?: return@LaunchedEffect
        scene?.setSaved(e.renderRoutes(toSession), showZones = false)
        scene?.setSavedVisible(phase != RecordPhase.ALIGN)
    }
    LaunchedEffect(scene, references.toList(), alignPoints.toList(), phase) {
        scene?.setMarkers(if (phase == RecordPhase.ALIGN) alignPoints.toList() else references.map { it.position })
    }

    fun close() { if (drafts.isEmpty() && current.points.isEmpty()) nav.popBackStack() else confirmClose = true }
    BackHandler { if (phase == RecordPhase.SAVE) phase = RecordPhase.RECORD else close() }

    fun placePoint() {
        val hit = ar.hit ?: return
        current = current.copy(points = current.points + DraftPoint(hit.position, hit.normal))
        haptics.tap()
    }

    fun finishLine() {
        if (current.points.size >= 2) drafts.add(current)
        current = DraftRoute(selectedType)
    }

    /** Отменяет последнюю точку; если линия пуста - «открывает» предыдущую */
    fun undo() {
        if (current.points.isEmpty() && drafts.isNotEmpty()) {
            current = drafts.removeAt(drafts.lastIndex)
            selectedType = current.type
        }
        if (current.points.isNotEmpty()) current = current.copy(points = current.points.dropLast(1))
    }

    fun requestSave() {
        if (current.points.size >= 2) finishLine()
        if (drafts.isEmpty()) {
            message = "Отметьте хотя бы одну трассу: поставьте две точки и нажмите «Закончить линию»."
            return
        }
        if (existing != null) {
            scope.launch { preview = recorder.takePhoto()?.scaledPreview(); phase = RecordPhase.SAVE }
        } else {
            references.clear()
            phase = RecordPhase.REFERENCES
        }
    }

    fun markReference() {
        val hit = ar.hit ?: return
        scope.launch {
            val photo = recorder.takePhoto()?.centerCrop()
            references.add(DraftReference(hit.position, photo))
            haptics.tap()
            if (references.size == REFERENCES_NEEDED) {
                // Проверяем, что по ориентирам можно определить поворот
                if (RefAlignment.solve(references.map { it.position }, references.map { it.position }) == AlignmentResult.PointsTooClose) {
                    message = "Ориентиры слишком близко друг к другу по горизонтали. Отметьте последний ориентир в другом месте."
                    references.removeAt(references.lastIndex)
                    return@launch
                }
                preview = recorder.takePhoto()?.scaledPreview()
                phase = RecordPhase.SAVE
            }
        }
    }

    fun markAlign() {
        val e = existing ?: return
        val hit = ar.hit ?: return
        alignPoints.add(hit.position)
        haptics.tap()
        if (alignPoints.size == e.references.size) {
            when (val r = RefAlignment.solve(e.references.map { it.position }, alignPoints.toList())) {
                is AlignmentResult.Success -> if (r.isReliable) {
                    toSession = r.transform
                    phase = RecordPhase.RECORD
                    haptics.success()
                    message = "Комната совмещена, погрешность ${Formatters.centimeters(r.maxError)}. Старые трассы показаны на стене."
                } else {
                    message = "Ориентиры не совпали: погрешность ${Formatters.centimeters(r.maxError)}. Отметьте их заново, точнее наводя прицел."
                    alignPoints.clear()
                }
                else -> { message = "Не удалось совместить. Отметьте ориентиры заново."; alignPoints.clear() }
            }
        }
    }

    // ── Интерфейс ──
    Box(Modifier.fillMaxSize().background(TrassaColors.Background)) {
        ArSceneHost(ar, recorder, showPlanes = true, onSceneReady = { scene = it }) {
            // «Резинка» от последней точки до прицела
            val last = current.points.lastOrNull()
            val hit = ar.hit
            if (phase == RecordPhase.RECORD && last != null && hit != null) scene?.setPreview(last.position, hit.position, hit.normal, current.type.color)
            else scene?.setPreview(null, null, Vec3.ZERO, TrassaColors.TextPrimary)
        }
        Box(Modifier.fillMaxSize().background(TrassaColors.CameraDim))

        // Прицел строго в центре - туда направлен hit test
        Reticle(if (ar.hit != null) TrassaColors.Accent else TrassaColors.TextMuted, Modifier.align(Alignment.Center))
        if (phase == RecordPhase.RECORD && current.points.isNotEmpty()) {
            // Подпись справа сверху от прицела. Слой нулевого размера в центре экрана:
            // подпись не участвует в раскладке и не может сдвинуть прицел с точки hit test
            Text("${Formatters.meters(current.length)} · ${Formatters.points(current.points.size)}", style = TrassaType.CaptionBold, color = current.type.color,
                modifier = Modifier.align(Alignment.Center).besideCenter(36.dp, (-48).dp)
                    .clip(RoundedCornerShape(6.dp)).background(TrassaColors.Background).padding(horizontal = 10.dp, vertical = 8.dp))
        }

        Column(Modifier.fillMaxSize()) {
            val (mode, modeColor) = when (phase) {
                RecordPhase.ALIGN -> "●  СОВМЕЩЕНИЕ" to TrassaColors.Safe
                RecordPhase.REFERENCES -> "●  ОРИЕНТИРЫ" to TrassaColors.Accent
                else -> "●  ЗАПИСЬ" to TrassaColors.Accent
            }
            ArTopBar(existing?.room?.name ?: "Новая комната", mode, modeColor, ::close) {
                if (phase == RecordPhase.RECORD) {
                    Column(
                        Modifier.width(72.dp).height(56.dp).clip(RoundedCornerShape(12.dp)).background(if (drafts.isNotEmpty() || current.points.size >= 2) TrassaColors.Accent else TrassaColors.Surface)
                            .combinedClickableCompat(::requestSave),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        val c = if (drafts.isNotEmpty() || current.points.size >= 2) TrassaColors.OnAccent else TrassaColors.TextSecondary
                        Icon(painterResource(R.drawable.ic_save), null, tint = c, modifier = Modifier.size(20.dp))
                        Text("Сохранить", style = TrassaType.MicroLabel, color = c)
                    }
                }
            }
            val hintText = when (phase) {
                RecordPhase.ALIGN -> "Сначала совместите комнату: старые трассы появятся на своих местах"
                RecordPhase.REFERENCES -> if (ar.hit != null) "Наведите прицел на ориентир ${references.size + 1} из $REFERENCES_NEEDED" else ar.hint.message
                else -> ar.hint.message
            }
            HintCards(ar.hint, hintText)
            Spacer(Modifier.weight(1f))

            when (phase) {
                RecordPhase.RECORD -> {
                    Row(Modifier.padding(horizontal = TrassaDimens.ScreenPadding).padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        val canUndo = current.points.isNotEmpty() || drafts.isNotEmpty()
                        Row(Modifier.width(150.dp).height(56.dp).alpha(if (canUndo) 1f else 0.45f).clip(RoundedCornerShape(12.dp)).background(TrassaColors.Background)
                            .combinedClickableCompat { if (canUndo) undo() }.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                            Icon(painterResource(R.drawable.ic_undo), null, tint = TrassaColors.TextPrimary, modifier = Modifier.size(22.dp))
                            Text("Отменить", style = TrassaType.Footnote, color = TrassaColors.TextPrimary)
                        }
                        val saved = Formatters.routes(drafts.size + (existing?.routes?.size ?: 0))
                        Text(if (current.points.isEmpty()) "$saved сохранено" else "$saved · новая линия", style = TrassaType.Small, color = TrassaColors.TextPrimary, modifier = Modifier.weight(1f))
                    }
                    Column(Modifier.fillMaxWidth().background(TrassaColors.Background).navigationBarsPadding().padding(horizontal = TrassaDimens.ScreenPadding).padding(top = 16.dp, bottom = 8.dp)) {
                        Text(if (current.points.isEmpty()) "Тип новой трассы" else "${current.type.title} · новая линия", style = TrassaType.Subheadline, color = TrassaColors.TextPrimary)
                        Box(Modifier.padding(top = 12.dp)) {
                            TypeGrid(selectedType) { t -> selectedType = t; current = current.copy(type = t) }
                        }
                        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            PrimaryButton("Поставить точку", ::placePoint, Modifier.weight(1f), enabled = ar.hit != null && ar.hint.canPlace, style = TrassaType.ButtonSmall)
                            SecondaryButton("Закончить линию", ::finishLine, Modifier.weight(1f), enabled = current.points.isNotEmpty(), bordered = false, style = TrassaType.Footnote)
                        }
                    }
                }
                RecordPhase.REFERENCES -> {
                    Column(Modifier.fillMaxWidth().background(TrassaColors.Background).navigationBarsPadding().padding(horizontal = TrassaDimens.ScreenPadding).padding(top = 20.dp, bottom = 8.dp)) {
                        Text("Ориентир ${minOf(references.size + 1, REFERENCES_NEEDED)} из $REFERENCES_NEEDED", style = TrassaType.Title2, color = TrassaColors.TextPrimary)
                        Text(REFERENCE_TIPS, style = TrassaType.FootnoteRegular, color = TrassaColors.TextSecondary, modifier = Modifier.padding(top = 8.dp))
                        Box(Modifier.padding(top = 16.dp)) { ReferenceThumbs(references.map { it.photo }, REFERENCES_NEEDED) }
                        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            PrimaryButton("Отметить ориентир", ::markReference, Modifier.weight(1f), enabled = ar.hit != null, style = TrassaType.ButtonSmall)
                            SecondaryButton(if (references.isEmpty()) "Назад" else "Отменить", {
                                if (references.isEmpty()) phase = RecordPhase.RECORD else references.removeAt(references.lastIndex)
                            }, Modifier.weight(1f), bordered = false, style = TrassaType.Footnote)
                        }
                    }
                }
                RecordPhase.ALIGN -> existing?.let { e ->
                    Box(Modifier.navigationBarsPadding()) {
                        AlignPanel(e.references, alignPoints.size.coerceAtMost(e.references.lastIndex), ar.hit != null, ::markAlign) { nav.popBackStack() }
                    }
                }
                RecordPhase.SAVE -> Unit
            }
        }

        if (phase == RecordPhase.SAVE) {
            SaveRoomPanel(
                projectName = project?.name ?: "",
                initialName = existing?.room?.name ?: "",
                preview = preview,
                routesCount = drafts.size + (existing?.routes?.size ?: 0),
                totalLength = drafts.sumOf { it.length.toDouble() }.toFloat() + (existing?.totalLength ?: 0f),
                referencesCount = existing?.references?.size ?: references.size,
                saving = saving,
                onBack = { phase = RecordPhase.RECORD; references.clear() },
            ) { name ->
                saving = true
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            repo.saveRecording(projectId, roomId, name, drafts.toList(), references.toList(), preview, toSession)
                        }
                    }.onSuccess { haptics.success(); nav.popBackStack() }
                        .onFailure { saving = false; message = "Не удалось сохранить: ${it.message}" }
                }
            }
        }
    }

    message?.let { m -> InfoDialog(m) { message = null } }
    ar.error?.let { e -> InfoDialog(e) { ar.error = null; nav.popBackStack() } }
    if (confirmClose) ConfirmDialog("Выйти без сохранения?", "Отмеченные трассы не сохранятся.", "Выйти", { confirmClose = false }) { nav.popBackStack() }
}

/** Экран 09 · Сохранение комнаты */
@Composable
private fun SaveRoomPanel(
    projectName: String, initialName: String, preview: Bitmap?, routesCount: Int, totalLength: Float, referencesCount: Int,
    saving: Boolean, onBack: () -> Unit, onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    ScreenScaffold(bottom = { PrimaryButton(if (saving) "Сохраняем…" else "Сохранить", { onSave(name) }, enabled = name.isNotBlank() && !saving) }) {
        Column {
            NavHeader("Сохранение комнаты", onBack = onBack)
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = TrassaDimens.ScreenPadding)) {
                Text("Схема готова", style = TrassaType.LargeTitle, color = TrassaColors.TextPrimary, modifier = Modifier.padding(top = 16.dp))
                Text("Трассы привязаны к ${Formatters.plural(referencesCount, "ориентиру", "ориентирам", "ориентирам")}.", style = TrassaType.SubheadlineRegular,
                    color = TrassaColors.TextSecondary, modifier = Modifier.padding(top = 10.dp))
                Box(Modifier.padding(top = 26.dp).fillMaxWidth().height(224.dp).clip(RoundedCornerShape(TrassaDimens.Radius))) {
                    if (preview != null) Image(preview.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Image(painterResource(R.drawable.wall_rough), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    TagLabel("${Formatters.routes(routesCount)} · ${Formatters.meters(totalLength)}", color = TrassaColors.Safe, modifier = Modifier.padding(16.dp))
                }
                LabeledField("Название комнаты", "Кухня", name, { name = it }, Modifier.padding(top = 24.dp))
                Text("Объект: $projectName", style = TrassaType.SubheadlineRegular, color = TrassaColors.TextSecondary, modifier = Modifier.padding(top = 22.dp))
                InfoCard("Запомните ориентиры", "Через месяцы их нужно будет отметить снова - приложение покажет их фото.", Modifier.padding(top = 22.dp, bottom = 24.dp))
            }
        }
    }
}

/** Сообщение с кнопкой «Понятно» */
@Composable
fun InfoDialog(text: String, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TrassaColors.Surface,
        text = { Text(text, style = TrassaType.Body, color = TrassaColors.TextPrimary) },
        confirmButton = {
            Text("Понятно", style = TrassaType.Subheadline, color = TrassaColors.Accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).combinedClickableCompat(onDismiss).padding(12.dp))
        },
    )
}

/**
 * Ставит элемент со сдвигом (dx, dy) от точки привязки, не занимая места в раскладке:
 * размер слоя 0×0, поэтому соседние элементы (прицел) остаются строго по центру.
 */
private fun Modifier.besideCenter(dx: Dp, dy: Dp) = layout { measurable, constraints ->
    val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(0, 0) { p.place(dx.roundToPx(), dy.roundToPx()) }
}
