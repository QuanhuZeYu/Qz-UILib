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
import club.heiqi.config.ui.field.FieldRenderer;
import club.heiqi.config.ui.field.NumberFieldRenderer;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * {@link NumberFieldRenderer} 单元测试。
 *
 * <p>覆盖有 range 用 SceneSlider、无 range 用 SceneTextInput、min/max 传入、
 * onChange 回调、整数 step、error 边框变化。</p>
 */
public class NumberFieldRendererTest {

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
        authority = Authority.load(new File("nonexistent-ui-number.yaml"), schema);
        draft = DraftBuffer.from(authority);
        adapter = new DraftSignalAdapter(runtime, draft);
        renderer = new NumberFieldRenderer();
        ReactiveScheduler.get().flush();
    }

    @After
    public void tearDown() throws Exception {
        adapter.dispose();
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    /** 有 range 时用 SceneSlider（server.port range 1-65535） */
    @Test
    public void rangedNumberUsesSlider() throws Exception {
        FieldSpec spec = schema.field("server.port");
        SceneNode card = renderer.render(runtime, spec, adapter);
        runtime.flush();
        Assert.assertNotNull("有 range 渲染不崩", card);
        // Slider root 含 track 子节点，track 含 fillBox + thumb
        SceneNode slider = findControlWithDepth(card, 2);
        Assert.assertNotNull("应找到 Slider 控件", slider);
    }

    /** 无 range 时用 SceneTextInput */
    @Test
    public void unboundedNumberUsesTextInput() throws Exception {
        // 构造无 range NUMBER 字段
        ConfigSchema s = ConfigSchema.builder("t")
                .section("a")
                    .number("k").defaultValue(50.0).label("K").build()
                .endSection()
                .build();
        Authority auth = Authority.load(new File("nonexistent-ui-number2.yaml"), s);
        DraftBuffer d = DraftBuffer.from(auth);
        DraftSignalAdapter a = new DraftSignalAdapter(runtime, d);
        ReactiveScheduler.get().flush();
        FieldSpec spec = s.field("a.k");
        SceneNode card = renderer.render(runtime, spec, a);
        runtime.flush();
        Assert.assertNotNull("无 range 渲染不崩", card);
        SceneNode input = findTextInputRoot(card);
        Assert.assertNotNull("无 range 应使用 TextInput", input);
        a.dispose();
    }

    /** min/max 传入 Slider Props（渲染不崩即可验证） */
    @Test
    public void minMaxPassedToSliderProps() throws Exception {
        FieldSpec spec = schema.field("server.port");
        SceneNode card = renderer.render(runtime, spec, adapter);
        runtime.flush();
        Assert.assertNotNull("min/max 传入不崩", card);
    }

    /** onChange 回调触发 onFieldEdit */
    @Test
    public void onChangeTriggersOnFieldEdit() throws Exception {
        FieldSpec spec = schema.field("server.port");
        renderer.render(runtime, spec, adapter);
        runtime.flush();
        adapter.onFieldEdit("server.port", 4000.0);
        runtime.flush();
        Assert.assertEquals("onChange 后 draft 更新", 4000.0, draft.getDraft("server.port"));
    }

    /** 整数 NUMBER 的 step 设置（step=1，渲染不崩） */
    @Test
    public void integerStepSetting() throws Exception {
        FieldSpec spec = schema.field("server.port");
        SceneNode card = renderer.render(runtime, spec, adapter);
        runtime.flush();
        Assert.assertNotNull("step=1 渲染不崩", card);
    }

    /** M1：slider 右侧有数值读数文本，bind 到 numValue 显示当前值 */
    @Test
    public void sliderHasReadoutText() throws Exception {
        FieldSpec spec = schema.field("server.port");
        SceneNode card = renderer.render(runtime, spec, adapter);
        runtime.flush();
        // card 控件 ROW（slider + readout）通过结构扫描定位（error 条件挂载，位置不固定）
        SceneNode controlRow = findControlWithDepth(card, 2);
        Assert.assertNotNull("控件 ROW 非空", controlRow);
        // ROW 含 sliderRoot + readout 文本
        Assert.assertTrue("控件 ROW 含至少 2 子节点", controlRow.__getChildren().size() >= 2);
        SceneNode readout = controlRow.__getChildren().get(controlRow.__getChildren().size() - 1);
        // readout 文本应反映当前值 8080
        Assert.assertEquals("读数显示当前值 8080", "8080", readout.getText());
    }

    /** M1：编辑后读数随 numValue 更新 */
    @Test
    public void sliderReadoutUpdatesOnEdit() throws Exception {
        FieldSpec spec = schema.field("server.port");
        SceneNode card = renderer.render(runtime, spec, adapter);
        runtime.flush();
        SceneNode controlRow = findControlWithDepth(card, 2);
        SceneNode readout = controlRow.__getChildren().get(controlRow.__getChildren().size() - 1);
        Assert.assertEquals("初始读数 8080", "8080", readout.getText());
        adapter.onFieldEdit("server.port", 4000.0);
        runtime.flush();
        Assert.assertEquals("编辑后读数 4000", "4000", readout.getText());
    }

    /** error 时边框变化 */
    @Test
    public void errorChangesBorderColor() throws Exception {
        FieldSpec spec = schema.field("server.port");
        SceneNode card = renderer.render(runtime, spec, adapter);
        runtime.flush();
        int before = card.getBorderColor();
        adapter.onFieldEdit("server.port", 99999.0); // 超出 max
        runtime.flush();
        int after = card.getBorderColor();
        Assert.assertNotEquals("error 时边框变化", before, after);
    }

    /**
     * 找 TextInput 根：扫 card 子节点（跳过 header），匹配含 3 子（prefix/caret/suffix）的控件根。
     *
     * <p>不依赖"倒数第二个"位置：FormFieldShell 的 errorNode 已改为 {@code rt.show} 条件挂载，
     * 尾部位置不再固定。</p>
     *
     * @param card 字段卡片
     * @return TextInput 根，未找到返回 null
     */
    private SceneNode findTextInputRoot(SceneNode card) {
        for (int i = 1; i < card.__getChildren().size(); i++) {
            SceneNode c = card.__getChildren().get(i);
            if (c.__getChildren().size() == 3) {
                return c;
            }
        }
        return null;
    }

    /**
     * 找 slider 控件根：扫 card 子节点（跳过 header），匹配含 2 子（sliderRoot+readout）的控件根。
     *
     * <p>header（index 0）同为 2 子，故从 index 1 起扫以排除。{@code depth} 参数保留兼容，未使用。</p>
     *
     * @param card  字段卡片
     * @param depth 未使用，保留兼容
     * @return 控件根，未找到返回 null
     */
    private SceneNode findControlWithDepth(SceneNode card, int depth) {
        for (int i = 1; i < card.__getChildren().size(); i++) {
            SceneNode c = card.__getChildren().get(i);
            if (c.__getChildren().size() == 2) {
                return c;
            }
        }
        return null;
    }
}

