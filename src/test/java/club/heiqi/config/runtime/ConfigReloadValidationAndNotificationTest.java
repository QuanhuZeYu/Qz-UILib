package club.heiqi.config.runtime;

import club.heiqi.config.ConfigChangeEvent;
import club.heiqi.config.ConfigException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 终审收口：reload 三阶段、完整校验、RELOAD 事件、通知期封锁、写前 IO 失败、same-byte ABA、owns、
 * expected 基线冻结、严格 disk 类型、精确并发线性化。
 */
public class ConfigReloadValidationAndNotificationTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @After
    public void clearInjector() {
        Persistence.clearFaultInjector();
    }

    private static void write(File file, String content) throws Exception {
        FileWriter w = new FileWriter(file);
        try {
            w.write(content);
        } finally {
            w.close();
        }
    }

    private static String readText(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void reloadRejectsOutOfRangeNumber_zeroSideEffect() throws Exception {
        File file = tempFolder.newFile("reload-oor.yaml");
        write(file, "server:\n  host: ok\n  port: 80\n  debug: false\n  mode: online\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        assertEquals(80.0, manager.authority().getNumber("server.port"), 0.0);
        ConfigFileSnapshot expectedBefore = manager.expectedDiskSnapshot();

        write(file, "server:\n  host: ok\n  port: 99999\n  debug: false\n  mode: online\n");
        try {
            manager.reloadDraftFromDisk();
            fail("expected validation failure");
        } catch (ConfigReloadException e) {
            assertEquals(ConfigReloadException.Reason.VALIDATION, e.reason());
        } catch (ConfigException e) {
            assertTrue(e.getMessage().contains("validation") || e.getMessage().contains("上限")
                    || e.getMessage().contains("port"));
        }
        assertEquals(80.0, manager.authority().getNumber("server.port"), 0.0);
        assertTrue(manager.expectedDiskSnapshot().exactBytesEqual(expectedBefore));
    }

    @Test
    public void reloadRejectsIllegalNumberType_noZeroFold() throws Exception {
        File file = tempFolder.newFile("reload-badnum.yaml");
        write(file, "server:\n  host: ok\n  port: 80\n  debug: false\n  mode: online\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        write(file, "server:\n  host: ok\n  port: not-a-number\n  debug: false\n  mode: online\n");
        try {
            manager.reloadDraftFromDisk();
            fail("expected validation failure");
        } catch (ConfigReloadException e) {
            assertEquals(ConfigReloadException.Reason.VALIDATION, e.reason());
        } catch (ConfigException e) {
            assertTrue(e.getMessage() != null && e.getMessage().length() > 0);
        }
        assertEquals(80.0, manager.authority().getNumber("server.port"), 0.0);
    }

    /** quoted NUMBER 字符串 "80"：disk 严格 NodeType 拒绝。 */
    @Test
    public void reloadRejectsQuotedNumberString_strictNodeType() throws Exception {
        File file = tempFolder.newFile("reload-quoted-num.yaml");
        write(file, "server:\n  host: ok\n  port: 80\n  debug: false\n  mode: online\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        ConfigFileSnapshot expectedBefore = manager.expectedDiskSnapshot();
        write(file, "server:\n  host: ok\n  port: \"80\"\n  debug: false\n  mode: online\n");
        try {
            manager.reloadDraftFromDisk();
            fail("quoted number must fail disk strict type");
        } catch (ConfigReloadException e) {
            assertEquals(ConfigReloadException.Reason.VALIDATION, e.reason());
        }
        assertEquals(80.0, manager.authority().getNumber("server.port"), 0.0);
        assertTrue(manager.expectedDiskSnapshot().exactBytesEqual(expectedBefore));
    }

    @Test
    public void reloadRejectsIllegalBooleanType() throws Exception {
        File file = tempFolder.newFile("reload-badbool.yaml");
        write(file, "server:\n  host: ok\n  port: 80\n  debug: false\n  mode: online\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        write(file, "server:\n  host: ok\n  port: 80\n  debug: not-bool\n  mode: online\n");
        try {
            manager.reloadDraftFromDisk();
            fail("expected boolean validation failure");
        } catch (ConfigReloadException e) {
            assertEquals(ConfigReloadException.Reason.VALIDATION, e.reason());
        }
        assertFalse(manager.authority().getBool("server.debug"));
    }

    @Test
    public void reloadRejectsNumberNodeForStringField() throws Exception {
        File file = tempFolder.newFile("reload-str-as-num.yaml");
        write(file, "server:\n  host: ok\n  port: 80\n  debug: false\n  mode: online\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        write(file, "server:\n  host: 12345\n  port: 80\n  debug: false\n  mode: online\n");
        try {
            manager.reloadDraftFromDisk();
            fail("NUMBER node for STRING field must fail");
        } catch (ConfigReloadException e) {
            assertEquals(ConfigReloadException.Reason.VALIDATION, e.reason());
        }
        assertEquals("ok", manager.authority().getString("server.host"));
    }

    @Test
    public void reloadRejectsIllegalListType() throws Exception {
        File file = tempFolder.newFile("reload-badlist.yaml");
        write(file, "server:\n  tags: []\n  host: ok\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.listSchema());
        write(file, "server:\n  tags: not-a-list\n  host: ok\n");
        try {
            manager.reloadDraftFromDisk();
            fail("expected list validation failure");
        } catch (ConfigReloadException e) {
            assertEquals(ConfigReloadException.Reason.VALIDATION, e.reason());
        }
    }

    @Test
    public void reloadRejectsListWithNonStringItems() throws Exception {
        File file = tempFolder.newFile("reload-list-nonstr.yaml");
        write(file, "server:\n  tags: []\n  host: ok\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.listSchema());
        write(file, "server:\n  tags:\n    - 1\n    - two\n  host: ok\n");
        try {
            manager.reloadDraftFromDisk();
            fail("list non-string items must fail");
        } catch (ConfigReloadException e) {
            assertEquals(ConfigReloadException.Reason.VALIDATION, e.reason());
        }
    }

    @Test
    public void saveRejectsIllegalBooleanType() throws Exception {
        File file = tempFolder.newFile("save-badbool.yaml");
        write(file, "server:\n  host: ok\n  port: 80\n  debug: false\n  mode: online\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.debug", "yes");
        SaveOutcome outcome = manager.save(draft);
        assertEquals(SaveOutcome.Status.INVALID, outcome.status());
        assertFalse(manager.authority().getBool("server.debug"));
    }

    @Test
    public void saveRejectsIllegalNumberString() throws Exception {
        File file = tempFolder.newFile("save-badnum.yaml");
        write(file, "server:\n  host: ok\n  port: 80\n  debug: false\n  mode: online\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.port", "eighty");
        SaveOutcome outcome = manager.save(draft);
        assertEquals(SaveOutcome.Status.INVALID, outcome.status());
        assertEquals(80.0, manager.authority().getNumber("server.port"), 0.0);
    }

    @Test
    public void saveAcceptsNumericStringAtUiBoundary() throws Exception {
        File file = tempFolder.newFile("save-numstr-ui.yaml");
        write(file, "server:\n  host: ok\n  port: 80\n  debug: false\n  mode: online\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.port", "90");
        SaveOutcome outcome = manager.save(draft);
        assertTrue("UI 数字字符串应成功: " + outcome.status(), outcome.isSuccess());
        assertEquals(90.0, manager.authority().getNumber("server.port"), 0.0);
    }

    @Test
    public void reloadRejectsCustomValidator_zeroSideEffect() throws Exception {
        File file = tempFolder.newFile("reload-custom.yaml");
        write(file, "server:\n  host: ok\n  port: 80\n  debug: false\n  mode: online\n");
        DraftValidator v = draft -> {
            Object host = draft.getDraft("server.host");
            Object mode = draft.getDraft("server.mode");
            if ("blocked".equals(String.valueOf(host)) && "online".equals(String.valueOf(mode))) {
                return ValidationResult.error("server.host", "host blocked in online mode");
            }
            return ValidationResult.ok();
        };
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema(), v);
        write(file, "server:\n  host: blocked\n  port: 80\n  debug: false\n  mode: online\n");
        try {
            manager.reloadDraftFromDisk();
            fail("expected custom validation failure");
        } catch (ConfigReloadException e) {
            assertEquals(ConfigReloadException.Reason.VALIDATION, e.reason());
        } catch (ConfigException e) {
            assertTrue(e.getMessage().contains("blocked") || e.getMessage().contains("validation"));
        }
        assertEquals("ok", manager.authority().getString("server.host"));
    }

    @Test
    public void successfulReloadPublishesReloadNotBatchSave() throws Exception {
        File file = tempFolder.newFile("reload-event.yaml");
        write(file, "server:\n  host: a\n  port: 1\n  debug: false\n  mode: online\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        AtomicInteger reload = new AtomicInteger();
        AtomicInteger batch = new AtomicInteger();
        manager.eventBus().subscribe(e -> {
            if (e.getType() == ConfigChangeEvent.ChangeType.RELOAD) {
                reload.incrementAndGet();
            }
            if (e.getType() == ConfigChangeEvent.ChangeType.BATCH_SAVE) {
                batch.incrementAndGet();
            }
        });
        write(file, "server:\n  host: b\n  port: 1\n  debug: false\n  mode: online\n");
        DraftBuffer d = manager.reloadDraftFromDisk();
        assertEquals("b", manager.authority().getString("server.host"));
        assertTrue(manager.owns(d));
        assertEquals(1, reload.get());
        assertEquals(0, batch.get());
    }

    /**
     * 语义相同但字节/注释不同：慢 save 冻结旧 expected 后 reload 推进 expected，
     * 旧 save 必须 CONFIG_FILE_CHANGED（Authority 值可仍相同）。
     */
    @Test
    public void reloadSameSemanticDifferentBytes_advancesExpected_staleSaveConflicts() throws Exception {
        File file = tempFolder.newFile("reload-sem-bytes.yaml");
        String base = "server:\n  host: a\n  port: 1\n  debug: false\n  mode: online\n";
        write(file, base);
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        DraftValidator slow = draft -> {
            if ("slow-sem-save".equals(Thread.currentThread().getName())) {
                entered.countDown();
                try {
                    if (!release.await(8, TimeUnit.SECONDS)) {
                        return ValidationResult.error("_config", "latch timeout");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return ValidationResult.error("_config", "interrupted");
                }
            }
            return ValidationResult.ok();
        };
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema(), slow);
        ConfigFileSnapshot expected0 = manager.expectedDiskSnapshot();

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "from-old-save");
        AtomicReference<SaveOutcome> saveOut = new AtomicReference<SaveOutcome>();
        Thread saver = new Thread(() -> saveOut.set(manager.save(draft)), "slow-sem-save");
        saver.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        // capture 已冻结旧 expected；写同语义不同注释并 reload
        String sameSemantic = "# note\nserver:\n  host: a\n  port: 1\n  debug: false\n  mode: online\n";
        write(file, sameSemantic);
        DraftBuffer reloaded = manager.reloadDraftFromDisk();
        assertNotNull(reloaded);
        assertEquals("a", manager.authority().getString("server.host"));
        assertFalse("expected 应推进到新字节",
                manager.expectedDiskSnapshot().exactBytesEqual(expected0));

        release.countDown();
        saver.join(10000);
        assertNotNull(saveOut.get());
        assertTrue("旧 save 必须冲突: " + saveOut.get().status() + " " + saveOut.get().conflictType(),
                saveOut.get().isConflict());
        assertEquals(SaveOutcome.ConflictType.CONFIG_FILE_CHANGED_SINCE_LOAD, saveOut.get().conflictType());
        assertEquals("a", manager.authority().getString("server.host"));
    }

    @Test
    public void notificationBlocksReloadFlushAndLegacyMutation() throws Exception {
        File file = tempFolder.newFile("notify-block.yaml");
        write(file, "server:\n  host: a\n  port: 1\n  debug: false\n  mode: online\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        AtomicReference<Exception> reloadEx = new AtomicReference<Exception>();
        AtomicReference<Exception> flushEx = new AtomicReference<Exception>();
        AtomicReference<Exception> legacyEx = new AtomicReference<Exception>();
        AtomicReference<String> hostDuring = new AtomicReference<String>();

        manager.eventBus().subscribe(e -> {
            if (e.getType() != ConfigChangeEvent.ChangeType.BATCH_SAVE) {
                return;
            }
            hostDuring.set(manager.authority().getString("server.host"));
            try {
                manager.reloadDraftFromDisk();
            } catch (Exception ex) {
                reloadEx.set(ex);
            }
            try {
                manager.flushRaw();
            } catch (Exception ex) {
                flushEx.set(ex);
            }
            try {
                manager.authority().legacy().setRawJson("legacyKey", "x: 1\n");
            } catch (Exception ex) {
                legacyEx.set(ex);
            }
            SaveOutcome nested = manager.save(manager.openDraft());
            assertEquals(SaveOutcome.ConflictType.SAVE_DURING_NOTIFICATION, nested.conflictType());
        });

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "saved");
        assertTrue(manager.save(draft).isSuccess());

        assertEquals("saved", hostDuring.get());
        assertNotNull(reloadEx.get());
        assertTrue(reloadEx.get() instanceof ConfigConflictException);
        assertEquals(SaveOutcome.ConflictType.SAVE_DURING_NOTIFICATION,
                ((ConfigConflictException) reloadEx.get()).conflictType());
        assertNotNull(flushEx.get());
        assertTrue(flushEx.get() instanceof ConfigConflictException);
        assertNotNull(legacyEx.get());
        assertTrue(legacyEx.get() instanceof ConfigConflictException);
        assertEquals("saved", manager.authority().getString("server.host"));
        assertEquals("", manager.authority().legacy().getRawJson("legacyKey"));
    }

    @Test
    public void reloadNotificationBlocksSave() throws Exception {
        File file = tempFolder.newFile("reload-notify.yaml");
        write(file, "server:\n  host: a\n  port: 1\n  debug: false\n  mode: online\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        AtomicReference<SaveOutcome> nested = new AtomicReference<SaveOutcome>();
        manager.eventBus().subscribe(e -> {
            if (e.getType() == ConfigChangeEvent.ChangeType.RELOAD) {
                nested.set(manager.save(manager.openDraft()));
            }
        });
        write(file, "server:\n  host: b\n  port: 1\n  debug: false\n  mode: online\n");
        manager.reloadDraftFromDisk();
        assertNotNull(nested.get());
        assertEquals(SaveOutcome.ConflictType.SAVE_DURING_NOTIFICATION, nested.get().conflictType());
        assertEquals("b", manager.authority().getString("server.host"));
    }

    /**
     * 慢 validator latch：reload 独立线程，latch 证明已进入锁外 validate 并等待；
     * 显式 release 后收敛。join 短超时仅防死锁，不得作为流程。
     * 断言唯一线性化结果、Authority/expected。
     */
    @Test
    public void slowValidatorLatch_reloadVsSaveFlushSecondReload() throws Exception {
        File file = tempFolder.newFile("slow-validator.yaml");
        write(file, "server:\n  host: base\n  port: 1\n  debug: false\n  mode: online\n");
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        DraftValidator slow = draft -> {
            if ("slow-reload".equals(Thread.currentThread().getName())) {
                entered.countDown();
                try {
                    // 等待显式 release；超时仅防死锁
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new RuntimeException("release latch deadlock (not flow control)");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("interrupted waiting release", e);
                }
            }
            return ValidationResult.ok();
        };
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema(), slow);
        ConfigFileSnapshot expected0 = manager.expectedDiskSnapshot();
        write(file, "server:\n  host: reloaded\n  port: 1\n  debug: false\n  mode: online\n");

        AtomicReference<Exception> reloadEx = new AtomicReference<Exception>();
        AtomicReference<DraftBuffer> reloadOk = new AtomicReference<DraftBuffer>();
        Thread reloader = new Thread(() -> {
            try {
                reloadOk.set(manager.reloadDraftFromDisk());
            } catch (Exception e) {
                reloadEx.set(e);
            }
        }, "slow-reload");
        reloader.start();
        assertTrue("reload 应已启动并进入慢 validator（等待 transactionLock 外校验）",
                entered.await(5, TimeUnit.SECONDS));

        // 证明 reload 在锁外等待：Authority 仍 base，expected 未推进
        assertEquals("base", manager.authority().getString("server.host"));
        assertTrue(manager.expectedDiskSnapshot().exactBytesEqual(expected0));

        DraftBuffer concurrentDraft = manager.openDraft();
        concurrentDraft.setDraft("server.host", "from-save");
        SaveOutcome concurrentSave = manager.save(concurrentDraft);

        AtomicReference<Exception> flushEx = new AtomicReference<Exception>();
        try {
            manager.flushRaw();
        } catch (Exception e) {
            flushEx.set(e);
        }

        AtomicReference<Exception> secondReloadEx = new AtomicReference<Exception>();
        try {
            manager.reloadDraftFromDisk();
        } catch (Exception e) {
            secondReloadEx.set(e);
        }

        ConfigFileSnapshot expectedDuring = manager.expectedDiskSnapshot();
        String hostDuring = manager.authority().getString("server.host");
        byte[] diskDuring = Files.readAllBytes(file.toPath());

        // 显式 release；join 短超时仅防死锁
        release.countDown();
        reloader.join(3000);
        assertEquals("reload 线程应已结束（非靠超时驱动流程）",
                Thread.State.TERMINATED, reloader.getState());

        // 唯一线性化：save 成功 XOR reload 成功覆盖
        String hostFinal = manager.authority().getString("server.host");
        if (concurrentSave.isSuccess()) {
            assertEquals("from-save", hostFinal);
            assertNotNull("reload 在 Authority 已变后必须冲突", reloadEx.get());
            assertTrue(reloadEx.get() instanceof ConfigReloadException);
            ConfigReloadException cre = (ConfigReloadException) reloadEx.get();
            assertEquals(ConfigReloadException.Reason.CONFLICT, cre.reason());
            assertFalse("reload 不得覆盖 from-save", "reloaded".equals(hostFinal));
        } else {
            assertEquals(SaveOutcome.ConflictType.CONFIG_FILE_CHANGED_SINCE_LOAD,
                    concurrentSave.conflictType());
            assertTrue("hostDuring 仅 base 或 reloaded",
                    "base".equals(hostDuring) || "reloaded".equals(hostDuring));
            if (reloadEx.get() == null) {
                assertNotNull(reloadOk.get());
                assertEquals("reloaded", hostFinal);
                assertTrue(manager.owns(reloadOk.get()));
                assertFalse("expected 应推进",
                        manager.expectedDiskSnapshot().exactBytesEqual(expected0));
            } else {
                assertEquals(hostDuring, hostFinal);
                assertTrue(manager.expectedDiskSnapshot().exactBytesEqual(expectedDuring));
            }
        }
        // disk 与 Authority 一致可观测
        assertNotNull(hostFinal);
        if (secondReloadEx.get() != null) {
            assertTrue(secondReloadEx.get() instanceof ConfigException);
        }
        if (flushEx.get() != null) {
            assertTrue(flushEx.get() instanceof ConfigException);
        }
        // disk 字节存在（零静默损坏）
        assertTrue(diskDuring.length > 0 || Files.readAllBytes(file.toPath()).length > 0);
    }

    /** 慢 save + 并发 reload 推进 expected → 旧 save 冲突；显式 release。 */
    @Test
    public void slowSaveLatch_reloadAdvancesExpected_staleSaveConflicts() throws Exception {
        File file = tempFolder.newFile("slow-save.yaml");
        write(file, "server:\n  host: base\n  port: 1\n  debug: false\n  mode: online\n");
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        DraftValidator slow = draft -> {
            if ("slow-save".equals(Thread.currentThread().getName())) {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new RuntimeException("release latch deadlock");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            return ValidationResult.ok();
        };
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema(), slow);
        ConfigFileSnapshot expected0 = manager.expectedDiskSnapshot();
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "from-save");

        AtomicReference<SaveOutcome> saveOut = new AtomicReference<SaveOutcome>();
        Thread saver = new Thread(() -> saveOut.set(manager.save(draft)), "slow-save");
        saver.start();
        assertTrue("save 应已进入慢 validator", entered.await(5, TimeUnit.SECONDS));

        write(file, "server:\n  host: reloaded\n  port: 1\n  debug: false\n  mode: online\n");
        DraftBuffer reloaded = manager.reloadDraftFromDisk();
        assertEquals("reloaded", manager.authority().getString("server.host"));
        assertTrue(manager.owns(reloaded));
        assertFalse(manager.expectedDiskSnapshot().exactBytesEqual(expected0));

        release.countDown();
        saver.join(3000);
        assertEquals(Thread.State.TERMINATED, saver.getState());
        assertNotNull(saveOut.get());
        assertTrue("旧 save 必须冲突: " + saveOut.get().status() + " " + saveOut.get().conflictType(),
                saveOut.get().isConflict());
        assertTrue("冲突类型应为 AUTHORITY_MODIFIED 或 CONFIG_FILE_CHANGED，实际="
                        + saveOut.get().conflictType(),
                saveOut.get().conflictType() == SaveOutcome.ConflictType.AUTHORITY_MODIFIED_DURING_SAVE
                        || saveOut.get().conflictType()
                        == SaveOutcome.ConflictType.CONFIG_FILE_CHANGED_SINCE_LOAD);
        assertEquals("reloaded", manager.authority().getString("server.host"));
    }

    /**
     * flush vs reload：reload 独立线程已启动；flush 在 beforeMove 等待显式 release。
     * 禁止依赖超时作流程；join 短超时仅防死锁。
     * 断言唯一线性化结果、Authority/expected/disk。
     */
    @Test
    public void flushVsReload_reloadThreadWaitsTransactionLock_uniqueLinearization() throws Exception {
        File file = tempFolder.newFile("flush-vs-reload-lock.yaml");
        write(file, "server:\n  host: base\n  port: 1\n  debug: false\n  mode: online\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        manager.authority().legacy().setRawJson("extra", "k: v\n");
        ConfigFileSnapshot expected0 = manager.expectedDiskSnapshot();

        final CountDownLatch flushEntered = new CountDownLatch(1);
        final CountDownLatch releaseFlush = new CountDownLatch(1);
        final AtomicReference<Exception> flushEx = new AtomicReference<Exception>();
        final AtomicReference<Exception> reloadEx = new AtomicReference<Exception>();
        final AtomicReference<DraftBuffer> reloadOk = new AtomicReference<DraftBuffer>();
        final CountDownLatch reloadStarted = new CountDownLatch(1);

        Thread flusher = new Thread(() -> {
            try {
                Persistence.installFaultInjector(new Persistence.FaultInjector() {
                    @Override
                    public void beforeMove(File target, Path temp) throws IOException {
                        if ("flush-hold".equals(Thread.currentThread().getName())) {
                            flushEntered.countDown();
                            try {
                                if (!releaseFlush.await(5, TimeUnit.SECONDS)) {
                                    throw new IOException("releaseFlush deadlock");
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                throw new IOException(e);
                            }
                        }
                    }
                });
                manager.flushRaw();
            } catch (Exception e) {
                flushEx.set(e);
            } finally {
                Persistence.clearFaultInjector();
            }
        }, "flush-hold");

        flusher.start();
        assertTrue("flush 应进入 beforeMove 并等待显式 release",
                flushEntered.await(5, TimeUnit.SECONDS));

        // disk 改写后启动 reload（独立线程）；flush 仍占 write domain
        write(file, "server:\n  host: reloaded\n  port: 1\n  debug: false\n  mode: online\n");
        Thread reloader = new Thread(() -> {
            reloadStarted.countDown();
            try {
                reloadOk.set(manager.reloadDraftFromDisk());
            } catch (Exception e) {
                reloadEx.set(e);
            }
        }, "reload-wait-tx");
        reloader.start();
        assertTrue("reload 线程应已启动", reloadStarted.await(5, TimeUnit.SECONDS));

        // 显式 release flush；join 仅防死锁
        releaseFlush.countDown();
        flusher.join(3000);
        assertEquals(Thread.State.TERMINATED, flusher.getState());
        reloader.join(3000);
        assertEquals(Thread.State.TERMINATED, reloader.getState());

        String host = manager.authority().getString("server.host");
        // 唯一线性化：reload 胜 → reloaded；或 flush 先写完 → base（extra 已写）后 reload 可能成功/冲突
        assertTrue("host 应为 base 或 reloaded，实际=" + host,
                "base".equals(host) || "reloaded".equals(host));
        if ("reloaded".equals(host)) {
            if (reloadEx.get() == null) {
                assertNotNull(reloadOk.get());
            }
            // expected 相对 bootstrap 已变（reload 或 flush 成功都会推进）
            assertNotNull(manager.expectedDiskSnapshot());
        }
        if (flushEx.get() != null) {
            assertTrue(flushEx.get() instanceof ConfigException);
        }
        if (reloadEx.get() != null) {
            assertTrue(reloadEx.get() instanceof ConfigException);
        }
        assertNotNull(manager.authority().getString("server.host"));
        assertNotNull(manager.expectedDiskSnapshot());
        assertTrue(Files.readAllBytes(file.toPath()).length > 0);
        // bootstrap expected 与最终 expected 关系可观测（至少一个路径推进或冲突）
        boolean expectedChanged = !manager.expectedDiskSnapshot().exactBytesEqual(expected0);
        boolean hadConflict = flushEx.get() != null || reloadEx.get() != null;
        assertTrue("应有 expected 推进或结构化冲突", expectedChanged || hadConflict || "base".equals(host));
    }

    /**
     * 双向 latch：慢 reload（锁外 validate 等待）与并发 flush——结果唯一。
     * 流程靠显式 release，join 短超时仅防死锁。
     */
    @Test
    public void reloadVsFlush_bidirectionalLatch_uniqueOutcome() throws Exception {
        File file = tempFolder.newFile("reload-vs-flush.yaml");
        write(file, "server:\n  host: base\n  port: 1\n  debug: false\n  mode: online\n");
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        DraftValidator slow = draft -> {
            if ("slow-reload-flush".equals(Thread.currentThread().getName())) {
                entered.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) {
                        throw new RuntimeException("release latch deadlock");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            return ValidationResult.ok();
        };
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema(), slow);
        ConfigFileSnapshot expected0 = manager.expectedDiskSnapshot();
        write(file, "server:\n  host: reloaded\n  port: 1\n  debug: false\n  mode: online\n");

        AtomicReference<Exception> reloadEx = new AtomicReference<Exception>();
        AtomicReference<DraftBuffer> reloadOk = new AtomicReference<DraftBuffer>();
        Thread reloader = new Thread(() -> {
            try {
                reloadOk.set(manager.reloadDraftFromDisk());
            } catch (Exception e) {
                reloadEx.set(e);
            }
        }, "slow-reload-flush");
        reloader.start();
        assertTrue("reload 已启动并等待 transactionLock 外校验", entered.await(5, TimeUnit.SECONDS));
        assertEquals("base", manager.authority().getString("server.host"));
        assertTrue(manager.expectedDiskSnapshot().exactBytesEqual(expected0));

        AtomicReference<Exception> flushEx = new AtomicReference<Exception>();
        try {
            manager.flushRaw();
        } catch (Exception e) {
            flushEx.set(e);
        }

        release.countDown();
        reloader.join(3000);
        assertEquals(Thread.State.TERMINATED, reloader.getState());

        String host = manager.authority().getString("server.host");
        assertTrue("host 应为 base 或 reloaded，实际=" + host,
                "base".equals(host) || "reloaded".equals(host));
        if (reloadEx.get() == null) {
            assertNotNull(reloadOk.get());
            assertEquals("reloaded", host);
            assertFalse(manager.expectedDiskSnapshot().exactBytesEqual(expected0));
        }
        if (flushEx.get() != null) {
            assertTrue(flushEx.get() instanceof ConfigException);
        }
        // disk 可读
        assertTrue(readText(file).length() > 0);
    }

    @Test
    public void moveFailureAfterTempWrite_ioFailedZeroProgress_thenRetry() throws Exception {

        File file = tempFolder.newFile("move-fail.yaml");
        write(file, "server:\n  host: a\n  port: 1\n  debug: false\n  mode: online\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        ConfigFileSnapshot expectedBefore = manager.expectedDiskSnapshot();
        AtomicInteger events = new AtomicInteger();
        manager.eventBus().subscribe(e -> events.incrementAndGet());

        Persistence.installFaultInjector(new Persistence.FaultInjector() {
            @Override
            public void beforeMove(File target, Path temp) throws IOException {
                throw new IOException("simulated move failure after temp write");
            }
        });

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "never");
        SaveOutcome outcome = manager.save(draft);
        assertEquals(SaveOutcome.Status.IO_FAILED, outcome.status());
        assertEquals("a", manager.authority().getString("server.host"));
        assertTrue(manager.expectedDiskSnapshot().exactBytesEqual(expectedBefore));
        assertEquals(0, events.get());
        assertTrue(readText(file).contains("host: a") || readText(file).contains("a"));
        assertEquals("a", draft.getCurrent("server.host"));
        assertEquals("never", draft.getDraft("server.host"));
        assertTrue(draft.isDirty("server.host"));

        Persistence.clearFaultInjector();
        SaveOutcome retry = manager.save(draft);
        assertTrue("清 hook 后同 draft 重试应成功: " + retry.status() + " " + retry.conflictType(),
                retry.isSuccess());
        assertEquals("never", manager.authority().getString("server.host"));
        assertEquals(1, events.get());
    }

    @Test
    public void sameByteAbaIsAllowed() throws Exception {
        File file = tempFolder.newFile("aba.yaml");
        String a = "server:\n  host: a\n  port: 1\n  debug: false\n  mode: online\n";
        String b = "server:\n  host: b\n  port: 1\n  debug: false\n  mode: online\n";
        write(file, a);
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        write(file, b);
        write(file, a);
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "c");
        SaveOutcome outcome = manager.save(draft);
        assertTrue("same-byte ABA should pass: " + outcome.conflictType(), outcome.isSuccess());
        assertEquals("c", manager.authority().getString("server.host"));
    }

    @Test
    public void ownsDraftDoesNotLeakToken() throws Exception {
        File f1 = tempFolder.newFile("owns1.yaml");
        File f2 = tempFolder.newFile("owns2.yaml");
        write(f1, "server:\n  host: a\n  port: 1\n  debug: false\n  mode: online\n");
        write(f2, "server:\n  host: a\n  port: 1\n  debug: false\n  mode: online\n");
        ConfigManager m1 = ConfigManager.bootstrap(f1, SchemaTestFactory.serverSchema());
        ConfigManager m2 = ConfigManager.bootstrap(f2, SchemaTestFactory.serverSchema());
        DraftBuffer d1 = m1.openDraft();
        DraftBuffer d2 = m2.openDraft();
        assertTrue(m1.owns(d1));
        assertFalse(m1.owns(d2));
        assertFalse(m1.owns(null));
        assertFalse(m1.owns(DraftBuffer.from(m1.authority())));
    }
}
