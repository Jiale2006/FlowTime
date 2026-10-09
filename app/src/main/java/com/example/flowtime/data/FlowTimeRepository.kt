package com.example.flowtime.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.flowtime.data.db.FlowTimeDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.io.File
import java.util.UUID

private val Context.flowTimeStore: DataStore<Preferences> by preferencesDataStore(name = "flowtime")

/** 倒计时默认时长（分钟）——与 AppViewModel.DEFAULT_DURATION_MINUTES 对齐 */
private const val DEFAULT_DURATION_MINUTES = 45

/** 旧版本的默认时长默认值，用于一次性迁移判断 */
private const val LEGACY_DEFAULT_MINUTES = 25

/** 本机自动回滚点保留份数（超出后删最旧的，避免私有目录无限增长） */
private const val ROLLBACK_KEEP = 5

/**
 * 本地持久化：
 * - 任务与专注记录走 Room（增量写入 + 按时间排序查询，不再整表重写 JSON）；
 * - 设置项仍在 DataStore（单行配置，读写都简单）；
 * - 旧版 DataStore 里的 JSON 在首次启动时一次性导入 Room 并置位标记（幂等，重试不会重复导入）。
 */
class FlowTimeRepository(private val context: Context) {

    private val dao = FlowTimeDatabase.get(context).dao()

    private object Keys {
        // 旧版 JSON：迁移时只读，不再写
        val TASKS = stringPreferencesKey("tasks_json")
        val CATEGORIES = stringPreferencesKey("categories_json")
        val RECORDS = stringPreferencesKey("records_json")
        val DB_MIGRATED = booleanPreferencesKey("room_migrated")

        val NICKNAME = stringPreferencesKey("nickname")
        val AVATAR_PATH = stringPreferencesKey("avatar_path")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val POMODORO_FOCUS = intPreferencesKey("pomodoro_focus_minutes")
        val POMODORO_BREAK = intPreferencesKey("pomodoro_break_minutes")
        val POMODORO_LONG_BREAK = intPreferencesKey("pomodoro_long_break_minutes")
        val POMODORO_ROUNDS = intPreferencesKey("pomodoro_rounds_before_long_break")
        val POMODORO_TIP_SHOWN = booleanPreferencesKey("pomodoro_tip_shown")
        val DEFAULT_MINUTES = intPreferencesKey("default_minutes")
        val TASK_LIST_VIEW = booleanPreferencesKey("task_list_view")
        val DURATION_MIGRATED = booleanPreferencesKey("duration_default_migrated")
        val RINGTONE = booleanPreferencesKey("ringtone_enabled")
        val AUTO_START = booleanPreferencesKey("auto_start")
        val DAILY_GOAL = intPreferencesKey("daily_goal_minutes")
        val REMINDER = stringPreferencesKey("reminder_time")
        val STATS_RANGE = stringPreferencesKey("stats_range")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }

    val tasks: Flow<List<FocusTask>> = dao.tasks()
    val records: Flow<List<FocusRecord>> = dao.records()

    val settings: Flow<AppSettings> = context.flowTimeStore.data.map { toSettings(it) }

    /** DataStore 偏好 → [AppSettings]（备份恢复时也要用同一套映射，所以抽出来） */
    private fun toSettings(prefs: Preferences): AppSettings = AppSettings(
        nickname = prefs[Keys.NICKNAME] ?: "心流用户",
        avatarPath = prefs[Keys.AVATAR_PATH].orEmpty(),
        defaultDurationMinutes = prefs[Keys.DEFAULT_MINUTES] ?: DEFAULT_DURATION_MINUTES,
        taskListView = prefs[Keys.TASK_LIST_VIEW] ?: false,
        ringtoneEnabled = prefs[Keys.RINGTONE] ?: true,
        autoStart = prefs[Keys.AUTO_START] ?: true,
        dailyGoalMinutes = prefs[Keys.DAILY_GOAL] ?: 120,
        reminderTime = prefs[Keys.REMINDER] ?: "20:00",
        reminderEnabled = prefs[Keys.REMINDER_ENABLED] ?: false,
        pomodoroFocusMinutes = prefs[Keys.POMODORO_FOCUS] ?: 25,
        pomodoroBreakMinutes = prefs[Keys.POMODORO_BREAK] ?: 5,
        pomodoroLongBreakMinutes = prefs[Keys.POMODORO_LONG_BREAK] ?: 15,
        pomodoroRoundsBeforeLongBreak = prefs[Keys.POMODORO_ROUNDS] ?: 4,
        pomodoroTipShown = prefs[Keys.POMODORO_TIP_SHOWN] ?: false,
        statsRange = prefs[Keys.STATS_RANGE] ?: "WEEK",
        themeMode = prefs[Keys.THEME_MODE] ?: "NOCTURNE",
    )

