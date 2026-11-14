package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Diamond
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.ArrowSkin
import com.example.data.model.ArrowSkinCatalog
import com.example.data.model.CompletedRun
import com.example.data.model.UserProfile
import com.example.ui.theme.DeepCyanContainer
import com.example.ui.theme.DeepEmeraldContainer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.GemGold
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard

@Composable
fun VaultScreen(
    profile: UserProfile?,
    recentRuns: List<CompletedRun>,
    onSelectOrUnlockSkin: (String) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onBackToArena: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBackToArena() }

    val gems = profile?.gems ?: 0L
    val completedRuns = profile?.completedRuns ?: 0L
    val totalHits = profile?.totalHits ?: 0L
    val activeSkinId = profile?.activeSkinId ?: "cyan_pulse"
    val unlockedSkins = profile?.unlockedSkins ?: listOf("cyan_pulse")
    val hapticsEnabled = profile?.hapticsEnabled ?: true

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            // Gem Balance Hero Card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(SlateCard, ColorPaletteVaultGlow)
                        )
                    )
                    .border(1.dp, GemGold.copy(alpha = 0.45f), RoundedCornerShape(24.dp))
                    .padding(18.dp)
                    .testTag("gem_vault_hero_card"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_gem_emblem),
                    contentDescription = "Gem vault crystal emblem",
                    modifier = Modifier
                        .size(76.dp)
                        .clip(RoundedCornerShape(18.dp)),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CLOUD GEM VAULT",
                        style = MaterialTheme.typography.labelSmall,
                        color = GemGold
                    )
                    Text(
                        text = "$gems Gems",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "$completedRuns Completed Runs • $totalHits Gates Hit",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Text(
                text = "Arrow Trail Armory",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "Unlock skins with gems earned from completed runs (+5 gems each, no level locks).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(ArrowSkinCatalog.allSkins, key = { it.id }) { skin ->
            SkinArmoryCard(
                skin = skin,
                isUnlocked = unlockedSkins.contains(skin.id),
                isEquipped = activeSkinId == skin.id,
                canAfford = gems >= skin.costGems,
                onAction = { onSelectOrUnlockSkin(skin.id) }
            )
        }

        item {
            // Preferences & Account Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SlateCard)
                    .border(1.dp, SlateBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tactile Launch Haptics",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Vibrate on arrow release and gate hits",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = hapticsEnabled,
                        onCheckedChange = onToggleHaptics,
                        modifier = Modifier.testTag("haptics_switch")
                    )
                }

                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("sign_out_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sign out (${profile?.displayName ?: "Player"})",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        if (recentRuns.isNotEmpty()) {
            item {
                Text(
                    text = "Recent Completed Runs (+5 Gems Each)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            items(recentRuns, key = { it.id }) { run ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SlateCard)
                        .border(1.dp, SlateBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${run.targetsCleared} Gates Cleared • ${run.accuracyPercent}% Acc",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = String.format("%.1fs run time", run.durationMs / 1000.0),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = GemGold.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = "+${run.gemsEarned} GEMS",
                            style = MaterialTheme.typography.labelSmall,
                            color = GemGold,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private val ColorPaletteVaultGlow = androidx.compose.ui.graphics.Color(0xFF211C38)

@Composable
private fun SkinArmoryCard(
    skin: ArrowSkin,
    isUnlocked: Boolean,
    isEquipped: Boolean,
    canAfford: Boolean,
    onAction: () -> Unit
) {
    val (primary, secondary) = skinColorsForId(skin.id)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SlateCard)
            .border(
                width = if (isEquipped) 1.5.dp else 1.dp,
                color = if (isEquipped) primary else SlateBorder,
                shape = RoundedCornerShape(18.dp)
            )
            .padding(14.dp)
            .testTag("skin_card_${skin.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(primary, secondary))
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isEquipped) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = "Equipped",
                    tint = ObsidianBg,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = skin.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = skin.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        when {
            isEquipped -> {
                Surface(
                    shape = CircleShape,
                    color = DeepEmeraldContainer
                ) {
                    Text(
                        text = "EQUIPPED",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldPulse,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
            isUnlocked -> {
                Button(
                    onClick = onAction,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DeepCyanContainer,
                        contentColor = ElectricCyan
                    )
                ) {
                    Text("Equip", style = MaterialTheme.typography.labelMedium)
                }
            }
            else -> {
                Button(
                    onClick = onAction,
                    enabled = canAfford,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = GemGold,
                        contentColor = ObsidianBg
                    )
                ) {
                    Icon(
                        imageVector = if (canAfford) Icons.Rounded.Diamond else Icons.Rounded.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("${skin.costGems}", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
