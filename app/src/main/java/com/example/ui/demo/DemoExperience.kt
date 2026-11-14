package com.example.ui.demo

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.data.model.ArrowLaunchStyle
import com.example.data.model.SwipeDirection
import com.example.data.repository.ArrowSwipeRepository
import com.example.ui.auth.onGoogleSignInClicked
import com.example.ui.components.OnlineStatusChip
import com.example.ui.screens.ArenaScreen
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.DeepCyanContainer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.GemGold
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard
import com.example.ui.viewmodel.ActiveArrowFlight
import com.example.ui.viewmodel.ArenaRunState
import com.example.ui.viewmodel.HitBurstEffect
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot

@Composable
fun DemoExperience(
    isOnline: Boolean,
    onAuthSuccess: () -> Unit,
    onExitDemo: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onExitDemo() }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val demoShortcuts = remember { ArrowSwipeRepository.defaultShortcuts() }
    var arenaState by remember {
        mutableStateOf(
            ArenaRunState(
                runStartedAtMs = System.currentTimeMillis(),
                currentTarget = ArenaRunState.createRandomTarget(1),
                lastSwipeLabel = "Demo Run: Swipe anywhere to launch arrow!"
            )
        )
    }
    var demoGems by remember { mutableStateOf(0L) }
    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    // Local demo simulation loop (paused automatically if offline; zero Firestore writes)
    LaunchedEffect(isOnline) {
        var lastTime = System.currentTimeMillis()
        while (true) {
            delay(16L)
            val now = System.currentTimeMillis()
            val dt = ((now - lastTime).coerceIn(1L, 50L)) / 1000f
            lastTime = now

            if (!isOnline) continue

            val state = arenaState
            if (state.isRunCompleted) {
                if (state.hitBursts.isNotEmpty()) {
                    arenaState = state.copy(
                        hitBursts = state.hitBursts
                            .map { it.copy(progress = it.progress + dt * 2.4f) }
                            .filter { it.progress < 1f }
                    )
                }
                continue
            }

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
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                val newCleared = state.targetsCleared + 1
                val burstsWithHit = nextBursts + HitBurstEffect(
                    id = System.nanoTime(),
                    normX = updatedTarget.normX,
                    normY = updatedTarget.normY,
                    progress = 0f
                )
                if (newCleared >= state.totalTargetsInRun) {
                    val duration = (System.currentTimeMillis() - state.runStartedAtMs).coerceAtLeast(500L)
                    val acc = if (state.shotsFired == 0) 100 else ((newCleared.toFloat() / state.shotsFired) * 100f).toInt().coerceIn(0, 100)
                    demoGems = 5L
                    arenaState = state.copy(
                        targetsCleared = newCleared,
                        activeArrow = null,
                        hitBursts = burstsWithHit,
                        isRunCompleted = true,
                        lastCompletionDurationMs = duration,
                        lastAccuracyPercent = acc,
                        lastSwipeLabel = "Demo Complete! +5 Demo Gems previewed"
                    )
                } else {
                    arenaState = state.copy(
                        targetsCleared = newCleared,
                        currentTarget = ArenaRunState.createRandomTarget(newCleared + 1),
                        activeArrow = null,
                        hitBursts = burstsWithHit,
                        lastSwipeLabel = "Gate $newCleared/${state.totalTargetsInRun} cleared!"
                    )
                }
            } else {
                arenaState = state.copy(
                    currentTarget = updatedTarget,
                    activeArrow = updatedArrow,
                    hitBursts = nextBursts
                )
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CarbonSurface)
                    .border(0.5.dp, SlateBorder)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = CircleShape,
                            color = DeepCyanContainer
                        ) {
                            Text(
                                text = "1-TIME DEMO",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.no_level_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldPulse
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = SlateCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, GemGold.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Diamond,
                                contentDescription = "Demo gems",
                                tint = GemGold,
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "$demoGems",
                                style = MaterialTheme.typography.labelLarge,
                                color = GemGold,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    OnlineStatusChip(isOnline = isOnline)

                    IconButton(
                        onClick = onExitDemo,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("exit_demo_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Exit demo",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            ArenaScreen(
                arenaState = arenaState,
                activeSkinId = "cyan_pulse",
                shortcuts = demoShortcuts,
                onLaunchArrow = { startNormX, startNormY, dirX, dirY ->
                    if (!isOnline || arenaState.isRunCompleted) return@ArenaScreen
                    val mag = hypot(dirX, dirY)
                    if (mag < 0.01f) return@ArenaScreen
                    val ux = dirX / mag
                    val uy = dirY / mag
                    val dir = SwipeDirection.fromVector(ux, uy)
                    val style = ArrowLaunchStyle.fromId(
                        demoShortcuts.firstOrNull { it.direction == dir.id }?.arrowStyle ?: "STRAIGHT"
                    )
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    arenaState = arenaState.copy(
                        shotsFired = arenaState.shotsFired + 1,
                        activeArrow = ActiveArrowFlight(
                            id = System.nanoTime(),
                            startX = startNormX,
                            startY = startNormY,
                            currentX = startNormX,
                            currentY = startNormY,
                            velX = ux * 1.65f,
                            velY = uy * 1.65f,
                            style = style,
                            trailPoints = listOf(startNormX to startNormY)
                        ),
                        lastSwipeLabel = "Demo Launch: ${dir.displayName}"
                    )
                },
                onRestartRun = {
                    scope.launch {
                        snackbarHostState.showSnackbar("Demo allows 1 run — Sign in with Google to play unlimited runs!")
                    }
                },
                onTriggerShortcut = { dir ->
                    val label = demoShortcuts.firstOrNull { it.direction == dir.id }?.label ?: dir.displayName
                    scope.launch {
                        snackbarHostState.showSnackbar("Demo Shortcut: ${dir.displayName} -> $label")
                    }
                }
            )

            // Override completion CTA when the single demo run finishes
            if (arenaState.isRunCompleted) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ObsidianBg.copy(alpha = 0.86f))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(SlateCard)
                            .border(1.5.dp, GemGold.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                            .padding(22.dp)
                            .testTag("demo_complete_card"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = GemGold.copy(alpha = 0.18f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GemGold)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Diamond,
                                    contentDescription = null,
                                    tint = GemGold,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "+5 DEMO GEMS EARNED",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = GemGold,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "One-Time Demo Complete!",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Sign in with Google to keep earning +5 Gems per run, customize all 8 swipe shortcuts, and unlock arrow skins.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        if (authError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = authError!!,
                                style = MaterialTheme.typography.bodyMedium,
                                color = CoralAlert,
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                if (!isOnline) {
                                    authError = "Internet connection required."
                                    return@Button
                                }
                                authError = null
                                isSigningIn = true
                                onGoogleSignInClicked(
                                    context = context,
                                    credentialManager = credentialManager,
                                    onAuthSuccess = {
                                        isSigningIn = false
                                        onAuthSuccess()
                                    },
                                    onAuthError = { msg ->
                                        isSigningIn = false
                                        authError = msg
                                    },
                                    scope = scope,
                                    onAuthCancelled = { isSigningIn = false }
                                )
                            },
                            enabled = !isSigningIn && isOnline,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("demo_complete_google_sign_in"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = ObsidianBg
                            )
                        ) {
                            if (isSigningIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = ObsidianBg,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.sign_in_cta),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = onExitDemo,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Back to Sign-In Screen")
                        }
                    }
                }
            }
        }
    }
}
