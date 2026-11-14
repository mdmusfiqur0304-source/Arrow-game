package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ArrowLaunchStyle
import com.example.data.model.ArrowSkinCatalog
import com.example.data.model.CompletedRun
import com.example.data.model.GestureShortcut
import com.example.data.model.ShortcutActionType
import com.example.data.model.SwipeDirection
import com.example.data.model.UserProfile
import com.example.data.network.ConnectivityObserver
import com.example.data.repository.ArrowSwipeRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.random.Random

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

sealed interface GemBalanceUiState {
    data object Loading : GemBalanceUiState
    data class Ready(
        val gems: Long,
        val recentDelta: Long = 0L,
        val isSyncing: Boolean = false
    ) : GemBalanceUiState
    data class Error(val message: String) : GemBalanceUiState
}

enum class LauncherDestination {
    ARENA,
    SHORTCUTS,
    VAULT
}

data class ArenaTarget(
    val id: Int,
    val normX: Float,
    val normY: Float,
    val radiusNorm: Float,
    val label: String,
    val driftSpeedX: Float,
    val driftSpeedY: Float
)

data class ActiveArrowFlight(
    val id: Long,
    val startX: Float,
    val startY: Float,
    val currentX: Float,
    val currentY: Float,
    val velX: Float,
    val velY: Float,
    val style: ArrowLaunchStyle,
    val trailPoints: List<Pair<Float, Float>>
)

data class HitBurstEffect(
    val id: Long,
    val normX: Float,
    val normY: Float,
    val progress: Float
)

data class ArenaRunState(
    val targetsCleared: Int = 0,
    val totalTargetsInRun: Int = TARGETS_PER_RUN,
    val shotsFired: Int = 0,
    val runStartedAtMs: Long = System.currentTimeMillis(),
    val lastCompletionDurationMs: Long = 0L,
    val lastAccuracyPercent: Int = 100,
    val isRunCompleted: Boolean = false,
    val isAwardingGems: Boolean = false,
    val gemsAwardedBannerVisible: Boolean = false,
    val currentTarget: ArenaTarget = createRandomTarget(1),
    val activeArrow: ActiveArrowFlight? = null,
    val hitBursts: List<HitBurstEffect> = emptyList(),
    val lastSwipeLabel: String = "Swipe anywhere to launch arrow"
) {
    val accuracyPercent: Int
        get() = if (shotsFired == 0) 100 else ((targetsCleared.toFloat() / shotsFired) * 100f).toInt().coerceIn(0, 100)

    companion object {
        const val TARGETS_PER_RUN = 6

        fun createRandomTarget(index: Int): ArenaTarget {
            val x = Random.nextFloat() * 0.64f + 0.18f
            val y = Random.nextFloat() * 0.42f + 0.14f
            val speedX = (Random.nextFloat() - 0.5f) * 0.34f
            val speedY = (Random.nextFloat() - 0.5f) * 0.16f
            return ArenaTarget(
                id = index,
                normX = x,
                normY = y,
                radiusNorm = 0.085f,
                label = "GATE $index",
                driftSpeedX = speedX,
                driftSpeedY = speedY
            )
        }
    }
}

sealed interface LauncherFeedbackEvent {
    data class Banner(val message: String, val isError: Boolean = false) : LauncherFeedbackEvent
    data object OpenSettingsIntent : LauncherFeedbackEvent
    data object OpenBrowserIntent : LauncherFeedbackEvent
    data class ShareStatsIntent(val gems: Long, val runs: Long) : LauncherFeedbackEvent
    data object HapticPulse : LauncherFeedbackEvent
}

