package club.heiqi.config.ui;

import java.io.File;
import java.io.FileWriter;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.runtime.SaveOutcome;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.ui.field.FieldRendererRegistry;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.layout.Constraints;
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
     * actionBar 应在 scrollContainer 外侧（root 最后一个子），固定底部，不进滚动容器。
     */
    @Test
    public void actionBarOutsideScrollContainerAtBottom() throws Exception {
        SceneNode root = screen.__getRoot();
        SceneNode lastChild = root.__getChildren().get(root.__getChildren().size() - 1);
        Assert.assertSame("root 最后一个子是 actionBar", screen.__getActionBar(), lastChild);
        Assert.assertNotSame("actionBar 不是 scrollContainer",
                screen.__getScrollContainer(), lastChild);
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
        // root 子节点：titleBar / statusSummary / scrollContainer / [anchor] / actionBar
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
        // root 中应能找到含"已保存"的节点（反馈行内文本）
        boolean found = false;
        for (SceneNode child : screen.__getRoot().__getChildren()) {
            if (child.getText() != null && child.getText().contains("已保存")) {
                found = true;
                break;
            }
            // 反馈行是 ROW，文本在子节点
            for (SceneNode grand : child.__getChildren()) {
                if (grand.getText() != null && grand.getText().contains("已保存")) {
                    found = true;
                    break;
                }
            }
            if (found) break;
        }
        Assert.assertTrue("save 成功后反馈行显示「已保存」", found);
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
}
