package com.dayline.app.ui.navigation

import android.Manifest
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dayline.app.core.data.prefs.SettingsRepository
import com.dayline.app.core.model.AppSettings
import com.dayline.app.core.model.TaskList
import com.dayline.app.core.model.TimeFormat
import com.dayline.app.ui.calendar.CalendarScreen
import com.dayline.app.ui.common.Formats
import com.dayline.app.ui.common.LocalFormats
import com.dayline.app.ui.components.DaylineNavBar
import com.dayline.app.ui.components.NavItem
import com.dayline.app.ui.focus.FocusScreen
import com.dayline.app.ui.habits.HabitDetailScreen
import com.dayline.app.ui.habits.HabitEditorScreen
import com.dayline.app.ui.habits.HabitsScreen
import com.dayline.app.ui.more.MoreScreen
import com.dayline.app.ui.motion.Motion
import com.dayline.app.ui.motion.rememberHaptics
import com.dayline.app.ui.notes.NoteEditorScreen
import com.dayline.app.ui.notes.NotesScreen
import com.dayline.app.ui.onboarding.LaunchIntro
import com.dayline.app.ui.onboarding.OnboardingScreen
import com.dayline.app.ui.quickadd.QuickAddDefaults
import com.dayline.app.ui.quickadd.QuickAddSheet
import com.dayline.app.ui.search.SearchScreen
import com.dayline.app.ui.settings.DashboardSettingsScreen
import com.dayline.app.ui.settings.SettingsScreen
import com.dayline.app.ui.settings.TagsScreen
import com.dayline.app.ui.stats.StatsScreen
import com.dayline.app.ui.tasks.TaskEditorScreen
import com.dayline.app.ui.tasks.TaskListMode
import com.dayline.app.ui.tasks.TaskListScreen
import com.dayline.app.ui.theme.DaylineTheme
import com.dayline.app.ui.theme.Palette
import com.dayline.app.ui.today.TodayScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(private val settingsRepository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<AppSettings?> = settingsRepository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val notificationPromptShown: StateFlow<Boolean?> = settingsRepository.notificationPromptShown.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val onboardingDone: StateFlow<Boolean?> = settingsRepository.onboardingDone.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    fun markPrompted() = viewModelScope.launch { settingsRepository.markNotificationPromptShown() }
    fun finishOnboarding() = viewModelScope.launch { settingsRepository.markOnboardingDone() }
    val nameAsked: StateFlow<Boolean?> = settingsRepository.nameAsked.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    fun setName(name: String) = viewModelScope.launch { settingsRepository.setUserName(name) }
    fun skipName() = viewModelScope.launch { settingsRepository.markNameAsked() }
}

/** Snackbar host shared by all screens (used for Undo and feedback). */
val LocalSnackbar = staticCompositionLocalOf { SnackbarHostState() }

/** Opens the global Quick Add sheet with optional defaults. */
val LocalQuickAdd = staticCompositionLocalOf<(QuickAddDefaults) -> Unit> { {} }

/** What the global add button pre-fills; screens override it while they are shown. */
@Stable
class FabDefaultsHolder {
    var defaults by mutableStateOf(QuickAddDefaults())
}

val LocalFabDefaults = staticCompositionLocalOf { FabDefaultsHolder() }

/** Call from a screen to make the add button pre-fill these values while the screen is visible. */
@Composable
fun FabDefaults(defaults: QuickAddDefaults) {
    val holder = LocalFabDefaults.current
    DisposableEffect(holder, defaults) {
        holder.defaults = defaults
        onDispose { if (holder.defaults == defaults) holder.defaults = QuickAddDefaults() }
    }
}

