package io.github.peanutpersimmon.rido.ui.feature.statistics

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peanutpersimmon.rido.domain.service.StatisticsGranularity
import io.github.peanutpersimmon.rido.presentation.statistics.StatisticsSection
import io.github.peanutpersimmon.rido.presentation.statistics.StatisticsUiState
import io.github.peanutpersimmon.rido.presentation.statistics.StatisticsViewModel
import io.github.peanutpersimmon.rido.ui.components.CompactPrimaryNavigation
import io.github.peanutpersimmon.rido.ui.components.CompactSectionSurface
import io.github.peanutpersimmon.rido.ui.components.PageHeader
import io.github.peanutpersimmon.rido.ui.components.LocalFloatingNavigationClearance
import io.github.peanutpersimmon.rido.ui.components.picker.AppCalendarDialog
import io.github.peanutpersimmon.rido.ui.components.picker.CalendarSelectionMode
import io.github.peanutpersimmon.rido.ui.theme.AppMotion
import io.github.peanutpersimmon.rido.ui.theme.AppTheme

@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel,
    contentPadding: PaddingValues,
    active: Boolean,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val d = AppTheme.dimens
    Column(
        Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(top = d.pageHeaderTop)
    ) {
        when (val ready = state) {
            StatisticsUiState.Loading ->
                Text("正在读取统计…",
                    modifier = Modifier.padding(horizontal = d.pageHorizontal),
                    style = AppTheme.type.secondary)
            is StatisticsUiState.Error ->
                Text(
                    ready.message,
                    Modifier.padding(horizontal = d.pageHorizontal),
                    style = AppTheme.type.secondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            is StatisticsUiState.Ready ->
                StatisticsReadyContent(ready, viewModel, active)
        }
    }
}

@Composable
private fun StatisticsReadyContent(
    ready: StatisticsUiState.Ready,
    viewModel: StatisticsViewModel,
    active: Boolean,
) {
    val sections = StatisticsSection.entries
    var dateDialog by remember { mutableStateOf(false) }
    val categoryScroll = rememberScrollState()
    val tagScroll = rememberScrollState()
    val selectedSection = ready.selection.section
    val d = AppTheme.dimens

    BackHandler(
        enabled = active && ready.selection.detailId(selectedSection) != null,
    ) {
        viewModel.closeDetail(selectedSection)
    }
    LaunchedEffect(ready.selection.categoryDetailId, ready.selection.period) {
        categoryScroll.scrollTo(0)
    }
    LaunchedEffect(ready.selection.tagDetailId, ready.selection.period) {
        tagScroll.scrollTo(0)
    }

    Column(Modifier.fillMaxSize()) {
        // The level-one tabs live in the shared fixed-height header so this page's first
        // row matches Home and Settings.
        PageHeader {
            CompactPrimaryNavigation(
                sections,
                selectedSection,
                { it.label },
                viewModel::selectSection,
            )
        }
        Crossfade(
            targetState = selectedSection,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = d.pageHorizontal),
            animationSpec = tween(
                durationMillis = AppMotion.quickDurationMillis,
                easing = AppMotion.standardEasing,
            ),
            label = "Statistics section",
        ) { section ->
            StatisticsSectionPage(
                section = section,
                ready = ready,
                onGranularity = viewModel::selectGranularity,
                onShiftPeriod = viewModel::shiftPeriod,
                onOpenDate = { dateDialog = true },
                onOpenDetail = { id -> viewModel.openDetail(section, id) },
                onCloseDetail = { viewModel.closeDetail(section) },
                onShiftSleepMonth = viewModel::shiftSleepMonth,
                scrollState = when (section) {
                    StatisticsSection.CATEGORY -> categoryScroll
                    StatisticsSection.TAG -> tagScroll
                    StatisticsSection.SLEEP -> null
                },
            )
        }
    }
    if (dateDialog) {
        AppCalendarDialog(
            ready.selection.period.anchor,
            mode = when (ready.selection.period.granularity) {
                StatisticsGranularity.DAY -> CalendarSelectionMode.DAY
                StatisticsGranularity.WEEK -> CalendarSelectionMode.WEEK
                StatisticsGranularity.MONTH -> CalendarSelectionMode.MONTH
                StatisticsGranularity.YEAR -> CalendarSelectionMode.YEAR
            },
            onDismiss = { dateDialog = false },
            onConfirm = {
                viewModel.selectDate(it)
                dateDialog = false
            },
        )
    }
}

