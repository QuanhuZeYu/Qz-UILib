package club.heiqi.config.ui.field;

import java.util.function.Supplier;

import club.heiqi.config.schema.FieldConstraints;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.schema.IntegerCodec;
import club.heiqi.config.schema.SliderSpec;
import club.heiqi.config.schema.WidgetSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.theme.ConfigTheme;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneInputType;
import club.heiqi.uilib.ui.scene.control.SceneSlider;
import club.heiqi.uilib.ui.scene.control.SceneTextInput;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * INTEGER 字段渲染器：按 {@link WidgetSpec} 声明分发——{@link SliderSpec} 用 {@link SceneSlider}，
 * {@code null} 或 {@code InputSpec} 用 {@link SceneTextInput}（整数文本形态）。
 *
 * <p><b>为什么不像颜色那样复用 {@link NumberFieldRenderer}</b>：颜色的值与校验就是 NUMBER，
 * 控件形态是唯一差别；整数不是——写回值必须是 {@link Long}（{@code Double} 落盘必然带小数点或
 * 指数），范围校验必须按整数判读（{@code 1.5} 是错而不是可四舍五入的值），显示文本必须无
 * {@code .0}。三者都落在值语义上，故独立一个渲染器；共用的部分（外壳装配、编辑期原文机制、
 * 读数格式化）继续走 {@link FieldShellBinder} / {@link FieldRenderSupport}，不复制第二套。</p>
 *
 * <h3>编辑期原文与写回</h3>
 * <p>文本输入框沿用 NUMBER 的编辑期原文机制（受控控件的「值 ↔ 文本」有损）：原文仍是当前值的
 * 未完成写法时显示原文，失焦归位规范写法；写回走 parse-then-writeback，读不出整数的原文按原样
 * 存进草稿（{@link IntegerCodec#parse(String)} 返回 {@code null} ⇒ 存原文），由 {@code DraftBuffer}
 * 报「值不是有效整数」——不静默取整、不夹取、不回落默认值。</p>
 */
public final class IntegerFieldRenderer implements FieldRenderer {

    /** 纯静态工厂语义，但实现接口需实例化；无实例字段。 */
    public IntegerFieldRenderer() {
    }

    @Override
    public SceneNode render(SceneRuntime rt, FieldSpec spec, DraftSignalAdapter adapter) {
        WidgetSpec w = spec.widget();
        if (w instanceof SliderSpec) {
            FieldConstraints c = spec.constraints();
            double min = c != null ? c.min() : Double.NEGATIVE_INFINITY;
            double max = c != null ? c.max() : Double.POSITIVE_INFINITY;
            return renderSlider(rt, spec, adapter, min, max, ((SliderSpec) w).step());
        }
        // w == null 或 InputSpec → input；ColorSpec 只对 NUMBER 声明（FieldSpec.Builder#color 在 INTEGER 上已拒绝）
        return renderTextInput(rt, spec, adapter);
    }

    /**
     * 声明了 range：用 SceneSlider + 右侧整数读数。
     *
     * <p>slider 的 value 域是 double（控件契约），写回时按 {@link Math#round(double)} 收敛为
     * {@link Long}；读数直接从草稿值派生（{@link FieldRenderSupport#integerTextOf}），
     * 因此 64 位域内的整数显示不经过 double 往返、无精度损失。</p>
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
        SceneSlider.Props props = SceneSlider.Props.builder(numValue)
                .min(min).max(max).step(step)
                .onChange((value, committing) -> adapter.onFieldEdit(path, Long.valueOf(Math.round(value))))
                .build();

        Supplier<SceneNode> control = () -> {
            SceneNode row = SceneNode.row();
            // 间距为布局常量（契约：主题不接管布局），与 NUMBER slider 同口径
            row.setGap(ConfigTheme.FIELD_GAP);
            row.setCrossAxisAlign(CrossAxisAlign.CENTER);
            SceneNode sliderRoot = SceneSlider.create(rt, props).get();
            row.appendChild(sliderRoot);
            SceneNode readout = new SceneNode();
            rt.bind(SceneThemes.foreground(rt), readout::setTextColor);
            readout.setFontSize(ConfigTheme.FONT_READOUT);
            readout.setHitTestable(false);
            // 读数取草稿值而非 slider 的 double 派生：整数显示不丢 64 位精度
            rt.bindComputed(() -> FieldRenderSupport.integerTextOf(draftSig.get()), readout::setText);
            row.appendChild(readout);
            return row;
        };

        return FieldShellBinder.build(rt, spec, adapter, control);
    }

    /**
     * 无 slider 声明：用 SceneTextInput（NUMBER 输入类型 + 整数文本编解码）。
     *
     * @param rt      场景运行时
     * @param spec    字段元数据
     * @param adapter 草稿适配器
     * @return 字段卡片节点
     */
    private SceneNode renderTextInput(SceneRuntime rt, FieldSpec spec, DraftSignalAdapter adapter) {
        final String path = spec.path();
        final ReadableSignal<Object> draftSig = adapter.draftSignal(path);

        // 构建期同步取初值：控件在事件 handler 里同步读 value（非 flush 路径），
        // 故规范文本与编辑期原文都必须有确定的起点。
        final String initialText = FieldRenderSupport.integerTextOf(draftSig.get());
        final Signal<String> editText = Signal.create(initialText);
        final ReadableSignal<String> displayText = Computed.create(initialText, () -> {
            Object value = draftSig.get();
            String raw = editText.get();
            return FieldRenderSupport.isUnfinishedIntegerText(raw, value)
                    ? raw : FieldRenderSupport.integerTextOf(value);
        });

        SceneTextInput.Props props = new SceneTextInput.Props(
                displayText,
                Signal.create(Boolean.TRUE),
                Signal.create(Boolean.FALSE),
                "",
                Integer.MAX_VALUE,
                SceneInputType.NUMBER,
                next -> {
                    editText.set(next);
                    Long parsed = IntegerCodec.parse(next);
                    // parse 失败存原始 String，让 DraftBuffer 校验报「值不是有效整数」
                    adapter.onFieldEdit(path, parsed != null ? (Object) parsed : next);
                });

        // 失焦：原文归位到值的规范写法（"007" → "7"），未完成形态不长期滞留；值本身不动。
        final Supplier<SceneNode> control = () -> {
            SceneNode input = SceneTextInput.create(rt, props).get();
            rt.bind(rt.interactionState(input).focused(), focused -> {
                if (!Boolean.TRUE.equals(focused)) {
                    editText.set(FieldRenderSupport.integerTextOf(draftSig.get()));
                }
            });
            return input;
        };

        return FieldShellBinder.build(rt, spec, adapter, control);
    }
}
