package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.ArrowLaunchStyle
import com.example.data.model.CompletedRun
import com.example.data.model.GestureShortcut
import com.example.data.model.ShortcutActionType
import com.example.data.model.SwipeDirection
import com.example.data.model.UserProfile
import com.example.data.remote.OperationType
import com.example.data.remote.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ArrowSwipeRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    fun observeProfile(userId: String): Flow<UserProfile?> =
        db.collection("users").document(userId)
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(
                        UserProfile::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                } else {
                    null
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.GET, "users/$userId")
                }
                throw error
            }

    fun observeShortcuts(userId: String): Flow<List<GestureShortcut>> =
        db.collection("users").document(userId).collection("shortcuts")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    GestureShortcut::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, "users/$userId/shortcuts")
                }
                throw error
            }

    fun observeRecentRuns(userId: String): Flow<List<CompletedRun>> =
        db.collection("users").document(userId).collection("runs")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(15)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    CompletedRun::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, "users/$userId/runs")
                }
                throw error
            }

    suspend fun ensureUserInitialized(displayName: String): Result<Unit> {
        val uid = requireUserId()
        val safeName = displayName.trim().ifBlank { "Arrow Player" }.take(60)
        val userRef = db.collection("users").document(uid)
        return try {
            val userDoc = userRef.get().await()
            if (!userDoc.exists()) {
                val initialProfile = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "gems" to 0L,
                    "completedRuns" to 0L,
                    "totalHits" to 0L,
                    "activeSkinId" to "cyan_pulse",
                    "unlockedSkins" to listOf("cyan_pulse"),
                    "hapticsEnabled" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userRef.set(initialProfile).await()
            }

            val existingShortcuts = userRef.collection("shortcuts").get().await()
            if (existingShortcuts.isEmpty) {
                val batch = db.batch()
                defaultShortcuts().forEach { shortcut ->
                    val docRef = userRef.collection("shortcuts").document(shortcut.direction)
                    val payload = mapOf(
                        "userId" to uid,
                        "direction" to shortcut.direction,
                        "label" to shortcut.label,
                        "actionType" to shortcut.actionType,
                        "arrowStyle" to shortcut.arrowStyle,
                        "enabled" to shortcut.enabled,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                    batch.set(docRef, payload)
                }
                batch.commit().await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userRef.path)
            Result.failure(e)
        }
    }

    suspend fun fetchGemBalanceFromServer(userId: String): Result<Long> {
        val userRef = db.collection("users").document(userId)
        return try {
            val snapshot = userRef.get(Source.SERVER).await()
            val gems = snapshot.getLong("gems") ?: 0L
            Result.success(gems)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, userRef.path)
            Result.failure(e)
        }
    }

    suspend fun addFiveGemsOnRunCompletion(targetsCleared: Int = 6): Result<Long> {
        val uid = requireUserId()
        val userRef = db.collection("users").document(uid)
        val clampedTargets = targetsCleared.coerceIn(0, 50).toLong()
        return try {
            userRef.update(buildRunCompletionUserUpdates(clampedTargets)).await()
            val updatedSnapshot = userRef.get(Source.SERVER).await()
            Result.success(updatedSnapshot.getLong("gems") ?: GEMS_PER_COMPLETION)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            Result.failure(e)
        }
    }

    suspend fun completeRunAndAwardGems(
        targetsCleared: Int,
        accuracyPercent: Int,
        durationMs: Long
    ): Result<String> {
        val uid = requireUserId()
        val runId = "run_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val userRef = db.collection("users").document(uid)
        val runRef = userRef.collection("runs").document(runId)

        val clampedTargets = targetsCleared.coerceIn(1, 50).toLong()
        val clampedAccuracy = accuracyPercent.coerceIn(0, 100).toLong()
        val clampedDuration = durationMs.coerceIn(1L, 3600000L)

        return try {
            val batch = db.batch()
            val runPayload = mapOf(
                "userId" to uid,
                "targetsCleared" to clampedTargets,
                "accuracyPercent" to clampedAccuracy,
                "gemsEarned" to GEMS_PER_COMPLETION,
                "durationMs" to clampedDuration,
                "createdAt" to FieldValue.serverTimestamp()
            )
            batch.set(runRef, runPayload)
            batch.update(userRef, buildRunCompletionUserUpdates(clampedTargets))
            batch.commit().await()
            Result.success(runId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, runRef.path)
            Result.failure(e)
        }
    }

    private fun buildRunCompletionUserUpdates(targetsCleared: Long): Map<String, Any> = mapOf(
        "gems" to FieldValue.increment(GEMS_PER_COMPLETION),
        "completedRuns" to FieldValue.increment(1L),
        "totalHits" to FieldValue.increment(targetsCleared),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    suspend fun saveShortcut(shortcut: GestureShortcut): Result<Unit> {
        val uid = requireUserId()
        val docId = shortcut.id.ifBlank { shortcut.direction }
        val docRef = db.collection("users").document(uid).collection("shortcuts").document(docId)
        val safeLabel = shortcut.label.trim().ifBlank { "Shortcut" }.take(40)
        return try {
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "label" to safeLabel,
                        "actionType" to shortcut.actionType,
                        "arrowStyle" to shortcut.arrowStyle,
                        "enabled" to shortcut.enabled,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "direction" to shortcut.direction,
                        "label" to safeLabel,
                        "actionType" to shortcut.actionType,
                        "arrowStyle" to shortcut.arrowStyle,
                        "enabled" to shortcut.enabled,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun unlockAndEquipSkin(skinId: String, costGems: Int): Result<Unit> {
        val uid = requireUserId()
        val userRef = db.collection("users").document(uid)
        return try {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                val currentGems = snapshot.getLong("gems") ?: 0L
                val unlocked = (snapshot.get("unlockedSkins") as? List<*>)
                    ?.mapNotNull { it as? String }
                    ?: listOf("cyan_pulse")
                if (unlocked.contains(skinId)) {
                    transaction.update(
                        userRef,
                        mapOf(
                            "activeSkinId" to skinId,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                } else {
                    if (currentGems < costGems) {
                        throw IllegalStateException("Not enough gems. Need $costGems gems.")
                    }
                    transaction.update(
                        userRef,
                        mapOf(
                            "gems" to currentGems - costGems,
                            "unlockedSkins" to (unlocked + skinId).distinct(),
                            "activeSkinId" to skinId,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
            }.await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            Result.failure(e)
        }
    }

    suspend fun equipSkin(skinId: String): Result<Unit> {
        val uid = requireUserId()
        val userRef = db.collection("users").document(uid)
        return try {
            userRef.update(
                mapOf(
                    "activeSkinId" to skinId,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateHaptics(enabled: Boolean): Result<Unit> {
        val uid = requireUserId()
        val userRef = db.collection("users").document(uid)
        return try {
            userRef.update(
                mapOf(
                    "hapticsEnabled" to enabled,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            Result.failure(e)
        }
    }

    companion object {
        const val GEMS_PER_COMPLETION = 5L

        fun defaultShortcuts(): List<GestureShortcut> = listOf(
            GestureShortcut(
                id = SwipeDirection.UP.id,
                direction = SwipeDirection.UP.id,
                label = "Arena Launch",
                actionType = ShortcutActionType.NAV_ARENA.id,
                arrowStyle = ArrowLaunchStyle.STRAIGHT.id,
                enabled = true
            ),
            GestureShortcut(
                id = SwipeDirection.RIGHT.id,
                direction = SwipeDirection.RIGHT.id,
                label = "Shortcuts",
                actionType = ShortcutActionType.NAV_SHORTCUTS.id,
                arrowStyle = ArrowLaunchStyle.BOLT.id,
                enabled = true
            ),
            GestureShortcut(
                id = SwipeDirection.LEFT.id,
                direction = SwipeDirection.LEFT.id,
                label = "Gem Vault",
                actionType = ShortcutActionType.NAV_VAULT.id,
                arrowStyle = ArrowLaunchStyle.SPLIT.id,
                enabled = true
            ),
            GestureShortcut(
                id = SwipeDirection.DOWN.id,
                direction = SwipeDirection.DOWN.id,
                label = "Quick Reset",
                actionType = ShortcutActionType.QUICK_RESTART.id,
                arrowStyle = ArrowLaunchStyle.ARC.id,
                enabled = true
            ),
            GestureShortcut(
                id = SwipeDirection.UP_RIGHT.id,
                direction = SwipeDirection.UP_RIGHT.id,
                label = "Web Search",
                actionType = ShortcutActionType.OPEN_BROWSER.id,
                arrowStyle = ArrowLaunchStyle.BOLT.id,
                enabled = true
            ),
            GestureShortcut(
                id = SwipeDirection.UP_LEFT.id,
                direction = SwipeDirection.UP_LEFT.id,
                label = "Share Score",
                actionType = ShortcutActionType.SHARE_STATS.id,
                arrowStyle = ArrowLaunchStyle.SPLIT.id,
                enabled = true
            ),
            GestureShortcut(
                id = SwipeDirection.DOWN_RIGHT.id,
                direction = SwipeDirection.DOWN_RIGHT.id,
                label = "Settings",
                actionType = ShortcutActionType.OPEN_SETTINGS.id,
                arrowStyle = ArrowLaunchStyle.STRAIGHT.id,
                enabled = true
            ),
            GestureShortcut(
                id = SwipeDirection.DOWN_LEFT.id,
                direction = SwipeDirection.DOWN_LEFT.id,
                label = "Haptics",
                actionType = ShortcutActionType.TOGGLE_HAPTICS.id,
                arrowStyle = ArrowLaunchStyle.ARC.id,
                enabled = true
            )
        )
    }
}
