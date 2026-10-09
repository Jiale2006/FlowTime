package com.example.flowtime.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 备份文件的编解码。纯 JVM，不碰 Context / Room / DataStore，可直接跑单测。
 *
 * 为什么单独抽出来：导出与恢复是两份**手工维护的字段清单**，
 * 加了字段却忘了同步另一头时，编译通过、导出成功，数据却静默丢失。
 * 放到这里配一组往返测试，忘同步会在提交前就红。
 */
object BackupFormat {

    /** 备份格式版本：2 起 records 带 source，并新增 settings 段 */
    const val SCHEMA = 2

    /** 设置项字段数。测试拿它当哨兵：加字段忘了同步编解码，测试会红 */
    const val SETTINGS_FIELD_COUNT = 16

    /** 备份校验结果，决定 UI 该说什么 */
    enum class Check { OK, NOT_A_BACKUP, TOO_NEW }

    // ---------- 整包 ----------

    fun encode(
        tasks: List<FocusTask>,
        records: List<FocusRecord>,
        settings: AppSettings,
        exportedAt: Long,
    ): String = JSONObject()
        .put("schema", SCHEMA)
        .put("exportedAt", exportedAt)
        .put("tasks", tasksToJson(tasks))
        .put("records", recordsToJson(records))
        .put("settings", settingsToJson(settings))
        .toString()

    /**
     * 只解析头部做校验，不动数据。
     * 版本比当前新时拒绝——否则新字段被静默丢弃，用户以为恢复成功其实数据缺了一块。
     */
    fun check(raw: String): Check = runCatching {
        val json = JSONObject(raw)
        if (!json.has("tasks") || !json.has("records")) return Check.NOT_A_BACKUP
        if (json.optInt("schema", 1) > SCHEMA) return Check.TOO_NEW
        Check.OK
    }.getOrDefault(Check.NOT_A_BACKUP)

    /** schema 1 的旧备份没有 settings 段，此时应保持当前设置不动 */
    fun hasSettings(json: JSONObject): Boolean = json.has("settings") && !json.isNull("settings")

    fun tasksOf(json: JSONObject): List<FocusTask> = tasksFromJson(json.getJSONArray("tasks"))

    fun recordsOf(json: JSONObject): List<FocusRecord> = recordsFromJson(json.getJSONArray("records"))

    fun settingsOf(json: JSONObject, base: AppSettings): AppSettings =
        settingsFromJson(json.getJSONObject("settings"), base)

    // ---------- 设置项 ----------

    fun settingsToJson(s: AppSettings): JSONObject = JSONObject()
        .put("nickname", s.nickname)
        // 头像只存路径：同机恢复能复用，换机后路径失效会自动退化成无头像
        .put("avatarPath", s.avatarPath)
        .put("defaultDurationMinutes", s.defaultDurationMinutes)
        .put("taskListView", s.taskListView)
        .put("ringtoneEnabled", s.ringtoneEnabled)
        .put("autoStart", s.autoStart)
        .put("dailyGoalMinutes", s.dailyGoalMinutes)
        .put("reminderTime", s.reminderTime)
        .put("reminderEnabled", s.reminderEnabled)
        .put("pomodoroFocusMinutes", s.pomodoroFocusMinutes)
        .put("pomodoroBreakMinutes", s.pomodoroBreakMinutes)
        .put("pomodoroLongBreakMinutes", s.pomodoroLongBreakMinutes)
        .put("pomodoroRoundsBeforeLongBreak", s.pomodoroRoundsBeforeLongBreak)
        .put("pomodoroTipShown", s.pomodoroTipShown)
        .put("statsRange", s.statsRange)
        .put("themeMode", s.themeMode)

    /** 缺字段就沿用 [base]（当前值），避免旧备份把没存过的项重置成默认 */
    fun settingsFromJson(o: JSONObject, base: AppSettings): AppSettings = base.copy(
        nickname = o.optString("nickname", base.nickname),
        avatarPath = o.optString("avatarPath", base.avatarPath),
        defaultDurationMinutes = o.optInt("defaultDurationMinutes", base.defaultDurationMinutes),
        taskListView = o.optBoolean("taskListView", base.taskListView),
        ringtoneEnabled = o.optBoolean("ringtoneEnabled", base.ringtoneEnabled),
        autoStart = o.optBoolean("autoStart", base.autoStart),
        dailyGoalMinutes = o.optInt("dailyGoalMinutes", base.dailyGoalMinutes),
        reminderTime = o.optString("reminderTime", base.reminderTime),
        reminderEnabled = o.optBoolean("reminderEnabled", base.reminderEnabled),
        pomodoroFocusMinutes = o.optInt("pomodoroFocusMinutes", base.pomodoroFocusMinutes),
        pomodoroBreakMinutes = o.optInt("pomodoroBreakMinutes", base.pomodoroBreakMinutes),
        pomodoroLongBreakMinutes = o.optInt(
            "pomodoroLongBreakMinutes",
            base.pomodoroLongBreakMinutes,
        ),
        pomodoroRoundsBeforeLongBreak = o.optInt(
            "pomodoroRoundsBeforeLongBreak",
            base.pomodoroRoundsBeforeLongBreak,
        ),
        pomodoroTipShown = o.optBoolean("pomodoroTipShown", base.pomodoroTipShown),
        statsRange = o.optString("statsRange", base.statsRange),
        themeMode = o.optString("themeMode", base.themeMode),
    )

    // ---------- 任务 ----------

    /** 旧版整表 JSON（一次性迁移用） */
    fun decodeTasks(raw: String): List<FocusTask> =
        if (raw.isBlank()) emptyList() else tasksFromJson(JSONArray(raw))

    fun tasksFromJson(array: JSONArray): List<FocusTask> = List(array.length()) { i ->
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
            // JSON null 用 optString 会得到字符串 "null"，必须显式判断，否则顶层任务会被误判为子任务。
            parentId = obj.optNullableString("parentId") ?: obj.optNullableString("categoryId"),
        )
    }

    fun tasksToJson(list: List<FocusTask>): JSONArray = JSONArray().apply {
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
    fun decodeCategories(raw: String): List<Category> {
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

    // ---------- 记录 ----------

    /** 旧版整表 JSON（一次性迁移用） */
    fun decodeRecords(raw: String): List<FocusRecord> =
        if (raw.isBlank()) emptyList() else recordsFromJson(JSONArray(raw))

    fun recordsFromJson(array: JSONArray): List<FocusRecord> = List(array.length()) { i ->
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
            // schema 1 的旧备份没有 source 列，一律按真实计时处理（与 db 迁移口径一致）
            source = RecordSource.of(obj.optString("source", "")).name,
        )
    }

    fun recordsToJson(list: List<FocusRecord>): JSONArray = JSONArray().apply {
        list.forEach { record ->
            put(
                JSONObject()
                    .put("id", record.id)
                    .put("taskId", record.taskId ?: JSONObject.NULL)
                    .put("taskName", record.taskName)
                    .put("minutes", record.minutes)
                    .put("startEpochMillis", record.startEpochMillis)
                    .put("endEpochMillis", record.endEpochMillis)
                    .put("source", record.source),
            )
        }
    }

    /** JSON 里的可空字符串：字段缺失 / null / 空串 / 字符串 "null" 一律当 null */
    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return getString(key).takeIf { it.isNotBlank() && it != "null" }
    }
}
