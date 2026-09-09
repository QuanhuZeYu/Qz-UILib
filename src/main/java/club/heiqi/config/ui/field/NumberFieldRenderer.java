package club.heiqi.config.ui.field;

import java.util.function.Supplier;

import club.heiqi.config.schema.FieldConstraints;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.schema.SliderSpec;
import club.heiqi.config.schema.WidgetSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.theme.ConfigTheme;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.control.SceneInputType;
import club.heiqi.uilib.ui.scene.control.SceneSlider;
import club.heiqi.uilib.ui.scene.control.SceneTextInput;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * NUMBER 字段渲染器：按 {@link WidgetSpec} 声明分发——
 * {@link SliderSpec} 用 {@link SceneSlider}，{@code null} 或 InputSpec 用 {@link SceneTextInput}。
 *
 * <p>有 range 且声明 slider 时 value 由 draftSignal 经 {@link FieldRenderSupport#toDoubleSignal} 转 Double，
 * onChange 调 {@link DraftSignalAdapter#onFieldEdit} 写回 Double，
 * step 由 {@link SliderSpec#step()} 透传（&le;0 表示连续不量化）。</p>
 *
 * <p>未声明 slider（widget=null 或 InputSpec）时走文本输入框，
 * value 转 String 显示（经 {@link FieldRenderSupport#toNumberStringSignal}），
 * onChange 把 String parse 为 Double 写回
 * （parse 失败时存原始 String，让 DraftBuffer 校验报"不是有效数字"）。
 * 外壳装配经 {@link FieldShellBinder#build} 收口，标题回退经 {@link FieldRenderSupport#labelOf}。</p>
 *
 * <p><b>G15/Number 外观口径</b>（契约 §4/§4.1/§4.2）：字段卡片表面与 dirty/error 语义色由
 * {@link FieldShellBinder} 下沉的 FormFieldShell theme-aware 路径派生（GROUP 角色），本类不复制；
 * {@code SceneSlider}（G06）与 {@code SceneTextInput}（G04）控件本体已默认消费
 * {@code SceneThemes}/{@code SceneSurfaceBinder}，本类只组 Props、不再叠第二层表面或边框。
 * slider 读数文本前景取 {@link SceneThemes#foreground(SceneRuntime)} 主题信号（构建期捕获、
 * effect 内应用，无 {@code .get()} 快照）；字号与行间距是布局/排版常量（契约 §4「padding/尺寸/
 * 布局属性归控件自身，主题不接管布局」），保留 {@code ConfigTheme} 取值。喂给 binder 的
 * {@code ConfigTheme.asFormTheme()} 是兼容占位形参（默认路径不消费，见 FieldShellBinder 类头），
 * 待全部 Renderer 实例迁移完成后由主代理统一收口删除，本实例不自行摘除。
 * 数值解析、min/max 钳制、step 量化、滚轮步进与 dirty/error 行为零改动。</p>
 */
public final class NumberFieldRenderer implements FieldRenderer {

    /** 纯静态工厂语义，但实现接口需实例化；无实例字段 */
    public NumberFieldRenderer() {
    }

    @Override
    public SceneNode render(SceneRuntime rt, FieldSpec spec, DraftSignalAdapter adapter) {
        WidgetSpec w = spec.widget();
        if (w instanceof SliderSpec) {
            SliderSpec s = (SliderSpec) w;
            FieldConstraints c = spec.constraints();
            double min = c != null ? c.min() : Double.NEGATIVE_INFINITY;
            double max = c != null ? c.max() : Double.POSITIVE_INFINITY;
            return renderSlider(rt, spec, adapter, min, max, s.step());
        }
        // w == null 或 InputSpec → input
        return renderTextInput(rt, spec, adapter);
    }

    /**
     * 有 range：用 SceneSlider + 右侧数值读数（M1）。
     *
     * <p>SceneSlider 不自带读数显示，故在 FormFieldShell 控件槽用 ROW 包 slider + 读数文本，
     * 读数文本 bind 到 numValue signal 显示当前值。</p>
     *
     * @param rt      场景运行时
     * @param spec    字段元数据
     * @param adapter 草稿适配器
     * @param min     最小值
     * @param max     最大值
     * @param step    量化步进，&le;0 表示连续不量化
     * @return 字段卡片节点
     */
    private SceneNode renderSlider(SceneRuntime rt, FieldSpec spec, DraftSignalAdapter adapter,
                                   double min, double max, double step) {
        final String path = spec.path();
        final ReadableSignal<Object> draftSig = adapter.draftSignal(path);

        ReadableSignal<Double> numValue = FieldRenderSupport.toDoubleSignal(draftSig);
        // step 由 SliderSpec 透传，<=0 表示连续不量化
        SceneSlider.Props props = SceneSlider.Props.builder(numValue)
                .min(min).max(max).step(step)
                .onChange((value, committing) -> adapter.onFieldEdit(path, Double.valueOf(value)))
                .build();

        // M1：slider + 读数文本 ROW 包装
        Supplier<SceneNode> control = () -> {
            SceneNode row = SceneNode.row();
            // 间距为布局常量（契约 §4：主题不接管布局），非外观写入点，保留 ConfigTheme 取值
            row.setGap(ConfigTheme.FIELD_GAP);
            row.setCrossAxisAlign(CrossAxisAlign.CENTER);
            // slider 子树（mount 后由 SceneSlider.create 产出）
            SceneNode sliderRoot = SceneSlider.create(rt, props).get();
            row.appendChild(sliderRoot);
            // 读数文本：bind 到 numValue，显示当前值（整数显示去 .0）
            SceneNode readout = new SceneNode();
            // 前景取来源主题 foreground 信号（契约 §4 textColor 唯一写入者 = 主题前景绑定；
            // 构建期捕获信号、effect 内应用，无构造期 .get() 快照，替换旧静态 TEXT_COLOR）
            rt.bind(SceneThemes.foreground(rt), readout::setTextColor);
            // 字号是排版/布局常量（契约 §4：尺寸/布局属性归控件自身，主题不接管布局），保留 ConfigTheme 取值
            readout.setFontSize(ConfigTheme.FONT_READOUT);
            readout.setHitTestable(false);
            rt.bindComputed(() -> FieldRenderSupport.formatReadout(numValue.get()),
                    readout::setText);
            row.appendChild(readout);
            return row;
        };

        return FieldShellBinder.build(rt, spec, adapter, control, ConfigTheme.asFormTheme());
    }

    /**
     * 无 range：用 SceneTextInput（NUMBER 输入类型）。
     *
     * @param rt      场景运行时
     * @param spec    字段元数据
     * @param adapter 草稿适配器
     * @return 字段卡片节点
     */
    private SceneNode renderTextInput(SceneRuntime rt, FieldSpec spec, DraftSignalAdapter adapter) {
        final String path = spec.path();
        final ReadableSignal<Object> draftSig = adapter.draftSignal(path);

        ReadableSignal<String> stringValue = FieldRenderSupport.toNumberStringSignal(draftSig);

        SceneTextInput.Props props = new SceneTextInput.Props(
                stringValue,
                Signal.create(Boolean.TRUE),
                Signal.create(Boolean.FALSE),
                "",
                Integer.MAX_VALUE,
                SceneInputType.NUMBER,
                next -> {
                    try {
                        adapter.onFieldEdit(path, Double.valueOf(Double.parseDouble(next)));
                    } catch (NumberFormatException e) {
                        // parse 失败存原始 String，让 DraftBuffer 校验报"不是有效数字"
                        adapter.onFieldEdit(path, next);
                    }
                });

        return FieldShellBinder.build(rt, spec, adapter,
                SceneTextInput.create(rt, props), ConfigTheme.asFormTheme());
    }
}
