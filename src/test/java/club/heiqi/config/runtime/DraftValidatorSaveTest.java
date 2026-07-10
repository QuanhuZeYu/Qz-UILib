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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link DraftValidator} 与 {@link ConfigManager#save} 提交前钩子测试。
 *
 * <p>覆盖：只读 {@link DraftView}、custom reject / throw / null → INVALID 且无副作用、
 * 通过路径、二参兼容、错误合并、视图不可变。</p>
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

    /** 统计所有事件（不仅 BATCH_SAVE） */
    private static AtomicInteger subscribeAnyEventCount(ConfigManager manager) {
        final AtomicInteger count = new AtomicInteger(0);
        manager.eventBus().subscribe(new ConfigChangeListener() {
            @Override
            public void onConfigChanged(ConfigChangeEvent event) {
                count.incrementAndGet();
            }
        });
        return count;
    }

    @Test
    public void customRejectReturnsInvalidWithoutSideEffects() throws Exception {
        File file = seedFile(tempFolder);
        byte[] beforeBytes = fileBytes(file);
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema, new DraftValidator() {
            @Override
            public ValidationResult validate(DraftView draft) {
                return ValidationResult.error("server.host", "host not allowed");
            }
        });
        AtomicInteger eventCount = subscribeAnyEventCount(manager);

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
        assertEquals("blocked.host", draft.getDraft("server.host"));
        assertEquals(3000.0, draft.getDraft("server.port"));
        assertTrue(draft.isDirty("server.host"));

        assertEquals(0, eventCount.get());
        assertArrayEquals("文件字节应不变", beforeBytes, fileBytes(file));
    }

    @Test
    public void customThrowReturnsInvalidFailClosed() throws Exception {
        File file = seedFile(tempFolder);
        byte[] beforeBytes = fileBytes(file);
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema, new DraftValidator() {
            @Override
            public ValidationResult validate(DraftView draft) {
                throw new IllegalStateException("boom from validator");
            }
        });
        AtomicInteger eventCount = subscribeAnyEventCount(manager);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "throw.host");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");

        SaveOutcome outcome = manager.save(draft);

        assertEquals(SaveOutcome.Status.INVALID, outcome.status());
        String global = outcome.validation().errorFor(DraftValidator.GLOBAL_ERROR_PATH);
        assertNotNull(global);
        assertTrue(global.contains("boom from validator"));
        assertEquals("original.host", manager.authority().getString("server.host"));
        assertEquals("throw.host", draft.getDraft("server.host"));
        assertEquals("original.host", draft.getCurrent("server.host"));
        assertEquals(0, eventCount.get());
        assertArrayEquals(beforeBytes, fileBytes(file));
    }

    @Test
    public void customNullReturnsInvalidFailClosed() throws Exception {
        File file = seedFile(tempFolder);
        byte[] beforeBytes = fileBytes(file);
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema, new DraftValidator() {
            @Override
            public ValidationResult validate(DraftView draft) {
                return null;
            }
        });
        AtomicInteger eventCount = subscribeAnyEventCount(manager);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "null.host");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");

        SaveOutcome outcome = manager.save(draft);

        assertEquals(SaveOutcome.Status.INVALID, outcome.status());
        assertNotNull(outcome.validation().errorFor(DraftValidator.GLOBAL_ERROR_PATH));
        assertEquals("original.host", manager.authority().getString("server.host"));
        assertEquals("null.host", draft.getDraft("server.host"));
        assertEquals(0, eventCount.get());
        assertArrayEquals(beforeBytes, fileBytes(file));
    }

    @Test
    public void customPassSavesNormallyWithSingleBatchSave() throws Exception {
        File file = seedFile(tempFolder);
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema, new DraftValidator() {
            @Override
            public ValidationResult validate(DraftView draft) {
                return ValidationResult.ok();
            }
        });
        AtomicInteger eventCount = subscribeAnyEventCount(manager);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "saved.host");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");

        SaveOutcome outcome = manager.save(draft);

        assertTrue(outcome.isSuccess());
        assertEquals("saved.host", manager.authority().getString("server.host"));
        assertEquals(3000.0, manager.authority().getNumber("server.port"), 0.0);
        assertEquals("saved.host", draft.getCurrent("server.host"));
        assertFalse(draft.isDirtyAny());
        assertEquals(1, eventCount.get());

        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertEquals("saved.host", reloaded.get("server.host").asString());
        assertEquals(3000, reloaded.get("server.port").asInt());
    }

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

    @Test
    public void mergesBuiltInAndCustomFieldErrors() throws Exception {
        File file = seedFile(tempFolder);
        byte[] beforeBytes = fileBytes(file);
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema, new DraftValidator() {
            @Override
            public ValidationResult validate(DraftView draft) {
                return ValidationResult.error("server.mode", "mode blocked by custom");
            }
        });
        AtomicInteger eventCount = subscribeAnyEventCount(manager);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.port", 99999.0);
        draft.setDraft("server.mode", "test");

        SaveOutcome outcome = manager.save(draft);

        assertEquals(SaveOutcome.Status.INVALID, outcome.status());
        ValidationResult v = outcome.validation();
        assertNotNull(v.errorFor("server.port"));
        assertEquals("mode blocked by custom", v.errorFor("server.mode"));
        assertEquals(8080.0, manager.authority().getNumber("server.port"), 0.0);
        assertEquals(0, eventCount.get());
        assertArrayEquals(beforeBytes, fileBytes(file));
        assertEquals(99999.0, draft.getDraft("server.port"));
    }

    @Test
    public void samePathPrefersBuiltInMessage() throws Exception {
        File file = seedFile(tempFolder);
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema, new DraftValidator() {
            @Override
            public ValidationResult validate(DraftView draft) {
                return ValidationResult.error("server.port", "custom port message");
            }
        });

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.port", 99999.0);

        SaveOutcome outcome = manager.save(draft);
        assertEquals(SaveOutcome.Status.INVALID, outcome.status());
        String msg = outcome.validation().errorFor("server.port");
        assertNotNull(msg);
        assertFalse(msg.equals("custom port message"));
        assertTrue(msg.contains("上限") || msg.contains("大于"));
    }

    @Test
    public void validationResultMergeKeepsDistinctPaths() {
        ValidationResult a = ValidationResult.error("a", "err-a");
        ValidationResult b = ValidationResult.error("b", "err-b");
        ValidationResult m = ValidationResult.merge(a, b);
        assertEquals("err-a", m.errorFor("a"));
        assertEquals("err-b", m.errorFor("b"));
        assertEquals(2, m.errors().size());
        assertFalse(ValidationResult.merge(ValidationResult.ok(), ValidationResult.ok()).hasErrors());
    }

    @Test
    public void customPassStillRollsBackOnIoFailure() throws Exception {
        File dir = tempFolder.newFolder("not_a_file");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(dir, schema, DraftValidator.noop());
        AtomicInteger eventCount = subscribeAnyEventCount(manager);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "should.not.persist");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");

        SaveOutcome outcome = manager.save(draft);

        assertEquals(SaveOutcome.Status.IO_FAILED, outcome.status());
        assertEquals("localhost", manager.authority().getString("server.host"));
        assertEquals("localhost", draft.getCurrent("server.host"));
        assertEquals("should.not.persist", draft.getDraft("server.host"));
        assertEquals(0, eventCount.get());
    }

    /**
     * validator 仅见 DraftView：快照不可写；无法 setDraft / commit 污染原 buffer。
     */
    @Test
    public void validatorReceivesImmutableViewAndCannotMutateDraft() throws Exception {
        File file = seedFile(tempFolder);
        final AtomicReference<DraftView> captured = new AtomicReference<DraftView>();
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema(),
                new DraftValidator() {
                    @Override
                    public ValidationResult validate(DraftView draft) {
                        captured.set(draft);
                        try {
                            draft.draftSnapshot().put("server.host", "injected");
                            fail("snapshot must be unmodifiable");
                        } catch (UnsupportedOperationException expected) {
                            // ok
                        }
                        return ValidationResult.ok();
                    }
                });

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "user.host");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");
        assertTrue(manager.save(draft).isSuccess());

        DraftView view = captured.get();
        assertNotNull(view);
        assertEquals("user.host", view.getDraft("server.host"));
        assertEquals("user.host", view.draftSnapshot().get("server.host"));
        assertNotNull(view.schema());
        assertTrue(view.fieldPaths().contains("server.host"));
        // 原 draft 在校验时未被注入
        assertEquals("user.host", draft.getDraft("server.host"));
        assertEquals("user.host", manager.authority().getString("server.host"));
    }

    /**
     * custom 不能在 built-in 之后向原 draft 注入非法值：视图与 buffer 隔离。
     */
    @Test
    public void customCannotInjectIllegalValueIntoOriginalDraft() throws Exception {
        File file = seedFile(tempFolder);
        byte[] beforeBytes = fileBytes(file);
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema(),
                new DraftValidator() {
                    @Override
                    public ValidationResult validate(DraftView draft) {
                        // 试图通过 snapshot 写非法 port——应抛 UnsupportedOperationException 并由 Manager fail-closed
                        Map<String, Object> snap = draft.draftSnapshot();
                        try {
                            snap.put("server.port", 99999.0);
                            fail("expected unmodifiable");
                        } catch (UnsupportedOperationException expected) {
                            // ok
                        }
                        return ValidationResult.ok();
                    }
                });

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "ok.host");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");

        SaveOutcome outcome = manager.save(draft);
        assertTrue(outcome.isSuccess());
        // 原 draft 仍为合法 3000，未被注入 99999
        assertEquals(3000.0, draft.getDraft("server.port"));
        assertEquals(3000.0, manager.authority().getNumber("server.port"), 0.0);
        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertEquals(3000, reloaded.get("server.port").asInt());
        // 文件已变为合法保存内容（与 seed 不同）
        assertFalse(java.util.Arrays.equals(beforeBytes, fileBytes(file)));
    }

    /**
     * SIMPLE_LIST 深度只读：getDraft list / snapshot nested 修改均抛 UOE，且源 List 与 draft/current/Authority 不变。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void deepFreezePreventsListMutationFromAffectingSource() throws Exception {
        File file = tempFolder.newFile("list-config.yaml");
        write(file, "server:\n  tags: []\n  host: original.host\n");
        ConfigSchema schema = SchemaTestFactory.listSchema();
        final AtomicReference<List<String>> sourceListRef = new AtomicReference<List<String>>();
        ConfigManager manager = ConfigManager.bootstrap(file, schema, new DraftValidator() {
            @Override
            public ValidationResult validate(DraftView view) {
                Object raw = view.getDraft("server.tags");
                assertTrue(raw instanceof List);
                List<String> frozen = (List<String>) raw;
                try {
                    frozen.add("injected");
                    fail("getDraft list must be unmodifiable");
                } catch (UnsupportedOperationException expected) {
                    // ok
                }
                try {
                    frozen.set(0, "mutated");
                    fail("getDraft list set must fail");
                } catch (UnsupportedOperationException expected) {
                    // ok
                } catch (IndexOutOfBoundsException emptyOk) {
                    // 空列表 set(0) 也可能越界，再试 snapshot
                }
                Object snapVal = view.draftSnapshot().get("server.tags");
                try {
                    ((List<Object>) snapVal).clear();
                    fail("snapshot nested list must be unmodifiable");
                } catch (UnsupportedOperationException expected) {
                    // ok
                }
                try {
                    view.draftSnapshot().put("server.tags", Arrays.asList("x"));
                    fail("snapshot map put must fail");
                } catch (UnsupportedOperationException expected) {
                    // ok
                }
                return ValidationResult.error("server.tags", "reject after mutate attempt");
            }
        });

        DraftBuffer draft = manager.openDraft();
        List<String> source = new ArrayList<String>(Arrays.asList("a", "b"));
        sourceListRef.set(source);
        draft.setDraft("server.tags", source);
        draft.setDraft("server.host", "list.host");

        SaveOutcome outcome = manager.save(draft);

        assertEquals(SaveOutcome.Status.INVALID, outcome.status());
        // 源 List 未被原地修改
        assertEquals(Arrays.asList("a", "b"), sourceListRef.get());
        assertEquals(Arrays.asList("a", "b"), draft.getDraft("server.tags"));
        // current 仍为种子（默认空 list 或 load 值），未 commit
        Object currentTags = draft.getCurrent("server.tags");
        assertTrue(currentTags instanceof List);
        assertTrue(((List<?>) currentTags).isEmpty() || !((List<?>) currentTags).contains("injected"));
        // Authority 未变
        Object authTags = manager.authority().get("server.tags");
        if (authTags instanceof List) {
            assertFalse(((List<?>) authTags).contains("injected"));
        }
        assertEquals("original.host", manager.authority().getString("server.host"));
        assertEquals("list.host", draft.getDraft("server.host"));
    }

    /**
     * 嵌套 Map 深度只读。
     */
    @Test
    @SuppressWarnings("unchecked")
    public void deepFreezePreventsNestedMapMutation() throws Exception {
        File file = seedFile(tempFolder);
        final AtomicReference<Map<String, Object>> nestedRef = new AtomicReference<Map<String, Object>>();
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema(),
                new DraftValidator() {
                    @Override
                    public ValidationResult validate(DraftView view) {
                        // 非 schema 字段也可进 draftSnapshot 若被 set；此处用 host 路径不测 map。
                        // 直接测 deepFreeze 单元
                        Map<String, Object> nested = new HashMap<String, Object>();
                        nested.put("k", new ArrayList<String>(Arrays.asList("v1")));
                        Object frozen = SnapshotDraftView.deepFreeze(nested);
                        nestedRef.set(nested);
                        Map<String, Object> frozenMap = (Map<String, Object>) frozen;
                        try {
                            frozenMap.put("k2", "x");
                            fail();
                        } catch (UnsupportedOperationException expected) {
                        }
                        try {
                            ((List<Object>) frozenMap.get("k")).add("v2");
                            fail();
                        } catch (UnsupportedOperationException expected) {
                        }
                        return ValidationResult.ok();
                    }
                });
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "map.host");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");
        assertTrue(manager.save(draft).isSuccess());
        // 源 nested 未被 deepFreeze 修改
        assertEquals(1, ((List<?>) nestedRef.get().get("k")).size());
        assertEquals("v1", ((List<?>) nestedRef.get().get("k")).get(0));
    }

    /**
     * 未知可变类型 fail-closed 为 INVALID（_config），原 draft 不变。
     */
    @Test
    public void unknownMutableTypeFailClosed() throws Exception {
        File file = seedFile(tempFolder);
        byte[] before = fileBytes(file);
        ConfigManager manager = ConfigManager.bootstrap(file, SchemaTestFactory.serverSchema(),
                DraftValidator.noop());
        DraftBuffer draft = manager.openDraft();
        draft.setDraft("server.host", "ok.host");
        draft.setDraft("server.port", 3000.0);
        draft.setDraft("server.mode", "test");
        // 注入未知可变类型到 draft（非 schema 路径也可存于 map）
        draft.setDraft("server.host", new StringBuilder("mutable-builder"));

        SaveOutcome outcome = manager.save(draft);
        // built-in 可能不校验 StringBuilder；若通过 built-in，deepFreeze 在 custom 前失败
        // 实际：built-in STRING 可能不检查类型，runCustomValidator 构造 view 时 deepFreeze 抛异常
        assertEquals(SaveOutcome.Status.INVALID, outcome.status());
        String global = outcome.validation().errorFor(DraftValidator.GLOBAL_ERROR_PATH);
        assertNotNull(global);
        assertTrue(global.contains("DraftValidator failed") || global.contains("cannot freeze"));
        assertArrayEquals(before, fileBytes(file));
        // seedFile 权威态为 original.host，未写盘未 apply
        assertEquals("original.host", manager.authority().getString("server.host"));
        assertTrue(draft.getDraft("server.host") instanceof StringBuilder);
    }
}
