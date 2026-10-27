package com.example.flowtime.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份编解码的单元测试。
 *
 * 重点不是"能不能跑"，而是**字段清单的完整性**：导出与恢复是两份手工维护的字段表，
 * 加了字段忘同步另一头时不会报错、只会静默丢数据。往返全等 + 字段数哨兵就是拦它的。
 */
class BackupFormatTest {

    /** 每一项都不同于默认值——否则往返测试会被"默认值恰好相同"蒙混过关 */
    private val customized = AppSettings(
        nickname = "佳乐",
        avatarPath = "avatar_123.jpg",
        defaultDurationMinutes = 90,
        taskListView = true,
        freeFocusOrder = 3,
        ringtoneEnabled = false,
        autoStart = false,
        dailyGoalMinutes = 240,
        reminderTime = "07:30",
        reminderEnabled = true,
        pomodoroFocusMinutes = 50,
        pomodoroBreakMinutes = 10,
        pomodoroLongBreakMinutes = 30,
        pomodoroRoundsBeforeLongBreak = 3,
        pomodoroTipShown = true,
        statsRange = "MONTH",
        themeMode = "WATERCOLOR",
    )

    /** 与 [customized] 在 17 个字段上全都不同——用来验证"每一项都真的从 JSON 读" */
    private val anotherSet = AppSettings(
        nickname = "另一个人",
        avatarPath = "other.png",
        defaultDurationMinutes = 5,
        taskListView = false,
        freeFocusOrder = 0,
        ringtoneEnabled = true,
        autoStart = true,
        dailyGoalMinutes = 30,
        reminderTime = "06:00",
        reminderEnabled = false,
        pomodoroFocusMinutes = 1,
        pomodoroBreakMinutes = 2,
        pomodoroLongBreakMinutes = 3,
        pomodoroRoundsBeforeLongBreak = 9,
        pomodoroTipShown = false,
        statsRange = "ALL",
        themeMode = "NOCTURNE",
    )

    private fun record(
        id: Long,
        taskId: String? = "t1",
        taskName: String = "英语阅读",
        minutes: Int = 45,
        startMs: Long = 1_700_000_000_000L,
        endMs: Long = 1_700_002_700_000L,
        source: RecordSource = RecordSource.MANUAL,
    ) = FocusRecord(
        id = id,
        taskId = taskId,
        taskName = taskName,
        minutes = minutes,
        startEpochMillis = startMs,
        endEpochMillis = endMs,
        source = source.name,
    )

    // ---- 设置：往返与哨兵 ----

    @Test
    fun `设置 17 项往返全等`() {
        val json = BackupFormat.settingsToJson(customized)
        // 换两个完全不同的 base 各读一次：任何一项忘了从 JSON 取，
        // 结果就会是 base 的对应值，这里立刻红
        assertEquals(customized, BackupFormat.settingsFromJson(json, AppSettings()))
        assertEquals(customized, BackupFormat.settingsFromJson(json, anotherSet))
    }

    @Test
    fun `设置字段数是 17——加字段忘了同步时这条会红`() {
        assertEquals(
            BackupFormat.SETTINGS_FIELD_COUNT,
            BackupFormat.settingsToJson(AppSettings()).length(),
        )
    }

    @Test
    fun `设置字段名与预期一致——改名或漏写时这条会红`() {
        val expected = setOf(
            "nickname", "avatarPath", "defaultDurationMinutes", "taskListView", "freeFocusOrder",
            "ringtoneEnabled", "autoStart", "dailyGoalMinutes", "reminderTime",
            "reminderEnabled", "pomodoroFocusMinutes", "pomodoroBreakMinutes",
            "pomodoroLongBreakMinutes", "pomodoroRoundsBeforeLongBreak",
            "pomodoroTipShown", "statsRange", "themeMode",
        )
        assertEquals(expected, BackupFormat.settingsToJson(customized).keys().asSequence().toSet())
    }

