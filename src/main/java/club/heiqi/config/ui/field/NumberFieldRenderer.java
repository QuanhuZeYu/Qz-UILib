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
 * value 经 {@link FieldRenderSupport#numberTextOf} 派生规范显示文本（编辑期显示用户原文，见下节），
 * onChange 把 String parse 为 Double 写回
 * （parse 失败时存原始 String，让 DraftBuffer 校验报"不是有效数字"）。
 * 外壳装配经 {@link FieldShellBinder#build} 收口，标题回退经 {@link FieldRenderSupport#labelOf}。</p>
 *
 * <h3>编辑期原文：数值输入框必须自己持有「用户正在输入的形态」</h3>
 * <p><b>缺陷与根因</b>：{@code SceneTextInput} 是受控控件——显示文本只从外部 value 派生，
 * 控件不缓存、不自改；而本层的「值 → 文本」是有损的（{@code Double.parseDouble("0.")} 合法
 * = 0.0，parse 后再格式化必然吃掉未完成写法）。若直接把「draft 值 → 文本」的派生信号当
 * value，用户先输 {@code 0} 再敲 {@code .} 时，parse 出的 0.0 与 draft 现值相等，写回被帧末
 * 「无净变化」去重丢弃 ⇒ 上游 signal 不通知 ⇒ 文本不重算 ⇒ 小数点永远显示不出来
 * （{@code 5.} / {@code .5} 同源受害）。</p>
 *
 * <p><b>修法</b>：编辑期原文由本层持有（受控契约下「谁拥有真值谁负责编辑期形态」），
 * 显示文本 = {@code 原文仍是当前值的未完成写法 ? 原文 : 值的规范写法}；值本身仍走
 * parse-then-writeback（数值提交、dirty、DraftBuffer 校验、保存路径全部不变），
 * 失焦时原文归位到规范写法（{@code "5." → "5"}），未完成形态不长期滞留。</p>
 *
 * <p><b>G15/Number 外观口径</b>（契约 §4/§4.1/§4.2）：字段卡片表面与 dirty/error 语义色由
 * {@link FieldShellBinder} 下沉的 FormFieldShell theme-aware 路径派生（GROUP 角色），本类不复制；
 * {@code SceneSlider}（G06）与 {@code SceneTextInput}（G04）控件本体已默认消费
 * {@code SceneThemes}/{@code SceneSurfaceBinder}，本类只组 Props、不再叠第二层表面或边框。
 * slider 读数文本前景取 {@link SceneThemes#foreground(SceneRuntime)} 主题信号（构建期捕获、
 * effect 内应用，无 {@code .get()} 快照）；字号与行间距是布局/排版常量（契约 §4「padding/尺寸/
 * 布局属性归控件自身，主题不接管布局」），保留 {@code ConfigTheme} 纯 int 常量取值。
 * <b>G15/收口</b>：曾喂给 binder 的 {@code ConfigTheme.asFormTheme()} 兼容占位实参已随
 * FieldShellBinder 的 theme 形参一并删除（默认路径不消费，4.0 施工期不留缓冲）。
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

        // G15/收口：原 theme 兼容占位实参（ConfigTheme.asFormTheme()）随 binder 签名收口摘除。
        return FieldShellBinder.build(rt, spec, adapter, control);
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

        // 构建期同步取初值：Computed.create(Supplier) 首帧前是 null，而控件在事件 handler 里同步读
        // value（非 flush 路径），故规范文本与编辑期原文都必须有确定的起点。
        final String initialText = FieldRenderSupport.numberTextOf(draftSig.get());
        // 编辑期原文（受控契约下的「字段权威」）：键入即写它，值的写回仍走下面既有 parse-then-writeback。
        final Signal<String> editText = Signal.create(initialText);
        // 显示文本 = 原文仍是当前值的未完成写法 ? 原文 : 值的规范写法。
        // 等值写回被帧末去重丢弃时，editText 的这次变化仍会推动本派生重算 ⇒ "0." 显示得出来。
        final ReadableSignal<String> displayText = Computed.create(initialText, () -> {
            Object value = draftSig.get();
            String raw = editText.get();
            return FieldRenderSupport.isUnfinishedNumberText(raw, value)
                    ? raw : FieldRenderSupport.numberTextOf(value);
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
                    try {
                        adapter.onFieldEdit(path, Double.valueOf(Double.parseDouble(next)));
                    } catch (NumberFormatException e) {
                        // parse 失败存原始 String，让 DraftBuffer 校验报"不是有效数字"
                        adapter.onFieldEdit(path, next);
                    }
                });

        // 失焦：原文归位到值的规范写法（"5." → "5"），未完成形态不长期滞留；值本身不动，
        // 校验仍由 DraftBuffer 按既有规则跑（parse 失败的原文依旧留在 draft 里报错）。
        // 该绑定同时读 draft：未聚焦期间外部改动（重置/撤销/他控件写同字段）也把原文带回规范写法。
        final Supplier<SceneNode> control = () -> {
            SceneNode input = SceneTextInput.create(rt, props).get();
            rt.bind(rt.interactionState(input).focused(), focused -> {
                if (!Boolean.TRUE.equals(focused)) {
                    editText.set(FieldRenderSupport.numberTextOf(draftSig.get()));
                }
            });
            return input;
        };

        // G15/收口：原 theme 兼容占位实参（ConfigTheme.asFormTheme()）随 binder 签名收口摘除。
        return FieldShellBinder.build(rt, spec, adapter, control);
    }
}
