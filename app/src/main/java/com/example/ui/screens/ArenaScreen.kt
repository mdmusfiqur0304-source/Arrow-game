package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.GestureShortcut
import com.example.data.model.SwipeDirection
import com.example.ui.components.GestureShortcutPad
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.DeepCyanContainer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.GemAmethyst
import com.example.ui.theme.GemGold
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard
import com.example.ui.viewmodel.ArenaRunState
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

fun skinColorsForId(skinId: String): Pair<Color, Color> = when (skinId) {
    "emerald_volt" -> EmeraldPulse to Color(0xFFB9F6CA)
    "solar_amber" -> GemGold to Color(0xFFFF9100)
    "crimson_nova" -> CoralAlert to Color(0xFFFF80AB)
    "prism_vector" -> GemAmethyst to ElectricCyan
    else -> ElectricCyan to Color.White
}

@Composable
fun ArenaScreen(
    arenaState: ArenaRunState,
    activeSkinId: String,
    shortcuts: List<GestureShortcut>,
    onLaunchArrow: (startNormX: Float, startNormY: Float, dirX: Float, dirY: Float) -> Unit,
    onRestartRun: () -> Unit,
    onTriggerShortcut: (SwipeDirection) -> Unit,
    modifier: Modifier = Modifier
) {
    val (skinPrimary, skinSecondary) = remember(activeSkinId) { skinColorsForId(activeSkinId) }
    var showShortcutDock by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Run Progress HUD (No Level System)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(SlateCard)
                .border(1.dp, SlateBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "OPEN RUN • ${arenaState.targetsCleared}/${arenaState.totalTargetsInRun} GATES",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Surface(
                        shape = CircleShape,
                        color = GemGold.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "+5 GEMS",
                            style = MaterialTheme.typography.labelSmall,
                            color = GemGold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(arenaState.totalTargetsInRun) { idx ->
                        val cleared = idx < arenaState.targetsCleared
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (cleared) skinPrimary else SlateBorder
                                )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            IconButton(
                onClick = { showShortcutDock = !showShortcutDock },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .background(if (showShortcutDock) DeepCyanContainer else ObsidianBg)
                    .testTag("toggle_shortcut_dock_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Swipe,
                    contentDescription = "Toggle swipe shortcut launcher pad",
                    tint = if (showShortcutDock) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onRestartRun,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .background(ObsidianBg)
                    .testTag("restart_run_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Restart current run",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Expandable Swipeable Gesture Shortcut Pad
        AnimatedVisibility(visible = showShortcutDock) {
            GestureShortcutPad(
                shortcuts = shortcuts,
                accentColor = skinPrimary,
                onSwipeTriggered = { dir ->
                    showShortcutDock = false
                    onTriggerShortcut(dir)
                },
                title = "Quick Navigation Gesture Pad"
            )
        }

        // Interactive Arrow Launcher Playfield
        var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }
        var dragStart by remember { mutableStateOf<Offset?>(null) }
        var dragCurrent by remember { mutableStateOf<Offset?>(null) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF0B1220), ObsidianBg, Color(0xFF0E1729))
                    )
                )
                .border(1.5.dp, SlateBorder, RoundedCornerShape(26.dp))
                .onSizeChanged { canvasSize = it }
                .pointerInput(arenaState.isRunCompleted) {
                    if (arenaState.isRunCompleted) return@pointerInput
                    detectDragGestures(
                        onDragStart = { start ->
                            dragStart = start
                            dragCurrent = start
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dragCurrent = change.position
                        },
                        onDragEnd = {
                            val start = dragStart
                            val end = dragCurrent
                            dragStart = null
                            dragCurrent = null
                            if (start != null && end != null && canvasSize.width > 0 && canvasSize.height > 0) {
                                val dx = end.x - start.x
                                val dy = end.y - start.y
                                if (hypot(dx, dy) >= 24f) {
                                    val startNormX = (start.x / canvasSize.width.toFloat()).coerceIn(0.1f, 0.9f)
                                    val startNormY = (start.y / canvasSize.height.toFloat()).coerceIn(0.62f, 0.9f)
                                    onLaunchArrow(startNormX, startNormY, dx, dy)
                                }
                            }
                        },
                        onDragCancel = {
                            dragStart = null
                            dragCurrent = null
                        }
                    )
                }
                .testTag("arrow_launcher_arena_canvas")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Cyber grid lines
                val stepX = w / 6f
                val stepY = h / 8f
                for (i in 1..5) {
                    drawLine(
                        color = SlateBorder.copy(alpha = 0.25f),
                        start = Offset(stepX * i, 0f),
                        end = Offset(stepX * i, h),
                        strokeWidth = 1f
                    )
                }
                for (j in 1..7) {
                    drawLine(
                        color = SlateBorder.copy(alpha = 0.25f),
                        start = Offset(0f, stepY * j),
                        end = Offset(w, stepY * j),
                        strokeWidth = 1f
                    )
                }

                // Launch zone divider
                val launchLineY = h * 0.62f
                drawLine(
                    color = skinPrimary.copy(alpha = 0.3f),
                    start = Offset(24f, launchLineY),
                    end = Offset(w - 24f, launchLineY),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f), 0f)
                )

                // Hit bursts
                arenaState.hitBursts.forEach { burst ->
                    val bx = burst.normX * w
                    val by = burst.normY * h
                    val rad = (w.coerceAtMost(h) * 0.16f) * burst.progress
                    drawCircle(
                        color = EmeraldPulse.copy(alpha = (1f - burst.progress).coerceIn(0f, 1f)),
                        radius = rad,
                        center = Offset(bx, by),
                        style = Stroke(width = 6f * (1f - burst.progress))
                    )
                }

                // Active Target Gate
                if (!arenaState.isRunCompleted) {
                    val tx = arenaState.currentTarget.normX * w
                    val ty = arenaState.currentTarget.normY * h
                    val tRad = arenaState.currentTarget.radiusNorm * w.coerceAtMost(h)

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                EmeraldPulse.copy(alpha = 0.45f),
                                EmeraldPulse.copy(alpha = 0.05f)
                            ),
                            center = Offset(tx, ty),
                            radius = tRad * 1.6f
                        ),
                        radius = tRad * 1.5f,
                        center = Offset(tx, ty)
                    )
                    drawCircle(
                        color = EmeraldPulse,
                        radius = tRad,
                        center = Offset(tx, ty),
                        style = Stroke(width = 5f)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = tRad * 0.35f,
                        center = Offset(tx, ty)
                    )
                }

                // Active Arrow Flight + Trail
                val flight = arenaState.activeArrow
                if (flight != null) {
                    val pts = flight.trailPoints
                    if (pts.size >= 2) {
                        for (i in 1 until pts.size) {
                            val alpha = i.toFloat() / pts.size.toFloat()
                            drawLine(
                                color = skinPrimary.copy(alpha = alpha * 0.9f),
                                start = Offset(pts[i - 1].first * w, pts[i - 1].second * h),
                                end = Offset(pts[i].first * w, pts[i].second * h),
                                strokeWidth = 10f * alpha,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                    val tip = Offset(flight.currentX * w, flight.currentY * h)
                    val angle = atan2(flight.velY.toDouble(), flight.velX.toDouble())
                    val headLen = 28f
                    val leftWing = Offset(
                        (tip.x - headLen * cos(angle - 0.52)).toFloat(),
                        (tip.y - headLen * sin(angle - 0.52)).toFloat()
                    )
                    val rightWing = Offset(
                        (tip.x - headLen * cos(angle + 0.52)).toFloat(),
                        (tip.y - headLen * sin(angle + 0.52)).toFloat()
                    )
                    val arrowPath = Path().apply {
                        moveTo(tip.x, tip.y)
                        lineTo(leftWing.x, leftWing.y)
                        moveTo(tip.x, tip.y)
                        lineTo(rightWing.x, rightWing.y)
                    }
                    drawPath(
                        path = arrowPath,
                        color = skinSecondary,
                        style = Stroke(width = 7f, cap = StrokeCap.Round)
                    )
                    drawCircle(
                        color = skinPrimary,
                        radius = 7f,
                        center = tip
                    )
                }

                // Aiming vector preview while user is swiping
                val s = dragStart
                val c = dragCurrent
                if (s != null && c != null && hypot(c.x - s.x, c.y - s.y) > 12f) {
                    drawLine(
                        color = skinPrimary.copy(alpha = 0.85f),
                        start = s,
                        end = c,
                        strokeWidth = 6f,
                        cap = StrokeCap.Round
                    )
                    val angle = atan2((c.y - s.y).toDouble(), (c.x - s.x).toDouble())
                    val projEnd = Offset(
                        (s.x + cos(angle) * 420f).toFloat(),
                        (s.y + sin(angle) * 420f).toFloat()
                    )
                    drawLine(
                        color = skinSecondary.copy(alpha = 0.35f),
                        start = c,
                        end = projEnd,
                        strokeWidth = 3f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                    )
                    drawCircle(
                        color = skinSecondary,
                        radius = 10f,
                        center = s
                    )
                }
            }

            // Bottom subtle instruction bar inside playfield
            if (!arenaState.isRunCompleted) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = arenaState.lastSwipeLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "ACC ${arenaState.accuracyPercent}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = skinPrimary
                    )
                }
            }

            // Run Completed +5 Gems Celebration Overlay
            this@Column.AnimatedVisibility(
                visible = arenaState.isRunCompleted,
                enter = fadeIn() + scaleIn(initialScale = 0.92f),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(SlateCard.copy(alpha = 0.96f))
                        .border(1.5.dp, GemGold.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                        .padding(22.dp)
                        .testTag("run_complete_card"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_gem_emblem),
                        contentDescription = "Five gems reward",
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(20.dp)),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "RUN COMPLETE!",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        shape = CircleShape,
                        color = GemGold.copy(alpha = 0.2f),
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
                                text = if (arenaState.isAwardingGems) "SYNCING +5 GEMS..." else "+5 GEMS AWARDED",
                                style = MaterialTheme.typography.labelLarge,
                                color = GemGold,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "6/6 Gates Cleared • ${arenaState.lastAccuracyPercent}% Accuracy • ${(arenaState.lastCompletionDurationMs / 1000.0).let { String.format("%.1fs", it) }}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Button(
                        onClick = onRestartRun,
                        enabled = !arenaState.isAwardingGems,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("launch_next_run_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = ObsidianBg
                        )
                    ) {
                        if (arenaState.isAwardingGems) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = ObsidianBg,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Launch Next Run",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    TextButton(
                        onClick = { showShortcutDock = true },
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Text(
                            text = "Or swipe shortcut pad to navigate",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan
                        )
                    }
                }
            }
        }
    }
}
