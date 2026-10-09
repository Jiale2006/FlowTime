package com.example.flowtime.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「任务 + 直接子任务」归属判定的单元测试。
 *
 * 这条规则是任务详情页统计口径的基础：统计页的「分类分布」本来就含子任务，
 * 任务详情页过去只算自己，同一个「数学」在两页给出不同时长。
 */
class RecordLinkTest {

    private fun task(id: String, name: String, parentId: String? = null) = FocusTask(
        id = id,
        name = name,
        durationMinutes = 25,
        dateLabel = "今天",
        colorIndex = 0,
        iconIndex = 0,
        parentId = parentId,
    )

    private fun record(taskId: String?, taskName: String) = FocusRecord(
        taskId = taskId,
        taskName = taskName,
        minutes = 30,
        startEpochMillis = 0L,
        endEpochMillis = 1_800_000L,
    )

    private val math = task("math", "数学")
    private val algebra = task("alg", "线代", parentId = "math")
    private val english = task("en", "英语")
    private val tasks = listOf(math, algebra, english)

    @Test
    fun `任务自己的记录命中`() {
        val hit = RecordLink.treePredicate(math, tasks)
        assertTrue(hit(record("math", "数学")))
    }

    @Test
    fun `直接子任务的记录也算命中`() {
        val hit = RecordLink.treePredicate(math, tasks)
        assertTrue(hit(record("alg", "线代")))
    }

    @Test
    fun `无关任务不命中`() {
        val hit = RecordLink.treePredicate(math, tasks)
        assertFalse(hit(record("en", "英语")))
    }

    @Test
    fun `子任务视角只算自己，不含父任务`() {
        val hit = RecordLink.treePredicate(algebra, tasks)
        assertTrue(hit(record("alg", "线代")))
        assertFalse("父任务的记录不该被子任务视图收进来", hit(record("math", "数学")))
    }

    @Test
    fun `旧记录按名字回退，主任务与子任务都命中`() {
        val hit = RecordLink.treePredicate(math, tasks)
        // taskId 为 null = 旧数据 / 自由专注，只能靠名字
        assertTrue(hit(record(null, "数学")))
        assertTrue(hit(record(null, "线代")))
        assertFalse(hit(record(null, "英语")))
    }

    @Test
    fun `改名后的旧记录仍归到任务名下`() {
        // 记录里存的是改名前的快照名；只要 taskId 还在，就照样命中
        val hit = RecordLink.treePredicate(math, tasks)
        assertTrue(hit(record("math", "高等数学")))
    }

    @Test
    fun `没有子任务时等价于 belongsTo`() {
        val hit = RecordLink.treePredicate(english, tasks)
        assertEquals(
            listOf(true, false),
            listOf(hit(record("en", "英语")), hit(record("math", "数学"))),
        )
    }
}
