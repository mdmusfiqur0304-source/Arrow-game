package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.ArrowLaunchStyle
import com.example.data.model.GestureShortcut
import com.example.data.model.ShortcutActionType
import com.example.data.model.SwipeDirection
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class ArrowSwipeRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserInitialized_createsProfileWithZeroGemsAndDefaultShortcuts() = runBlocking {
        val uid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val repository = ArrowSwipeRepository(firestore, auth)

        val initResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureUserInitialized("Alice")
        }
        assertTrue("Expected initialization to succeed: ${initResult.exceptionOrNull()}", initResult.isSuccess)

        val profile = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeProfile(uid).first { it != null }
        }
        assertNotNull(profile)
        assertEquals(0L, profile!!.gems)
        assertEquals(0L, profile.completedRuns)

        val shortcuts = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeShortcuts(uid).first { it.size == 8 }
        }
        assertEquals(8, shortcuts.size)
    }

    @Test
    fun completeRunAndAwardGems_awardsExactlyFiveGemsPerCompletion() = runBlocking {
        val uid = signInTestUser("runner_${UUID.randomUUID()}@test.com")
        val repository = ArrowSwipeRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureUserInitialized("Runner").getOrThrow()
        }

        val firstRun = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.completeRunAndAwardGems(targetsCleared = 6, accuracyPercent = 90, durationMs = 15000L)
        }
        assertTrue(firstRun.isSuccess)

        val secondRun = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.completeRunAndAwardGems(targetsCleared = 6, accuracyPercent = 100, durationMs = 12000L)
        }
        assertTrue(secondRun.isSuccess)

        val profile = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeProfile(uid).first { it != null && it.gems == 10L }
        }
        assertEquals(10L, profile!!.gems)
        assertEquals(2L, profile.completedRuns)
    }

    @Test
    fun fetchGemBalanceFromServer_fetchesInitialAndUpdatedBalanceAfterSession() = runBlocking {
        val uid = signInTestUser("gemsync_${UUID.randomUUID()}@test.com")
        val repository = ArrowSwipeRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureUserInitialized("GemSync").getOrThrow()
        }

        val initialBalance = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.fetchGemBalanceFromServer(uid).getOrThrow()
        }
        assertEquals(0L, initialBalance)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.completeRunAndAwardGems(targetsCleared = 6, accuracyPercent = 95, durationMs = 11000L).getOrThrow()
        }

        val postSessionBalance = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.fetchGemBalanceFromServer(uid).getOrThrow()
        }
        assertEquals(5L, postSessionBalance)
    }

    @Test
    fun addFiveGemsOnRunCompletion_updatesUserDocumentByFiveGems() = runBlocking {
        val uid = signInTestUser("directadd_${UUID.randomUUID()}@test.com")
        val repository = ArrowSwipeRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureUserInitialized("DirectAdd").getOrThrow()
        }

        val afterFirst = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addFiveGemsOnRunCompletion(targetsCleared = 6).getOrThrow()
        }
        assertEquals(5L, afterFirst)

        val afterSecond = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addFiveGemsOnRunCompletion(targetsCleared = 6).getOrThrow()
        }
        assertEquals(10L, afterSecond)

        val profile = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeProfile(uid).first { it != null && it.gems == 10L }
        }
        assertEquals(10L, profile!!.gems)
        assertEquals(2L, profile.completedRuns)
    }

    @Test
    fun saveShortcut_updatesCustomizableGestureShortcut() = runBlocking {
        val uid = signInTestUser("customizer_${UUID.randomUUID()}@test.com")
        val repository = ArrowSwipeRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureUserInitialized("Customizer").getOrThrow()
        }

        val updatedShortcut = GestureShortcut(
            id = SwipeDirection.UP_RIGHT.id,
            userId = uid,
            direction = SwipeDirection.UP_RIGHT.id,
            label = "Vault Jump",
            actionType = ShortcutActionType.NAV_VAULT.id,
            arrowStyle = ArrowLaunchStyle.SPLIT.id,
            enabled = true
        )

        val saveResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.saveShortcut(updatedShortcut)
        }
        assertTrue(saveResult.isSuccess)

        val shortcuts = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeShortcuts(uid).first { list ->
                list.any { it.direction == SwipeDirection.UP_RIGHT.id && it.label == "Vault Jump" }
            }
        }
        assertTrue(shortcuts.any { it.actionType == ShortcutActionType.NAV_VAULT.id })
    }

    @Test
    fun observeProfile_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_iso_${UUID.randomUUID()}@test.com")
        val aliceRepo = ArrowSwipeRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.ensureUserInitialized("Alice").getOrThrow()
        }

        signInTestUser("bob_iso_${UUID.randomUUID()}@test.com")
        val bobRepo = ArrowSwipeRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                bobRepo.observeProfile(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED when Bob observes Alice's profile")
        } catch (e: Exception) {
            val firestoreEx = checkNotNull(e.unwrapFirestoreException()) {
                "Expected nested FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
        }
    }

    @Test
    fun observeShortcuts_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = ArrowSwipeRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeShortcuts("some_user_id").first()
            }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val firestoreEx = checkNotNull(e.unwrapFirestoreException()) {
                "Expected nested FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx.code)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
