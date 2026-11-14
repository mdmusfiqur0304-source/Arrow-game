package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CoralAlert
import com.example.ui.theme.DeepCoralContainer
import com.example.ui.theme.DeepEmeraldContainer
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.GemGold
import com.example.ui.theme.SlateCard
import com.example.ui.viewmodel.GemBalanceUiState

@Composable
fun PersistentGemBalanceBadge(
    state: GemBalanceUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasDelta = state is GemBalanceUiState.Ready && state.recentDelta > 0L
    val scale by animateFloatAsState(
        targetValue = if (hasDelta) 1.07f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "gem_badge_scale"
    )

    val borderColor = when (state) {
        is GemBalanceUiState.Error -> CoralAlert.copy(alpha = 0.7f)
        is GemBalanceUiState.Ready -> if (state.recentDelta > 0L) EmeraldPulse else GemGold.copy(alpha = 0.55f)
        GemBalanceUiState.Loading -> GemGold.copy(alpha = 0.35f)
    }

    val containerColor = when (state) {
        is GemBalanceUiState.Error -> DeepCoralContainer
        else -> SlateCard
    }

    val accessibilityLabel = when (state) {
        GemBalanceUiState.Loading -> "Loading gem balance from cloud"
        is GemBalanceUiState.Ready -> "Current gem balance: ${state.gems} gems"
        is GemBalanceUiState.Error -> "Gem balance sync error, tap to retry"
    }

    Surface(
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .scale(scale)
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = accessibilityLabel }
            .testTag("persistent_gem_balance_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            when (state) {
                GemBalanceUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = GemGold,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "...",
                        style = MaterialTheme.typography.labelLarge,
                        color = GemGold,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("gem_balance_value")
                    )
                }

                is GemBalanceUiState.Ready -> {
                    val animatedGems by animateIntAsState(
                        targetValue = state.gems.toInt(),
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "animated_gem_count"
                    )

                    Icon(
                        imageVector = Icons.Rounded.Diamond,
                        contentDescription = null,
                        tint = GemGold,
                        modifier = Modifier.size(16.dp)
                    )

                    Text(
                        text = "$animatedGems",
                        style = MaterialTheme.typography.labelLarge,
                        color = GemGold,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("gem_balance_value")
                    )

                    if (state.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = GemGold,
                            strokeWidth = 1.8.dp
                        )
                    }

                    AnimatedVisibility(
                        visible = state.recentDelta > 0L,
                        enter = fadeIn() + scaleIn(initialScale = 0.7f),
                        exit = fadeOut() + scaleOut()
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = DeepEmeraldContainer
                        ) {
                            Text(
                                text = "+${state.recentDelta}",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldPulse,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .testTag("gem_balance_delta")
                            )
                        }
                    }
                }

                is GemBalanceUiState.Error -> {
                    Icon(
                        imageVector = Icons.Rounded.SyncProblem,
                        contentDescription = null,
                        tint = CoralAlert,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Retry",
                        style = MaterialTheme.typography.labelMedium,
                        color = CoralAlert,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("gem_balance_value")
                    )
                }
            }
        }
    }
}
