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
    version = 1,
    exportSchema = false,
)
abstract class FlowTimeDatabase : RoomDatabase() {

    abstract fun dao(): FlowTimeDao

    companion object {
        @Volatile
        private var instance: FlowTimeDatabase? = null

        fun get(context: Context): FlowTimeDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                FlowTimeDatabase::class.java,
                "flowtime.db",
            ).build().also { instance = it }
        }
    }
}
