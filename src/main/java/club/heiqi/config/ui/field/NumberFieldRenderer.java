package club.heiqi.config.ui.field;

import club.heiqi.config.schema.FieldConstraints;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.control.SceneInputType;
import club.heiqi.uilib.ui.scene.control.SceneSlider;
import club.heiqi.uilib.ui.scene.control.SceneSliderPrimitive;
import club.heiqi.uilib.ui.scene.control.SceneTextInput;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * NUMBER 字段渲染器：有 range 用 {@link SceneSlider}，无 range 用 {@link SceneTextInput}（数值文本框）。
 *
 * <p>有 range 时 value 由 draftSignal 经 {@link Computed} 转 Double，onChange 调
 * {@link DraftSignalAdapter#onFieldEdit} 写回 Double。</p>
 *
 * <p>无 range 时 value 转 String 显示，onChange 把 String parse 为 Double 写回
 * （parse 失败时存原始 String，让 DraftBuffer 校验报"不是有效数字"）。</p>
 */
public final class NumberFieldRenderer implements FieldRenderer {

    /** 纯静态工厂语义，但实现接口需实例化；无实例字段 */
    public NumberFieldRenderer() {
    }

    @Override
    public SceneNode render(SceneRuntime rt, FieldSpec spec, DraftSignalAdapter adapter) {
        FieldConstraints c = spec.constraints();
        boolean hasRange = c != null
                && c.min() != Double.NEGATIVE_INFINITY
                && c.max() != Double.POSITIVE_INFINITY;
        if (hasRange) {
            return renderSlider(rt, spec, adapter, c.min(), c.max());
        }
        return renderTextInput(rt, spec, adapter);
    }

    /**
     * 有 range：用 SceneSlider。
     *
     * @param rt      场景运行时
     * @param spec    字段元数据
     * @param adapter 草稿适配器
     * @param min     最小值
     * @param max     最大值
     * @return 字段卡片节点
     */
    private SceneNode renderSlider(SceneRuntime rt, FieldSpec spec, DraftSignalAdapter adapter,
                                   double min, double max) {
        final String path = spec.path();
        final ReadableSignal<Object> draftSig = adapter.draftSignal(path);

        ReadableSignal<Double> numValue = Computed.create(() -> toDouble(draftSig.get()));
        // step=1 离散量化（P1 简化：整数语义）
        SceneSlider.Props props = SceneSlider.Props.builder(numValue)
                .min(min).max(max).step(1.0)
                .onChange((value, committing) -> adapter.onFieldEdit(path, Double.valueOf(value)))
                .build();

        return FieldShell.build(rt, spec, adapter, SceneSlider.create(rt, props));
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
                double d = ((Number) v).doubleValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    return Long.toString((long) d);
                }
                return Double.toString(d);
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
