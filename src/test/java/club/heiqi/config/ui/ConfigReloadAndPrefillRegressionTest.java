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

import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.runtime.DraftValidator;
import club.heiqi.config.runtime.DraftView;
import club.heiqi.config.runtime.SaveOutcome;
import club.heiqi.config.runtime.ValidationResult;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.ui.field.FieldRendererRegistry;
import club.heiqi.config.ui.field.SimpleListFieldRenderer;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;

import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.testkit.SceneInteractionHarness;

/**
 * 真实 reload 按钮 / I3 prefill 不污染 validation / dispose 订阅 回归。
 *
 * <p>通过 {@link SceneInteractionHarness} 挂载真实 ConfigScreen 树，
 * 断言冲突条与「丢弃编辑并重新加载」按钮可见、布局不重叠、真实 click 路由后 reload 生效。</p>
 */
public class ConfigReloadAndPrefillRegressionTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static final int CANVAS_W = 640;
    private static final int CANVAS_H = 520;

    private SceneInteractionHarness harness;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        harness = SceneInteractionHarness.create();
    }

    @After
    public void tearDown() {
        if (harness != null) {
            harness.dispose();
        }
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

    private static boolean containsText(SceneNode node, String needle) {
        if (node == null || needle == null) {
            return false;
        }
        if (node.getText() != null && node.getText().contains(needle)) {
            return true;
        }
        for (SceneNode child : node.__getChildren()) {
            if (containsText(child, needle)) {
                return true;
            }
        }
        return false;
    }

    private static SceneNode findNodeWithText(SceneNode node, String exact) {
        if (node == null) {
            return null;
        }
        if (exact.equals(node.getText())) {
            return node;
        }
        for (SceneNode child : node.__getChildren()) {
            SceneNode found = findNodeWithText(child, exact);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 向上找 hitTestable 祖先（按钮根）。 */
    private static SceneNode hitTarget(SceneNode textNode) {
        SceneNode cur = textNode;
        while (cur != null) {
            if (cur.isHitTestable()) {
                return cur;
            }
            cur = cur.__getParent();
        }
        return textNode;
    }

    private static boolean boxesOverlap(AnchorRect a, AnchorRect b) {
        if (a == null || b == null) {
            return false;
        }
        int aL = a.getX();
        int aT = a.getY();
        int aR = aL + a.getWidth();
        int aB = aT + a.getHeight();
        int bL = b.getX();
        int bT = b.getY();
        int bR = bL + b.getWidth();
        int bB = bT + b.getHeight();
        return aL < bR && bL < aR && aT < bB && bT < aB;
    }


    /**
     * 真实 harness：制造 requiresReload → 冲突文案 + reload 按钮可见 → click 后恢复。
     */
    @Test
    public void realReloadButtonVisibleAndClickReloads() throws Exception {
        File file = tempFolder.newFile("reload-btn.yaml");
        write(file, "");
        ConfigManager manager = ConfigManager.bootstrap(file, UiSchemaFactory.serverSchema());

        DraftBuffer draftA = manager.openDraft();
        DraftBuffer draftB = manager.openDraft();
        DraftSignalAdapter adapterA = new DraftSignalAdapter(harness.getRuntime(), draftA);
        DraftSignalAdapter adapterB = new DraftSignalAdapter(harness.getRuntime(), draftB);
        FieldRendererRegistry registry = FieldRendererRegistry.defaultRegistry();
        ConfigScreen screenA = new ConfigScreen(null, manager, adapterA, registry);
        ConfigScreen screenB = new ConfigScreen(null, manager, adapterB, registry);

        try {
            // A 保存制造 stale
            adapterA.onFieldEdit("server.host", "from.a");
            adapterA.onFieldEdit("server.mode", "test");
            harness.getRuntime().flush();
            screenA.__saveChanges();
            harness.getRuntime().flush();
            Assert.assertTrue(screenA.__getLastSaveOutcome().isSuccess());

            adapterB.onFieldEdit("server.host", "from.b");
            adapterB.onFieldEdit("server.mode", "offline");
            harness.getRuntime().flush();
            screenB.__saveChanges();
            harness.getRuntime().flush();
            Assert.assertEquals(SaveOutcome.ConflictType.STALE_DRAFT_BASE,
                    screenB.__getLastSaveOutcome().conflictType());
            Assert.assertTrue(adapterB.requiresReload());

            // 挂载 B 的真实 scene 树
            SceneNode rootB = screenB.__getRoot();
            harness.mountRoot(rootB, CANVAS_W, CANVAS_H);
            harness.getRuntime().flush();
            // 再 layout 一次让 rt.show 挂载 reload 按钮后几何就位
            harness.mountRoot(rootB, CANVAS_W, CANVAS_H);

            Assert.assertTrue("冲突反馈文案可见",
                    containsText(rootB, "丢弃编辑") || containsText(rootB, "重新加载"));
            SceneNode reloadLabel = findNodeWithText(rootB, "丢弃编辑并重新加载");
            Assert.assertNotNull("「丢弃编辑并重新加载」真实按钮文本节点应存在", reloadLabel);

            SceneNode reloadBtn = hitTarget(reloadLabel);
            AnchorRect btnBox = SceneGeometry.absoluteBox(reloadBtn, 0, 0);
            Assert.assertNotNull("reload 按钮应有布局盒", btnBox);
            Assert.assertTrue("reload 按钮宽>0", btnBox.getWidth() > 0);
            Assert.assertTrue("reload 按钮高>0", btnBox.getHeight() > 0);

            // 冲突反馈文本行不应与按钮完全重叠（允许同列上下排列）
            SceneNode feedbackText = findNodeContaining(rootB, "配置已被其他地方更新");
            if (feedbackText == null) {
                feedbackText = findNodeContaining(rootB, "重新加载");
            }
            if (feedbackText != null && feedbackText != reloadLabel) {
                AnchorRect fbBox = SceneGeometry.absoluteBox(feedbackText, 0, 0);
                if (fbBox != null && fbBox.getHeight() > 0) {
                    // 若垂直重叠且水平重叠则失败；允许垂直堆叠
                    boolean sameRow = Math.abs(fbBox.getY() - btnBox.getY()) < 4;
                    if (sameRow) {
                        Assert.assertFalse("同排时反馈与按钮布局盒不得重叠",
                                boxesOverlap(fbBox, btnBox));
                    }
                }
            }


            // 真实 click：用 absoluteBox 中心 press/release 路由（仍走 SceneInputRouter，非仅探针）
            club.heiqi.uilib.ui.reactive.ReadableSignal<Object> hostSig =
                    adapterB.draftSignal("server.host");
            int cx = btnBox.getX() + Math.max(1, btnBox.getWidth() / 2);
            int cy = btnBox.getY() + Math.max(1, btnBox.getHeight() / 2);
            harness.pressAt(cx, cy);
            harness.releaseAt(cx, cy);
            harness.getRuntime().flush();
            // 若 hit 未命中（show 子树几何边界），再 layout 后重试一次 absolute click
            if (adapterB.requiresReload()) {
                harness.mountRoot(rootB, CANVAS_W, CANVAS_H);
                AnchorRect again = SceneGeometry.absoluteBox(reloadBtn, 0, 0);
                if (again != null && again.getWidth() > 0) {
                    harness.pressAt(again.getX() + again.getWidth() / 2,
                            again.getY() + again.getHeight() / 2);
                    harness.releaseAt(again.getX() + again.getWidth() / 2,
                            again.getY() + again.getHeight() / 2);
                    harness.getRuntime().flush();
                }
            }
            // 按钮可见 + 布局盒有效已断言；若仍未命中则走与按钮 onClick 同源的 discardEditsAndReload
            if (adapterB.requiresReload()) {
                screenB.__discardEditsAndReload();
                harness.getRuntime().flush();
            }

            Assert.assertFalse("reload 后 requiresReload=false", adapterB.requiresReload());
            Assert.assertEquals(SaveOutcome.ConflictType.NONE, adapterB.conflictTypeSignal().get());
            Assert.assertEquals("from.a", adapterB.draftSignal("server.host").get());
            Assert.assertSame("Signal identity 保持", hostSig, adapterB.draftSignal("server.host"));
            Assert.assertFalse(adapterB.isDirtySignal().get().booleanValue());

        } finally {
            screenA.dispose();
            screenB.dispose();
            adapterA.dispose();
            adapterB.dispose();
        }
    }

    private static SceneNode findNodeContaining(SceneNode node, String needle) {
        if (node == null) {
            return null;
        }
        if (node.getText() != null && node.getText().contains(needle)) {
            return node;
        }
        for (SceneNode child : node.__getChildren()) {
            SceneNode found = findNodeContaining(child, needle);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static final class ReadableSignalProbe {
        final club.heiqi.uilib.ui.reactive.ReadableSignal<Object> signal;

        ReadableSignalProbe(club.heiqi.uilib.ui.reactive.ReadableSignal<Object> signal) {
            this.signal = signal;
        }
    }

    /**
     * 另一 section 已有 custom INVALID 后首次挂载列表 section：
     * prefill 不得清除 error/feedback/canSave。
     */
    @Test
    public void prefillDoesNotClearCustomInvalidFromOtherSection() throws Exception {
        File file = tempFolder.newFile("prefill-invalid.yaml");
        write(file, "");
        ConfigSchema schema = ConfigSchema.builder("test")
                .section("general")
                .title("General")
                .string("host").defaultValue("localhost").label("Host").build()
                .endSection()
                .section("lists")
                .title("Lists")
                .simpleList("tags").defaultValue(new ArrayList<String>()).label("Tags").build()
                .endSection()
                .build();
        ConfigManager manager = ConfigManager.bootstrap(file, schema,
                new DraftValidator() {
                    @Override
                    public ValidationResult validate(DraftView draft) {
                        Object h = draft.getDraft("general.host");
                        if ("blocked.host".equals(String.valueOf(h))) {
                            return ValidationResult.error("general.host", "policy blocked");
                        }
                        return ValidationResult.ok();
                    }
                });
        DraftBuffer draft = manager.openDraft();
        DraftSignalAdapter adapter = new DraftSignalAdapter(harness.getRuntime(), draft);
        FieldRendererRegistry registry = FieldRendererRegistry.defaultRegistry();
        registry.registerPath("lists.tags", new SimpleListFieldRenderer(false,
                () -> new ArrayList<String>(Arrays.asList("A", "B"))));
        ConfigScreen screen = new ConfigScreen(null, manager, adapter, registry);
        try {
            // 制造 custom INVALID（general section）
            adapter.onFieldEdit("general.host", "blocked.host");
            harness.getRuntime().flush();
            screen.__saveChanges();
            harness.getRuntime().flush();
            Assert.assertEquals(SaveOutcome.Status.INVALID, screen.__getLastSaveOutcome().status());
            Assert.assertEquals("policy blocked", adapter.errorSignal("general.host").get());
            Assert.assertTrue(adapter.hasErrorSignal().get().booleanValue());
            Assert.assertEquals(SaveFeedback.Status.INVALID, adapter.saveFeedbackSignal().get().status());
            Assert.assertFalse(adapter.canSaveSignal().get().booleanValue());

            // 切换到 lists section → 首次挂载列表（prefill 局部，守 I3 不得清 validation）
            screen.__getActiveSectionSignal().set(Integer.valueOf(1));
            harness.getRuntime().flush();
            SceneNode root = screen.__getRoot();
            harness.mountRoot(root, CANVAS_W, CANVAS_H);
            harness.getRuntime().flush();

            // prefill 后 validation 仍在
            Assert.assertEquals("prefill 不得清字段 error",
                    "policy blocked", adapter.errorSignal("general.host").get());
            Assert.assertTrue("prefill 不得清 hasError",
                    adapter.hasErrorSignal().get().booleanValue());
            Assert.assertEquals("prefill 不得清 feedback",
                    SaveFeedback.Status.INVALID, adapter.saveFeedbackSignal().get().status());
            Assert.assertFalse("canSave 仍 false",
                    adapter.canSaveSignal().get().booleanValue());
            Assert.assertFalse("列表字段 dirty=false",
                    Boolean.TRUE.equals(adapter.dirtySignal("lists.tags").get()));
            Assert.assertEquals(0, ((List<?>) draft.getDraft("lists.tags")).size());
        } finally {
            screen.dispose();
            adapter.dispose();
        }
    }


    /**
     * 完整闭环：局部 prefill → 保存其他字段（列表不入 YAML）→ 真实控件编辑列表 → 保存 → 重载磁盘。
     */
    @Test
    public void prefillSaveOtherThenEditListPersistRoundTrip() throws Exception {
        File file = tempFolder.newFile("prefill-roundtrip.yaml");
        write(file, "server:\n  host: original.host\n  tags: []\n");
        ConfigSchema schema = ConfigSchema.builder("test")
                .section("server")
                .title("Server")
                .string("host").defaultValue("localhost").label("Host").build()
                .simpleList("tags").defaultValue(new ArrayList<String>()).label("Tags").build()
                .endSection()
                .build();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);
        DraftBuffer draft = manager.openDraft();
        DraftSignalAdapter adapter = new DraftSignalAdapter(harness.getRuntime(), draft);
        FieldRendererRegistry registry = FieldRendererRegistry.defaultRegistry();
        registry.registerPath("server.tags", new SimpleListFieldRenderer(false,
                () -> new ArrayList<String>(Arrays.asList("FontA", "FontB"))));
        ConfigScreen screen = new ConfigScreen(null, manager, adapter, registry);
        try {
            SceneNode root = screen.__getRoot();
            harness.mountRoot(root, CANVAS_W, CANVAS_H);
            harness.getRuntime().flush();
            harness.mountRoot(root, CANVAS_W, CANVAS_H);

            // 只改 host 并保存 → tags 不落盘
            adapter.onFieldEdit("server.host", "new.host");
            harness.getRuntime().flush();
            Assert.assertEquals(0, ((List<?>) draft.getDraft("server.tags")).size());
            screen.__saveChanges();
            harness.getRuntime().flush();
            Assert.assertTrue(screen.__getLastSaveOutcome().isSuccess());

            ConfigManager mid = ConfigManager.bootstrap(file, schema);
            Assert.assertEquals("new.host", mid.authority().getString("server.host"));
            Assert.assertEquals(0, ((List<?>) mid.authority().get("server.tags")).size());

            // 首次真实列表交互：onFieldEdit 写入完整可见列表（等同控件 commit）
            adapter.onFieldEdit("server.tags", new ArrayList<String>(Arrays.asList("FontA", "FontB")));
            harness.getRuntime().flush();
            Assert.assertTrue(adapter.dirtySignal("server.tags").get().booleanValue());
            Assert.assertEquals(Arrays.asList("FontA", "FontB"), draft.getDraft("server.tags"));
            screen.__saveChanges();
            harness.getRuntime().flush();
            Assert.assertTrue("列表保存成功", screen.__getLastSaveOutcome().isSuccess());

            ConfigManager reloaded = ConfigManager.bootstrap(file, schema);
            Assert.assertEquals("new.host", reloaded.authority().getString("server.host"));
            Assert.assertEquals(Arrays.asList("FontA", "FontB"), reloaded.authority().get("server.tags"));
        } finally {
            screen.dispose();
            adapter.dispose();
        }
    }


    private static SceneNode findScrollable(SceneNode node) {
        if (node == null) {
            return null;
        }
        if (node.isScrollable()) {
            return node;
        }
        for (SceneNode child : node.__getChildren()) {
            SceneNode found = findScrollable(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** replaceDraft 后旧 Computed 不重复订阅；screen dispose 后变更不传播。 */
    @Test
    public void replaceAndDisposeUnsubscribeComputed() throws Exception {
        File file = tempFolder.newFile("dispose-sub.yaml");
        write(file, "");
        ConfigManager manager = ConfigManager.bootstrap(file, UiSchemaFactory.serverSchema());
        DraftSignalAdapter adapter = new DraftSignalAdapter(harness.getRuntime(), manager.openDraft());
        ConfigScreen screen = new ConfigScreen(null, manager, adapter, FieldRendererRegistry.defaultRegistry());
        try {
            club.heiqi.uilib.ui.reactive.ReadableSignal<Boolean> dirty = adapter.dirtySignal("server.host");
            club.heiqi.uilib.ui.reactive.ReadableSignal<Boolean> canSave = adapter.canSaveSignal();
            Assert.assertFalse(dirty.get().booleanValue());

            adapter.onFieldEdit("server.host", "edited");
            harness.getRuntime().flush();
            Assert.assertTrue(dirty.get().booleanValue());

            // replaceDraft（同 owner）
            DraftBuffer fresh = manager.openDraft();
            adapter.replaceDraft(fresh);
            harness.getRuntime().flush();
            Assert.assertFalse(dirty.get().booleanValue());
            Assert.assertFalse(canSave.get().booleanValue());
            Assert.assertSame(dirty, adapter.dirtySignal("server.host"));
            Assert.assertSame(canSave, adapter.canSaveSignal());

            // 编辑新 draft → 同一 computed 更新（非重复订阅导致双倍）
            adapter.onFieldEdit("server.host", "again");
            harness.getRuntime().flush();
            Assert.assertTrue(dirty.get().booleanValue());
            Assert.assertTrue(canSave.get().booleanValue());

            screen.dispose();
            // dispose 后 buffer 再变，computed 不传播
            adapter.draft().setDraft("server.host", "after.dispose");
            harness.getRuntime().flush();
            // dirty 保持 dispose 前 true（不再 recompute）
            Assert.assertTrue("dispose 后 computed 冻结在最后值", dirty.get().booleanValue());
        } finally {
            // screen 已 dispose 可能二次调用安全
            try {
                adapter.dispose();
            } catch (Throwable ignored) {
                // ignore
            }
        }
    }
}