@Composable
private fun StatisticsSectionPage(
    section: StatisticsSection,
    ready: StatisticsUiState.Ready,
    onGranularity: (StatisticsGranularity) -> Unit,
    onShiftPeriod: (Long) -> Unit,
    onOpenDate: () -> Unit,
    onOpenDetail: (Long?) -> Unit,
    onCloseDetail: () -> Unit,
    onShiftSleepMonth: (Long) -> Unit,
    scrollState: ScrollState?,
) {
    val d = AppTheme.dimens
    val navigationClearance = LocalFloatingNavigationClearance.current
    if (section == StatisticsSection.SLEEP) {
        SleepStatisticsScreen(
            ready.selection.sleepMonth,
            ready.sources.nightSleeps,
            ready.sources.naps,
            onShiftSleepMonth,
            Modifier
                .fillMaxSize()
        )
        return
    }

    val category = section == StatisticsSection.CATEGORY
    val detail = ready.selection.detailId(section)
    val names = if (category) {
        ready.sources.labels.categoriesById.mapValues { it.value.name } + mapOf(-1L to "未分类")
    } else {
        ready.sources.labels.tagNames
    }
    val scroll = requireNotNull(scrollState)

    Column(Modifier.fillMaxSize()) {
        StatisticsPeriodNavigation(
            ready.selection.period,
            onGranularity,
            onShiftPeriod,
            onOpenDate,
        )
        if (detail != null) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(d.minimumTouchTarget)
                        .clickable(onClick = onCloseDetail),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("‹", style = AppTheme.type.sectionTitle)
                }
                Text(
                    if (category) names[detail] ?: "未分类"
                    else "#" + (names[detail] ?: "已删除标签"),
                    Modifier.weight(1f),
                    style = AppTheme.type.secondary,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scroll)
                .padding(bottom = maxOf(d.spaceL, navigationClearance)),
            verticalArrangement = Arrangement.spacedBy(d.spaceS),
        ) {
            if (detail == null) {
                StatisticsSummaryRow(formatHours(ready.sources.actual.totalMillis))
                CompactSectionSurface {
                    Text(
                        if (category) "分类时长" else "标签时长",
                        style = AppTheme.type.compactSectionTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(d.spaceXs))
                    DurationRanking(
                        ready.sources.actual.rank(category),
                        names,
                        category,
                        onOpenDetail,
                        categoryColorKeys = ready.sources.labels.categoriesById.mapValues { it.value.colorKey },
                    )
                    if (!category) {
                        Spacer(Modifier.height(d.spaceXs))
                        Text(
                            "同一记录可计入多个标签",
                            style = AppTheme.type.assistive,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                val selected = ready.sources.actual.selected(category, detail)
                StatisticsSummaryRow(
                    formatHours(selected.sumOf { it.interval.durationMillis })
                )
                val period = ready.selection.period
                if (period.granularity != StatisticsGranularity.DAY) {
                    CompactSectionSurface {
                        Text(
                            "趋势",
                            style = AppTheme.type.compactSectionTitle,
                        )
                        Spacer(Modifier.height(d.spaceXs))
                        DurationTrend(
                            ready.sources.actual.trend(selected),
                            period.granularity,
                        )
                    }
                }
                if (
                    period.granularity == StatisticsGranularity.MONTH ||
                    period.granularity == StatisticsGranularity.YEAR
                ) {
                    CompactSectionSurface {
                        Text(
                            "活跃分布",
                            style = AppTheme.type.compactSectionTitle,
                        )
                        Spacer(Modifier.height(d.spaceXs))
                        DurationHeatmap(
                            period.start.year,
                            period.start.monthValue.takeIf {
                                period.granularity == StatisticsGranularity.MONTH
                            },
                            ready.sources.actual.daily(selected),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatisticsSummaryRow(value: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "总实际时长",
            style = AppTheme.type.bodyCompact,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = AppTheme.type.bodyCompact,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

