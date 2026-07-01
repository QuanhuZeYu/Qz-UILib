package club.heiqi.config.ui.field;

import java.util.function.Supplier;

import club.heiqi.config.schema.FieldConstraints;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.schema.SliderSpec;
import club.heiqi.config.schema.WidgetSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.theme.ConfigTheme;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.control.SceneInputType;
import club.heiqi.uilib.ui.scene.control.SceneSlider;
import club.heiqi.uilib.ui.scene.control.SceneSliderPrimitive;
import club.heiqi.uilib.ui.scene.control.SceneTextInput;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * NUMBER 字段渲染器：按 {@link WidgetSpec} 声明分发——
 * {@link SliderSpec} 用 {@link SceneSlider}，{@code null} 或 InputSpec 用 {@link SceneTextInput}。
 *
 * <p>有 range 且声明 slider 时 value 由 draftSignal 经 {@link Computed} 转 Double，
 * onChange 调 {@link DraftSignalAdapter#onFieldEdit} 写回 Double，
 * step 由 {@link SliderSpec#step()} 透传（&le;0 表示连续不量化）。</p>
 *
 * <p>未声明 slider（widget=null 或 InputSpec）时走文本输入框，
 * value 转 String 显示，onChange 把 String parse 为 Double 写回
 * （parse 失败时存原始 String，让 DraftBuffer 校验报"不是有效数字"）。</p>
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
     * <p>SceneSlider 不自带读数显示，故在 FieldShell 控件槽用 ROW 包 slider + 读数文本，
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

        ReadableSignal<Double> numValue = Computed.create(() -> toDouble(draftSig.get()));
        // step 由 SliderSpec 透传，<=0 表示连续不量化
        SceneSlider.Props props = SceneSlider.Props.builder(numValue)
                .min(min).max(max).step(step)
                .onChange((value, committing) -> adapter.onFieldEdit(path, Double.valueOf(value)))
                .build();

        // M1：slider + 读数文本 ROW 包装
        Supplier<SceneNode> control = () -> {
            SceneNode row = SceneNode.row();
            row.setGap(ConfigTheme.FIELD_GAP);
            row.setCrossAxisAlign(CrossAxisAlign.CENTER);
            // slider 子树（mount 后由 SceneSlider.create 产出）
            SceneNode sliderRoot = SceneSlider.create(rt, props).get();
            row.appendChild(sliderRoot);
            // 读数文本：bind 到 numValue，显示当前值（整数显示去 .0）
            SceneNode readout = new SceneNode();
            readout.setTextColor(ConfigTheme.TEXT_COLOR);
            readout.setFontSize(ConfigTheme.FONT_READOUT);
            readout.setHitTestable(false);
            rt.bind(Computed.create(() -> formatReadout(numValue.get())),
                    readout::setText);
            row.appendChild(readout);
            return row;
        };

        return FieldShell.build(rt, spec, adapter, control);
    }

    /**
     * 格式化 slider 读数：整数去 .0，浮点保留原值。
     *
     * @param v 当前值
     * @return 读数字符串
     */
    private static String formatReadout(double v) {
        if (v == Math.floor(v) && !Double.isInfinite(v)) {
            return Long.toString((long) v);
        }
        return Double.toString(v);
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

        ReadableSignal<String> stringValue = Computed.create(() -> {
            Object v = draftSig.get();
            if (v == null) {
                return "";
            }
            if (v instanceof Number) {
                return formatReadout(((Number) v).doubleValue());
            }
            return String.valueOf(v);
        });

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

        return FieldShell.build(rt, spec, adapter, SceneTextInput.create(rt, props));
    }

    /**
     * 把 Object 安全转为 double。
     *
     * @param v 原始值
     * @return double 值，无法解析返回 0.0
     */
    private static double toDouble(Object v) {
        if (v instanceof Number) {
            return ((Number) v).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
