package com.example.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.GestureShortcut
import com.example.data.model.SwipeDirection
import com.example.ui.theme.DeepCyanContainer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun GestureShortcutPad(
    shortcuts: List<GestureShortcut>,
    accentColor: Color,
    onSwipeTriggered: (SwipeDirection) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Swipe Gesture Launcher Pad"
) {
    var dragStart by remember { mutableStateOf<Offset?>(null) }
    var dragCurrent by remember { mutableStateOf<Offset?>(null) }
    var hoveredDirection by remember { mutableStateOf<SwipeDirection?>(null) }

    val activeShortcut = hoveredDirection?.let { dir ->
        shortcuts.firstOrNull { it.direction == dir.id }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(SlateCard)
            .border(1.dp, SlateBorder, RoundedCornerShape(24.dp))
            .padding(14.dp)
            .testTag("gesture_shortcut_pad")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Swipe,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Surface(
                shape = CircleShape,
                color = if (activeShortcut != null) DeepCyanContainer else ObsidianBg
            ) {
                Text(
                    text = activeShortcut?.label ?: "8-Way Swipe",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (activeShortcut != null) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.14f),
                            ObsidianBg
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = if (hoveredDirection != null) accentColor else SlateBorder,
                    shape = RoundedCornerShape(18.dp)
                )
                .pointerInput(shortcuts) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragStart = offset
                            dragCurrent = offset
                            hoveredDirection = null
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val start = dragStart ?: return@detectDragGestures
                            val current = change.position
                            dragCurrent = current
                            val dx = current.x - start.x
                            val dy = current.y - start.y
                            hoveredDirection = if (hypot(dx, dy) >= 28f) {
                                SwipeDirection.fromVector(dx, dy)
                            } else {
                                null
                            }
                        },
                        onDragEnd = {
                            val dir = hoveredDirection
                            dragStart = null
                            dragCurrent = null
                            hoveredDirection = null
                            if (dir != null) {
                                onSwipeTriggered(dir)
                            }
                        },
                        onDragCancel = {
                            dragStart = null
                            dragCurrent = null
                            hoveredDirection = null
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val ringRadius = size.minDimension * 0.36f

                // Outer compass ring and 8 directional ticks
                drawCircle(
                    color = SlateBorder.copy(alpha = 0.6f),
                    radius = ringRadius,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2f)
                )
                SwipeDirection.entries.forEach { dir ->
                    val rad = Math.toRadians(dir.angleDegrees.toDouble())
                    val dotX = cx + (cos(rad) * ringRadius).toFloat()
                    val dotY = cy + (sin(rad) * ringRadius).toFloat()
                    val isHovered = dir == hoveredDirection
                    val enabled = shortcuts.firstOrNull { it.direction == dir.id }?.enabled ?: true
                    drawCircle(
                        color = when {
                            isHovered -> accentColor
                            enabled -> EmeraldPulse.copy(alpha = 0.55f)
                            else -> SlateBorder.copy(alpha = 0.35f)
                        },
                        radius = if (isHovered) 11f else 5.5f,
                        center = Offset(dotX, dotY)
                    )
                }

                // Live swipe arrow vector
                val start = dragStart
                val end = dragCurrent
                if (start != null && end != null && hypot(end.x - start.x, end.y - start.y) > 12f) {
                    drawLine(
                        color = accentColor,
                        start = start,
                        end = end,
                        strokeWidth = 8f,
                        cap = StrokeCap.Round
                    )
                    val angle = atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble())
                    val headLen = 24f
                    val p1 = Offset(
                        (end.x - headLen * cos(angle - 0.5)).toFloat(),
                        (end.y - headLen * sin(angle - 0.5)).toFloat()
                    )
                    val p2 = Offset(
                        (end.x - headLen * cos(angle + 0.5)).toFloat(),
                        (end.y - headLen * sin(angle + 0.5)).toFloat()
                    )
                    val headPath = Path().apply {
                        moveTo(end.x, end.y)
                        lineTo(p1.x, p1.y)
                        moveTo(end.x, end.y)
                        lineTo(p2.x, p2.y)
                    }
                    drawPath(
                        path = headPath,
                        color = accentColor,
                        style = Stroke(width = 7f, cap = StrokeCap.Round)
                    )
                }
            }

            // Center prompt when idle
            if (dragStart == null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SWIPE ANY DIRECTION",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Instant shortcut navigation",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
