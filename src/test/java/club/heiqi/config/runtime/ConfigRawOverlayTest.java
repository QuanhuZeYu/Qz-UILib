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
     * schema section 已知字段 + nested unknown 共存：
     * schema typed 覆盖 raw known；unknown 完整保留；删除 unknown 后 schema 仍在。
     */
    @Test
    public void sectionKnownAndNestedUnknown_typedCoversKnown_unknownPreserved_deletePath()
            throws Exception {
        File file = tempFolder.newFile("raw-nested-known-unknown.yaml");
        write(file,
                "server:\n" +
                "  host: from-disk\n" +
                "  port: 7777\n" +
                "  debug: true\n" +
                "  mode: offline\n" +
                "  nested:\n" +
                "    unknownLeaf: keep-nested\n" +
                "    deeper:\n" +
                "      flag: true\n");

        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        // schema known 从 disk 载入
        assertEquals("from-disk", manager.authority().getString("server.host"));
        assertEquals(7777.0, manager.authority().getNumber("server.port"), 0.0);
        // nested unknown 完整保留
        String nested = manager.authority().legacy().getRawJson("server.nested");
        assertTrue("nested unknown 应保留: " + nested, nested.contains("keep-nested"));
        assertTrue(nested.contains("deeper") || nested.contains("flag"));

        // schema typed 覆盖 known（save）
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "schema-typed");
        draft.setDraft("server.port", Double.valueOf(9090));
        assertTrue(manager.save(draft).isSuccess());

        String disk = new String(Files.readAllBytes(file.toPath()), "UTF-8");
        assertTrue("typed 应覆盖 host: " + disk, disk.contains("schema-typed"));
        assertTrue(disk.contains("9090"));
        assertTrue("unknown nested 完整保留: " + disk, disk.contains("keep-nested"));
        assertTrue(disk.contains("nested"));

        ConfigManager reloaded = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        assertEquals("schema-typed", reloaded.authority().getString("server.host"));
        assertEquals(9090.0, reloaded.authority().getNumber("server.port"), 0.0);
        String nested2 = reloaded.authority().legacy().getRawJson("server.nested");
        assertTrue("reload 后 nested 仍在: " + nested2, nested2.contains("keep-nested"));

        // 删除路径：putRaw null 去掉 nested unknown，schema known 仍在
        synchronized (reloaded.authority().transactionLock()) {
            reloaded.authority().putRaw("server.nested", null);
        }
        assertEquals("", reloaded.authority().legacy().getRawJson("server.nested"));
        reloaded.flushRaw();

        ConfigNode afterDelete = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        ConfigNode nestedNode = afterDelete.get("server.nested");
        assertTrue(nestedNode == null || nestedNode.isNull());
        assertEquals("schema-typed", afterDelete.get("server.host").asString());
        assertEquals(9090.0, afterDelete.get("server.port").asDouble(), 0.0);
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

    /**
     * schema section 为 scalar：bootstrap fail-closed，不静默用默认覆盖。
     */
    @Test
    public void schemaSectionScalar_bootstrapFailClosed() throws Exception {
        File file = tempFolder.newFile("raw-section-scalar.yaml");
        write(file, "server: just-a-string\n");
        try {
            ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
            fail("schema section scalar 应 fail-closed");
        } catch (ConfigException e) {
            assertEquals(ConfigException.Category.VALIDATION, e.category());
            assertTrue(e.getMessage().contains("server")
                    || e.getMessage().contains("MAP")
                    || e.getMessage().contains("strict section"));
        }
        // 磁盘未被静默重写
        String disk = new String(Files.readAllBytes(file.toPath()), "UTF-8");
        assertTrue("fail-closed 不得静默覆盖 disk: " + disk, disk.contains("just-a-string"));
    }

    /**
     * schema section 为 list：bootstrap fail-closed。
     */
    @Test
    public void schemaSectionList_bootstrapFailClosed() throws Exception {
        File file = tempFolder.newFile("raw-section-list.yaml");
        write(file, "server:\n  - a\n  - b\n");
        try {
            ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
            fail("schema section list 应 fail-closed");
        } catch (ConfigException e) {
            assertEquals(ConfigException.Category.VALIDATION, e.category());
            assertTrue(e.getMessage().contains("server")
                    || e.getMessage().contains("MAP")
                    || e.getMessage().contains("strict section"));
        }
        String disk = new String(Files.readAllBytes(file.toPath()), "UTF-8");
        assertTrue(disk.contains("- a") || disk.contains("a"));
    }

    /**
     * schema section 为 scalar：reload 同样 fail-closed，Authority/expected 零推进。
     */
    @Test
    public void schemaSectionScalar_reloadFailClosed_zeroProgress() throws Exception {
        File file = tempFolder.newFile("raw-section-scalar-reload.yaml");
        write(file,
                "server:\n" +
                "  host: keep-host\n" +
                "  port: 4242\n" +
                "  debug: true\n" +
                "  mode: online\n");
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema());
        assertEquals("keep-host", manager.authority().getString("server.host"));
        assertEquals(4242.0, manager.authority().getNumber("server.port"), 0.0);

        // 破坏 disk：section 变 scalar
        write(file, "server: broken-scalar\n");
        try {
            manager.reloadDraftFromDisk();
            fail("reload scalar section 应 fail-closed");
        } catch (ConfigReloadException e) {
            assertEquals(ConfigReloadException.Reason.VALIDATION, e.reason());
        } catch (ConfigException e) {
            assertEquals(ConfigException.Category.VALIDATION, e.category());
        }
        // Authority 零推进
        assertEquals("keep-host", manager.authority().getString("server.host"));
        assertEquals(4242.0, manager.authority().getNumber("server.port"), 0.0);
    }
}
