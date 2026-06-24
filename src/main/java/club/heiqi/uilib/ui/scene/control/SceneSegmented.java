package club.heiqi.uilib.ui.scene.control;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.SceneStateColors;

/**
 * SceneSegmented —— scene 新栈控件层 Phase 4 批 2 分段单选控件（水平段式）。
 *
 * <h3>定位：多选项单选受控控件（契约 R8，与 {@link SceneRadioGroup} 同构）</h3>
 * <p>Props 与 RadioGroup 完全同构（selectedIndex + options 固定 + enabled + onSelect），复用 R8：
 * 当前选中段由外部 {@code selectedIndex} 只读 signal 唯一驱动；激活某段时<b>只经
 * {@code onSelect.accept(i)} 上抛期望选中下标</b>，控件<b>绝不自己维护或修改 selectedIndex</b>。</p>
 *
 * <h3>结构</h3>
 * <pre>
 * root (ROW, crossAxisAlign=STRETCH, gap)
 *   └─ segment[i] (ROW, mainAxisAlign=CENTER, crossAxisAlign=CENTER, padding, cornerRadius, preferredWidth=固定段宽)  ← 交互单元 hitTestable=true
 *         └─ label[i] (text)   ← 装饰 hitTestable=false
 * </pre>
 *
 * <h3>设计要点</h3>
 * <ul>
 *   <li><b>不复用 SceneButton</b>：它是试金石不是积木，嵌套会让交互态归属混乱，直接建段节点。</li>
 *   <li><b>等宽用固定段宽</b>：scene 无 flex-grow，用 {@code setPreferredWidth(固定段宽)} 替代（YAGNI 退让）。</li>
 *   <li><b>R6 段穿透权威落地</b>：段本身 hitTestable=true，段内 label 文字 hitTestable=false 穿透到所属段。</li>
 * </ul>
 *
 * <h3>契约</h3>
 * <p>R1 纯静态工厂零实例字段 / R2 Props 只读 signal + 常量 + 回调 / R3 组件函数只执行一次 /
 * R4 外观随状态经 bind 派生 / R5 交互态读 interactionState / R6 段内文字命中穿透到段 /
 * R8 多选项单选受控零状态。</p>
 */
public final class SceneSegmented {

    /**
     * 固定段宽（像素，scene 无 flex-grow 的等宽退让，本批契约外决定）
     */
    private static final int SEGMENT_WIDTH = 72;
    /**
     * 段内边距（像素）
     */
    private static final int SEGMENT_PADDING = SceneChromeTokens.PAD_LG;
    /**
     * 段圆角（像素）
     */
    private static final int SEGMENT_RADIUS = SceneChromeTokens.RADIUS_MD;
    /**
     * 各段之间的横向间距（像素）
     */
    private static final int SEG_GAP = SceneChromeTokens.GAP_SM;

    /**
     * 纯静态工厂，禁止实例化（强制无状态，契约 R1）
     */
    private SceneSegmented() {
    }

    /**
     * Segmented 输入契约 —— 多选项单选受控，与 {@link SceneRadioGroup.Props} 同构（契约 R2/R8）。
     *
     * @param selectedIndex 当前选中段下标（响应式只读，受控源），控件绝不自己修改此值
     * @param options       段文本列表（构建期固定常量，R2 允许常量）
     * @param enabled       是否启用（响应式只读），false 时禁用点击/键盘并切灰态
     * @param onSelect      选择回调，激活某段时以该段下标调用，由外部 set 回 selectedIndex signal
     */
    @Desugar
    public record Props(
        ReadableSignal<Integer> selectedIndex,
        List<String> options,
        ReadableSignal<Boolean> enabled,
        Consumer<Integer> onSelect
    ) {
    }

    /**
     * 工厂：构建 Segmented 组件函数。
     *
     * <p>返回的 {@code Supplier} 体由 {@link SceneRuntime#mount} 执行一次（R3）：
     * 体内 for 循环建 N 个 segment 节点（options 固定，循环建树无副作用、只跑一次，守 I3）。
     * 动态外观全落 {@code bind(computed(...))}，交互只经 {@code on} 调 {@code onSelect}（R4/R5/R8）。</p>
     *
     * @param rt    场景运行时
     * @param props Segmented 输入契约
     * @return 组件函数，交 {@code rt.mount(parent, ...)} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            SceneSingleSelectPrimitive.Props primitiveProps = new SceneSingleSelectPrimitive.Props(
                props.selectedIndex(),
                props.options(),
                props.enabled(),
                props.onSelect(),
                SceneSingleSelectPrimitive.Orientation.HORIZONTAL);
            SceneSingleSelectPrimitive.Result result = SceneSingleSelectPrimitive.create(rt, primitiveProps);
            result.root().setCrossAxisAlign(CrossAxisAlign.STRETCH);
            result.root().setGap(SEG_GAP);

            for (SceneSingleSelectPrimitive.ItemHandle handle : result.items()) {
                SceneNode segment = handle.item();
                segment.setFlexDirection(FlexDirection.ROW);
                segment.setMainAxisAlign(MainAxisAlign.CENTER);
                segment.setCrossAxisAlign(CrossAxisAlign.CENTER);
                segment.setPadding(SEGMENT_PADDING);
                segment.setCornerRadius(SEGMENT_RADIUS);
                segment.setPreferredWidth(SEGMENT_WIDTH);
                segment.setBorderWidth(1);
                segment.setBorderColor(SceneChromeTokens.BORDER_DEFAULT);
                segment.appendChild(handle.label());

                SceneInteractionState interaction = handle.interaction();

                rt.bind(Invalidation.PAINT,
                    Computed.create(() -> Boolean.TRUE.equals(handle.selected().get())
                        ? SceneStateColors.selectedBackground(
                            Boolean.TRUE.equals(props.enabled().get()),
                            Boolean.TRUE.equals(interaction.hovered().get()),
                            Boolean.TRUE.equals(interaction.pressed().get()))
                        : SceneStateColors.standardBackground(
                            Boolean.TRUE.equals(props.enabled().get()),
                            Boolean.TRUE.equals(interaction.hovered().get()),
                            Boolean.TRUE.equals(interaction.pressed().get()))),
                    segment::setBackgroundColor);
                rt.bind(Invalidation.PAINT,
                    Computed.create(() -> SceneStateColors.standardBorder(
                        Boolean.TRUE.equals(props.enabled().get()),
                        Boolean.TRUE.equals(interaction.focused().get()))),
                    segment::setBorderColor);
                rt.bind(Invalidation.PAINT,
                    Computed.create(() -> Boolean.TRUE.equals(handle.selected().get())
                        ? SceneStateColors.standardText(Boolean.TRUE.equals(props.enabled().get()), true)
                        : SceneStateColors.secondaryText(Boolean.TRUE.equals(props.enabled().get()))),
                    handle.label()::setTextColor);
                rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> segment.setCursor(Boolean.TRUE.equals(e) ? SceneCursor.POINTER : SceneCursor.NOT_ALLOWED));
            }

            return result.root();
        };
    }
}
