package ru.trassa.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.trassa.R
import ru.trassa.data.RouteWithPoints
import ru.trassa.export.PdfExporter
import ru.trassa.export.Schematic
import ru.trassa.geometry.RouteType
import ru.trassa.theme.TrassaColors
import ru.trassa.theme.TrassaDimens
import ru.trassa.theme.TrassaType
import ru.trassa.theme.color
import ru.trassa.theme.title
import ru.trassa.ui.LocalRepository
import ru.trassa.ui.Routes
import ru.trassa.ui.components.ActionsMenu
import ru.trassa.ui.components.ConfirmDialog
import ru.trassa.ui.components.InfoCard
import ru.trassa.ui.components.LabeledField
import ru.trassa.ui.components.MenuAction
import ru.trassa.ui.components.NavHeader
import ru.trassa.ui.components.PhotoBox
import ru.trassa.ui.components.PrimaryButton
import ru.trassa.ui.components.RouteTypeChip
import ru.trassa.ui.components.SecondaryButton
import ru.trassa.ui.components.TextInputDialog
import ru.trassa.ui.components.card
import ru.trassa.util.Formatters

private val Pad = TrassaDimens.ScreenPadding

// ── 14 · Трассы комнаты ──────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RoomRoutesScreen(nav: NavHostController, roomId: Long) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val details by repo.dao.observeRoom(roomId).collectAsState(initial = null)
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var addingNote by remember { mutableStateOf(false) }
    var routeMenu by remember { mutableStateOf<RouteWithPoints?>(null) }
    var deletingRoute by remember { mutableStateOf<RouteWithPoints?>(null) }
    val d = details ?: return

    ScreenScaffold(bottom = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Открыть в AR", enabled = d.hasReferences, onClick = { nav.navigate(Routes.view(roomId)) })
            PrimaryButton("Экспорт схемы в PDF", onClick = { nav.navigate(Routes.export(roomId)) })
        }
    }) {
        Column {
            NavHeader("${d.room.name} · трассы", onBack = { nav.popBackStack() }) {
                Box {
                    Icon(painterResource(R.drawable.ic_more), "Меню", tint = TrassaColors.TextPrimary,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = { menu = true }).padding(10.dp))
                    ActionsMenu(menu, { menu = false }, listOf(
                        MenuAction("Дописать трассы") { nav.navigate(Routes.record(d.room.projectId, roomId)) },
                        MenuAction("Добавить заметку") { addingNote = true },
                        MenuAction("Переименовать") { renaming = true },
                        MenuAction("Удалить комнату", destructive = true) { deleting = true },
                    ))
                }
            }
            LazyColumn(Modifier.padding(horizontal = Pad), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Column(Modifier.padding(bottom = 12.dp)) {
                        Text("Что внутри стены", style = TrassaType.Title28, color = TrassaColors.TextPrimary, modifier = Modifier.padding(top = 14.dp))
                        Text("${Formatters.routes(d.routes.size)} · ${Formatters.meters(d.totalLength)} · ${Formatters.fullDate(d.room.updatedAt)}",
                            style = TrassaType.FootnoteRegular, color = TrassaColors.TextSecondary, modifier = Modifier.padding(top = 9.dp))
                    }
                }
                items(d.routes, key = { it.route.id }) { r ->
                    Box {
                        RouteCard(r, Modifier.combinedClickable(onClick = { nav.navigate(Routes.route(r.route.id)) }, onLongClick = { routeMenu = r }))
                        ActionsMenu(routeMenu == r, { routeMenu = null }, listOf(MenuAction("Удалить трассу", destructive = true) { deletingRoute = r }))
                    }
                }
                if (d.routes.isEmpty()) item { InfoCard("Трасс нет", "Откройте меню «…» → «Дописать трассы».") }
                if (!d.hasReferences) item { InfoCard("Нет ориентиров", "Без ориентиров комнату нельзя совместить в AR. Схема доступна в PDF.") }
                if (d.notes.isNotEmpty()) {
                    item { Text("ЗАМЕТКИ", style = TrassaType.Label, color = TrassaColors.TextSecondary, modifier = Modifier.padding(top = 12.dp)) }
                    items(d.notes, key = { "n" + it.id }) { n ->
                        Column(Modifier.fillMaxWidth().card().combinedClickable(onClick = {}, onLongClick = { scope.launch { repo.deleteNote(n) } }).padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(n.text, style = TrassaType.Body, color = TrassaColors.TextPrimary)
                            Text(Formatters.numeric(n.createdAt) + " · долгое нажатие - удалить", style = TrassaType.Small, color = TrassaColors.TextSecondary)
                        }
                    }
                }
                item { Box(Modifier.height(12.dp)) }
            }
        }
    }
    if (renaming) TextInputDialog("Переименовать комнату", d.room.name, "Сохранить", { renaming = false }) { scope.launch { repo.renameRoom(roomId, it) } }
    if (addingNote) TextInputDialog("Заметка к комнате", "", "Добавить", { addingNote = false }) { scope.launch { repo.addNote(roomId, it) } }
    if (deleting) ConfirmDialog("Удалить комнату?", "Трассы и ориентиры будут удалены.", "Удалить", { deleting = false }) {
        scope.launch { repo.deleteRoom(roomId); nav.popBackStack() }
    }
    deletingRoute?.let { r -> ConfirmDialog("Удалить трассу?", r.route.type.title, "Удалить", { deletingRoute = null }) { scope.launch { repo.deleteRoute(r.route.id) } } }
}

