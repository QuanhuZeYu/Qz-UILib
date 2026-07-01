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
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 端到端 UI 集成测试：bootstrap → openDraft → 建 adapter → 建 screen → 编辑 → 保存 → 验证。
 *
 * <p>覆盖完整配置编辑流程、取消回滚、非法值阻断、恢复默认、保存后重开。</p>
 */
public class EndToEndUITest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private ConfigManager manager;
    private DraftSignalAdapter adapter;
    private ConfigScreen screen;

    @Before
    public void setUp() throws Exception {
        ReactiveScheduler.get().reset();
        File file = tempFolder.newFile("config-e2e.yaml");
        write(file, "");
        ConfigSchema schema = UiSchemaFactory.serverSchema();
        manager = ConfigManager.bootstrap(file, schema);
        DraftBuffer draft = manager.openDraft();
        adapter = new DraftSignalAdapter(null, draft);
        FieldRendererRegistry registry = FieldRendererRegistry.defaultRegistry();
        screen = new ConfigScreen(null, manager, adapter, registry);
    }

    @After
    public void tearDown() {
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

    /** 1. 完整流程：编辑→保存→authority 更新 */
    @Test
    public void fullFlowEditSaveAuthorityUpdated() {
        adapter.onFieldEdit("server.host", "new.host");
        adapter.onFieldEdit("server.port", 4000.0);
        screen.__getRuntime().flush();
        Assert.assertTrue("canSave=true", adapter.canSaveSignal().get().booleanValue());
        screen.__saveChanges();
        Assert.assertTrue("保存成功", screen.__getLastSaveOutcome().isSuccess());
        screen.__getRuntime().flush();
        Assert.assertEquals("authority host 更新", "new.host",
                manager.authority().getString("server.host"));
        Assert.assertEquals("authority port 更新", 4000.0,
                manager.authority().getNumber("server.port"), 0.001);
    }

    /** 2. 编辑后取消→draftSignal 回到 current */
    @Test
    public void cancelRestoresDraftToCurrent() {
        adapter.onFieldEdit("server.host", "temp.host");
        screen.__getRuntime().flush();
        Assert.assertEquals("编辑后 draft=temp", "temp.host",
                adapter.draftSignal("server.host").get());
        screen.__cancelChanges();
        screen.__getRuntime().flush();
        Assert.assertEquals("取消后 draft=current", "localhost",
                adapter.draftSignal("server.host").get());
        Assert.assertFalse("取消后 dirty=false",
                adapter.dirtySignal("server.host").get().booleanValue());
    }

    /** 3. 编辑非法值→canSave=false + error 显示 */
    @Test
    public void invalidValueBlocksSaveAndShowsError() {
        adapter.onFieldEdit("server.port", 99999.0);
        screen.__getRuntime().flush();
        Assert.assertFalse("非法值 canSave=false",
                adapter.canSaveSignal().get().booleanValue());
        String err = adapter.errorSignal("server.port").get();
        Assert.assertNotNull("error 显示", err);
        Assert.assertTrue("error 含上限信息", err.contains("上限"));
    }

    /** 4. 恢复默认→draftSignal=default + dirty=true */
    @Test
    public void restoreDefaultsSetsDraftToDefaultAndDirty() {
        // 先改 current
        adapter.onFieldEdit("server.host", "current.host");
        screen.__getRuntime().flush();
        screen.__saveChanges();
        screen.__getRuntime().flush();
        // 恢复默认
        screen.__restoreDefaults();
        screen.__getRuntime().flush();
        Assert.assertEquals("draftSignal=default", "localhost",
                adapter.draftSignal("server.host").get());
        Assert.assertTrue("default != current → dirty=true",
                adapter.dirtySignal("server.host").get().booleanValue());
    }

    /** 5. 保存成功后→afterSaveSync→dirty=false */
    @Test
    public void afterSaveSyncClearsDirty() {
        adapter.onFieldEdit("server.host", "saved.host");
        screen.__getRuntime().flush();
        Assert.assertTrue("保存前 dirty=true",
                adapter.dirtySignal("server.host").get().booleanValue());
        screen.__saveChanges();
        screen.__getRuntime().flush();
        Assert.assertFalse("保存后 dirty=false",
                adapter.dirtySignal("server.host").get().booleanValue());
        Assert.assertFalse("保存后 isDirty=false",
                adapter.isDirtySignal().get().booleanValue());
    }

    /** 6. 多字段编辑→只有改动的字段 dirty */
    @Test
    public void onlyEditedFieldsAreDirty() {
        adapter.onFieldEdit("server.host", "edited");
        screen.__getRuntime().flush();
        Assert.assertTrue("host dirty", adapter.dirtySignal("server.host").get().booleanValue());
        Assert.assertFalse("port 未编辑不 dirty",
                adapter.dirtySignal("server.port").get().booleanValue());
        Assert.assertFalse("debug 未编辑不 dirty",
                adapter.dirtySignal("server.debug").get().booleanValue());
        Assert.assertFalse("mode 未编辑不 dirty",
                adapter.dirtySignal("server.mode").get().booleanValue());
    }

    /** 7. Schema 含 4 种字段类型全渲染+全编辑 */
    @Test
    public void allFourTypesRenderAndEdit() {
        // screen 已渲染 4 字段（setUp）
        SceneNode content = screen.__getContent();
        Assert.assertEquals("4 字段全渲染", 5, content.__getChildren().get(0).__getChildren().size());
        // 全编辑
        adapter.onFieldEdit("server.host", "h");
        adapter.onFieldEdit("server.port", 2000.0);
        adapter.onFieldEdit("server.debug", Boolean.TRUE);
        adapter.onFieldEdit("server.mode", "test");
        screen.__getRuntime().flush();
        Assert.assertTrue("全编辑后 isDirty=true",
                adapter.isDirtySignal().get().booleanValue());
        Assert.assertTrue("canSave=true（全合法）",
                adapter.canSaveSignal().get().booleanValue());
        screen.__saveChanges();
        Assert.assertTrue("全保存成功", screen.__getLastSaveOutcome().isSuccess());
    }

    /** 8. 保存后重新 openDraft→current=保存后的值 */
    @Test
    public void reopenDraftAfterSaveReflectsNewValues() {
        adapter.onFieldEdit("server.host", "persisted.host");
        adapter.onFieldEdit("server.port", 7000.0);
        screen.__getRuntime().flush();
        screen.__saveChanges();
        screen.__getRuntime().flush();
        // 重新 openDraft
        DraftBuffer newDraft = manager.openDraft();
        Assert.assertEquals("重开 draft host=保存值", "persisted.host",
                newDraft.getCurrent("server.host"));
        Assert.assertEquals("重开 draft port=保存值", 7000.0,
                newDraft.getCurrent("server.port"));
    }
}
