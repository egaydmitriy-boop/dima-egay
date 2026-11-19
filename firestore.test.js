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
const CHARLIE_UID = "charlie_789";
const WS_ID = "mossnode-core";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
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

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauth = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauth.collection("users").doc(ALICE_UID).get());
  await assertFails(unauth.collection("workspaces").where("memberUids", "array-contains", ALICE_UID).get());
  await assertFails(
    unauth.collection("workspaces").doc(WS_ID).set({
      ownerId: ALICE_UID,
      name: "Test",
      mission: "Build server",
      memberUids: [ALICE_UID],
      createdAt: new Date(Date.now() - 1000),
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("User profile isolation and strict schema validation", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const now = new Date(Date.now() - 1000);

  await assertSucceeds(
    alice.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Дима",
      email: "dima@example.com",
      role: "Infra Lead",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Ghost field rejected
  await assertFails(
    alice.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Cross-user PII read rejected
  await assertFails(bob.collection("users").doc(ALICE_UID).get());
});

test("Workspace creation, member join, and Kanban task + dependency CRUD", async () => {
  const alice = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bob = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlie = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const t0 = new Date(Date.now() - 10000);

  // Alice creates workspace
  await assertSucceeds(
    alice.collection("workspaces").doc(WS_ID).set({
      ownerId: ALICE_UID,
      name: "MossNode AI",
      mission: "Сервер локальных ИИ для инвестора",
      memberUids: [ALICE_UID],
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // Bob joins workspace by adding only himself to memberUids
  await assertSucceeds(
    bob.collection("workspaces").doc(WS_ID).update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: new Date(Date.now() - 5000),
    })
  );

  // Bob cannot hijack ownerId or spoof another member
  await assertFails(
    charlie.collection("workspaces").doc(WS_ID).update({
      ownerId: CHARLIE_UID,
      memberUids: [ALICE_UID, BOB_UID, CHARLIE_UID],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Unfiltered workspace query fails; filtered succeeds
  await assertFails(alice.collection("workspaces").get());
  await assertSucceeds(
    alice.collection("workspaces").where("memberUids", "array-contains", ALICE_UID).get()
  );

  // Alice creates a Kanban task (no @DocumentId `id` key)
  await assertSucceeds(
    alice.collection("workspaces").doc(WS_ID).collection("tasks").doc("task_gpu").set({
      workspaceId: WS_ID,
      authorUid: ALICE_UID,
      title: "Настроить 4x RTX 4090 и CUDA",
      description: "Собрать узел под vLLM",
      department: "GPU & Железо",
      status: "IN_PROGRESS",
      priority: "CRITICAL",
      assigneeId: "mem_1",
      assigneeName: "Дима",
      dueDateIso: "2026-11-24",
      dueTimeText: "18:00",
      estimatedHours: 16,
      blockedByTaskIds: [],
      sourceType: "MANUAL",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // Reject payload containing @DocumentId `id`
  await assertFails(
    alice.collection("workspaces").doc(WS_ID).collection("tasks").doc("task_bad").set({
      id: "task_bad",
      workspaceId: WS_ID,
      authorUid: ALICE_UID,
      title: "Bad task",
      description: "",
      department: "API",
      status: "TODO",
      priority: "NORMAL",
      assigneeId: "",
      assigneeName: "",
      dueDateIso: "2026-11-25",
      dueTimeText: "12:00",
      estimatedHours: 4,
      blockedByTaskIds: [],
      sourceType: "MANUAL",
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // Bob updates task status to TESTING and adds dependency
  await assertSucceeds(
    bob.collection("workspaces").doc(WS_ID).collection("tasks").doc("task_gpu").update({
      status: "TESTING",
      blockedByTaskIds: ["task_rack"],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject future timestamp
  await assertFails(
    bob.collection("workspaces").doc(WS_ID).collection("tasks").doc("task_gpu").update({
      status: "DONE",
      updatedAt: new Date(Date.now() + 60000),
    })
  );

  // Charlie (non-member) cannot read Alice's task, run unfiltered query, or query Alice's memberUids
  await assertFails(
    charlie.collection("workspaces").doc(WS_ID).collection("tasks").doc("task_gpu").get()
  );
  await assertFails(
    charlie.collection("workspaces").doc(WS_ID).collection("tasks").get()
  );
  await assertFails(
    charlie.collection("workspaces").doc(WS_ID).collection("tasks").where("memberUids", "array-contains", ALICE_UID).get()
  );
  const charlieSnap = await assertSucceeds(
    charlie.collection("workspaces").doc(WS_ID).collection("tasks").where("memberUids", "array-contains", CHARLIE_UID).get()
  );
  if (charlieSnap.size !== 0) throw new Error("Expected 0 tasks for non-member");

  // Alice posts shared AI chat message with YouTube link
  await assertSucceeds(
    alice.collection("workspaces").doc(WS_ID).collection("chat_messages").doc("msg_1").set({
      workspaceId: WS_ID,
      senderUid: ALICE_UID,
      senderName: "Дима",
      role: "USER",
      text: "Глянь это видео по vLLM: https://youtu.be/dQw4w9WgXcQ",
      youtubeUrl: "https://youtu.be/dQw4w9WgXcQ",
      linkedTaskIds: [],
      memberUids: [ALICE_UID, BOB_UID],
      createdAt: t0,
      updatedAt: t0,
    })
  );

  // Bob can read shared chat messages via memberUids query
  await assertSucceeds(
    bob.collection("workspaces").doc(WS_ID).collection("chat_messages").where("memberUids", "array-contains", BOB_UID).get()
  );
});
