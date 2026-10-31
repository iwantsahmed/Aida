package com.rayluxeo.aida.ui.navigation

import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.rayluxeo.aida.assistant.MiraShortcuts
import com.rayluxeo.aida.core.common.SpokenLanguage
import com.rayluxeo.aida.core.design.LocalHapticsEnabled
import com.rayluxeo.aida.ui.common.LocalDateStyle
import com.rayluxeo.aida.core.design.MiraMotion
import com.rayluxeo.aida.core.design.component.LocalMiraBottomInset
import com.rayluxeo.aida.core.design.component.MiraAmbientBackground
import com.rayluxeo.aida.core.design.component.MiraNavigationBar
import com.rayluxeo.aida.core.design.component.MiraNavItem
import com.rayluxeo.aida.ui.alarms.AlarmsScreen
import com.rayluxeo.aida.ui.alarms.TimersScreen
import com.rayluxeo.aida.ui.home.HomeScreen
import com.rayluxeo.aida.ui.ink.InkCanvasScreen
import com.rayluxeo.aida.ui.invoices.InvoicesScreen
import com.rayluxeo.aida.ui.money.MoneyScreen
import com.rayluxeo.aida.ui.reports.ReportsScreen
import com.rayluxeo.aida.ui.mira.MiraScreen
import com.rayluxeo.aida.ui.notes.NotesScreen
import com.rayluxeo.aida.ui.notes.detail.NoteDetailScreen
import com.rayluxeo.aida.ui.onboarding.OnboardingScreen
import com.rayluxeo.aida.ui.automation.AutomationsScreen
import com.rayluxeo.aida.ui.automation.openAccessibilitySettings
import com.rayluxeo.aida.ui.automation.openNotificationListenerSettings
import com.rayluxeo.aida.ui.backup.BackupScreen
import com.rayluxeo.aida.ui.clients.ClientsScreen
import com.rayluxeo.aida.ui.memory.MemoryScreen
import com.rayluxeo.aida.ui.privacy.PrivacyDashboardScreen
import com.rayluxeo.aida.ui.projects.ProjectsScreen
import com.rayluxeo.aida.ui.meetings.MeetingsScreen
import com.rayluxeo.aida.ui.voicenotes.VoiceNotesScreen
import com.rayluxeo.aida.ui.scanner.FilesScreen
import com.rayluxeo.aida.ui.scanner.ScannerScreen
import com.rayluxeo.aida.ui.search.SearchOverlay
import com.rayluxeo.aida.ui.activity.ActivityLogScreen
import com.rayluxeo.aida.ui.licenses.LicensesScreen
import com.rayluxeo.aida.ui.salah.SalahMonthScreen
import com.rayluxeo.aida.ui.salah.SalahScreen
import com.rayluxeo.aida.ui.salah.SalahSettingsScreen
import com.rayluxeo.aida.ui.settings.AssistantSetupScreen
import com.rayluxeo.aida.ui.settings.PermissionsScreen
import com.rayluxeo.aida.ui.settings.PlacesScreen
import com.rayluxeo.aida.ui.settings.SettingsScreen
import com.rayluxeo.aida.ui.settings.VoiceSetupScreen
import com.rayluxeo.aida.ui.tasks.TasksScreen
import com.rayluxeo.aida.ui.tasks.detail.TaskDetailScreen
import com.rayluxeo.aida.ui.quran.QuranScreen
import com.rayluxeo.aida.ui.vault.AppLockGate
import com.rayluxeo.aida.ui.vault.AreaLockGate
import com.rayluxeo.aida.ui.vault.AppLockStrings
import com.rayluxeo.aida.ui.vault.VaultScreen

/**
 * How far the app blurs behind the search overlay at full reveal. Enough that the words underneath
 * stop competing for the eye, not so much that Abu loses his place on the screen he came from.
 */
private val SearchBlurRadius = 20.dp

/**
 * The whole navigable surface of Mira.
 *
 * Nothing is drawn until the shell knows whether Abu has been through onboarding, because both
 * guesses show him a screen he should not see. Once it knows, the bottom bar belongs to the four
 * primary tabs and everything else — the hub, the areas, settings — is pushed over the top.
 */