    /**
     * 一次性迁移：旧版本「默认时长」默认值是 25 分钟，且任何一次设置保存都会把它写进偏好，
     * 导致新默认值 45 分钟被旧值覆盖。这里把从未主动调整过的 25 分钟升级为 45 分钟。
     */
    suspend fun migrateDefaultDurationIfNeeded() {
        context.flowTimeStore.edit { prefs ->
            if (prefs[Keys.DURATION_MIGRATED] == true) return@edit
            if (prefs[Keys.DEFAULT_MINUTES] == LEGACY_DEFAULT_MINUTES) {
                prefs[Keys.DEFAULT_MINUTES] = DEFAULT_DURATION_MINUTES
            }
            prefs[Keys.DURATION_MIGRATED] = true
        }
    }

    /**
     * 旧版 JSON → Room 迁移：
     * - 任务/记录从 DataStore 的 JSON 读出后灌进 Room；
     * - 旧记录没有 taskId，按任务名补上（补不到就留空，视为自由专注/任务已删）；
     * - 旧版独立「大类」表补成顶层任务，小任务的 categoryId 已在解析时变成 parentId；
     * - 幂等：已迁移或库里已有记录则只补标记位，重试不会重复导入。
     */
    suspend fun migrateJsonToRoomIfNeeded() {
        val prefs = context.flowTimeStore.data.first()
        if (prefs[Keys.DB_MIGRATED] == true || dao.recordCount() > 0) {
            markJsonMigrated()
            return
        }
        val legacyTasks = prefs[Keys.TASKS]?.let { runCatching { decodeTasks(it) }.getOrNull() }
        val baseTasks = legacyTasks ?: if (dao.taskCount() == 0) defaultSeedTasks() else emptyList()
        val categoryTasks = decodeCategories(prefs[Keys.CATEGORIES].orEmpty())
            .filter { category -> baseTasks.none { it.id == category.id } }
            .map { category ->
                FocusTask(
                    id = category.id,
                    name = category.name,
                    durationMinutes = 25,
                    dateLabel = "今天",
                    colorIndex = category.colorIndex,
                    iconIndex = 0,
                    parentId = null,
                )
            }
        val tasks = baseTasks + categoryTasks
        val nameToId = tasks.associate { it.name to it.id }
        val records = decodeRecords(prefs[Keys.RECORDS].orEmpty()).map { record ->
            if (record.taskId == null) record.copy(taskId = nameToId[record.taskName]) else record
        }
        dao.importAll(tasks, records)
        markJsonMigrated()
    }

    private suspend fun markJsonMigrated() {
        context.flowTimeStore.edit { prefs -> prefs[Keys.DB_MIGRATED] = true }
    }

    suspend fun addTask(
        name: String,
        durationMinutes: Int,
        dateLabel: String,
        colorIndex: Int,
        iconIndex: Int,
        parentId: String?,
    ) {
        dao.insertTask(
            FocusTask(
                id = UUID.randomUUID().toString(),
                name = name,
                durationMinutes = durationMinutes,
                dateLabel = dateLabel,
                colorIndex = colorIndex,
                iconIndex = iconIndex,
                parentId = parentId,
            ),
        )
    }

    /** 删除任务：连同它下面的小任务一起删（级联）；历史记录保留 */
    suspend fun deleteTask(taskId: String) {
        val all = dao.tasksOnce()
        val removeIds = mutableSetOf(taskId)
        var changed = true
        while (changed) {
            changed = false
            all.forEach { task ->
                val parent = task.parentId ?: return@forEach
                if (parent in removeIds && removeIds.add(task.id)) changed = true
            }
        }
        dao.deleteTasks(removeIds.toList())
    }

    suspend fun addRecord(record: FocusRecord) {
        dao.insertRecord(record)
    }

    /** 改一条记录（手动补录改时长用；结束时刻保持不变由调用方算好） */
    suspend fun updateRecord(record: FocusRecord) {
        dao.updateRecord(record)
    }

    /** 删一条记录 */
    suspend fun deleteRecord(id: Long) {
        dao.deleteRecord(id)
    }