    @Test
    fun `缺失字段沿用当前值而不是重置成默认`() {
        // 模拟 schema 1 的旧备份：只有部分字段，其余应保持用户当前设置
        val json = JSONObject().put("nickname", "旧备份里的名字")
        val base = customized.copy(themeMode = "NOCTURNE", dailyGoalMinutes = 999)
        val restored = BackupFormat.settingsFromJson(json, base)
        assertEquals("旧备份里的名字", restored.nickname)
        assertEquals("NOCTURNE", restored.themeMode)
        assertEquals(999, restored.dailyGoalMinutes)
    }

    @Test
    fun `hasSettings 能区分旧备份与显式 null`() {
        val withSettings = JSONObject("""{"tasks":[],"records":[],"settings":{"themeMode":"WEEK"}}""")
        val noSettings = JSONObject("""{"tasks":[],"records":[]}""")
        val nullSettings = JSONObject("""{"tasks":[],"records":[],"settings":null}""")
        assertTrue(BackupFormat.hasSettings(withSettings))
        assertFalse(BackupFormat.hasSettings(noSettings))
        assertFalse(BackupFormat.hasSettings(nullSettings))
    }

    // ---- 任务 ----

    @Test
    fun `任务往返——父任务与子任务都不丢`() {
        val tasks = listOf(
            FocusTask("p1", "英语", 25, "今天", 0, 0, null),
            FocusTask("c1", "语法练习", 40, "周六", 1, 2, "p1"),
        )
        val json = BackupFormat.tasksToJson(tasks)
        assertEquals(tasks, BackupFormat.tasksFromJson(json))
    }

    @Test
    fun `任务 parentId 写 null 时读回来仍是 null`() {
        // JSON null 用 optString 会变成字符串 "null"，那样顶层任务会被当成子任务
        val array = JSONArray("""[{"id":"p1","name":"英语","parentId":null}]""")
        val task = BackupFormat.tasksFromJson(array).single()
        assertNull(task.parentId)
    }

    @Test
    fun `任务旧格式——categoryId 当父任务 缺 iconIndex 时沿用 colorIndex`() {
        val array = JSONArray(
            """[{"id":"c1","name":"语法","categoryId":"p1","colorIndex":2}]""",
        )
        val task = BackupFormat.tasksFromJson(array).single()
        assertEquals("p1", task.parentId)
        assertEquals(2, task.iconIndex)
        assertEquals(2, task.colorIndex)
        assertEquals(25, task.durationMinutes)
        assertEquals("今天", task.dateLabel)
    }

    @Test
    fun `任务旧格式——显式 categoryId null 不算父任务`() {
        val array = JSONArray("""[{"id":"p1","name":"英语","categoryId":"null"}]""")
        assertNull(BackupFormat.tasksFromJson(array).single().parentId)
    }

    @Test
    fun `空的任务 JSON 串解成空列表`() {
        assertTrue(BackupFormat.decodeTasks("").isEmpty())
        assertTrue(BackupFormat.decodeTasks("  ").isEmpty())
    }

    @Test
    fun `旧版大类表能解出来`() {
        val categories = BackupFormat.decodeCategories(
            """[{"id":"cat1","name":"学习"},{"id":"cat2","name":"运动","colorIndex":3}]""",
        )
        assertEquals(2, categories.size)
        assertEquals(Category("cat1", "学习", 0), categories[0])
        assertEquals(Category("cat2", "运动", 3), categories[1])
        assertTrue(BackupFormat.decodeCategories("").isEmpty())
    }

    // ---- 记录 ----

    @Test
    fun `记录 7 项往返全等 含 source`() {
        val records = listOf(
            record(id = 1, source = RecordSource.MANUAL),
            record(id = 2, source = RecordSource.TIMER, taskId = null, taskName = "自由专注"),
        )
        val json = BackupFormat.recordsToJson(records)
        assertEquals(7, json.getJSONObject(0).length())
        assertEquals(records, BackupFormat.recordsFromJson(json))
    }

    @Test
    fun `记录 taskId 为 null 时读回来仍是 null`() {
        val array = JSONArray("""[{"id":1,"taskId":null,"minutes":30}]""")
        assertNull(BackupFormat.recordsFromJson(array).single().taskId)
    }

