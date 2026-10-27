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

    /** 任务：先按手动排序，再按插入顺序（没拖过的旧任务 sortOrder 都是 0） */
    @Query("SELECT * FROM tasks ORDER BY sortOrder, rowid")
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

    /** 归档/取消归档（调用方负责级联子任务） */
    @Query("UPDATE tasks SET archived = :archived WHERE id IN (:ids)")
    suspend fun setArchived(ids: List<String>, archived: Boolean)

    /** 排序：按传入顺序写成 1..n（一个事务内写完，中途失败不会留半套顺序） */
    @Query("UPDATE tasks SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: String, sortOrder: Int)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM tasks")
    suspend fun maxSortOrder(): Int

    @Transaction
    suspend fun applyOrder(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id -> setSortOrder(id, index + 1) }
    }

    /**
     * 改一个任务（改名 / 换图标配色）。
     * **必须用 @Update 而不是 insertTask 的 REPLACE**：REPLACE = 先删后插，
     * rowid 会变（任务列表按 rowid 排序），改个名字任务就跳到列表末尾了。
     */
    @Update
    suspend fun updateTaskRow(task: FocusTask)

    /** 改名后同步记录里的名字快照 —— 列表行直接用 taskName 显示，不刷就是旧名 */
    @Query("UPDATE records SET taskName = :name WHERE taskId = :taskId")
    suspend fun renameRecords(taskId: String, name: String)

    @Transaction
    suspend fun renameTask(task: FocusTask) {
        updateTaskRow(task)
        renameRecords(task.id, task.name)
    }

    /** 记录：按结束时间升序（导出与分页都按时间走） */
    @Query("SELECT * FROM records ORDER BY endEpochMillis")
    fun records(): Flow<List<FocusRecord>>

    @Query("SELECT * FROM records ORDER BY endEpochMillis")
    suspend fun recordsOnce(): List<FocusRecord>

    @Query("SELECT COUNT(*) FROM records")
    suspend fun recordCount(): Int

    /** 按 id 取一条；回收站恢复前用它判断原 id 是否已被新记录占用 */
    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun recordById(id: Long): FocusRecord?

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    /** 插一条记录，返回新 rowid（补录要用它做撤销） */
    @Insert
    suspend fun insertRecord(record: FocusRecord): Long

    @Insert
    suspend fun insertRecords(records: List<FocusRecord>)

    /** 改一条记录（目前只用于手动补录记录改时长，结束时刻保持不变） */
    @Update
    suspend fun updateRecord(record: FocusRecord)

    /** 删一条记录 */
    @Query("DELETE FROM records WHERE id = :id")
    suspend fun deleteRecord(id: Long)

    /** 批量删记录（删除任务时把名下记录一起摘进回收站） */
    @Query("DELETE FROM records WHERE id IN (:ids)")
    suspend fun deleteRecords(ids: List<Long>)

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
    version = 3,
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

        /**
         * v2 → v3：tasks 表加 `archived`（归档）与 `sortOrder`（手动排序键）。
         *
         * 两条 ALTER 都带 `DEFAULT 0`，与实体上的 `@ColumnInfo(defaultValue = "0")`
         * 一致，否则迁移后 Room 的 schema 校验会失败。旧任务自然就是“未归档 + sortOrder 0”，
         * 而 `ORDER BY sortOrder, rowid` 在全部为 0 时等价于原来的插入顺序 —— 清单不会被打乱。
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tasks ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
                .also { instance = it }
        }
    }
}
