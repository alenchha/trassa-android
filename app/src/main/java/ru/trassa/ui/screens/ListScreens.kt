package ru.trassa.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import kotlinx.coroutines.launch
import ru.trassa.R
import ru.trassa.data.ProjectSummary
import ru.trassa.data.RoomSummary
import ru.trassa.theme.RouteColors
import ru.trassa.theme.TrassaColors
import ru.trassa.theme.TrassaDimens
import ru.trassa.theme.TrassaType
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
import ru.trassa.ui.components.TagLabel
import ru.trassa.ui.components.TextInputDialog
import ru.trassa.ui.components.card
import ru.trassa.util.Formatters

private val Pad = TrassaDimens.ScreenPadding

/** Экран с фоном макета, отступами под системные панели и кнопкой снизу */
@Composable
fun ScreenScaffold(bottom: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(TrassaColors.Background).systemBarsPadding().imePadding()) {
        Box(Modifier.weight(1f)) { content() }
        Box(Modifier.padding(horizontal = Pad).padding(top = 8.dp, bottom = 8.dp)) { bottom() }
    }
}

// ── 01–03 · Онбординг ────────────────────────────────────────────────────────

private data class OnboardingPage(val tag: String, val title: String, val text: String, val photo: Int)

private val pages = listOf(
    OnboardingPage("До отделки", "Отметьте трассы\nдо отделки", "Соединяйте точки на открытой стене.\nКаждая труба и кабель - на своём месте.", R.drawable.wall_rough),
    OnboardingPage("Карта комнаты", "Сохраните\nкомнату", "Трассы, фото и заметки останутся\nв одной карте - не в галерее телефона.", R.drawable.wall_rough),
    OnboardingPage("После ремонта", "Сверлите\nбез риска", "Наведите камеру на готовую стену\nи проверьте место перед сверлением.", R.drawable.wall_finished),
)

@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    ScreenScaffold(bottom = {
        PrimaryButton(if (pager.currentPage == pages.lastIndex) "Начать работу" else "Далее", onClick = {
            if (pager.currentPage < pages.lastIndex) scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } else onFinish()
        })
    }) {
        Column(Modifier.padding(horizontal = Pad)) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("ТРАССА", style = TrassaType.Brand, color = TrassaColors.Accent, modifier = Modifier.weight(1f))
                Text("Пропустить", style = TrassaType.Footnote, color = TrassaColors.TextSecondary,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = onFinish).padding(12.dp))
            }
            HorizontalPager(pager, Modifier.weight(1f), verticalAlignment = Alignment.Top) { i ->
                val p = pages[i]
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OnboardingIllustration(p.photo, p.tag, i, Modifier.padding(top = 17.dp).fillMaxWidth().height(345.dp))
                    Text("%02d / %02d".format(i + 1, pages.size), style = TrassaType.CaptionBold, color = TrassaColors.Accent, modifier = Modifier.padding(top = 27.dp))
                    Text(p.title, style = TrassaType.LargeTitle, color = TrassaColors.TextPrimary, modifier = Modifier.padding(top = 13.dp))
                    Text(p.text, style = TrassaType.Body, color = TrassaColors.TextSecondary, modifier = Modifier.padding(top = 12.dp))
                }
            }
            // Индикатор: активная страница - 22×6 оранжевая, остальные - 8×6
            Row(Modifier.padding(top = 16.dp, bottom = 29.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                pages.indices.forEach { i ->
                    Box(Modifier.width(if (i == pager.currentPage) 22.dp else 8.dp).height(6.dp).clip(CircleShape)
                        .background(if (i == pager.currentPage) TrassaColors.Accent else TrassaColors.TextMuted))
                }
            }
        }
    }
}

