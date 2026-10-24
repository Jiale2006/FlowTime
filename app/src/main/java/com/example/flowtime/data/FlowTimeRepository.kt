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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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

    /** 回收站：私有目录里的一个 JSON 文件，不碰 Room 表结构 */
    private val trash = TrashStore(File(context.filesDir, "trash"))

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

    /**
     * 删除任务：任务（含子任务）**连同它名下的记录**一起进回收站（一次删除动作 = 一条条目）。
     *
     * 为什么记录也要挪走：留在表里的话统计会继续显示"已删任务"的名字与分类，
     * 而墓碑过期解绑 `taskId` 后它们还会按名字"复活"回到统计 —— 删了就该哪儿都看不见。
     * 挪进回收站仍可恢复；彻底删 / 超 30 天时记录被放生回「未分类」，时长一分不丢。
     *
     * 顺序与删记录同一条规矩：**先落回收站再删库**，反了就是真丢数据。
     */
    suspend fun deleteTask(taskId: String) = withContext(Dispatchers.IO) {
        val all = dao.tasksOnce()
        val removeIds = collectSubtree(taskId, all)
        val removed = all.filter { it.id in removeIds }.sortedBy { it.id != taskId }
        val names = removed.mapTo(mutableSetOf()) { it.name }
        val affected = dao.recordsOnce().filter { belongsToAny(it, removeIds, names) }
        trash.addTask(removed, affected)
        dao.deleteTasks(removeIds.toList())
        if (affected.isNotEmpty()) dao.deleteRecords(affected.map { it.id })
    }

    /**
     * 记录是否属于这批任务 —— **与 [RecordLink.belongsTo] 同口径**：
     * 有 `taskId` 按 id 认，没有的（旧数据 / 迁移时没对上名字）按 `taskName` 快照认。
     *
     * 只按 id 认会漏掉 `taskId = null` 但名字就是它的老记录 —— 那些记录留在表里，
     * 统计一按名字归属就又把已删任务显示出来了（2026-10-23 他报的就是这个）。
     */
    private fun belongsToAny(record: FocusRecord, ids: Set<String>, names: Set<String>): Boolean =
        if (record.taskId != null) record.taskId in ids else record.taskName in names

    /** 任务 + 它所有层级的子孙 id（层级本就最多两层，这里仍写成通用的） */
    private fun collectSubtree(rootId: String, all: List<FocusTask>): Set<String> {
        val removeIds = mutableSetOf(rootId)
        var changed = true
        while (changed) {
            changed = false
            all.forEach { task ->
                val parent = task.parentId ?: return@forEach
                if (parent in removeIds && removeIds.add(task.id)) changed = true
            }
        }
        return removeIds
    }

    /** 插一条记录，返回新 rowid */
    suspend fun addRecord(record: FocusRecord): Long = dao.insertRecord(record)

    /** 改一条记录（手动补录改时长用；结束时刻保持不变由调用方算好） */
    suspend fun updateRecord(record: FocusRecord) {
        dao.updateRecord(record)
    }

    /** 删一条记录：**先落回收站再删库**（顺序反了就是真丢数据） */
    suspend fun deleteRecord(record: FocusRecord) = withContext(Dispatchers.IO) {
        trash.add(record)
        dao.deleteRecord(record.id)
    }

    // ---- 回收站 ----

    /** 可恢复的记录（30 天内），最新删的在前面 */
    suspend fun trashedRecords(): List<TrashedRecord> =
        withContext(Dispatchers.IO) { trash.all() }

    suspend fun trashCount(): Int = withContext(Dispatchers.IO) { trash.all().size }

    /**
     * 可恢复的任务（30 天内），最新删的在前面。
     *
     * 三件事按顺序做：
    1. **自愈**：前几版删除逻辑漏下的记录（`taskId = null` 但名字就是它的老记录、
     *    或者第一版只挪任务没挪记录）还赖在 `records` 表里 → 统计会照着名字把它们
     *    重新归到已删任务名下。这里扫一遍表补摘进条目，老用户不清数据也干净。
    2. **过期清理**：超 30 天的条目，记录不销毁，解绑（`taskId = null`）放生回
     *    `records`，统计里归「自由专注」，时长一分不少。
    3. 返回剩下的条目。
     */
    suspend fun trashedTasks(): List<TrashedTask> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val loaded = trash.allTasks()
        var all = loaded

        // 不能只在 `records` 为空时扫 —— 上一版按 taskId 摘过一次、漏掉 `taskId = null`
        // 的同名老记录，那些条目 records 非空，光看空不空永远发现不了。
        if (loaded.isNotEmpty()) {
            val table = dao.recordsOnce()
            // 只删"刚补摘的"这批：条目里已有的记录早就不在表里，
            // 它们的 id 可能已被 autoGenerate 复用给别人的记录，误删就是丢数据
            val pulledIds = mutableListOf<Long>()
            val fixed = loaded.map { entry ->
                val entryIds = entry.ids.toMutableSet()
                val entryNames = entry.tasks.mapTo(mutableSetOf()) { it.name }
                val known = entry.records.mapTo(mutableSetOf()) { it.id }
                val extra = table.filter { it.id !in known && belongsToAny(it, entryIds, entryNames) }
                if (extra.isEmpty()) {
                    entry
                } else {
                    pulledIds += extra.map { it.id }
                    entry.copy(records = entry.records + extra)
                }
            }
            if (pulledIds.isNotEmpty()) {
                dao.deleteRecords(pulledIds)
                trash.replaceTasks(fixed)
                all = fixed
            }
        }

        val (alive, expired) = all.partition { !TrashStore.isExpired(it.deletedAt, now) }
        if (expired.isNotEmpty()) {
            expired.forEach { releaseRecords(it.records) }
            trash.replaceTasks(alive)
        }
        alive
    }

    /**
     * 恢复一条。
     * 原 id 可能已被新记录占用 —— Room 的 autoGenerate 在删掉最大 id 后会复用它，
     * 所以先查一次，被占了就换成新 id 插回，否则主键冲突直接崩。
     */
    suspend fun restoreRecord(record: FocusRecord) = withContext(Dispatchers.IO) {
        val id = if (dao.recordById(record.id) == null) record.id else 0L
        dao.insertRecord(record.copy(id = id))
        trash.remove(record.id)
    }

    /** 永久删除一条回收站记录（不可逆，调用方负责确认） */
    suspend fun purgeRecord(recordId: Long) = withContext(Dispatchers.IO) { trash.remove(recordId) }

    /**
     * 恢复一个任务（连同被一起删掉的子任务**和记录**）。
     *
     * 先查库里是否已有同 id —— 恢复备份等场景可能已经塞回来过，
     * 直接 insert 会覆盖现有任务（UUID 撞车概率为零，但这条兜底不花什么钱）。
     * 记录同理：Room 的 autoGenerate 在删掉最大 id 后会复用，被占了就用新 id。
     */
    suspend fun restoreTask(rootId: String) = withContext(Dispatchers.IO) {
        val entry = trash.allTasks().firstOrNull { it.root.id == rootId } ?: return@withContext
        val existing = dao.tasksOnce().mapTo(mutableSetOf()) { it.id }
        val back = entry.tasks.filterNot { it.id in existing }
        if (back.isNotEmpty()) dao.insertTasks(back)
        entry.records.forEach { record ->
            val id = if (dao.recordById(record.id) == null) record.id else 0L
            dao.insertRecord(record.copy(id = id))
        }
        trash.removeTask(rootId)
    }

    /**
     * 永久删除一个任务：任务信息彻底丢掉，但它名下的记录**放生**（`taskId = null`）
     * 回到统计的「未分类」里 —— 时长是已经发生过的事实，不该因为删个任务就消失。
     */
    suspend fun purgeTask(rootId: String) = withContext(Dispatchers.IO) {
        val entry = trash.allTasks().firstOrNull { it.root.id == rootId } ?: return@withContext
        releaseRecords(entry.records)
        trash.removeTask(rootId)
    }

    /** 清空回收站：任务名下的记录同样放生（时长保留），只是不再挂在任何任务上 */
    suspend fun purgeTrash() = withContext(Dispatchers.IO) {
        trash.allTasks().forEach { releaseRecords(it.records) }
        trash.clearTasks()
        trash.clear()
    }

    /**
     * 把记录放生回 `records` 表并解绑（`taskId = null`）：归到「未分类」，时长不动。
     * 原 id 可能被 autoGenerate 复用，被占了就换成新 id。
     */
    private suspend fun releaseRecords(records: List<FocusRecord>) {
        records.forEach { record ->
            val id = if (dao.recordById(record.id) == null) record.id else 0L
            dao.insertRecord(record.copy(id = id, taskId = null))
        }
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
