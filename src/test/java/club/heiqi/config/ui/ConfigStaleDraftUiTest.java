package club.heiqi.config.ui;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import club.heiqi.config.runtime.Authority;
import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.runtime.SaveOutcome;
import club.heiqi.config.runtime.ValidationResult;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.field.FieldRendererRegistry;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;

/**
 * stale draft 恢复 / replaceDraft / presentation seed / 冲突态 UI 回归。
 *
 * <p>headless（input=null），断言读 conflictType / Signal identity，禁止依赖英文诊断串或 GL。</p>
 */
public class ConfigStaleDraftUiTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    private static void write(File file, String content) throws Exception {
        FileWriter w = new FileWriter(file);
        try {
            w.write(content);
        } finally {
            w.close();
        }
    }

    private static ConfigSchema serverSchema() {
        return UiSchemaFactory.serverSchema();
    }

    private static void flush() {
        ReactiveScheduler.get().flush();
    }

    /** 两个真实 ConfigScreen：A 保存后 B stale；B 编辑保留；重复 save 仍冲突；reload 后 identity 稳定。 */
    @Test
    public void twoScreensStaleThenReloadKeepsSignalIdentity() throws Exception {
        File file = tempFolder.newFile("two-screens.yaml");
        write(file, "");
        ConfigManager manager = ConfigManager.bootstrap(file, serverSchema());

        DraftBuffer draftA = manager.openDraft();
        DraftBuffer draftB = manager.openDraft();
        DraftSignalAdapter adapterA = new DraftSignalAdapter(null, draftA);
        DraftSignalAdapter adapterB = new DraftSignalAdapter(null, draftB);
        FieldRendererRegistry registry = FieldRendererRegistry.defaultRegistry();
        ConfigScreen screenA = new ConfigScreen(null, manager, adapterA, registry);
        ConfigScreen screenB = new ConfigScreen(null, manager, adapterB, registry);
        flush();

        // 记录 B 的 Signal identity
        ReadableSignal<Object> hostSigB = adapterB.draftSignal("server.host");
        ReadableSignal<Boolean> dirtySigB = adapterB.dirtySignal("server.host");
        ReadableSignal<Boolean> canSaveB = adapterB.canSaveSignal();
        Assert.assertNotNull(hostSigB);
        Assert.assertNotNull(dirtySigB);

        // A 编辑并保存
        adapterA.onFieldEdit("server.host", "from.a");
        adapterA.onFieldEdit("server.mode", "test");
        flush();
        screenA.__saveChanges();
        flush();
        Assert.assertTrue(screenA.__getLastSaveOutcome().isSuccess());
        Assert.assertEquals("from.a", manager.authority().getString("server.host"));

        // B 编辑并尝试保存 → STALE
        adapterB.onFieldEdit("server.host", "from.b");
        adapterB.onFieldEdit("server.mode", "offline");
        flush();
        Assert.assertEquals("from.b", adapterB.draftSignal("server.host").get());
        screenB.__saveChanges();
        flush();

        SaveOutcome staleOutcome = screenB.__getLastSaveOutcome();
        Assert.assertEquals(SaveOutcome.Status.INVALID, staleOutcome.status());
        Assert.assertEquals(SaveOutcome.ConflictType.STALE_DRAFT_BASE, staleOutcome.conflictType());
        Assert.assertTrue(staleOutcome.requiresReload());
        Assert.assertTrue(adapterB.requiresReload());
        Assert.assertFalse(adapterB.canSaveSignal().get().booleanValue());
        // 冲突不注入 errorCount
        Assert.assertEquals(Integer.valueOf(0), adapterB.errorCountSignal().get());
        // 编辑保留
        Assert.assertEquals("from.b", adapterB.draftSignal("server.host").get());
        Assert.assertEquals("from.b", adapterB.draft().getDraft("server.host"));
        // 友好中文反馈
        SaveFeedback fb = adapterB.saveFeedbackSignal().get();
        Assert.assertEquals(SaveFeedback.Status.CONFLICT, fb.status());
        Assert.assertTrue(fb.message().contains("丢弃编辑") || fb.message().contains("重新加载"));

        // 普通编辑不能清 requiresReload 冲突
        adapterB.onFieldEdit("server.host", "from.b.edited");
        flush();
        Assert.assertTrue(adapterB.requiresReload());
        Assert.assertFalse(adapterB.canSaveSignal().get().booleanValue());
        Assert.assertEquals(SaveOutcome.ConflictType.STALE_DRAFT_BASE,
                adapterB.conflictTypeSignal().get());

        // 重复 save 仍冲突
        screenB.__saveChanges();
        flush();
        Assert.assertEquals(SaveOutcome.ConflictType.STALE_DRAFT_BASE,
                screenB.__getLastSaveOutcome().conflictType());

        // reload：Signal identity 稳定，值=Authority，dirty/error/conflict 清零
        screenB.__discardEditsAndReload();
        flush();

        Assert.assertSame("draftSignal identity 保持", hostSigB, adapterB.draftSignal("server.host"));
        Assert.assertSame("dirtySignal identity 保持", dirtySigB, adapterB.dirtySignal("server.host"));
        Assert.assertSame("canSaveSignal identity 保持", canSaveB, adapterB.canSaveSignal());
        Assert.assertEquals("from.a", adapterB.draftSignal("server.host").get());
        Assert.assertEquals("from.a", adapterB.draft().getDraft("server.host"));
        Assert.assertEquals("from.a", adapterB.draft().getCurrent("server.host"));
        Assert.assertFalse(adapterB.isDirtySignal().get().booleanValue());
        Assert.assertFalse(adapterB.hasErrorSignal().get().booleanValue());
        Assert.assertEquals(Integer.valueOf(0), adapterB.errorCountSignal().get());
        Assert.assertEquals(SaveOutcome.ConflictType.NONE, adapterB.conflictTypeSignal().get());
        Assert.assertFalse(adapterB.requiresReload());
        Assert.assertEquals(SaveFeedback.Status.NONE, adapterB.saveFeedbackSignal().get().status());

        // 再编辑 save 成功
        adapterB.onFieldEdit("server.host", "from.b.after.reload");
        adapterB.onFieldEdit("server.mode", "test");
        flush();
        Assert.assertTrue(adapterB.canSaveSignal().get().booleanValue());
        screenB.__saveChanges();
        flush();
        Assert.assertTrue(screenB.__getLastSaveOutcome().isSuccess());
        Assert.assertEquals("from.b.after.reload", manager.authority().getString("server.host"));

        screenA.dispose();
        screenB.dispose();
        adapterA.dispose();
        adapterB.dispose();
    }

    /** ConfigScreen 层连续两次编辑保存。 */
    @Test
    public void configScreenTwoEditsAndSaves() throws Exception {
        File file = tempFolder.newFile("two-edits.yaml");
        write(file, "");
        ConfigManager manager = ConfigManager.bootstrap(file, serverSchema());
        DraftSignalAdapter adapter = new DraftSignalAdapter(null, manager.openDraft());
        ConfigScreen screen = new ConfigScreen(null, manager, adapter, FieldRendererRegistry.defaultRegistry());
        flush();

        adapter.onFieldEdit("server.host", "first.host");
        adapter.onFieldEdit("server.mode", "test");
        flush();
        screen.__saveChanges();
        flush();
        Assert.assertTrue(screen.__getLastSaveOutcome().isSuccess());
        Assert.assertEquals("first.host", manager.authority().getString("server.host"));
        Assert.assertFalse(adapter.isDirtySignal().get().booleanValue());

        adapter.onFieldEdit("server.host", "second.host");
        flush();
        screen.__saveChanges();
        flush();
        Assert.assertTrue(screen.__getLastSaveOutcome().isSuccess());
        Assert.assertEquals("second.host", manager.authority().getString("server.host"));

        screen.dispose();
        adapter.dispose();
    }

    /** replaceDraft schema 不兼容拒绝且旧状态不变。 */
    @Test
    public void replaceDraftRejectsIncompatibleSchema() throws Exception {
        File fileA = tempFolder.newFile("replace-a.yaml");
        File fileB = tempFolder.newFile("replace-b.yaml");
        write(fileA, "");
        write(fileB, "");
        ConfigSchema schemaA = serverSchema();
        ConfigSchema schemaB = ConfigSchema.builder("other")
                .section("server")
                .title("Server")
                .string("host").defaultValue("x").label("Host").build()
                .endSection()
                .build();
        ConfigManager managerA = ConfigManager.bootstrap(fileA, schemaA);
        ConfigManager managerB = ConfigManager.bootstrap(fileB, schemaB);
        DraftBuffer draftA = managerA.openDraft();
        DraftBuffer draftB = managerB.openDraft();
        DraftSignalAdapter adapter = new DraftSignalAdapter(null, draftA);
        flush();

        adapter.onFieldEdit("server.host", "keep.me");
        flush();
        ReadableSignal<Object> hostSig = adapter.draftSignal("server.host");
        DraftBuffer before = adapter.draft();

        try {
            adapter.replaceDraft(draftB);
            Assert.fail("应拒绝不兼容 schema / 不同 owner");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("replaceDraft rejected"));
        }
        flush();

        Assert.assertSame(before, adapter.draft());
        Assert.assertSame(hostSig, adapter.draftSignal("server.host"));
        Assert.assertEquals("keep.me", adapter.draftSignal("server.host").get());
        Assert.assertTrue(adapter.isDirtySignal().get().booleanValue());

        adapter.dispose();
    }

    /** replaceDraft 拒绝不同 manager 的同形 schema draft（owner mismatch）。 */
    @Test
    public void replaceDraftRejectsForeignOwnerSameShapeSchema() throws Exception {
        File fileA = tempFolder.newFile("owner-a.yaml");
        File fileB = tempFolder.newFile("owner-b.yaml");
        write(fileA, "");
        write(fileB, "");
        ConfigSchema schema = serverSchema();
        ConfigManager managerA = ConfigManager.bootstrap(fileA, schema);
        ConfigManager managerB = ConfigManager.bootstrap(fileB, schema);
        DraftBuffer draftA = managerA.openDraft();
        DraftBuffer draftB = managerB.openDraft();
        DraftSignalAdapter adapter = new DraftSignalAdapter(null, draftA);
        flush();
        adapter.onFieldEdit("server.host", "keep.owner");
        flush();
        DraftBuffer before = adapter.draft();
        try {
            adapter.replaceDraft(draftB);
            Assert.fail("应拒绝不同 manager 的 draft");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("owner mismatch")
                    || expected.getMessage().contains("replaceDraft rejected"));
        }
        Assert.assertSame(before, adapter.draft());
        Assert.assertEquals("keep.owner", adapter.draftSignal("server.host").get());
        adapter.dispose();
    }

    /** dispose 后 Computed 不再随 revision 传播（断言值冻结，而非仅不抛）。 */
    @Test
    public void disposeStopsComputedPropagation() throws Exception {
        File file = tempFolder.newFile("dispose-prop.yaml");
        write(file, "");
        ConfigManager manager = ConfigManager.bootstrap(file, serverSchema());
        DraftBuffer draft = manager.openDraft();
        DraftSignalAdapter adapter = new DraftSignalAdapter(null, draft);
        flush();
        ReadableSignal<Boolean> dirty = adapter.dirtySignal("server.host");
        ReadableSignal<Boolean> isDirty = adapter.isDirtySignal();
        Assert.assertFalse(dirty.get().booleanValue());
        Assert.assertFalse(isDirty.get().booleanValue());

        adapter.dispose();
        // dispose 后编辑 draft 并尝试通过 adapter 路径 bump：onFieldEdit 仍可能写 buffer，
        // 但 disposed Computed 不得再传播新值
        draft.setDraft("server.host", "after.dispose");
        // 直接读：disposed computed 保持 dispose 前的缓存值
        Assert.assertFalse("dispose 后 dirty computed 不因 buffer 变更而更新",
                dirty.get().booleanValue());
        Assert.assertFalse("dispose 后 isDirty computed 不传播",
                isDirty.get().booleanValue());
        flush();
        Assert.assertFalse(dirty.get().booleanValue());
        Assert.assertFalse(isDirty.get().booleanValue());

        // 新建 adapter 正常
        DraftSignalAdapter adapter2 = new DraftSignalAdapter(null, manager.openDraft());
        flush();
        adapter2.onFieldEdit("server.host", "x");
        flush();
        Assert.assertTrue(adapter2.isDirtySignal().get().booleanValue());
        club.heiqi.uilib.ui.reactive.ReadableSignal<Boolean> dirty2 = adapter2.isDirtySignal();
        adapter2.dispose();
        // dispose 后冻结在 dispose 前的 true，不再因 buffer 变化而重算为 false
        adapter2.draft().setDraft("server.host", "y");
        flush();
        Assert.assertTrue("dispose 后 computed 冻结在最后值（true），不因 buffer 再变而重算",
                dirty2.get().booleanValue());
    }


    /** presentation seed API：保存其他字段不落列表；首次列表交互后 save 成功。 */
    @Test
    public void presentationSeedDoesNotPersistUntilFirstEdit() throws Exception {
        File file = tempFolder.newFile("prefill-list.yaml");
        write(file, "server:\n  tags: []\n  host: original.host\n");
        ConfigSchema listSchema = ConfigSchema.builder("test")
                .section("server")
                .title("Server")
                .simpleList("tags").defaultValue(new ArrayList<String>()).label("Tags").build()
                .string("host").defaultValue("localhost").label("Host").build()
                .endSection()
                .build();
        ConfigManager manager = ConfigManager.bootstrap(file, listSchema);
        DraftBuffer draft = manager.openDraft();
        DraftSignalAdapter adapter = new DraftSignalAdapter(null, draft);
        flush();

        // presentation seed（非 render 路径 API）
        List<String> discovered = Arrays.asList("FontA", "FontB");
        adapter.seedPresentation("server.tags", new ArrayList<String>(discovered));
        flush();
        Assert.assertEquals(discovered, adapter.draftSignal("server.tags").get());
        Assert.assertEquals(0, ((List<?>) draft.getDraft("server.tags")).size());
        Assert.assertFalse(adapter.dirtySignal("server.tags").get().booleanValue());
        Assert.assertFalse(adapter.isDirtySignal().get().booleanValue());
        Assert.assertTrue(adapter.hasPresentationSeed("server.tags"));

        // 只改 host 并保存 → tags 不落盘
        adapter.onFieldEdit("server.host", "new.host");
        flush();
        Assert.assertTrue(adapter.canSaveSignal().get().booleanValue());
        SaveOutcome hostOnly = manager.save(adapter.draft());
        Assert.assertTrue(hostOnly.isSuccess());
        adapter.afterSaveSync();
        flush();

        ConfigManager reloaded = ConfigManager.bootstrap(file, listSchema);
        Assert.assertEquals("new.host", reloaded.authority().getString("server.host"));
        Assert.assertEquals(0, ((List<?>) reloaded.authority().get("server.tags")).size());

        // 新 draft + seed 后再首次编辑列表 → 写入并保存成功
        DraftBuffer draft2 = reloaded.openDraft();
        DraftSignalAdapter adapter2 = new DraftSignalAdapter(null, draft2);
        flush();
        adapter2.seedPresentation("server.tags", new ArrayList<String>(discovered));
        flush();
        adapter2.onFieldEdit("server.tags", new ArrayList<String>(discovered));
        flush();
        Assert.assertFalse(adapter2.hasPresentationSeed("server.tags"));
        Assert.assertTrue(adapter2.dirtySignal("server.tags").get().booleanValue());
        Assert.assertEquals(discovered, draft2.getDraft("server.tags"));
        SaveOutcome listSave = reloaded.save(draft2);
        Assert.assertTrue(listSave.isSuccess());
        ConfigManager reloaded2 = ConfigManager.bootstrap(file, listSchema);
        Assert.assertEquals(discovered, reloaded2.authority().get("server.tags"));

        adapter.dispose();
        adapter2.dispose();
    }

    /** DRAFT_MODIFIED 冲突：保留草稿、可重试、不要求 reload。 */
    @Test
    public void draftModifiedConflictRetainsDraftWithoutReload() throws Exception {
        File file = tempFolder.newFile("draft-mod-ui.yaml");
        write(file, "server:\n  host: original.host\n  port: 8080\n  debug: false\n  mode: online\n");
        final DraftBuffer[] holder = new DraftBuffer[1];
        final java.util.concurrent.atomic.AtomicInteger mutateOnce =
                new java.util.concurrent.atomic.AtomicInteger(0);
        ConfigManager manager = ConfigManager.bootstrap(file, serverSchema(),
                view -> {
                    // 仅首次 validate 时改 draft，模拟并发编辑；第二次允许提交
                    if (mutateOnce.getAndIncrement() == 0) {
                        holder[0].setDraft("server.host", "mutated");
                    }
                    return ValidationResult.ok();
                });
        DraftBuffer draft = manager.openDraft();
        holder[0] = draft;
        DraftSignalAdapter adapter = new DraftSignalAdapter(null, draft);
        ConfigScreen screen = new ConfigScreen(null, manager, adapter, FieldRendererRegistry.defaultRegistry());
        flush();

        adapter.onFieldEdit("server.host", "user.host");
        adapter.onFieldEdit("server.port", 3000.0);
        adapter.onFieldEdit("server.mode", "test");
        flush();
        screen.__saveChanges();
        flush();

        SaveOutcome outcome = screen.__getLastSaveOutcome();
        Assert.assertEquals(SaveOutcome.ConflictType.DRAFT_MODIFIED_DURING_SAVE, outcome.conflictType());
        Assert.assertFalse(outcome.requiresReload());
        Assert.assertFalse(adapter.requiresReload());
        Assert.assertEquals("mutated", adapter.draft().getDraft("server.host"));
        Assert.assertEquals(SaveFeedback.Status.CONFLICT, adapter.saveFeedbackSignal().get().status());
        Assert.assertTrue(adapter.saveFeedbackSignal().get().message().contains("重试")
                || adapter.saveFeedbackSignal().get().message().contains("修改"));

        // 保留草稿后直接重试：validator 不再改 draft → 应成功
        SaveOutcome retry = manager.save(adapter.draft());
        Assert.assertTrue("可重试冲突后再次 save 应成功: " + retry.conflictType()
                        + " " + (retry.validation() == null ? "" : retry.validation().errors()),
                retry.isSuccess());
        Assert.assertEquals("mutated", manager.authority().getString("server.host"));

        screen.dispose();
        adapter.dispose();
    }

    /** applySaveFailure 冲突不进 errorCount；普通 INVALID 进 errorCount。 */
    @Test
    public void conflictDoesNotInflateErrorCount() throws Exception {
        File file = tempFolder.newFile("err-count.yaml");
        write(file, "");
        ConfigManager manager = ConfigManager.bootstrap(file, serverSchema());
        DraftBuffer a = manager.openDraft();
        DraftBuffer b = manager.openDraft();
        a.setDraft("server.host", "winner");
        a.setDraft("server.mode", "test");
        assertTrue(manager.save(a).isSuccess());

        DraftSignalAdapter adapter = new DraftSignalAdapter(null, b);
        flush();
        adapter.onFieldEdit("server.host", "loser");
        adapter.onFieldEdit("server.mode", "offline");
        flush();
        SaveOutcome outcome = manager.save(b);
        adapter.applySaveFailure(outcome);
        flush();

        Assert.assertEquals(SaveOutcome.ConflictType.STALE_DRAFT_BASE, outcome.conflictType());
        Assert.assertEquals(Integer.valueOf(0), adapter.errorCountSignal().get());
        Assert.assertFalse(adapter.hasErrorSignal().get().booleanValue());
        Assert.assertFalse(adapter.canSaveSignal().get().booleanValue());

        adapter.dispose();
    }

    private static void assertTrue(boolean v) {
        Assert.assertTrue(v);
    }
}
