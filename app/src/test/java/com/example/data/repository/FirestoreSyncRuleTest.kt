package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class FirestoreSyncRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun userProfile_ownerCanWriteAndReadOwnProfile() = runBlocking {
    val uid = signInTestUser("alice@example.com")

    val docRef = firestore.collection("users").document(uid)
    val payload = hashMapOf<String, Any>(
        "userId" to uid,
        "displayName" to "Alice",
        "email" to "alice@example.com",
        "dailyStudyGoalMinutes" to 60,
        "currentStreak" to 3,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    withTimeout(DEFAULT_TIMEOUT_MS) {
      docRef.set(payload).await()
    }

    val snapshot = withTimeout(DEFAULT_TIMEOUT_MS) {
      docRef.get().await()
    }
    assertTrue(snapshot.exists())
    assertEquals(uid, snapshot.getString("userId"))
    assertEquals("Alice", snapshot.getString("displayName"))
  }

  @Test
  fun sessionRecord_ownerCanCreateSession() = runBlocking {
    val uid = signInTestUser("alice@example.com")
    val sessionId = "session_${UUID.randomUUID()}"

    val sessionDoc = firestore.collection("users").document(uid).collection("sessions").document(sessionId)
    val payload = hashMapOf<String, Any>(
        "id" to sessionId,
        "userId" to uid,
        "mode" to "POMODORO",
        "durationMinutes" to 25,
        "completedAt" to FieldValue.serverTimestamp(),
        "createdAt" to FieldValue.serverTimestamp()
    )

    withTimeout(DEFAULT_TIMEOUT_MS) {
      sessionDoc.set(payload).await()
    }

    val snapshot = withTimeout(DEFAULT_TIMEOUT_MS) {
      sessionDoc.get().await()
    }
    assertTrue(snapshot.exists())
    assertEquals("POMODORO", snapshot.getString("mode"))
  }

  @Test
  fun crossUser_bobCannotReadAliceProfile() = runBlocking {
    val aliceUid = signInTestUser("alice@example.com")

    val aliceDoc = firestore.collection("users").document(aliceUid)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceDoc.set(
          hashMapOf(
              "userId" to aliceUid,
              "displayName" to "Alice",
              "email" to "alice@example.com",
              "dailyStudyGoalMinutes" to 60,
              "currentStreak" to 3,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp()
          )
      ).await()
    }

    // Sign in as Bob
    signInTestUser("bob@example.com")

    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceDoc.get().await()
      }
      fail("Expected FirebaseFirestoreException when Bob reads Alice's doc")
    } catch (e: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
    }
  }

  @Test
  fun unauthenticatedUser_cannotReadUserProfile() = runBlocking {
    val aliceUid = signInTestUser("alice@example.com")
    val aliceDoc = firestore.collection("users").document(aliceUid)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceDoc.set(
          hashMapOf(
              "userId" to aliceUid,
              "displayName" to "Alice",
              "email" to "alice@example.com",
              "dailyStudyGoalMinutes" to 60,
              "currentStreak" to 3,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp()
          )
      ).await()
    }

    auth.signOut()

    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        aliceDoc.get().await()
      }
      fail("Expected FirebaseFirestoreException when unauthenticated caller reads user profile")
    } catch (e: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
    }
  }

  private companion object {
    const val DEFAULT_TIMEOUT_MS = 5000L
  }
}
