package ru.trassa.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.trassa.geometry.RigidTransformY
import ru.trassa.geometry.RouteType
import ru.trassa.geometry.Vec3
import java.io.File

/** Сохранение и загрузка модели: Room в памяти + файлы фото во временной папке */
@RunWith(RobolectricTestRunner::class)
class RepositoryTest {
    private lateinit var db: TrassaDatabase
    private lateinit var repo: TrassaRepository
    private lateinit var images: File
    private val normal = Vec3(0f, 0f, 1f)

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TrassaDatabase::class.java).allowMainThreadQueries().build()
        images = File(context.cacheDir, "test-images").apply { deleteRecursively(); mkdirs() }
        repo = TrassaRepository(db, images)
    }

    @After fun tearDown() {
        db.close()
        images.deleteRecursively()
    }

    private fun draft(type: RouteType, vararg pts: Vec3) = DraftRoute(type, pts.map { DraftPoint(it, normal) })
    private val refs = listOf(Vec3(0f, 1f, 0f), Vec3(1.2f, 1f, 0f), Vec3(1.2f, 2.4f, -0.8f)).map { DraftReference(it, null) }

    @Test fun saveRecordingAndReadBack() = runBlocking {
        val projectId = repo.createProject("ЖК «Речной» · кв. 42", "Ленинградский пр-т, 18")
        val roomId = repo.saveRecording(
            projectId, null, "  Кухня ",
            drafts = listOf(
                draft(RouteType.ELECTRIC, Vec3(0f, 0f, 0f), Vec3(0f, 1f, 0f), Vec3(0.5f, 1f, 0f)),
                draft(RouteType.WATER, Vec3(1f, 0f, 0f), Vec3(1f, 0.4f, 0f)),
                draft(RouteType.HEATING, Vec3(2f, 0f, 0f)), // одна точка - не сохраняется
            ),
            references = refs, preview = null,
        )
        val room = repo.dao.room(roomId)!!
        assertEquals("Кухня", room.room.name)
        assertEquals(2, room.routes.size)
        assertEquals(3, room.references.size)
        assertTrue(room.hasReferences)
        val electric = room.routes.first { it.route.type == RouteType.ELECTRIC }
        assertEquals(listOf(Vec3(0f, 0f, 0f), Vec3(0f, 1f, 0f), Vec3(0.5f, 1f, 0f)), electric.points.map { it.position })
        assertEquals(1.5f, electric.length, 1e-5f)
        assertEquals(1.9f, room.totalLength, 1e-5f)

        val summary = repo.dao.observeProjects().first().single()
        assertEquals(1, summary.roomsCount)
        assertEquals(2, summary.routesCount)
    }

    @Test fun appendConvertsPointsBackToRoomFrame() = runBlocking {
        val projectId = repo.createProject("Дом", "")
        val roomId = repo.saveRecording(projectId, null, "Ванная",
            listOf(draft(RouteType.WATER, Vec3(0f, 0f, 0f), Vec3(0f, 1f, 0f))), refs, null)

        // Новая сессия сдвинута и повёрнута относительно комнаты
        val toSession = RigidTransformY(0.8f, Vec3(1f, 0f, -2f))
        val roomPoint = Vec3(0.3f, 0.5f, 0f)
        val sessionPoint = toSession.apply(roomPoint)
        repo.saveRecording(projectId, roomId, "Ванная",
            listOf(draft(RouteType.LOW_VOLTAGE, sessionPoint, toSession.apply(Vec3(0.3f, 1.5f, 0f)))),
            emptyList(), null, toSession)

        val room = repo.dao.room(roomId)!!
        assertEquals(2, room.routes.size)
        assertEquals(3, room.references.size) // ориентиры комнаты не меняются
        val added = room.routes.first { it.route.type == RouteType.LOW_VOLTAGE }.points.first().position
        assertEquals(0f, added.distanceTo(roomPoint), 1e-4f)
    }

    @Test fun renameAndCascadeDelete() = runBlocking {
        val projectId = repo.createProject("Старое", "")
        val roomId = repo.saveRecording(projectId, null, "Спальня",
            listOf(draft(RouteType.ELECTRIC, Vec3(0f, 0f, 0f), Vec3(0f, 1f, 0f))), refs, null)
        repo.renameProject(projectId, "Новое")
        repo.renameRoom(roomId, "Детская")
        assertEquals("Новое", repo.dao.project(projectId)!!.name)
        assertEquals("Детская", repo.dao.room(roomId)!!.room.name)

        repo.deleteProject(projectId)
        assertNull(repo.dao.project(projectId))
        assertNull(repo.dao.room(roomId))
        assertTrue(repo.dao.observeProjects().first().isEmpty())
    }
}
