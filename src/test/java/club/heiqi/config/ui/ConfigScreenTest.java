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
        // server schema 有 1 section，含 4 字段 → content 含 1 sectionNode
        Assert.assertEquals("content 含 1 section", 1, content.__getChildren().size());
        SceneNode sectionNode = content.__getChildren().get(0);
        // sectionNode 含 1 sectionTitle + 4 field cards = 5
        Assert.assertEquals("section 含 title + 4 fields", 5, sectionNode.__getChildren().size());
    }

    // ==================== 4. actionBar 含 3 按钮 ====================

    @Test
    public void actionBarContainsThreeButtons() throws Exception {
        SceneNode actionBar = screen.__getActionBar();
        Assert.assertNotNull("actionBar 非空", actionBar);
        Assert.assertEquals("actionBar 含 3 按钮", 3, actionBar.__getChildren().size());
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
        Assert.assertEquals("含 2 徽标（dirty + error）", 2, status.__getChildren().size());
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

    // ==================== 18. 多 section 渲染保序 ====================

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
        SceneNode content = s.__getContent();
        Assert.assertEquals("2 section 保序", 2, content.__getChildren().size());
        // 第一个 section title = Alpha
        SceneNode sec0 = content.__getChildren().get(0);
        Assert.assertEquals("第一个 section=Alpha", "Alpha", sec0.__getChildren().get(0).getText());
        SceneNode sec1 = content.__getChildren().get(1);
        Assert.assertEquals("第二个 section=Beta", "Beta", sec1.__getChildren().get(0).getText());
        s.dispose();
        a.dispose();
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
        SceneNode sectionNode = content.__getChildren().get(0);
        // 1 title + 10 fields = 11
        Assert.assertEquals("10 字段渲染不崩", 11, sectionNode.__getChildren().size());
        s.dispose();
        a.dispose();
    }
}
