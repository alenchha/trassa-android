package ru.trassa.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.trassa.data.TrassaRepository
import ru.trassa.theme.TrassaColors
import ru.trassa.ui.screens.ExportScreen
import ru.trassa.ui.screens.NewProjectScreen
import ru.trassa.ui.screens.OnboardingScreen
import ru.trassa.ui.screens.ProjectsScreen
import ru.trassa.ui.screens.RecordScreen
import ru.trassa.ui.screens.RoomRoutesScreen
import ru.trassa.ui.screens.RoomsScreen
import ru.trassa.ui.screens.RouteDetailScreen
import ru.trassa.ui.screens.ViewerScreen

/** Репозиторий доступен всем экранам */
val LocalRepository = staticCompositionLocalOf<TrassaRepository> { error("Репозиторий не задан") }

/** Маршруты навигации */
object Routes {
    const val PROJECTS = "projects"
    const val NEW_PROJECT = "projects/new"
    fun project(id: Long) = "project/$id"
    fun routes(roomId: Long) = "room/$roomId"
    fun route(routeId: Long) = "route/$routeId"
    fun export(roomId: Long) = "export/$roomId"
    /** roomId = -1 - новая комната */
    fun record(projectId: Long, roomId: Long = -1) = "record/$projectId?roomId=$roomId"
    /** focus = -1 - без подсветки трассы */
    fun view(roomId: Long, focusRouteId: Long = -1) = "view/$roomId?focus=$focusRouteId"
}

@Composable
fun TrassaApp(repository: TrassaRepository) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("trassa", Context.MODE_PRIVATE) }
    var onboardingDone by remember { mutableStateOf(prefs.getBoolean("onboarding", false)) }

    androidx.compose.runtime.CompositionLocalProvider(LocalRepository provides repository) {
        if (!onboardingDone) {
            OnboardingScreen {
                prefs.edit().putBoolean("onboarding", true).apply()
                onboardingDone = true
            }
        } else {
            AppNavHost(rememberNavController())
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController) {
    val long = { name: String -> navArgument(name) { type = NavType.LongType; defaultValue = -1L } }
    NavHost(nav, startDestination = Routes.PROJECTS, modifier = Modifier.fillMaxSize().background(TrassaColors.Background)) {
        composable(Routes.PROJECTS) { ProjectsScreen(nav) }
        composable(Routes.NEW_PROJECT) { NewProjectScreen(nav) }
        composable("project/{id}", listOf(long("id"))) { ProjectArgs(it.arguments?.getLong("id")) { id -> RoomsScreen(nav, id) } }
        composable("room/{roomId}", listOf(long("roomId"))) { ProjectArgs(it.arguments?.getLong("roomId")) { id -> RoomRoutesScreen(nav, id) } }
        composable("route/{routeId}", listOf(long("routeId"))) { ProjectArgs(it.arguments?.getLong("routeId")) { id -> RouteDetailScreen(nav, id) } }
        composable("export/{roomId}", listOf(long("roomId"))) { ProjectArgs(it.arguments?.getLong("roomId")) { id -> ExportScreen(nav, id) } }
        composable("record/{projectId}?roomId={roomId}", listOf(long("projectId"), long("roomId"))) {
            val projectId = it.arguments?.getLong("projectId") ?: -1
            val roomId = it.arguments?.getLong("roomId")?.takeIf { r -> r >= 0 }
            RecordScreen(nav, projectId, roomId)
        }
        composable("view/{roomId}?focus={focus}", listOf(long("roomId"), long("focus"))) {
            val roomId = it.arguments?.getLong("roomId") ?: -1
            val focus = it.arguments?.getLong("focus")?.takeIf { f -> f >= 0 }
            ViewerScreen(nav, roomId, focus)
        }
    }
}

@Composable
private fun ProjectArgs(id: Long?, content: @Composable (Long) -> Unit) {
    if (id != null && id >= 0) content(id)
}
