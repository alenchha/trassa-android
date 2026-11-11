package ru.trassa.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import ru.trassa.geometry.RouteType
import ru.trassa.geometry.Vec3
import ru.trassa.theme.title

// Схема данных:
// Project (объект) 1—N Room (комната) 1—N Route (трасса) 1—N RoutePoint (точка)
//                                     1—N ReferencePoint (ориентир)
//                                     1—N RoomNote (заметка)
// Все координаты хранятся в системе координат ПЕРВОЙ записи комнаты.
// При просмотре они переводятся в текущую AR-сессию через совмещение ориентиров.
// Фото хранятся файлами в filesDir/images, в базе - только путь.

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val address: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

@Entity(
    tableName = "rooms",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId")],
)
data class RoomEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    /** Снимок AR-экрана с трассами */
    val previewPath: String? = null,
)

@Entity(
    tableName = "routes",
    foreignKeys = [ForeignKey(RoomEntity::class, ["id"], ["roomId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("roomId")],
)
data class RouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roomId: Long,
    val type: RouteType,
    val title: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** Фото штробы до отделки */
    val photoPath: String? = null,
)

@Entity(
    tableName = "route_points",
    foreignKeys = [ForeignKey(RouteEntity::class, ["id"], ["routeId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("routeId")],
)
data class RoutePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routeId: Long,
    val idx: Int,
    val x: Float, val y: Float, val z: Float,
    /** Нормаль стены - чтобы «положить» линию и зону на стену */
    val nx: Float, val ny: Float, val nz: Float,
) {
    val position get() = Vec3(x, y, z)
    val normal get() = Vec3(nx, ny, nz)
}

/** Ориентир (реперная точка): угол, край проёма - то, что не изменится после ремонта */
@Entity(
    tableName = "reference_points",
    foreignKeys = [ForeignKey(RoomEntity::class, ["id"], ["roomId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("roomId")],
)
data class ReferencePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roomId: Long,
    val idx: Int,
    val x: Float, val y: Float, val z: Float,
    /** Фото места вокруг ориентира - подсказка при совмещении */
    val photoPath: String? = null,
) {
    val position get() = Vec3(x, y, z)
}

@Entity(
    tableName = "room_notes",
    foreignKeys = [ForeignKey(RoomEntity::class, ["id"], ["roomId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("roomId")],
)
data class RoomNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val roomId: Long,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
)

// ── Составные модели для чтения ──────────────────────────────────────────────

data class RouteWithPoints(
    @Embedded val route: RouteEntity,
    @Relation(parentColumn = "id", entityColumn = "routeId")
    val pointsUnordered: List<RoutePointEntity>,
) {
    val points get() = pointsUnordered.sortedBy { it.idx }
    val length get() = ru.trassa.geometry.Geometry.polylineLength(points.map { it.position })
    val displayTitle get() = route.title.ifBlank { route.type.title }
}

data class RoomDetails(
    @Embedded val room: RoomEntity,
    @Relation(entity = RouteEntity::class, parentColumn = "id", entityColumn = "roomId")
    val routesUnordered: List<RouteWithPoints>,
    @Relation(parentColumn = "id", entityColumn = "roomId")
    val referencesUnordered: List<ReferencePointEntity>,
    @Relation(parentColumn = "id", entityColumn = "roomId")
    val notesUnordered: List<RoomNoteEntity>,
) {
    val routes get() = routesUnordered.sortedBy { it.route.createdAt }
    val references get() = referencesUnordered.sortedBy { it.idx }
    val notes get() = notesUnordered.sortedByDescending { it.createdAt }
    val totalLength get() = routes.sumOf { it.length.toDouble() }.toFloat()
    val hasReferences get() = references.size >= 2
}

/** Строка списка объектов: объект + счётчики + превью */
data class ProjectSummary(
    @Embedded val project: ProjectEntity,
    val roomsCount: Int,
    val routesCount: Int,
    val coverPath: String?,
)

/** Строка списка комнат */
data class RoomSummary(
    @Embedded val room: RoomEntity,
    val routesCount: Int,
)
