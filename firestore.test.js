const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read user profile", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("Unauthenticated user: cannot read user sessions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("sessions").get());
});

test("Authenticated user: cannot read another user's profile", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      dailyStudyGoalMinutes: 60,
      currentStreak: 1,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user: cannot spoof another user's userId on create", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: BOB_UID,
      displayName: "Alice Spoof",
      email: "alice@example.com",
      dailyStudyGoalMinutes: 60,
      currentStreak: 1,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
});

test("Authenticated user: can read and write their own profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      email: "alice@example.com",
      dailyStudyGoalMinutes: 120,
      currentStreak: 5,
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
});

test("Authenticated user: can write their own session record", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1").set({
      id: "sess_1",
      userId: ALICE_UID,
      mode: "POMODORO",
      durationMinutes: 25,
      completedAt: new Date(),
      createdAt: new Date(),
    })
  );
});

test("Cross-user isolation: Bob cannot read Alice's sessions", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1").set({
      id: "sess_1",
      userId: ALICE_UID,
      mode: "POMODORO",
      durationMinutes: 25,
      completedAt: new Date(),
      createdAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1").get()
  );
});

test("Authenticated user: can create and read their own study plan", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("study_plans").doc("plan_1").set({
      id: "plan_1",
      userId: ALICE_UID,
      title: "Calculus Review",
      subject: "Mathematics",
      targetMinutes: 60,
      isCompleted: false,
      scheduledDate: "2026-10-31",
      createdAt: new Date(),
      updatedAt: new Date(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("study_plans").get()
  );
});

test("Authenticated user: can sync study_subjects, blocked_apps (with dots), blocked_websites, blocked_keywords, scratch_cards, and user_preferences", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("study_subjects").doc("sub_1").set({
      id: "sub_1",
      userId: ALICE_UID,
      name: "Physics",
      colorHex: "#7C3AED",
      iconIdentifier: "physics",
      isArchived: false,
      createdAt: Date.now(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("blocked_apps").doc("com.google.android.youtube").set({
      packageName: "com.google.android.youtube",
      userId: ALICE_UID,
      appName: "YouTube",
      isEnabled: true,
      createdAt: Date.now(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("blocked_websites").doc("instagram.com").set({
      domain: "instagram.com",
      userId: ALICE_UID,
      isEnabled: true,
      category: "MANUAL",
      createdAt: Date.now(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("blocked_keywords").doc("block:5:prank").set({
      userId: ALICE_UID,
      keyword_type: "block",
      keyword_value: "prank",
      is_active: true,
      created_at: Date.now(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("scratch_cards").doc("sess_1").set({
      sessionId: "sess_1",
      userId: ALICE_UID,
      createdAt: Date.now(),
      rewardType: "QUOTE",
      rewardEmoji: "✨",
      rewardTitle: "Deep Work",
      rewardMessage: "Keep going!",
      studyMinutes: 25,
      isRevealed: true,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("user_preferences").doc("default").set({
      userId: ALICE_UID,
      preferencesJson: "{\"dailyGoalMinutes\":180}",
      updatedAt: Date.now(),
    })
  );
});

test("Motivational quotes: public read is allowed, client writes are denied", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("session_quotes").doc("quote_1").set({
      quoteId: "quote_1",
      quoteText: "Stay focused and keep building.",
      category: "FOCUS",
      authorOrTone: "Scholar",
      active: true,
    });
  });

  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Public read allowed for both unauthenticated and authenticated clients
  await assertSucceeds(unauthDb.collection("session_quotes").doc("quote_1").get());
  await assertSucceeds(aliceDb.collection("session_quotes").doc("quote_1").get());

  // Client-side create, update, and delete are strictly denied
  await assertFails(
    unauthDb.collection("session_quotes").doc("quote_2").set({
      quoteId: "quote_2",
      quoteText: "Injected quote",
    })
  );
  await assertFails(
    aliceDb.collection("session_quotes").doc("quote_2").set({
      quoteId: "quote_2",
      quoteText: "Injected quote by authenticated user",
    })
  );
  await assertFails(
    aliceDb.collection("session_quotes").doc("quote_1").update({
      quoteText: "Tampered quote text",
    })
  );
  await assertFails(
    aliceDb.collection("session_quotes").doc("quote_1").delete()
  );
});


