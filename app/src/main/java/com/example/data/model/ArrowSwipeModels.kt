package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class SwipeDirection(
    val id: String,
    val displayName: String,
    val angleDegrees: Float
) {
    UP("UP", "Swipe Up", -90f),
    UP_RIGHT("UP_RIGHT", "Up-Right", -45f),
    RIGHT("RIGHT", "Swipe Right", 0f),
    DOWN_RIGHT("DOWN_RIGHT", "Down-Right", 45f),
    DOWN("DOWN", "Swipe Down", 90f),
    DOWN_LEFT("DOWN_LEFT", "Down-Left", 135f),
    LEFT("LEFT", "Swipe Left", 180f),
    UP_LEFT("UP_LEFT", "Up-Left", -135f);

    companion object {
        fun fromId(id: String): SwipeDirection =
            entries.firstOrNull { it.id == id } ?: UP

        fun fromVector(dx: Float, dy: Float): SwipeDirection {
            val angle = Math.toDegrees(kotlin.math.atan2(dy.toDouble(), dx.toDouble())).toFloat()
            return when {
                angle >= -22.5f && angle < 22.5f -> RIGHT
                angle >= 22.5f && angle < 67.5f -> DOWN_RIGHT
                angle >= 67.5f && angle < 112.5f -> DOWN
                angle >= 112.5f && angle < 157.5f -> DOWN_LEFT
                angle >= -67.5f && angle < -22.5f -> UP_RIGHT
                angle >= -112.5f && angle < -67.5f -> UP
                angle >= -157.5f && angle < -112.5f -> UP_LEFT
                else -> LEFT
            }
        }
    }
}

enum class ShortcutActionType(
    val id: String,
    val title: String,
    val subtitle: String
) {
    NAV_ARENA("NAV_ARENA", "Jump to Arena", "Open the arrow launcher playfield"),
    NAV_SHORTCUTS("NAV_SHORTCUTS", "Open Shortcuts", "Customize swipeable gesture bindings"),
    NAV_VAULT("NAV_VAULT", "Open Gem Vault", "Spend earned gems on arrow skins"),
    QUICK_RESTART("QUICK_RESTART", "Quick Reset Run", "Reset current arrow run targets"),
    OPEN_SETTINGS("OPEN_SETTINGS", "System Settings", "Open Android device settings"),
    OPEN_BROWSER("OPEN_BROWSER", "Quick Web Search", "Launch default browser search"),
    SHARE_STATS("SHARE_STATS", "Share Gem Score", "Share your runs & gems via system sheet"),
    TOGGLE_HAPTICS("TOGGLE_HAPTICS", "Toggle Haptics", "Switch tactile launch feedback on/off");

    companion object {
        fun fromId(id: String): ShortcutActionType =
            entries.firstOrNull { it.id == id } ?: NAV_ARENA
    }
}

enum class ArrowLaunchStyle(
    val id: String,
    val title: String
) {
    STRAIGHT("STRAIGHT", "Laser Bolt"),
    ARC("ARC", "Kinetic Arc"),
    BOLT("BOLT", "Hyper Pulse"),
    SPLIT("SPLIT", "Prism Vector");

    companion object {
        fun fromId(id: String): ArrowLaunchStyle =
            entries.firstOrNull { it.id == id } ?: STRAIGHT
    }
}

data class ArrowSkin(
    val id: String,
    val name: String,
    val description: String,
    val costGems: Int
)

object ArrowSkinCatalog {
    val allSkins = listOf(
        ArrowSkin("cyan_pulse", "Cyan Pulse", "Standard electric vector trail", 0),
        ArrowSkin("emerald_volt", "Emerald Volt", "High-voltage neon mint plasma", 10),
        ArrowSkin("solar_amber", "Solar Amber", "Blazing golden flare vector", 15),
        ArrowSkin("crimson_nova", "Crimson Nova", "Supernova ruby kinetic stream", 25),
        ArrowSkin("prism_vector", "Prism Vector", "Chromatic dual-spectrum arrow", 40)
    )
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "Player",
    val gems: Long = 0L,
    val completedRuns: Long = 0L,
    val totalHits: Long = 0L,
    val activeSkinId: String = "cyan_pulse",
    val unlockedSkins: List<String> = listOf("cyan_pulse"),
    val hapticsEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class GestureShortcut(
    @DocumentId val id: String = "",
    val userId: String = "",
    val direction: String = SwipeDirection.UP.id,
    val label: String = "Arena",
    val actionType: String = ShortcutActionType.NAV_ARENA.id,
    val arrowStyle: String = ArrowLaunchStyle.STRAIGHT.id,
    val enabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CompletedRun(
    @DocumentId val id: String = "",
    val userId: String = "",
    val targetsCleared: Long = 6L,
    val accuracyPercent: Long = 100L,
    val gemsEarned: Long = 5L,
    val durationMs: Long = 1000L,
    @ServerTimestamp val createdAt: Timestamp? = null
)
