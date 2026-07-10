package club.heiqi.config.runtime;

import club.heiqi.config.AtomicFileWrites;
import club.heiqi.config.ConfigChangeEvent;
import club.heiqi.config.ConfigException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * 终审收口：reload 完整校验、RELOAD 事件、通知期封锁、写前 IO 失败、same-byte ABA、owns。
 */
public class ConfigReloadValidationAndNotificationTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @After
    public void clearInjector() {
        AtomicFileWrites.clearFaultInjector();
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
        } catch (ConfigException e) {
            assertTrue(e.getMessage() != null && e.getMessage().length() > 0);
        }
        assertEquals(80.0, manager.authority().getNumber("server.port"), 0.0);
        assertFalse("不得折叠为 0.0 后写 Authority",
                Math.abs(manager.authority().getNumber("server.port")) < 0.001
                        && manager.authority().getNumber("server.port") != 80.0);
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
            // nested save
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
        // legacy 未写入
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

    /** temp 写成功后 move 失败 → IO_FAILED，expected/Authority 零推进，不发事件。 */
    @Test
    public void moveFailureAfterTempWrite_ioFailedZeroProgress() throws Exception {
        File file = tempFolder.newFile("move-fail.yaml");
        write(file, "server:\n  host: a\n  port: 1\n  debug: false\n  mode: online\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        ConfigFileSnapshot expectedBefore = manager.expectedDiskSnapshot();
        AtomicInteger events = new AtomicInteger();
        manager.eventBus().subscribe(e -> events.incrementAndGet());

        AtomicFileWrites.installFaultInjector(new AtomicFileWrites.FaultInjector() {
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
    }

    /** A→B→A 相同字节 ABA 明确允许。 */
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