/** Фото стены из макета с нарисованными поверх трассами (координаты из Figma, карточка 342×345) */
@Composable
private fun OnboardingIllustration(photo: Int, tag: String, variant: Int, modifier: Modifier) {
    BoxWithConstraints(modifier.clip(RoundedCornerShape(TrassaDimens.Radius))) {
        Image(painterResource(photo), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (variant != 1) {
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                val s = size.width / 342f
                fun path(vararg pts: Pair<Float, Float>) = Path().apply {
                    moveTo(pts[0].first * s, pts[0].second * s)
                    pts.drop(1).forEach { lineTo(it.first * s, it.second * s) }
                }
                val yellow = path(102f to 289f, 102f to 124f, 259f to 124f, 259f to 67f)
                val blue = path(46f to 311f, 46f to 230f, 206f to 230f)
                if (variant == 2) drawPath(yellow, TrassaColors.Danger.copy(alpha = 0.24f), style = Stroke(42 * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(blue, RouteColors.Water, style = Stroke(5 * s, join = StrokeJoin.Round))
                drawPath(yellow, RouteColors.Electric, style = Stroke(5 * s, join = StrokeJoin.Round))
                listOf(102f to 289f, 102f to 124f, 259f to 124f).forEach { drawCircle(RouteColors.Electric, 5 * s, Offset(it.first * s, it.second * s)) }
            }
        } else {
            Column(
                Modifier.padding(start = 16.dp, top = maxWidth * 215f / 342f).width(maxWidth - 32.dp).height(108.dp)
                    .clip(RoundedCornerShape(TrassaDimens.Radius)).background(TrassaColors.Background).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(21.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(painterResource(R.drawable.ic_check), null, tint = TrassaColors.Safe, modifier = Modifier.size(22.dp))
                    Text("Кухня сохранена", style = TrassaType.Card17, color = TrassaColors.TextPrimary)
                }
                Text("4 трассы · 3 ориентира", style = TrassaType.FootnoteRegular, color = TrassaColors.TextSecondary)
            }
        }
        TagLabel(tag, modifier = Modifier.offset(16.dp, 17.dp).width(155.dp))
    }
}

// ── 04 · Объекты ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProjectsScreen(nav: NavHostController) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val projects by repo.dao.observeProjects().collectAsState(initial = emptyList())
    var menuFor by remember { mutableStateOf<ProjectSummary?>(null) }
    var renaming by remember { mutableStateOf<ProjectSummary?>(null) }
    var deleting by remember { mutableStateOf<ProjectSummary?>(null) }

    ScreenScaffold(bottom = { PrimaryButton("+ Новый объект", onClick = { nav.navigate(Routes.NEW_PROJECT) }) }) {
        LazyColumn(Modifier.padding(horizontal = Pad), verticalArrangement = Arrangement.spacedBy(TrassaDimens.CardSpacing)) {
            item {
                Column {
                    Text("Мои объекты", style = TrassaType.Headline, color = TrassaColors.TextPrimary, modifier = Modifier.padding(top = 20.dp))
                    Text("Стены помнят.\nВы - спокойны.", style = TrassaType.Hero, color = TrassaColors.TextPrimary, modifier = Modifier.padding(top = 36.dp))
                    Text(
                        if (projects.isEmpty()) "Создайте первый объект, чтобы записать трассы"
                        else "${Formatters.projects(projects.size)} · все схемы под рукой",
                        style = TrassaType.SubheadlineRegular, color = TrassaColors.TextSecondary, modifier = Modifier.padding(top = 11.dp, bottom = 5.dp),
                    )
                }
            }
            items(projects, key = { it.project.id }) { p ->
                Box {
                    ProjectCard(p, Modifier.combinedClickable(onClick = { nav.navigate(Routes.project(p.project.id)) }, onLongClick = { menuFor = p }))
                    ActionsMenu(menuFor == p, { menuFor = null }, listOf(
                        MenuAction("Переименовать") { renaming = p },
                        MenuAction("Удалить", destructive = true) { deleting = p },
                    ))
                }
            }
            if (projects.isEmpty()) item {
                InfoCard("Пока пусто", "Объект - это квартира или дом. Внутри - комнаты с картами трасс.", icon = painterResource(R.drawable.ic_cube))
            } else item {
                Text("Долгое нажатие на карточку - переименовать или удалить", style = TrassaType.Caption, color = TrassaColors.TextMuted, modifier = Modifier.padding(bottom = 16.dp))
            }
        }
    }
    renaming?.let { p -> TextInputDialog("Переименовать объект", p.project.name, "Сохранить", { renaming = null }) { scope.launch { repo.renameProject(p.project.id, it) } } }
    deleting?.let { p ->
        ConfirmDialog("Удалить объект?", "Комнаты, трассы и ориентиры будут удалены без возможности восстановления.", "Удалить", { deleting = null }) {
            scope.launch { repo.deleteProject(p.project.id) }
        }
    }
}

@Composable
private fun ProjectCard(p: ProjectSummary, modifier: Modifier) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(TrassaDimens.Radius)).background(TrassaColors.Surface).then(modifier)) {
        PhotoBox(p.coverPath, Modifier.fillMaxWidth().height(TrassaDimens.ProjectPhotoHeight))
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 15.dp)) {
            Text(p.project.name, style = TrassaType.Headline, color = TrassaColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${Formatters.rooms(p.roomsCount)}  ·  ${Formatters.routes(p.routesCount)}", style = TrassaType.SubheadlineRegular,
                color = TrassaColors.TextSecondary, modifier = Modifier.padding(top = 7.dp))
            Text("Обновлено ${Formatters.dayMonth(p.project.updatedAt)}", style = TrassaType.Caption, color = TrassaColors.TextSecondary, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// ── 06 · Новый объект ────────────────────────────────────────────────────────

@Composable
fun NewProjectScreen(nav: NavHostController) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }

    ScreenScaffold(bottom = {
        PrimaryButton("Создать и записать комнату", enabled = name.isNotBlank(), onClick = {
            scope.launch {
                val id = repo.createProject(name, address)
                // Заменяем экран создания на список комнат и сразу открываем AR-запись
                nav.navigate(Routes.project(id)) { popUpTo(Routes.PROJECTS) }
                nav.navigate(Routes.record(id))
            }
        })
    }) {
        Column {
            NavHeader("Новый объект", onBack = { nav.popBackStack() })
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Pad)) {
                Text("С чего начнём?", style = TrassaType.LargeTitle, color = TrassaColors.TextPrimary, modifier = Modifier.padding(top = 16.dp))
                Text("Объедините комнаты одной квартиры\nили дома в общий объект.", style = TrassaType.Body, color = TrassaColors.TextSecondary, modifier = Modifier.padding(top = 12.dp))
                LabeledField("Название объекта", "ЖК «Речной» · кв. 42", name, { name = it }, Modifier.padding(top = 39.dp))
                LabeledField("Адрес · необязательно", "Ленинградский пр-т, 18", address, { address = it }, Modifier.padding(top = 24.dp))
                InfoCard("Сначала - первая комната", "Наведите камеру на открытую стену\nи отметьте трубы и кабели.",
                    Modifier.padding(top = 48.dp), icon = painterResource(R.drawable.ic_cube))
            }
        }
    }
}

