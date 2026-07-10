package club.heiqi.config.runtime;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.ConfigNode;
import club.heiqi.config.ConfigSource;
import club.heiqi.config.schema.ConfigSchema;

import java.io.File;


import java.io.FileWriter;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 草稿所有权 fail-closed 与三阶段冲突确定性窗口回归。
 *
 * <p>断言读 {@link SaveOutcome.ConflictType}，禁止英文诊断串匹配。</p>
 */
public class DraftOwnerAndConflictWindowTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static void write(File file, String content) throws Exception {
        FileWriter w = new FileWriter(file);
        try {
            w.write(content);
        } finally {
            w.close();
        }
    }

    /** 同 manager openDraft 的 draft 共享 owner；hasSameOwner 为 true。 */
    @Test
    public void openDraftBindsSameOwnerToken() throws Exception {
        File file = tempFolder.newFile("owner-same.yaml");
        write(file, "");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        DraftBuffer a = manager.openDraft();
        DraftBuffer b = manager.openDraft();
        assertTrue(a.hasSameOwner(b));
        assertTrue(b.hasSameOwner(a));
        assertFalse(a.hasSameOwner(null));
    }

    /** DraftBuffer.from 未绑定 owner；hasSameOwner 为 false；save 拒绝。 */
    @Test
    public void unboundDraftCannotSaveToAnyManager() throws Exception {
        File file = tempFolder.newFile("unbound.yaml");
        write(file, "server:\n  host: localhost\n  port: 8080\n  debug: false\n  mode: online\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        Authority auth = manager.authority();
        DraftBuffer unbound = DraftBuffer.from(auth);
        unbound.setDraft("server.host", "evil.host");

        SaveOutcome outcome = manager.save(unbound);
        assertEquals(SaveOutcome.Status.INVALID, outcome.status());
        assertEquals(SaveOutcome.ConflictType.DRAFT_OWNER_MISMATCH, outcome.conflictType());
        assertFalse(outcome.requiresReload());
        assertEquals("localhost", manager.authority().getString("server.host"));
        // Authority/YAML 零副作用：磁盘仍是写入的 localhost
        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertEquals("localhost", reloaded.get("server.host").asString());
    }



    /** 不同 manager 的 draft 互相 save 均 DRAFT_OWNER_MISMATCH。 */
    @Test
    public void foreignManagerDraftRejectedBeforeValidation() throws Exception {
        File fileA = tempFolder.newFile("mgr-a.yaml");
        File fileB = tempFolder.newFile("mgr-b.yaml");
        write(fileA, "");
        write(fileB, "");
        ConfigManager managerA = ConfigManager.bootstrap(fileA, SchemaTestFactory.serverSchema());
        ConfigManager managerB = ConfigManager.bootstrap(fileB, SchemaTestFactory.serverSchema());
        DraftBuffer draftA = managerA.openDraft();
        DraftBuffer draftB = managerB.openDraft();
        draftA.setDraft("server.host", "from.a");
        draftB.setDraft("server.host", "from.b");

        SaveOutcome aOnB = managerB.save(draftA);
        assertEquals(SaveOutcome.ConflictType.DRAFT_OWNER_MISMATCH, aOnB.conflictType());
        assertFalse(aOnB.requiresReload());
        assertEquals("localhost", managerB.authority().getString("server.host"));

        SaveOutcome bOnA = managerA.save(draftB);
        assertEquals(SaveOutcome.ConflictType.DRAFT_OWNER_MISMATCH, bOnA.conflictType());
        assertFalse(bOnA.requiresReload());
        assertEquals("localhost", managerA.authority().getString("server.host"));
    }

    /**
     * 两 schema 路径/类型/默认相同但约束不同：宽 draft 不能写进窄 manager。
     * 同形异约束 + 不同 manager → DRAFT_OWNER_MISMATCH（owner 先于约束）。
     */
    @Test
    public void sameShapeDifferentConstraintForeignDraftRejected() throws Exception {
        File wideFile = tempFolder.newFile("wide.yaml");
        File narrowFile = tempFolder.newFile("narrow.yaml");
        write(wideFile, "");
        write(narrowFile, "");
        ConfigSchema wide = ConfigSchema.builder("wide")
                .section("server")
                .title("Server")
                .string("host").defaultValue("ab").label("Host")
                .maxLength(100).build()
                .endSection()
                .build();
        ConfigSchema narrow = ConfigSchema.builder("narrow")
                .section("server")
                .title("Server")
                .string("host").defaultValue("ab").label("Host")
                .maxLength(5).build()
                .endSection()
                .build();

        ConfigManager wideMgr = ConfigManager.bootstrap(wideFile, wide);
        ConfigManager narrowMgr = ConfigManager.bootstrap(narrowFile, narrow);
        DraftBuffer wideDraft = wideMgr.openDraft();
        // 宽值：长度 10，对窄 schema maxLength=5 非法
        wideDraft.setDraft("server.host", "toolonghost");

        SaveOutcome outcome = narrowMgr.save(wideDraft);
        // owner 检查在任何 base/validator 前
        assertEquals(SaveOutcome.ConflictType.DRAFT_OWNER_MISMATCH, outcome.conflictType());
        assertFalse(outcome.requiresReload());
        assertEquals("ab", narrowMgr.authority().getString("server.host"));
        // 空 YAML 未写盘：磁盘无 host 或仍为空，Authority 保持默认 ab
        assertTrue(narrowFile.length() == 0
                || Config.load(ConfigSource.fromFile(narrowFile), ConfigFormat.YAML)
                        .get("server.host") == null
                || "ab".equals(String.valueOf(
                        Config.load(ConfigSource.fromFile(narrowFile), ConfigFormat.YAML)
                                .get("server.host").asString())));
    }


    /**
     * 确定性窗口：两个 saver 均完成 capture 并进入 validator 后释放；
     * loser 在 final verify 时精确为 AUTHORITY_MODIFIED_DURING_SAVE 或 SAVE_DURING_NOTIFICATION。
     * 用 latch 拆出「通知期」与「非通知期」两个确定性子窗口。
     */
    @Test(timeout = 15000L)
    public void concurrentCapturedSaversLoserIsAuthorityOrNotification() throws Exception {
        File file = tempFolder.newFile("captured-precise.yaml");
        final CountDownLatch validatorsEntered = new CountDownLatch(2);
        final CountDownLatch releaseValidators = new CountDownLatch(1);
        final CountDownLatch loserReturned = new CountDownLatch(1);
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema(),
                new DraftValidator() {
                    @Override
                    public ValidationResult validate(DraftView view) {
                        validatorsEntered.countDown();
                        try {
                            if (!releaseValidators.await(5, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("validator release timed out");
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("validator interrupted", e);
                        }
                        return ValidationResult.ok();
                    }
                });
        final AtomicInteger batchCount = new AtomicInteger(0);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        manager.eventBus().subscribe(event -> {
            batchCount.incrementAndGet();
            try {
                if (!loserReturned.await(5, TimeUnit.SECONDS)) {
                    failure.compareAndSet(null,
                            new AssertionError("loser did not finish during notification"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                failure.compareAndSet(null, e);
            }
        });

        final DraftBuffer firstDraft = manager.openDraft();
        final DraftBuffer secondDraft = manager.openDraft();
        firstDraft.setDraft("server.host", "first.concurrent.host");
        secondDraft.setDraft("server.host", "second.concurrent.host");
        final AtomicReference<SaveOutcome> firstOutcome = new AtomicReference<SaveOutcome>();
        final AtomicReference<SaveOutcome> secondOutcome = new AtomicReference<SaveOutcome>();

        Thread firstSaver = new Thread(() -> runSave(manager, firstDraft, firstOutcome, loserReturned, failure),
                "first-saver");
        Thread secondSaver = new Thread(() -> runSave(manager, secondDraft, secondOutcome, loserReturned, failure),
                "second-saver");
        firstSaver.start();
        secondSaver.start();
        assertTrue(validatorsEntered.await(5, TimeUnit.SECONDS));
        releaseValidators.countDown();
        firstSaver.join(7000L);
        secondSaver.join(7000L);

        assertFalse(firstSaver.isAlive());
        assertFalse(secondSaver.isAlive());
        assertNullFailure(failure);
        assertNotNull(firstOutcome.get());
        assertNotNull(secondOutcome.get());
        assertEquals(1, batchCount.get());
        int successes = (firstOutcome.get().isSuccess() ? 1 : 0)
                + (secondOutcome.get().isSuccess() ? 1 : 0);
        assertEquals(1, successes);

        SaveOutcome loserOutcome = firstOutcome.get().isSuccess()
                ? secondOutcome.get() : firstOutcome.get();
        assertEquals(SaveOutcome.Status.INVALID, loserOutcome.status());
        // 本窗口：双方均已 capture，loser 在 final verify 时 Authority 已变或进入通知期
        SaveOutcome.ConflictType type = loserOutcome.conflictType();
        assertTrue("loser 应为 AUTHORITY_MODIFIED 或 SAVE_DURING_NOTIFICATION，实际=" + type,
                type == SaveOutcome.ConflictType.AUTHORITY_MODIFIED_DURING_SAVE
                        || type == SaveOutcome.ConflictType.SAVE_DURING_NOTIFICATION);
        // 本窗口不会是 STALE_DRAFT_BASE（双方 base 在 capture 时均有效）
        assertFalse(type == SaveOutcome.ConflictType.STALE_DRAFT_BASE);
        assertFalse(type == SaveOutcome.ConflictType.DRAFT_OWNER_MISMATCH);
    }

    /**
     * 确定性 STALE 窗口：先串行成功保存一方，再 save 同源旧 draft → 精确 STALE_DRAFT_BASE。
     */
    @Test
    public void sequentialSaveLoserIsExactlyStaleDraftBase() throws Exception {
        File file = tempFolder.newFile("stale-exact.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        DraftBuffer first = manager.openDraft();
        DraftBuffer stale = manager.openDraft();
        first.setDraft("server.host", "winner.host");
        stale.setDraft("server.host", "stale.host");
        assertTrue(manager.save(first).isSuccess());
        SaveOutcome second = manager.save(stale);
        assertEquals(SaveOutcome.ConflictType.STALE_DRAFT_BASE, second.conflictType());
        assertTrue(second.requiresReload());
        assertEquals("winner.host", manager.authority().getString("server.host"));
    }

    /**
     * 确定性通知期窗口：BATCH_SAVE listener 内同步 save → 精确 SAVE_DURING_NOTIFICATION。
     */
    @Test
    public void notificationWindowLoserIsExactlySaveDuringNotification() throws Exception {
        File file = tempFolder.newFile("notify-exact.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        final AtomicReference<SaveOutcome> nested = new AtomicReference<SaveOutcome>();
        manager.eventBus().subscribe(event -> {
            nested.set(manager.save(manager.openDraft()));
        });
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "event.host");
        SaveOutcome outer = manager.save(draft);
        assertTrue(outer.isSuccess());
        assertNotNull(nested.get());
        assertEquals(SaveOutcome.ConflictType.SAVE_DURING_NOTIFICATION, nested.get().conflictType());
        assertFalse(nested.get().requiresReload());
    }

    private static void runSave(ConfigManager manager, DraftBuffer draft,
                                AtomicReference<SaveOutcome> outcomeRef,
                                CountDownLatch loserReturned,
                                AtomicReference<Throwable> failure) {
        try {
            SaveOutcome outcome = manager.save(draft);
            outcomeRef.set(outcome);
            if (outcome.status() == SaveOutcome.Status.INVALID) {
                loserReturned.countDown();
            }
        } catch (Throwable e) {
            failure.compareAndSet(null, e);
            loserReturned.countDown();
        }
    }

    private static void assertNullFailure(AtomicReference<Throwable> failure) {
        Throwable t = failure.get();
        if (t != null) {
            throw new AssertionError("concurrent failure", t);
        }
    }
}
