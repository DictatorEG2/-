package com.sawt.voice.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import java.util.concurrent.ConcurrentHashMap

/** فهرس على createdAt لأن كل الاستعلامات ترتّب به. */
@Entity(tableName = "history", indices = [Index(value = ["createdAt"])])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val utterance: String,
    val actionLabel: String,
    val success: Boolean,
)

/** فهرس فريد على phrase لأن البحث عن الاختصار يتم بها في كل أمر. */
@Entity(tableName = "shortcuts", indices = [Index(value = ["phrase"], unique = true)])
data class ShortcutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phrase: String,
    val displayPhrase: String,
    val target: String,
    val createdAt: Long,
)

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(entity: HistoryEntity)

    @Query("SELECT id, createdAt, utterance, actionLabel, success FROM history ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<HistoryEntity>

    @Query("SELECT COUNT(id) FROM history")
    suspend fun count(): Int

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY createdAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}

@Dao
interface ShortcutDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ShortcutEntity)

    @Query("SELECT target FROM shortcuts WHERE phrase = :phrase LIMIT 1")
    suspend fun targetFor(phrase: String): String?

    @Query("SELECT id, phrase, displayPhrase, target, createdAt FROM shortcuts ORDER BY createdAt DESC LIMIT :limit OFFSET :offset")
    suspend fun page(limit: Int, offset: Int): List<ShortcutEntity>

    @Query("SELECT COUNT(id) FROM shortcuts")
    suspend fun count(): Int

    @Query("DELETE FROM shortcuts WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [HistoryEntity::class, ShortcutEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao
    abstract fun shortcutDao(): ShortcutDao
}

/** غلاف بذاكرة مؤقتة: بحث الاختصار يحدث مع كل أمر، والبيانات تتغير نادرًا. */
class ShortcutRepository(private val dao: ShortcutDao) {
    private val cache = ConcurrentHashMap<String, String>() // "" = غير موجود (تخزين سلبي)

    suspend fun targetFor(phrase: String): String? {
        if (phrase.isEmpty()) return null
        cache[phrase]?.let { return it.ifEmpty { null } }
        val target = dao.targetFor(phrase)
        cache[phrase] = target ?: ""
        return target
    }

    suspend fun add(phrase: String, displayPhrase: String, target: String) {
        dao.upsert(ShortcutEntity(phrase = phrase, displayPhrase = displayPhrase, target = target, createdAt = System.currentTimeMillis()))
        cache.clear()
    }

    suspend fun remove(id: Long) {
        dao.delete(id)
        cache.clear()
    }

    suspend fun page(limit: Int, offset: Int) = dao.page(limit, offset)
    suspend fun count() = dao.count()
}