    @Test
    fun `记录旧格式——无 source 按 TIMER 无开始时间按结束时间前推`() {
        val array = JSONArray(
            """[{"id":1,"taskName":"高数","minutes":60,"endEpochMillis":1700003600000}]""",
        )
        val rec = BackupFormat.recordsFromJson(array).single()
        assertEquals(RecordSource.TIMER.name, rec.source)
        assertEquals(1_700_003_600_000L, rec.endEpochMillis)
        assertEquals(1_700_003_600_000L - 60 * 60_000L, rec.startEpochMillis)
        assertNull(rec.taskId)
    }

    @Test
    fun `记录 source 未知值时按 TIMER 处理`() {
        val array = JSONArray("""[{"id":1,"minutes":10,"source":"SOMETHING_ELSE"}]""")
        assertEquals(RecordSource.TIMER.name, BackupFormat.recordsFromJson(array).single().source)
    }

    @Test
    fun `空的记录 JSON 串解成空列表`() {
        assertTrue(BackupFormat.decodeRecords("").isEmpty())
    }

    // ---- 校验 ----

    @Test
    fun `合法备份通过校验`() {
        assertEquals(BackupFormat.Check.OK, BackupFormat.check(twoKeyBackup(schema = 2)))
        // 老备份没有 schema 字段也算合法（按 1 处理）
        assertEquals(BackupFormat.Check.OK, BackupFormat.check(twoKeyBackup(schema = null)))
    }

    @Test
    fun `缺 tasks 或 records 的文件不是备份`() {
        assertEquals(BackupFormat.Check.NOT_A_BACKUP, BackupFormat.check("""{"tasks":[]}"""))
        assertEquals(BackupFormat.Check.NOT_A_BACKUP, BackupFormat.check("""{"records":[]}"""))
        assertEquals(BackupFormat.Check.NOT_A_BACKUP, BackupFormat.check("{}"))
    }

    @Test
    fun `非法 JSON 不是备份而不是抛异常`() {
        assertEquals(BackupFormat.Check.NOT_A_BACKUP, BackupFormat.check("这不是 JSON"))
        assertEquals(BackupFormat.Check.NOT_A_BACKUP, BackupFormat.check(""))
    }

    @Test
    fun `来自更新版本的备份被拒绝`() {
        assertEquals(
            BackupFormat.Check.TOO_NEW,
            BackupFormat.check(twoKeyBackup(schema = BackupFormat.SCHEMA + 1)),
        )
    }

    // ---- 整包 ----

    @Test
    fun `整包导出后能原样解回来`() {
        val tasks = listOf(
            FocusTask("p1", "英语", 25, "今天", 0, 0, null),
            FocusTask("c1", "语法练习", 40, "周六", 1, 2, "p1"),
        )
        val records = listOf(record(id = 1), record(id = 2, source = RecordSource.TIMER))
        val raw = BackupFormat.encode(tasks, records, customized, exportedAt = 1_760_000_000_000L)

        assertEquals(BackupFormat.Check.OK, BackupFormat.check(raw))
        val json = JSONObject(raw)
        assertEquals(BackupFormat.SCHEMA, json.getInt("schema"))
        assertEquals(1_760_000_000_000L, json.getLong("exportedAt"))
        assertEquals(tasks, BackupFormat.tasksOf(json))
        assertEquals(records, BackupFormat.recordsOf(json))
        assertEquals(customized, BackupFormat.settingsOf(json, AppSettings()))
    }

    @Test
    fun `空数据的整包也能往返`() {
        val raw = BackupFormat.encode(emptyList(), emptyList(), AppSettings(), exportedAt = 1L)
        assertEquals(BackupFormat.Check.OK, BackupFormat.check(raw))
        val json = JSONObject(raw)
        assertTrue(BackupFormat.tasksOf(json).isEmpty())
        assertTrue(BackupFormat.recordsOf(json).isEmpty())
        assertEquals(AppSettings(), BackupFormat.settingsOf(json, AppSettings()))
    }

    /** 构造一个只有 tasks / records 的备份串；schema 传 null 表示老格式 */
    private fun twoKeyBackup(schema: Int?): String = JSONObject()
        .put("tasks", JSONArray())
        .put("records", JSONArray())
        .apply { if (schema != null) put("schema", schema) }
        .toString()
}