@Composable
fun MiraApp(
    modifier: Modifier = Modifier,
    entryAction: String? = null,
    entryTargetId: Long? = null,
    onEntryHandled: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
    shellViewModel: MiraShellViewModel = hiltViewModel(),
) {
    val state by shellViewModel.state.collectAsStateWithLifecycle()

    when (val shell = state) {
        MiraShellState.Loading -> Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        )
        is MiraShellState.Ready -> MiraShell(
            state = shell,
            navController = navController,
            entryAction = entryAction,
            entryTargetId = entryTargetId,
            onEntryHandled = onEntryHandled,
            onAskMira = shellViewModel::askMira,
            onStartListening = shellViewModel::startListening,
            onAppBackgrounded = shellViewModel::onAppBackgrounded,
            onAppForegrounded = shellViewModel::onAppForegrounded,
            onUnlockApp = shellViewModel::unlockApp,
            modifier = modifier,
        )
    }
}

/**
 * The frame, once there is something true to put in it.
 *
 * The start destination is captured once and saved rather than read from [state] on every
 * recomposition: finishing onboarding flips `onboardingComplete`, and a NavHost whose start
 * destination changes underneath it rebuilds its graph and discards the back stack.
 */
@Composable
private fun MiraShell(
    state: MiraShellState.Ready,
    navController: NavHostController,
    entryAction: String? = null,
    entryTargetId: Long? = null,
    onEntryHandled: () -> Unit = {},
    onAskMira: (String) -> Unit = {},
    onStartListening: () -> Unit = {},
    onAppBackgrounded: () -> Unit = {},
    onAppForegrounded: () -> Unit = {},
    onUnlockApp: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val startDestination = rememberSaveable {
        if (state.onboardingComplete && state.userName.isNotBlank()) MiraRoutes.HOME else MiraRoutes.ONBOARDING
    }
    val words = shellWording(state.language)
    val backStackEntry by navController.currentBackStackEntryAsState()
    // Which item is lit. Read from the destination Abu just chose, because a tap has to be
    // answered on the frame it happens; it is null on every pushed screen and during onboarding.
    val selectedTab = remember(backStackEntry) { backStackEntry?.destination.tabOrNull() }
    // Whether there is a bar at all is a different question, and it has a different answer.
    val barTab = rememberBottomBarTab(navController)

    // The tile, the widget and the launcher shortcuts name a destination by action. Onboarding
    // outranks them: a shortcut tapped before first run would otherwise drop Abu into a screen
    // that has no data behind it yet.
    LaunchedEffect(entryAction, entryTargetId, state.onboardingComplete, state.userName) {
        val destination = entryAction?.let(::tabForEntryAction)
        if (destination != null && state.onboardingComplete && state.userName.isNotBlank()) {
            navController.switchTab(destination)
            // Three of these promise a SCREEN, not the tab it sits on, so each is pushed over its
            // tab and Back lands on that tab rather than leaving the app. An entry point that
            // opens Mira and then does nothing is the defect AGENTS.md section 12 names first.
            when (entryAction) {
                // Talk rests on the Mira tab itself, so there is nothing to push — instead the
                // tab is asked to come up already listening, which is what the shortcut promised.
                MiraShortcuts.ACTION_TALK -> onStartListening()
                // A launcher shortcut opens a blank task; a task reminder carries the id of the
                // task it is about, so it opens that one rather than an empty create form.
                MiraShortcuts.ACTION_NEW_TASK -> navController.navigate(
                    if (entryTargetId != null) MiraRoutes.taskDetail(entryTargetId) else MiraRoutes.taskDetail(),
                )
                MiraShortcuts.ACTION_CLIENT -> navController.navigate(MiraRoutes.CLIENTS)
                MiraShortcuts.ACTION_NEW_NOTE -> navController.navigate(MiraRoutes.noteDetail())
                // No note and no page: Quick Ink is a blank sheet, and the canvas mints the note
                // it hangs on the first time Abu saves. Sending him to a note first would make
                // the fastest way to write something the slowest.
                MiraShortcuts.ACTION_QUICK_INK -> navController.navigate(
                    MiraRoutes.inkPage(MiraRoutes.NEW_NOTE_ID, MiraRoutes.NEW_INK_PAGE_ID),
                )
                // Both are published launcher shortcuts and notification deep-links, and both have
                // a built screen behind them; without these two lines the shortcut just opened Mira
                // at its last location — the dead end §12 names first.
                MiraShortcuts.ACTION_NEW_INVOICE -> navController.navigate(MiraRoutes.INVOICES)
                MiraShortcuts.ACTION_SCAN_RECEIPT -> navController.navigate(MiraRoutes.SCANNER)
            }
        }
        if (entryAction != null) onEntryHandled()
    }

    CompositionLocalProvider(
        LocalHapticsEnabled provides state.hapticsEnabled,
        LocalDateStyle provides state.dateStyle,
    ) {
        AppLockGate(
            enabled = state.appLockEnabled,
            locked = state.locked,
            strings = words.appLock.toGateStrings(),
            onEnterBackground = onAppBackgrounded,
            onEnterForeground = onAppForegrounded,
            onUnlock = onUnlockApp,
        ) {
            // The aurora lives at the shell so it fills the whole window — behind the content AND
            // behind the translucent nav bar. It shows only on the two presence tabs, and only when
            // one of them is the screen on top: a pushed detail screen has its own opaque obsidian
            // scaffold that covers it. Home and the Mira screen ask for a transparent scaffold so it
            // reads through them.
            //
            // The nav bar FLOATS over the content (not a reserved bottomBar), so the content scrolls
            // edge to edge behind the translucent pill — a card reads through it rather than being
            // cut off above a bar. Its measured height is handed to every screen via
            // [LocalMiraBottomInset], which each MiraScaffold folds into its content's bottom padding
            // so the last item still clears the pill. Each screen owns its own top/side insets.
            Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                var navBarHeight by remember { mutableStateOf(0.dp) }
                val density = LocalDensity.current

                // Only the screens that actually carry the floating bar reserve room for it. A pushed
                // screen (barTab == null) has no bar, so it gets no extra inset — otherwise it would
                // keep the last measured height and leave a dead band at the foot where the bar is not
                // (the cut-off Abu saw on bar-less pages). Its own MiraScaffold still clears the
                // system gesture inset on its own.
                val bottomInset = if (barTab != null) navBarHeight else 0.dp

                // Search is an overlay, not a page: the circle on Home blooms into a full-width field
                // over a blurred app. One progress drives both the blur here and the bloom in the
                // overlay, so they move as one; the overlay stays mounted while it is non-zero so the
                // field blooms shut on dismiss. The anchor is the search circle's own bounds, handed
                // up from Home so the field starts exactly over the icon.
                var searchOpen by remember { mutableStateOf(false) }
                var searchAnchor by remember { mutableStateOf<Rect?>(null) }
                val searchTransition = updateTransition(searchOpen, label = "search")
                val searchProgress by searchTransition.animateFloat(
                    transitionSpec = {
                        if (targetState) MiraMotion.enter(MiraMotion.EXPRESSIVE_MS) else MiraMotion.exit()
                    },
                    label = "searchProgress",
                ) { if (it) 1f else 0f }
                val searchActive = searchOpen || searchProgress > 0.001f

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (searchActive) Modifier.blur(SearchBlurRadius * searchProgress) else Modifier,
                        ),
                ) {
                    if (selectedTab == MiraTab.HOME || selectedTab == MiraTab.MIRA) {
                        MiraAmbientBackground()
                    }

                    CompositionLocalProvider(LocalMiraBottomInset provides bottomInset) {
                        MiraNavHost(
                            navController = navController,
                            startDestination = startDestination,
                            language = state.language,
                            protectMoney = state.protectMoney,
                            protectMemory = state.protectMemory,
                            protectInvoices = state.protectInvoices,
                            protectBackups = state.protectBackups,
                            onAskMira = onAskMira,
                            onOpenSearch = { anchor ->
                                searchAnchor = anchor
                                searchOpen = true
                            },
                        )
                    }

                    // Onboarding and every pushed screen resolve to no tab, which hides the bar — but
                    // only once the screen that hid it has finished arriving (see [rememberBottomBarTab]).
                    if (barTab != null) {
                        MiraNavigationBar(
                            items = MiraTab.entries.map { tab ->
                                val isSelected = tab == (selectedTab ?: barTab)
                                MiraNavItem(
                                    // The selected tab wears the Bold (filled) Solar cut; the rest the
                                    // Linear line — the premium idle/active treatment.
                                    icon = ImageVector.vectorResource(if (isSelected) tab.activeIconRes else tab.iconRes),
                                    label = words.tabLabel(tab),
                                    // While a push covers a tab, the bar is still on screen and still
                                    // belongs to the tab underneath it: the highlight stays where it
                                    // was rather than sliding halfway through the push.
                                    selected = isSelected,
                                    onClick = { navController.switchTab(tab) },
                                )
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .onSizeChanged { navBarHeight = with(density) { it.height.toDp() } },
                        )
                    }
                }

                val anchor = searchAnchor
                if (searchActive && anchor != null) {
                    SearchOverlay(
                        visible = searchOpen,
                        progress = searchProgress,
                        anchor = anchor,
                        onDismiss = { searchOpen = false },
                        onOpenTask = { id -> searchOpen = false; navController.navigate(MiraRoutes.taskDetail(id)) },
                        onOpenNote = { id -> searchOpen = false; navController.navigate(MiraRoutes.noteDetail(id)) },
                        onOpenClient = { searchOpen = false; navController.navigate(MiraRoutes.CLIENTS) },
                        onOpenProject = { searchOpen = false; navController.navigate(MiraRoutes.PROJECTS) },
                        onOpenInvoice = { searchOpen = false; navController.navigate(MiraRoutes.INVOICES) },
                        onOpenMemory = { searchOpen = false; navController.navigate(MiraRoutes.MEMORY) },
                        onOpenMoney = { searchOpen = false; navController.navigate(MiraRoutes.MONEY) },
                        onOpenFiles = { searchOpen = false; navController.navigate(MiraRoutes.FILES) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MiraNavHost(
    navController: NavHostController,
    startDestination: String,
    language: SpokenLanguage,
    protectMoney: Boolean,
    protectMemory: Boolean,
    protectInvoices: Boolean,
    protectBackups: Boolean,
    onAskMira: (String) -> Unit,
    onOpenSearch: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The strings the "Require unlock for" cover shows, named for the area it guards. Resolved once
    // here from the language the host already carries, and copied over the shared app-lock strings
    // so a Money gate reads "Money is locked", not "Mira is locked".
    val words = shellWording(language)
    fun areaLock(areaName: String): com.rayluxeo.aida.ui.vault.AppLockStrings =
        words.appLock.toGateStrings().copy(
            title = words.areaLockedTitle(areaName),
            subtitle = words.areaLockedSubtitle(areaName),
        )

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        // One policy for how Mira moves between screens, decided in one place rather than
        // destination by destination. See `isTab` for what counts as a push.
        enterTransition = {
            if (targetState.isTab) fadeIn(MiraMotion.enter(MiraMotion.STANDARD_MS)) else pushEnter()
        },
        exitTransition = {
            if (targetState.isTab) fadeOut(MiraMotion.exit()) else pushExit()
        },
        popEnterTransition = {
            if (initialState.isTab) fadeIn(MiraMotion.enter(MiraMotion.STANDARD_MS)) else popEnter()
        },
        popExitTransition = {
            if (initialState.isTab) fadeOut(MiraMotion.exit()) else popExit()
        },
    ) {
        composable(MiraRoutes.ONBOARDING) {
            OnboardingScreen(
                onFinished = { navController.finishOnboarding() },
                onOpenVoiceSetup = { navController.navigate(MiraRoutes.VOICE_SETUP) },
            )
        }

        composable(MiraRoutes.HOME) {
            HomeScreen(
                onOpenMira = { navController.switchTab(MiraTab.MIRA) },
                onOpenTasks = { navController.switchTab(MiraTab.TASKS) },
                onOpenSearch = onOpenSearch,
                onOpenSettings = { navController.navigate(MiraRoutes.SETTINGS) },
                onOpenSalah = { navController.navigate(MiraRoutes.SALAH) },
                onOpenQuran = { navController.navigate(MiraRoutes.QURAN) },
                // A row or an area tile goes to the area's own screen — each area is one tap from
                // Home's own grid, so there is no second hub to route through.
                onOpenArea = { area -> navController.navigate(area.route) },
            )
        }
        composable(MiraRoutes.MIRA) {
            MiraScreen(onOpenSettings = { navController.navigate(MiraRoutes.SETTINGS) })
        }
        // TasksScreen belongs to the list, and it does not yet report which task was opened.
        // The editor is reached from the "new task" entry below until it takes an `onOpenTask`
        // callback; the route and the screen behind it are already here and wired.
        composable(MiraRoutes.TASKS) {
            TasksScreen(onOpenTask = { id -> navController.navigate(MiraRoutes.taskDetail(id)) })
        }
        composable(MiraRoutes.VAULT) { VaultScreen() }

        composable(
            route = MiraRoutes.TASK_DETAIL,
            arguments = listOf(navArgument(MiraRoutes.TASK_ID_ARG) { type = NavType.LongType }),
        ) {
            TaskDetailScreen(onBack = { navController.popBackStack() })
        }

        // Notes, its editor and the handwriting canvas. Registered BEFORE MiraArea.NOTES flips to
        // isBuilt = true, in that order: the flag is what removes the planned-area fallback, so
        // flipping it first would leave the hub's own row navigating to a route with no
        // destination (MiraDestination says so, and section 12 calls that a dead end).
        composable(MiraRoutes.NOTES) {
            NotesScreen(
                onOpenNote = { id -> navController.navigate(MiraRoutes.noteDetail(id)) },
                onNewNote = { navController.navigate(MiraRoutes.noteDetail()) },
                onOpenVoiceNotes = { navController.navigate(MiraRoutes.VOICE_NOTES) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = MiraRoutes.NOTE_DETAIL,
            arguments = listOf(navArgument(MiraRoutes.NOTE_ID_ARG) { type = NavType.LongType }),
        ) { entry ->
            val noteId = entry.arguments?.getLong(MiraRoutes.NOTE_ID_ARG) ?: MiraRoutes.NEW_NOTE_ID
            NoteDetailScreen(
                onBack = { navController.popBackStack() },
                onAskMira = { navController.switchTab(MiraTab.MIRA) },
                onOpenInkPage = { pageId -> navController.navigate(MiraRoutes.inkPage(noteId, pageId)) },
                onNewInkPage = {
                    navController.navigate(MiraRoutes.inkPage(noteId, MiraRoutes.NEW_INK_PAGE_ID))
                },
            )
        }

        composable(
            route = MiraRoutes.INK_PAGE,
            arguments = listOf(
                navArgument(MiraRoutes.NOTE_ID_ARG) { type = NavType.LongType },
                navArgument(MiraRoutes.INK_PAGE_ID_ARG) { type = NavType.LongType },
            ),
        ) { entry ->
            val noteId = entry.arguments?.getLong(MiraRoutes.NOTE_ID_ARG) ?: MiraRoutes.NEW_NOTE_ID
            val pageId = entry.arguments?.getLong(MiraRoutes.INK_PAGE_ID_ARG)
                ?: MiraRoutes.NEW_INK_PAGE_ID
            InkCanvasScreen(
                // Both sentinels become absences here and nowhere else, so neither the canvas nor
                // its view model has to know that zero means anything. A zero note is Quick Ink
                // started from a shortcut: no note exists yet, and the canvas mints one when Abu
                // first saves.
                noteId = noteId.takeIf { it != MiraRoutes.NEW_NOTE_ID },
                pageId = pageId.takeIf { it != MiraRoutes.NEW_INK_PAGE_ID },
                onClose = { navController.popBackStack() },
            )
        }

        composable(MiraRoutes.ALARMS) {
            AlarmsScreen(onBack = { navController.popBackStack() })
        }
        composable(MiraRoutes.TIMERS) {
            TimersScreen(onBack = { navController.popBackStack() })
        }
        composable(MiraRoutes.INVOICES) {
            AreaLockGate(protectInvoices, areaLock(words.areaTitle(MiraArea.INVOICES))) {
                InvoicesScreen(onBack = { navController.popBackStack() })
            }
        }
        composable(MiraRoutes.MONEY) {
            AreaLockGate(protectMoney, areaLock(words.areaTitle(MiraArea.MONEY))) {
                MoneyScreen(onBack = { navController.popBackStack() })
            }
        }
        composable(MiraRoutes.REPORTS) {
            ReportsScreen(onNavigateUp = { navController.popBackStack() })
        }
        composable(MiraRoutes.SALAH) {
            SalahScreen(
                onBack = { navController.popBackStack() },
                onOpenMonth = { navController.navigate(MiraRoutes.SALAH_MONTH) },
                onOpenSettings = { navController.navigate(MiraRoutes.SALAH_SETTINGS) },
                onOpenQuran = { navController.navigate(MiraRoutes.QURAN) },
            )
        }
        composable(MiraRoutes.SALAH_MONTH) {
            SalahMonthScreen(onBack = { navController.popBackStack() })
        }
        composable(MiraRoutes.SALAH_SETTINGS) {
            SalahSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(MiraRoutes.QURAN) {
            QuranScreen(onBack = { navController.popBackStack() })
        }
        composable(MiraRoutes.FILES) {
            FilesScreen(
                onNavigateUp = { navController.popBackStack() },
                onOpenScanner = { navController.navigate(MiraRoutes.SCANNER) },
                // The question Abu tapped — "analyse this document" — rides onto the Mira tab and
                // lands in the composer, instead of being thrown away at the tab switch (§12).
                onAskMira = { prompt ->
                    onAskMira(prompt)
                    navController.switchTab(MiraTab.MIRA)
                },
            )
        }
        composable(MiraRoutes.SCANNER) {
            ScannerScreen(
                onNavigateUp = { navController.popBackStack() },
                onAskMira = { prompt ->
                    onAskMira(prompt)
                    navController.switchTab(MiraTab.MIRA)
                },
            )
        }
        composable(MiraRoutes.MEMORY) {
            AreaLockGate(protectMemory, areaLock(words.areaTitle(MiraArea.MEMORY))) {
                MemoryScreen(
                    onNavigateUp = { navController.popBackStack() },
                )
            }
        }
        composable(MiraRoutes.AUTOMATIONS) {
            val context = androidx.compose.ui.platform.LocalContext.current
            AutomationsScreen(
                onNavigateUp = { navController.popBackStack() },
                onOpenPlaces = { navController.navigate(MiraRoutes.PLACES) },
                onEnableAccessibility = { context.openAccessibilitySettings() },
                onEnableNotificationAccess = { context.openNotificationListenerSettings() },
            )
        }
        composable(MiraRoutes.VOICE_NOTES) {
            VoiceNotesScreen(
                onNavigateUp = { navController.popBackStack() },
            )
        }
        composable(MiraRoutes.CALENDAR) {
            MeetingsScreen(
                onNavigateUp = { navController.popBackStack() },
            )
        }
        composable(MiraRoutes.BACKUP) {
            AreaLockGate(protectBackups, areaLock(words.backupAreaName)) {
                BackupScreen(
                    onNavigateUp = { navController.popBackStack() },
                )
            }
        }
        composable(MiraRoutes.PROJECTS) {
            ProjectsScreen(
                onNavigateUp = { navController.popBackStack() },
            )
        }
        composable(MiraRoutes.CLIENTS) {
            ClientsScreen(
                onNavigateUp = { navController.popBackStack() },
            )
        }
        composable(MiraRoutes.PRIVACY_DASHBOARD) {
            PrivacyDashboardScreen(
                onNavigateUp = { navController.popBackStack() },
            )
        }
        // The areas Mira does not have yet are registered from the enum rather than written out
        // one by one, so shipping one is a single flag in MiraDestination and nothing else moves.
        MiraArea.entries.filterNot { it.isBuilt }.forEach { area ->
            composable(area.route) {
                PlannedAreaScreen(
                    area = area,
                    language = language,
                    onAskMira = { navController.switchTab(MiraTab.MIRA) },
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable(MiraRoutes.SETTINGS) {
            SettingsScreen(
                onOpenVoiceSetup = { navController.navigate(MiraRoutes.VOICE_SETUP) },
                onOpenAssistantSetup = { navController.navigate(MiraRoutes.ASSISTANT_SETUP) },
                onOpenPlaces = { navController.navigate(MiraRoutes.PLACES) },
                onOpenPrivacy = { navController.navigate(MiraRoutes.PRIVACY_DASHBOARD) },
                onOpenBackup = { navController.navigate(MiraRoutes.BACKUP) },
                onOpenPermissions = { navController.navigate(MiraRoutes.PERMISSIONS) },
                onOpenActivityLog = { navController.navigate(MiraRoutes.ACTIVITY_LOG) },
                onOpenLicenses = { navController.navigate(MiraRoutes.LICENSES) },
                onOpenSalahSettings = { navController.navigate(MiraRoutes.SALAH_SETTINGS) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(MiraRoutes.VOICE_SETUP) {
            VoiceSetupScreen(onBack = { navController.popBackStack() })
        }
        composable(MiraRoutes.ASSISTANT_SETUP) {
            AssistantSetupScreen(onBack = { navController.popBackStack() })
        }
        composable(MiraRoutes.PLACES) {
            PlacesScreen(onBack = { navController.popBackStack() })
        }
        composable(MiraRoutes.PERMISSIONS) {
            PermissionsScreen(onBack = { navController.popBackStack() })
        }
        composable(MiraRoutes.ACTIVITY_LOG) {
            ActivityLogScreen(onBack = { navController.popBackStack() })
        }
        composable(MiraRoutes.LICENSES) {
            LicensesScreen(onBack = { navController.popBackStack() })
        }
    }
}

/**
 * Where each entry point lands.
 *
 * An unknown action — including the plain launcher intent — resolves to null so that opening Mira
 * normally leaves the back stack exactly where Abu left it.
 */
private fun tabForEntryAction(action: String): MiraTab? = when (action) {
    MiraShortcuts.ACTION_TALK -> MiraTab.MIRA
    MiraShortcuts.ACTION_NEW_TASK -> MiraTab.TASKS
    MiraShortcuts.ACTION_TODAY -> MiraTab.HOME
    // Notes is not a tab, so both note entries rest on Home: Back from the editor or the canvas
    // lands somewhere Abu recognises instead of closing Mira.
    MiraShortcuts.ACTION_NEW_NOTE, MiraShortcuts.ACTION_QUICK_INK -> MiraTab.HOME
    // Invoices and the scanner are not tabs either, so they rest on Home and the screen is pushed
    // over it — Back from an invoice or a receipt scan lands somewhere Abu recognises.
    MiraShortcuts.ACTION_NEW_INVOICE, MiraShortcuts.ACTION_SCAN_RECEIPT -> MiraTab.HOME
    // A client reminder rests on Home too, then pushes the client it names.
    MiraShortcuts.ACTION_CLIENT -> MiraTab.HOME
    else -> null
}

/**
 * Hand the gate its words. [AppLockWording] is the shell's internal phrasebook entry;
 * [AppLockStrings] is the gate's own public, language-free input, and this is the one seam between
 * them so neither has to know the other's shape.
 */
private fun AppLockWording.toGateStrings(): AppLockStrings = AppLockStrings(
    title = title,
    subtitle = subtitle,
    unlock = unlock,
    promptTitle = promptTitle,
    promptSubtitle = promptSubtitle,
    lockedOut = lockedOut,
    cannotVerify = cannotVerify,
    notEnrolled = notEnrolled,
    enroll = enroll,
)

/**
 * The tab a destination belongs to, or null when it is not one of the four.
 *
 * The shell's single route-to-tab answer. Both questions the frame asks — which item the bar lights
 * and whether a forward navigation is a push or a sideways move — resolve through here, so they
 * cannot drift apart into two lists that disagree about what a tab is.
 */
private fun NavDestination?.tabOrNull(): MiraTab? =
    MiraTab.entries.firstOrNull { tab -> this?.hierarchy?.any { it.route == tab.route } == true }

/**
 * The tab the bottom bar is standing on, or null when there should be no bar.
 *
 * Read from the **lowest** entry still on screen rather than from the current one, and that is the
 * whole point of it. Whether the bar exists changes the height of everything above it, so dropping
 * it the instant `navigate()` is called re-measures both screens on the first frame of a push —
 * and a slide, unlike a cross-fade, has nothing to hide that under. Abu feels it on every Home to
 * Areas, every Home to Settings, dozens of times a day.
 *
 * `visibleEntries` holds every entry that is still drawn, which during a transition is more than
 * one. Navigation lists an entry that is finishing its exit transition ahead of the back stack, so
 * the first of them is the tab being covered during a push and the pushed screen being dismissed
 * during a pop. Taking that one holds the bar still for the length of the motion in both
 * directions and lets it change on a frame where nothing else is moving.
 *
 * Which item is LIT does not come from here — see [MiraShell] — because a tap has to be answered
 * on the frame it happens, not when the screen it opened has finished arriving.
 */
@Composable
private fun rememberBottomBarTab(navController: NavHostController): MiraTab? {
    val visibleEntries by navController.visibleEntries.collectAsState()
    return remember(visibleEntries) { visibleEntries.firstOrNull()?.destination.tabOrNull() }
}

/**
 * Whether a destination sits beside the tabs or over them.
 *
 * [MiraTab] is the only list of what belongs in the bottom bar, so it is also the only list of
 * what a forward navigation treats as a sideways move. Everything else — the hub, an area,
 * settings, an editor — arrives over the top and is therefore pushed. Deriving it means a new
 * pushed screen is registered with a plain `composable` and moves correctly with no second list
 * to remember to add it to.
 */
private val NavBackStackEntry.isTab: Boolean
    get() = destination.tabOrNull() != null

/**
 * A push, rather than a slide.
 *
 * The screen arriving comes the full width from the trailing edge; the screen it covers travels a
 * short way in the same direction and dims. That parallax is the whole difference between two
 * views sliding past each other and one surface being laid over another — without it, the screen
 * underneath simply vanishes and the new one looks like a card flying in over nothing.
 *
 * The travel is a spring and the dimming is a curve, which is the rule in [MiraMotion]: a spring
 * carries its velocity through an interruption, so a push Abu reverses mid-flight follows his
 * thumb, while a fade has no momentum to preserve and only flickers if it is sprung.
 *
 * It is [MiraMotion.move] and not [MiraMotion.moveGently], although the latter names a screen.
 * Neither spring is given a `visibilityThreshold`, so Compose applies its default of 0.01 — which
 * for the `IntOffset` these slides animate is a hundredth of a pixel. The softer spring keeps
 * running for roughly a third of a second after its last visible pixel, and a back stack that
 * cannot be driven for that long is worse than one that arrives a little faster.
 */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushEnter(): EnterTransition =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = MiraMotion.move(),
    )

private fun AnimatedContentTransitionScope<NavBackStackEntry>.pushExit(): ExitTransition =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.Start,
        animationSpec = MiraMotion.move(),
        targetOffset = { full -> (full * UNDERLAY_TRAVEL).toInt() },
    ) + fadeOut(MiraMotion.fade(), targetAlpha = UNDERLAY_ALPHA)

/** The mirror of [pushExit]: the screen underneath comes back up to meet the edge it left. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = MiraMotion.move(),
        initialOffset = { full -> (full * UNDERLAY_TRAVEL).toInt() },
    ) + fadeIn(MiraMotion.fade(), initialAlpha = UNDERLAY_ALPHA)

/** The mirror of [pushEnter]: back is the same distance, travelled the other way. */
private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = MiraMotion.move(),
    )

/**
 * How far the screen underneath travels, as a share of its own width.
 *
 * Roughly a third. Less and the parallax is not read as depth; more and the screen underneath is
 * halfway off the display by the time the new one has covered it, which draws the eye to the
 * thing that is leaving instead of the thing that has arrived.
 */
private const val UNDERLAY_TRAVEL = 0.3f

/**
 * How far it dims while it is behind. Never to nothing — it is still a surface under another
 * surface, and a screen that fades to black leaves the new one floating on emptiness.
 */
private const val UNDERLAY_ALPHA = 0.6f

/**
 * Onboarding is a one-way door. Clearing it from the back stack is what makes Back from Home
 * leave the app instead of walking Abu through the permission screens again.
 */
private fun NavHostController.finishOnboarding() {
    navigate(MiraRoutes.HOME) {
        popUpTo(MiraRoutes.ONBOARDING) { inclusive = true }
        launchSingleTop = true
    }
}

/**
 * Tab switching keeps one back-stack entry per tab and restores where Abu left off.
 *
 * It pops back to Home by route rather than to the graph's start destination, because on a clean
 * install the graph starts at onboarding — which is gone by the time any tab can be tapped, and
 * popping to a destination that is no longer on the stack silently pops nothing, leaving the tabs
 * piled on top of one another.
 */
private fun NavHostController.switchTab(tab: MiraTab) {
    navigate(tab.route) {
        popUpTo(MiraRoutes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
