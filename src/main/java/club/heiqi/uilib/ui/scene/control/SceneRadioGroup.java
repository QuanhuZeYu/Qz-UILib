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
 * SceneRadioGroup —— scene 新栈控件层 Phase 4 批 2 首个迁移控件（单选组，VERTICAL）。
 *
 * <h3>定位：多选项单选受控控件范本（契约 R8 确立者）</h3>
 * <p>本控件确立「多选项单选受控零状态」契约 R8：带「N 选 1」语义的受控控件，当前选中项由外部
 * {@code selectedIndex} 只读 signal 唯一驱动；激活某选项时<b>只经 {@code onSelect.accept(targetIndex)}
 * 上抛期望选中项</b>，控件<b>绝不自己维护或修改 selectedIndex</b>。这是 R7 从二值布尔到 N 值下标的推广
 * ——同一灵魂（外部唯一源 + 期望值上抛），杜绝「内部选中态」与「外部 signal」双源（守 R1/R5/I11/R8）。</p>
 *
 * <h3>结构（VERTICAL only）</h3>
 * <pre>
 * root (COLUMN, crossAxisAlign=START, gap)                  ← 容器，非交互单元
 *   └─ option[i] (ROW, crossAxisAlign=CENTER, gap, padding, cornerRadius, borderWidth)  ← 交互单元 hitTestable=true
 *         ├─ circle[i] (16×16, 圆, borderWidth)             ← 装饰 hitTestable=false
 *         │     └─ dot[i] (8×8, 圆)                          ← 装饰 hitTestable=false
 *         └─ label[i] (text)                                ← 装饰 hitTestable=false
 * </pre>
 *
 * <h3>选中表达：透明背景而非 display:none（纯 PAINT 级零重排）</h3>
 * <p>dot 节点常驻占位，靠 {@code bind(PAINT, selectedIndex==i ? DOT_COLOR : 透明, dot::setBackgroundColor)}
 * 切换显隐，绝不增删节点——保证选中切换帧零重排（I7）。</p>
 *
 * <h3>契约</h3>
 * <p>R1 纯静态工厂零实例字段 / R2 Props 只读 signal + 常量 + 回调 / R3 组件函数只执行一次 /
 * R4 外观随状态经 bind 派生 / R5 交互态读 interactionState / R6 装饰子节点命中穿透 /
 * R8 多选项单选受控零状态。</p>
 */
public final class SceneRadioGroup {

    /**
     * dot 未选中时颜色（全透明，纯 PAINT 切换不重排）
     */
    private static final int DOT_TRANSPARENT = 0x00000000;

    /**
     * circle 固定边长（像素）
     */
    private static final int CIRCLE_SIZE = 16;
    /**
     * dot 固定边长（像素）
     */
    private static final int DOT_SIZE = 8;
    /**
     * circle/dot 圆角（足够大呈圆）
     */
    private static final int CIRCLE_RADIUS = SceneChromeTokens.RADIUS_PILL;
    /**
     * 边框宽度（像素）
     */
    private static final int BORDER_WIDTH = 1;
    /**
     * option 行圆角（像素，小圆角）
     */
    private static final int OPTION_RADIUS = 6;
    /**
     * option 行内边距（像素）
     */
    private static final int OPTION_PADDING = SceneChromeTokens.PAD_SM;
    /**
     * option 行内 circle 与 label 间距（像素）
     */
    private static final int OPTION_GAP = SceneChromeTokens.GAP_SM;
    /**
     * 各 option 行之间的纵向间距（像素）
     */
    private static final int ITEM_GAP = SceneChromeTokens.GAP_SM;

    /**
     * 纯静态工厂，禁止实例化（强制无状态，契约 R1）
     */
    private SceneRadioGroup() {
    }

