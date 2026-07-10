package club.heiqi.config.runtime;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigException;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.ConfigNode;
import club.heiqi.config.ConfigSource;
import club.heiqi.config.schema.ConfigSchema;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
import static org.junit.Assert.fail;

/**
 * 磁盘 CAS：同 canonical 写域、外部编辑/删除/目录、flushRaw、reloadFromDisk、BATCH_SAVE 计数。
 *
 * <p>断言读 {@link SaveOutcome.ConflictType}，禁止英文诊断串匹配。</p>
 */
public class ConfigDiskCasTest {

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

    private static String readText(File file) throws Exception {
        if (!file.exists()) {
            return null;
        }
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /** M1/M2 同 canonical file：M2 save 后 M1 save 冲突；M1 reloadFromDisk 后新 draft 可 save。 */
    @Test
    public void twoManagersSameCanonicalFile_m2SaveThenM1ConflictsThenReloadSaves() throws Exception {
        File file = tempFolder.newFile("shared-cas.yaml");
        write(file, "server:\n  host: base\n  port: 1\n  debug: false\n  mode: online\n");

        ConfigManager m1 = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        ConfigManager m2 = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        DraftBuffer d1 = m1.openDraft();
        DraftBuffer d2 = m2.openDraft();
        d1.setDraft("server.host", "from.m1");
        d2.setDraft("server.host", "from.m2");

        SaveOutcome s2 = m2.save(d2);
        assertTrue(s2.isSuccess());
        assertEquals("from.m2", m2.authority().getString("server.host"));

        SaveOutcome s1 = m1.save(d1);
        assertEquals(SaveOutcome.ConflictType.CONFIG_FILE_CHANGED_SINCE_LOAD, s1.conflictType());
        assertTrue(s1.requiresReload());
        // M1 Authority 未推进
        assertEquals("base", m1.authority().getString("server.host"));
        // 磁盘仍是 m2 值
        ConfigNode onDisk = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertEquals("from.m2", onDisk.get("server.host").asString());

        DraftBuffer reloaded = m1.reloadDraftFromDisk();
        assertTrue(reloaded.hasSameOwner(d1));
        assertEquals("from.m2", m1.authority().getString("server.host"));
        reloaded.setDraft("server.host", "from.m1.after.reload");
        SaveOutcome s1b = m1.save(reloaded);
        assertTrue(s1b.isSuccess());
        assertEquals("from.m1.after.reload",
                Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML).get("server.host").asString());
    }

    /** 外部编辑不同内容 → CONFIG_FILE_CHANGED。 */
    @Test
    public void externalEditDifferentBytesCausesCasConflict() throws Exception {
        File file = tempFolder.newFile("ext-edit.yaml");
        write(file, "server:\n  host: original\n  port: 1\n  debug: false\n  mode: online\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "mine");

        write(file, "server:\n  host: external\n  port: 1\n  debug: false\n  mode: online\n");

        SaveOutcome outcome = manager.save(draft);
        assertEquals(SaveOutcome.ConflictType.CONFIG_FILE_CHANGED_SINCE_LOAD, outcome.conflictType());
        assertTrue(outcome.requiresReload());
        assertEquals("original", manager.authority().getString("server.host"));
        assertTrue(readText(file).contains("external"));
    }

    /** 相同字节重建视为等价，可 save。 */
    @Test
    public void sameBytesRewriteIsEquivalent() throws Exception {
        File file = tempFolder.newFile("same-bytes.yaml");
        String content = "server:\n  host: same\n  port: 1\n  debug: false\n  mode: online\n";
        write(file, content);
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "next");

        // 用相同字节覆盖（模拟 touch 重建）
        write(file, content);

        SaveOutcome outcome = manager.save(draft);
        assertTrue("相同字节应 CAS 通过: " + outcome.conflictType(), outcome.isSuccess());
        assertEquals("next", manager.authority().getString("server.host"));
    }

