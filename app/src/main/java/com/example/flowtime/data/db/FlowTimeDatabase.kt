package com.example.flowtime.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.flowtime.data.FocusRecord
import com.example.flowtime.data.FocusTask
import kotlinx.coroutines.flow.Flow

@Dao
interface FlowTimeDao {

    /** 任务：按插入顺序返回（rowid 稳定） */
    @Query("SELECT * FROM tasks ORDER BY rowid")
    fun tasks(): Flow<List<FocusTask>>

    @Query("SELECT * FROM tasks")
    suspend fun tasksOnce(): List<FocusTask>

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun taskCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: FocusTask)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<FocusTask>)

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun deleteTasks(ids: List<String>)

    /** 记录：按结束时间升序（导出与分页都按时间走） */
    @Query("SELECT * FROM records ORDER BY endEpochMillis")
    fun records(): Flow<List<FocusRecord>>

    @Query("SELECT * FROM records ORDER BY endEpochMillis")
    suspend fun recordsOnce(): List<FocusRecord>

    @Query("SELECT COUNT(*) FROM records")
    suspend fun recordCount(): Int

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    @Insert
    suspend fun insertRecord(record: FocusRecord)

    @Insert
    suspend fun insertRecords(records: List<FocusRecord>)

    /** 改一条记录（目前只用于手动补录记录改时长，结束时刻保持不变） */
    @Update
    suspend fun updateRecord(record: FocusRecord)

    /** 删一条记录 */
    @Query("DELETE FROM records WHERE id = :id")
    suspend fun deleteRecord(id: Long)

    @Query("DELETE FROM records")
    suspend fun clearRecords()

    /** 旧 JSON 迁移：一次事务灌完，失败不会留下半份数据 */
    @Transaction
    suspend fun importAll(tasks: List<FocusTask>, records: List<FocusRecord>) {
        if (tasks.isNotEmpty()) insertTasks(tasks)
        if (records.isNotEmpty()) insertRecords(records)
    }

    /** 恢复备份：整体覆盖（调用方负责二次确认） */
    @Transaction
    suspend fun replaceAll(tasks: List<FocusTask>, records: List<FocusRecord>) {
        deleteAllTasks()
        clearRecords()
        if (tasks.isNotEmpty()) insertTasks(tasks)
        if (records.isNotEmpty()) insertRecords(records)
    }
}

@Database(
    entities = [FocusTask::class, FocusRecord::class],
    version = 2,
    exportSchema = false,
)
abstract class FlowTimeDatabase : RoomDatabase() {

    abstract fun dao(): FlowTimeDao

    companion object {
        /**
         * v1 → v2：records 表加 `source` 列（记录来源）。
         *
         * 语句必须与实体上 `@ColumnInfo(defaultValue = "TIMER")` 生成的
         * `DEFAULT 'TIMER'` 完全一致，否则迁移后 Room 的 schema 校验会失败。
         * 旧数据没有来源概念，一律按 TIMER（真实计时）处理，语义正确。
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE records ADD COLUMN source TEXT NOT NULL DEFAULT 'TIMER'")
            }
        }

        @Volatile
        private var instance: FlowTimeDatabase? = null

        fun get(context: Context): FlowTimeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                FlowTimeDatabase::class.java,
                "flowtime.db",
            )
                .addMigrations(MIGRATION_1_2)
                .build()
                .also { instance = it }
        }
    }
}