@Composable
fun DaylineRoot(pendingRoute: String?, onRouteConsumed: () -> Unit, vm: MainViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val onboarded by vm.onboardingDone.collectAsStateWithLifecycle()
    // Skip the intro when the app is opened for a specific screen (notification, widget, quick add).
    var introDone by rememberSaveable { mutableStateOf(pendingRoute != null) }
    val s = settings
    DaylineTheme(themeMode = s?.themeMode ?: com.dayline.app.core.model.ThemeMode.SYSTEM, dynamicColor = s?.dynamicColor ?: true, accentColor = s?.accentColor ?: AppSettings.DEFAULT_ACCENT) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            if (s != null && onboarded != null) {
                val context = LocalContext.current
                val is24 = when (s.timeFormat) {
                    TimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
                    TimeFormat.H24 -> true
                    TimeFormat.H12 -> false
                }
                CompositionLocalProvider(LocalFormats provides Formats(s.dateFormat, is24, s.weekStart)) {
                    if (onboarded == true) {
                        NotificationPermissionPrompt(vm)
                        AppScaffold(pendingRoute, onRouteConsumed)
                        val nameAsked by vm.nameAsked.collectAsStateWithLifecycle()
                        if (nameAsked == false && introDone) {
                            com.dayline.app.ui.onboarding.NamePromptDialog(onSave = { vm.setName(it) }, onSkip = { vm.skipName() })
                        }
                    } else {
                        OnboardingScreen(
                            onFinish = { vm.markPrompted(); vm.finishOnboarding(); vm.skipName() },
                            onName = { vm.setName(it) },
                        )
                    }
                }
            }
            if (!introDone) LaunchIntro(ready = s != null && onboarded != null, onFinished = { introDone = true })
            SystemBarsAppearance(onPrimary = !introDone)
        }
    }
}

/**
 * Status and navigation bar icons follow the app's own theme (not just the system one), so the
 * top of the screen always matches Dayline — the bars themselves are transparent and the app's
 * headers draw behind them.
 */
@Composable
fun SystemBarsAppearance(onPrimary: Boolean = false) {
    val view = androidx.compose.ui.platform.LocalView.current
    val dark = DaylineTheme.isDark
    if (view.isInEditMode) return
    androidx.compose.runtime.SideEffect {
        var ctx = view.context
        while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) ctx = ctx.baseContext
        val window = (ctx as? android.app.Activity)?.window ?: return@SideEffect
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !dark && !onPrimary
        controller.isAppearanceLightNavigationBars = !dark && !onPrimary
    }
}

@Composable
private fun NotificationPermissionPrompt(vm: MainViewModel) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val shown by vm.notificationPromptShown.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.markPrompted() }
    LaunchedEffect(shown) {
        if (shown == false) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

private val topLevelRoutes = TopLevel.entries.map { it.route }.toSet()
private val fabRoutes = setOf(Routes.TODAY, Routes.CALENDAR, Routes.INBOX, Routes.UPCOMING, Routes.ALL)

/** Material "emphasized" easing — fast start, long gentle settle (iOS-like). */
private val Emphasized = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)
private fun <T> pushSpec() = tween<T>(durationMillis = 380, easing = Emphasized)
private val navItems = TopLevel.entries.map { NavItem(it.route, it.label, it.selected, it.unselected) }

