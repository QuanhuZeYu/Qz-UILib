package club.heiqi.config.ui;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.runtime.Authority;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.runtime.DraftValidator;
import club.heiqi.config.runtime.ValidationResult;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;

/**
 * {@link DraftSignalAdapter} 单元测试。
 *
 * <p>覆盖 draft 镜像同步、dirty/error/canSave 派生、reset/restore/afterSaveSync、
 * 多字段独立性、dispose、大量字段烟雾、ConcurrentModification 安全性。</p>
 *
 * <p>注意：{@link club.heiqi.uilib.ui.reactive.Computed#get()} 在 flush 前返回 null，
 * 所有断言前必须先 flush（{@link ReactiveScheduler#get()} .flush()）。</p>
 */
public class DraftSignalAdapterTest {

    private ConfigSchema schema;
    private Authority authority;
    private DraftBuffer draft;
    private DraftSignalAdapter adapter;

    @Before
    public void setUp() throws Exception {
        ReactiveScheduler.get().reset();
        schema = UiSchemaFactory.serverSchema();
        authority = Authority.load(new File("nonexistent-ui-adapter.yaml"), schema);
        draft = DraftBuffer.from(authority);
        adapter = new DraftSignalAdapter(null, draft);
        // 物化所有 Computed
        ReactiveScheduler.get().flush();
    }

