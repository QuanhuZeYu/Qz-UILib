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
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneInputFrame;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 真实 reload 按钮 / I3 prefill 不污染 validation / dispose 订阅 回归。
 *
 * <p>输入必须走 ConfigScreen 自身 {@link SceneRuntime#route}（包级 {@code __getRuntime}），
 * 禁止探针 fallback；真实 click 不生效直接失败。</p>
 */
public class ConfigReloadAndPrefillRegressionTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static final int CANVAS_W = 640;
    private static final int CANVAS_H = 520;

    private SceneLayoutEngine layoutEngine;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        layoutEngine = new SceneLayoutEngine(new FixedTextMeasurer(8, 16));
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

    private void layout(SceneNode root) {
        layoutEngine.layout(root, new Constraints(CANVAS_W, CANVAS_H));
    }

    /** 经 screen 自身 runtime 路由指针事件（非 harness 旁路 runtime）。 */
    private static void routeClick(ConfigScreen screen, int x, int y) {
        SceneRuntime rt = screen.__getRuntime();
        SceneNode root = screen.__getRoot();
        InputFrameBuilder down = new InputFrameBuilder(x, y);
        down.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, x, y,
                SceneMouseButton.LEFT, 0, 0, 0, false, false, false, false, 1000L));
        SceneInputFrame fDown = down.drainFrame();
        rt.route(root, fDown, 0, 0);
        rt.flush();
        InputFrameBuilder up = new InputFrameBuilder(x, y);
        up.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, x, y,
                SceneMouseButton.LEFT, 0, 0, 0, false, false, false, false, 1001L));
        SceneInputFrame fUp = up.drainFrame();
        rt.route(root, fUp, 0, 0);
        rt.flush();
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
     * 真实：requiresReload → 冲突文案 + reload 按钮 → 经 ConfigScreen 自身 runtime 点击生效。
     * 禁止 __discardEditsAndReload 探针 fallback。
     */
    @Test
    public void realReloadButtonVisibleAndClickReloads() throws Exception {
        File file = tempFolder.newFile("reload-btn.yaml");
        write(file, "");
        ConfigManager manager = ConfigManager.bootstrap(file, UiSchemaFactory.serverSchema());

        DraftBuffer draftA = manager.openDraft();
        DraftBuffer draftB = manager.openDraft();
        // adapter 与 screen 同线程；runtime 由 screen 自建，adapter 可传 null
        DraftSignalAdapter adapterA = new DraftSignalAdapter(null, draftA);
        DraftSignalAdapter adapterB = new DraftSignalAdapter(null, draftB);
        FieldRendererRegistry registry = FieldRendererRegistry.defaultRegistry();
        ConfigScreen screenA = new ConfigScreen(null, manager, adapterA, registry);
        ConfigScreen screenB = new ConfigScreen(null, manager, adapterB, registry);

        try {
            adapterA.onFieldEdit("server.host", "from.a");
            adapterA.onFieldEdit("server.mode", "test");
            screenA.__getRuntime().flush();
            screenA.__saveChanges();
            screenA.__getRuntime().flush();
            Assert.assertTrue(screenA.__getLastSaveOutcome().isSuccess());

            adapterB.onFieldEdit("server.host", "from.b");
            adapterB.onFieldEdit("server.mode", "offline");
            screenB.__getRuntime().flush();
            screenB.__saveChanges();
            screenB.__getRuntime().flush();
            Assert.assertEquals(SaveOutcome.ConflictType.STALE_DRAFT_BASE,
                    screenB.__getLastSaveOutcome().conflictType());
            Assert.assertTrue(adapterB.requiresReload());

            SceneNode rootB = screenB.__getRoot();
            // 两遍 layout：rt.show 挂载 feedback/reload 后几何就位
            layout(rootB);
            screenB.__getRuntime().flush();
            layout(rootB);
            screenB.__getRuntime().flush();

            Assert.assertTrue("冲突反馈文案可见",
                    containsText(rootB, "丢弃编辑") || containsText(rootB, "重新加载")
                            || containsText(rootB, "配置已被其他地方更新"));
            SceneNode reloadLabel = findNodeWithText(rootB, "丢弃编辑并重新加载");
            Assert.assertNotNull("「丢弃编辑并重新加载」真实按钮文本节点应存在", reloadLabel);

            SceneNode reloadBtn = hitTarget(reloadLabel);
            AnchorRect btnBox = SceneGeometry.absoluteBox(reloadBtn, 0, 0);
            Assert.assertNotNull("reload 按钮应有布局盒", btnBox);
            Assert.assertTrue("reload 按钮宽>0", btnBox.getWidth() > 0);
            Assert.assertTrue("reload 按钮高>0", btnBox.getHeight() > 0);

            SceneNode feedbackText = findNodeContaining(rootB, "配置已被其他地方更新");
            if (feedbackText == null) {
                feedbackText = findNodeContaining(rootB, "重新加载");
            }
            if (feedbackText != null && feedbackText != reloadLabel) {
                AnchorRect fbBox = SceneGeometry.absoluteBox(feedbackText, 0, 0);
                if (fbBox != null && fbBox.getHeight() > 0) {
                    boolean sameRow = Math.abs(fbBox.getY() - btnBox.getY()) < 4;
                    if (sameRow) {
                        Assert.assertFalse("同排时反馈与按钮布局盒不得重叠",
                                boxesOverlap(fbBox, btnBox));
                    }
                }
            }

            club.heiqi.uilib.ui.reactive.ReadableSignal<Object> hostSig =
                    adapterB.draftSignal("server.host");
            int cx = btnBox.getX() + Math.max(1, btnBox.getWidth() / 2);
            int cy = btnBox.getY() + Math.max(1, btnBox.getHeight() / 2);

            // 必须走 screen 自身 runtime.route；禁止探针 fallback
            routeClick(screenB, cx, cy);

            if (adapterB.requiresReload()) {
                layout(rootB);
                screenB.__getRuntime().flush();
                reloadLabel = findNodeWithText(rootB, "丢弃编辑并重新加载");
                Assert.assertNotNull("重 layout 后 reload 按钮仍应存在", reloadLabel);
                reloadBtn = hitTarget(reloadLabel);
                AnchorRect again = SceneGeometry.absoluteBox(reloadBtn, 0, 0);
                Assert.assertNotNull(again);
                Assert.assertTrue(again.getWidth() > 0);
                routeClick(screenB,
                        again.getX() + Math.max(1, again.getWidth() / 2),
                        again.getY() + Math.max(1, again.getHeight() / 2));
            }

            Assert.assertFalse(
                    "真实 click 后 requiresReload 必须为 false（禁止 __discardEditsAndReload fallback）",
                    adapterB.requiresReload());
            Assert.assertEquals(SaveOutcome.ConflictType.NONE, adapterB.conflictTypeSignal().get());
            Assert.assertEquals("from.a", adapterB.draftSignal("server.host").get());
            Assert.assertSame("Signal identity 保持", hostSig, adapterB.draftSignal("server.host"));
            Assert.assertFalse(adapterB.isDirtySignal().get().booleanValue());
        } finally {
            screenA.dispose();
            screenB.dispose();
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
        DraftSignalAdapter adapter = new DraftSignalAdapter(null, draft);
        FieldRendererRegistry registry = FieldRendererRegistry.defaultRegistry();
        registry.registerPath("lists.tags", new SimpleListFieldRenderer(false,
                () -> new ArrayList<String>(Arrays.asList("A", "B"))));
        ConfigScreen screen = new ConfigScreen(null, manager, adapter, registry);
        try {
            adapter.onFieldEdit("general.host", "blocked.host");
            screen.__getRuntime().flush();
            screen.__saveChanges();
            screen.__getRuntime().flush();
            Assert.assertEquals(SaveOutcome.Status.INVALID, screen.__getLastSaveOutcome().status());
            Assert.assertEquals("policy blocked", adapter.errorSignal("general.host").get());
            Assert.assertTrue(adapter.hasErrorSignal().get().booleanValue());
            Assert.assertEquals(SaveFeedback.Status.INVALID, adapter.saveFeedbackSignal().get().status());
            Assert.assertFalse(adapter.canSaveSignal().get().booleanValue());

            screen.__getActiveSectionSignal().set(Integer.valueOf(1));
            screen.__getRuntime().__finishMotionForTest();
            layout(screen.__getRoot());
            screen.__getRuntime().flush();

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
        }
    }

    /**
     * 完整闭环：局部 prefill → 保存其他字段（列表不入 YAML）→ 真实列表删除写 draft → 保存 → 重载磁盘。
     * 禁止 adapter.onFieldEdit 代替首次列表交互。
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
        DraftSignalAdapter adapter = new DraftSignalAdapter(null, draft);
        FieldRendererRegistry registry = FieldRendererRegistry.defaultRegistry();
        registry.registerPath("server.tags", new SimpleListFieldRenderer(false,
                () -> new ArrayList<String>(Arrays.asList("FontA", "FontB"))));
        ConfigScreen screen = new ConfigScreen(null, manager, adapter, registry);
        try {
            SceneNode root = screen.__getRoot();
            layout(root);
            screen.__getRuntime().flush();
            layout(root);
            screen.__getRuntime().flush();

            // 只改 host 并保存 → tags 不落盘
            adapter.onFieldEdit("server.host", "new.host");
            screen.__getRuntime().flush();
            Assert.assertEquals(0, ((List<?>) draft.getDraft("server.tags")).size());
            screen.__saveChanges();
            screen.__getRuntime().flush();
            Assert.assertTrue(screen.__getLastSaveOutcome().isSuccess());

            ConfigManager mid = ConfigManager.bootstrap(file, schema);
            Assert.assertEquals("new.host", mid.authority().getString("server.host"));
            Assert.assertEquals(0, ((List<?>) mid.authority().get("server.tags")).size());

            // 重新 layout 确保列表可见（prefill 局部 FontA/FontB）
            layout(root);
            screen.__getRuntime().__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
            screen.__getRuntime().flush();
            screen.__getRuntime().__finishMotionForTest();

            // 真实列表交互：点击「×」删除按钮，触发 onItemsChanged → onFieldEdit
            // 删除一项后列表应变为 ["FontB"] 或 ["FontA"] 写入 draft
            SceneNode deleteMark = findNodeWithText(root, "×");
            if (deleteMark == null) {
                // 列表可能在滚动视口内；扩大 canvas 再 layout
                layoutEngine.layout(root, new Constraints(800, 800));
                screen.__getRuntime().flush();
                deleteMark = findNodeWithText(root, "×");
            }
            Assert.assertNotNull("应存在列表删除按钮「×」（真实控件）", deleteMark);
            SceneNode delBtn = hitTarget(deleteMark);
            AnchorRect delBox = SceneGeometry.absoluteBox(delBtn, 0, 0);
            Assert.assertNotNull(delBox);
            Assert.assertTrue(delBox.getWidth() > 0);
            routeClick(screen,
                    delBox.getX() + Math.max(1, delBox.getWidth() / 2),
                    delBox.getY() + Math.max(1, delBox.getHeight() / 2));

            Assert.assertTrue("真实删除后 tags 应 dirty",
                    Boolean.TRUE.equals(adapter.dirtySignal("server.tags").get()));
            List<?> tags = (List<?>) draft.getDraft("server.tags");
            Assert.assertEquals("删除后剩 1 项（prefill 2 项删 1）", 1, tags.size());
            Assert.assertTrue(tags.contains("FontA") || tags.contains("FontB"));

            screen.__saveChanges();
            screen.__getRuntime().flush();
            Assert.assertTrue("列表保存成功", screen.__getLastSaveOutcome().isSuccess());

            ConfigManager reloaded = ConfigManager.bootstrap(file, schema);
            Assert.assertEquals("new.host", reloaded.authority().getString("server.host"));
            Assert.assertEquals(1, ((List<?>) reloaded.authority().get("server.tags")).size());
        } finally {
            screen.dispose();
        }
    }

    /** replaceDraft 前后 effect 计数不增长；dispose 后回基线；revision bump 不传播。 */
    @Test
    public void replaceAndDisposeUnsubscribeComputed() throws Exception {
        File file = tempFolder.newFile("dispose-sub.yaml");
        write(file, "");
        ConfigManager manager = ConfigManager.bootstrap(file, UiSchemaFactory.serverSchema());
        DraftSignalAdapter adapter = new DraftSignalAdapter(null, manager.openDraft());
        ConfigScreen screen = new ConfigScreen(null, manager, adapter, FieldRendererRegistry.defaultRegistry());
        try {
            screen.__getRuntime().flush();
            int baseline = ReactiveTestProbe.registeredEffectCount();

            club.heiqi.uilib.ui.reactive.ReadableSignal<Boolean> dirty =
                    adapter.dirtySignal("server.host");
            Assert.assertFalse(dirty.get().booleanValue());

            adapter.onFieldEdit("server.host", "edited");
            screen.__getRuntime().flush();
            Assert.assertTrue(dirty.get().booleanValue());
            int afterEdit = ReactiveTestProbe.registeredEffectCount();
            Assert.assertTrue("编辑不应泄漏 effect", afterEdit <= baseline + 5);

            int beforeReplace = ReactiveTestProbe.registeredEffectCount();
            DraftBuffer fresh = manager.reloadDraftFromDisk();
            adapter.replaceDraft(fresh);
            screen.__getRuntime().flush();
            int afterReplace = ReactiveTestProbe.registeredEffectCount();
            Assert.assertEquals("replaceDraft 后 effect 计数不增长", beforeReplace, afterReplace);
            Assert.assertFalse(dirty.get().booleanValue());
            Assert.assertSame(dirty, adapter.dirtySignal("server.host"));

            adapter.onFieldEdit("server.host", "again");
            screen.__getRuntime().flush();
            Assert.assertTrue(dirty.get().booleanValue());

            screen.dispose();
            int afterDispose = ReactiveTestProbe.registeredEffectCount();
            Assert.assertTrue("screen/adapter dispose 后 effect 回落",
                    afterDispose <= baseline);

            // dispose 后 buffer 再变，computed 不传播（冻结）
            try {
                adapter.draft().setDraft("server.host", "after.dispose");
            } catch (Throwable ignored) {
                // dispose 后 adapter 可能拒 mutator
            }
            Assert.assertTrue("dispose 后 computed 冻结在最后值", dirty.get().booleanValue());
        } finally {
            try {
                adapter.dispose();
            } catch (Throwable ignored) {
                // ignore double dispose
            }
        }
    }

    /** DraftSignalAdapter 非 owner 线程 mutator 抛 IllegalStateException 且状态零变化。 */
    @Test
    public void adapterMutatorRejectsForeignThread() throws Exception {
        File file = tempFolder.newFile("thread.yaml");
        write(file, "");
        ConfigManager manager = ConfigManager.bootstrap(file, UiSchemaFactory.serverSchema());
        DraftSignalAdapter adapter = new DraftSignalAdapter(null, manager.openDraft());
        try {
            adapter.onFieldEdit("server.host", "main");
            ReactiveScheduler.get().flush();
            Assert.assertEquals("main", adapter.draftSignal("server.host").get());
            Assert.assertEquals("main", adapter.draft().getDraft("server.host"));

            final Exception[] holder = new Exception[1];
            Thread t = new Thread(() -> {
                try {
                    adapter.onFieldEdit("server.host", "other-thread");
                } catch (Exception e) {
                    holder[0] = e;
                }
            }, "foreign-ui-thread");
            t.start();
            t.join(5000);
            Assert.assertNotNull(holder[0]);
            Assert.assertTrue(holder[0] instanceof IllegalStateException);
            Assert.assertTrue(holder[0].getMessage().contains("owner thread")
                    || holder[0].getMessage().contains("foreign-ui-thread"));
            ReactiveScheduler.get().flush();
            Assert.assertEquals("跨线程 mutator 状态零变化", "main",
                    adapter.draftSignal("server.host").get());
            Assert.assertEquals("main", adapter.draft().getDraft("server.host"));
        } finally {
            adapter.dispose();
        }
    }
}
