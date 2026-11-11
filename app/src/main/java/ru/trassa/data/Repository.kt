package ru.trassa.data

import android.content.Context
import android.graphics.Bitmap
import androidx.room.withTransaction
import ru.trassa.geometry.RigidTransformY
import ru.trassa.geometry.RouteType
import ru.trassa.geometry.Vec3
import java.io.File
import java.util.UUID

/** Точка трассы во время AR-записи (координаты текущей сессии) */
data class DraftPoint(val position: Vec3, val normal: Vec3)

/** Трасса, записываемая в текущей AR-сессии */
data class DraftRoute(val type: RouteType, val points: List<DraftPoint> = emptyList()) {
    val length get() = ru.trassa.geometry.Geometry.polylineLength(points.map { it.position })
}

/** Ориентир, отмеченный в текущей сессии */
data class DraftReference(val position: Vec3, val photo: Bitmap?)

/**
 * Операции, которые затрагивают и базу, и файлы фото.
 * [imagesDir] вынесен в параметр, чтобы тесты могли писать во временную папку.
 */
class TrassaRepository(
    private val db: TrassaDatabase,
    private val imagesDir: File,
) {
    constructor(context: Context) : this(
        TrassaDatabase.get(context),
        File(context.filesDir, "images").apply { mkdirs() },
    )

    val dao = db.dao()

    // ── Объекты ──

    suspend fun createProject(name: String, address: String): Long =
        dao.insert(ProjectEntity(name = name.trim().ifEmpty { "Новый объект" }, address = address.trim()))

    suspend fun renameProject(id: Long, name: String) {
        val p = dao.project(id) ?: return
        if (name.isBlank()) return
        dao.update(p.copy(name = name.trim(), updatedAt = now()))
    }

    suspend fun deleteProject(id: Long) {
        val p = dao.project(id) ?: return
        dao.roomsOf(id).forEach { deleteRoomFiles(it) }
        dao.delete(p) // комнаты, трассы, точки и ориентиры удалятся каскадно
    }

    // ── Комнаты ──

    suspend fun renameRoom(id: Long, name: String) {
        val r = dao.room(id)?.room ?: return
        if (name.isBlank()) return
        dao.update(r.copy(name = name.trim(), updatedAt = now()))
        touchProject(r.projectId)
    }

    suspend fun deleteRoom(id: Long) {
        val r = dao.room(id)?.room ?: return
        deleteRoomFiles(r)
        dao.delete(r)
        touchProject(r.projectId)
    }

    suspend fun deleteRoute(id: Long) {
        val route = dao.route(id) ?: return
        route.photoPath?.let { File(it).delete() }
        dao.delete(route)
        touchRoom(route.roomId)
    }

    suspend fun updateRoute(route: RouteEntity) {
        dao.update(route)
        touchRoom(route.roomId)
    }

    suspend fun setRoutePhoto(routeId: Long, bitmap: Bitmap) {
        val route = dao.route(routeId) ?: return
        route.photoPath?.let { File(it).delete() }
        dao.update(route.copy(photoPath = saveImage(bitmap)))
        touchRoom(route.roomId)
    }

    suspend fun addNote(roomId: Long, text: String) {
        if (text.isBlank()) return
        dao.insert(RoomNoteEntity(roomId = roomId, text = text.trim()))
        touchRoom(roomId)
    }

    suspend fun deleteNote(note: RoomNoteEntity) = dao.delete(note)

    /**
     * Сохраняет результат AR-записи.
     *
     * Новая комната: координаты текущей сессии становятся «системой комнаты»,
     * ориентиры [references] сохраняются как есть.
     *
     * Существующая комната: сначала пользователь совместил ориентиры, получив
     * [toCurrentSession] (комната → текущая сессия). Новые точки переводим
     * обратно в систему комнаты обратным преобразованием, чтобы все трассы
     * комнаты жили в одной системе координат.
     */
    suspend fun saveRecording(
        projectId: Long,
        existingRoomId: Long?,
        name: String,
        drafts: List<DraftRoute>,
        references: List<DraftReference>,
        preview: Bitmap?,
        toCurrentSession: RigidTransformY = RigidTransformY.IDENTITY,
    ): Long {
        val toRoom = toCurrentSession.inverse()
        val previewPath = preview?.let { saveImage(it) }
        // Фото ориентиров пишем до транзакции - файловые операции не откатываются
        val refPhotos = references.map { r -> r.photo?.let { saveImage(it) } }

        return db.withTransaction {
            val roomId: Long
            if (existingRoomId == null) {
                roomId = dao.insert(
                    RoomEntity(projectId = projectId, name = name.trim().ifEmpty { "Комната" }, previewPath = previewPath)
                )
                dao.insertReferences(references.mapIndexed { i, r ->
                    ReferencePointEntity(roomId = roomId, idx = i, x = r.position.x, y = r.position.y, z = r.position.z, photoPath = refPhotos[i])
                })
            } else {
                roomId = existingRoomId
                val old = dao.room(roomId)!!.room
                old.previewPath?.takeIf { previewPath != null }?.let { File(it).delete() }
                dao.update(old.copy(
                    name = name.trim().ifEmpty { old.name },
                    previewPath = previewPath ?: old.previewPath,
                    updatedAt = now(),
                ))
            }
            // Трассы из одной точки не сохраняем - это не линия
            for (draft in drafts.filter { it.points.size >= 2 }) {
                val routeId = dao.insert(RouteEntity(roomId = roomId, type = draft.type))
                dao.insertPoints(draft.points.mapIndexed { i, p ->
                    val pos = toRoom.apply(p.position)
                    val n = toRoom.rotate(p.normal)
                    RoutePointEntity(routeId = routeId, idx = i, x = pos.x, y = pos.y, z = pos.z, nx = n.x, ny = n.y, nz = n.z)
                })
            }
            touchProject(projectId)
            roomId
        }
    }

    // ── Помощники ──

    private suspend fun touchRoom(roomId: Long) {
        val r = dao.room(roomId)?.room ?: return
        dao.update(r.copy(updatedAt = now()))
        touchProject(r.projectId)
    }

    private suspend fun touchProject(projectId: Long) {
        val p = dao.project(projectId) ?: return
        dao.update(p.copy(updatedAt = now()))
    }

    private suspend fun deleteRoomFiles(room: RoomEntity) {
        room.previewPath?.let { File(it).delete() }
        (dao.routePhotos(room.id) + dao.referencePhotos(room.id)).forEach { File(it).delete() }
    }

    fun saveImage(bitmap: Bitmap): String {
        imagesDir.mkdirs()
        val file = File(imagesDir, "${UUID.randomUUID()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
        return file.absolutePath
    }

    private fun now() = System.currentTimeMillis()
}
