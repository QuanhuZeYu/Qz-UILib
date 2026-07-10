package club.heiqi.config.runtime;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigException;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.ConfigNode;
import club.heiqi.config.ConfigSource;
import club.heiqi.config.schema.ConfigSchema;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * section raw overlay：schema section 内未知字段无静默丢失；
 * setRawJson / flush / reload / 删除 / known 更新不删 unknown。
 */
public class ConfigRawOverlayTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static void write(File file, String content) throws Exception {
        FileWriter writer = new FileWriter(file);
        try {
            writer.write(content);
        } finally {
            writer.close();
        }
    }

    /**
     * server.unknown 嵌套 → bootstrap → save/flush → 仍存在。
     */
    @Test
    public void sectionUnknown_bootstrap_saveFlush_stillPresent() throws Exception {
        File file = tempFolder.newFile("raw-overlay-bootstrap.yaml");
        write(file,
                "server:\n" +
                "  host: localhost\n" +
                "  port: 8080\n" +
                "  debug: false\n" +
                "  mode: online\n" +
                "  unknown:\n" +
                "    nested: keep-me\n" +
                "    flag: true\n");

        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        String raw = manager.authority().legacy().getRawJson("server.unknown");
        assertTrue("bootstrap 后应保留 server.unknown: " + raw, raw.contains("keep-me"));

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.port", Double.valueOf(9090));
        assertTrue(manager.save(draft).isSuccess());

        String disk = new String(Files.readAllBytes(file.toPath()), "UTF-8");
        assertTrue("save 后 disk 应含 unknown: " + disk, disk.contains("unknown") || disk.contains("keep-me"));
        assertTrue(disk.contains("keep-me"));

        ConfigManager reloaded = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        String again = reloaded.authority().legacy().getRawJson("server.unknown");
        assertTrue("reload 后仍保留: " + again, again.contains("keep-me"));
        assertEquals(9090.0, reloaded.authority().getNumber("server.port"), 0.0);
    }

    /**
     * setRawJson(server.extra) → flush → reload roundtrip。
     */
    @Test
    public void setRawJson_serverExtra_flush_reload_roundtrip() throws Exception {
        File file = tempFolder.newFile("raw-set-extra.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());

        manager.authority().legacy().setRawJson("server.extra", "nested:\n  value: hijack\n");
        manager.flushRaw();

        ConfigNode disk = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertNotNull(disk.get("server.extra"));
        assertEquals("hijack", disk.get("server.extra.nested.value").asString());

        ConfigManager reloaded = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        String raw = reloaded.authority().legacy().getRawJson("server.extra");
        assertTrue(raw.contains("hijack"));
        // schema 字段仍在
        assertEquals("localhost", reloaded.authority().getString("server.host"));
    }

    /**
     * 删除 raw：setRawJson 路径后 remove 子树。
     */
    @Test
    public void deleteRaw_removesUnknownKeepsSchema() throws Exception {
        File file = tempFolder.newFile("raw-delete.yaml");
        write(file,
                "server:\n" +
                "  host: h1\n" +
                "  port: 1\n" +
                "  debug: false\n" +
                "  mode: online\n" +
                "  unknown: drop-me\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        assertTrue(manager.authority().legacy().getRawJson("server.unknown").contains("drop-me"));

        // 删除：putRaw null 经 Legacy 需空 YAML 或直接 Authority——用 set 空后 remove 顶层 unknown
        // 通过 setRawJson 覆盖为仅 schema 不可——用 put 删除：legacy 写 null 不支持；
        // 改写整个 section overlay：仅写空 map 无效。直接 Authority 包级：用 setRawJson 写 extra 再删。
        manager.authority().legacy().setRawJson("server.unknown", "x: 1\n");
        // 删除子路径：通过写回不含 unknown 的方式——Authority.putRaw(path,null)
        // LegacyAdapter 无 delete API；用 setRawJson 顶层 section 不可整删 schema。
        // 用 reflection-free 包内：flush 后写盘无 unknown 的完整文件再 bootstrap 验证 delete 路径。
        // 内存删除：putRaw("server.unknown", null) 需包级——经 set 空 ConfigNode 不够。
        // 使用 ConfigManager 打开 draft 不碰 raw；直接用 Authority 同包测试：
        synchronized (manager.authority().transactionLock()) {
            manager.authority().putRaw("server.unknown", null);
        }
        assertEquals("", manager.authority().legacy().getRawJson("server.unknown"));
        manager.flushRaw();

        ConfigNode disk = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        ConfigNode unk = disk.get("server.unknown");
        assertTrue(unk == null || unk.isNull());
        assertEquals("h1", disk.get("server.host").asString());
    }

    /**
     * known 字段更新不删 unknown。
     */
    @Test
    public void knownFieldUpdate_doesNotDropUnknown() throws Exception {
        File file = tempFolder.newFile("raw-known-update.yaml");
        write(file,
                "server:\n" +
                "  host: old\n" +
                "  port: 80\n" +
                "  debug: false\n" +
                "  mode: online\n" +
                "  customFlag: stay\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "new-host");
        assertTrue(manager.save(draft).isSuccess());

        String disk = new String(Files.readAllBytes(file.toPath()), "UTF-8");
        assertTrue(disk.contains("new-host"));
        assertTrue("known 更新不得删 unknown: " + disk, disk.contains("customFlag") || disk.contains("stay"));
        assertTrue(manager.authority().legacy().getRawJson("server.customFlag").contains("stay")
                || manager.authority().legacy().getRawJson("server").contains("stay"));
    }

    /**
     * schema 字段自身 raw 错型仍 strict 拒绝。
     */
    @Test
    public void schemaField_rawWrongType_strictReject() throws Exception {
        File file = tempFolder.newFile("raw-strict.yaml");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        double before = manager.authority().getNumber("server.port");
        try {
            manager.authority().legacy().setRawJson("server.port", "\"80\"\n");
            fail("quoted NUMBER 应 strict 拒绝");
        } catch (ConfigException e) {
            assertEquals(ConfigException.Category.VALIDATION, e.category());
        }
        assertEquals(before, manager.authority().getNumber("server.port"), 0.0);
    }

    /**
     * 顶层 unknown 仍保留（既有契约）。
     */
    @Test
    public void topLevelUnknown_preservedOnSave() throws Exception {
        File file = tempFolder.newFile("raw-top-unknown.yaml");
        write(file,
                "server:\n" +
                "  host: localhost\n" +
                "  port: 8080\n" +
                "  debug: false\n" +
                "  mode: online\n" +
                "extra:\n" +
                "  name: legacy\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.debug", Boolean.TRUE);
        assertTrue(manager.save(draft).isSuccess());
        String disk = new String(Files.readAllBytes(file.toPath()), "UTF-8");
        assertTrue(disk.contains("extra"));
        assertTrue(disk.contains("legacy"));
    }
}
