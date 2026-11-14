package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Swipe
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.network.ConnectivityObserver
import com.example.data.repository.ArrowSwipeRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.attemptAutoSignIn
import com.example.ui.auth.signOutFromGoogle
import com.example.ui.components.OfflineLockOverlay
import com.example.ui.components.OnlineStatusChip
import com.example.ui.components.PersistentGemBalanceBadge
import com.example.ui.demo.DemoExperience
import com.example.ui.screens.ArenaScreen
import com.example.ui.screens.ShortcutsScreen
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.DeepCyanContainer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.GemGold
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ArrowSwipeViewModel
import com.example.ui.viewmodel.GemBalanceUiState
import com.example.ui.viewmodel.LauncherDestination
import com.example.ui.viewmodel.LauncherFeedbackEvent
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private const val DEMO_PREFS = "arrow_swipe_demo_prefs"
private const val KEY_DEMO_USED = "has_used_one_time_demo"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                ArrowSwipeRoot()
            }
        }
    }
}

@Composable
fun ArrowSwipeRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) {
        context.applicationContext.getSharedPreferences(DEMO_PREFS, Context.MODE_PRIVATE)
    }
    var hasUsedDemo by remember { mutableStateOf(prefs.getBoolean(KEY_DEMO_USED, false)) }
    var isDemoActive by remember { mutableStateOf(false) }

    val connectivityObserver = remember(context) { ConnectivityObserver(context) }
    val isOnline by connectivityObserver.observeOnlineStatus().collectAsStateWithLifecycle(
        initialValue = connectivityObserver.checkIsOnlineNow()
    )
    var manualOnlineCheckTick by remember { mutableStateOf(0) }
    val effectiveOnline = remember(isOnline, manualOnlineCheckTick) {
        isOnline || connectivityObserver.checkIsOnlineNow()
    }

    var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

    DisposableEffect(Unit) {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            currentUser = auth.currentUser
            if (auth.currentUser != null) {
                isDemoActive = false
            }
        }
        Firebase.auth.addAuthStateListener(listener)
        onDispose {
            Firebase.auth.removeAuthStateListener(listener)
        }
    }

    LaunchedEffect(effectiveOnline) {
        if (effectiveOnline && Firebase.auth.currentUser == null) {
            attemptAutoSignIn(
                context = context,
                credentialManager = CredentialManager.create(context),
                onAuthSuccess = { currentUser = Firebase.auth.currentUser },
                onUnauthenticated = {},
                scope = scope
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        val user = currentUser
        when {
            user != null -> {
                val uid = user.uid
                val displayName = user.displayName ?: "Arrow Player"
                key(uid) {
                    ArrowSwipeMainContent(
                        currentUserId = uid,
                        initialDisplayName = displayName,
                        isOnline = effectiveOnline,
                        connectivityObserver = connectivityObserver
                    )
                }
            }
            isDemoActive -> {
                DemoExperience(
                    isOnline = effectiveOnline,
                    onAuthSuccess = {
                        isDemoActive = false
                        currentUser = Firebase.auth.currentUser
                    },
                    onExitDemo = { isDemoActive = false }
                )
            }
            else -> {
                AuthScreen(
                    isOnline = effectiveOnline,
                    hasUsedDemo = hasUsedDemo,
                    onStartDemo = {
                        if (!hasUsedDemo && effectiveOnline) {
                            prefs.edit().putBoolean(KEY_DEMO_USED, true).apply()
                            hasUsedDemo = true
                            isDemoActive = true
                        }
                    },
                    onAuthSuccess = { currentUser = Firebase.auth.currentUser }
                )
            }
        }

        // Strict Online-Only Lock Overlay: blocks all offline gameplay & navigation
        if (!effectiveOnline) {
            OfflineLockOverlay(
                onRetryCheck = { manualOnlineCheckTick++ }
            )
        }
    }
}

@Composable
fun ArrowSwipeMainContent(
    currentUserId: String,
    initialDisplayName: String,
    isOnline: Boolean,
    connectivityObserver: ConnectivityObserver
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val credentialManager = remember(context) { CredentialManager.create(context) }

    val viewModel: ArrowSwipeViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                ArrowSwipeViewModel(
                    repository = ArrowSwipeRepository(db),
                    connectivityObserver = connectivityObserver,
                    currentUserId = currentUserId,
                    initialDisplayName = initialDisplayName
                )
            }
        }
    )

    val profileState by viewModel.profileState.collectAsStateWithLifecycle()
    val gemBalanceState by viewModel.gemBalanceState.collectAsStateWithLifecycle()
    val shortcutsState by viewModel.shortcutsState.collectAsStateWithLifecycle()
    val runsState by viewModel.recentRunsState.collectAsStateWithLifecycle()
    val arenaState by viewModel.arenaState.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.feedbackEvents.collect { event ->
            when (event) {
                is LauncherFeedbackEvent.Banner -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                LauncherFeedbackEvent.HapticPulse -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                LauncherFeedbackEvent.OpenSettingsIntent -> {
                    runCatching {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                LauncherFeedbackEvent.OpenBrowserIntent -> {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
                is LauncherFeedbackEvent.ShareStatsIntent -> {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "I've earned ${event.gems} Gems across ${event.runs} completed runs in Arrow Swipe!"
                        )
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(sendIntent, "Share Arrow Swipe Score"))
                    }
                }
            }
        }
    }

    val profile = (profileState as? UiState.Success)?.data
    val shortcuts = (shortcutsState as? UiState.Success)?.data ?: ArrowSwipeRepository.defaultShortcuts()
    val runs = (runsState as? UiState.Success)?.data ?: emptyList()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CarbonSurface)
                    .border(width = 0.5.dp, color = SlateBorder)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.no_level_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PersistentGemBalanceBadge(
                        state = gemBalanceState,
                        onClick = {
                            if (gemBalanceState is GemBalanceUiState.Error) {
                                viewModel.refreshGemBalanceFromServer()
                            } else {
                                viewModel.selectTab(LauncherDestination.VAULT)
                            }
                        }
                    )

                    OnlineStatusChip(isOnline = isOnline)
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = CarbonSurface,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = activeTab == LauncherDestination.ARENA,
                    onClick = { viewModel.selectTab(LauncherDestination.ARENA) },
                    icon = {
                        Icon(
                            imageVector = if (activeTab == LauncherDestination.ARENA) Icons.Rounded.NearMe else Icons.Outlined.NearMe,
                            contentDescription = "Arena"
                        )
                    },
                    label = { Text(stringResource(R.string.nav_arena)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ElectricCyan,
                        selectedTextColor = ElectricCyan,
                        indicatorColor = DeepCyanContainer
                    ),
                    modifier = Modifier.testTag("nav_arena_tab")
                )
                NavigationBarItem(
                    selected = activeTab == LauncherDestination.SHORTCUTS,
                    onClick = { viewModel.selectTab(LauncherDestination.SHORTCUTS) },
                    icon = {
                        Icon(
                            imageVector = if (activeTab == LauncherDestination.SHORTCUTS) Icons.Rounded.Swipe else Icons.Outlined.Swipe,
                            contentDescription = "Shortcuts"
                        )
                    },
                    label = { Text(stringResource(R.string.nav_shortcuts)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = ElectricCyan,
                        selectedTextColor = ElectricCyan,
                        indicatorColor = DeepCyanContainer
                    ),
                    modifier = Modifier.testTag("nav_shortcuts_tab")
                )
                NavigationBarItem(
                    selected = activeTab == LauncherDestination.VAULT,
                    onClick = { viewModel.selectTab(LauncherDestination.VAULT) },
                    icon = {
                        Icon(
                            imageVector = if (activeTab == LauncherDestination.VAULT) Icons.Rounded.Diamond else Icons.Outlined.Diamond,
                            contentDescription = "Vault"
                        )
                    },
                    label = { Text(stringResource(R.string.nav_vault)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = GemGold,
                        selectedTextColor = GemGold,
                        indicatorColor = DeepCyanContainer
                    ),
                    modifier = Modifier.testTag("nav_vault_tab")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (profileState is UiState.Loading) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(color = ElectricCyan)
                    Text(
                        text = "Syncing cloud profile & shortcuts...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                when (activeTab) {
                    LauncherDestination.ARENA -> ArenaScreen(
                        arenaState = arenaState,
                        activeSkinId = profile?.activeSkinId ?: "cyan_pulse",
                        shortcuts = shortcuts,
                        onLaunchArrow = viewModel::launchArrow,
                        onRestartRun = viewModel::startFreshRun,
                        onTriggerShortcut = viewModel::triggerGestureShortcut
                    )
                    LauncherDestination.SHORTCUTS -> ShortcutsScreen(
                        shortcuts = shortcuts,
                        activeSkinId = profile?.activeSkinId ?: "cyan_pulse",
                        onSaveShortcut = viewModel::saveShortcut,
                        onTriggerShortcut = viewModel::triggerGestureShortcut,
                        onBackToArena = { viewModel.selectTab(LauncherDestination.ARENA) }
                    )
                    LauncherDestination.VAULT -> VaultScreen(
                        profile = profile,
                        recentRuns = runs,
                        onSelectOrUnlockSkin = viewModel::unlockOrEquipSkin,
                        onToggleHaptics = viewModel::toggleHaptics,
                        onSignOut = {
                            signOutFromGoogle(
                                credentialManager = credentialManager,
                                scope = scope
                            )
                        },
                        onBackToArena = { viewModel.selectTab(LauncherDestination.ARENA) }
                    )
                }
            }
        }
    }
}