// ── 05 · Комнаты ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RoomsScreen(nav: NavHostController, projectId: Long) {
    val repo = LocalRepository.current
    val scope = rememberCoroutineScope()
    val project by repo.dao.observeProject(projectId).collectAsState(initial = null)
    val rooms by repo.dao.observeRooms(projectId).collectAsState(initial = emptyList())
    var headerMenu by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<RoomSummary?>(null) }
    var renamingProject by remember { mutableStateOf(false) }
    var deletingProject by remember { mutableStateOf(false) }
    var renamingRoom by remember { mutableStateOf<RoomSummary?>(null) }
    var deletingRoom by remember { mutableStateOf<RoomSummary?>(null) }
    val p = project ?: return

    ScreenScaffold(bottom = { PrimaryButton("+ Добавить комнату", onClick = { nav.navigate(Routes.record(projectId)) }) }) {
        Column {
            NavHeader(p.name, onBack = { nav.popBackStack() }) {
                Box {
                    Icon(painterResource(R.drawable.ic_more), "Меню", tint = TrassaColors.TextPrimary,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).combinedClickable(onClick = { headerMenu = true }).padding(10.dp))
                    ActionsMenu(headerMenu, { headerMenu = false }, listOf(
                        MenuAction("Переименовать объект") { renamingProject = true },
                        MenuAction("Удалить объект", destructive = true) { deletingProject = true },
                    ))
                }
            }
            LazyColumn(Modifier.padding(horizontal = Pad), verticalArrangement = Arrangement.spacedBy(TrassaDimens.CardSpacing)) {
                item {
                    Column {
                        Text("Комнаты", style = TrassaType.LargeTitle, color = TrassaColors.TextPrimary, modifier = Modifier.padding(top = 16.dp))
                        if (p.address.isNotBlank()) Text(p.address, style = TrassaType.SubheadlineRegular, color = TrassaColors.TextSecondary, modifier = Modifier.padding(top = 8.dp))
                        TagLabel("${Formatters.routes(rooms.sumOf { it.routesCount })} сохранено", color = TrassaColors.Safe,
                            modifier = Modifier.padding(top = 14.dp, bottom = 10.dp).offset(x = (-10).dp))
                    }
                }
                items(rooms, key = { it.room.id }) { r ->
                    Box {
                        RoomRow(r, Modifier.combinedClickable(onClick = { nav.navigate(Routes.routes(r.room.id)) }, onLongClick = { menuFor = r }))
                        ActionsMenu(menuFor == r, { menuFor = null }, listOf(
                            MenuAction("Переименовать") { renamingRoom = r },
                            MenuAction("Дописать трассы") { nav.navigate(Routes.record(projectId, r.room.id)) },
                            MenuAction("Удалить", destructive = true) { deletingRoom = r },
                        ))
                    }
                }
                item {
                    InfoCard(
                        if (rooms.isEmpty()) "Комнат пока нет" else "Схема остаётся с объектом",
                        if (rooms.isEmpty()) "Нажмите «Добавить комнату» и отметьте трассы на открытой стене." else "Откройте комнату, чтобы увидеть трассы.",
                        Modifier.padding(top = 18.dp, bottom = 16.dp),
                    )
                }
            }
        }
    }
    if (renamingProject) TextInputDialog("Переименовать объект", p.name, "Сохранить", { renamingProject = false }) { scope.launch { repo.renameProject(projectId, it) } }
    if (deletingProject) ConfirmDialog("Удалить объект?", "Все комнаты и трассы будут удалены.", "Удалить", { deletingProject = false }) {
        scope.launch { repo.deleteProject(projectId); nav.popBackStack() }
    }
    renamingRoom?.let { r -> TextInputDialog("Переименовать комнату", r.room.name, "Сохранить", { renamingRoom = null }) { scope.launch { repo.renameRoom(r.room.id, it) } } }
    deletingRoom?.let { r -> ConfirmDialog("Удалить комнату?", "Трассы и ориентиры комнаты будут удалены.", "Удалить", { deletingRoom = null }) { scope.launch { repo.deleteRoom(r.room.id) } } }
}

@Composable
private fun RoomRow(r: RoomSummary, modifier: Modifier) {
    Row(Modifier.fillMaxWidth().height(96.dp).card().then(modifier).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        PhotoBox(r.room.previewPath, Modifier.size(TrassaDimens.RoomThumb).clip(RoundedCornerShape(TrassaDimens.SmallRadius)), R.drawable.wall_finished)
        Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(r.room.name, style = TrassaType.Headline, color = TrassaColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${Formatters.routes(r.routesCount)} · ${Formatters.dayMonth(r.room.updatedAt)}", style = TrassaType.SubheadlineRegular, color = TrassaColors.TextSecondary)
        }
        Icon(painterResource(R.drawable.ic_arrow_right), null, tint = TrassaColors.TextPrimary, modifier = Modifier.padding(end = 16.dp).size(22.dp))
        Spacer(Modifier.width(0.dp))
    }
}
