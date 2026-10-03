package com.example.flowtime.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.flowtime.data.Category
import com.example.flowtime.data.FocusRecord
import com.example.flowtime.data.FocusTask
import com.example.flowtime.data.RecordLink
import java.io.File
import java.time.Instant
import java.time.ZoneId

/**
 * CSV 导出：全部明细 / 按大类汇总。
 * - [writeTo]：存到用户自己挑的位置（系统文件选择器返回的 uri），不依赖任何接收应用，是主路径；
 * - [share]：发给已安装的接收应用，设备上若无人接收 text/csv 会提示「没有应用可执行此操作」。
 * 两条路径都不需要存储权限。
 */
object CsvExporter {

    enum class Mode { DETAIL, CATEGORY }

    fun fileName(mode: Mode): String =
        if (mode == Mode.DETAIL) "flowtime_detail.csv" else "flowtime_category.csv"

    /** 生成 CSV 文本（带 BOM，保证 Excel 打开中文不乱码） */
    fun toCsv(
        mode: Mode,
        records: List<FocusRecord>,
        tasks: List<FocusTask>,
        categories: List<Category>,
    ): String {
        val body = when (mode) {
            Mode.DETAIL -> buildDetail(records, tasks, categories)
            Mode.CATEGORY -> buildCategorySummary(records, tasks, categories)
        }
        return "\uFEFF$body"
    }

    /** 写进用户选定的文件；失败返回 false（调用方给提示） */
    fun writeTo(context: Context, uri: Uri, csv: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(csv.toByteArray(Charsets.UTF_8))
        } ?: return false
    }.isSuccess

    fun share(
        context: Context,
        mode: Mode,
        records: List<FocusRecord>,
        tasks: List<FocusTask>,
        categories: List<Category>,
    ) {
        val dir = File(context.cacheDir, "export").apply { mkdirs() }
        val file = File(dir, fileName(mode))
        file.writeText(toCsv(mode, records, tasks, categories), Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "心流时间专注记录")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "导出 CSV"))
    }

    private fun escape(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""

    private fun formatTime(epochMillis: Long): String {
        val t = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
        return "%04d-%02d-%02d %02d:%02d".format(t.year, t.monthValue, t.dayOfMonth, t.hour, t.minute)
    }

    /** 全部明细：每条记录一行，含所属大类与起止时间 */
    private fun buildDetail(
        records: List<FocusRecord>,
        tasks: List<FocusTask>,
        categories: List<Category>,
    ): String {
        val index = RecordLink.TaskIndex.of(tasks)
        val catName = categories.associate { it.id to it.name }
        val sb = StringBuilder("task_name,category,minutes,start_time,end_time\n")
        records.sortedBy { it.endEpochMillis }.forEach { record ->
            val category = index.categoryIdOf(record)?.let { catName[it] } ?: "未分类"
            sb.append(
                "${escape(index.displayName(record))},${escape(category)},${record.minutes}," +
                    "${formatTime(record.startEpochMillis)},${formatTime(record.endEpochMillis)}\n",
            )
        }
        return sb.toString()
    }

    /** 按大类汇总：每个大类一行合计 */
    private fun buildCategorySummary(
        records: List<FocusRecord>,
        tasks: List<FocusTask>,
        categories: List<Category>,
    ): String {
        val index = RecordLink.TaskIndex.of(tasks)
        val catName = categories.associate { it.id to it.name }
        val sb = StringBuilder("category,total_minutes,total_hours,session_count,task_count\n")
        records.groupBy { index.categoryIdOf(it) }
            .map { (categoryId, list) ->
                CategoryRow(
                    name = categoryId?.let { catName[it] } ?: "未分类",
                    minutes = list.sumOf { it.minutes },
                    sessions = list.size,
                    taskCount = list.map { RecordLink.keyOf(it) }.distinct().size,
                )
            }
            .sortedByDescending { it.minutes }
            .forEach { row ->
                sb.append(
                    "${escape(row.name)},${row.minutes},${"%.2f".format(row.minutes / 60.0)},${row.sessions},${row.taskCount}\n",
                )
            }
        return sb.toString()
    }

    private data class CategoryRow(
        val name: String,
        val minutes: Int,
        val sessions: Int,
        val taskCount: Int,
    )
}
