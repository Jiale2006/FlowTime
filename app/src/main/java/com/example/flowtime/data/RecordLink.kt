package com.example.flowtime.data

/**
 * 记录与任务的关联规则：
 * 优先 [FocusRecord.taskId]（任务改名不断链），
 * 旧记录 / 自由专注没有 taskId 时退回任务名匹配。
 *
 * 聚合时统一用 [keyOf] 作为分组键，避免"同一个任务因为改名被拆成两条"。
 */
object RecordLink {

    private const val NAME_PREFIX = "name:"

    /** 聚合分组键：有 taskId 用 id，否则用名字（自由专注 / 已删除任务） */
    fun keyOf(record: FocusRecord): String = record.taskId ?: (NAME_PREFIX + record.taskName)

    /** 任务对应的分组键 */
    fun keyOfTask(task: FocusTask): String = task.id

    /**
     * 该任务名下所有可能的分组键：
     * 任务本身（按 id）+ 同名的旧记录（改名前的历史仍然归到它名下）。
     */
    fun keysOf(task: FocusTask): Set<String> = setOf(keyOfTask(task), NAME_PREFIX + task.name)

    /** 记录是否属于该任务 */
    fun belongsTo(record: FocusRecord, task: FocusTask): Boolean =
        if (record.taskId != null) record.taskId == task.id else record.taskName == task.name

    /** 按分组键汇总分钟数 */
    fun minutesByKey(records: List<FocusRecord>): Map<String, Int> =
        records.groupBy { keyOf(it) }.mapValues { (_, list) -> list.sumOf { it.minutes } }

    /** 某任务的累计分钟数（含改名前的旧记录） */
    fun minutesOf(task: FocusTask, records: List<FocusRecord>): Int =
        records.filter { belongsTo(it, task) }.sumOf { it.minutes }

    /**
     * 任务查找表：统计、导出、任务页共用一份，避免每处各写一遍 id/名字映射。
     * 改名不断链的关键就在这里 —— 记录先按 taskId 找，找不到才退回名字。
     */
    data class TaskIndex(
        val byId: Map<String, FocusTask>,
        val idByName: Map<String, String>,
    ) {
        companion object {
            fun of(tasks: List<FocusTask>) = TaskIndex(
                byId = tasks.associateBy { it.id },
                idByName = tasks.associate { it.name to it.id },
            )
        }

        /** 记录归属的任务；任务已删或自由专注时返回 null */
        fun ownerOf(record: FocusRecord): FocusTask? {
            val ownerId = record.taskId ?: idByName[record.taskName]
            return ownerId?.let { byId[it] }
        }

        /** 记录显示名：任务还在就用当前名字，否则用记录里的历史快照 */
        fun displayName(record: FocusRecord): String = ownerOf(record)?.name ?: record.taskName

        /** 记录所属顶层分类 id（没有父任务返回 null，即「未分类」） */
        fun categoryIdOf(record: FocusRecord): String? = ownerOf(record)?.parentId
    }
}
