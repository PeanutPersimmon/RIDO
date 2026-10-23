package io.github.peanutpersimmon.rido.presentation.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.peanutpersimmon.rido.data.preferences.PlanViewOrderPreferences
import io.github.peanutpersimmon.rido.data.repository.RoomRidoRepository
import io.github.peanutpersimmon.rido.domain.model.AwakeDisplayWindow
import io.github.peanutpersimmon.rido.domain.model.DomainResult
import io.github.peanutpersimmon.rido.domain.model.Event
import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.model.TaskStatus
import io.github.peanutpersimmon.rido.domain.service.TaskTemporalPolicy
import io.github.peanutpersimmon.rido.domain.usecase.CompleteTaskUseCase
import io.github.peanutpersimmon.rido.domain.usecase.ReopenTaskUseCase
import io.github.peanutpersimmon.rido.presentation.today.DefaultAwakeTimeConfig
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The active view and date are transient UI state; only the view order is persisted (DataStore). */
enum class PlanViewMode(val label: String) {
    DAY("日视图"), WEEK("周视图"), MONTH("月视图"), BOARD("2×4 周计划"),
    TODO_DONE("Todo & Done"), TASK_LIST("任务列表")
}

enum class MonthContent { PLAN, RECORD }

data class PlanUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val mode: PlanViewMode = PlanViewMode.BOARD,
    val monthContent: MonthContent = MonthContent.PLAN,
    val tasks: List<Task> = emptyList(),
    val events: List<Event> = emptyList(),
    val records: List<Session> = emptyList(),
    val categoryColorKeys: Map<Long, String> = emptyMap(),
    val loading: Boolean = true,
    val error: String? = null,
    val nowMillis: Long = System.currentTimeMillis(),
    val awakeDisplayWindow: AwakeDisplayWindow? = null,
)

internal fun planTasksForView(
    view: PlanViewMode,
    content: MonthContent,
    tasks: List<Task>,
): List<Task> {
    val showsTasks = view == PlanViewMode.DAY || view == PlanViewMode.WEEK ||
        view == PlanViewMode.BOARD || view == PlanViewMode.TODO_DONE ||
        view == PlanViewMode.TASK_LIST || view == PlanViewMode.MONTH && content == MonthContent.PLAN
    return if (showsTasks) tasks.filter(TaskTemporalPolicy::isVisibleInPlan) else emptyList()
}

class PlanViewModel(
    private val repository: RoomRidoRepository,
    private val viewOrderPreferences: PlanViewOrderPreferences,
    private val defaultAwakeTime: DefaultAwakeTimeConfig = DefaultAwakeTimeConfig.Standard,
) : ViewModel() {
    private val completeTask = CompleteTaskUseCase(repository)
    private val reopenTask = ReopenTaskUseCase(repository)
    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val monthContent = MutableStateFlow(MonthContent.PLAN)
    private val pageVisible = MutableStateFlow(false)
    private val zone = ZoneId.systemDefault()
    private val sourceQueries = RoomPlanSourceQueries(repository)

    /** 用户自定义的视图顺序；首项即计划页冷启动默认视图。 */
    val viewOrder: StateFlow<List<PlanViewMode>> = viewOrderPreferences.orderNames
        .map { names ->
            val parsed = names.mapNotNull { name -> PlanViewMode.entries.firstOrNull { it.name == name } }
            parsed + PlanViewMode.entries.filter { it !in parsed }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_VIEW_ORDER)

    /** 非空表示本次会话用户手动切换过视图；为空表示跟随顺序首项。 */
    private val explicitMode = MutableStateFlow<PlanViewMode?>(null)

    private val mode: StateFlow<PlanViewMode> = combine(explicitMode, viewOrder) { explicit, order ->
        explicit ?: order.firstOrNull() ?: PlanViewMode.BOARD
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PlanViewMode.BOARD)

    init {
        // 无排序数据时写回默认序列；默认序列以 BOARD 开头，与回退值一致。
        viewModelScope.launch {
            viewOrderPreferences.ensureDefaultOrder(DEFAULT_VIEW_ORDER.map { it.name })
        }
        // 用户在设置中改序后，放弃本次会话的手动选择，让新的首项立即生效。
        // StateFlow 已连续去重，无需 distinctUntilChanged。
        viewModelScope.launch {
            viewOrder.drop(1).collect { explicitMode.value = null }
        }
    }

    private val context = combine(selectedDate, mode, monthContent, ::PlanContext)

    val uiState = planUiStates(
        dataChanges = repository.observeDataChanges(),
        contexts = context,
        pageVisible = pageVisible,
        loadSources = { key -> loadPlanStableSources(sourceQueries, key, zone) },
        timeProvider = io.github.peanutpersimmon.rido.presentation.time.SystemCurrentTimeProvider,
        zoneId = zone,
        defaultWakeMinute = defaultAwakeTime.wakeMinute,
        defaultSleepMinute = defaultAwakeTime.sleepMinute,
        ticker = { periodMillis ->
            io.github.peanutpersimmon.rido.presentation.time.alignedTicks(
                io.github.peanutpersimmon.rido.presentation.time.SystemCurrentTimeProvider,
                periodMillis,
            )
        },
    ).catch { error ->
        emit(PlanUiState(selectedDate.value, mode.value, monthContent.value, loading = false,
            error = error.message ?: "无法读取计划数据"))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    fun toggleTaskCompletion(task: Task, onSuccess: () -> Unit = {}) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val result = if (task.status == TaskStatus.DONE) reopenTask(task.id, now)
        else completeTask(task.id, now)
        if (result is DomainResult.Success) onSuccess()
    }

    fun selectMode(value: PlanViewMode) { explicitMode.value = value }

    fun setPageVisible(visible: Boolean) {
        pageVisible.value = visible
    }

    /** Persists one complete, validated order after a drag/drop or accessibility move. */
    fun setViewOrder(order: List<PlanViewMode>) {
        if (order.size != PlanViewMode.entries.size ||
            order.toSet() != PlanViewMode.entries.toSet() ||
            order == viewOrder.value
        ) return
        viewModelScope.launch { viewOrderPreferences.setOrderNames(order.map { it.name }) }
    }
    fun selectDate(value: LocalDate) { selectedDate.value = value }
    fun selectMonth(value: YearMonth) {
        selectedDate.value = value.atDay(selectedDate.value.dayOfMonth.coerceAtMost(value.lengthOfMonth()))
    }
    fun selectMonthContent(value: MonthContent) { monthContent.value = value }
    fun step(direction: Long) {
        selectedDate.value = when (mode.value) {
            PlanViewMode.DAY, PlanViewMode.TODO_DONE -> selectedDate.value.plusDays(direction)
            PlanViewMode.WEEK, PlanViewMode.BOARD -> selectedDate.value.plusWeeks(direction)
            PlanViewMode.MONTH -> selectedDate.value.plusMonths(direction)
            PlanViewMode.TASK_LIST -> selectedDate.value
        }
    }

    companion object {
        /** 无持久化数据时的默认顺序：以 BOARD 开头，与旧硬编码行为一致。 */
        val DEFAULT_VIEW_ORDER: List<PlanViewMode> = listOf(
            PlanViewMode.BOARD, PlanViewMode.DAY, PlanViewMode.WEEK,
            PlanViewMode.MONTH, PlanViewMode.TODO_DONE, PlanViewMode.TASK_LIST,
        )

        fun factory(repository: RoomRidoRepository,
            viewOrderPreferences: PlanViewOrderPreferences): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PlanViewModel(repository, viewOrderPreferences) as T
            }
    }
}
