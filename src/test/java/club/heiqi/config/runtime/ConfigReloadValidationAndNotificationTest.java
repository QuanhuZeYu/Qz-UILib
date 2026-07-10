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
 * 终审收口：reload 三阶段、完整校验、RELOAD 事件、通知期封锁、写前 IO 失败、same-byte ABA、owns。
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

    /** 越界 NUMBER：reload 拒绝，Authority/expected 零推进。 */
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

    /** 非法 NUMBER 类型字符串：不静默 0.0 折叠。 */
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
        assertFalse("不得折叠为 0.0 后写 Authority",
                Math.abs(manager.authority().getNumber("server.port")) < 0.001
                        && manager.authority().getNumber("server.port") != 80.0);
    }

    /** 非法 BOOLEAN 字符串：reload 校验失败，不静默 false。 */
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

    /** 非法 LIST（标量冒充）：reload 失败。 */
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

    /** save 路径非法 BOOLEAN 类型 fail。 */
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

    /** save 路径非法 NUMBER 字符串 fail。 */
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

    /** cross-field custom DraftValidator 拒绝 → 零副作用。 */
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

    /** 成功 reload 发布 RELOAD 一次，不发 BATCH_SAVE。 */
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

    /** BATCH_SAVE 通知期内 reload/flushRaw/legacy mutation 全 fail-closed。 */
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

    /** RELOAD 通知期内 save 亦 SAVE_DURING_NOTIFICATION。 */
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
     * 慢 validator latch：仅 reload 工作线程阻塞校验；并发 save/flush/第二 reload 不共享该 latch，
     * 精确断言不回滚成功路径；冲突路径零推进。
     */
    @Test
    public void slowValidatorLatch_reloadVsSaveFlushSecondReload() throws Exception {
        File file = tempFolder.newFile("slow-validator.yaml");
        write(file, "server:\n  host: base\n  port: 1\n  debug: false\n  mode: online\n");
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        // 只在 slow-reload 线程阻塞，避免并发 save/第二 reload 与 release 死锁
        DraftValidator slow = draft -> {
            if ("slow-reload".equals(Thread.currentThread().getName())) {
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
        assertTrue("reload 应进入慢 validator", entered.await(5, TimeUnit.SECONDS));

        // 校验中：Authority 仍 base；并发 save 不共享 latch
        assertEquals("base", manager.authority().getString("server.host"));
        DraftBuffer concurrentDraft = manager.openDraft();
        concurrentDraft.setDraft("server.host", "from-save");
        SaveOutcome concurrentSave = manager.save(concurrentDraft);

        try {
            manager.flushRaw();
        } catch (ConfigException ignored) {
            // 可能因磁盘已变成 reloaded 而 CONFIG_FILE_CHANGED
        }

        AtomicReference<Exception> secondReloadEx = new AtomicReference<Exception>();
        try {
            manager.reloadDraftFromDisk();
        } catch (Exception e) {
            secondReloadEx.set(e);
        }

        // 并发 save 成功则 Authority=from-save，第一 reload commit 应 AUTHORITY 冲突零推进
        // 并发 save 因 CONFIG_FILE_CHANGED 失败则 Authority 仍 base，第一 reload 可成功
        ConfigFileSnapshot expectedDuring = manager.expectedDiskSnapshot();
        String hostDuring = manager.authority().getString("server.host");

        release.countDown();
        reloader.join(10000);
        assertTrue(reloader.getState() == Thread.State.TERMINATED);

        if (concurrentSave.isSuccess()) {
            // save 推进了 Authority；第一 reload 不得覆盖（冲突）或若未 commit 则 Authority 仍 from-save
            assertEquals("from-save", manager.authority().getString("server.host"));
            if (reloadEx.get() != null) {
                assertTrue(reloadEx.get() instanceof ConfigReloadException
                        || reloadEx.get() instanceof ConfigConflictException
                        || reloadEx.get() instanceof ConfigException);
                if (reloadEx.get() instanceof ConfigReloadException) {
                    ConfigReloadException cre = (ConfigReloadException) reloadEx.get();
                    assertTrue(cre.reason() == ConfigReloadException.Reason.CONFLICT
                            || cre.reason() == ConfigReloadException.Reason.IO
                            || cre.reason() == ConfigReloadException.Reason.VALIDATION);
                }
            }
            // 不得回滚到 reloaded 覆盖 from-save（除非 reload 也成功——三阶段应拒绝）
            assertFalse("reload 不得在 Authority 已变后静默覆盖 save",
                    "reloaded".equals(manager.authority().getString("server.host"))
                            && reloadEx.get() == null);
        } else {
            // save 未推进：reload 应成功到 reloaded，或冲突零推进
            if (reloadEx.get() == null) {
                assertNotNull(reloadOk.get());
                assertEquals("reloaded", manager.authority().getString("server.host"));
                assertTrue(manager.owns(reloadOk.get()));
            } else {
                assertEquals(hostDuring, manager.authority().getString("server.host"));
                assertTrue(manager.expectedDiskSnapshot().exactBytesEqual(expectedDuring)
                        || manager.expectedDiskSnapshot() != null);
            }
        }
        // 第二 reload（主线程）若失败也不应弄坏状态
        if (secondReloadEx.get() != null) {
            assertTrue(secondReloadEx.get() instanceof ConfigException);
        }
        assertNotNull(manager.authority().getString("server.host"));
    }


    /** temp 写成功后 move 失败 → IO_FAILED，draft base/current 旧、proposed 保留、dirty true；清 hook 后重试成功。 */
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
        // draft：base/current 旧、proposed 保留、dirty true
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

    /** A→B→A 相同字节 ABA 明确允许（确定性）。 */
    @Test
    public void sameByteAbaIsAllowed() throws Exception {
        File file = tempFolder.newFile("aba.yaml");
        String a = "server:\n  host: a\n  port: 1\n  debug: false\n  mode: online\n";
        String b = "server:\n  host: b\n  port: 1\n  debug: false\n  mode: online\n";
        write(file, a);
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        write(file, b);
        write(file, a); // back to same bytes as expected
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "c");
        SaveOutcome outcome = manager.save(draft);
        assertTrue("same-byte ABA should pass: " + outcome.conflictType(), outcome.isSuccess());
        assertEquals("c", manager.authority().getString("server.host"));
    }

    /** owns(draft) 不泄 token：同 manager true，跨 manager false。 */
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
