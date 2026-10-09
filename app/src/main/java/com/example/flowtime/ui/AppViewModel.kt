package com.example.flowtime.ui

import android.app.Application
import android.widget.Toast
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.RingtoneManager
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.flowtime.service.FocusReminder
import com.example.flowtime.service.FocusTimerService
import com.example.flowtime.data.AppSettings
import com.example.flowtime.data.BackfillRules
import com.example.flowtime.data.BackupFormat
import com.example.flowtime.data.Category
import com.example.flowtime.data.FlowTimeRepository
import com.example.flowtime.data.FocusRecord
import com.example.flowtime.data.FocusTask
import com.example.flowtime.data.RecordLink
import com.example.flowtime.data.RecordSource
import com.example.flowtime.data.StatsRange
import com.example.flowtime.data.StatsRules
import com.example.flowtime.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = FlowTimeRepository(app)

    val tasks: StateFlow<List<FocusTask>> =
        repo.tasks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val records: StateFlow<List<FocusRecord>> =
        repo.records.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<AppSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    // ---- 界面主题 ----

    /** 当前主题（持久化；设置页切换后全 app 换装） */
    val themeMode: StateFlow<ThemeMode> = settings
        .map { parseThemeMode(it.themeMode) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.NOCTURNE)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repo.updateSettings(settings.value.copy(themeMode = mode.name)) }
    }

    private fun parseThemeMode(raw: String): ThemeMode =
        runCatching { ThemeMode.valueOf(raw) }.getOrDefault(ThemeMode.NOCTURNE)

    /**
     * 「分类」= 顶层且已挂有小任务的任务（衍生视图，不再单独存储）。
     * 任何任务点进去加了小任务，它就成为分类。
     */
    val categories: StateFlow<List<Category>> = repo.tasks
        .map { list ->
            val childCount = list.filter { it.parentId != null }.groupingBy { it.parentId!! }.eachCount()
            list.filter { it.parentId == null && (childCount[it.id] ?: 0) > 0 }
                .map { Category(id = it.id, name = it.name, colorIndex = it.colorIndex) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- 计时器 ----
    enum class TimerPhase { IDLE, RUNNING, PAUSED }

    /** 计时模式：正计时（无限累加）/ 倒计时（任务时长到点完成）/ 番茄计时（专注+休息循环） */
    enum class TimerMode { COUNT_UP, COUNT_DOWN, POMODORO }

    data class TimerState(
        val phase: TimerPhase = TimerPhase.IDLE,
        val mode: TimerMode = TimerMode.COUNT_DOWN,
        /** 番茄计时专用：当前是否处于休息段 */
        val inBreak: Boolean = false,
        /** 番茄计时专用：当前第几轮（从 1 开始） */
        val round: Int = 1,
        val selectedTaskId: String? = null,
        /** 默认 45 分钟（倒计时默认时长，不合适可随时重选） */
        val totalMillis: Long = DEFAULT_DURATION_MINUTES * 60_000L,
        val elapsedMillis: Long = 0L,
    ) {
        val remainingMillis: Long get() = (totalMillis - elapsedMillis).coerceAtLeast(0L)
        /** 环形进度：倒计时类为剩余比例；正计时为 30 分钟转一圈 */
        val progress: Float
            get() = when {
                mode == TimerMode.COUNT_UP -> (elapsedMillis % COUNT_UP_RING_PERIOD_MILLIS) / COUNT_UP_RING_PERIOD_MILLIS.toFloat()
                totalMillis <= 0L -> 0f
                else -> remainingMillis.toFloat() / totalMillis
            }
    }

    private val _timer = MutableStateFlow(TimerState())
    val timer: StateFlow<TimerState> = _timer.asStateFlow()

    /** 完成事件：非空时 UI 跳转完成页 */
    private val _completedRecord = MutableStateFlow<FocusRecord?>(null)
    val completedRecord: StateFlow<FocusRecord?> = _completedRecord.asStateFlow()

    /** 首页开始专注时选择的时长（分钟），倒计时使用；正计时/番茄忽略 */
    private val _selectedDurationMinutes = MutableStateFlow(DEFAULT_DURATION_MINUTES)
    val selectedDurationMinutes: StateFlow<Int> = _selectedDurationMinutes.asStateFlow()

    private var ticker: Job? = null

    private var taskSelectionInitialized = false

    /** 用户在首页手动选过时长后，不再被设置里的「默认时长」覆盖 */
    private var userPickedDuration = false

    /**
     * 统计页范围（本周/本月/全部）。
     * 必须声明在 init 之前：init 里的 settings.collect 会在构建期同步触发（StateFlow 立即发射首个值），
     * 若声明在 init 之后，此刻字段仍为 null，会直接 NPE 闪退。
     */
    private val _statsRange = MutableStateFlow(StatsRange.WEEK)

    init {
        // 旧版本写死过的 25 分钟默认值升级为 45 分钟（一次）
        viewModelScope.launch { repo.migrateDefaultDurationIfNeeded() }
        // 旧版 JSON 数据（任务/记录/独立大类表）一次性导入 Room，幂等
        viewModelScope.launch { repo.migrateJsonToRoomIfNeeded() }
        // 设置里的「默认专注时长」驱动首页倒计时默认值（未手动选过时生效）
        viewModelScope.launch {
            settings.collect { s ->
                if (userPickedDuration) return@collect
                if (_selectedDurationMinutes.value == s.defaultDurationMinutes) return@collect
                _selectedDurationMinutes.value = s.defaultDurationMinutes
                if (_timer.value.phase == TimerPhase.IDLE &&
                    _timer.value.mode == TimerMode.COUNT_DOWN
                ) {
                    _timer.update {
                        it.copy(totalMillis = s.defaultDurationMinutes * 60_000L, elapsedMillis = 0L)
                    }
                }
            }
        }
        // 统计页范围：启动时恢复上次选择（持久化在设置里）
        viewModelScope.launch {
            settings.collect { s ->
                val restored = runCatching { StatsRange.valueOf(s.statsRange) }
                    .getOrDefault(StatsRange.WEEK)
                if (_statsRange.value != restored) _statsRange.value = restored
            }
        }
        // 每日习惯提醒：设置变化时重新排程（冷启动也会执行一次，幂等）
        viewModelScope.launch {
            settings.collect { s ->
                FocusReminder.apply(
                    getApplication(),
                    enabled = s.reminderEnabled,
                    time = s.reminderTime,
                    goalMinutes = s.dailyGoalMinutes,
                )
            }
        }
        viewModelScope.launch {
            tasks.collect { list ->
                val state = _timer.value
                // 首次加载默认选第一个任务并同步待机时长；之后尊重用户的「自由专注」选择
                if (!taskSelectionInitialized && list.isNotEmpty()) {
                    taskSelectionInitialized = true
                    if (state.phase == TimerPhase.IDLE) {
                        val total = if (state.mode == TimerMode.COUNT_UP) {
                            0L
                        } else {
                            _selectedDurationMinutes.value * 60_000L
                        }
                        _timer.update {
                            it.copy(
                                selectedTaskId = state.selectedTaskId ?: list.first().id,
                                totalMillis = total,
                                elapsedMillis = 0L,
                            )
                        }
                    }
                }
                // 选中任务被删除时回退为自由专注（不自动绑其他任务）
                if (state.selectedTaskId != null && list.none { it.id == state.selectedTaskId }) {
                    _timer.update { it.copy(selectedTaskId = null, elapsedMillis = 0L) }
                }
            }
        }
        // 推送计时快照到前台服务通知
        viewModelScope.launch {
            timer.collect { state ->
                FocusTimerBus.snapshot.value = buildSnapshot(state)
            }
        }
        // 通知按钮命令回传执行
        viewModelScope.launch {
            FocusTimerBus.commands.collect { command ->
                when (command) {
                    FocusTimerBus.Command.PAUSE -> pauseFocus()
                    FocusTimerBus.Command.RESUME -> resumeFocus()
                    FocusTimerBus.Command.STOP -> cancelFocus()
                }
            }
        }
    }

    private fun buildSnapshot(state: TimerState): FocusTimerBus.Snapshot {
        val active = state.phase != TimerPhase.IDLE
        val taskName = if (state.inBreak) {
            "休息一下"
        } else {
            tasks.value.firstOrNull { it.id == state.selectedTaskId }?.name ?: "自由专注"
        }
        val millis = if (state.mode == TimerMode.COUNT_UP) state.elapsedMillis else state.remainingMillis
        val totalSeconds = (millis + 999) / 1000
        val clock = "${(totalSeconds / 60).toString().padStart(2, '0')}:" +
            (totalSeconds % 60).toString().padStart(2, '0')
        val modeLabel = when {
            state.inBreak -> "番茄计时 · 休息中"
            state.mode == TimerMode.COUNT_UP -> "正计时"
            state.mode == TimerMode.POMODORO -> "番茄计时 · 专注中"
            else -> "倒计时"
        }
        val prefix = if (state.mode == TimerMode.COUNT_UP) "已计时" else "剩余"
        return FocusTimerBus.Snapshot(
            active = active,
            paused = state.phase == TimerPhase.PAUSED,
            taskName = taskName,
            displayText = if (active) "$prefix $clock" else "",
            modeLabel = modeLabel,
        )
    }

    private fun syncService(active: Boolean) {
        val app = getApplication<Application>()
        val intent = Intent(app, FocusTimerService::class.java)
        if (active) {
            ContextCompat.startForegroundService(app, intent)
        } else {
            app.stopService(intent)
        }
    }

    /** 选择开始时长（仅待机态；倒计时/番茄生效并同步待机环时长） */
    fun selectDuration(minutes: Int) {
        if (_timer.value.phase != TimerPhase.IDLE) return
        val m = minutes.coerceIn(1, BackfillRules.MAX_MINUTES)
        userPickedDuration = true
        _selectedDurationMinutes.value = m
        if (_timer.value.mode != TimerMode.COUNT_UP) {
            _timer.update { it.copy(totalMillis = m * 60_000L, elapsedMillis = 0L) }
        }
    }

    /** 当前选中任务；null = 自由专注 */
    fun currentTask(): FocusTask? {
        val id = _timer.value.selectedTaskId ?: return null
        return tasks.value.firstOrNull { it.id == id }
    }

    /** 选择自由专注（不绑定任务，记录归未分类） */
    fun selectFreeFocus() {
        if (_timer.value.phase != TimerPhase.IDLE) return
        _timer.update { it.copy(selectedTaskId = null, elapsedMillis = 0L) }
    }

    fun selectTask(taskId: String) {
        if (_timer.value.phase != TimerPhase.IDLE) return
        if (tasks.value.none { it.id == taskId }) return
        _timer.update { it.copy(selectedTaskId = taskId, elapsedMillis = 0L) }
    }

    /** 切换计时模式（仅待机态可操作） */
    fun selectMode(mode: TimerMode) {
        if (_timer.value.phase != TimerPhase.IDLE) return
        _timer.update {
            it.copy(
                mode = mode,
                inBreak = false,
                round = 1,
                totalMillis = idleTotalMillis(mode),
                elapsedMillis = 0L,
            )
        }
    }

    /**
     * 待机态环形时长：
     * 正计时 0（无限累加）；番茄用设置里的专注段时长（不受倒计时时长选择影响）；倒计时用所选时长。
     */
    private fun idleTotalMillis(mode: TimerMode): Long = when (mode) {
        TimerMode.COUNT_UP -> 0L
        TimerMode.POMODORO -> pomodoroFocusMillis()
        TimerMode.COUNT_DOWN -> _selectedDurationMinutes.value * 60_000L
    }

    /** 番茄计时 · 专注段时长（分钟，设置可调，1-180） */
    fun pomodoroFocusMillis(): Long = settings.value.pomodoroFocusMinutes.coerceIn(1, 180) * 60_000L

    /** 番茄计时 · 短休息时长（分钟，1-60） */
    fun pomodoroBreakMillis(): Long = settings.value.pomodoroBreakMinutes.coerceIn(1, 60) * 60_000L

    /** 番茄计时 · 长休息时长（分钟，1-120） */
    fun pomodoroLongBreakMillis(): Long = settings.value.pomodoroLongBreakMinutes.coerceIn(1, 120) * 60_000L

    /** 番茄计时 · 每几轮进入一次长休息（至少 1 轮） */
    fun pomodoroRoundsPerCycle(): Int = settings.value.pomodoroRoundsBeforeLongBreak.coerceAtLeast(1)

    /** 开始专注：可不选任务（自由专注，归未分类）；时长用首页所选 */
    fun startFocus() {
        val state = _timer.value
        val total = idleTotalMillis(state.mode)
        _timer.value = TimerState(
            phase = TimerPhase.RUNNING,
            mode = state.mode,
            selectedTaskId = currentTask()?.id,
            totalMillis = total,
        )
        syncService(true)
        startTicker()
    }

    fun pauseFocus() {
        if (_timer.value.phase == TimerPhase.RUNNING) {
            _timer.update { it.copy(phase = TimerPhase.PAUSED) }
        }
    }

    fun resumeFocus() {
        if (_timer.value.phase == TimerPhase.PAUSED) {
            _timer.update { it.copy(phase = TimerPhase.RUNNING) }
        }
    }

    /** 完成：结束本次计时；满 1 分钟才计入记录并进入完成页（休息段不重复计） */
    fun finishEarly() {
        val state = _timer.value
        if (state.phase == TimerPhase.IDLE) return
        // 番茄休息段时专注段已自动入库，休息时长不计入
        val elapsedMinutes = if (state.inBreak) 0 else (state.elapsedMillis / 60_000L).toInt()
        ticker?.cancel()
        val taskId = state.selectedTaskId
        resetToIdle()
        if (elapsedMinutes >= 1) {
            completeRecord(taskId, elapsedMinutes)
        } else {
            // 不足 1 分钟不入库，但必须给出明确反馈，避免"点了没反应"
            Toast.makeText(
                getApplication(),
                "不足 1 分钟，本次未计入记录",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    /** 取消：丢弃本次计时数据，不写记录，回到待机 */
    fun cancelFocus() {
        if (_timer.value.phase == TimerPhase.IDLE) return
        ticker?.cancel()
        resetToIdle()
    }

    private fun resetToIdle() {
        val total = idleTotalMillis(_timer.value.mode)
        _timer.update {
            it.copy(
                phase = TimerPhase.IDLE,
                inBreak = false,
                round = 1,
                totalMillis = total,
                elapsedMillis = 0L,
            )
        }
        syncService(false)
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            var lastTick = SystemClock.elapsedRealtime()
            while (isActive) {
                delay(100)
                val now = SystemClock.elapsedRealtime()
                val delta = now - lastTick
                lastTick = now
                val state = _timer.value
                if (state.phase != TimerPhase.RUNNING) continue
                val newElapsed = state.elapsedMillis + delta
                when {
                    // 正计时：只累加；超 12 小时自动结算（防忘停挂一夜）
                    state.mode == TimerMode.COUNT_UP -> {
                        if (newElapsed >= COUNT_UP_AUTO_FINISH_MILLIS) {
                            resetToIdle()
                            completeRecord(state.selectedTaskId, (COUNT_UP_AUTO_FINISH_MILLIS / 60_000L).toInt())
                            break
                        }
                        _timer.update { it.copy(elapsedMillis = newElapsed) }
                    }
                    // 番茄·休息段：到点自动开下一轮（或回待机）
                    state.inBreak -> {
                        if (newElapsed >= state.totalMillis) {
                            if (settings.value.autoStart) {
                                _timer.update {
                                    it.copy(
                                        inBreak = false,
                                        round = state.round + 1,
                                        totalMillis = pomodoroFocusMillis(),
                                        elapsedMillis = 0L,
                                    )
                                }
                            } else {
                                resetToIdle()
                                break
                            }
                        } else {
                            _timer.update { it.copy(elapsedMillis = newElapsed) }
                        }
                    }
                    // 倒计时/番茄·专注段到点
                    newElapsed >= state.totalMillis -> {
                        val minutes = (state.totalMillis / 60_000L).toInt()
                        if (state.mode == TimerMode.POMODORO) {
                            // 番茄：本段入库+响铃后自动进入休息段，不打断循环
                            saveSegment(state.selectedTaskId, minutes)
                            if (settings.value.ringtoneEnabled) playRingtone()
                            // 轮数达到设定值给长休息，否则短休息
                            val needLongBreak = state.round % pomodoroRoundsPerCycle() == 0
                            _timer.update {
                                it.copy(
                                    inBreak = true,
                                    totalMillis = if (needLongBreak) {
                                        pomodoroLongBreakMillis()
                                    } else {
                                        pomodoroBreakMillis()
                                    },
                                    elapsedMillis = 0L,
                                )
                            }
                        } else {
                            resetToIdle()
                            completeRecord(state.selectedTaskId, minutes)
                            break
                        }
                    }
                    else -> _timer.update { it.copy(elapsedMillis = newElapsed) }
                }
            }
        }
    }

    /** 本次计时完成：入库 + 响铃 + 进完成页 */
    private fun completeRecord(taskId: String?, minutes: Int) {
        val record = newRecord(taskId, minutes)
        viewModelScope.launch { repo.addRecord(record) }
        if (settings.value.ringtoneEnabled) playRingtone()
        _completedRecord.value = record
    }

    /** 番茄专注段结束：只入库，不打断循环、不进完成页 */
    private fun saveSegment(taskId: String?, minutes: Int) {
        val record = newRecord(taskId, minutes)
        viewModelScope.launch { repo.addRecord(record) }
    }

    /**
     * 组装一条记录：taskId 关联任务（任务改名不断链），taskName 存展示快照；
     * 开始时间按 end - minutes 推算（暂停时长不计入，口径统一、可预期）。
     */
    private fun newRecord(taskId: String?, minutes: Int): FocusRecord {
        val end = System.currentTimeMillis()
        return FocusRecord(
            taskId = taskId,
            taskName = taskNameOf(taskId),
            minutes = minutes,
            startEpochMillis = end - minutes * 60_000L,
            endEpochMillis = end,
        )
    }

    /** 任务名快照；没选任务（自由专注）或任务已删时用默认名 */
    private fun taskNameOf(taskId: String?): String =
        taskId?.let { id -> tasks.value.firstOrNull { it.id == id }?.name } ?: FREE_FOCUS_NAME

    /** 完成页关闭：仅消费完成事件（「继续专注」按钮已按需求移除） */
    fun consumeCompleted() {
        _completedRecord.value = null
    }

    private fun playRingtone() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(getApplication(), uri)?.play()
        } catch (e: Exception) {
            // 个别 ROM 通知音播放会抛 SecurityException，失败仅静默降级为不响铃
            Log.w("AppViewModel", "playRingtone failed: ${e.message}")
        }
    }

    companion object {
        /** 倒计时默认时长（分钟）：45 分钟，不合适可在首页点「倒计时」随时重选 */
        const val DEFAULT_DURATION_MINUTES = 45
        /** 未选择任务时的记录名快照 */
        private const val FREE_FOCUS_NAME = "自由专注"
        /** 头像最长边（像素）：相册原图可能很大，落盘前统一缩到这个尺寸 */
        private const val AVATAR_MAX_PX = 512
        /** 正计时环形周期：30 分钟转一圈 */
        private const val COUNT_UP_RING_PERIOD_MILLIS = 30 * 60_000L
        /** 正计时自动结算兜底：超过 12 小时自动保存并回待机 */
        private const val COUNT_UP_AUTO_FINISH_MILLIS = 12 * 60 * 60_000L
        /** 月份热力图最多回看几个月（再早的记录也能翻到，但页数收敛） */
        private const val HEAT_MONTH_LIMIT = 12
        /** 「最强一天」取前几名（全量口径的成就类数据） */
        private const val TOP_DAYS_COUNT = 3

        /** 任务详情「专注记录」卡里预列的条数（完整列表去记录页看） */
        const val RECENT_RECORDS_PREVIEW = 10
    }

    // ---- 任务 CRUD ----
    /** 新建任务；parentId 为空 = 未分类（顶层），否则归档到该父任务（分类）下 */
    fun addTask(name: String, dateLabel: String, colorIndex: Int, iconIndex: Int, parentId: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { repo.addTask(trimmed, 25, dateLabel, colorIndex, iconIndex, parentId) }
    }

    /** 顶层任务（未分类/分类候选） */
    fun topLevelTasks(): List<FocusTask> = tasks.value.filter { it.parentId == null }

    /** 某个任务下的小任务 */
    fun childrenOf(taskId: String): List<FocusTask> = tasks.value.filter { it.parentId == taskId }

    /** 某任务的父任务（分类） */
    fun parentOf(task: FocusTask?): FocusTask? =
        task?.parentId?.let { id -> tasks.value.firstOrNull { it.id == id } }

    /**
     * 补录：手动补一条记录。
     *
     * 区间由调用方用 [BackfillRules.resolve] 按「结束时刻锚定 + 时长」算好后传入
     * （跨天溢出放行，不在这里再做钳制）；[task] 为 null 表示自由专注（未分类）。
     * 来源固定写 [RecordSource.MANUAL]——这是"只有它允许改时长"的依据。
     *
     * 入库成功后写 [lastAddedRecord]：界面据此弹 6 秒撤销条。
     * 放在 ViewModel 而不是页面里，是因为补录弹窗关掉后由**宿主的页面**接着弹条，
     * 状态不该跟着弹窗一起消失。
     */
    fun addManualRecord(task: FocusTask?, startEpochMillis: Long, endEpochMillis: Long) {
        if (endEpochMillis <= startEpochMillis) return
        val minutes = ((endEpochMillis - startEpochMillis) / 60_000L).toInt()
        if (minutes < 1) return
        viewModelScope.launch {
            val record = FocusRecord(
                taskId = task?.id,
                taskName = task?.name ?: FREE_FOCUS_NAME,
                minutes = minutes,
                startEpochMillis = startEpochMillis,
                endEpochMillis = endEpochMillis,
                source = RecordSource.MANUAL.name,
            )
            _lastAddedRecord.value = record.copy(id = repo.addRecord(record))
        }
    }

    /** 刚补录进来、还在撤销窗口里的记录（null = 没有） */
    private val _lastAddedRecord = MutableStateFlow<FocusRecord?>(null)
    val lastAddedRecord: StateFlow<FocusRecord?> = _lastAddedRecord.asStateFlow()

    /** 撤销条消失 / 已撤销时清掉 */
    fun clearLastAddedRecord() {
        _lastAddedRecord.value = null
    }

    /** 删除一条记录（撤销时用 [restoreRecord] 原样插回） */
    fun deleteRecord(id: Long) {
        viewModelScope.launch { repo.deleteRecord(id) }
    }

    /** 撤销删除：带着原 id 插回，位置与内容都不变 */
    fun restoreRecord(record: FocusRecord) {
        viewModelScope.launch { repo.addRecord(record) }
    }

    /**
     * 改手动补录记录的时长：**结束时刻不变**，开始时刻按新时长回退。
     * 真实计时的记录直接忽略（[BackfillRules.canEditDuration] 为 false）。
     */
    fun updateRecordDuration(record: FocusRecord, minutes: Int) {
        val updated = BackfillRules.withDuration(record, minutes) ?: return
        viewModelScope.launch { repo.updateRecord(updated) }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch { repo.deleteTask(taskId) }
    }

    /** 首页「＋新建任务」请求：置位后由任务清单页消费并直接打开编辑器 */
    private val _newTaskRequest = MutableStateFlow(false)
    val newTaskRequest: StateFlow<Boolean> = _newTaskRequest.asStateFlow()

    fun requestNewTask() {
        _newTaskRequest.value = true
    }

    fun consumeNewTaskRequest() {
        _newTaskRequest.value = false
    }

    fun updateSettings(newSettings: AppSettings) {
        viewModelScope.launch { repo.updateSettings(newSettings) }
    }

    // ---- 备份 / 恢复 ----

    /** 恢复结果：分开报错，界面才能说清是"不是备份文件"还是"来自更新的版本" */
    enum class RestoreResult { OK, NOT_A_BACKUP, TOO_NEW, FAILED }

    /** 导出全部任务、记录与设置到用户选定的文件（SAF）；返回是否成功 */
    suspend fun exportBackupTo(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val json = repo.exportBackup()
            val stream = getApplication<Application>().contentResolver.openOutputStream(uri)
            if (stream == null) {
                false
            } else {
                stream.use { out -> out.write(json.toByteArray(Charsets.UTF_8)) }
                true
            }
        }.getOrDefault(false)
    }

    /**
     * 从备份文件整体覆盖当前数据。
     *
     * 顺序是刻意的：**先校验 → 再留回滚点 → 最后才覆盖**。
     * 校验不过时连回滚点都不必产生；覆盖中途失败时库里的数据也没被清掉。
     */
    suspend fun restoreBackupFrom(uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = getApplication<Application>().contentResolver.openInputStream(uri)
                ?.use { input -> input.readBytes() }
                ?: return@withContext RestoreResult.FAILED
            val raw = bytes.toString(Charsets.UTF_8)
            when (repo.checkBackup(raw)) {
                BackupFormat.Check.NOT_A_BACKUP ->
                    return@withContext RestoreResult.NOT_A_BACKUP

                BackupFormat.Check.TOO_NEW ->
                    return@withContext RestoreResult.TOO_NEW

                BackupFormat.Check.OK -> Unit
            }
            repo.saveRollback()
            if (repo.importBackup(raw)) RestoreResult.OK else RestoreResult.FAILED
        }.getOrDefault(RestoreResult.FAILED)
    }

    /** 本机自动回滚点（时间倒序） */
    suspend fun rollbackPoints(): List<FlowTimeRepository.Rollback> =
        withContext(Dispatchers.IO) { repo.rollbacks() }

    /** 用某个回滚点恢复；恢复前再留一份底，于是"这次恢复"本身也能被撤销 */
    suspend fun restoreFromRollback(rollback: FlowTimeRepository.Rollback): Boolean =
        withContext(Dispatchers.IO) {
            repo.saveRollback()
            repo.restoreRollback(rollback)
        }

    // ---- 个人资料：头像 + 昵称 ----
    /** 头像文件（未设置或文件已丢失时返回 null） */
    fun avatarFile(): File? {
        val name = settings.value.avatarPath
        if (name.isBlank()) return null
        val app = getApplication<Application>()
        return File(app.filesDir, name).takeIf { it.exists() }
    }

    /** 通知是否可用（Android 13+ 需运行时授权，关掉的话提醒不会响） */
    fun notificationsEnabled(): Boolean = FocusReminder.notificationsEnabled(getApplication())

    /** 精确闹钟是否可用（Android 12+ 需在系统设置允许「闹钟和提醒」） */
    fun exactAlarmAllowed(): Boolean = FocusReminder.canScheduleExact(getApplication())

    /**
     * 相册选中的图片设为头像：缩到 [AVATAR_MAX_PX] 内再写入应用私有目录（不需要存储权限）。
     * 文件名带时间戳，换头像后图片组件会重新解码；旧文件顺手删除。
     */
    fun updateAvatar(uri: Uri) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            val fileName = withContext(Dispatchers.IO) {
                runCatching {
                    // 先只读尺寸算出采样率，避免原图整张先进内存
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input, null, bounds)
                    }
                    val options = BitmapFactory.Options().apply {
                        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, AVATAR_MAX_PX)
                    }
                    val source = app.contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input, null, options)
                    } ?: return@runCatching null
                    val scaled = scaleDown(source, AVATAR_MAX_PX)
                    val name = "avatar_${System.currentTimeMillis()}.jpg"
                    File(app.filesDir, name).outputStream().use { out ->
                        scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
                    }
                    if (scaled !== source) source.recycle()
                    name
                }.getOrNull()
            }
            if (fileName == null) {
                Toast.makeText(app, "这张图片读不出来，换一张试试", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val previous = settings.value.avatarPath
            repo.updateSettings(settings.value.copy(avatarPath = fileName))
            if (previous.isNotBlank()) {
                withContext(Dispatchers.IO) { runCatching { File(app.filesDir, previous).delete() } }
            }
        }
    }

    /** 移除头像，恢复默认图标 */
    fun clearAvatar() {
        val app = getApplication<Application>()
        val previous = settings.value.avatarPath
        viewModelScope.launch {
            repo.updateSettings(settings.value.copy(avatarPath = ""))
            if (previous.isNotBlank()) {
                withContext(Dispatchers.IO) { runCatching { File(app.filesDir, previous).delete() } }
            }
        }
    }

    /** 解码采样率：取不超过目标尺寸的最大 2 的幂（1/2/4/8…） */
    private fun sampleSizeFor(width: Int, height: Int, target: Int): Int {
        var sample = 1
        var longest = maxOf(width, height)
        while (longest / 2 >= target) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    /** 等比缩放到最长边不超过 maxPx（已够小则原样返回） */
    private fun scaleDown(source: Bitmap, maxPx: Int): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= maxPx) return source
        val ratio = maxPx.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    // ---- 统计 ----
    data class DayMinutes(val label: String, val minutes: Int, val isToday: Boolean)

    data class StatsState(
        val totalMinutes: Int = 0,
        val todayMinutes: Int = 0,
        /** 当前连续天数：今天还没记录时从昨天起算，避免白天被清零 */
        val streakDays: Int = 0,
        /** 历史最长连续天数（比单看当前连续更有参照意义） */
        val bestStreakDays: Int = 0,
        /** 近 7 天里达到「每日目标」的天数（0-7），比"断了就归零"更宽容 */
        val goalHitDaysInWeek: Int = 0,
        val week: List<DayMinutes> = emptyList(),
        val goalMinutes: Int = 120,
    )

    val stats: StateFlow<StatsState> = combine(records, settings) { recs, s ->
        computeStats(recs, s.dailyGoalMinutes)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, StatsState())

    private fun computeStats(recs: List<FocusRecord>, goalMinutes: Int): StatsState {
        val zone = ZoneId.systemDefault()
        val byDay = recs
            .groupBy { Instant.ofEpochMilli(it.endEpochMillis).atZone(zone).toLocalDate() }
            .mapValues { entry -> entry.value.sumOf { it.minutes } }
        val today = LocalDate.now(zone)
        // 连续天数：今天暂无记录时从昨天起算，避免白天 streak 被清零
        var cursor = if (byDay.containsKey(today)) today else today.minusDays(1)
        var streak = 0
        while (byDay.containsKey(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        // 历史最长连续：按日期排序后扫一遍，相邻两天则累加
        var best = 0
        var run = 0
        var previous: LocalDate? = null
        byDay.keys.sorted().forEach { day ->
            run = if (previous != null && day == previous!!.plusDays(1)) run + 1 else 1
            if (run > best) best = run
            previous = day
        }
        // 近 7 天达成「每日目标」的天数（目标为 0 时不算达成，避免除零口径混乱）
        val goalHitDays = if (goalMinutes <= 0) {
            0
        } else {
            (0..6).count { offset -> (byDay[today.minusDays(offset.toLong())] ?: 0) >= goalMinutes }
        }
        val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
        val week = (6 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            DayMinutes(
                label = weekLabels[day.dayOfWeek.value - 1],
                minutes = byDay[day] ?: 0,
                isToday = offset == 0,
            )
        }
        return StatsState(
            totalMinutes = recs.sumOf { it.minutes },
            todayMinutes = byDay[today] ?: 0,
            streakDays = streak,
            bestStreakDays = maxOf(best, streak),
            goalHitDaysInWeek = goalHitDays,
            week = week,
            goalMinutes = goalMinutes,
        )
    }

    // ---- 范围统计（本周 / 本月 / 全部）----
    // StatsRange 已移到 data 层（StatsRules.kt）——它是统计口径的一部分，不属于 UI

    /** 热力图一格：当天分钟数 + 是否落在所选范围内（范围外淡显，未来空白） */
    data class HeatCell(val date: LocalDate, val minutes: Int, val inRange: Boolean)

    /** 一个月的热力图一页（可左右滑动看更早的月份） */
    data class HeatMonth(
        val year: Int,
        val month: Int,
        val cells: List<HeatCell>,
        val activeDays: Int,
        val totalMinutes: Int,
    ) {
        val label: String get() = "%d 年 %d 月".format(year, month)
        val firstDay: LocalDate get() = LocalDate.of(year, month, 1)
    }

    /** 所选范围下一次性聚合结果（概览 / 时段分布 / 热力图 / 分布 / 对比） */
    data class RangeStats(
        val range: StatsRange = StatsRange.WEEK,
        val title: String = "本周概览",
        val totalMinutes: Int = 0,
        /** 日均；范围内已过天数不足时为 null，界面显示「—」 */
        val dailyAverageMinutes: Int? = null,
        val sessionCount: Int = 0,
        val longestSessionMinutes: Int = 0,
        val activeDays: Int = 0,
        val hourMinutes: List<Int> = List(24) { 0 },
        /** 按天柱状数据：本周 = 周一~周日，本月 = 1 号~月末，全部范围为空（走按周分页） */
        val dailyDays: List<DayMinutes> = emptyList(),
        /** 可左右滑动的月份热力图，由早到晚（最后一页 = 当月） */
        val heatMonths: List<HeatMonth> = emptyList(),
        val taskTotals: List<TaskTotal> = emptyList(),
        val categoryTotals: List<CategoryTotal> = emptyList(),
        /** 上一个同长度区间的累计分钟（本周→上周 / 本月→上月）；「全部」档为 null */
        val previousMinutes: Int? = null,
        val previousLabel: String? = null,
        /** 与上一区间相比的涨跌百分比；上一区间为 0 或没有上一区间时为 null */
        val changePercent: Int? = null,
        /** 单次时长分布（跟范围） */
        val durationBuckets: List<StatsRules.DurationBucket> = emptyList(),
        /** 历史最强一天 Top3（**全量**，口径原则里的"成就/里程碑类"） */
        val topDays: List<StatsRules.TopDay> = emptyList(),
    )

    /** 切换统计范围并写回设置（退出应用后保持） */
    fun updateStatsRange(range: StatsRange) {
        if (_statsRange.value == range) return
        _statsRange.value = range
        viewModelScope.launch { repo.updateSettings(settings.value.copy(statsRange = range.name)) }
    }

    /** 范围统计：记录/任务/范围任一变化时重算一次（O(n)），UI 只订阅结果 */
    val rangeStats: StateFlow<RangeStats> =
        combine(records, tasks, _statsRange) { recs, taskList, range ->
            computeRangeStats(recs, taskList, range)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, RangeStats())

    private fun computeRangeStats(
        recs: List<FocusRecord>,
        taskList: List<FocusTask>,
        range: StatsRange,
    ): RangeStats {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val dayOf: (Long) -> LocalDate = { millis ->
            Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        }

        // 范围起点：本周从周一起算（与 buildWeekPages 口径一致），本月从 1 号起算，全部不限
        val rangeStart: LocalDate? = when (range) {
            StatsRange.WEEK -> today.with(DayOfWeek.MONDAY)
            StatsRange.MONTH -> today.withDayOfMonth(1)
            StatsRange.ALL -> null
        }
        val inScope = if (rangeStart == null) {
            recs
        } else {
            recs.filter { !dayOf(it.endEpochMillis).isBefore(rangeStart) }
        }
        val scopeByDay = inScope
            .groupBy { dayOf(it.endEpochMillis) }
            .mapValues { (_, list) -> list.sumOf { it.minutes } }
        val scopeMinutes = inScope.sumOf { it.minutes }

        // 日均：分母 = 范围内已过的自然日；不足 2 天时给 null（否则周一算出来就是"当天总量"）
        val averageStart = rangeStart ?: recs.minOfOrNull { dayOf(it.endEpochMillis) } ?: today
        val elapsedDays = (ChronoUnit.DAYS.between(averageStart, today) + 1).coerceAtLeast(1).toInt()
        val dailyAverage = StatsRules.dailyAverage(scopeMinutes, elapsedDays)

        // 时段分布：按分钟摊到跨越的每个小时。
        // 旧实现把整段分钟挂在**结束小时**上，一条 8 小时记录全堆在一根柱子里，跨天时更离谱。
        val hourMinutes = StatsRules.hourMinutesOf(inScope, zone)

        // 全量按天（热力图与「最强一天」共用；这两者按口径原则都属于成就/里程碑类 → 全量）
        val allByDay = recs
            .groupBy { dayOf(it.endEpochMillis) }
            .mapValues { (_, list) -> list.sumOf { it.minutes } }

        // 月份热力图页：从最早一条记录所在月一直到当月，最多回看 12 个月
        val heatMonths = buildHeatMonths(allByDay, today, rangeStart)

        // 上一个同长度区间（本周→上周 / 本月→上月）；「全部」档没有可比区间
        val previousWindow = StatsRules.previousWindow(range, today)
        val previousMinutes = previousWindow?.let { window ->
            recs.filter { dayOf(it.endEpochMillis) in window }.sumOf { it.minutes }
        }

        // 记录 → 归属：优先 taskId，旧记录按任务名找回
        val index = RecordLink.TaskIndex.of(taskList)

        // 范围内任务分布：按归属聚合（任务改名不断链），显示名用任务当前名字
        val taskTotals = inScope
            .groupBy { RecordLink.keyOf(it) }
            .map { (key, list) ->
                TaskTotal(index.byId[key]?.name ?: list.first().taskName, list.sumOf { it.minutes })
            }
            .sortedByDescending { it.minutes }

        // 范围内分类分布（归属任务的父任务 → 大类；没有父任务的算「未分类」）
        val categoryTotals = inScope
            .groupBy { index.categoryIdOf(it) }
            .map { (catId, list) ->
                CategoryTotal(
                    categoryId = catId,
                    name = catId?.let { index.byId[it]?.name } ?: "未分类",
                    minutes = list.sumOf { it.minutes },
                    // 按归属键去重，避免同一任务改名后被算成两个任务
                    taskCount = list.map { RecordLink.keyOf(it) }.distinct().size,
                )
            }
            .sortedByDescending { it.minutes }

        val dailyDays = when (range) {
            StatsRange.ALL -> emptyList()
            StatsRange.WEEK -> (0..6).map { offset ->
                val day = today.with(DayOfWeek.MONDAY).plusDays(offset.toLong())
                DayMinutes(dayLabel(day), scopeByDay[day] ?: 0, day == today)
            }

            StatsRange.MONTH -> (1..today.lengthOfMonth()).map { dayOfMonth ->
                val day = today.withDayOfMonth(dayOfMonth)
                DayMinutes(dayLabel(day), scopeByDay[day] ?: 0, day == today)
            }
        }

        return RangeStats(
            range = range,
            title = when (range) {
                StatsRange.WEEK -> "本周概览"
                StatsRange.MONTH -> "本月概览"
                StatsRange.ALL -> "全部概览"
            },
            totalMinutes = scopeMinutes,
            dailyAverageMinutes = dailyAverage,
            sessionCount = inScope.size,
            longestSessionMinutes = inScope.maxOfOrNull { it.minutes } ?: 0,
            activeDays = scopeByDay.size,
            hourMinutes = hourMinutes,
            dailyDays = dailyDays,
            heatMonths = heatMonths,
            taskTotals = taskTotals,
            categoryTotals = categoryTotals,
            previousMinutes = previousMinutes,
            previousLabel = when (range) {
                StatsRange.WEEK -> "上周"
                StatsRange.MONTH -> "上月"
                StatsRange.ALL -> null
            },
            changePercent = previousMinutes?.let { StatsRules.changePercent(scopeMinutes, it) },
            durationBuckets = StatsRules.durationBuckets(inScope),
            topDays = StatsRules.topDays(allByDay, TOP_DAYS_COUNT),
        )
    }

    /**
     * 月份热力图页：由早到晚排（最后一页 = 当月），最多回看 [HEAT_MONTH_LIMIT] 个月。
     * 日期格子用全量记录（保持"坚持情况"可读），范围外的日子靠 [HeatCell.inRange] 淡显。
     */
    private fun buildHeatMonths(
        allByDay: Map<LocalDate, Int>,
        today: LocalDate,
        rangeStart: LocalDate?,
    ): List<AppViewModel.HeatMonth> {
        val currentMonth = today.withDayOfMonth(1)
        val earliestMonth = allByDay.keys.minOrNull()?.withDayOfMonth(1) ?: currentMonth
        val pages = mutableListOf<HeatMonth>()
        var cursor = currentMonth
        while (!cursor.isBefore(earliestMonth) && pages.size < HEAT_MONTH_LIMIT) {
            val cells = (1..cursor.lengthOfMonth()).map { dayOfMonth ->
                val day = cursor.withDayOfMonth(dayOfMonth)
                HeatCell(
                    date = day,
                    minutes = allByDay[day] ?: 0,
                    inRange = !day.isAfter(today) && (rangeStart == null || !day.isBefore(rangeStart)),
                )
            }
            val inRangeCells = cells.filter { it.inRange }
            pages.add(
                HeatMonth(
                    year = cursor.year,
                    month = cursor.monthValue,
                    cells = cells,
                    activeDays = inRangeCells.count { it.minutes > 0 },
                    totalMinutes = inRangeCells.sumOf { it.minutes },
                ),
            )
            cursor = cursor.minusMonths(1)
        }
        return pages.asReversed()
    }

    /**
     * 导出用：按所选范围过滤记录。
     * 口径与统计页一致（按 endEpochMillis 取日），避免"统计看本周、导出却是全部"。
     */
    fun recordsInRange(range: StatsRange): List<FocusRecord> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = when (range) {
            StatsRange.WEEK -> today.with(DayOfWeek.MONDAY)
            StatsRange.MONTH -> today.withDayOfMonth(1)
            StatsRange.ALL -> return records.value
        }
        return records.value.filter {
            !Instant.ofEpochMilli(it.endEpochMillis).atZone(zone).toLocalDate().isBefore(start)
        }
    }

    /** 按天柱状的横轴标签：M/d */
    private fun dayLabel(day: LocalDate): String = "${day.monthValue}/${day.dayOfMonth}"

    // ---- 任务维度统计 ----
    /** 一周分页：index 0 = 本周，越往后越早 */
    data class WeekPage(
        val weekStart: LocalDate,
        val label: String,
        val days: List<DayMinutes>,
        val totalMinutes: Int,
    )

    /** 全部记录按周分页构建器（调用方先过滤记录）；无记录时至少返回本周一页 */
    fun buildWeekPages(records: List<FocusRecord>): List<WeekPage> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val currentWeekStart = today.with(DayOfWeek.MONDAY)
        val byDay = records
            .groupBy { Instant.ofEpochMilli(it.endEpochMillis).atZone(zone).toLocalDate() }
            .mapValues { entry -> entry.value.sumOf { it.minutes } }
        val earliestWeekStart = (byDay.keys.minOrNull() ?: today).with(DayOfWeek.MONDAY)
        val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
        val pages = mutableListOf<WeekPage>()
        var cursor = currentWeekStart
        while (!cursor.isBefore(earliestWeekStart)) {
            val weekEnd = cursor.plusDays(6)
            val days = (0..6).map { offset ->
                val day = cursor.plusDays(offset.toLong())
                DayMinutes(
                    label = weekLabels[offset],
                    minutes = byDay[day] ?: 0,
                    isToday = day == today,
                )
            }
            val label = if (cursor == currentWeekStart) {
                "本周"
            } else {
                "%02d.%02d-%02d.%02d".format(cursor.monthValue, cursor.dayOfMonth, weekEnd.monthValue, weekEnd.dayOfMonth)
            }
            pages.add(WeekPage(cursor, label, days, days.sumOf { it.minutes }))
            cursor = cursor.minusWeeks(1)
        }
        return pages
    }

    /** 大类聚合项（范围统计用）：含「未分类」，按分钟降序 */
    data class CategoryTotal(val categoryId: String?, val name: String, val minutes: Int, val taskCount: Int)

    /** 任务聚合项（范围统计用），按时长降序 */
    data class TaskTotal(val name: String, val minutes: Int)

    data class TaskStats(
        val totalMinutes: Int,
        val sessionCount: Int,
        val lastFocusMillis: Long?,
        val recent: List<FocusRecord>,
    )

    /**
     * 单任务统计（任务详情页）：**含它的直接子任务**（口径与统计页「分类分布」一致）；
     * 记录优先按 taskId 匹配，旧记录退回任务名；图表分页用 buildWeekPages。
     *
     * [recent] 只取最近 [RECENT_RECORDS_PREVIEW] 条 —— 卡片里那是"最近记录"的预览，
     * 完整列表走右上角「查看更多记录」。
     */
    fun taskStats(
        task: FocusTask,
        allRecords: List<FocusRecord>,
        allTasks: List<FocusTask>,
    ): TaskStats {
        val belongs = RecordLink.treePredicate(task, allTasks)
        val mine = allRecords.filter(belongs)
        return TaskStats(
            totalMinutes = mine.sumOf { it.minutes },
            sessionCount = mine.size,
            lastFocusMillis = mine.maxOfOrNull { it.endEpochMillis },
            recent = mine.sortedByDescending { it.endEpochMillis }.take(RECENT_RECORDS_PREVIEW),
        )
    }

}
