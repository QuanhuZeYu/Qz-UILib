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
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link DraftValidator} 与 {@link ConfigManager#save} 提交前钩子测试。
 *
 * <p>覆盖：custom reject / throw / null → INVALID 且 Authority、文件字节、draft current 不变、
 * BATCH_SAVE 0 次、draft 输入可继续编辑；通过时正常保存且仅一次 BATCH_SAVE；
 * 二参 bootstrap 行为不变；built-in + custom 错误合并。</p>
 */
public class DraftValidatorSaveTest {

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

    private static File seedFile(TemporaryFolder folder) throws Exception {
        File file = folder.newFile("config.yaml");
        write(file,
                "server:\n" +
                "  host: original.host\n" +
                "  port: 8080\n" +
                "  debug: false\n" +
                "  mode: online\n");
        return file;
    }

    private static byte[] fileBytes(File file) throws Exception {
        return Files.readAllBytes(file.toPath());
    }

    private static AtomicInteger subscribeBatchSaveCount(ConfigManager manager) {
        final AtomicInteger count = new AtomicInteger(0);
        manager.eventBus().subscribe(new ConfigChangeListener() {
            @Override
            public void onConfigChanged(ConfigChangeEvent event) {
                if (event.getType() == ConfigChangeEvent.ChangeType.BATCH_SAVE) {
                    count.incrementAndGet();
                }
            }
        });
        return count;
    }

    /**
     * custom 拒绝：INVALID；Authority / 文件 / current 不变；BATCH_SAVE 0；draft 输入保留。
     */
    @Test
    public void customRejectReturnsInvalidWithoutSideEffects() throws Exception {
        File file = seedFile(tempFolder);
        byte[] beforeBytes = fileBytes(file);
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema, new DraftValidator() {
            @Override
            public ValidationResult validate(DraftBuffer draft) {
                return ValidationResult.error("server.host", "host not allowed");
            }
        });
        AtomicInteger batchSaveCount = subscribeBatchSaveCount(manager);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "blocked.host");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");

        SaveOutcome outcome = manager.save(draft);

        assertEquals(SaveOutcome.Status.INVALID, outcome.status());
        assertFalse(outcome.isSuccess());
        assertNotNull(outcome.validation());
        assertTrue(outcome.validation().hasErrors());
        assertEquals("host not allowed", outcome.validation().errorFor("server.host"));

        assertEquals("original.host", manager.authority().getString("server.host"));
        assertEquals(8080.0, manager.authority().getNumber("server.port"), 0.0);
        assertEquals("original.host", draft.getCurrent("server.host"));
        assertEquals(8080.0, draft.getCurrent("server.port"));
        // draft 输入保留，可继续编辑
        assertEquals("blocked.host", draft.getDraft("server.host"));
        assertEquals(3000.0, draft.getDraft("server.port"));
        assertTrue(draft.isDirty("server.host"));

        assertEquals(0, batchSaveCount.get());
        assertEquals("文件字节应不变", new String(beforeBytes), new String(fileBytes(file)));
    }

    /**
     * custom 抛 RuntimeException：fail-closed INVALID，全局 path，无副作用。
     */
    @Test
    public void customThrowReturnsInvalidFailClosed() throws Exception {
        File file = seedFile(tempFolder);
        byte[] beforeBytes = fileBytes(file);
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema, new DraftValidator() {
            @Override
            public ValidationResult validate(DraftBuffer draft) {
                throw new IllegalStateException("boom from validator");
            }
        });
        AtomicInteger batchSaveCount = subscribeBatchSaveCount(manager);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "throw.host");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");

        SaveOutcome outcome = manager.save(draft);

        assertEquals(SaveOutcome.Status.INVALID, outcome.status());
        assertNotNull(outcome.validation());
        assertTrue(outcome.validation().hasErrors());
        String global = outcome.validation().errorFor(DraftValidator.GLOBAL_ERROR_PATH);
        assertNotNull(global);
        assertTrue(global.contains("boom from validator"));

        assertEquals("original.host", manager.authority().getString("server.host"));
        assertEquals("throw.host", draft.getDraft("server.host"));
        assertEquals("original.host", draft.getCurrent("server.host"));
        assertEquals(0, batchSaveCount.get());
        assertEquals(new String(beforeBytes), new String(fileBytes(file)));
    }

    /**
     * custom 返回 null：fail-closed INVALID，全局 path，无副作用。
     */
    @Test
    public void customNullReturnsInvalidFailClosed() throws Exception {
        File file = seedFile(tempFolder);
        byte[] beforeBytes = fileBytes(file);
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema, new DraftValidator() {
            @Override
            public ValidationResult validate(DraftBuffer draft) {
                return null;
            }
        });
        AtomicInteger batchSaveCount = subscribeBatchSaveCount(manager);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "null.host");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");

        SaveOutcome outcome = manager.save(draft);

        assertEquals(SaveOutcome.Status.INVALID, outcome.status());
        assertNotNull(outcome.validation().errorFor(DraftValidator.GLOBAL_ERROR_PATH));
        assertEquals("original.host", manager.authority().getString("server.host"));
        assertEquals("null.host", draft.getDraft("server.host"));
        assertEquals("original.host", draft.getCurrent("server.host"));
        assertEquals(0, batchSaveCount.get());
        assertEquals(new String(beforeBytes), new String(fileBytes(file)));
    }

    /**
     * custom 通过：正常保存，Authority/文件更新，BATCH_SAVE 恰好一次。
     */
    @Test
    public void customPassSavesNormallyWithSingleBatchSave() throws Exception {
        File file = seedFile(tempFolder);
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema, new DraftValidator() {
            @Override
            public ValidationResult validate(DraftBuffer draft) {
                return ValidationResult.ok();
            }
        });
        AtomicInteger batchSaveCount = subscribeBatchSaveCount(manager);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "saved.host");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");

        SaveOutcome outcome = manager.save(draft);

        assertTrue(outcome.isSuccess());
        assertEquals(SaveOutcome.Status.OK, outcome.status());
        assertEquals("saved.host", manager.authority().getString("server.host"));
        assertEquals(3000.0, manager.authority().getNumber("server.port"), 0.0);
        assertEquals("saved.host", draft.getCurrent("server.host"));
        assertFalse(draft.isDirtyAny());
        assertEquals(1, batchSaveCount.get());

        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertEquals("saved.host", reloaded.get("server.host").asString());
        assertEquals(3000, reloaded.get("server.port").asInt());
    }

    /**
     * 二参 bootstrap 默认 no-op：行为与原先一致，validator 为 noop 单例。
     */
    @Test
    public void twoArgBootstrapUsesNoopAndSaves() throws Exception {
        File file = seedFile(tempFolder);
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        assertSame(DraftValidator.noop(), manager.draftValidator());

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "compat.host");
        draft.setDraft("server.port", 4000.0);
        draft.setDraft("server.mode", "test");
        SaveOutcome outcome = manager.save(draft);

        assertTrue(outcome.isSuccess());
        assertEquals("compat.host", manager.authority().getString("server.host"));
    }

    /**
     * bootstrap 传 null validator 拒绝。
     */
    @Test
    public void bootstrapRejectsNullValidator() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        try {
            ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema(), null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("validator"));
        }
    }

    /**
     * built-in 字段错误 + custom 另一 path 错误：合并后两者都在，不写盘。
     */
    @Test
    public void mergesBuiltInAndCustomFieldErrors() throws Exception {
        File file = seedFile(tempFolder);
        byte[] beforeBytes = fileBytes(file);
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema, new DraftValidator() {
            @Override
            public ValidationResult validate(DraftBuffer draft) {
                return ValidationResult.error("server.mode", "mode blocked by custom");
            }
        });
        AtomicInteger batchSaveCount = subscribeBatchSaveCount(manager);

        DraftBuffer draft = manager.openDraft();
        // built-in：port 超范围
        draft.setDraft("server.port", 99999.0);
        draft.setDraft("server.mode", "test");

        SaveOutcome outcome = manager.save(draft);

        assertEquals(SaveOutcome.Status.INVALID, outcome.status());
        ValidationResult v = outcome.validation();
        assertNotNull(v);
        assertTrue(v.hasErrors());
        assertNotNull("应保留 built-in port 错误", v.errorFor("server.port"));
        assertEquals("mode blocked by custom", v.errorFor("server.mode"));
        assertEquals("original.host", manager.authority().getString("server.host"));
        assertEquals(8080.0, manager.authority().getNumber("server.port"), 0.0);
        assertEquals(0, batchSaveCount.get());
        assertEquals(new String(beforeBytes), new String(fileBytes(file)));
        // draft 输入保留
        assertEquals(99999.0, draft.getDraft("server.port"));
        assertEquals(8080.0, draft.getCurrent("server.port"));
    }

    /**
     * 同一 path 两侧均有错误时，保留 built-in 消息。
     */
    @Test
    public void samePathPrefersBuiltInMessage() throws Exception {
        File file = seedFile(tempFolder);
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema, new DraftValidator() {
            @Override
            public ValidationResult validate(DraftBuffer draft) {
                return ValidationResult.error("server.port", "custom port message");
            }
        });

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.port", 99999.0);

        SaveOutcome outcome = manager.save(draft);
        assertEquals(SaveOutcome.Status.INVALID, outcome.status());
        String msg = outcome.validation().errorFor("server.port");
        assertNotNull(msg);
        assertFalse("应保留内置消息而非 custom", msg.equals("custom port message"));
        assertTrue(msg.contains("上限") || msg.contains("大于"));
    }

    /**
     * ValidationResult.merge 单元行为。
     */
    @Test
    public void validationResultMergeKeepsDistinctPaths() {
        ValidationResult a = ValidationResult.error("a", "err-a");
        ValidationResult b = ValidationResult.error("b", "err-b");
        ValidationResult m = ValidationResult.merge(a, b);
        assertEquals("err-a", m.errorFor("a"));
        assertEquals("err-b", m.errorFor("b"));
        assertEquals(2, m.errors().size());

        assertFalse(ValidationResult.merge(ValidationResult.ok(), ValidationResult.ok()).hasErrors());
        assertEquals("err-a", ValidationResult.merge(a, ValidationResult.ok()).errorFor("a"));
        assertEquals("err-b", ValidationResult.merge(ValidationResult.ok(), b).errorFor("b"));
    }

    /**
     * 带 custom validator 时 IO 失败仍回滚 Authority，不 commit current。
     */
    @Test
    public void customPassStillRollsBackOnIoFailure() throws Exception {
        File dir = tempFolder.newFolder("not_a_file");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(dir, schema, DraftValidator.noop());
        AtomicInteger batchSaveCount = subscribeBatchSaveCount(manager);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "should.not.persist");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");

        SaveOutcome outcome = manager.save(draft);

        assertEquals(SaveOutcome.Status.IO_FAILED, outcome.status());
        assertEquals("localhost", manager.authority().getString("server.host"));
        assertEquals("localhost", draft.getCurrent("server.host"));
        assertEquals("should.not.persist", draft.getDraft("server.host"));
        assertEquals(0, batchSaveCount.get());
    }
}