/** Карточка трассы: цветная полоса, тип, описание, дата, длина */
@Composable
fun RouteCard(r: RouteWithPoints, modifier: Modifier = Modifier) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).card().then(modifier).padding(horizontal = 12.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(TrassaDimens.RouteStripe).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(r.route.type.color))
        Column(Modifier.weight(1f)) {
            Text(r.route.type.title, style = TrassaType.Card17, color = TrassaColors.TextPrimary)
            Text(r.route.title.ifBlank { r.route.note.ifBlank { "Без описания" } }, style = TrassaType.FootnoteRegular, color = TrassaColors.TextSecondary,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 9.dp))
            Text(Formatters.numeric(r.route.createdAt) + if (r.route.photoPath != null) "  ·  Фото штробы ↗" else "",
                style = TrassaType.Small, color = TrassaColors.TextSecondary, modifier = Modifier.padding(top = 12.dp))
        }
        Text(Formatters.meters(r.length), style = TrassaType.Callout.copy(fontWeight = TrassaType.Card17.fontWeight), color = r.route.type.color)
    }
}

// ── 15, 23–25 · Карточка трассы ──────────────────────────────────────────────

@Composable
fun RouteDetailScreen(nav: NavHostController, routeId: Long) {
    val repo = LocalRepository.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val data by repo.dao.observeRoute(routeId).collectAsState(initial = null)
    var editing by remember { mutableStateOf(false) }
    val roomDetails by remember(data?.route?.roomId) { repo.dao.observeRoom(data?.route?.roomId ?: -1) }.collectAsState(initial = null)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val bmp = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 2 }) }
            } ?: return@launch
            withContext(Dispatchers.IO) { repo.setRoutePhoto(routeId, bmp) }
        }
    }
    val r = data ?: return

    if (editing) {
        RouteEditScreen(r, onClose = { editing = false }) { updated -> scope.launch { repo.updateRoute(updated) } }
        return
    }
    ScreenScaffold(bottom = {
        PrimaryButton("Показать на стене", enabled = roomDetails?.hasReferences == true,
            onClick = { nav.navigate(Routes.view(r.route.roomId, routeId)) })
    }) {
        Column {
            NavHeader(r.route.type.title, onBack = { nav.popBackStack() }) {
                Text("Изменить", style = TrassaType.Subheadline, color = TrassaColors.Accent,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).combinedClickableCompat { editing = true }.padding(10.dp))
            }
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Pad)) {
                Box(Modifier.padding(top = 22.dp).fillMaxWidth().height(298.dp).clip(RoundedCornerShape(TrassaDimens.Radius))) {
                    PhotoBox(r.route.photoPath ?: roomDetails?.room?.previewPath, Modifier.fillMaxWidth().height(298.dp))
                    Row(Modifier.align(Alignment.BottomEnd).padding(12.dp).clip(RoundedCornerShape(8.dp)).background(TrassaColors.Overlay)
                        .combinedClickableCompat { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        .padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(painterResource(R.drawable.ic_camera), null, tint = TrassaColors.TextPrimary, modifier = Modifier.size(16.dp))
                        Text(if (r.route.photoPath == null) "Фото штробы" else "Заменить фото", style = TrassaType.CaptionBold, color = TrassaColors.TextPrimary)
                    }
                }
                Text(r.displayTitle, style = TrassaType.Title, color = TrassaColors.TextPrimary, modifier = Modifier.padding(top = 27.dp))
                Row(Modifier.padding(top = 21.dp).fillMaxWidth().height(72.dp).card().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Stat("ДЛИНА", Formatters.meters(r.length), r.route.type.color, TrassaType.Headline, Modifier.weight(1f))
                    Stat("ДАТА ЗАПИСИ", Formatters.numeric(r.route.createdAt), TrassaColors.TextPrimary, TrassaType.Button, Modifier.weight(1f))
                }
                Text("ЗАМЕТКА", style = TrassaType.Label, color = TrassaColors.TextSecondary, modifier = Modifier.padding(top = 27.dp))
                Text(r.route.note.ifBlank { "Нажмите «Изменить», чтобы добавить марку кабеля или трубы и как проходит трасса." },
                    style = TrassaType.Body, color = if (r.route.note.isBlank()) TrassaColors.TextMuted else TrassaColors.TextPrimary,
                    modifier = Modifier.padding(top = 13.dp, bottom = 24.dp))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: androidx.compose.ui.graphics.Color, style: androidx.compose.ui.text.TextStyle, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = TrassaType.MicroLabel, color = TrassaColors.TextSecondary)
        Text(value, style = style, color = color)
    }
}

