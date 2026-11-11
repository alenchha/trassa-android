package ru.trassa.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.trassa.geometry.RouteType

class Converters {
    @TypeConverter fun fromType(t: RouteType): String = t.name
    @TypeConverter fun toType(s: String): RouteType = RouteType.valueOf(s)
}

@Dao
interface TrassaDao {
    // ── Объекты ──
    @Query(
        """
        SELECT p.*,
          (SELECT COUNT(*) FROM rooms r WHERE r.projectId = p.id) AS roomsCount,
          (SELECT COUNT(*) FROM routes t JOIN rooms r ON t.roomId = r.id WHERE r.projectId = p.id) AS routesCount,
          (SELECT r.previewPath FROM rooms r WHERE r.projectId = p.id AND r.previewPath IS NOT NULL
             ORDER BY r.updatedAt DESC LIMIT 1) AS coverPath
        FROM projects p ORDER BY p.updatedAt DESC
        """
    )
    fun observeProjects(): Flow<List<ProjectSummary>>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observeProject(id: Long): Flow<ProjectEntity?>

    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun project(id: Long): ProjectEntity?

    @Insert suspend fun insert(project: ProjectEntity): Long
    @Update suspend fun update(project: ProjectEntity)
    @Delete suspend fun delete(project: ProjectEntity)

    // ── Комнаты ──
    @Query(
        """
        SELECT r.*, (SELECT COUNT(*) FROM routes t WHERE t.roomId = r.id) AS routesCount
        FROM rooms r WHERE r.projectId = :projectId ORDER BY r.createdAt
        """
    )
    fun observeRooms(projectId: Long): Flow<List<RoomSummary>>

    @Transaction
    @Query("SELECT * FROM rooms WHERE id = :id")
    fun observeRoom(id: Long): Flow<RoomDetails?>

    @Transaction
    @Query("SELECT * FROM rooms WHERE id = :id")
    suspend fun room(id: Long): RoomDetails?

    @Query("SELECT * FROM rooms WHERE projectId = :projectId")
    suspend fun roomsOf(projectId: Long): List<RoomEntity>

    @Insert suspend fun insert(room: RoomEntity): Long
    @Update suspend fun update(room: RoomEntity)
    @Delete suspend fun delete(room: RoomEntity)

    // ── Трассы ──
    @Transaction
    @Query("SELECT * FROM routes WHERE id = :id")
    fun observeRoute(id: Long): Flow<RouteWithPoints?>

    @Query("SELECT * FROM routes WHERE id = :id")
    suspend fun route(id: Long): RouteEntity?

    @Query("SELECT photoPath FROM routes WHERE roomId = :roomId AND photoPath IS NOT NULL")
    suspend fun routePhotos(roomId: Long): List<String>

    @Insert suspend fun insert(route: RouteEntity): Long
    @Update suspend fun update(route: RouteEntity)
    @Delete suspend fun delete(route: RouteEntity)

    @Insert suspend fun insertPoints(points: List<RoutePointEntity>)
    @Insert suspend fun insertReferences(refs: List<ReferencePointEntity>)

    @Query("SELECT photoPath FROM reference_points WHERE roomId = :roomId AND photoPath IS NOT NULL")
    suspend fun referencePhotos(roomId: Long): List<String>

    // ── Заметки ──
    @Insert suspend fun insert(note: RoomNoteEntity): Long
    @Delete suspend fun delete(note: RoomNoteEntity)
}

@Database(
    entities = [
        ProjectEntity::class, RoomEntity::class, RouteEntity::class,
        RoutePointEntity::class, ReferencePointEntity::class, RoomNoteEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class TrassaDatabase : RoomDatabase() {
    abstract fun dao(): TrassaDao

    companion object {
        @Volatile private var instance: TrassaDatabase? = null

        fun get(context: Context): TrassaDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TrassaDatabase::class.java, "trassa.db")
                .build().also { instance = it }
        }
    }
}