    /** 文件删除 → missing vs regular expected → CAS 冲突。 */
    @Test
    public void deletedFileCausesCasConflict() throws Exception {
        File file = tempFolder.newFile("deleted.yaml");
        write(file, "server:\n  host: x\n  port: 1\n  debug: false\n  mode: online\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "y");

        assertTrue(file.delete());

        SaveOutcome outcome = manager.save(draft);
        assertEquals(SaveOutcome.ConflictType.CONFIG_FILE_CHANGED_SINCE_LOAD, outcome.conflictType());
        assertTrue(outcome.requiresReload());
        assertFalse(file.exists());
    }

    /** missing → exists（bootstrap 空/不存在后外部创建文件）。 */
    @Test
    public void missingThenExistsCausesCasConflict() throws Exception {
        File file = new File(tempFolder.getRoot(), "was-missing.yaml");
        assertFalse(file.exists());
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "created.by.me");

        write(file, "server:\n  host: sneaky\n  port: 1\n  debug: false\n  mode: online\n");

        SaveOutcome outcome = manager.save(draft);
        assertEquals(SaveOutcome.ConflictType.CONFIG_FILE_CHANGED_SINCE_LOAD, outcome.conflictType());
        assertTrue(readText(file).contains("sneaky"));
    }

    /** 路径被目录替换 → NON_REGULAR → CAS 冲突（或 reload 失败）。 */
    @Test
    public void directoryInsteadOfFileCausesCasConflict() throws Exception {
        File file = tempFolder.newFile("to-dir.yaml");
        write(file, "server:\n  host: x\n  port: 1\n  debug: false\n  mode: online\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "y");

        assertTrue(file.delete());
        assertTrue(file.mkdir());

        SaveOutcome outcome = manager.save(draft);
        assertEquals(SaveOutcome.ConflictType.CONFIG_FILE_CHANGED_SINCE_LOAD, outcome.conflictType());
    }

    /** canonical alias（相对 vs 绝对）指向同一写域。 */
    @Test
    public void canonicalAliasSharesWriteDomain() throws Exception {
        File file = tempFolder.newFile("alias.yaml");
        write(file, "server:\n  host: a\n  port: 1\n  debug: false\n  mode: online\n");
        File abs = file.getAbsoluteFile();
        File viaParent = new File(file.getParentFile(), file.getName());
        assertTrue(ConfigFileSnapshot.sameCanonicalWriteDomain(abs, viaParent));

        ConfigManager m1 = ConfigManager.bootstrap(abs, SchemaTestFactory.serverSchema());
        ConfigManager m2 = ConfigManager.bootstrap(viaParent, SchemaTestFactory.serverSchema());
        DraftBuffer d1 = m1.openDraft();
        DraftBuffer d2 = m2.openDraft();
        d2.setDraft("server.host", "via.alias");
        assertTrue(m2.save(d2).isSuccess());
        d1.setDraft("server.host", "stale");
        assertEquals(SaveOutcome.ConflictType.CONFIG_FILE_CHANGED_SINCE_LOAD,
                m1.save(d1).conflictType());
    }

    /** flushRaw 走 CAS：外部改后抛 ConfigConflictException。 */
    @Test
    public void flushRawCasConflictThrowsConfigConflictException() throws Exception {
        File file = tempFolder.newFile("flush-cas.yaml");
        write(file, "server:\n  host: a\n  port: 1\n  debug: false\n  mode: online\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        write(file, "server:\n  host: external\n  port: 1\n  debug: false\n  mode: online\n");
        try {
            manager.flushRaw();
            fail("expected ConfigConflictException");
        } catch (ConfigConflictException e) {
            assertEquals(SaveOutcome.ConflictType.CONFIG_FILE_CHANGED_SINCE_LOAD, e.conflictType());
        }
        assertTrue(readText(file).contains("external"));
    }

    /** CAS 冲突不发 BATCH_SAVE。 */
    @Test
    public void casConflictDoesNotPublishBatchSave() throws Exception {
        File file = tempFolder.newFile("no-event.yaml");
        write(file, "server:\n  host: a\n  port: 1\n  debug: false\n  mode: online\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        AtomicInteger batch = new AtomicInteger();
        manager.eventBus().subscribe(e -> {
            if (e.getType() == club.heiqi.config.ConfigChangeEvent.ChangeType.BATCH_SAVE) {
                batch.incrementAndGet();
            }
        });
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "mine");
        write(file, "server:\n  host: other\n  port: 1\n  debug: false\n  mode: online\n");
        assertEquals(SaveOutcome.ConflictType.CONFIG_FILE_CHANGED_SINCE_LOAD,
                manager.save(draft).conflictType());
        assertEquals(0, batch.get());
    }

    /** reloadDraftFromDisk 成功发 RELOAD 不发 BATCH_SAVE；失败保持 Authority。 */
    @Test
    public void reloadFromDiskNoBatchSave_andFailureKeepsAuthority() throws Exception {
        File file = tempFolder.newFile("reload-no-batch.yaml");
        write(file, "server:\n  host: keep\n  port: 1\n  debug: false\n  mode: online\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        AtomicInteger batch = new AtomicInteger();
        AtomicInteger reload = new AtomicInteger();
        manager.eventBus().subscribe(e -> {
            if (e.getType() == club.heiqi.config.ConfigChangeEvent.ChangeType.BATCH_SAVE) {
                batch.incrementAndGet();
            }
            if (e.getType() == club.heiqi.config.ConfigChangeEvent.ChangeType.RELOAD) {
                reload.incrementAndGet();
            }
        });
        write(file, "server:\n  host: reloaded\n  port: 1\n  debug: false\n  mode: online\n");
        DraftBuffer d = manager.reloadDraftFromDisk();
        assertEquals("reloaded", manager.authority().getString("server.host"));
        assertEquals(0, batch.get());
        assertEquals(1, reload.get());
        assertNotNull(d);

        // 变成目录后 reload 失败，Authority 保持 reloaded
        assertTrue(file.delete());
        assertTrue(file.mkdir());
        try {
            manager.reloadDraftFromDisk();
            fail("expected ConfigException");
        } catch (ConfigException expected) {
            // ok
        }
        assertEquals("reloaded", manager.authority().getString("server.host"));
        assertEquals(1, reload.get());
    }

    /**
     * 成功 save 后 expected 更新；再外部改则冲突。
     * latch 精确：先完成 save，再外部写，再 save → 确定 CONFIG_FILE_CHANGED。
     */
    @Test
    public void afterSuccessfulSave_expectedUpdates_thenExternalEditConflicts() throws Exception {
        File file = tempFolder.newFile("after-save.yaml");
        write(file, "server:\n  host: a\n  port: 1\n  debug: false\n  mode: online\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        DraftBuffer d1 = manager.openDraft();
        d1.setDraft("server.host", "b");
        assertTrue(manager.save(d1).isSuccess());

        DraftBuffer d2 = manager.openDraft();
        d2.setDraft("server.host", "c");
        write(file, "server:\n  host: external\n  port: 1\n  debug: false\n  mode: online\n");
        assertEquals(SaveOutcome.ConflictType.CONFIG_FILE_CHANGED_SINCE_LOAD,
                manager.save(d2).conflictType());
    }

    /** ConfigFileSnapshot 精确字节等价（状态+字节）。 */
    @Test
    public void snapshotExactBytesEqual() throws Exception {
        File file = tempFolder.newFile("snap.yaml");
        write(file, "abc");
        ConfigFileSnapshot a = ConfigFileSnapshot.capture(file);
        ConfigFileSnapshot b = ConfigFileSnapshot.capture(file);
        assertTrue(a.exactBytesEqual(b));
        write(file, "abd");
        ConfigFileSnapshot c = ConfigFileSnapshot.capture(file);
        assertFalse(a.exactBytesEqual(c));
        assertEquals(ConfigFileSnapshot.State.REGULAR, a.state());
        File missing = new File(tempFolder.getRoot(), "nope.yaml");
        assertEquals(ConfigFileSnapshot.State.MISSING, ConfigFileSnapshot.capture(missing).state());
    }
}
