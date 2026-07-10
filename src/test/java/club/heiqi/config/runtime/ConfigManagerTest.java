package club.heiqi.config.runtime;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigChangeEvent;
import club.heiqi.config.ConfigChangeListener;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.ConfigNode;
import club.heiqi.config.ConfigSource;
import club.heiqi.config.schema.ConfigSchema;

import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link ConfigManager} 测试，覆盖 bootstrap、save 成功、save 校验失败、
 * save IO 失败零提交、openDraft 独立、flushRaw。
 */
public class ConfigManagerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /**
     * 写字符串到文件。
     */
    private static void write(File file, String content) throws Exception {
        FileWriter writer = new FileWriter(file);
        try {
            writer.write(content);
        } finally {
            writer.close();
        }
    }

    /**
     * bootstrap 加载默认值。
     */
    @Test
    public void bootstrapLoadsDefaults() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();

        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        assertEquals("localhost", manager.authority().getString("server.host"));
        assertEquals(8080.0, manager.authority().getNumber("server.port"), 0.0);
        assertEquals(schema, manager.schema());
        assertNotNull(manager.eventBus());
    }

    /**
     * save 成功：validate 通过 → authority 更新 → 文件写入 → eventBus 收到 BATCH_SAVE。
     */
    @Test
    public void saveSuccessUpdatesAuthorityAndFile() throws Exception {
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
        draft.setDraft("server.host", "saved.host");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");

        SaveOutcome outcome = manager.save(draft);

        assertTrue(outcome.isSuccess());
        assertEquals(SaveOutcome.Status.OK, outcome.status());
        // authority 已更新
        assertEquals("saved.host", manager.authority().getString("server.host"));
        assertEquals(3000.0, manager.authority().getNumber("server.port"), 0.0);
        // 文件已写入
        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertEquals("saved.host", reloaded.get("server.host").asString());
        assertEquals(3000, reloaded.get("server.port").asInt());
        // 事件已发布
        assertNotNull(received.get());
        assertEquals(ConfigChangeEvent.ChangeType.BATCH_SAVE, received.get().getType());
    }

    /**
     * save 校验失败：返回 invalid，authority 不变，文件不变。
     */
    @Test
    public void saveValidationFailureDoesNotWrite() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file,
                "server:\n" +
                "  host: original.host\n" +
                "  port: 8080\n" +
                "  debug: false\n" +
                "  mode: online\n");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.port", 99999.0); // 超范围

        SaveOutcome outcome = manager.save(draft);

        assertEquals(SaveOutcome.Status.INVALID, outcome.status());
        assertFalse(outcome.isSuccess());
        assertNotNull(outcome.validation());
        assertTrue(outcome.validation().hasErrors());
        // authority 不变
        assertEquals("original.host", manager.authority().getString("server.host"));
        assertEquals(8080.0, manager.authority().getNumber("server.port"), 0.0);
        // 文件不变
        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertEquals("original.host", reloaded.get("server.host").asString());
    }

    /**
     * save IO 失败：写盘失败发生在 Authority/current 提交前，返回 ioFailed。
     *
     * <p>用目录作为 file 路径触发 FileOutputStream 失败。</p>
     */
    @Test
    public void saveIoFailureRollsBackAuthority() throws Exception {
        // file 指向一个已存在的目录，写盘必然失败
        File dir = tempFolder.newFolder("not_a_file");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(dir, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "should.not.persist");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");

        SaveOutcome outcome = manager.save(draft);

        assertEquals(SaveOutcome.Status.IO_FAILED, outcome.status());
        assertFalse(outcome.isSuccess());
        assertNotNull(outcome.errorMessage());
        // Authority 尚未应用 draft，保持默认值
        assertEquals("localhost", manager.authority().getString("server.host"));
        assertEquals(8080.0, manager.authority().getNumber("server.port"), 0.0);
    }

    /**
     * openDraft 独立：编辑 draft 不影响 authority。
     */
    @Test
    public void openDraftIsIndependentFromAuthority() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "draft.only");

        assertEquals("draft.only", draft.getDraft("server.host"));
        assertEquals("localhost", manager.authority().getString("server.host"));
    }

    /**
     * flushRaw：legacy setRawJson 后 flushRaw → 文件更新。
     */
    @Test
    public void flushRawPersistsLegacyChanges() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        manager.authority().legacy().setRawJson("extra", "nested:\n  value: flushed\n");
        manager.flushRaw();

        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertEquals("flushed", reloaded.get("extra.nested.value").asString());
        // schema 字段仍保留
        assertEquals("localhost", reloaded.get("server.host").asString());
    }
}