/** Редактирование описания, заметки и типа трассы */
@Composable
private fun RouteEditScreen(r: RouteWithPoints, onClose: () -> Unit, onSave: (ru.trassa.data.RouteEntity) -> Unit) {
    var title by remember { mutableStateOf(r.route.title) }
    var note by remember { mutableStateOf(r.route.note) }
    var type by remember { mutableStateOf(r.route.type) }
    ScreenScaffold(bottom = { PrimaryButton("Готово", onClick = { onSave(r.route.copy(title = title.trim(), note = note.trim(), type = type)); onClose() }) }) {
        Column {
            NavHeader("Трасса", onBack = onClose)
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Pad), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                LabeledField("Куда идёт трасса", "К розеткам над столешницей", title, { title = it }, Modifier.padding(top = 16.dp))
                LabeledField("Заметка", "Кабель ВВГнг 3×2,5…", note, { note = it }, singleLine = false)
                TypeGrid(type) { type = it }
            }
        }
    }
}

/** Сетка 2×2 типов трасс */
@Composable
fun TypeGrid(selected: RouteType, onSelect: (RouteType) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RouteType.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { t -> RouteTypeChip(t, selected == t, { onSelect(t) }, Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier = this.combinedClickable(onClick = onClick)

// ── 16, 27 · Экспорт PDF ─────────────────────────────────────────────────────

@Composable
fun ExportScreen(nav: NavHostController, roomId: Long) {
    val repo = LocalRepository.current
    val context = LocalContext.current
    val details by repo.dao.observeRoom(roomId).collectAsState(initial = null)
    var result by remember { mutableStateOf<PdfExporter.Result?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val d = details

    LaunchedEffect(d) {
        d ?: return@LaunchedEffect
        val project = repo.dao.project(d.room.projectId)
        runCatching { withContext(Dispatchers.IO) { PdfExporter(context).export(d, project) } }
            .onSuccess { result = it }.onFailure { error = "Не удалось создать PDF: ${it.message}" }
    }
    d ?: return

    ScreenScaffold(bottom = {
        PrimaryButton("Поделиться PDF", enabled = result != null, onClick = {
            val file = result?.file ?: return@PrimaryButton
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Схема трасс · ${d.room.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Поделиться схемой"))
        })
    }) {
        Column {
            NavHeader("Экспорт схемы", onBack = { nav.popBackStack() })
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Pad)) {
                Text("Для мебельщиков", style = TrassaType.Title28, color = TrassaColors.TextPrimary, modifier = Modifier.padding(top = 14.dp))
                Text("Схема трасс и зоны, где нельзя сверлить.", style = TrassaType.SubheadlineRegular, color = TrassaColors.TextSecondary, modifier = Modifier.padding(top = 13.dp))
                Box(Modifier.fillMaxWidth().padding(top = 25.dp), contentAlignment = Alignment.Center) {
                    Column(Modifier.width(288.dp).heightIn(min = 344.dp).clip(RoundedCornerShape(4.dp)).background(TrassaColors.Paper).padding(20.dp)) {
                        Text("ТРАССА / СХЕМА КОМНАТЫ", style = TrassaType.MicroLabel, color = TrassaColors.TextMuted)
                        Text(d.room.name, style = TrassaType.Brand, color = TrassaColors.Background, modifier = Modifier.padding(top = 12.dp))
                        Canvas(Modifier.padding(top = 18.dp).fillMaxWidth().height(132.dp)) {
                            Schematic.draw(drawContext.canvas.nativeCanvas, d, RectF(0f, 0f, size.width, size.height))
                        }
                        Text("±15 см от трассы - не сверлить", style = TrassaType.MicroLabel.copy(fontWeight = TrassaType.Footnote.fontWeight),
                            color = TrassaColors.PaperDanger, modifier = Modifier.padding(top = 14.dp))
                        Text("${Formatters.routes(d.routes.size)} · ${Formatters.meters(d.totalLength)} · ${Formatters.numeric(d.room.updatedAt)}",
                            style = TrassaType.MicroLabel.copy(fontWeight = TrassaType.Body.fontWeight), color = TrassaColors.TextMuted, modifier = Modifier.padding(top = 10.dp))
                        Text("Проверьте детектором перед сверлением.", style = TrassaType.MicroLabel.copy(fontWeight = TrassaType.Body.fontWeight),
                            color = TrassaColors.TextMuted, modifier = Modifier.padding(top = 12.dp))
                    }
                }
                Text("В PDF войдут", style = TrassaType.Button, color = TrassaColors.TextPrimary, modifier = Modifier.padding(top = 30.dp))
                Text("Схема · типы и длины · фото штроб\nЗаметки · дата · зона запрета ±15 см", style = TrassaType.SubheadlineRegular,
                    color = TrassaColors.TextSecondary, modifier = Modifier.padding(top = 13.dp))
                Box(Modifier.padding(top = 28.dp, bottom = 24.dp).fillMaxWidth().heightIn(min = 40.dp).clip(RoundedCornerShape(8.dp)).background(TrassaColors.Surface).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    val res = result
                    when {
                        res != null -> Text("✓  ${res.file.name} · ${Formatters.pages(res.pages)}", style = TrassaType.CaptionBold, color = TrassaColors.Safe)
                        error != null -> Text(error!!, style = TrassaType.CaptionBold, color = TrassaColors.Danger)
                        else -> Text("Готовим файл…", style = TrassaType.CaptionBold, color = TrassaColors.TextSecondary)
                    }
                }
            }
        }
    }
}

