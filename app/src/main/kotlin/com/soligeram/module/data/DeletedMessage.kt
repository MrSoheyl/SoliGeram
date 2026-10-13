package com.soligeram.module.data

import androidx.room.*

// ── Entity ───────────────────────────────────────────────────────────────

@Entity(tableName = "deleted_messages", primaryKeys = ["id", "dialog_id"])
data class DeletedMessage(
    @ColumnInfo(name = "id")        val id: Long,
    @ColumnInfo(name = "dialog_id") val dialogId: Long,
    @ColumnInfo(name = "text")      val text: String?,
    @ColumnInfo(name = "date")      val date: Long,
    @ColumnInfo(name = "sender_id") val senderId: Long = 0L,
    @ColumnInfo(name = "is_edited") val isEdited: Boolean = false
)

// ── DAO ──────────────────────────────────────────────────────────────────

@Dao
interface DeletedMessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: DeletedMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<DeletedMessage>)

    @Query("SELECT * FROM deleted_messages WHERE dialog_id = :dialogId ORDER BY date DESC")
    suspend fun getForDialog(dialogId: Long): List<DeletedMessage>

    @Query("SELECT * FROM deleted_messages WHERE id = :id AND dialog_id = :dialogId LIMIT 1")
    suspend fun get(id: Long, dialogId: Long): DeletedMessage?

    @Query("DELETE FROM deleted_messages WHERE id = :id AND dialog_id = :dialogId")
    suspend fun delete(id: Long, dialogId: Long)

    @Query("DELETE FROM deleted_messages WHERE dialog_id = :dialogId")
    suspend fun deleteAllForDialog(dialogId: Long)

    @Query("DELETE FROM deleted_messages")
    suspend fun deleteAll()
}

// ── Database ─────────────────────────────────────────────────────────────

@Database(entities = [DeletedMessage::class], version = 1, exportSchema = false)
abstract class SoliDatabase : RoomDatabase() {
    abstract fun deletedMessageDao(): DeletedMessageDao

    companion object {
        @Volatile private var INSTANCE: SoliDatabase? = null

        fun get(context: android.content.Context): SoliDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SoliDatabase::class.java,
                    "soligeram_db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
