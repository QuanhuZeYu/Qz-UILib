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
 * {@link NumberFieldRenderer} 的 WidgetSpec 分发行为测试。
 *
 * <p>验证方案 D：widget=null 或 InputSpec → 文本输入框；
 * widget=SliderSpec → slider 控件；step 透传渲染不崩。</p>
 */
public class NumberFieldRendererWidgetTest {

    private SceneRuntime runtime;
    private FieldRenderer renderer;

    @Before
    public void setUp() throws Exception {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        runtime = new SceneRuntime(measurer);
        renderer = new NumberFieldRenderer();
        ReactiveScheduler.get().flush();
    }

    @After
    public void tearDown() throws Exception {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    /** widget=null + 有 range → 走 input（方案 D：有 range 不再自动 slider）。 */
    @Test
    public void nullWidgetWithRangeUsesInput() throws Exception {
        ConfigSchema s = ConfigSchema.builder("t")
                .section("a")
                    .number("k").defaultValue(5.0).range(0, 100).build()
                .endSection()
                .build();
        Authority auth = Authority.load(new File("nonexistent-widget-null.yaml"), s);
        DraftBuffer d = DraftBuffer.from(auth);
        DraftSignalAdapter a = new DraftSignalAdapter(runtime, d);
        ReactiveScheduler.get().flush();
        FieldSpec spec = s.field("a.k");
        SceneNode card = renderer.render(runtime, spec, a);
        runtime.flush();
        Assert.assertNotNull("widget=null + range 渲染不崩", card);
        SceneNode input = findTextInputRoot(card);
        Assert.assertNotNull("widget=null + range 应走 TextInput", input);
        a.dispose();
    }

    /** .input() 显式声明 + 有 range → 走 input。 */
    @Test
    public void inputSpecWithRangeUsesInput() throws Exception {
        ConfigSchema s = ConfigSchema.builder("t")
                .section("a")
                    .number("k").defaultValue(5.0).range(0, 100).input().build()
                .endSection()
                .build();
        Authority auth = Authority.load(new File("nonexistent-widget-input.yaml"), s);
        DraftBuffer d = DraftBuffer.from(auth);
        DraftSignalAdapter a = new DraftSignalAdapter(runtime, d);
        ReactiveScheduler.get().flush();
        FieldSpec spec = s.field("a.k");
        SceneNode card = renderer.render(runtime, spec, a);
        runtime.flush();
        Assert.assertNotNull(".input() + range 渲染不崩", card);
        SceneNode input = findTextInputRoot(card);
        Assert.assertNotNull(".input() + range 应走 TextInput", input);
        a.dispose();
    }

    /** .slider() 声明 → 走 slider 控件。 */
    @Test
    public void sliderSpecUsesSlider() throws Exception {
        ConfigSchema s = ConfigSchema.builder("t")
                .section("a")
                    .number("k").defaultValue(5.0).range(0, 100).slider().build()
                .endSection()
                .build();
        Authority auth = Authority.load(new File("nonexistent-widget-slider.yaml"), s);
        DraftBuffer d = DraftBuffer.from(auth);
        DraftSignalAdapter a = new DraftSignalAdapter(runtime, d);
        ReactiveScheduler.get().flush();
        FieldSpec spec = s.field("a.k");
        SceneNode card = renderer.render(runtime, spec, a);
        runtime.flush();
        Assert.assertNotNull(".slider() 渲染不崩", card);
        SceneNode slider = findControlRoot(card);
        Assert.assertNotNull(".slider() 应产出 slider 控件", slider);
        a.dispose();
    }

    /** .slider(0.5) 量化步进透传渲染不崩。 */
    @Test
    public void sliderWithStepRendersWithoutCrash() throws Exception {
        ConfigSchema s = ConfigSchema.builder("t")
                .section("a")
                    .number("k").defaultValue(0.5).range(0, 1).slider(0.5).build()
                .endSection()
                .build();
        Authority auth = Authority.load(new File("nonexistent-widget-slider-step.yaml"), s);
        DraftBuffer d = DraftBuffer.from(auth);
        DraftSignalAdapter a = new DraftSignalAdapter(runtime, d);
        ReactiveScheduler.get().flush();
        FieldSpec spec = s.field("a.k");
        SceneNode card = renderer.render(runtime, spec, a);
        runtime.flush();
        Assert.assertNotNull(".slider(0.5) 渲染不崩", card);
        SceneNode slider = findControlRoot(card);
        Assert.assertNotNull(".slider(0.5) 应产出 slider 控件", slider);
        a.dispose();
    }

    /** .slider() 无 range 仍走 slider（min/max 为无穷）。 */
    @Test
    public void sliderWithoutRangeRendersWithoutCrash() throws Exception {
        ConfigSchema s = ConfigSchema.builder("t")
                .section("a")
                    .number("k").defaultValue(5.0).slider().build()
                .endSection()
                .build();
        Authority auth = Authority.load(new File("nonexistent-widget-slider-norange.yaml"), s);
        DraftBuffer d = DraftBuffer.from(auth);
        DraftSignalAdapter a = new DraftSignalAdapter(runtime, d);
        ReactiveScheduler.get().flush();
        FieldSpec spec = s.field("a.k");
        SceneNode card = renderer.render(runtime, spec, a);
        runtime.flush();
        Assert.assertNotNull(".slider() 无 range 渲染不崩", card);
        a.dispose();
    }

    /**
     * 找 TextInput 根（card 倒数第二个子节点，且控件 root 子节点数为 3）。
     *
     * <p>结构依据：{@code SceneTextInput.create} 产出的 root 是 ROW，
     * 含 3 子（prefixText、caret、suffixText，见 {@code SceneTextInputPrimitive.create}）。
     * slider 的 control root 是 ROW 含 2 子（sliderRoot、readout），故用子节点数区分，
     * 避免两者实现相同导致误匹配。</p>
     *
     * @param card 字段卡片
     * @return TextInput 根，未找到或结构不匹配返回 null
     */
    private SceneNode findTextInputRoot(SceneNode card) {
        int n = card.__getChildren().size();
        if (n < 2) {
            return null;
        }
        SceneNode control = card.__getChildren().get(n - 2);
        // TextInput root 含 3 子（prefixText/caret/suffixText），slider control root 含 2 子
        if (control.__getChildren().size() != 3) {
            return null;
        }
        return control;
    }

    /**
     * 找 slider 控件根（card 倒数第二个子节点，且控件 root 子节点数为 2）。
     *
     * <p>结构依据：{@code NumberFieldRenderer.renderSlider} 的 control 是 ROW，
     * 含 2 子（sliderRoot、readout）。TextInput root 含 3 子（prefixText/caret/suffixText），
     * 故用子节点数区分，避免两者实现相同导致误匹配。</p>
     *
     * @param card 字段卡片
     * @return slider 控件根，未找到或结构不匹配返回 null
     */
    private SceneNode findControlRoot(SceneNode card) {
        int n = card.__getChildren().size();
        if (n < 2) {
            return null;
        }
        SceneNode control = card.__getChildren().get(n - 2);
        // slider control root 含 2 子（sliderRoot/readout），TextInput root 含 3 子
        if (control.__getChildren().size() != 2) {
            return null;
        }
        return control;
    }
}
