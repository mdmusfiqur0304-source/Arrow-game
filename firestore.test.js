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

function nowPast() {
  return new Date(Date.now() - 1000);
}

function makeValidProfile(uid, ts) {
  return {
    userId: uid,
    displayName: "Arrow Player",
    gems: 0,
    completedRuns: 0,
    totalHits: 0,
    activeSkinId: "cyan_pulse",
    unlockedSkins: ["cyan_pulse"],
    hapticsEnabled: true,
    createdAt: ts,
    updatedAt: ts,
  };
}

test("Unauthenticated user cannot read or write users, shortcuts, or runs", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("shortcuts").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("runs").get());
});

test("Owner can create valid initial profile with 0 gems", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(makeValidProfile(ALICE_UID, nowPast()))
  );
});

test("Owner cannot create profile with forged starting gems != 0", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const badProfile = makeValidProfile(ALICE_UID, nowPast());
  badProfile.gems = 500;
  await assertFails(aliceDb.collection("users").doc(ALICE_UID).set(badProfile));
});

test("Cross-user access to profile, shortcuts, and runs is denied", async () => {
  const ts = nowPast();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(makeValidProfile(ALICE_UID, ts))
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("shortcuts").doc("UP").set({
      userId: ALICE_UID,
      direction: "UP",
      label: "Arena",
      actionType: "NAV_ARENA",
      arrowStyle: "STRAIGHT",
      enabled: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("shortcuts").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("runs").get());
});

test("Owner receives exactly +5 gems when completing a run, and cannot forge +100 gems", async () => {
  const ts = nowPast();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profileRef = aliceDb.collection("users").doc(ALICE_UID);

  await assertSucceeds(profileRef.set(makeValidProfile(ALICE_UID, ts)));

  // Valid +5 gems run completion
  await assertSucceeds(
    profileRef.update({
      gems: 5,
      completedRuns: 1,
      totalHits: 6,
      updatedAt: nowPast(),
    })
  );

  // Forged +100 gems update must fail
  await assertFails(
    profileRef.update({
      gems: 105,
      completedRuns: 2,
      totalHits: 12,
      updatedAt: nowPast(),
    })
  );
});

test("CompletedRun requires gemsEarned == 5 and rejects updates/deletes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const runRef = aliceDb.collection("users").doc(ALICE_UID).collection("runs").doc("run_1");

  await assertSucceeds(
    runRef.set({
      userId: ALICE_UID,
      targetsCleared: 6,
      accuracyPercent: 92,
      gemsEarned: 5,
      durationMs: 18400,
      createdAt: nowPast(),
    })
  );

  // Updating or deleting an immutable run fails
  await assertFails(runRef.update({ gemsEarned: 50 }));
  await assertFails(runRef.delete());

  // Creating a run with gemsEarned != 5 fails
  const badRunRef = aliceDb.collection("users").doc(ALICE_UID).collection("runs").doc("run_2");
  await assertFails(
    badRunRef.set({
      userId: ALICE_UID,
      targetsCleared: 6,
      accuracyPercent: 92,
      gemsEarned: 10,
      durationMs: 18400,
      createdAt: nowPast(),
    })
  );
});

test("Owner can create and update customizable swipeable gesture shortcuts", async () => {
  const ts = nowPast();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const shortcutRef = aliceDb.collection("users").doc(ALICE_UID).collection("shortcuts").doc("UP_RIGHT");

  await assertSucceeds(
    shortcutRef.set({
      userId: ALICE_UID,
      direction: "UP_RIGHT",
      label: "Gem Vault",
      actionType: "NAV_VAULT",
      arrowStyle: "BOLT",
      enabled: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(
    shortcutRef.update({
      label: "Quick Restart",
      actionType: "QUICK_RESTART",
      arrowStyle: "SPLIT",
      enabled: false,
      updatedAt: nowPast(),
    })
  );

  // Ghost field shadow update must fail
  await assertFails(
    shortcutRef.update({
      label: "Hack",
      ghostField: true,
      updatedAt: nowPast(),
    })
  );
});
