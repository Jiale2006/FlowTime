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
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Context.flowTimeStore: DataStore<Preferences> by preferencesDataStore(name = "flowtime")

/** 倒计时默认时长（分钟）——与 AppViewModel.DEFAULT_DURATION_MINUTES 对齐 */
private const val DEFAULT_DURATION_MINUTES = 45

/** 旧版本的默认时长默认值，用于一次性迁移判断 */
private const val LEGACY_DEFAULT_MINUTES = 25

/** 备份文件格式版本：以后加字段靠它做兼容 */
private const val BACKUP_SCHEMA = 1

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

    val settings: Flow<AppSettings> = context.flowTimeStore.data.map { prefs ->
        AppSettings(
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
    }

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

    /** 全量导出（任务 + 记录），用于换机、重装后恢复 */
    suspend fun exportBackup(): String = JSONObject()
        .put("schema", BACKUP_SCHEMA)
        .put("exportedAt", System.currentTimeMillis())
        .put("tasks", tasksToJson(dao.tasksOnce()))
        .put("records", recordsToJson(dao.recordsOnce()))
        .toString()

    /** 全量恢复：整体覆盖现有数据。解析失败返回 false，此时不动库里的数据 */
    suspend fun importBackup(raw: String): Boolean = runCatching {
        val json = JSONObject(raw)
        if (!json.has("tasks") || !json.has("records")) return false
        val tasks = tasksFromJson(json.getJSONArray("tasks"))
        val records = recordsFromJson(json.getJSONArray("records"))
        dao.replaceAll(tasks, records)
    }.isSuccess

    // ---- JSON 编解码（旧数据迁移与备份共用）----

    private fun defaultSeedTasks(): List<FocusTask> = listOf(
        FocusTask("seed-1", "英语阅读", 25, "今天", 0, 0, null),
        FocusTask("seed-2", "高数习题", 40, "今天", 1, 1, null),
        FocusTask("seed-3", "项目整理", 30, "周六", 2, 2, null),
        FocusTask("seed-4", "复盘总结", 20, "周日", 3, 3, null),
    )

    private fun decodeTasks(raw: String): List<FocusTask> =
        if (raw.isBlank()) emptyList() else tasksFromJson(JSONArray(raw))

    private fun tasksFromJson(array: JSONArray): List<FocusTask> = List(array.length()) { i ->
        val obj = array.getJSONObject(i)
        FocusTask(
            id = obj.getString("id"),
            name = obj.getString("name"),
            durationMinutes = obj.optInt("minutes", 25),
            dateLabel = obj.optString("dateLabel", "今天"),
            colorIndex = obj.optInt("colorIndex", 0),
            // 旧数据无 iconIndex 字段时按 colorIndex 映射，保持原图标
            iconIndex = obj.optInt("iconIndex", obj.optInt("colorIndex", 0)),
            // parentId 优先；旧数据里叫 categoryId（旧版独立大类表的 id，已迁移为父任务 id）。
            // 注意：JSON null 用 optString 会得到字符串 "null"，必须显式判断 isNull，否则顶层任务会被误判为子任务。
            parentId = obj.optNullableString("parentId") ?: obj.optNullableString("categoryId"),
        )
    }

    private fun tasksToJson(list: List<FocusTask>): JSONArray = JSONArray().apply {
        list.forEach { task ->
            put(
                JSONObject()
                    .put("id", task.id)
                    .put("name", task.name)
                    .put("minutes", task.durationMinutes)
                    .put("dateLabel", task.dateLabel)
                    .put("colorIndex", task.colorIndex)
                    .put("iconIndex", task.iconIndex)
                    .put("parentId", task.parentId ?: JSONObject.NULL),
            )
        }
    }

    /** 旧版独立大类表的解码（仅一次性迁移使用） */
    private fun decodeCategories(raw: String): List<Category> {
        if (raw.isBlank()) return emptyList()
        val array = JSONArray(raw)
        return List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            Category(
                id = obj.getString("id"),
                name = obj.getString("name"),
                colorIndex = obj.optInt("colorIndex", i),
            )
        }
    }

    private fun decodeRecords(raw: String): List<FocusRecord> =
        if (raw.isBlank()) emptyList() else recordsFromJson(JSONArray(raw))

    private fun recordsFromJson(array: JSONArray): List<FocusRecord> = List(array.length()) { i ->
        val obj = array.getJSONObject(i)
        val minutes = obj.optInt("minutes", 0)
        val end = obj.optLong("endEpochMillis", 0L)
        FocusRecord(
            id = obj.optLong("id", 0L),
            taskId = obj.optNullableString("taskId"),
            taskName = obj.optString("taskName", ""),
            minutes = minutes,
            // 旧数据没有开始时间：按结束时间往前推
            startEpochMillis = obj.optLong("startEpochMillis", end - minutes * 60_000L),
            endEpochMillis = end,
        )
    }

    private fun recordsToJson(list: List<FocusRecord>): JSONArray = JSONArray().apply {
        list.forEach { record ->
            put(
                JSONObject()
                    .put("id", record.id)
                    .put("taskId", record.taskId ?: JSONObject.NULL)
                    .put("taskName", record.taskName)
                    .put("minutes", record.minutes)
                    .put("startEpochMillis", record.startEpochMillis)
                    .put("endEpochMillis", record.endEpochMillis),
            )
        }
    }

    /** JSON 里的可空字符串：字段缺失 / null / 空串 / 字符串 "null" 一律当 null */
    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return getString(key).takeIf { it.isNotBlank() && it != "null" }
    }
}