@Composable
private fun AppScaffold(pendingRoute: String?, onRouteConsumed: () -> Unit) {
    val navController = rememberNavController()
    val navigator = remember(navController) { AppNavigator(navController) }
    val snackbar = remember { SnackbarHostState() }
    val fabDefaults = remember { FabDefaultsHolder() }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var quickAdd by remember { mutableStateOf<QuickAddDefaults?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var fabHiddenByScroll by remember { mutableStateOf(false) }
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    LaunchedEffect(route) { fabHiddenByScroll = false; menuOpen = false }
    // Refused task actions (e.g. completing a task that's still blocked) explain themselves here.
    LaunchedEffect(Unit) {
        com.dayline.app.core.data.repository.TaskNotices.messages.collect { msg ->
            haptics.heavy()
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(msg)
        }
    }

    LaunchedEffect(pendingRoute) {
        pendingRoute?.let { r ->
            when {
                r.startsWith("task/") -> navigator.to(r)
                r == ACTION_QUICK_ADD -> quickAdd = QuickAddDefaults()
                r in topLevelRoutes -> navigator.topLevel(r)
                else -> navigator.to(r)
            }
            onRouteConsumed()
        }
    }

    // Hide the add button while scrolling down, bring it back on the way up.
    val scrollWatcher = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -4f) fabHiddenByScroll = true else if (available.y > 4f) fabHiddenByScroll = false
                return Offset.Zero
            }
        }
    }

    val openQuickAdd: (QuickAddDefaults) -> Unit = { quickAdd = it }
    CompositionLocalProvider(
        LocalSnackbar provides snackbar,
        LocalQuickAdd provides openQuickAdd,
        LocalFabDefaults provides fabDefaults,
        com.dayline.app.ui.components.LocalScrollingDown provides fabHiddenByScroll,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            val useRail = maxWidth >= 600.dp
            val isTopLevel = route in topLevelRoutes
            val showBottomBar = !useRail && isTopLevel
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                // Screens draw their own headers behind the status bar, so its colour always matches the app.
                contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                bottomBar = {
                    if (showBottomBar) {
                        DaylineNavBar(navItems, route, onSelect = { navigator.topLevel(it.key) })
                    }
                },
            ) { padding ->
                Row(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                    if (useRail) {
                        NavigationRail(Modifier.fillMaxHeight(), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                            Spacer(Modifier.height(12.dp))
                            TopLevel.entries.forEach { dest ->
                                val selected = route == dest.route
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { haptics.light(); navigator.topLevel(dest.route) },
                                    icon = { Icon(if (selected) dest.selected else dest.unselected, contentDescription = null) },
                                    label = { Text(dest.label) },
                                )
                            }
                        }
                    }
                    AppNavHost(navigator, navController, Modifier.weight(1f).nestedScroll(scrollWatcher))
                }
            }

            // Overlay: scrim, snackbars and the add button sit above everything, including the nav bar.
            val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val bottomOffset = if (showBottomBar) 76.dp + navInset else navInset
            val fabVisible = route in fabRoutes && !fabHiddenByScroll && quickAdd == null
            AddMenuScrim(menuOpen) { menuOpen = false }
            Column(
                Modifier
                    .align(Alignment.BottomEnd)
                    .fillMaxWidth()
                    .padding(bottom = bottomOffset),
                horizontalAlignment = Alignment.End,
            ) {
                SnackbarHost(snackbar, Modifier.fillMaxWidth())
                if (route == Routes.NOTES) {
                    // Notes' "New Note" lives up here too, so holding it dims the whole app (nav bar included).
                    com.dayline.app.ui.notes.NoteFabMenu(
                        visible = !fabHiddenByScroll && quickAdd == null,
                        expanded = menuOpen,
                        onExpandedChange = { menuOpen = it },
                        onNew = { navigator.note(0) },
                        onPick = { kind -> menuOpen = false; navigator.newNote(kind) },
                        modifier = Modifier.padding(end = 16.dp, bottom = 16.dp),
                    )
                }
                if (route in fabRoutes) {
                    AddFab(
                        visible = fabVisible,
                        expanded = menuOpen,
                        onTap = { quickAdd = fabDefaults.defaults },
                        onExpandedChange = { menuOpen = it },
                        onAction = { action ->
                            when (action) {
                                AddAction.TASK -> quickAdd = fabDefaults.defaults
                                AddAction.NOTE -> navigator.note(0)
                                AddAction.HABIT -> navigator.editHabit(0)
                                AddAction.FOCUS -> navigator.to(Routes.FOCUS)
                            }
                        },
                        modifier = Modifier.padding(end = 16.dp, bottom = 16.dp),
                    )
                }
            }
        }
        quickAdd?.let { defaults ->
            QuickAddSheet(
                defaults = defaults,
                onDismiss = { quickAdd = null },
                onOpenFullEditor = { d -> navigator.newTask(d.date, d.projectId, d.listId) },
                onAdded = { title ->
                    haptics.success()
                    scope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar("Added “$title”")
                    }
                },
            )
        }
    }
}