    /**
     * RadioGroup 输入契约 —— 多选项单选受控：当前选中项由外部只读 signal 驱动，
     * 激活经 onSelect 交还期望选中下标（契约 R2/R8）。
     *
     * @param selectedIndex 当前选中项下标（响应式只读，受控源），控件绝不自己修改此值
     * @param options       选项文本列表（构建期固定常量，R2 允许常量）
     * @param enabled       是否启用（响应式只读），false 时禁用点击/键盘并切灰态
     * @param onSelect      选择回调，激活某选项时以该项下标调用，由外部 set 回 selectedIndex signal
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
     * 工厂：构建 RadioGroup 组件函数。
     *
     * <p>返回的 {@code Supplier} 体由 {@link SceneRuntime#mount} 执行一次（R3）：
     * 体内 for 循环建 N 个 option 节点（options 固定，循环建树无副作用、只跑一次，守 I3）。
     * 动态外观全落 {@code bind(computed(...))}，交互只经 {@code on} 调 {@code onSelect}（R4/R5/R8）。</p>
     *
     * @param rt    场景运行时
     * @param props RadioGroup 输入契约
     * @return 组件函数，交 {@code rt.mount(parent, ...)} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            SceneSingleSelectPrimitive.Props primitiveProps = new SceneSingleSelectPrimitive.Props(
                props.selectedIndex(),
                props.options(),
                props.enabled(),
                props.onSelect(),
                SceneSingleSelectPrimitive.Orientation.VERTICAL);
            SceneSingleSelectPrimitive.Result result = SceneSingleSelectPrimitive.create(rt, primitiveProps);
            result.root().setCrossAxisAlign(CrossAxisAlign.START);
            result.root().setGap(ITEM_GAP);

            for (SceneSingleSelectPrimitive.ItemHandle handle : result.items()) {
                SceneNode option = handle.item();
                option.setFlexDirection(FlexDirection.ROW);
                option.setCrossAxisAlign(CrossAxisAlign.CENTER);
                option.setGap(OPTION_GAP);
                option.setPadding(OPTION_PADDING);
                option.setCornerRadius(OPTION_RADIUS);
                option.setBorderWidth(BORDER_WIDTH);
                option.setBorderColor(SceneChromeTokens.BORDER_DEFAULT);

                SceneNode circle = new SceneNode();
                circle.setFlexDirection(FlexDirection.ROW);
                circle.setCrossAxisAlign(CrossAxisAlign.CENTER);
                circle.setMainAxisAlign(MainAxisAlign.CENTER);
                circle.setPreferredWidth(CIRCLE_SIZE);
                circle.setPreferredHeight(CIRCLE_SIZE);
                circle.setCornerRadius(CIRCLE_RADIUS);
                circle.setBorderWidth(BORDER_WIDTH);
                circle.setBorderColor(SceneChromeTokens.BORDER_DEFAULT);
                circle.setHitTestable(false);
                option.appendChild(circle);

                SceneNode dot = new SceneNode();
                dot.setPreferredWidth(DOT_SIZE);
                dot.setPreferredHeight(DOT_SIZE);
                dot.setCornerRadius(CIRCLE_RADIUS);
                dot.setHitTestable(false);
                circle.appendChild(dot);

                option.appendChild(handle.label());

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
                    circle::setBackgroundColor);
                rt.bind(Invalidation.PAINT,
                    Computed.create(() -> SceneStateColors.standardBorder(
                        Boolean.TRUE.equals(props.enabled().get()),
                        Boolean.TRUE.equals(interaction.focused().get()))),
                    circle::setBorderColor);
                rt.bind(Invalidation.PAINT,
                    Computed.create(() -> Boolean.TRUE.equals(handle.selected().get())
                        ? SceneChromeTokens.TEXT_ON_ACCENT : DOT_TRANSPARENT),
                    dot::setBackgroundColor);
                rt.bind(Invalidation.PAINT,
                    Computed.create(() -> SceneStateColors.standardText(
                        Boolean.TRUE.equals(props.enabled().get()), false)),
                    handle.label()::setTextColor);
                rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> option.setCursor(Boolean.TRUE.equals(e) ? SceneCursor.POINTER : SceneCursor.NOT_ALLOWED));
            }

            return result.root();
        };
    }
}
