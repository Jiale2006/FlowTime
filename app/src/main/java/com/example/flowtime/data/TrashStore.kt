package com.example.flowtime.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 回收站条目：记录本体 + 被删除的时刻 */
data class TrashedRecord(val record: FocusRecord, val deletedAt: Long)

/**
 * 任务回收站条目：**一次删除动作 = 一条**（父任务连同它的子任务、以及它名下的记录）。
 *
 * [records] 是删任务那一刻从 `records` 表摘下来的快照（`taskId` 保持原值）。
 * 为什么要一起摘：任务删了统计里还挂着它的名字（"已删除的内容又出现了"），
 * 而墓碑过期解绑 `taskId` 后这些记录还会按名字"复活"进统计。挪走才彻底干净。
 *
 * 彻底删 / 超 30 天时这些记录**不会销毁**，而是解绑成自由记录放回 `records`
 * （`taskId = null` → 统计里归「未分类」），历史时长一分不丢。
 */
data class TrashedTask(
    val tasks: List<FocusTask>,
    val records: List<FocusRecord> = emptyList(),
    val deletedAt: Long,
) {
    /** 被点删除的那个任务（列表展示用）；子任务排在后面 */
    val root: FocusTask get() = tasks.first()

    val ids: List<String> get() = tasks.map { it.id }

    /** 除 root 外的子任务数 */
    val childCount: Int get() = (tasks.size - 1).coerceAtLeast(0)

    val recordMinutes: Int get() = records.sumOf { it.minutes }
}

/**
 * 回收站：删掉的记录落成私有目录里的一个 JSON 文件，保留 30 天。
 *
 * 为什么不给 `records` 加 `deletedAt` 列做软删除：那要手写 MIGRATION_2_3，
 * 而 release 包没有 run-as、数据不可还原 —— 迁移写错的代价是用户数据全丢。
 * 单文件足够：一条记录 JSON 约 150 字节，**30 天 / 200 条上限**远远到不了 MB 级。
 *
 * 所有方法都是同步文件 IO，调用方必须在 IO 线程（`FlowTimeRepository` 已包好）。
 */
class TrashStore(dir: File) {

    private val file = File(dir.apply { mkdirs() }, "trash.json")

    /** 任务回收站单独一个文件：老的 trash.json 结构不用改，也就不可能读坏历史记录 */
    private val taskFile = File(dir, "trash_tasks.json")

    /** 全部有效条目，最新删的前面；过期的顺手剔除并回写 */
    fun all(now: Long = System.currentTimeMillis()): List<TrashedRecord> {
        val raw = load()
        val alive = raw.filter { it.deletedAt > now - RETENTION_MS }
        if (alive.size != raw.size) save(alive)
        return alive.sortedByDescending { it.deletedAt }
    }

    /** 追加一条。同一 id 已在回收站里时先去掉旧的，避免连删两次留两条 */
    fun add(record: FocusRecord, now: Long = System.currentTimeMillis()) {
        val kept = all(now).filter { it.record.id != record.id }.toMutableList()
        kept.add(0, TrashedRecord(record, now))
        save(kept.take(MAX_ENTRIES))
    }

    /** 按原 id 移除（恢复或彻底删除后调用） */
    fun remove(recordId: Long) {
        val kept = all().filter { it.record.id != recordId }
        save(kept)
    }

    fun clear() {
        runCatching { file.delete() }
    }

    // ---- 任务回收站 ----

    /**
     * 全部任务条目，最新删的前面。
     * **不过滤过期**：过期任务要先把名下记录 `taskId` 解绑，那步有数据库写入，
     * 放在 [FlowTimeRepository] 里做；文件层只认读写。
     */
    fun allTasks(): List<TrashedTask> = loadTasks().sortedByDescending { it.deletedAt }