const val ACTION_QUICK_ADD = "action/quick_add"


private fun isTop(e: NavBackStackEntry) = e.destination.route in topLevelRoutes

private fun AnimatedContentTransitionScope<NavBackStackEntry>.tabSwitch() = isTop(initialState) && isTop(targetState)

/** Every screen sits on an opaque surface, so the previous one never shows through mid-transition. */
@Composable
private fun Page(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { content() }
}

@Composable
private fun AppNavHost(navigator: AppNavigator, navController: androidx.navigation.NavHostController, modifier: Modifier) {
    val idArg = listOf(navArgument("id") { type = NavType.LongType })
    NavHost(
        navController = navController,
        startDestination = Routes.TODAY,
        modifier = modifier,
        // Tabs cross-fade with a gentle zoom; pushed screens slide in from the right like iOS.
        enterTransition = {
            if (tabSwitch()) fadeIn(tween(220, delayMillis = 40)) + scaleIn(tween(260), initialScale = 0.97f)
            else slideInHorizontally(pushSpec()) { it }
        },
        exitTransition = {
            if (tabSwitch()) fadeOut(tween(120))
            else slideOutHorizontally(pushSpec()) { -it / 4 } + fadeOut(pushSpec(), targetAlpha = 0.85f)
        },
        popEnterTransition = {
            if (tabSwitch()) fadeIn(tween(220)) else slideInHorizontally(pushSpec()) { -it / 4 } + fadeIn(pushSpec(), initialAlpha = 0.85f)
        },
        popExitTransition = {
            if (tabSwitch()) fadeOut(tween(120)) else slideOutHorizontally(pushSpec()) { it }
        },
    ) {
        composable(Routes.TODAY) { Page { TodayScreen(navigator) } }
        composable(Routes.CALENDAR) { Page { CalendarScreen(navigator) } }
        composable(Routes.INBOX) { Page { TaskListScreen(TaskListMode.INBOX, navigator) } }
        composable(Routes.HABITS) { Page { HabitsScreen(navigator) } }
        composable(Routes.MORE) { Page { MoreScreen(navigator) } }
        composable(Routes.UPCOMING) { Page { TaskListScreen(TaskListMode.UPCOMING, navigator) } }
        composable(Routes.ALL) { Page { TaskListScreen(TaskListMode.ALL, navigator) } }
        composable(Routes.COMPLETED) { Page { TaskListScreen(TaskListMode.COMPLETED, navigator) } }
        composable(Routes.STATS) { Page { StatsScreen(navigator) } }
        composable(Routes.FOCUS_PATTERN, listOf(navArgument("task") { type = NavType.LongType; defaultValue = -1L })) { Page { FocusScreen(navigator) } }
        composable(Routes.NOTES) { Page { NotesScreen(navigator) } }
        composable(Routes.NOTE, idArg + navArgument("kind") { type = NavType.StringType; defaultValue = "" }) { Page { NoteEditorScreen(navigator) } }
        composable(Routes.SETTINGS) { Page { SettingsScreen(navigator) } }
        composable(Routes.DASHBOARD_SETTINGS) { Page { DashboardSettingsScreen(navigator) } }
        composable(Routes.TAGS) { Page { TagsScreen(navigator) } }
        composable(Routes.SEARCH) { Page { SearchScreen(navigator) } }
        composable(Routes.HABIT, idArg) { Page { HabitDetailScreen(navigator) } }
        composable(Routes.HABIT_EDIT, idArg) { Page { HabitEditorScreen(navigator) } }
        composable(
            Routes.TASK,
            listOf(
                navArgument("id") { type = NavType.LongType },
                navArgument("date") { type = NavType.LongType; defaultValue = Long.MIN_VALUE },
                navArgument("project") { type = NavType.LongType; defaultValue = -1L },
                navArgument("list") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { Page { TaskEditorScreen(navigator) } }
    }
}

