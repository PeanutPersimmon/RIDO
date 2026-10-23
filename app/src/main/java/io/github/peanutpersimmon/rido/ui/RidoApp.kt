package io.github.peanutpersimmon.rido.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.peanutpersimmon.rido.R
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.presentation.AppViewModel
import io.github.peanutpersimmon.rido.presentation.editor.event.EventEditorViewModel
import io.github.peanutpersimmon.rido.presentation.editor.session.SessionEditorViewModel
import io.github.peanutpersimmon.rido.presentation.editor.task.TaskEditorViewModel
import io.github.peanutpersimmon.rido.presentation.timer.TimerViewModel
import io.github.peanutpersimmon.rido.presentation.timer.TimerUiState
import io.github.peanutpersimmon.rido.presentation.today.TodayViewModel
import io.github.peanutpersimmon.rido.presentation.plan.PlanViewModel
import io.github.peanutpersimmon.rido.presentation.statistics.StatisticsViewModel
import io.github.peanutpersimmon.rido.ui.feature.statistics.StatisticsScreen
import io.github.peanutpersimmon.rido.ui.components.AppNavigationBar
import io.github.peanutpersimmon.rido.ui.components.AppNavigationItem
import io.github.peanutpersimmon.rido.ui.components.AppScaffold
import io.github.peanutpersimmon.rido.ui.components.AppSnackbar
import io.github.peanutpersimmon.rido.ui.components.LocalBottomSheetVisibility
import io.github.peanutpersimmon.rido.ui.feature.editor.event.EventEditorSheet
import io.github.peanutpersimmon.rido.ui.feature.editor.session.SessionEditorSheet
import io.github.peanutpersimmon.rido.ui.feature.editor.task.TaskEditorSheet
import io.github.peanutpersimmon.rido.ui.feature.timer.TimerScreen
import io.github.peanutpersimmon.rido.ui.feature.timer.ActiveSessionPill
import io.github.peanutpersimmon.rido.ui.feature.timer.TimerControlSheet
import io.github.peanutpersimmon.rido.ui.feature.today.TodayScreen
import io.github.peanutpersimmon.rido.ui.feature.plan.PlanScreen
import io.github.peanutpersimmon.rido.ui.feature.today.InlineCreateKind
import io.github.peanutpersimmon.rido.ui.feature.today.TodayEditorSheet
import io.github.peanutpersimmon.rido.ui.feature.today.TodayEditorRequest
import io.github.peanutpersimmon.rido.ui.feature.today.todayEditorRequestFor
import io.github.peanutpersimmon.rido.ui.navigation.AppRoute
import io.github.peanutpersimmon.rido.ui.navigation.RootDestination
import io.github.peanutpersimmon.rido.ui.screen.PlaceholderScreen
import io.github.peanutpersimmon.rido.ui.theme.RidoTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import io.github.peanutpersimmon.rido.ui.feature.voice.VoiceOverlayHost

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RidoApp(
    viewModel: AppViewModel,
    voiceViewModel: io.github.peanutpersimmon.rido.presentation.voice.VoiceViewModel,
    metadataViewModel: io.github.peanutpersimmon.rido.presentation.settings.MetadataViewModel,
    qwenSettingsViewModel: io.github.peanutpersimmon.rido.presentation.settings.QwenSettingsViewModel,
    todayViewModel: TodayViewModel,
    planViewModel: PlanViewModel,
    statisticsViewModel: StatisticsViewModel,
    timerViewModel: TimerViewModel,
    taskEditorViewModel: TaskEditorViewModel,
    eventEditorViewModel: EventEditorViewModel,
    sessionEditorViewModel: SessionEditorViewModel,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val metadata by metadataViewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var voiceOpen by remember { mutableStateOf(false) }
    var voiceAutoStart by remember { mutableStateOf(false) }
    var voiceHoldActive by remember { mutableStateOf(false) }
    var voicePressCancelled by remember { mutableStateOf(false) }
    var voiceStartedImmediately by remember { mutableStateOf(false) }
    var voiceRequest by remember { mutableStateOf(0) }
    var visibleBottomSheets by remember { mutableIntStateOf(0) }
    val bottomSheetVisibilityObserver = remember {
        { visible: Boolean ->
            visibleBottomSheets = (visibleBottomSheets + if (visible) 1 else -1).coerceAtLeast(0)
        }
    }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    val imeVisible = rememberUpdatedState(WindowInsets.isImeVisible)
    LaunchedEffect(voiceViewModel) {
        voiceViewModel.executed.collect { receipt ->
            focus.clearFocus(); keyboard?.hide()
            voiceOpen = false
            snackbar.currentSnackbarData?.dismiss()
            scope.launch {
                snapshotFlow { !imeVisible.value && ime.getBottom(density) == 0 }.first { it }
                if (snackbar.showSnackbar(receipt.message, "撤销", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
                    voiceViewModel.undo(receipt)?.let { snackbar.showSnackbar(it) }
                }
            }
        }
    }
    var todayEditor by remember { mutableStateOf<TodayEditorRequest?>(null) }
    androidx.compose.runtime.CompositionLocalProvider(
        io.github.peanutpersimmon.rido.ui.feature.settings.LocalMetadataViewModel provides metadataViewModel,
        io.github.peanutpersimmon.rido.ui.feature.settings.LocalMetadataState provides metadata,
        LocalBottomSheetVisibility provides bottomSheetVisibilityObserver,
    ) {
        RidoTheme(themeMode = uiState.themeMode) {
            // The underlying page stays composed while any editor sheet is visible.
            if (uiState.route == AppRoute.Timer || uiState.route is AppRoute.SessionEditor) {
                TimerScreen(
                    viewModel = timerViewModel,
                    onBack = viewModel::back,
                    onEdit = { id -> viewModel.navigate(AppRoute.SessionEditor(id)) },
                )
            } else {
                RootContent(viewModel, todayViewModel, planViewModel, statisticsViewModel, timerViewModel,
                    qwenSettingsViewModel, snackbar, voiceOpen,
                    activeOverlaySuppressed = voiceOpen || todayEditor != null ||
                        uiState.route != null || visibleBottomSheets > 0,
                    onVoice = { start ->
                        if (!voiceViewModel.uiState.value.busy && !voiceViewModel.isAudioActive()) {
                            if (!voiceOpen) voiceViewModel.reset()
                            voiceAutoStart = start
                            voiceHoldActive = start
                            voicePressCancelled = false
                            voiceRequest++
                            voiceOpen = true
                            voiceStartedImmediately = start &&
                                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                                PackageManager.PERMISSION_GRANTED
                            if (voiceStartedImmediately) voiceViewModel.startAudio()
                        }
                    },
                    onVoiceRelease = { voiceHoldActive = false; voiceViewModel.releaseAudio() },
                    onVoiceCancel = {
                        voiceHoldActive = false
                        voicePressCancelled = true
                        voiceViewModel.cancelAudio()
                        voiceOpen = false
                    },
                    voiceInput = {
                        if (voiceOpen) io.github.peanutpersimmon.rido.ui.feature.voice.VoiceInputSheet(
                            voiceViewModel, taskEditorViewModel, eventEditorViewModel, sessionEditorViewModel,
                            autoStart = voiceAutoStart, holdActive = voiceHoldActive,
                            pressCancelled = voicePressCancelled,
                            startedImmediately = voiceStartedImmediately, requestId = voiceRequest,
                            onDismiss = { voiceHoldActive = false; voiceViewModel.cancelAudio(); voiceOpen = false; voiceViewModel.reset() })
                    },
                    onCreate = { todayEditor = TodayEditorRequest(it) },
                    onEdit = { todayEditor = it },
                )
            }
            when (val route = uiState.route) {
                AppRoute.Timer -> Unit
                is AppRoute.TaskEditor -> TaskEditorSheet(taskEditorViewModel, route.id, viewModel::back,
                    onChanged = todayViewModel::refreshAfterEditorChange) {
                    viewModel.back()
                }
                is AppRoute.EventEditor -> EventEditorSheet(eventEditorViewModel, route.id, viewModel::back,
                    onCreateRecord = { id -> viewModel.back(); todayEditor = TodayEditorRequest(InlineCreateKind.SESSION, sourceEventId = id) },
                    onChanged = todayViewModel::refreshAfterEditorChange) { snapshot, restore ->
                    viewModel.back()
                    scope.launch { showUndo(snackbar, snapshot, restore) }
                }
                is AppRoute.SessionEditor -> SessionEditorSheet(
                    sessionEditorViewModel, route.id, viewModel::back,
                    onStarted = viewModel::back,
                    onDeleted = { snapshot, restore -> viewModel.back(); scope.launch { showUndo(snackbar, snapshot, restore) } },
                )
                null -> {
                    todayEditor?.let { request ->
                        TodayEditorSheet(
                            request = request,
                            taskViewModel = taskEditorViewModel,
                            eventViewModel = eventEditorViewModel,
                            sessionViewModel = sessionEditorViewModel,
                            snackbar = snackbar,
                            scope = scope,
                            onDismiss = { todayEditor = null },
                            onEventRecord = { id -> todayEditor = TodayEditorRequest(InlineCreateKind.SESSION, sourceEventId = id) },
                            onEventChanged = todayViewModel::refreshAfterEditorChange,
                            onTaskChanged = todayViewModel::refreshAfterEditorChange,
                            onSessionStarted = { todayEditor = null },
                        )
                    }
                }
            }
        }
    }
}

internal suspend fun <T> showUndo(host: SnackbarHostState, snapshot: T, restore: suspend (T) -> Boolean) {
    val result = host.showSnackbar("已删除", "撤销", duration = SnackbarDuration.Short)
    if (result == SnackbarResult.ActionPerformed && !restore(snapshot)) host.showSnackbar("无法恢复记录")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RootContent(
    viewModel: AppViewModel,
    todayViewModel: TodayViewModel,
    planViewModel: PlanViewModel,
    statisticsViewModel: StatisticsViewModel,
    timerViewModel: TimerViewModel,
    qwenSettingsViewModel: io.github.peanutpersimmon.rido.presentation.settings.QwenSettingsViewModel,
    snackbar: SnackbarHostState,
    voiceOpen: Boolean,
    activeOverlaySuppressed: Boolean,
    onVoice: (Boolean) -> Unit,
    onVoiceRelease: () -> Unit,
    onVoiceCancel: () -> Unit,
    voiceInput: @Composable () -> Unit,
    onCreate: (InlineCreateKind) -> Unit,
    onEdit: (TodayEditorRequest) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val timerUiState by timerViewModel.uiState.collectAsStateWithLifecycle()
    var timerControlOpen by remember { mutableStateOf(false) }
    val activeSession = timerUiState as? TimerUiState.Active
    LaunchedEffect(activeSession?.sessionId) {
        if (activeSession == null) timerControlOpen = false
    }
    val destinations = RootDestination.entries
    val navigationItems = destinations.map { AppNavigationItem(stringResource(it.labelRes), it.icon) }
    val pager = rememberPagerState(initialPage = destinations.indexOf(uiState.destination)) { destinations.size }
    val isTodayVisible = uiState.destination == RootDestination.Today
    LaunchedEffect(isTodayVisible) {
        todayViewModel.setPageVisible(isTodayVisible)
    }
    val isPlanVisible = uiState.destination == RootDestination.Plan
    LaunchedEffect(isPlanVisible) {
        planViewModel.setPageVisible(isPlanVisible)
    }
    LaunchedEffect(uiState.destination) {
        val target = destinations.indexOf(uiState.destination)
        if (pager.currentPage != target || pager.currentPageOffsetFraction != 0f)
            pager.animateScrollToPage(target, animationSpec = tween(180))
    }
    // safeDrawing includes IME. The root and its nested page scaffold deliberately use only system bars/cutouts.
    val pageInsets = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout)
    VoiceOverlayHost(voiceOpen = voiceOpen,
        bottomBar = {
            AppNavigationBar(items = navigationItems, selectedIndex = destinations.indexOf(uiState.destination),
                onItemSelected = { viewModel.selectDestination(destinations[it]) },
                onVoiceClick = { onVoice(false) }, onVoiceLongClick = { onVoice(true) },
                onVoiceRelease = onVoiceRelease, onVoiceCancel = onVoiceCancel)
        }, voiceInput = voiceInput,
        snackbar = { SnackbarHost(snackbar) { AppSnackbar(it) } },
        floatingOverlayVisible = activeSession != null && !activeOverlaySuppressed && !timerControlOpen,
        floatingOverlay = {
            activeSession?.let { active ->
                ActiveSessionPill(state = active, onClick = {
                    if (active.kind == SessionKind.ACTIVITY) {
                        timerControlOpen = true
                    } else {
                        val warning = if (active.elapsed.elapsedMillis > 24L * 60L * 60L * 1_000L) {
                            "这条睡眠记录已超过 24 小时，请确认实际起床时间"
                        } else null
                        onEdit(TodayEditorRequest(
                            kind = InlineCreateKind.SESSION,
                            id = active.sessionId,
                            isActive = true,
                            sleep = true,
                            sleepWarning = warning,
                        ))
                    }
                })
            }
        },
        content = {
    AppScaffold(contentWindowInsets = pageInsets) { outerPadding ->
        HorizontalPager(state = pager, userScrollEnabled = false, beyondViewportPageCount = destinations.size - 1,
            key = { destinations[it].name },
            modifier = Modifier.fillMaxSize().padding(outerPadding).consumeWindowInsets(outerPadding)) { page ->
        val destination = destinations[page]
        AppScaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            when (destination) {
                RootDestination.Today -> Unit
                RootDestination.Plan -> Unit
                RootDestination.Statistics -> Unit
                RootDestination.Settings -> Unit
            }
        },
    ) { contentPadding ->
        if (destination == RootDestination.Today) {
            TodayScreen(
                todayViewModel, contentPadding,
                onAddTask = { onCreate(InlineCreateKind.TASK) },
                onAddEvent = { onCreate(InlineCreateKind.EVENT) },
                onAddSession = { onCreate(InlineCreateKind.SESSION) },
                onAddSleep = { onEdit(TodayEditorRequest(InlineCreateKind.SESSION, sleep = true)) },
                onPlanItemClick = { item -> onEdit(TodayEditorRequest(
                    if (item.source == io.github.peanutpersimmon.rido.domain.usecase.TodayPlanSource.TASK) InlineCreateKind.TASK else InlineCreateKind.EVENT, item.sourceId)) },
                onEntryClick = { entry ->
                    todayEditorRequestFor(entry)?.let(onEdit)
                },
            )
        } else if (destination == RootDestination.Plan) {
            PlanScreen(planViewModel, contentPadding,
                onTaskClick = { onEdit(TodayEditorRequest(InlineCreateKind.TASK, it)) },
                onEventClick = { onEdit(TodayEditorRequest(InlineCreateKind.EVENT, it)) },
                onRecordClick = { id, kind ->
                    onEdit(TodayEditorRequest(
                        InlineCreateKind.SESSION,
                        id,
                        sleep = opensSleepEditor(kind),
                    ))
                },
                onTaskChanged = todayViewModel::refreshAfterEditorChange)
        } else if (destination == RootDestination.Statistics) {
            StatisticsScreen(statisticsViewModel, contentPadding, uiState.destination == RootDestination.Statistics)
        } else if (destination == RootDestination.Settings) {
            io.github.peanutpersimmon.rido.ui.feature.settings.SettingsContent(
                uiState.themeMode, viewModel::setNightMode, qwenSettingsViewModel, planViewModel,
                Modifier.padding(contentPadding))
        } else {
            val body = when (destination) {
                RootDestination.Plan -> R.string.skeleton_plan_body
                RootDestination.Statistics -> R.string.skeleton_statistics_body
                RootDestination.Settings -> R.string.skeleton_settings_body
                RootDestination.Today -> error("handled above")
            }
            PlaceholderScreen(stringResource(body), Modifier.padding(contentPadding))
        }
        }
        }
    }
    })
    val activity = activeSession?.takeIf { it.kind == SessionKind.ACTIVITY }
    if (timerControlOpen && activity != null) {
        TimerControlSheet(
            state = activity,
            onDismiss = { timerControlOpen = false; timerViewModel.clearError() },
            onEdit = { id ->
                timerControlOpen = false
                onEdit(TodayEditorRequest(InlineCreateKind.SESSION, id, isActive = true))
            },
            onStop = timerViewModel::stop,
            onStopAndComplete = timerViewModel::stopAndCompleteTask,
        )
    }
}

internal fun opensSleepEditor(kind: SessionKind): Boolean = kind != SessionKind.ACTIVITY

