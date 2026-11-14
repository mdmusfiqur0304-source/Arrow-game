package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.ArrowLaunchStyle
import com.example.data.model.GestureShortcut
import com.example.data.model.ShortcutActionType
import com.example.data.model.SwipeDirection
import com.example.ui.components.GestureShortcutPad
import com.example.ui.theme.DeepCyanContainer
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldPulse
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SlateCard

@Composable
fun ShortcutsScreen(
    shortcuts: List<GestureShortcut>,
    activeSkinId: String,
    onSaveShortcut: (GestureShortcut) -> Unit,
    onTriggerShortcut: (SwipeDirection) -> Unit,
    onBackToArena: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { onBackToArena() }

    val (skinPrimary, _) = remember(activeSkinId) { skinColorsForId(activeSkinId) }
    var editingShortcut by remember { mutableStateOf<GestureShortcut?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            GestureShortcutPad(
                shortcuts = shortcuts,
                accentColor = skinPrimary,
                onSwipeTriggered = onTriggerShortcut,
                title = "Live Swipe Shortcut Tester"
            )
        }

        item {
            Column(modifier = Modifier.padding(top = 4.dp)) {
                Text(
                    text = "Customizable Swipe Directions",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Tap any direction to customize its label, arrow style, and instant navigation action.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        items(shortcuts, key = { it.direction }) { shortcut ->
            ShortcutSlotCard(
                shortcut = shortcut,
                onClick = { editingShortcut = shortcut },
                onToggleEnabled = { enabled ->
                    onSaveShortcut(shortcut.copy(enabled = enabled))
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    editingShortcut?.let { current ->
        EditShortcutDialog(
            shortcut = current,
            onDismiss = { editingShortcut = null },
            onSave = { updated ->
                onSaveShortcut(updated)
                editingShortcut = null
            }
        )
    }
}

@Composable
private fun ShortcutSlotCard(
    shortcut: GestureShortcut,
    onClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    val direction = SwipeDirection.fromId(shortcut.direction)
    val action = ShortcutActionType.fromId(shortcut.actionType)
    val style = ArrowLaunchStyle.fromId(shortcut.arrowStyle)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SlateCard)
            .border(
                width = 1.dp,
                color = if (shortcut.enabled) SlateBorder else SlateBorder.copy(alpha = 0.4f),
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("shortcut_card_${shortcut.direction}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (shortcut.enabled) DeepCyanContainer else ObsidianBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowUpward,
                contentDescription = direction.displayName,
                tint = if (shortcut.enabled) ElectricCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(24.dp)
                    .rotate(direction.angleDegrees + 90f)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = shortcut.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "• ${direction.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
            }
            Text(
                text = "${action.title} • ${style.title}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = Icons.Rounded.Edit,
            contentDescription = "Edit shortcut",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(end = 8.dp)
                .size(18.dp)
        )

        Switch(
            checked = shortcut.enabled,
            onCheckedChange = onToggleEnabled,
            modifier = Modifier.testTag("shortcut_switch_${shortcut.direction}")
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditShortcutDialog(
    shortcut: GestureShortcut,
    onDismiss: () -> Unit,
    onSave: (GestureShortcut) -> Unit
) {
    val direction = SwipeDirection.fromId(shortcut.direction)
    var label by remember(shortcut) { mutableStateOf(shortcut.label) }
    var selectedAction by remember(shortcut) { mutableStateOf(ShortcutActionType.fromId(shortcut.actionType)) }
    var selectedStyle by remember(shortcut) { mutableStateOf(ArrowLaunchStyle.fromId(shortcut.arrowStyle)) }
    var enabled by remember(shortcut) { mutableStateOf(shortcut.enabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SlateCard,
        title = {
            Text(
                text = "Customize ${direction.displayName}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { if (it.length <= 40) label = it },
                    label = { Text("Shortcut Label") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_shortcut_label_input")
                )

                Text(
                    text = "Bound Navigation / Action",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ShortcutActionType.entries.forEach { action ->
                        val isSelected = action == selectedAction
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedAction = action
                                if (label.isBlank() || ShortcutActionType.entries.any { it.title == label }) {
                                    label = action.title
                                }
                            },
                            label = { Text(action.title, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DeepCyanContainer,
                                selectedLabelColor = ElectricCyan
                            )
                        )
                    }
                }

                Text(
                    text = "Arrow Launch Style",
                    style = MaterialTheme.typography.labelLarge,
                    color = EmeraldPulse
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ArrowLaunchStyle.entries.forEach { style ->
                        FilterChip(
                            selected = style == selectedStyle,
                            onClick = { selectedStyle = style },
                            label = { Text(style.title, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DeepCyanContainer,
                                selectedLabelColor = EmeraldPulse
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Shortcut Enabled",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        shortcut.copy(
                            label = label.trim().ifBlank { selectedAction.title },
                            actionType = selectedAction.id,
                            arrowStyle = selectedStyle.id,
                            enabled = enabled
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = ObsidianBg
                ),
                modifier = Modifier.testTag("save_shortcut_button")
            ) {
                Text("Save", style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
