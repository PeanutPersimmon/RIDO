package io.github.peanutpersimmon.rido.ui.feature.today

import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peanutpersimmon.rido.R
import io.github.peanutpersimmon.rido.presentation.today.TodayUiState
import io.github.peanutpersimmon.rido.presentation.today.TodayViewModel
import io.github.peanutpersimmon.rido.presentation.today.todayInitialContent
import io.github.peanutpersimmon.rido.presentation.today.TimelineEntryUiModel
import io.github.peanutpersimmon.rido.ui.components.AppBottomSheet
import io.github.peanutpersimmon.rido.ui.components.ErrorState
import io.github.peanutpersimmon.rido.ui.components.PageHeader
import io.github.peanutpersimmon.rido.ui.components.LocalFloatingNavigationClearance
import io.github.peanutpersimmon.rido.ui.feature.editor.components.ChoiceRow
import io.github.peanutpersimmon.rido.ui.feature.editor.components.EditorIconAction
import io.github.peanutpersimmon.rido.ui.theme.AppTheme
import kotlinx.coroutines.launch

@Composable
fun TodayScreen(
    viewModel: TodayViewModel,
    contentPadding: PaddingValues,
    onAddTask: () -> Unit,
    onAddEvent: () -> Unit,
    onAddSession: () -> Unit,
    onAddSleep: () -> Unit,
    onEntryClick: (TimelineEntryUiModel) -> Unit,
    modifier: Modifier = Modifier,
    onPlanItemClick: (io.github.peanutpersimmon.rido.presentation.today.TodayPlanItemUiModel) -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    TodayScreenContent(
        uiState = uiState,
        contentPadding = contentPadding,
        onEntryClick = onEntryClick,
        onAddTask = onAddTask,
        onAddEvent = onAddEvent,
        onAddSession = onAddSession,
        onAddSleep = onAddSleep,
        modifier = modifier, onPlanItemClick = onPlanItemClick,
    )
}

@Composable
fun TodayScreenContent(
    uiState: TodayUiState,
    contentPadding: PaddingValues,
    onEntryClick: (TimelineEntryUiModel) -> Unit,
    modifier: Modifier = Modifier,
    onAddTask: () -> Unit = {},
    onAddEvent: () -> Unit = {},
    onAddSession: () -> Unit = {},
    onAddSleep: () -> Unit = {},
    onPlanItemClick: (io.github.peanutpersimmon.rido.presentation.today.TodayPlanItemUiModel) -> Unit = {},
) {
    var addOpen by remember { mutableStateOf(false) }
    val navigationClearance = LocalFloatingNavigationClearance.current
    val content = when (uiState) {
        TodayUiState.Loading -> remember { todayInitialContent(System.currentTimeMillis()) }
        is TodayUiState.Content -> uiState
        is TodayUiState.Error -> null
    }
    if (content == null) {
        ErrorState(
            title = stringResource(R.string.today_load_error),
            body = (uiState as TodayUiState.Error).message,
            modifier = modifier.padding(contentPadding),
        )
    } else {
        BoxWithConstraints(
            modifier.fillMaxSize().padding(contentPadding)

        ) {
            val density = LocalDensity.current
            val scope = rememberCoroutineScope()
            val summary = content.planSummary
            val maxExpandedBodyHeight = maxHeight * 0.35f
            val range = with(density) { maxExpandedBodyHeight.toPx() }
            val pull = remember(content.dateLabel, range) { TodayPullState(range) }

            Column(Modifier.fillMaxSize()) {
                PageHeader (height = AppTheme.dimens.todayHeaderHeight,
                    contentTopOffset = (-10).dp,
                    ) {
                    TodayHeader(dateLabel = content.dateLabel,
                        onAdd = { addOpen = true }, onSleep = onAddSleep)
                }
                Spacer(Modifier.height(AppTheme.dimens.todayHeaderContentGap))
                Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = navigationClearance)
                    .padding(horizontal = AppTheme.dimens.pageHorizontal)) {
                    TodayTimeline(
                        awakeDay = content.awakeDay,
                        entries = content.timelineEntries,
                        displayMode = content.displayMode,
                        nowMillis = content.nowMillis,
                        onEntryClick = onEntryClick,
                        modifier = Modifier.fillMaxSize(),
                        isToday = content.isToday,
                        reminderVisible = summary != null,
                        isLoadingShell = uiState == TodayUiState.Loading,
                    )
                    summary?.let {
                        Column(Modifier.align(Alignment.TopCenter).zIndex(1f)) {
                            TodayPlanSummary(
                                summary = it,
                                state = pull,
                                maxExpandedBodyHeight = maxExpandedBodyHeight,
                                onItemClick = onPlanItemClick,
                                onToggle = {
                                    scope.launch { pull.settle(expand = pull.fraction < 0.5f) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
    if (addOpen) AppBottomSheet("新增", { addOpen = false },
        bottomVisual = io.github.peanutpersimmon.rido.ui.components.SheetBottomVisual.BodyTextRow,
        headerAction = { EditorIconAction(Icons.Outlined.Close, "关闭新增", { addOpen = false }, compactSheetAction = true) }) {
        Column {
            ChoiceRow("新建任务", false, sheetStyle = true, showSelectionIndicator = false) { addOpen = false; onAddTask() }
            ChoiceRow("新建日程", false, sheetStyle = true, showSelectionIndicator = false) { addOpen = false; onAddEvent() }
            ChoiceRow("新建记录", false, sheetStyle = true, showSelectionIndicator = false) { addOpen = false; onAddSession() }
        }
    }
}
