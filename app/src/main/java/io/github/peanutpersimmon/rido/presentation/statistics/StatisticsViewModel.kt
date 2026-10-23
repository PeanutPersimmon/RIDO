package io.github.peanutpersimmon.rido.presentation.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.peanutpersimmon.rido.domain.model.DomainResult
import io.github.peanutpersimmon.rido.domain.service.StatisticsGranularity
import io.github.peanutpersimmon.rido.domain.service.StatisticsPeriod
import io.github.peanutpersimmon.rido.domain.usecase.GetStatisticsUseCase
import io.github.peanutpersimmon.rido.domain.usecase.StatisticsSources
import io.github.peanutpersimmon.rido.domain.usecase.ObserveTimelineDataChangesUseCase
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class StatisticsSection(val label: String) { CATEGORY("分类"), TAG("标签"), SLEEP("睡眠") }
data class StatisticsSelection(
    val section: StatisticsSection = StatisticsSection.CATEGORY,
    val period: StatisticsPeriod = StatisticsPeriod(StatisticsGranularity.MONTH, LocalDate.now()),
    val categoryDetailId: Long? = null,
    val tagDetailId: Long? = null,
    val sleepMonth: YearMonth = YearMonth.now(),
) {
    fun detailId(section: StatisticsSection): Long? = when (section) {
        StatisticsSection.CATEGORY -> categoryDetailId
        StatisticsSection.TAG -> tagDetailId
        StatisticsSection.SLEEP -> null
    }
}
sealed interface StatisticsUiState {
    data object Loading : StatisticsUiState
    data class Error(val message: String) : StatisticsUiState
    data class Ready(val selection: StatisticsSelection, val sources: StatisticsSources) : StatisticsUiState
}

class StatisticsViewModel(
    observeChanges: ObserveTimelineDataChangesUseCase,
    private val getStatistics: GetStatisticsUseCase,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val selection = MutableStateFlow(StatisticsSelection())
    private val statisticsSources = combine(
        observeChanges().onStart { emit(Unit) },
        selection
            .map { it.period to it.sleepMonth }
            .distinctUntilChanged(),
    ) { _, (period, sleepMonth) ->
        getStatistics(period, sleepMonth, zone, System.currentTimeMillis())
    }

    val uiState = combine(selection, statisticsSources) { selected, result ->
        when (result) {
            is DomainResult.Success -> StatisticsUiState.Ready(selected, result.value)
            is DomainResult.Failure -> StatisticsUiState.Error(result.error.toString())
        }
    }.catch { emit(StatisticsUiState.Error(it.message ?: "统计数据加载失败")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState.Loading)

    fun selectSection(section: StatisticsSection) = selection.update { it.copy(section = section) }
    fun selectGranularity(granularity: StatisticsGranularity) = selection.update {
        it.copy(period = it.period.copy(granularity = granularity))
    }
    fun shiftPeriod(direction: Long) = selection.update { it.copy(period = it.period.shifted(direction)) }
    fun selectDate(date: LocalDate) = selection.update { it.copy(period = it.period.copy(anchor = date)) }
    fun openDetail(section: StatisticsSection, id: Long?) = selection.update {
        when (section) {
            StatisticsSection.CATEGORY -> it.copy(categoryDetailId = id)
            StatisticsSection.TAG -> it.copy(tagDetailId = id)
            StatisticsSection.SLEEP -> it
        }
    }
    fun closeDetail(section: StatisticsSection) = openDetail(section, null)
    fun shiftSleepMonth(direction: Long) = selection.update { it.copy(sleepMonth = it.sleepMonth.plusMonths(direction)) }

    companion object {
        fun factory(observeChanges: ObserveTimelineDataChangesUseCase, getStatistics: GetStatisticsUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    StatisticsViewModel(observeChanges, getStatistics) as T
            }
    }
}