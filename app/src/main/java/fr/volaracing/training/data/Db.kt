package fr.volaracing.training.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity
data class Course(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val activity: String,
    val aLat: Double?, val aLon: Double?,
    val bLat: Double?, val bLon: Double?,
    val gateWidth: Double,
    /** Points intermediaires : "lat,lon;lat,lon" dans l'ordre du parcours. */
    val waypoints: String = "",
    /** Identifiant global stable (partage de parcours entre amis). */
    val uuid: String = "",
    val groupId: String = "",
)

/** Une sortie. courseId = -1 : session libre (sans parcours). */
@Entity
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: Long,
    val startUtcMs: Long,
    val name: String = "",
    val place: String = "",
    val notes: String = "",
    val activity: String = "VTT",
    val uuid: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val ownerColor: Int = 0,
    /** 0 = enregistree sur ce telephone, 1 = importee d'un ami. */
    val origin: Int = 0,
    val groupId: String = "",
)

@Entity
data class GroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
)

/**
 * Le trace detaille reste dans les CSV du dossier [path].
 * kind : 0 = chrono par les portes A/B ; 1 = run libre (portes non franchies, chrono approximatif).
 * splits : temps aux portes d'etape, decalages en ns depuis le depart, separes par des virgules.
 */
@Entity
data class Passage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val courseId: Long,
    val timeNs: Long,
    val lowQuality: Boolean,
    val vMax: Double,
    val gMax: Double,
    val startNs: Long,
    val endNs: Long,
    val path: String,
    val splits: String = "",
    val kind: Int = 0,
)

object Waypoints {
    fun encode(l: List<Pair<Double, Double>>): String = l.joinToString(";") { "${it.first},${it.second}" }
    fun decode(s: String): List<Pair<Double, Double>> =
        if (s.isBlank()) emptyList()
        else s.split(';').mapNotNull { v ->
            val p = v.split(',')
            val a = p.getOrNull(0)?.toDoubleOrNull()
            val b = p.getOrNull(1)?.toDoubleOrNull()
            if (p.size == 2 && a != null && b != null) Pair(a, b) else null
        }
}

@Dao
interface AppDao {
    @Insert suspend fun insertCourse(c: Course): Long
    @Update suspend fun updateCourse(c: Course)
    @Query("SELECT * FROM Course ORDER BY id DESC") fun courses(): Flow<List<Course>>
    @Query("SELECT * FROM Course WHERE id = :id") suspend fun course(id: Long): Course?
    @Query("DELETE FROM Course WHERE id = :id") suspend fun deleteCourse(id: Long)

    @Insert suspend fun insertGroup(g: GroupEntity)
    @Query("SELECT * FROM GroupEntity ORDER BY createdAt DESC") fun groups(): Flow<List<GroupEntity>>
    @Query("DELETE FROM GroupEntity WHERE id = :id") suspend fun deleteGroup(id: String)
    @Query("UPDATE Session SET groupId = '' WHERE groupId = :gid") suspend fun detachGroupSessions(gid: String)
    @Query("UPDATE Course SET groupId = '' WHERE groupId = :gid") suspend fun detachGroupCourses(gid: String)
    @Query("SELECT * FROM Session WHERE uuid = :uuid AND uuid != '' LIMIT 1") suspend fun sessionByUuid(uuid: String): Session?
    @Query("SELECT * FROM Course WHERE uuid = :uuid AND uuid != '' LIMIT 1") suspend fun courseByUuid(uuid: String): Course?

    @Insert suspend fun insertSession(s: Session): Long
    @Update suspend fun updateSession(s: Session)
    @Query("SELECT * FROM Session ORDER BY startUtcMs DESC") fun sessions(): Flow<List<Session>>
    @Query("SELECT * FROM Session WHERE id = :id") suspend fun session(id: Long): Session?
    @Query("DELETE FROM Session WHERE id = :id") suspend fun deleteSession(id: Long)
    @Query("DELETE FROM Passage WHERE sessionId = :id") suspend fun deletePassagesOfSession(id: Long)
    @Query("SELECT * FROM Passage WHERE sessionId = :sid ORDER BY id") fun passagesOfSession(sid: Long): Flow<List<Passage>>
    @Query("SELECT * FROM Passage WHERE sessionId = :sid ORDER BY id") suspend fun passagesOfSessionNow(sid: Long): List<Passage>
    @Query("DELETE FROM Passage WHERE sessionId = :sid AND kind IN (1, 2)") suspend fun deleteFreeRuns(sid: Long)
    @Query("UPDATE Passage SET courseId = -1 WHERE courseId = :cid") suspend fun detachPassages(cid: Long)
    @Query("UPDATE Session SET courseId = -1 WHERE courseId = :cid") suspend fun detachSessions(cid: Long)

    @Insert suspend fun insertPassage(p: Passage): Long
    @Query("SELECT * FROM Passage WHERE courseId = :cid ORDER BY id DESC") fun passages(cid: Long): Flow<List<Passage>>
    @Query("SELECT * FROM Passage WHERE id = :id") suspend fun passage(id: Long): Passage?
    @Query("SELECT * FROM Passage ORDER BY id DESC") fun allPassages(): Flow<List<Passage>>
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Course ADD COLUMN waypoints TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE Passage ADD COLUMN splits TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE Passage ADD COLUMN kind INTEGER NOT NULL DEFAULT 0")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Course ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE Course ADD COLUMN groupId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE Session ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE Session ADD COLUMN ownerId TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE Session ADD COLUMN ownerName TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE Session ADD COLUMN ownerColor INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE Session ADD COLUMN origin INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE Session ADD COLUMN groupId TEXT NOT NULL DEFAULT ''")
        db.execSQL("UPDATE Course SET uuid = lower(hex(randomblob(16)))")
        db.execSQL("UPDATE Session SET uuid = lower(hex(randomblob(16)))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `GroupEntity` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE Session ADD COLUMN name TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE Session ADD COLUMN place TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE Session ADD COLUMN notes TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE Session ADD COLUMN activity TEXT NOT NULL DEFAULT 'VTT'")
        db.execSQL("UPDATE Session SET activity = COALESCE((SELECT activity FROM Course WHERE Course.id = Session.courseId), 'VTT')")
    }
}

@Database(entities = [Course::class, Session::class, Passage::class, GroupEntity::class], version = 4, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "vola.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build().also { inst = it }
        }
    }
}