    /**
     * 任务进回收站（[tasks] 第一个是被点删除的那个，其余是它的子任务；
     * [records] 是同时从表里摘下来的记录快照）。
     * 同一 rootId 已在里面时先替换，避免"删了 → 恢复 → 又删"留两行。
     */
    fun addTask(
        tasks: List<FocusTask>,
        records: List<FocusRecord> = emptyList(),
        now: Long = System.currentTimeMillis(),
    ) {
        if (tasks.isEmpty()) return
        val rootId = tasks.first().id
        val kept = allTasks().filter { it.root.id != rootId }.toMutableList()
        kept.add(0, TrashedTask(tasks, records, now))
        saveTasks(kept.take(MAX_TASK_ENTRIES))
    }

    fun removeTask(rootId: String) {
        saveTasks(allTasks().filter { it.root.id != rootId })
    }

    fun replaceTasks(list: List<TrashedTask>) {
        saveTasks(list)
    }

    fun clearTasks() {
        runCatching { taskFile.delete() }
    }

    private fun loadTasks(): List<TrashedTask> = runCatching {
        if (!taskFile.exists()) return emptyList()
        val array = JSONArray(taskFile.readText(Charsets.UTF_8))
        List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            TrashedTask(
                tasks = BackupFormat.tasksFromJson(obj.getJSONArray("tasks")),
                records = obj.optJSONArray("records")
                    ?.let { BackupFormat.recordsFromJson(it) }
                    .orEmpty(),
                deletedAt = obj.optLong("deletedAt", 0L),
            )
        }.filter { it.tasks.isNotEmpty() }
        // 文件坏了就当空的：任务删除这条路不能因为读不出来而崩
    }.getOrDefault(emptyList())

    private fun saveTasks(list: List<TrashedTask>) {
        runCatching {
            val array = JSONArray()
            list.forEach { entry ->
                array.put(
                    JSONObject()
                        .put("deletedAt", entry.deletedAt)
                        .put("tasks", BackupFormat.tasksToJson(entry.tasks))
                        .put("records", BackupFormat.recordsToJson(entry.records)),
                )
            }
            taskFile.writeText(array.toString(), Charsets.UTF_8)
        }
    }

    private fun load(): List<TrashedRecord> = runCatching {
        if (!file.exists()) return emptyList()
        val array = JSONArray(file.readText(Charsets.UTF_8))
        List(array.length()) { i ->
            val obj = array.getJSONObject(i)
            TrashedRecord(
                record = BackupFormat.recordFromJson(obj.getJSONObject("record")),
                deletedAt = obj.optLong("deletedAt", 0L),
            )
        }
        // 文件损坏不该让删除这条路崩掉：读不出来就当空的，下次写会覆盖
    }.getOrDefault(emptyList())

    private fun save(list: List<TrashedRecord>) {
        runCatching {
            val array = JSONArray()
            list.forEach { entry ->
                array.put(
                    JSONObject()
                        .put("deletedAt", entry.deletedAt)
                        .put("record", BackupFormat.recordToJson(entry.record)),
                )
            }
            file.writeText(array.toString(), Charsets.UTF_8)
        }
    }

    companion object {
        /** 保留天数：与 Apple 备忘录的「最近删除」一致 */
        const val RETENTION_DAYS = 30

        /** 保留毫秒数（公开：任务过期清理在 Repository 里判，口径必须同一份） */
        const val RETENTION_MS = RETENTION_DAYS * 24L * 60L * 60L * 1000L

        /** 防御性上限：正常用不可能触到，只防"一天删几百条"把文件撑大 */
        private const val MAX_ENTRIES = 200

        /**
         * 任务条目上限（比记录少）：一条条目可能裹着几十条记录，
         * 50 条 × 平均 20 条记录 ≈ 150KB 量级，足够。
         */
        private const val MAX_TASK_ENTRIES = 50

        fun isExpired(deletedAt: Long, now: Long = System.currentTimeMillis()): Boolean =
            deletedAt <= now - RETENTION_MS
    }
}