class ArrowSwipeViewModel(
    private val repository: ArrowSwipeRepository,
    private val connectivityObserver: ConnectivityObserver,
    private val currentUserId: String,
    initialDisplayName: String
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = connectivityObserver.observeOnlineStatus()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = connectivityObserver.checkIsOnlineNow()
        )

    val profileState: StateFlow<UiState<UserProfile?>> = repository.observeProfile(currentUserId)
        .map<UserProfile?, UiState<UserProfile?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing profile", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load profile"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val shortcutsState: StateFlow<UiState<List<GestureShortcut>>> = repository.observeShortcuts(currentUserId)
        .map<List<GestureShortcut>, UiState<List<GestureShortcut>>> { list ->
            val sorted = SwipeDirection.entries.mapNotNull { dir ->
                list.firstOrNull { it.direction == dir.id }
            }
            UiState.Success(if (sorted.isNotEmpty()) sorted else list)
        }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing shortcuts", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load shortcuts"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val recentRunsState: StateFlow<UiState<List<CompletedRun>>> = repository.observeRecentRuns(currentUserId)
        .map<List<CompletedRun>, UiState<List<CompletedRun>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing runs", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load run history"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    private val _gemBalanceState = MutableStateFlow<GemBalanceUiState>(GemBalanceUiState.Loading)
    val gemBalanceState: StateFlow<GemBalanceUiState> = _gemBalanceState.asStateFlow()

    private val _activeTab = MutableStateFlow(LauncherDestination.ARENA)
    val activeTab: StateFlow<LauncherDestination> = _activeTab.asStateFlow()

    private val _arenaState = MutableStateFlow(ArenaRunState())
    val arenaState: StateFlow<ArenaRunState> = _arenaState.asStateFlow()

    private val _feedbackEvents = MutableSharedFlow<LauncherFeedbackEvent>(extraBufferCapacity = 16)
    val feedbackEvents = _feedbackEvents.asSharedFlow()

    private var simulationJob: Job? = null
    private var deltaClearJob: Job? = null

    init {
        viewModelScope.launch {
            if (connectivityObserver.checkIsOnlineNow()) {
                val initResult = repository.ensureUserInitialized(initialDisplayName)
                if (initResult.isSuccess) {
                    refreshGemBalanceFromServer()
                } else {
                    _gemBalanceState.value = GemBalanceUiState.Error(
                        initResult.exceptionOrNull()?.message ?: "Failed to initialize profile"
                    )
                }
            }
        }
        observeProfileGemUpdates()
        startSimulationLoop()
    }

    private fun observeProfileGemUpdates() {
        viewModelScope.launch {
            profileState.collect { state ->
                when (state) {
                    is UiState.Success -> {
                        val profile = state.data
                        if (profile != null) {
                            val prevReady = _gemBalanceState.value as? GemBalanceUiState.Ready
                            val delta = if (prevReady != null && profile.gems > prevReady.gems) {
                                profile.gems - prevReady.gems
                            } else {
                                prevReady?.recentDelta ?: 0L
                            }
                            _gemBalanceState.value = GemBalanceUiState.Ready(
                                gems = profile.gems,
                                recentDelta = delta,
                                isSyncing = false
                            )
                            if (delta > 0L && (prevReady == null || profile.gems > prevReady.gems)) {
                                scheduleDeltaClear()
                            }
                        }
                    }
                    is UiState.Error -> {
                        if (_gemBalanceState.value !is GemBalanceUiState.Ready) {
                            _gemBalanceState.value = GemBalanceUiState.Error(state.message)
                        }
                    }
                    UiState.Loading -> Unit
                }
            }
        }
    }

    fun refreshGemBalanceFromServer() {
        if (!connectivityObserver.checkIsOnlineNow()) return
        viewModelScope.launch {
            val current = _gemBalanceState.value
            if (current is GemBalanceUiState.Ready) {
                _gemBalanceState.value = current.copy(isSyncing = true)
            } else {
                _gemBalanceState.value = GemBalanceUiState.Loading
            }
            val result = repository.fetchGemBalanceFromServer(currentUserId)
            result.onSuccess { serverGems ->
                val prevReady = _gemBalanceState.value as? GemBalanceUiState.Ready
                val delta = if (prevReady != null && serverGems > prevReady.gems) {
                    serverGems - prevReady.gems
                } else {
                    prevReady?.recentDelta ?: 0L
                }
                _gemBalanceState.value = GemBalanceUiState.Ready(
                    gems = serverGems,
                    recentDelta = delta,
                    isSyncing = false
                )
                if (delta > 0L) scheduleDeltaClear()
            }.onFailure { err ->
                if (current !is GemBalanceUiState.Ready) {
                    _gemBalanceState.value = GemBalanceUiState.Error(
                        err.message ?: "Failed to fetch gems"
                    )
                } else {
                    _gemBalanceState.value = current.copy(isSyncing = false)
                }
            }
        }
    }

    private fun scheduleDeltaClear() {
        deltaClearJob?.cancel()
        deltaClearJob = viewModelScope.launch {
            delay(3000L)
            val current = _gemBalanceState.value
            if (current is GemBalanceUiState.Ready && current.recentDelta > 0L) {
                _gemBalanceState.value = current.copy(recentDelta = 0L)
            }
        }
    }

    fun selectTab(destination: LauncherDestination) {
        _activeTab.value = destination
    }

    fun retryInitialization(displayName: String) {
        viewModelScope.launch {
            if (connectivityObserver.checkIsOnlineNow()) {
                val res = repository.ensureUserInitialized(displayName)
                if (res.isFailure) {
                    _feedbackEvents.tryEmit(
                        LauncherFeedbackEvent.Banner(
                            res.exceptionOrNull()?.message ?: "Sync failed",
                            isError = true
                        )
                    )
                }
            } else {
                _feedbackEvents.tryEmit(
                    LauncherFeedbackEvent.Banner("Internet connection required.", isError = true)
                )
            }
        }
    }

    private fun startSimulationLoop() {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            var lastTime = System.currentTimeMillis()
            while (true) {
                delay(16L)
                val now = System.currentTimeMillis()
                val dt = ((now - lastTime).coerceIn(1L, 50L)) / 1000f
                lastTime = now

                if (!isOnline.value) continue

                val state = _arenaState.value
                if (state.isRunCompleted) {
                    if (state.hitBursts.isNotEmpty()) {
                        _arenaState.update { cur ->
                            cur.copy(
                                hitBursts = cur.hitBursts
                                    .map { it.copy(progress = it.progress + dt * 2.4f) }
                                    .filter { it.progress < 1f }
                            )
                        }
                    }
                    continue
                }

                // Drift target smoothly within arena bounds
                val t = state.currentTarget
                var nx = t.normX + t.driftSpeedX * dt
                var ny = t.normY + t.driftSpeedY * dt
                var sx = t.driftSpeedX
                var sy = t.driftSpeedY
                if (nx < 0.14f || nx > 0.86f) {
                    sx = -sx
                    nx = nx.coerceIn(0.14f, 0.86f)
                }
                if (ny < 0.12f || ny > 0.56f) {
                    sy = -sy
                    ny = ny.coerceIn(0.12f, 0.56f)
                }
                val updatedTarget = t.copy(normX = nx, normY = ny, driftSpeedX = sx, driftSpeedY = sy)

                // Advance active arrow flight
                val arrow = state.activeArrow
                var updatedArrow: ActiveArrowFlight? = null
                var hitDetected = false

                if (arrow != null) {
                    val nextX = arrow.currentX + arrow.velX * dt
                    val nextY = arrow.currentY + arrow.velY * dt
                    val dist = hypot(nextX - updatedTarget.normX, nextY - updatedTarget.normY)
                    if (dist <= updatedTarget.radiusNorm + 0.032f) {
                        hitDetected = true
                    } else if (nextX in -0.05f..1.05f && nextY in -0.05f..1.05f) {
                        val nextTrail = (arrow.trailPoints + (nextX to nextY)).takeLast(14)
                        updatedArrow = arrow.copy(
                            currentX = nextX,
                            currentY = nextY,
                            trailPoints = nextTrail
                        )
                    }
                }

                val nextBursts = state.hitBursts
                    .map { it.copy(progress = it.progress + dt * 2.5f) }
                    .filter { it.progress < 1f }

                if (hitDetected) {
                    val newCleared = state.targetsCleared + 1
                    val burstsWithHit = nextBursts + HitBurstEffect(
                        id = System.nanoTime(),
                        normX = updatedTarget.normX,
                        normY = updatedTarget.normY,
                        progress = 0f
                    )
                    maybeEmitHaptic()

                    if (newCleared >= state.totalTargetsInRun) {
                        val duration = (System.currentTimeMillis() - state.runStartedAtMs).coerceAtLeast(500L)
                        val acc = if (state.shotsFired == 0) 100 else ((newCleared.toFloat() / state.shotsFired) * 100f).toInt().coerceIn(0, 100)
                        _arenaState.update {
                            it.copy(
                                targetsCleared = newCleared,
                                activeArrow = null,
                                hitBursts = burstsWithHit,
                                isRunCompleted = true,
                                lastCompletionDurationMs = duration,
                                lastAccuracyPercent = acc,
                                lastSwipeLabel = "Run Complete! Awarding +5 Gems..."
                            )
                        }
                        awardGemsForCompletedRun(newCleared, acc, duration)
                    } else {
                        _arenaState.update {
                            it.copy(
                                targetsCleared = newCleared,
                                currentTarget = ArenaRunState.createRandomTarget(newCleared + 1),
                                activeArrow = null,
                                hitBursts = burstsWithHit,
                                lastSwipeLabel = "Gate $newCleared/${state.totalTargetsInRun} cleared!"
                            )
                        }
                    }
                } else {
                    _arenaState.update {
                        it.copy(
                            currentTarget = updatedTarget,
                            activeArrow = updatedArrow,
                            hitBursts = nextBursts
                        )
                    }
                }
            }
        }
    }

    fun launchArrow(startNormX: Float, startNormY: Float, dirX: Float, dirY: Float) {
        if (!isOnline.value) {
            _feedbackEvents.tryEmit(
                LauncherFeedbackEvent.Banner("Offline: Reconnect to launch arrows.", isError = true)
            )
            return
        }
        val state = _arenaState.value
        if (state.isRunCompleted) return

        val mag = hypot(dirX, dirY)
        if (mag < 0.01f) return

        val unitX = dirX / mag
        val unitY = dirY / mag
        val speed = 1.65f

        val swipeDir = SwipeDirection.fromVector(unitX, unitY)
        val style = resolveArrowStyleForDirection(swipeDir)

        val flight = ActiveArrowFlight(
            id = System.nanoTime(),
            startX = startNormX.coerceIn(0.08f, 0.92f),
            startY = startNormY.coerceIn(0.58f, 0.92f),
            currentX = startNormX.coerceIn(0.08f, 0.92f),
            currentY = startNormY.coerceIn(0.58f, 0.92f),
            velX = unitX * speed,
            velY = unitY * speed,
            style = style,
            trailPoints = listOf(startNormX.coerceIn(0.08f, 0.92f) to startNormY.coerceIn(0.58f, 0.92f))
        )

        maybeEmitHaptic()
        _arenaState.update {
            it.copy(
                shotsFired = it.shotsFired + 1,
                activeArrow = flight,
                lastSwipeLabel = "Launched ${swipeDir.displayName} (${style.title})"
            )
        }
    }

    fun startFreshRun() {
        if (!isOnline.value) {
            _feedbackEvents.tryEmit(
                LauncherFeedbackEvent.Banner("Internet connection required to start a run.", isError = true)
            )
            return
        }
        _arenaState.value = ArenaRunState(
            runStartedAtMs = System.currentTimeMillis(),
            currentTarget = ArenaRunState.createRandomTarget(1),
            lastSwipeLabel = "New run active — swipe to hit 6 gates!"
        )
    }

    private fun awardGemsForCompletedRun(targetsCleared: Int, accuracyPercent: Int, durationMs: Long) {
        if (!connectivityObserver.checkIsOnlineNow()) {
            _arenaState.update { it.copy(isAwardingGems = false) }
            _feedbackEvents.tryEmit(
                LauncherFeedbackEvent.Banner("Connection lost before syncing +5 Gems.", isError = true)
            )
            return
        }
        viewModelScope.launch {
            _arenaState.update { it.copy(isAwardingGems = true) }
            val currentReady = _gemBalanceState.value as? GemBalanceUiState.Ready
            if (currentReady != null) {
                _gemBalanceState.value = currentReady.copy(isSyncing = true)
            }
            val result = repository.completeRunAndAwardGems(targetsCleared, accuracyPercent, durationMs)
            _arenaState.update {
                it.copy(
                    isAwardingGems = false,
                    gemsAwardedBannerVisible = result.isSuccess
                )
            }
            if (result.isSuccess) {
                refreshGemBalanceFromServer()
                _feedbackEvents.tryEmit(
                    LauncherFeedbackEvent.Banner("+5 Gems added to your cloud vault!")
                )
            } else {
                if (currentReady != null) {
                    _gemBalanceState.value = currentReady.copy(isSyncing = false)
                }
                _feedbackEvents.tryEmit(
                    LauncherFeedbackEvent.Banner(
                        result.exceptionOrNull()?.message ?: "Failed to sync +5 Gems",
                        isError = true
                    )
                )
            }
        }
    }

    fun triggerGestureShortcut(direction: SwipeDirection) {
        if (!isOnline.value) {
            _feedbackEvents.tryEmit(
                LauncherFeedbackEvent.Banner("Internet connection required.", isError = true)
            )
            return
        }
        val list = (shortcutsState.value as? UiState.Success)?.data
            ?: ArrowSwipeRepository.defaultShortcuts()
        val shortcut = list.firstOrNull { it.direction == direction.id }
        if (shortcut == null || !shortcut.enabled) {
            _feedbackEvents.tryEmit(
                LauncherFeedbackEvent.Banner("${direction.displayName} shortcut is disabled.")
            )
            return
        }

        maybeEmitHaptic()
        val action = ShortcutActionType.fromId(shortcut.actionType)
        when (action) {
            ShortcutActionType.NAV_ARENA -> {
                _activeTab.value = LauncherDestination.ARENA
                _feedbackEvents.tryEmit(LauncherFeedbackEvent.Banner("Swipe ${direction.displayName} -> ${shortcut.label}"))
            }
            ShortcutActionType.NAV_SHORTCUTS -> {
                _activeTab.value = LauncherDestination.SHORTCUTS
                _feedbackEvents.tryEmit(LauncherFeedbackEvent.Banner("Swipe ${direction.displayName} -> ${shortcut.label}"))
            }
            ShortcutActionType.NAV_VAULT -> {
                _activeTab.value = LauncherDestination.VAULT
                _feedbackEvents.tryEmit(LauncherFeedbackEvent.Banner("Swipe ${direction.displayName} -> ${shortcut.label}"))
            }
            ShortcutActionType.QUICK_RESTART -> {
                _activeTab.value = LauncherDestination.ARENA
                startFreshRun()
                _feedbackEvents.tryEmit(LauncherFeedbackEvent.Banner("Quick Reset Run triggered"))
            }
            ShortcutActionType.OPEN_SETTINGS -> {
                _feedbackEvents.tryEmit(LauncherFeedbackEvent.OpenSettingsIntent)
            }
            ShortcutActionType.OPEN_BROWSER -> {
                _feedbackEvents.tryEmit(LauncherFeedbackEvent.OpenBrowserIntent)
            }
            ShortcutActionType.SHARE_STATS -> {
                val profile = (profileState.value as? UiState.Success)?.data
                _feedbackEvents.tryEmit(
                    LauncherFeedbackEvent.ShareStatsIntent(
                        gems = profile?.gems ?: 0L,
                        runs = profile?.completedRuns ?: 0L
                    )
                )
            }
            ShortcutActionType.TOGGLE_HAPTICS -> {
                val current = (profileState.value as? UiState.Success)?.data?.hapticsEnabled ?: true
                toggleHaptics(!current)
            }
        }
    }

    fun saveShortcut(shortcut: GestureShortcut) {
        if (!isOnline.value) {
            _feedbackEvents.tryEmit(
                LauncherFeedbackEvent.Banner("Connect to internet to save shortcuts.", isError = true)
            )
            return
        }
        viewModelScope.launch {
            val res = repository.saveShortcut(shortcut)
            if (res.isSuccess) {
                _feedbackEvents.tryEmit(LauncherFeedbackEvent.Banner("${shortcut.label} shortcut saved"))
            } else {
                _feedbackEvents.tryEmit(
                    LauncherFeedbackEvent.Banner(
                        res.exceptionOrNull()?.message ?: "Failed to save shortcut",
                        isError = true
                    )
                )
            }
        }
    }

    fun unlockOrEquipSkin(skinId: String) {
        if (!isOnline.value) {
            _feedbackEvents.tryEmit(
                LauncherFeedbackEvent.Banner("Internet required to access Gem Vault.", isError = true)
            )
            return
        }
        val skin = ArrowSkinCatalog.allSkins.firstOrNull { it.id == skinId } ?: return
        val profile = (profileState.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            if (profile.unlockedSkins.contains(skinId)) {
                val res = repository.equipSkin(skinId)
                if (res.isSuccess) {
                    _feedbackEvents.tryEmit(LauncherFeedbackEvent.Banner("Equipped ${skin.name}"))
                }
            } else {
                if (profile.gems < skin.costGems) {
                    _feedbackEvents.tryEmit(
                        LauncherFeedbackEvent.Banner(
                            "Need ${skin.costGems - profile.gems} more gems (earn +5 per run!)",
                            isError = true
                        )
                    )
                    return@launch
                }
                val res = repository.unlockAndEquipSkin(skinId, skin.costGems)
                if (res.isSuccess) {
                    _feedbackEvents.tryEmit(LauncherFeedbackEvent.Banner("Unlocked & equipped ${skin.name}!"))
                } else {
                    _feedbackEvents.tryEmit(
                        LauncherFeedbackEvent.Banner(
                            res.exceptionOrNull()?.message ?: "Unlock failed",
                            isError = true
                        )
                    )
                }
            }
        }
    }

    fun toggleHaptics(enabled: Boolean) {
        if (!isOnline.value) return
        viewModelScope.launch {
            val res = repository.updateHaptics(enabled)
            if (res.isSuccess) {
                _feedbackEvents.tryEmit(
                    LauncherFeedbackEvent.Banner(if (enabled) "Haptics enabled" else "Haptics muted")
                )
            }
        }
    }

    private fun resolveArrowStyleForDirection(direction: SwipeDirection): ArrowLaunchStyle {
        val list = (shortcutsState.value as? UiState.Success)?.data ?: return ArrowLaunchStyle.STRAIGHT
        val shortcut = list.firstOrNull { it.direction == direction.id } ?: return ArrowLaunchStyle.STRAIGHT
        return ArrowLaunchStyle.fromId(shortcut.arrowStyle)
    }

    private fun maybeEmitHaptic() {
        val enabled = (profileState.value as? UiState.Success)?.data?.hapticsEnabled ?: true
        if (enabled) {
            _feedbackEvents.tryEmit(LauncherFeedbackEvent.HapticPulse)
        }
    }

    private companion object {
        const val TAG = "ArrowSwipeVM"
    }
}