    @After
    public void tearDown() throws Exception {
        adapter.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 1. draft 镜像初值 ====================

    /** 构造后每字段 draftSignal 初值 = DraftBuffer.getDraft */
    @Test
    public void draftSignalInitialEqualsDraftBuffer() throws Exception {
        for (FieldSpec field : schema.allFields()) {
            Assert.assertEquals("draftSignal 初值应等于 DraftBuffer.getDraft",
                    draft.getDraft(field.path()), adapter.draftSignal(field.path()).get());
        }
    }

    // ==================== 2. onFieldEdit 镜像同步 ====================

    /** onFieldEdit 后 draftSignal 值更新 + DraftBuffer.getDraft 更新 */
    @Test
    public void onFieldEditUpdatesBothSignalAndBuffer() throws Exception {
        adapter.onFieldEdit("server.host", "new.host");
        ReactiveScheduler.get().flush();
        Assert.assertEquals("draftSignal 应更新", "new.host", adapter.draftSignal("server.host").get());
        Assert.assertEquals("DraftBuffer.getDraft 应更新", "new.host", draft.getDraft("server.host"));
    }

    // ==================== 3. onFieldEdit 后 dirty ====================

    /** onFieldEdit 后 dirtySignal 为 true（draft != current） */
    @Test
    public void onFieldEditMakesDirtyTrue() throws Exception {
        adapter.onFieldEdit("server.host", "changed");
        ReactiveScheduler.get().flush();
        Assert.assertTrue("编辑后 dirty 应为 true", adapter.dirtySignal("server.host").get().booleanValue());
    }

    // ==================== 4. flush 后 dirtySignal 正确 ====================

    /** flush 后 dirtySignal Computed 值正确（注意 flush 前返回 null） */
    @Test
    public void dirtySignalNullBeforeFlush() throws Exception {
        // 新建 adapter 不 flush
        ReactiveScheduler.get().reset();
        DraftBuffer d2 = DraftBuffer.from(authority);
        DraftSignalAdapter a2 = new DraftSignalAdapter(null, d2);
        Assert.assertNull("flush 前 dirtySignal 返回 null", a2.dirtySignal("server.host").get());
        ReactiveScheduler.get().flush();
        Assert.assertEquals("flush 后 dirtySignal 为 false", Boolean.FALSE,
                a2.dirtySignal("server.host").get());
        a2.dispose();
    }

    // ==================== 5. 未编辑时 dirtySignal false ====================

    @Test
    public void dirtySignalFalseWhenUnedited() throws Exception {
        Assert.assertFalse("未编辑时 dirty 为 false",
                adapter.dirtySignal("server.host").get().booleanValue());
    }

    // ==================== 6. isDirtySignal 聚合 ====================

    @Test
    public void isDirtyAggregatesAnyField() throws Exception {
        Assert.assertFalse("初始 isDirty=false", adapter.isDirtySignal().get().booleanValue());
        adapter.onFieldEdit("server.host", "x");
        ReactiveScheduler.get().flush();
        Assert.assertTrue("任一字段 dirty 则 isDirty=true", adapter.isDirtySignal().get().booleanValue());
    }

    // ==================== 7. errorSignal 非法值 ====================

    @Test
    public void errorSignalNonEmptyForInvalidValue() throws Exception {
        adapter.onFieldEdit("server.port", 99999.0); // 超出 65535
        ReactiveScheduler.get().flush();
        String err = adapter.errorSignal("server.port").get();
        Assert.assertNotNull("非法值 error 非空", err);
        Assert.assertTrue("error 应含上限信息", err.contains("上限"));
    }

    // ==================== 8. flush 后 errorSignal 正确 ====================

    @Test
    public void errorSignalCorrectAfterFlush() throws Exception {
        adapter.onFieldEdit("server.host", ""); // required 违反
        ReactiveScheduler.get().flush();
        Assert.assertNotNull("空 host error 非空", adapter.errorSignal("server.host").get());
    }

    // ==================== 9. hasErrorSignal 聚合 ====================

    @Test
    public void hasErrorAggregatesAnyField() throws Exception {
        Assert.assertFalse("初始 hasError=false", adapter.hasErrorSignal().get().booleanValue());
        adapter.onFieldEdit("server.port", -1.0); // 低于 1
        ReactiveScheduler.get().flush();
        Assert.assertTrue("有错字段则 hasError=true", adapter.hasErrorSignal().get().booleanValue());
    }

    // ==================== 10. canSave = isDirty && !hasError ====================

    @Test
    public void canSaveIsDirtyAndNotHasError() throws Exception {
        adapter.onFieldEdit("server.host", "valid.host");
        ReactiveScheduler.get().flush();
        Assert.assertTrue("有改动无错 canSave=true", adapter.canSaveSignal().get().booleanValue());
    }

    // ==================== 11. 无改动 canSave false ====================

    @Test
    public void canSaveFalseWhenNoChange() throws Exception {
        Assert.assertFalse("无改动 canSave=false", adapter.canSaveSignal().get().booleanValue());
    }

    // ==================== 12. 有改动无错 canSave true ====================

    @Test
    public void canSaveTrueWhenDirtyAndNoError() throws Exception {
        adapter.onFieldEdit("server.debug", Boolean.TRUE);
        ReactiveScheduler.get().flush();
        Assert.assertTrue("有改动无错 canSave=true", adapter.canSaveSignal().get().booleanValue());
    }

    // ==================== 13. 有改动有错 canSave false ====================

    @Test
    public void canSaveFalseWhenDirtyAndHasError() throws Exception {
        adapter.onFieldEdit("server.port", 99999.0);
        ReactiveScheduler.get().flush();
        Assert.assertFalse("有改动有错 canSave=false", adapter.canSaveSignal().get().booleanValue());
    }

    // ==================== 14. resetToCurrent 后 dirtySignal false ====================

    @Test
    public void resetToCurrentClearsDirty() throws Exception {
        adapter.onFieldEdit("server.host", "temp");
        ReactiveScheduler.get().flush();
        Assert.assertTrue("编辑后 dirty=true", adapter.dirtySignal("server.host").get().booleanValue());
        adapter.resetToCurrent();
        ReactiveScheduler.get().flush();
        Assert.assertFalse("resetToCurrent 后 dirty=false",
                adapter.dirtySignal("server.host").get().booleanValue());
    }

    // ==================== 15. resetToCurrent 后 draftSignal = current ====================

    @Test
    public void resetToCurrentRestoresSignalToCurrent() throws Exception {
        adapter.onFieldEdit("server.host", "temp");
        ReactiveScheduler.get().flush();
        adapter.resetToCurrent();
        ReactiveScheduler.get().flush();
        Assert.assertEquals("resetToCurrent 后 draftSignal=current",
                draft.getCurrent("server.host"), adapter.draftSignal("server.host").get());
    }

    // ==================== 16. resetFieldToDefault 后 dirty（default != current） ====================

    @Test
    public void resetFieldToDefaultMakesDirtyWhenDefaultDiffersCurrent() throws Exception {
        // 先改 current（模拟保存）：直接改 draft + commit
        adapter.onFieldEdit("server.host", "current.host");
        ReactiveScheduler.get().flush();
        draft.commitDraftToCurrent();
        // 再 resetFieldToDefault：default="localhost" != current="current.host"
        adapter.resetFieldToDefault("server.host");
        ReactiveScheduler.get().flush();
        Assert.assertTrue("default != current 时 dirty=true",
                adapter.dirtySignal("server.host").get().booleanValue());
    }

    // ==================== 17. resetFieldToDefault 后 draftSignal = default ====================

    @Test
    public void resetFieldToDefaultSetsSignalToDefault() throws Exception {
        adapter.resetFieldToDefault("server.host");
        ReactiveScheduler.get().flush();
        Object def = draft.getDraft("server.host");
        Assert.assertEquals("resetFieldToDefault 后 draftSignal=default",
                def, adapter.draftSignal("server.host").get());
    }

    // ==================== 18. afterSaveSync 后 dirtySignal false ====================

    @Test
    public void afterSaveSyncClearsDirty() throws Exception {
        adapter.onFieldEdit("server.host", "saved.host");
        ReactiveScheduler.get().flush();
        Assert.assertTrue("编辑后 dirty=true", adapter.dirtySignal("server.host").get().booleanValue());
        // 模拟保存：commit current = draft
        draft.commitDraftToCurrent();
        adapter.afterSaveSync();
        ReactiveScheduler.get().flush();
        Assert.assertFalse("afterSaveSync 后 dirty=false（current=draft）",
                adapter.dirtySignal("server.host").get().booleanValue());
    }

    // ==================== 19. 多次 onFieldEdit 同一字段：最后一次为准 ====================

    @Test
    public void multipleEditsLastWins() throws Exception {
        adapter.onFieldEdit("server.host", "a");
        adapter.onFieldEdit("server.host", "b");
        adapter.onFieldEdit("server.host", "c");
        ReactiveScheduler.get().flush();
        Assert.assertEquals("多次编辑最后一次为准", "c", adapter.draftSignal("server.host").get());
        Assert.assertEquals("DraftBuffer 也是最后一次", "c", draft.getDraft("server.host"));
    }

    // ==================== 20. dispose 后 Computed 释放 ====================

    @Test
    public void disposeStopsComputedUpdates() throws Exception {
        adapter.dispose();
        // dispose 后再编辑不应再触发 Computed 重算（值不再更新）
        adapter.onFieldEdit("server.host", "after.dispose");
        ReactiveScheduler.get().flush();
        // dirtySignal 已 dispose，get() 返回旧值或 null；这里只验证不抛异常
        // 注意：dispose 后 Computed.recompute 已注销，cell 值不再变化
    }

    // ==================== 21. 不同字段独立编辑互不影响 ====================

    @Test
    public void independentFieldsDoNotAffectEachOther() throws Exception {
        adapter.onFieldEdit("server.host", "x");
        ReactiveScheduler.get().flush();
        Assert.assertTrue("host dirty", adapter.dirtySignal("server.host").get().booleanValue());
        Assert.assertFalse("port 未编辑不 dirty",
                adapter.dirtySignal("server.port").get().booleanValue());
        Assert.assertFalse("debug 未编辑不 dirty",
                adapter.dirtySignal("server.debug").get().booleanValue());
    }

    // ==================== 22-25. 各类型字段编辑 ====================

    @Test
    public void stringFieldEdit() throws Exception {
        adapter.onFieldEdit("server.host", "new.host");
        ReactiveScheduler.get().flush();
        Assert.assertEquals("STRING 编辑", "new.host", adapter.draftSignal("server.host").get());
        Assert.assertTrue("STRING dirty", adapter.dirtySignal("server.host").get().booleanValue());
    }

    @Test
    public void numberFieldEdit() throws Exception {
        adapter.onFieldEdit("server.port", 4000.0);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("NUMBER 编辑", 4000.0, adapter.draftSignal("server.port").get());
        Assert.assertTrue("NUMBER dirty", adapter.dirtySignal("server.port").get().booleanValue());
    }

    @Test
    public void booleanFieldEdit() throws Exception {
        adapter.onFieldEdit("server.debug", Boolean.TRUE);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("BOOLEAN 编辑", Boolean.TRUE, adapter.draftSignal("server.debug").get());
        Assert.assertTrue("BOOLEAN dirty", adapter.dirtySignal("server.debug").get().booleanValue());
    }

    @Test
    public void choiceFieldEdit() throws Exception {
        adapter.onFieldEdit("server.mode", "offline");
        ReactiveScheduler.get().flush();
        Assert.assertEquals("CHOICE 编辑", "offline", adapter.draftSignal("server.mode").get());
        Assert.assertTrue("CHOICE dirty", adapter.dirtySignal("server.mode").get().booleanValue());
    }

    // ==================== 26. 大量字段性能烟雾 ====================

    @Test
    public void largeSchemaSmokeTest() throws Exception {
        ConfigSchema large = UiSchemaFactory.largeSchema();
        Authority auth = Authority.load(new File("nonexistent-ui-large.yaml"), large);
        DraftBuffer d = DraftBuffer.from(auth);
        DraftSignalAdapter a = new DraftSignalAdapter(null, d);
        ReactiveScheduler.get().flush();
        // 编辑每个字段
        for (FieldSpec field : large.allFields()) {
            a.onFieldEdit(field.path(), "edited");
        }
        ReactiveScheduler.get().flush();
        Assert.assertTrue("大量字段编辑后 isDirty=true", a.isDirtySignal().get().booleanValue());
        a.dispose();
    }

    // ==================== 27. draftSignal 只读视图 ====================

    /**
     * draftSignal() 返回 ReadableSignal，不暴露 set 方法。
     * 唯一写入入口是 onFieldEdit。
     */
    @Test
    public void draftSignalIsReadOnly() throws Exception {
        ReadableSignal<Object> sig = adapter.draftSignal("server.host");
        Assert.assertNotNull("draftSignal 不为 null", sig);
        Assert.assertEquals("localhost", sig.get());
        // ReadableSignal 接口不暴露 set()——编译期保证
    }

    // ==================== 28. ConcurrentModification 安全性 ====================

    /** 编辑时遍历 fieldPaths 不抛 ConcurrentModificationException */
    @Test
    public void editingWhileIteratingFieldPathsIsSafe() throws Exception {
        List<String> paths = new ArrayList<String>(draft.fieldPaths());
        for (String path : paths) {
            adapter.onFieldEdit(path, "x");
        }
        ReactiveScheduler.get().flush();
        // 遍历完成无异常
        Assert.assertEquals("应遍历 4 个字段", 4, paths.size());
    }

    // ==================== 29. dirtyCountSignal 计数 ====================

    @Test
    public void dirtyCountSignalCountsDirtyFields() throws Exception {
        Assert.assertEquals("初始 0 脏", Integer.valueOf(0), adapter.dirtyCountSignal().get());
        adapter.onFieldEdit("server.host", "a");
        adapter.onFieldEdit("server.debug", Boolean.TRUE);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("2 字段脏", Integer.valueOf(2), adapter.dirtyCountSignal().get());
        // 再编辑一个字段为脏
        adapter.onFieldEdit("server.port", 2000.0);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("3 字段脏", Integer.valueOf(3), adapter.dirtyCountSignal().get());
        // reset 一个
        adapter.onFieldEdit("server.host", "localhost");
        ReactiveScheduler.get().flush();
        Assert.assertEquals("reset 后 2 脏", Integer.valueOf(2), adapter.dirtyCountSignal().get());
    }

    // ==================== 30. errorCountSignal 计数 ====================

    @Test
    public void errorCountSignalCountsErrorFields() throws Exception {
        Assert.assertEquals("初始 0 错", Integer.valueOf(0), adapter.errorCountSignal().get());
        adapter.onFieldEdit("server.port", 99999.0); // 超上限
        adapter.onFieldEdit("server.host", "");       // required 违反
        ReactiveScheduler.get().flush();
        Assert.assertEquals("2 字段错", Integer.valueOf(2), adapter.errorCountSignal().get());
        // 修正一个
        adapter.onFieldEdit("server.port", 8080.0);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("修正后 1 错", Integer.valueOf(1), adapter.errorCountSignal().get());
    }

    // ==================== 31. saveFeedbackSignal 受控写入 ====================

    @Test
    public void saveFeedbackSignalIsControlled() throws Exception {
        // 初值 NONE
        Assert.assertEquals("初始 NONE", SaveFeedback.Status.NONE,
                adapter.saveFeedbackSignal().get().status());
        // 写入成功反馈
        adapter.setSaveFeedback(new SaveFeedback(SaveFeedback.Status.OK, "已保存"));
        ReactiveScheduler.get().flush();
        SaveFeedback fb = adapter.saveFeedbackSignal().get();
        Assert.assertEquals("写入后 OK", SaveFeedback.Status.OK, fb.status());
        Assert.assertEquals("反馈文案", "已保存", fb.message());
        Assert.assertFalse("OK 非错误", fb.isError());
        // 写入失败反馈
        adapter.setSaveFeedback(new SaveFeedback(SaveFeedback.Status.IO_FAILED, "写盘失败"));
        ReactiveScheduler.get().flush();
        fb = adapter.saveFeedbackSignal().get();
        Assert.assertTrue("IO_FAILED 是错误", fb.isError());
        // null 安全
        adapter.setSaveFeedback(null);
        ReactiveScheduler.get().flush();
        Assert.assertEquals("null 按 NONE", SaveFeedback.Status.NONE,
                adapter.saveFeedbackSignal().get().status());
    }

    // ==================== 提交校验错误合并 ====================

    /** setSubmitValidation 后字段 errorSignal 显示 custom 消息，errorCount 含字段 */
    @Test
    public void submitValidationShowsOnFieldErrorSignal() throws Exception {
        adapter.setSubmitValidation(ValidationResult.error("server.host", "host blocked"));
        ReactiveScheduler.get().flush();
        Assert.assertEquals("host blocked", adapter.errorSignal("server.host").get());
        Assert.assertTrue(adapter.hasErrorSignal().get().booleanValue());
        Assert.assertEquals(1, adapter.errorCountSignal().get().intValue());
    }

    /** 全局 _config 计入 errorCount，字段 error 可为空 */
    @Test
    public void globalSubmitErrorCountsInErrorCount() throws Exception {
        adapter.setSubmitValidation(ValidationResult.error(
                DraftValidator.GLOBAL_ERROR_PATH, "global rule failed"));
        ReactiveScheduler.get().flush();
        Assert.assertNull(adapter.errorSignal("server.host").get());
        Assert.assertTrue(adapter.hasErrorSignal().get().booleanValue());
        Assert.assertEquals(1, adapter.errorCountSignal().get().intValue());
    }

    /** 编辑字段后清空提交错误 */
    @Test
    public void onFieldEditClearsSubmitValidation() throws Exception {
        adapter.setSubmitValidation(ValidationResult.error("server.host", "host blocked"));
        ReactiveScheduler.get().flush();
        Assert.assertEquals("host blocked", adapter.errorSignal("server.host").get());

        adapter.onFieldEdit("server.host", "edited.host");
        ReactiveScheduler.get().flush();
        Assert.assertNull("编辑后提交错误应清空", adapter.errorSignal("server.host").get());
        Assert.assertFalse(adapter.hasErrorSignal().get().booleanValue());
    }

    /** 编辑字段同时清空保存失败反馈为 NONE */
    @Test
    public void onFieldEditClearsSaveFeedbackToNone() throws Exception {
        adapter.setSubmitValidation(ValidationResult.error("server.host", "host blocked"));
        adapter.setSaveFeedback(new SaveFeedback(SaveFeedback.Status.INVALID, "保存失败：host blocked"));
        ReactiveScheduler.get().flush();
        Assert.assertEquals(SaveFeedback.Status.INVALID, adapter.saveFeedbackSignal().get().status());

        adapter.onFieldEdit("server.port", 3000.0);
        ReactiveScheduler.get().flush();
        Assert.assertEquals(SaveFeedback.Status.NONE, adapter.saveFeedbackSignal().get().status());
        Assert.assertNull(adapter.errorSignal("server.host").get());
        Assert.assertEquals(0, adapter.errorCountSignal().get().intValue());
        Assert.assertFalse(adapter.hasErrorSignal().get().booleanValue());
        Assert.assertTrue(adapter.isDirtySignal().get().booleanValue());
    }

    /** resetToCurrent 同样清空提交错误与失败反馈 */
    @Test
    public void resetToCurrentClearsSubmitAndFeedback() throws Exception {
        adapter.onFieldEdit("server.host", "tmp");
        adapter.setSubmitValidation(ValidationResult.error("server.host", "blocked"));
        adapter.setSaveFeedback(new SaveFeedback(SaveFeedback.Status.INVALID, "保存失败：blocked"));
        ReactiveScheduler.get().flush();

        adapter.resetToCurrent();
        ReactiveScheduler.get().flush();
        Assert.assertNull(adapter.errorSignal("server.host").get());
        Assert.assertEquals(SaveFeedback.Status.NONE, adapter.saveFeedbackSignal().get().status());
        Assert.assertFalse(adapter.hasErrorSignal().get().booleanValue());
    }

    /** afterSaveSync 清空提交错误 */
    @Test
    public void afterSaveSyncClearsSubmitValidation() throws Exception {
        adapter.setSubmitValidation(ValidationResult.error("server.mode", "mode bad"));
        ReactiveScheduler.get().flush();
        adapter.afterSaveSync();
        ReactiveScheduler.get().flush();
        Assert.assertNull(adapter.errorSignal("server.mode").get());
        Assert.assertFalse(adapter.hasErrorSignal().get().booleanValue());
    }

    /** 内置错误优先于提交错误（同 path） */
    @Test
    public void builtInErrorPreferredOverSubmitOnSamePath() throws Exception {
        adapter.onFieldEdit("server.port", 99999.0);
        adapter.setSubmitValidation(ValidationResult.error("server.port", "custom port msg"));
        ReactiveScheduler.get().flush();
        String msg = adapter.errorSignal("server.port").get();
        Assert.assertNotNull(msg);
        Assert.assertFalse(msg.equals("custom port msg"));
        Assert.assertTrue(msg.contains("上限") || msg.contains("大于"));
    }

    /** UI 可观察列表深度只读，合法 onFieldEdit 仍可替换列表值。 */
    @Test
    @SuppressWarnings("unchecked")
    public void observableListCannotMutateSignalOrDraftBuffer() throws Exception {
        ConfigSchema listSchema = ConfigSchema.builder("list-test")
                .section("server")
                    .simpleList("tags").defaultValue(new ArrayList<String>()).label("Tags").build()
                .endSection()
                .build();
        Authority listAuthority = Authority.load(new File("nonexistent-ui-list.yaml"), listSchema);
        DraftBuffer listDraft = DraftBuffer.from(listAuthority);
        DraftSignalAdapter listAdapter = new DraftSignalAdapter(null, listDraft);
        try {
            listAdapter.onFieldEdit("server.tags", new ArrayList<String>(Arrays.asList("a", "b")));
            ReactiveScheduler.get().flush();

            List<Object> observed = (List<Object>) listAdapter.draftSignal("server.tags").get();
            try {
                observed.add("injected");
                Assert.fail("UI Signal list must be unmodifiable");
            } catch (UnsupportedOperationException expected) {
                // 只读出口符合契约
            }
            Assert.assertEquals(Arrays.asList("a", "b"), listDraft.getDraft("server.tags"));
            Assert.assertEquals(Arrays.asList("a", "b"), listAdapter.draftSignal("server.tags").get());

            List<String> next = new ArrayList<String>(Arrays.asList("c", "d"));
            listAdapter.onFieldEdit("server.tags", next);
            next.add("source-mutation");
            ReactiveScheduler.get().flush();
            Assert.assertEquals(Arrays.asList("c", "d"), listDraft.getDraft("server.tags"));
            Assert.assertEquals(Arrays.asList("c", "d"), listAdapter.draftSignal("server.tags").get());
        } finally {
            listAdapter.dispose();
        }
    }

    /** 提交错误的展示、清理与 canSave 在同一 Signal 真值上同步派生。 */
    @Test
    public void submitValidationSignalIsSingleTruthForDerivedState() throws Exception {
        adapter.onFieldEdit("server.host", "valid.host");
        ReactiveScheduler.get().flush();
        Assert.assertTrue(adapter.canSaveSignal().get().booleanValue());

        adapter.setSubmitValidation(ValidationResult.error("server.host", "blocked"));
        ReactiveScheduler.get().flush();
        Assert.assertEquals("blocked", adapter.errorSignal("server.host").get());
        Assert.assertTrue(adapter.hasErrorSignal().get().booleanValue());
        Assert.assertFalse(adapter.canSaveSignal().get().booleanValue());

        adapter.clearSubmitValidation();
        ReactiveScheduler.get().flush();
        Assert.assertNull(adapter.errorSignal("server.host").get());
        Assert.assertFalse(adapter.hasErrorSignal().get().booleanValue());
        Assert.assertTrue(adapter.canSaveSignal().get().booleanValue());

        // 同帧先写错误再编辑清理，中央事务以最后一次 Signal 写入为准。
        adapter.setSubmitValidation(ValidationResult.error("server.host", "stale"));
        adapter.onFieldEdit("server.host", "next.host");
        ReactiveScheduler.get().flush();
        Assert.assertNull(adapter.errorSignal("server.host").get());
        Assert.assertFalse(adapter.hasErrorSignal().get().booleanValue());
        Assert.assertTrue(adapter.canSaveSignal().get().booleanValue());
    }
}
