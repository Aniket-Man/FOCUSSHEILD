package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.Date
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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

    docRef.set(payload).await()

    val snapshot = docRef.get().await()
    assertTrue(snapshot.exists())
    assertEquals(uid, snapshot.getString("userId"))
    assertEquals("Alice", snapshot.getString("displayName"))
  }

  @Test
  fun sessionRecord_ownerCanCreateSession() = runBlocking {
    val uid = signInTestUser("alice@example.com")

    val sessionDoc = firestore.collection("users").document(uid).collection("sessions").document("session_1")
    val payload = hashMapOf<String, Any>(
        "id" to "session_1",
        "userId" to uid,
        "mode" to "POMODORO",
        "durationMinutes" to 25,
        "completedAt" to FieldValue.serverTimestamp(),
        "createdAt" to FieldValue.serverTimestamp()
    )

    sessionDoc.set(payload).await()

    val snapshot = sessionDoc.get().await()
    assertTrue(snapshot.exists())
    assertEquals("POMODORO", snapshot.getString("mode"))
  }

  @Test
  fun crossUser_bobCannotReadAliceProfile() = runBlocking {
    val aliceUid = signInTestUser("alice@example.com")

    val aliceDoc = firestore.collection("users").document(aliceUid)
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

    // Sign in as Bob
    signInTestUser("bob@example.com")

    try {
      aliceDoc.get().await()
      fail("Expected FirebaseFirestoreException when Bob reads Alice's doc")
    } catch (e: FirebaseFirestoreException) {
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
    }
  }
}
