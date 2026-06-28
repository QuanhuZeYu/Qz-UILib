package club.heiqi.config.runtime;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigChangeEvent;
import club.heiqi.config.ConfigChangeListener;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.ConfigNode;
import club.heiqi.config.ConfigSource;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.SectionSpec;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 端到端集成测试，覆盖从 bootstrap 到 save 的完整使用流程：
 * 标量保存、部分修改、取消重编辑、恢复默认、二次编辑、非 Schema 子树共存、
 * LegacyAdapter 协作、大量字段、事务原子性、事件发布、空文件默认、
 * YAML 格式输出、注释跳过、深层与 list 非 Schema 子树保留。
 *
 * <p>使用 {@link SchemaTestFactory#serverSchema()} 与 {@link TemporaryFolder}，
 * 不触碰主代码。</p>
 */
public class EndToEndIntegrationTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /** 写字符串到文件 */
    private static void write(File file, String content) throws Exception {
        FileWriter writer = new FileWriter(file);
        try {
            writer.write(content);
        } finally {
            writer.close();
        }
    }

    /** 读文件全文（UTF-8） */
    private static String readText(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /** 构造 5 section × 10 string 字段的大型 schema */
    private static ConfigSchema bigSchema() {
        ConfigSchema.Builder b = ConfigSchema.builder("big");
        for (int s = 0; s < 5; s++) {
            SectionSpec.Builder sb = b.section("sec" + s);
            for (int f = 0; f < 10; f++) {
                sb = sb.string("f" + f).defaultValue("v" + f).build();
            }
            sb.endSection();
        }
        return b.build();
    }

    /**
     * 完整保存流程：bootstrap 空文件 → openDraft → 改 4 个字段 → save
     * → 读回文件验证 4 个值 → 重新 bootstrap → authority.get 返回 4 个新值。
     */
    @Test
    public void fullSaveFlowPersistsAllFourFields() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "new.host");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.debug", true);
        draft.setDraft("server.mode", "test");

        SaveOutcome outcome = manager.save(draft);
        assertTrue(outcome.isSuccess());

        // 读回文件验证 4 个值
        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertEquals("new.host", reloaded.get("server.host").asString());
        assertEquals(3000, reloaded.get("server.port").asInt());
        assertTrue(reloaded.get("server.debug").asBoolean());
        assertEquals("test", reloaded.get("server.mode").asString());

        // 重新 bootstrap，authority 返回新值
        ConfigManager manager2 = ConfigManager.bootstrap(file, schema);
        assertEquals("new.host", manager2.authority().getString("server.host"));
        assertEquals(3000.0, manager2.authority().getNumber("server.port"), 0.0);
        assertTrue(manager2.authority().getBool("server.debug"));
        assertEquals("test", manager2.authority().getString("server.mode"));
    }

    /**
     * 保存部分修改：只改 1 个字段，其他字段保持默认。
     */
    @Test
    public void partialSaveKeepsOtherDefaults() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "changed.host");
        SaveOutcome outcome = manager.save(draft);
        assertTrue(outcome.isSuccess());

        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertEquals("changed.host", reloaded.get("server.host").asString());
        // 其他字段保持默认
        assertEquals(8080, reloaded.get("server.port").asInt());
        assertFalse(reloaded.get("server.debug").asBoolean());
        assertEquals("online", reloaded.get("server.mode").asString());
    }

    /**
     * 取消后重新编辑：改字段 → resetToCurrent → 改另一字段 → save → 只有第二个改动生效。
     */
    @Test
    public void resetToCurrentThenEditOnlySecondChange() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "first.host");
        // 取消，回到当前（默认）
        draft.resetToCurrent();
        // 改另一个字段
        draft.setDraft("server.port", 3000.0);

        SaveOutcome outcome = manager.save(draft);
        assertTrue(outcome.isSuccess());

        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        // host 保持默认，port 为新值
        assertEquals("localhost", reloaded.get("server.host").asString());
        assertEquals(3000, reloaded.get("server.port").asInt());
    }

    /**
     * 恢复默认后保存：bootstrap 带初始值 → resetFieldToDefault → save
     * → 文件中该字段变为 schema 默认值。
     */
    @Test
    public void resetFieldToDefaultThenSave() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file,
                "server:\n  host: other.host\n  port: 8080\n  debug: false\n  mode: online\n");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        // 确认加载了非默认值
        assertEquals("other.host", manager.authority().getString("server.host"));

        DraftBuffer draft = manager.openDraft();
        draft.resetFieldToDefault("server.host");
        SaveOutcome outcome = manager.save(draft);
        assertTrue(outcome.isSuccess());

        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        // host 回到 schema 默认 localhost
        assertEquals("localhost", reloaded.get("server.host").asString());
    }

    /**
     * 保存后再次打开编辑：save → openDraft → current=保存后的值 → 改 → save
     * → 文件反映第二次改动。
     */
    @Test
    public void reopenDraftAfterSaveReflectsCommittedValues() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        // 第一次保存 host
        DraftBuffer draft1 = manager.openDraft();
        draft1.setDraft("server.host", "first.host");
        assertTrue(manager.save(draft1).isSuccess());

        // 再次打开，current 应为保存后的值
        DraftBuffer draft2 = manager.openDraft();
        assertEquals("first.host", draft2.getCurrent("server.host"));
        // 改 port
        draft2.setDraft("server.port", 3000.0);
        assertTrue(manager.save(draft2).isSuccess());

        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertEquals("first.host", reloaded.get("server.host").asString());
        assertEquals(3000, reloaded.get("server.port").asInt());
    }

    /**
     * 标量 + 非 Schema 子树共存：文件含 Schema 字段 + 非 Schema 子树
     * → bootstrap → save(改标量) → 文件中标量更新、非 Schema 子树保留。
     */
    @Test
    public void scalarAndNonSchemaSubtreeCoexist() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file,
                "server:\n  host: orig.host\n  port: 8080\n  debug: false\n  mode: online\n"
                + "extra:\n  nested:\n    value: keep\n");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "updated.host");
        assertTrue(manager.save(draft).isSuccess());

        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertEquals("updated.host", reloaded.get("server.host").asString());
        // 非 Schema 子树保留
        assertEquals("keep", reloaded.get("extra.nested.value").asString());
    }

    /**
     * LegacyAdapter 修改后正常保存：bootstrap → openDraft → 改标量 → save
     * → legacy.setRawJson 改非 Schema → flushRaw → 文件两者都更新。
     */
    @Test
    public void legacyAdapterChangePersistsAfterFlush() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "saved.host");
        assertTrue(manager.save(draft).isSuccess());

        manager.authority().legacy().setRawJson("extra", "nested:\n  value: flushed\n");
        manager.flushRaw();

        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertEquals("saved.host", reloaded.get("server.host").asString());
        assertEquals("flushed", reloaded.get("extra.nested.value").asString());
    }

    /**
     * Schema 字段和非 Schema 字段独立：bootstrap → openDraft → 改标量 → save
     * → legacy.getRawJson 取非 Schema 子树 → 仍为原值。
     */
    @Test
    public void schemaAndNonSchemaFieldsIndependent() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file,
                "server:\n  host: orig.host\n  port: 8080\n  debug: false\n  mode: online\n"
                + "extra:\n  nested:\n    value: keep\n");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "changed.host");
        assertTrue(manager.save(draft).isSuccess());

        // 非 Schema 子树应不受 Schema 字段保存影响
        String raw = manager.authority().legacy().getRawJson("extra");
        assertTrue("非 Schema 子树应保留原值: " + raw, raw.contains("keep"));
    }

    /**
     * 大量字段保存：5 section × 10 字段 → openDraft → 改全部 → save → 读回验证。
     */
    @Test
    public void largeSchemaSaveAllFields() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = bigSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        for (int s = 0; s < 5; s++) {
            for (int f = 0; f < 10; f++) {
                draft.setDraft("sec" + s + ".f" + f, "new" + s + "_" + f);
            }
        }
        SaveOutcome outcome = manager.save(draft);
        assertTrue(outcome.isSuccess());

        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        for (int s = 0; s < 5; s++) {
            for (int f = 0; f < 10; f++) {
                assertEquals("new" + s + "_" + f,
                        reloaded.get("sec" + s + ".f" + f).asString());
            }
        }
    }

    /**
     * 保存事务原子性：openDraft → 改 3 个字段(1 个非法) → save → INVALID
     * → 文件不变 → authority 不变 → 3 个字段都没写入。
     */
    @Test
    public void saveAtomicityOnValidationFailure() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        // 空文件 bootstrap
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "should.not.persist");
        draft.setDraft("server.port", 99999.0); // 非法，超范围
        draft.setDraft("server.mode", "test");

        SaveOutcome outcome = manager.save(draft);
        assertEquals(SaveOutcome.Status.INVALID, outcome.status());

        // 文件不变：仍为空
        assertTrue("文件应保持空: " + file.length(), file.length() == 0);
        // authority 不变：仍为默认
        assertEquals("localhost", manager.authority().getString("server.host"));
        assertEquals(8080.0, manager.authority().getNumber("server.port"), 0.0);
        assertEquals("online", manager.authority().getString("server.mode"));
    }

    /**
     * 保存后事件含正确 ChangeType：save 后收到的事件 ChangeType==BATCH_SAVE。
     */
    @Test
    public void savePublishesBatchSaveEvent() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        AtomicReference<ConfigChangeEvent> received = new AtomicReference<ConfigChangeEvent>();
        manager.eventBus().subscribe(new ConfigChangeListener() {
            @Override
            public void onConfigChanged(ConfigChangeEvent event) {
                received.set(event);
            }
        });

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "evt.host");
        assertTrue(manager.save(draft).isSuccess());

        ConfigChangeEvent event = received.get();
        assertNotNull("应收到事件", event);
        assertEquals(ConfigChangeEvent.ChangeType.BATCH_SAVE, event.getType());
    }

    /**
     * 多次 bootstrap 同一文件一致性：bootstrap → save → 再 bootstrap
     * → 两次 authority.get 值一致。
     */
    @Test
    public void multipleBootstrapConsistent() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager1 = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager1.openDraft();
        draft.setDraft("server.host", "stable.host");
        draft.setDraft("server.port", 3000.0);
        assertTrue(manager1.save(draft).isSuccess());

        String host1 = manager1.authority().getString("server.host");
        double port1 = manager1.authority().getNumber("server.port");

        ConfigManager manager2 = ConfigManager.bootstrap(file, schema);
        assertEquals(host1, manager2.authority().getString("server.host"));
        assertEquals(port1, manager2.authority().getNumber("server.port"), 0.0);
    }

    /**
     * 空文件 bootstrap 后 typed get 全默认：getString/getNumber/getBool 返回 schema 默认值。
     */
    @Test
    public void emptyFileBootstrapReturnsAllDefaults() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        assertEquals("localhost", manager.authority().getString("server.host"));
        assertEquals(8080.0, manager.authority().getNumber("server.port"), 0.0);
        assertFalse(manager.authority().getBool("server.debug"));
        assertEquals("online", manager.authority().getString("server.mode"));
    }

    /**
     * 空文件 bootstrap 后 save 写出完整默认配置：不改任何字段 → save
     * → 文件含全部默认值。
     */
    @Test
    public void emptyFileSaveWritesAllDefaults() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        // 不改任何字段
        SaveOutcome outcome = manager.save(draft);
        assertTrue(outcome.isSuccess());

        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertEquals("localhost", reloaded.get("server.host").asString());
        assertEquals(8080, reloaded.get("server.port").asInt());
        assertFalse(reloaded.get("server.debug").asBoolean());
        assertEquals("online", reloaded.get("server.mode").asString());
    }

    /**
     * YAML 格式输出验证：save 后读回文件内容，确认是 YAML 格式
     * （含冒号缩进，不是 JSON 大括号）。
     */
    @Test
    public void saveProducesYamlNotJson() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "yaml.host");
        assertTrue(manager.save(draft).isSuccess());

        String text = readText(file);
        assertTrue("YAML 应含冒号键: " + text, text.contains("server:"));
        assertTrue("YAML 应含 host 键: " + text, text.contains("host:"));
        // 不应是 JSON 大括号格式
        assertFalse("不应含 JSON 大括号: " + text, text.contains("{"));
        assertFalse("不应含 JSON 大括号: " + text, text.contains("}"));
    }

    /**
     * 文件带注释 bootstrap 不报错：文件含 `# comment` 行 → bootstrap → 正常加载。
     */
    @Test
    public void commentedFileBootstrapSucceeds() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file,
                "# top comment\n"
                + "server:\n  host: commented.host  # inline\n  port: 8080\n  debug: false\n  mode: online\n");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        assertEquals("commented.host", manager.authority().getString("server.host"));
    }

    /**
     * 深层非 Schema 子树保留：文件含 5 层嵌套非 Schema 子树 → bootstrap → save
     * → 子树结构完整保留。
     */
    @Test
    public void deepNonSchemaSubtreePreserved() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file,
                "server:\n  host: localhost\n  port: 8080\n  debug: false\n  mode: online\n"
                + "extra:\n  l1:\n    l2:\n      l3:\n        l4:\n          l5: deep\n");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "x.host");
        assertTrue(manager.save(draft).isSuccess());

        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertEquals("deep", reloaded.get("extra.l1.l2.l3.l4.l5").asString());
    }

    /**
     * 非 Schema list 子树保留：文件含 `servers: [{name: a}, {name: b}]`
     * → bootstrap → save → list 结构保留。
     */
    @Test
    public void nonSchemaListSubtreePreserved() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file,
                "server:\n  host: localhost\n  port: 8080\n  debug: false\n  mode: online\n"
                + "servers:\n  - name: a\n  - name: b\n");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "y.host");
        assertTrue(manager.save(draft).isSuccess());

        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        ConfigNode servers = reloaded.get("servers");
        assertEquals(ConfigNode.NodeType.LIST, servers.getType());
        assertEquals(2, servers.asList().size());
        assertEquals("a", servers.get(0).get("name").asString());
        assertEquals("b", servers.get(1).get("name").asString());
    }
}
