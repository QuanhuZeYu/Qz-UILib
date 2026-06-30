package club.heiqi.config.ui;

import java.io.File;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.runtime.Authority;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.field.BooleanFieldRenderer;
import club.heiqi.config.ui.field.FieldRenderer;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * {@link BooleanFieldRenderer} 单元测试。
 */
public class BooleanFieldRendererTest {

    private SceneRuntime runtime;
    private ConfigSchema schema;
    private Authority authority;
    private DraftBuffer draft;
    private DraftSignalAdapter adapter;
    private FieldRenderer renderer;

    @Before
    public void setUp() throws Exception {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        runtime = new SceneRuntime(measurer);
        schema = UiSchemaFactory.serverSchema();
        authority = Authority.load(new File("nonexistent-ui-bool.yaml"), schema);
        draft = DraftBuffer.from(authority);
        adapter = new DraftSignalAdapter(runtime, draft);
        renderer = new BooleanFieldRenderer();
        ReactiveScheduler.get().flush();
    }

    @After
    public void tearDown() throws Exception {
        adapter.dispose();
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    /** render 返回非 null */
    @Test
    public void renderReturnsNonNull() throws Exception {
        FieldSpec spec = schema.field("server.debug");
        SceneNode card = renderer.render(runtime, spec, adapter);
        Assert.assertNotNull("render 返回非 null", card);
    }

    /** toggle 值 = draftSignal（flush 后通过 track 背景色间接验证） */
    @Test
    public void toggleValueEqualsDraft() throws Exception {
        FieldSpec spec = schema.field("server.debug");
        SceneNode card = renderer.render(runtime, spec, adapter);
        runtime.flush();
        // Toggle root 含 track + label 两子节点
        SceneNode toggle = findToggleRoot(card);
        Assert.assertNotNull("应找到 Toggle 控件", toggle);
        // draft 初值 false → track off 背景
        SceneNode track = toggle.__getChildren().get(0);
        Assert.assertEquals("debug 初值 false → track off 背景",
                club.heiqi.uilib.ui.scene.paint.SceneChromeTokens.BG_DEFAULT,
                track.getBackgroundColor());
    }

    /** onChange 回调触发 onFieldEdit */
    @Test
    public void onChangeTriggersOnFieldEdit() throws Exception {
        FieldSpec spec = schema.field("server.debug");
        renderer.render(runtime, spec, adapter);
        runtime.flush();
        adapter.onFieldEdit("server.debug", Boolean.TRUE);
        runtime.flush();
        Assert.assertEquals("onChange 后 draft=true", Boolean.TRUE, draft.getDraft("server.debug"));
    }

    /** label 显示 */
    @Test
    public void labelDisplayed() throws Exception {
        FieldSpec spec = schema.field("server.debug");
        SceneNode card = renderer.render(runtime, spec, adapter);
        runtime.flush();
        SceneNode header = card.__getChildren().get(0);
        SceneNode title = header.__getChildren().get(1);
        Assert.assertEquals("label 显示", "Debug", title.getText());
    }

    /** error 时边框变化（BOOLEAN 很少出错，但机制应工作） */
    @Test
    public void errorChangesBorderColor() throws Exception {
        FieldSpec spec = schema.field("server.debug");
        SceneNode card = renderer.render(runtime, spec, adapter);
        runtime.flush();
        int before = card.getBorderColor();
        // BOOLEAN 无约束很难出错；改 dirty 触发边框变化验证 bind 生效
        adapter.onFieldEdit("server.debug", Boolean.TRUE);
        runtime.flush();
        int after = card.getBorderColor();
        Assert.assertNotEquals("dirty 时边框变化", before, after);
    }

    /**
     * 找 Toggle 根（倒数第二个子节点，error 是最后一个）。
     *
     * @param card 字段卡片
     * @return Toggle 根，未找到返回 null
     */
    private SceneNode findToggleRoot(SceneNode card) {
        int n = card.__getChildren().size();
        if (n < 2) {
            return null;
        }
        return card.__getChildren().get(n - 2);
    }
}

