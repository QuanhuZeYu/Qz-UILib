package club.heiqi.config.ui.field;

import java.util.function.Supplier;

import club.heiqi.config.schema.ColorSpec;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.schema.HexColorCodec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneInputType;
import club.heiqi.uilib.ui.scene.control.SceneTextInput;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 颜色字段渲染器（{@code spec.widget()} 为 {@link ColorSpec} 的 NUMBER 字段）：
 * 把 {@code 0xRRGGBB} 整数值渲染成 <b>{@code #RRGGBB} 文本输入框</b>，
 * 替代通用数值输入框里用户看不懂的十进制形态（如 {@code 4253439}）。
 *
 * <p>值语义与校验完全不变：颜色仍是 NUMBER（range {@code [0, 0xFFFFFF]}），提交仍走
 * {@link DraftSignalAdapter#onFieldEdit} 写草稿、dirty / error / 保存路径与数值字段一致；
 * 本类只决定「用什么控件、按什么文本形态显示」。控件与外观全部复用既有件
 * （{@link SceneTextInput} + {@link FieldShellBinder} + 主题语义色），不新增控件实现。</p>
 *
 * <h3>为什么是文本型输入</h3>
 * <p>{@link SceneInputType#NUMBER} 的字符白名单只放行 {@code 0-9 . - + e E}，会吃掉颜色语法自带的
 * {@code '#'}，故取 {@link SceneInputType#TEXT}：字符合法性交给 {@link HexColorCodec} 判读，
 * 输入通道本身不做字符级拒绝。</p>
 *
 * <h3>编辑期原文（沿用数值输入框那套机制，不另造第二套）</h3>
 * <p>{@link SceneTextInput} 是完全受控控件：显示文本只从外部 value 派生、自己不缓存原文，而
 * 「值 → 文本」有损——用户输入过程中的中间态（刚敲完 {@code "#"}、{@code "#40E6F"}、十进制的
 * {@code "4"}）都不是合法颜色，若按「输入即解析回写 value」处理，显示会被规范形态或解析失败吃掉，
 * 出现「敲了没反应」；十进制形态更糟：首字符 {@code 4} 是合法颜色，立刻被规范化成 {@code #000004}，
 * 后续输入全废。故与 {@code NumberFieldRenderer} 同法：</p>
 * <ul>
 *   <li>本层自持编辑期原文 {@code editText}（受控契约下「谁拥有真值谁负责编辑期形态」），
 *       显示文本 = {@link FieldRenderSupport#isUnfinishedText}（原文仍代表当前值 ? 原文 : 规范形态）；</li>
 *   <li>原文只有构成合法颜色时才回写值（{@link HexColorCodec#parse} 非 null），否则按原文写进草稿，
 *       走草稿既有 NUMBER 校验报错——<b>不静默改值、不夹取、不四舍五入、不回落默认值</b>；</li>
 *   <li>失焦时原文归位到值的规范写法（{@code 40e6ff} / {@code 4253439} → {@code #40E6FF}），
 *       未完成形态不长期滞留；值本身不动，非法原文继续留在草稿里报错。</li>
 * </ul>
 *
 * <p>分发入口在 {@code NumberFieldRenderer}（与 {@code SliderSpec} 同一处分发）：颜色是 NUMBER 的
 * 控件形态变体，按类型注册表（{@code FieldRendererRegistry}）与 schema 兼容判定都不受影响。</p>
 */
public final class ColorFieldRenderer implements FieldRenderer {

    /** 纯静态工厂语义，但实现接口需实例化；无实例字段 */
    public ColorFieldRenderer() {
    }

    @Override
    public SceneNode render(SceneRuntime rt, FieldSpec spec, DraftSignalAdapter adapter) {
        final String path = spec.path();
        final ReadableSignal<Object> draftSig = adapter.draftSignal(path);

        // 构建期同步取初值：Computed.create(Supplier) 首帧前是 null，而控件在事件 handler 里同步读
        // value（非 flush 路径），故规范文本与编辑期原文都必须有确定的起点。
        final String initialText = displayTextOf(draftSig.get());
        // 编辑期原文（受控契约下的「字段权威」）：键入即写它，值的写回仍走下面既有 parse-then-writeback。
        final Signal<String> editText = Signal.create(initialText);
        // 显示文本 = 原文仍是当前值的未完成写法 ? 原文 : 值的规范写法。
        // 等值写回被帧末去重丢弃时，editText 的这次变化仍会推动本派生重算 ⇒ "#"、十进制首字符显示得出来。
        final ReadableSignal<String> display = Computed.create(initialText, () -> {
            Object value = draftSig.get();
            String raw = editText.get();
            return FieldRenderSupport.isUnfinishedText(raw, value, TEXT_CODEC) ? raw : displayTextOf(value);
        });

        SceneTextInput.Props props = SceneTextInput.Props.builder(display)
                // NUMBER 过滤会吃掉 '#'（颜色语法的一部分）⇒ 文本型；合法性由 HexColorCodec 判读
                .inputType(SceneInputType.TEXT)
                .placeholder(ColorSpec.PLACEHOLDER)
                .onChange(next -> {
                    editText.set(next);
                    Integer rgb = HexColorCodec.parse(next);
                    if (rgb != null) {
                        adapter.onFieldEdit(path, Double.valueOf(rgb.intValue()));
                    } else {
                        // 未完成 / 非法原文按原文写进草稿，由 DraftBuffer 既有 NUMBER 校验报
                        //"值不是有效数字"或"数值超出范围"，不在此处改值
                        adapter.onFieldEdit(path, next);
                    }
                })
                .build();

        // 失焦：原文归位到值的规范写法，未完成形态不长期滞留；值本身不动。
        // 该绑定同时读 draft：未聚焦期间外部改动（重置 / 撤销 / 其它控件写同字段）也把原文带回规范写法。
        final Supplier<SceneNode> control = () -> {
            SceneNode input = SceneTextInput.create(rt, props).get();
            rt.bind(rt.interactionState(input).focused(), focused -> {
                if (!Boolean.TRUE.equals(focused)) {
                    editText.set(displayTextOf(draftSig.get()));
                }
            });
            return input;
        };

        return FieldShellBinder.build(rt, spec, adapter, control);
    }

    /**
     * 显示文本：合法颜色值 → {@code #RRGGBB}（大写）；其余如实透出，绝不显示成一个并不存在的颜色。
     *
     * <p>草稿里可能是：合法数值（用户输入 / 默认值 / 磁盘读入）、用户敲的非法原文（String）、
     * 或越界 / 非整数数值（理论上被校验拦下，此处仍如实显示十进制原文而非环绕后的假颜色）。</p>
     *
     * @param value 草稿值，可为 null
     * @return 显示文本（null → 空串）
     */
    static String displayTextOf(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number) {
            double number = ((Number) value).doubleValue();
            if (number == Math.rint(number) && number >= 0 && number <= HexColorCodec.MAX_RGB) {
                return HexColorCodec.format((int) number);
            }
            return FieldRenderSupport.formatReadout(number);
        }
        return String.valueOf(value);
    }

    /**
     * 颜色文本编解码：规范形态 {@code #RRGGBB} ↔ 值。规则全部来自 {@link HexColorCodec}，
     * 本类不复制字面量规则，只把它接进既有的编辑期原文机制（{@link FieldRenderSupport#isUnfinishedText}）。
     */
    static final FieldRenderSupport.ValueTextCodec TEXT_CODEC = new FieldRenderSupport.ValueTextCodec() {
        @Override
        public String format(Object value) {
            return displayTextOf(value);
        }

        @Override
        public Object parse(String text) {
            return HexColorCodec.parse(text);
        }
    };
}