    suspend fun updateSettings(settings: AppSettings) {
        context.flowTimeStore.edit { prefs ->
            prefs[Keys.NICKNAME] = settings.nickname
            prefs[Keys.AVATAR_PATH] = settings.avatarPath
            prefs[Keys.DEFAULT_MINUTES] = settings.defaultDurationMinutes
            prefs[Keys.TASK_LIST_VIEW] = settings.taskListView
            prefs[Keys.RINGTONE] = settings.ringtoneEnabled
            prefs[Keys.AUTO_START] = settings.autoStart
            prefs[Keys.DAILY_GOAL] = settings.dailyGoalMinutes
            prefs[Keys.REMINDER] = settings.reminderTime
            prefs[Keys.REMINDER_ENABLED] = settings.reminderEnabled
            prefs[Keys.POMODORO_FOCUS] = settings.pomodoroFocusMinutes
            prefs[Keys.POMODORO_BREAK] = settings.pomodoroBreakMinutes
            prefs[Keys.POMODORO_LONG_BREAK] = settings.pomodoroLongBreakMinutes
            prefs[Keys.POMODORO_ROUNDS] = settings.pomodoroRoundsBeforeLongBreak
            prefs[Keys.POMODORO_TIP_SHOWN] = settings.pomodoroTipShown
            prefs[Keys.STATS_RANGE] = settings.statsRange
            prefs[Keys.THEME_MODE] = settings.themeMode
        }
    }

    // ---- 备份 / 恢复 ----
    // 编解码全在 BackupFormat（纯 JVM、有单测覆盖），这里只管读写数据库与偏好。

    /** 本机自动回滚点：恢复前留底，出事了能一键退回去 */
    data class Rollback(val file: File, val savedAt: Long)

    /** 只解析头部做校验，不动数据 */
    fun checkBackup(raw: String): BackupFormat.Check = BackupFormat.check(raw)

    /** 全量导出：任务 + 记录 + 全部设置（头像文件本身不入备份，只存路径） */
    suspend fun exportBackup(): String = BackupFormat.encode(
        tasks = dao.tasksOnce(),
        records = dao.recordsOnce(),
        settings = toSettings(context.flowTimeStore.data.first()),
        exportedAt = System.currentTimeMillis(),
    )

    /** 全量恢复：整体覆盖现有数据（调用方负责先留回滚点）。校验不过或解析失败时不动库里数据 */
    suspend fun importBackup(raw: String): Boolean = runCatching {
        if (BackupFormat.check(raw) != BackupFormat.Check.OK) return false
        val json = JSONObject(raw)
        dao.replaceAll(BackupFormat.tasksOf(json), BackupFormat.recordsOf(json))
        // schema 1 的旧备份没有 settings 段：保持当前设置不动，而不是把它们重置成默认值
        if (BackupFormat.hasSettings(json)) {
            val base = toSettings(context.flowTimeStore.data.first())
            updateSettings(BackupFormat.settingsOf(json, base))
        }
    }.isSuccess

    // ---- 本机回滚点 ----

    private val rollbackDir: File
        get() = File(context.filesDir, "backup").apply { mkdirs() }

    /** 把当前数据存成一份回滚点（恢复备份前自动调用） */
    suspend fun saveRollback(): Rollback? = runCatching {
        val now = System.currentTimeMillis()
        val file = File(rollbackDir, "rollback_$now.json")
        file.writeText(exportBackup(), Charsets.UTF_8)
        pruneRollbacks()
        Rollback(file, now)
    }.getOrNull()

    /** 本机回滚点，按时间倒序 */
    fun rollbacks(): List<Rollback> = (rollbackDir.listFiles() ?: emptyArray())
        .mapNotNull { file ->
            val stamp = file.name.removePrefix("rollback_").removeSuffix(".json").toLongOrNull()
                ?: return@mapNotNull null
            Rollback(file, stamp)
        }
        .sortedByDescending { it.savedAt }

    /** 只保留最近的 [ROLLBACK_KEEP] 份 */
    private fun pruneRollbacks() {
        rollbacks().drop(ROLLBACK_KEEP).forEach { runCatching { it.file.delete() } }
    }

    /** 用某个回滚点恢复（同样走 schema 校验） */
    suspend fun restoreRollback(rollback: Rollback): Boolean = runCatching {
        importBackup(rollback.file.readText(Charsets.UTF_8))
    }.getOrDefault(false)

    // ---- JSON 编解码（旧数据迁移用；其余见 BackupFormat）----

    private fun defaultSeedTasks(): List<FocusTask> = listOf(
        FocusTask("seed-1", "英语阅读", 25, "今天", 0, 0, null),
        FocusTask("seed-2", "高数习题", 40, "今天", 1, 1, null),
        FocusTask("seed-3", "项目整理", 30, "周六", 2, 2, null),
        FocusTask("seed-4", "复盘总结", 20, "周日", 3, 3, null),
    )

    private fun decodeTasks(raw: String): List<FocusTask> = BackupFormat.decodeTasks(raw)

    /** 旧版独立大类表的解码（仅一次性迁移使用） */
    private fun decodeCategories(raw: String): List<Category> = BackupFormat.decodeCategories(raw)

    private fun decodeRecords(raw: String): List<FocusRecord> = BackupFormat.decodeRecords(raw)
}
