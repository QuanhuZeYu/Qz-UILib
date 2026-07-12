package club.heiqi.config.ui;

import java.io.File;
import java.io.FileWriter;
import java.util.concurrent.atomic.AtomicReference;

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
import club.heiqi.config.schema.SectionSpec;
import club.heiqi.config.ui.field.FieldRendererRegistry;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * {@link ConfigScreen} 单元测试。
 *
 * <p>照 scene 控件测试范式：headless 构造（input=null）+ __getXxx 访问器。
 * 覆盖骨架结构、按钮 enabled 派生、保存/取消/恢复默认回调、statusSummary 徽标、
 * 多 section 保序、大量字段渲染。</p>
 */
public class ConfigScreenTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static final int CANVAS_WIDTH = 520;
    private static final int CANVAS_HEIGHT = 420;

    private ConfigManager manager;
    private DraftSignalAdapter adapter;
    private ConfigScreen screen;

    @Before
    public void setUp() throws Exception {
        ReactiveScheduler.get().reset();
        File file = tempFolder.newFile("config-screen.yaml");
        write(file, "");
        ConfigSchema schema = UiSchemaFactory.serverSchema();
        manager = ConfigManager.bootstrap(file, schema);
        DraftBuffer draft = manager.openDraft();
        adapter = new DraftSignalAdapter(null, draft);
        FieldRendererRegistry registry = FieldRendererRegistry.defaultRegistry();
        screen = new ConfigScreen(null, manager, adapter, registry);
    }

    @After
    public void tearDown() throws Exception {
        screen.dispose();
        adapter.dispose();
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

    /** 跑一次布局（用 AbstractSceneHostWidget.getLayoutEngine） */
    private void doLayout() {
        SceneLayoutEngine engine = screen.getLayoutEngine();
        engine.layout(screen.__getRoot(), new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    // ==================== 1-3. 骨架结构 ====================

    @Test
    public void rootNonNull() throws Exception {
        Assert.assertNotNull("root 非空", screen.__getRoot());
    }

    @Test
    public void titleBarContainsModId() throws Exception {
        SceneNode titleBar = screen.__getTitleBar();
        Assert.assertNotNull("titleBar 非空", titleBar);
        // titleBar 第二个子节点含 modId
        SceneNode modNode = titleBar.__getChildren().get(1);
        Assert.assertTrue("titleBar 含 modId", modNode.getText().contains("test"));
    }

    @Test
    public void contentContainsAllSectionFields() throws Exception {
        SceneNode content = screen.__getContent();
        Assert.assertNotNull("content 非空", content);
        // server schema 有 1 section：content = [激活 panel, anchor]（rt.show 内容插到 anchor 之前）
        Assert.assertEquals("content 含 1 panel + 1 anchor", 2, content.__getChildren().size());
        SceneNode sectionPanel = content.__getChildren().get(0);
        // sectionPanel 含 1 sectionTitle + 4 field cards = 5
        Assert.assertEquals("section 含 title + 4 fields", 5, sectionPanel.__getChildren().size());
    }

    // ==================== 4. actionBar 含 3 按钮 ====================

    @Test
    public void actionBarContainsThreeButtons() throws Exception {
        SceneNode actionBar = screen.__getActionBar();
        Assert.assertNotNull("actionBar 非空", screen.__getActionBar());
        // S3：左右分区后含 3 按钮 + 1 spacer = 4 子节点
        Assert.assertEquals("actionBar 含 3 按钮 + 1 spacer", 4, actionBar.__getChildren().size());
    }

    // ==================== 5-6. 按钮 enabled 派生 ====================

    @Test
    public void saveButtonEnabledFollowsCanSave() throws Exception {
        // 无改动时 canSave=false → 保存按钮 disabled
        Assert.assertFalse("无改动 canSave=false",
                screen.__getAdapter().canSaveSignal().get().booleanValue());
        // 有改动无错 canSave=true
        adapter.onFieldEdit("server.host", "new.host");
        screen.__getRuntime().flush();
        Assert.assertTrue("有改动 canSave=true",
                screen.__getAdapter().canSaveSignal().get().booleanValue());
    }

    @Test
    public void cancelButtonEnabledFollowsIsDirty() throws Exception {
        Assert.assertFalse("无改动 isDirty=false",
                screen.__getAdapter().isDirtySignal().get().booleanValue());
        adapter.onFieldEdit("server.debug", Boolean.TRUE);
        screen.__getRuntime().flush();
        Assert.assertTrue("有改动 isDirty=true",
                screen.__getAdapter().isDirtySignal().get().booleanValue());
    }

    // ==================== 7. 无改动时保存/取消禁用 ====================

    @Test
    public void noChangeBothDisabled() throws Exception {
        Assert.assertFalse("保存禁用", screen.__getAdapter().canSaveSignal().get().booleanValue());
        Assert.assertFalse("取消禁用", screen.__getAdapter().isDirtySignal().get().booleanValue());
    }

    // ==================== 8. 有改动时保存启用（无错） ====================

    @Test
    public void dirtyNoErrorSaveEnabled() throws Exception {
        adapter.onFieldEdit("server.host", "valid.host");
        screen.__getRuntime().flush();
        Assert.assertTrue("有改动无错保存启用", screen.__getAdapter().canSaveSignal().get().booleanValue());
    }

    // ==================== 9. 有改动有错时保存禁用 ====================

    @Test
    public void dirtyWithErrorSaveDisabled() throws Exception {
        adapter.onFieldEdit("server.port", 99999.0);
        screen.__getRuntime().flush();
        Assert.assertFalse("有改动有错保存禁用",
                screen.__getAdapter().canSaveSignal().get().booleanValue());
    }

    // ==================== 10. 保存回调触发 mgr.save ====================

    @Test
    public void saveCallbackTriggersManagerSave() throws Exception {
        adapter.onFieldEdit("server.host", "saved.host");
        screen.__getRuntime().flush();
        screen.__saveChanges();
        Assert.assertNotNull("保存结果非 null", screen.__getLastSaveOutcome());
        Assert.assertTrue("保存成功", screen.__getLastSaveOutcome().isSuccess());
        // authority 应更新
        Assert.assertEquals("authority 更新", "saved.host", manager.authority().getString("server.host"));
    }

    // ==================== 11. 取消回调触发 resetToCurrent ====================

    @Test
    public void cancelCallbackTriggersResetToCurrent() throws Exception {
        adapter.onFieldEdit("server.host", "temp.host");
        screen.__getRuntime().flush();
        Assert.assertTrue("编辑后 dirty", adapter.dirtySignal("server.host").get().booleanValue());
        screen.__cancelChanges();
        screen.__getRuntime().flush();
        Assert.assertFalse("取消后 dirty=false",
                adapter.dirtySignal("server.host").get().booleanValue());
        Assert.assertEquals("取消后 draftSignal=current",
                "localhost", adapter.draftSignal("server.host").get());
    }

    // ==================== 12. 恢复默认回调触发逐字段 resetFieldToDefault ====================

    @Test
    public void restoreDefaultsCallbackTriggersResetFieldToDefault() throws Exception {
        // 先改 current（模拟保存）
        adapter.onFieldEdit("server.host", "current.host");
        screen.__getRuntime().flush();
        screen.__saveChanges();
        screen.__getRuntime().flush();
        // 恢复默认
        screen.__restoreDefaults();
        screen.__getRuntime().flush();
        Assert.assertEquals("恢复默认后 draftSignal=default",
                "localhost", adapter.draftSignal("server.host").get());
        Assert.assertTrue("default != current → dirty",
                adapter.dirtySignal("server.host").get().booleanValue());
    }

    /**
     * 恢复默认策略 skip 优先级最高：被跳过字段不执行 resetFieldToDefault。
     */
    @Test
    public void restoreDefaultsSkipsPolicyField() throws Exception {
        File file = tempFolder.newFile("config-restore-skip.yaml");
        write(file, "");
        ConfigManager mgr = ConfigManager.bootstrap(file, UiSchemaFactory.serverSchema());
        DraftSignalAdapter a = new DraftSignalAdapter(null, mgr.openDraft());
        FieldRestorePolicy policy = new FieldRestorePolicy().skip("server.debug");
        ConfigScreen s = new ConfigScreen(null, mgr, a, FieldRendererRegistry.defaultRegistry(), policy);
        try {
            a.onFieldEdit("server.debug", Boolean.TRUE);
            s.__getRuntime().flush();
            s.__saveChanges();
            s.__getRuntime().flush();

            s.__restoreDefaults();
            s.__getRuntime().flush();

            Assert.assertEquals("skip 字段 draft 应保持 current，不恢复 schema 默认值",
                    Boolean.TRUE, a.draftSignal("server.debug").get());
            Assert.assertFalse("skip 字段 current=draft，应保持非 dirty",
                    a.dirtySignal("server.debug").get().booleanValue());
        } finally {
            s.dispose();
            a.dispose();
        }
    }

    /**
     * 恢复默认策略 custom：字段执行自定义动作，不执行默认 resetFieldToDefault。
     */
    @Test
    public void restoreDefaultsUsesCustomPolicyAction() throws Exception {
        File file = tempFolder.newFile("config-restore-custom.yaml");
        write(file, "");
        ConfigManager mgr = ConfigManager.bootstrap(file, UiSchemaFactory.serverSchema());
        DraftSignalAdapter a = new DraftSignalAdapter(null, mgr.openDraft());
        FieldRestorePolicy policy = new FieldRestorePolicy()
                .custom("server.host", adapter -> adapter.onFieldEdit("server.host", "custom.host"));
        ConfigScreen s = new ConfigScreen(null, mgr, a, FieldRendererRegistry.defaultRegistry(), policy);
        try {
            a.onFieldEdit("server.host", "current.host");
            s.__getRuntime().flush();
            s.__saveChanges();
            s.__getRuntime().flush();

            s.__restoreDefaults();
            s.__getRuntime().flush();

            Assert.assertEquals("custom 字段应使用自定义恢复值，而非 schema 默认 localhost",
                    "custom.host", a.draftSignal("server.host").get());
            Assert.assertTrue("custom 值 != current，应标记 dirty",
                    a.dirtySignal("server.host").get().booleanValue());
        } finally {
            s.dispose();
            a.dispose();
        }
    }

    // ==================== 13. statusSummary 显示徽标 ====================

    @Test
    public void statusSummaryShowsBadges() throws Exception {
        SceneNode status = screen.__getStatusSummary();
        Assert.assertNotNull("statusSummary 非空", status);
        // S4：save 反馈拆出独立行后，statusSummary 只含 2 计数徽标
        Assert.assertEquals("含 2 徽标（save 反馈已拆行）", 2, status.__getChildren().size());
    }

    // ==================== 14. headless 构造不崩 ====================

    @Test
    public void headlessConstructionDoesNotCrash() throws Exception {
        // setUp 已用 input=null 构造，到这里即证明不崩
        Assert.assertNotNull("headless 构造成功", screen);
    }

    // ==================== 15-17. __getXxx 返回非 null ====================

    @Test
    public void getRuntimeNonNull() throws Exception {
        Assert.assertNotNull("__getRuntime 非空", screen.__getRuntime());
    }

    @Test
    public void getRootNonNull() throws Exception {
        Assert.assertNotNull("__getRoot 非空", screen.__getRoot());
    }

    @Test
    public void getContentNonNull() throws Exception {
        Assert.assertNotNull("__getContent 非空", screen.__getContent());
    }

    // ==================== 18. 多 section 渲染保序（rt.show 切换） ====================

    @Test
    public void multiSectionPreservesOrder() throws Exception {
        File file = tempFolder.newFile("config-multi.yaml");
        write(file, "");
        ConfigSchema multi = ConfigSchema.builder("multi")
                .section("alpha").title("Alpha")
                    .string("a1").defaultValue("x").label("A1").build()
                .endSection()
                .section("beta").title("Beta")
                    .bool("b1").defaultValue(false).label("B1").build()
                .endSection()
                .build();
        ConfigManager mgr = ConfigManager.bootstrap(file, multi);
        DraftBuffer d = mgr.openDraft();
        DraftSignalAdapter a = new DraftSignalAdapter(null, d);
        ConfigScreen s = new ConfigScreen(null, mgr, a, FieldRendererRegistry.defaultRegistry());
        // 2 section ≤5 → 用 Tab 导航（navRoot 非 null），无 bodyRow
        Assert.assertNotNull("≤5 section 用 Tab 导航", s.__getNavRoot());
        Assert.assertNull("≤5 section 无 bodyRow", s.__getBodyRow());
        // content = [激活 panel, anchor0, anchor1]（panel insertBefore anchor0）
        SceneNode content = s.__getContent();
        Assert.assertEquals("content 含 1 panel + 2 anchor", 3, content.__getChildren().size());
        // 初始 activeSection=0 → panel0 挂载，title=Alpha
        SceneNode panel0 = findActivePanel(content);
        Assert.assertEquals("初始激活 section=Alpha", "Alpha", panel0.__getChildren().get(0).getText());
        // 切换到 section 1 → panel0 卸载、panel1 挂载
        s.__getActiveSectionSignal().set(Integer.valueOf(1));
        s.__getRuntime().flush();
        Assert.assertEquals("切换后仍 1 panel + 2 anchor", 3, content.__getChildren().size());
        SceneNode panel1 = findActivePanel(content);
        Assert.assertEquals("切换后激活 section=Beta", "Beta", panel1.__getChildren().get(0).getText());
        s.dispose();
        a.dispose();
    }

    /**
     * 在 content 子节点中找激活的 section panel（anchor 是零尺寸空节点无 children，panel 有 children）。
     *
     * @param content content 节点
     * @return 第一个有 children 的子节点（即激活 panel）
     */
    private static SceneNode findActivePanel(SceneNode content) {
        for (SceneNode child : content.__getChildren()) {
            if (!child.__getChildren().isEmpty()) {
                return child;
            }
        }
        throw new AssertionError("content 中无激活 panel");
    }

    // ==================== 18b. >5 section 用侧栏导航 ====================

    @Test
    public void moreThanFiveSectionsUseSidebarNav() throws Exception {
        File file = tempFolder.newFile("config-sidebar.yaml");
        write(file, "");
        ConfigSchema.Builder b = ConfigSchema.builder("sidebar");
        for (int i = 0; i < 6; i++) {
            b.section("s" + i).title("Section " + i)
                    .string("f").defaultValue("v").label("F").build()
                    .endSection();
        }
        ConfigSchema sidebar = b.build();
        ConfigManager mgr = ConfigManager.bootstrap(file, sidebar);
        DraftBuffer d = mgr.openDraft();
        DraftSignalAdapter a = new DraftSignalAdapter(null, d);
        ConfigScreen s = new ConfigScreen(null, mgr, a, FieldRendererRegistry.defaultRegistry());
        // 6 section >5 → 用侧栏导航（navRoot 非 null，bodyRow 非 null）
        Assert.assertNotNull(">5 section 用侧栏导航", s.__getNavRoot());
        Assert.assertNotNull(">5 section 有 bodyRow", s.__getBodyRow());
        // bodyRow 含 navPane + scrollContainer = 2
        Assert.assertEquals("bodyRow 含 navPane + scrollContainer", 2, s.__getBodyRow().__getChildren().size());
        s.dispose();
        a.dispose();
    }

    // ==================== 18c. 滚动容器承载 viewport + scrollbar（项2/3/4） ====================

    /**
     * scrollContainer 应含 viewport + scrollbarColumn = 2，viewport 在 scrollbar 左侧。
     */
    @Test
    public void scrollContainerHoldsViewportAndScrollbar() throws Exception {
        SceneNode scrollContainer = screen.__getScrollContainer();
        Assert.assertNotNull("scrollContainer 非空", scrollContainer);
        Assert.assertEquals("scrollContainer 含 viewport + scrollbarColumn", 2,
                scrollContainer.__getChildren().size());
        Assert.assertSame("scrollContainer 第一个子是 viewport",
                screen.__getViewport(), scrollContainer.__getChildren().get(0));
        Assert.assertSame("scrollContainer 第二个子是 scrollbarColumn",
                screen.__getScrollbarColumn(), scrollContainer.__getChildren().get(1));
        Assert.assertNotNull("scrollbarColumn 非空", screen.__getScrollbarColumn());
    }

    /**
     * actionBar 应在 scrollContainer 外侧，作为 root COLUMN 顶部固定行（紧随 titleBar，index=1），
     * 不进滚动容器；scrollContainer 是 root 直接子（saveFeedback 走 rt.show 懒挂，其 anchor 占末位）。
     */
    @Test
    public void actionBarOutsideScrollContainerAtTopFixed() throws Exception {
        SceneNode root = screen.__getRoot();
        // actionBar 位于 root 第 2 位（紧随 titleBar）
        Assert.assertSame("root 第 2 个子是 actionBar（index=1）",
                screen.__getActionBar(), root.__getChildren().get(1));
        Assert.assertSame("root 第 1 个子是 titleBar",
                screen.__getTitleBar(), root.__getChildren().get(0));
        // scrollContainer 是 root 直接子（不限定位置：saveFeedback 的 rt.show anchor 在末位）
        boolean scrollContainerIsDirectChild = false;
        for (SceneNode child : root.__getChildren()) {
            if (child == screen.__getScrollContainer()) {
                scrollContainerIsDirectChild = true;
                break;
            }
        }
        Assert.assertTrue("scrollContainer 是 root 直接子", scrollContainerIsDirectChild);
        // actionBar 不在 scrollContainer 内
        for (SceneNode child : screen.__getScrollContainer().__getChildren()) {
            Assert.assertNotSame("actionBar 不在 scrollContainer 内", screen.__getActionBar(), child);
        }
    }

    // ==================== 19. 大量字段渲染不崩 ====================

    @Test
    public void largeFieldCountDoesNotCrash() throws Exception {
        File file = tempFolder.newFile("config-large.yaml");
        write(file, "");
        ConfigSchema large = ConfigSchema.builder("large")
                .section("s")
                    .string("f0").defaultValue("v0").build()
                    .string("f1").defaultValue("v1").build()
                    .string("f2").defaultValue("v2").build()
                    .string("f3").defaultValue("v3").build()
                    .string("f4").defaultValue("v4").build()
                    .string("f5").defaultValue("v5").build()
                    .string("f6").defaultValue("v6").build()
                    .string("f7").defaultValue("v7").build()
                    .string("f8").defaultValue("v8").build()
                    .string("f9").defaultValue("v9").build()
                .endSection()
                .build();
        ConfigManager mgr = ConfigManager.bootstrap(file, large);
        DraftBuffer d = mgr.openDraft();
        DraftSignalAdapter a = new DraftSignalAdapter(null, d);
        ConfigScreen s = new ConfigScreen(null, mgr, a, FieldRendererRegistry.defaultRegistry());
        SceneNode content = s.__getContent();
        // content = [激活 panel, anchor]；panel 含 1 title + 10 fields = 11
        SceneNode sectionPanel = content.__getChildren().get(0);
        Assert.assertEquals("10 字段渲染不崩", 11, sectionPanel.__getChildren().size());
        s.dispose();
        a.dispose();
    }

    // ==================== 20. save 成功反馈 ====================

    @Test
    public void saveSuccessWritesOkFeedback() throws Exception {
        adapter.onFieldEdit("server.host", "saved.host");
        screen.__getRuntime().flush();
        screen.__saveChanges();
        screen.__getRuntime().flush();
        SaveFeedback fb = screen.__getAdapter().saveFeedbackSignal().get();
        Assert.assertNotNull("save 反馈非 null", fb);
        Assert.assertEquals("save 成功反馈状态 OK", SaveFeedback.Status.OK, fb.status());
        Assert.assertFalse("save 成功非错误", fb.isError());
    }

    // ==================== 21. save 失败反馈（校验失败） ====================

    @Test
    public void saveFailureWritesErrorFeedback() throws Exception {
        // 编辑为非法值（port 超上限），canSave=false 但 __saveChanges 直接调 manager.save
        adapter.onFieldEdit("server.port", 99999.0);
        screen.__getRuntime().flush();
        screen.__saveChanges();
        screen.__getRuntime().flush();
        SaveOutcome outcome = screen.__getLastSaveOutcome();
        Assert.assertFalse("非法值保存失败", outcome.isSuccess());
        SaveFeedback fb = screen.__getAdapter().saveFeedbackSignal().get();
        Assert.assertNotNull("save 反馈非 null", fb);
        Assert.assertTrue("save 失败反馈为错误", fb.isError());
        Assert.assertTrue("反馈文案含失败提示", fb.message().contains("保存失败"));
    }

    // ==================== 22. 状态栏计数徽标随编辑更新 ====================

    @Test
    public void statusBadgesReflectDirtyAndErrorCounts() throws Exception {
        // 初始 0 脏 0 错
        screen.__getRuntime().flush();
        Assert.assertEquals("初始 0 脏", Integer.valueOf(0), adapter.dirtyCountSignal().get());
        Assert.assertEquals("初始 0 错", Integer.valueOf(0), adapter.errorCountSignal().get());
        // 编辑 2 字段为脏
        adapter.onFieldEdit("server.host", "a");
        adapter.onFieldEdit("server.debug", Boolean.TRUE);
        screen.__getRuntime().flush();
        Assert.assertEquals("2 脏字段", Integer.valueOf(2), adapter.dirtyCountSignal().get());
        // 1 字段非法 → 1 错
        adapter.onFieldEdit("server.port", 99999.0);
        screen.__getRuntime().flush();
        Assert.assertEquals("1 错字段", Integer.valueOf(1), adapter.errorCountSignal().get());
    }

    // ==================== 23. S3 actionBar 左右分区（spacer 节点） ====================

    @Test
    public void actionBarSpacerSplitsLeftAndRight() throws Exception {
        SceneNode actionBar = screen.__getActionBar();
        // 4 子节点：恢复默认(0) / spacer(1) / 取消(2) / 保存(3)
        Assert.assertEquals("actionBar 4 子节点", 4, actionBar.__getChildren().size());
        SceneNode spacer = actionBar.__getChildren().get(1);
        Assert.assertEquals("spacer flexGrow=1", 1, spacer.getFlexGrow());
        // spacer 无文本无背景（纯占位）
        Assert.assertFalse("spacer hitTestable=false", spacer.isHitTestable());
    }

    // ==================== 24. S4 save 反馈独立行懒挂载 ====================

    @Test
    public void saveFeedbackBarHiddenWhenNone() throws Exception {
        screen.__getRuntime().flush();
        // 初始 saveFeedback=NONE → 反馈行不挂载
        SaveFeedback fb = adapter.saveFeedbackSignal().get();
        Assert.assertNotNull("初始 saveFeedback 非 null", fb);
        Assert.assertTrue("初始 saveFeedback isNone", fb.isNone());
        // root 子节点：titleBar / actionBar / statusSummary / scrollContainer / [anchor]
        // rt.show 的 anchor 常驻（零尺寸），content 不挂载
        // 无法直接断言 anchor 数量，但可断言 save 反馈不显示：root 中无文本含"已保存"或"保存失败"
        for (SceneNode child : screen.__getRoot().__getChildren()) {
            String t = child.getText();
            Assert.assertTrue("NONE 时无 save 反馈文本", t == null || !t.contains("保存"));
        }
    }

    @Test
    public void saveFeedbackBarShownWhenNotNone() throws Exception {
        adapter.onFieldEdit("server.host", "saved.host");
        screen.__getRuntime().flush();
        screen.__saveChanges();
        screen.__getRuntime().flush();
        // save 成功 → saveFeedback=OK → 反馈行挂载
        SaveFeedback fb = adapter.saveFeedbackSignal().get();
        Assert.assertFalse("save 后 saveFeedback 非 NONE", fb.isNone());
        // root 中应能找到含"已保存"的节点（反馈行内文本，可能嵌套 2~3 层）
        boolean found = containsText(screen.__getRoot(), "已保存");
        Assert.assertTrue("save 成功后反馈行显示「已保存」", found);
    }

    /** 递归查找文本节点是否包含指定子串。 */
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

    // ==================== 25. S1 字号梯度 ====================

    @Test
    public void titleBarUsesTitleFontSize() throws Exception {
        SceneNode titleBar = screen.__getTitleBar();
        SceneNode titleNode = titleBar.__getChildren().get(0);
        Assert.assertEquals("页标题字号 22", 22, titleNode.getFontSize());
        SceneNode subNode = titleBar.__getChildren().get(1);
        Assert.assertEquals("副标题字号 12", 12, subNode.getFontSize());
    }

    @Test
    public void sectionTitleUsesSectionFontSize() throws Exception {
        // server schema 1 section → content 含激活 panel，panel 第一个子是 sectionTitle
        SceneNode panel = screen.__getContent().__getChildren().get(0);
        SceneNode sectionTitle = panel.__getChildren().get(0);
        Assert.assertEquals("section 标题字号 18", 18, sectionTitle.getFontSize());
    }

    @Test
    public void badgeUsesBadgeFontSize() throws Exception {
        SceneNode status = screen.__getStatusSummary();
        SceneNode badge0 = status.__getChildren().get(0);
        SceneNode badgeText = badge0.__getChildren().get(0);
        Assert.assertEquals("徽标字号 12", 12, badgeText.getFontSize());
    }

    // ==================== 26. m2 titleBar 用 schema.title() ====================

    @Test
    public void titleBarShowsSchemaTitle() throws Exception {
        // server schema modId="test"，未设 title → title 回退 "test"
        SceneNode titleBar = screen.__getTitleBar();
        SceneNode titleNode = titleBar.__getChildren().get(0);
        Assert.assertEquals("titleBar 显示 schema.title()（回退 modId）", "test", titleNode.getText());
    }

    @Test
    public void titleBarShowsExplicitSchemaTitle() throws Exception {
        File file = tempFolder.newFile("config-title.yaml");
        write(file, "");
        ConfigSchema titled = ConfigSchema.builder("titled_mod")
                .title("我的模组配置")
                .section("s")
                    .string("a").defaultValue("v").build()
                .endSection()
                .build();
        ConfigManager mgr = ConfigManager.bootstrap(file, titled);
        DraftBuffer d = mgr.openDraft();
        DraftSignalAdapter a = new DraftSignalAdapter(null, d);
        ConfigScreen s = new ConfigScreen(null, mgr, a, FieldRendererRegistry.defaultRegistry());
        SceneNode titleNode = s.__getTitleBar().__getChildren().get(0);
        Assert.assertEquals("titleBar 显示显式 schema title", "我的模组配置", titleNode.getText());
        SceneNode subNode = s.__getTitleBar().__getChildren().get(1);
        Assert.assertTrue("副标题仍含 modId", subNode.getText().contains("titled_mod"));
        s.dispose();
        a.dispose();
    }

    // ==================== 27. ≤5 section 横向 Tab 形态 viewport 收到固定高约束（grow 求解器不早退） ====================

    /**
     * 回归：≤5 section 横向 Tab 形态下，navRoot（SceneSegmented 根）是容器型固定子，
     * 未设 preferredHeight 时 ConstraintResolver.computeColumnGrowHeights 命中容器分支
     * 返回 UNCONSTRAINED 早退（ConstraintResolver.java:332），scrollContainer 收不到 grow
     * 分配高，viewport 被内容撑大 → maxScroll=0。修复后 navRoot 设 preferredHeight，
     * grow 求解器正常分配，viewport 收到固定高约束，content 溢出时 maxScroll > 0。
     */
    @Test
    public void tabNavViewportReceivesFixedHeightAndCanScrollWhenContentOverflows() throws Exception {
        File file = tempFolder.newFile("config-tabnav-scroll.yaml");
        write(file, "");
        // 2 section（≤5 → 横向 Tab 形态），每 section 12 个 string 字段，确保激活 panel 溢出视口
        ConfigSchema.Builder b = ConfigSchema.builder("tabnav");
        for (int s = 0; s < 2; s++) {
            SectionSpec.Builder sec = b.section("sec" + s).title("Section " + s);
            for (int f = 0; f < 12; f++) {
                sec.string("f" + f).defaultValue("v" + f).label("Field " + f).helper("helper " + f).build();
            }
            sec.endSection();
        }
        ConfigSchema schema = b.build();
        ConfigManager mgr = ConfigManager.bootstrap(file, schema);
        DraftBuffer d = mgr.openDraft();
        DraftSignalAdapter a = new DraftSignalAdapter(null, d);
        ConfigScreen s = new ConfigScreen(null, mgr, a, FieldRendererRegistry.defaultRegistry());
        try {
            // 横向 Tab 形态：navRoot 非 null，无 bodyRow
            Assert.assertNotNull("≤5 section 用 Tab 导航", s.__getNavRoot());
            Assert.assertNull("≤5 section 无 bodyRow", s.__getBodyRow());
            // navRoot 已设 preferredHeight（>0），grow 求解器不早退
            Assert.assertTrue("navRoot 已设 preferredHeight",
                    s.__getNavRoot().getPreferredHeight() > 0);

            // 用较小画布跑布局，确保 content 溢出 viewport
            SceneLayoutEngine engine = s.getLayoutEngine();
            engine.layout(s.__getRoot(), new Constraints(520, 300));

            SceneNode viewport = s.__getViewport();
            Object cached = viewport.getCachedLayout();
            Assert.assertTrue("viewport 已布局（LayoutBox 非空）", cached instanceof LayoutBox);
            int viewportH = ((LayoutBox) cached).getHeight();
            // viewport 收到固定高约束（不被内容撑大）：应远小于画布高 300
            Assert.assertTrue("viewport 高度受固定约束（未被内容撑大）",
                    viewportH > 0 && viewportH < 300);
            // content 溢出时 maxScroll > 0（核心回归断言）
            int maxScroll = SceneGeometry.maxScrollY(viewport);
            Assert.assertTrue("content 溢出时 maxScroll > 0（grow 求解器未早退）", maxScroll > 0);
        } finally {
            s.dispose();
            a.dispose();
        }
    }

    // ==================== 28. saveFeedback 显示态 viewport 仍收到固定高约束 ====================

    /**
     * 回归：saveFeedbackBar 显示态作为 root COLUMN 内固定行，未设 preferredHeight 时
     * 同样触发 grow 求解器早退，viewport 被内容撑大。修复后该行设 preferredHeight，
     * 显示态下 viewport 仍收到固定高约束，content 溢出时 maxScroll > 0。
     */
    @Test
    public void saveFeedbackShownViewportStillReceivesFixedHeight() throws Exception {
        File file = tempFolder.newFile("config-savefb-scroll.yaml");
        write(file, "");
        // 2 section × 12 string 字段，确保溢出
        ConfigSchema.Builder b = ConfigSchema.builder("savefb");
        for (int s = 0; s < 2; s++) {
            SectionSpec.Builder sec = b.section("sec" + s).title("Section " + s);
            for (int f = 0; f < 12; f++) {
                sec.string("f" + f).defaultValue("v" + f).label("Field " + f).build();
            }
            sec.endSection();
        }
        ConfigSchema schema = b.build();
        ConfigManager mgr = ConfigManager.bootstrap(file, schema);
        DraftBuffer d = mgr.openDraft();
        DraftSignalAdapter a = new DraftSignalAdapter(null, d);
        ConfigScreen s = new ConfigScreen(null, mgr, a, FieldRendererRegistry.defaultRegistry());
        try {
            // 触发 save 成功 → saveFeedback=OK → 反馈行挂载（显示态）
            a.onFieldEdit("sec0.f0", "edited.value");
            s.__getRuntime().flush();
            s.__saveChanges();
            s.__getRuntime().flush();
            SaveFeedback fb = a.saveFeedbackSignal().get();
            Assert.assertFalse("save 后反馈行显示（非 NONE）", fb.isNone());

            // 跑布局
            SceneLayoutEngine engine = s.getLayoutEngine();
            engine.layout(s.__getRoot(), new Constraints(520, 300));

            SceneNode viewport = s.__getViewport();
            Object cached = viewport.getCachedLayout();
            Assert.assertTrue("viewport 已布局", cached instanceof LayoutBox);
            int viewportH = ((LayoutBox) cached).getHeight();
            Assert.assertTrue("显示态 viewport 高度受固定约束（未被内容撑大）",
                    viewportH > 0 && viewportH < 300);
            int maxScroll = SceneGeometry.maxScrollY(viewport);
            Assert.assertTrue("显示态 content 溢出时 maxScroll > 0", maxScroll > 0);
        } finally {
            s.dispose();
            a.dispose();
        }
    }

    // ==================== 29. per-section scroll state（BUG2：section 切换不丢失滚动位置） ====================

    /**
     * BUG2 根因：原 ConfigScreen 用单一 scrollSignal，切换 section 时 viewport 内容高度变化但
     * scrollSignal 值不变，clamp 后 scrollOffsetY 可能超出新 section 的 maxScroll，导致内容
     * 滚出视口外看似消失。修复：per-section Signal[] + 派生 Computed（当前 active section 的 scroll，
     * clamp 到当前 maxScroll）+ 写入回调写当前 active section 的 signal。
     *
     * <p>公共构造：2 section，sec0 长（12 字段，溢出视口），sec1 短（1 字段，不溢出）。</p>
     */
    private ConfigScreen buildPerSectionScrollScreen() throws Exception {
        File file = tempFolder.newFile("config-persection-scroll.yaml");
        write(file, "");
        ConfigSchema.Builder b = ConfigSchema.builder("persection");
        SectionSpec.Builder sec0 = b.section("sec0").title("Section 0");
        for (int f = 0; f < 12; f++) {
            sec0.string("f" + f).defaultValue("v" + f).label("Field " + f).helper("helper " + f).build();
        }
        sec0.endSection();
        SectionSpec.Builder sec1 = b.section("sec1").title("Section 1");
        sec1.string("f0").defaultValue("v").label("Field 0").build();
        sec1.endSection();
        ConfigSchema schema = b.build();
        ConfigManager mgr = ConfigManager.bootstrap(file, schema);
        DraftBuffer d = mgr.openDraft();
        DraftSignalAdapter a = new DraftSignalAdapter(null, d);
        ConfigScreen s = new ConfigScreen(null, mgr, a, FieldRendererRegistry.defaultRegistry());
        // 跑一帧（layout + bump layoutDoneSignal + flush + layout），使 viewport 有 LayoutBox
        // 且 activeScroll Computed 物化读最新几何
        s.__doFrameForTest(520, 300);
        return s;
    }

    /**
     * 首次进入 section scroll=0（per-section signal 初始值 0）。
     */
    @Test
    public void firstEnterSectionShouldScrollZero() throws Exception {
        ConfigScreen s = buildPerSectionScrollScreen();
        DraftSignalAdapter a = s.__getAdapter();
        try {
            // 首次进入 sec0 scroll=0
            Assert.assertEquals("首次进入 sec0 scroll=0", 0, s.__getViewport().getScrollOffsetY());
            // 切到 sec1 scroll=0
            s.__getActiveSectionSignal().set(Integer.valueOf(1));
            s.__doFrameForTest(520, 300);
            Assert.assertEquals("首次进入 sec1 scroll=0", 0, s.__getViewport().getScrollOffsetY());
        } finally {
            s.dispose();
            a.dispose();
        }
    }

    /**
     * 长 section 滚到 200 → 切短 section → scrollOffsetY=0（sec1 scroll state=0）。
     */
    @Test
    public void switchToShortSectionShouldStartFromTop() throws Exception {
        ConfigScreen s = buildPerSectionScrollScreen();
        DraftSignalAdapter a = s.__getAdapter();
        try {
            // 确认 sec0 溢出足够（maxScroll > 200）
            int maxScrollSec0 = SceneGeometry.maxScrollY(s.__getViewport());
            Assert.assertTrue("sec0 maxScroll > 200（溢出足够）", maxScrollSec0 > 200);

            // sec0 滚到 200
            s.__getSetScroll().accept(Integer.valueOf(200));
            s.__doFrameForTest(520, 300);
            Assert.assertEquals("sec0 滚到 200", 200, s.__getViewport().getScrollOffsetY());

            // 切到 sec1（短 section）→ scroll=0
            s.__getActiveSectionSignal().set(Integer.valueOf(1));
            s.__doFrameForTest(520, 300);
            Assert.assertEquals("切到 sec1 scroll=0（短 section 从顶部开始）",
                    0, s.__getViewport().getScrollOffsetY());
        } finally {
            s.dispose();
            a.dispose();
        }
    }

    /**
     * 长 section 滚到 200 → 切走 → 切回 → scrollOffsetY=200（per-section state 保持）。
     *
     * <p>注：rt.show 挂卸会清除 content.cachedLayout（markSelfLayout 清 cachedLayout），
     * 切回 sec0 当帧 activeScroll 重算读 null → maxScroll=0 → 兜底 0；下一帧 layout 后
     * content.cachedLayout 更新 + layoutDoneSignal bump → activeScroll 重算读新 maxScroll
     * → clamp(200, 0, maxScroll)=200。故切回后需跑两帧 __doFrameForTest 消除一帧滞后。</p>
     */
    @Test
    public void switchBackShouldRestoreScrollPosition() throws Exception {
        ConfigScreen s = buildPerSectionScrollScreen();
        DraftSignalAdapter a = s.__getAdapter();
        try {
            int maxScrollSec0 = SceneGeometry.maxScrollY(s.__getViewport());
            Assert.assertTrue("sec0 maxScroll > 200", maxScrollSec0 > 200);

            // sec0 滚到 200
            s.__getSetScroll().accept(Integer.valueOf(200));
            s.__doFrameForTest(520, 300);
            Assert.assertEquals("sec0 滚到 200", 200, s.__getViewport().getScrollOffsetY());

            // 切到 sec1
            s.__getActiveSectionSignal().set(Integer.valueOf(1));
            s.__doFrameForTest(520, 300);
            Assert.assertEquals("切到 sec1 scroll=0", 0, s.__getViewport().getScrollOffsetY());

            // 切回 sec0 → 第一帧 rt.show 挂卸清除 cachedLayout，activeScroll 兜底 0；
            // 第二帧 layout 更新 cachedLayout + layoutDoneSignal bump，activeScroll 重算恢复 200
            s.__getActiveSectionSignal().set(Integer.valueOf(0));
            s.__doFrameForTest(520, 300);
            s.__doFrameForTest(520, 300);
            Assert.assertEquals("切回 sec0 恢复 scroll=200（per-section state 保持）",
                    200, s.__getViewport().getScrollOffsetY());
        } finally {
            s.dispose();
            a.dispose();
        }
    }

    // ==================== DraftValidator → UI 提交错误 ====================

    /**
     * custom reject：字段 error、计数、反馈含真实消息；draft 保留；Authority 不变。
     */
    @Test
    public void customValidatorRejectShowsFieldErrorAndFeedback() throws Exception {
        File file = tempFolder.newFile("config-custom-reject.yaml");
        write(file, "");
        ConfigManager mgr = ConfigManager.bootstrap(file, UiSchemaFactory.serverSchema(),
                new DraftValidator() {
                    @Override
                    public ValidationResult validate(DraftView draft) {
                        return ValidationResult.error("server.host", "host not allowed by policy");
                    }
                });
        DraftSignalAdapter a = new DraftSignalAdapter(null, mgr.openDraft());
        ConfigScreen s = new ConfigScreen(null, mgr, a, FieldRendererRegistry.defaultRegistry());
        try {
            a.onFieldEdit("server.host", "blocked.host");
            a.onFieldEdit("server.port", 3000.0);
            a.onFieldEdit("server.mode", "test");
            s.__getRuntime().flush();
            Assert.assertTrue(a.canSaveSignal().get().booleanValue());

            s.__saveChanges();
            s.__getRuntime().flush();

            Assert.assertEquals(SaveOutcome.Status.INVALID, s.__getLastSaveOutcome().status());
            Assert.assertEquals("host not allowed by policy", a.errorSignal("server.host").get());
            Assert.assertTrue(a.hasErrorSignal().get().booleanValue());
            Assert.assertTrue(a.errorCountSignal().get().intValue() >= 1);
            SaveFeedback fb = a.saveFeedbackSignal().get();
            Assert.assertEquals(SaveFeedback.Status.INVALID, fb.status());
            Assert.assertTrue(fb.message().contains("host not allowed"));
            Assert.assertFalse("禁止仅固定 validation failed",
                    fb.message().equals("保存失败：validation failed"));
            Assert.assertEquals("blocked.host", a.draft().getDraft("server.host"));
            Assert.assertEquals("localhost", mgr.authority().getString("server.host"));
        } finally {
            s.dispose();
            a.dispose();
        }
    }

    /** 编辑后清理提交错误，可再次保存 */
    @Test
    public void editAfterCustomRejectClearsSubmitError() throws Exception {
        File file = tempFolder.newFile("config-custom-clear.yaml");
        write(file, "");
        ConfigManager mgr = ConfigManager.bootstrap(file, UiSchemaFactory.serverSchema(),
                new DraftValidator() {
                    @Override
                    public ValidationResult validate(DraftView draft) {
                        Object host = draft.getDraft("server.host");
                        if ("blocked.host".equals(host)) {
                            return ValidationResult.error("server.host", "blocked");
                        }
                        return ValidationResult.ok();
                    }
                });
        DraftSignalAdapter a = new DraftSignalAdapter(null, mgr.openDraft());
        ConfigScreen s = new ConfigScreen(null, mgr, a, FieldRendererRegistry.defaultRegistry());
        try {
            a.onFieldEdit("server.host", "blocked.host");
            a.onFieldEdit("server.mode", "test");
            s.__getRuntime().flush();
            s.__saveChanges();
            s.__getRuntime().flush();
            Assert.assertEquals("blocked", a.errorSignal("server.host").get());

            a.onFieldEdit("server.host", "ok.host");
            s.__getRuntime().flush();
            Assert.assertNull(a.errorSignal("server.host").get());
            Assert.assertTrue(a.canSaveSignal().get().booleanValue());

            s.__saveChanges();
            s.__getRuntime().flush();
            Assert.assertTrue(s.__getLastSaveOutcome().isSuccess());
            Assert.assertEquals(SaveFeedback.Status.OK, a.saveFeedbackSignal().get().status());
            Assert.assertEquals("ok.host", mgr.authority().getString("server.host"));
        } finally {
            s.dispose();
            a.dispose();
        }
    }

    /** 全局 _config 错误：计数与反馈可见 */
    @Test
    public void globalConfigErrorVisibleInCountAndFeedback() throws Exception {
        File file = tempFolder.newFile("config-global-err.yaml");
        write(file, "");
        ConfigManager mgr = ConfigManager.bootstrap(file, UiSchemaFactory.serverSchema(),
                new DraftValidator() {
                    @Override
                    public ValidationResult validate(DraftView draft) {
                        return ValidationResult.error(
                                DraftValidator.GLOBAL_ERROR_PATH, "cross-field rule failed");
                    }
                });
        DraftSignalAdapter a = new DraftSignalAdapter(null, mgr.openDraft());
        ConfigScreen s = new ConfigScreen(null, mgr, a, FieldRendererRegistry.defaultRegistry());
        try {
            a.onFieldEdit("server.host", "any.host");
            a.onFieldEdit("server.mode", "test");
            s.__getRuntime().flush();
            s.__saveChanges();
            s.__getRuntime().flush();

            Assert.assertEquals(SaveOutcome.Status.INVALID, s.__getLastSaveOutcome().status());
            Assert.assertTrue(a.hasErrorSignal().get().booleanValue());
            Assert.assertEquals(1, a.errorCountSignal().get().intValue());
            SaveFeedback fb = a.saveFeedbackSignal().get();
            Assert.assertTrue(fb.message().contains("cross-field rule failed"));
            Assert.assertEquals("any.host", a.draft().getDraft("server.host"));
        } finally {
            s.dispose();
            a.dispose();
        }
    }

    /**
     * custom INVALID 后编辑任意字段：字段错误、errorCount、hasError、「保存失败」反馈全部清除；
     * draft 仍脏且可再次保存。
     */
    @Test
    public void editAnyFieldAfterInvalidClearsAllSubmitUiState() throws Exception {
        File file = tempFolder.newFile("config-edit-clears-all.yaml");
        write(file, "");
        ConfigManager mgr = ConfigManager.bootstrap(file, UiSchemaFactory.serverSchema(),
                new DraftValidator() {
                    @Override
                    public ValidationResult validate(DraftView draft) {
                        Object host = draft.getDraft("server.host");
                        if ("blocked.host".equals(host)) {
                            return ValidationResult.error("server.host", "policy blocked");
                        }
                        return ValidationResult.ok();
                    }
                });
        DraftSignalAdapter a = new DraftSignalAdapter(null, mgr.openDraft());
        ConfigScreen s = new ConfigScreen(null, mgr, a, FieldRendererRegistry.defaultRegistry());
        try {
            a.onFieldEdit("server.host", "blocked.host");
            a.onFieldEdit("server.mode", "test");
            s.__getRuntime().flush();
            s.__saveChanges();
            s.__getRuntime().flush();

            Assert.assertEquals(SaveOutcome.Status.INVALID, s.__getLastSaveOutcome().status());
            Assert.assertEquals("policy blocked", a.errorSignal("server.host").get());
            Assert.assertTrue(a.errorCountSignal().get().intValue() >= 1);
            Assert.assertTrue(a.hasErrorSignal().get().booleanValue());
            Assert.assertEquals(SaveFeedback.Status.INVALID, a.saveFeedbackSignal().get().status());
            Assert.assertTrue(a.saveFeedbackSignal().get().message().contains("保存失败"));

            // 编辑另一字段（非出错 path）也应清空全部提交 UI 状态
            a.onFieldEdit("server.debug", Boolean.TRUE);
            s.__getRuntime().flush();

            Assert.assertNull(a.errorSignal("server.host").get());
            Assert.assertEquals(0, a.errorCountSignal().get().intValue());
            Assert.assertFalse(a.hasErrorSignal().get().booleanValue());
            Assert.assertEquals(SaveFeedback.Status.NONE, a.saveFeedbackSignal().get().status());
            Assert.assertTrue("draft 仍脏", a.isDirtySignal().get().booleanValue());
            Assert.assertTrue(a.canSaveSignal().get().booleanValue());
            Assert.assertEquals("blocked.host", a.draft().getDraft("server.host"));

            // 改合法 host 后可保存
            a.onFieldEdit("server.host", "ok.host");
            s.__getRuntime().flush();
            s.__saveChanges();
            s.__getRuntime().flush();
            Assert.assertTrue(s.__getLastSaveOutcome().isSuccess());
            Assert.assertEquals(SaveFeedback.Status.OK, a.saveFeedbackSignal().get().status());
            Assert.assertEquals("ok.host", mgr.authority().getString("server.host"));
        } finally {
            s.dispose();
            a.dispose();
        }
    }

    /** validator 闭包改原 draft 后外层 INVALID，ConfigScreen 将全部 Signal 回读到实际编辑。 */
    @Test
    public void validatorDraftMutationInvalidResyncsAllUiSignals() throws Exception {
        File file = tempFolder.newFile("config-validator-resync.yaml");
        write(file, "");
        final AtomicReference<DraftBuffer> source = new AtomicReference<DraftBuffer>();
        ConfigManager mgr = ConfigManager.bootstrap(file, UiSchemaFactory.serverSchema(),
                new DraftValidator() {
                    @Override
                    public ValidationResult validate(DraftView view) {
                        source.get().setDraft("server.host", "validator.host");
                        source.get().setDraft("server.port", Double.valueOf(4321.0));
                        source.get().setDraft("server.debug", Boolean.TRUE);
                        source.get().setDraft("server.mode", "offline");
                        return ValidationResult.ok();
                    }
                });
        DraftBuffer d = mgr.openDraft();
        source.set(d);
        DraftSignalAdapter a = new DraftSignalAdapter(null, d);
        ConfigScreen s = new ConfigScreen(null, mgr, a, FieldRendererRegistry.defaultRegistry());
        try {
            a.onFieldEdit("server.host", "candidate.host");
            s.__getRuntime().flush();

            s.__saveChanges();
            s.__getRuntime().flush();

            Assert.assertEquals(SaveOutcome.Status.INVALID, s.__getLastSaveOutcome().status());
            for (club.heiqi.config.schema.FieldSpec field : d.schema().allFields()) {
                Assert.assertEquals("冲突后 Signal 应同步实际 draft: " + field.path(),
                        d.getDraft(field.path()), a.draftSignal(field.path()).get());
            }
            Assert.assertEquals("validator.host", a.draftSignal("server.host").get());
            Assert.assertEquals("localhost", mgr.authority().getString("server.host"));
        } finally {
            s.dispose();
            a.dispose();
        }
    }

    /** NUMBER 字符串成功保存后，afterSaveSync 回读规范化 Double 到 UI Signal。 */
    @Test
    public void numberStringSaveResyncsUiSignalToDouble() throws Exception {
        File file = tempFolder.newFile("config-number-resync.yaml");
        write(file, "");
        final AtomicReference<Object> validatorValue = new AtomicReference<Object>();
        ConfigManager mgr = ConfigManager.bootstrap(file, UiSchemaFactory.serverSchema(),
                view -> {
                    validatorValue.set(view.getDraft("server.port"));
                    return ValidationResult.ok();
                });
        DraftBuffer d = mgr.openDraft();
        DraftSignalAdapter a = new DraftSignalAdapter(null, d);
        ConfigScreen s = new ConfigScreen(null, mgr, a, FieldRendererRegistry.defaultRegistry());
        try {
            a.onFieldEdit("server.port", "3000.5");
            s.__getRuntime().flush();
            Assert.assertEquals("3000.5", a.draftSignal("server.port").get());

            s.__saveChanges();
            s.__getRuntime().flush();

            Assert.assertTrue(s.__getLastSaveOutcome().isSuccess());
            Assert.assertTrue(validatorValue.get() instanceof Double);
            Assert.assertEquals(Double.valueOf(3000.5), a.draftSignal("server.port").get());
            Assert.assertEquals(Double.valueOf(3000.5), d.getDraft("server.port"));
            Assert.assertEquals(Double.valueOf(3000.5), d.getCurrent("server.port"));
            Assert.assertTrue(mgr.authority().get("server.port") instanceof Double);
        } finally {
            s.dispose();
            a.dispose();
        }
    }
}
