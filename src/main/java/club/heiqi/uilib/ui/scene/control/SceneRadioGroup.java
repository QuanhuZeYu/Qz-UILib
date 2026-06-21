package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;

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

    // ==================== circle 四态背景配色（grounded 深灰系，复用 SceneCheckbox 风格） ====================

    /** 未选中 + 默认态 circle 背景（深灰） */
    private static final int CIRCLE_UNSEL_ENABLED = 0xFF3A3A3A;
    /** 未选中 + hover 态 circle 背景（稍亮） */
    private static final int CIRCLE_UNSEL_HOVER = 0xFF505050;
    /** 未选中 + pressed 态 circle 背景（更暗） */
    private static final int CIRCLE_UNSEL_PRESSED = 0xFF2A2A2A;
    /** 选中 + 默认态 circle 背景（亮蓝实心） */
    private static final int CIRCLE_SEL_ENABLED = 0xFF4A90D9;
    /** 选中 + hover 态 circle 背景（更亮蓝） */
    private static final int CIRCLE_SEL_HOVER = 0xFF5BA0E9;
    /** 选中 + pressed 态 circle 背景（暗蓝） */
    private static final int CIRCLE_SEL_PRESSED = 0xFF3A7BC8;
    /** disabled 态 circle 背景（灰，选中与否同色） */
    private static final int CIRCLE_DISABLED = 0xFF2F2F2F;

    /** circle 边框色（中灰） */
    private static final int BORDER_COLOR = 0xFF808080;

    /** dot 选中时颜色（亮灰白） */
    private static final int DOT_COLOR = 0xFFE0E0E0;
    /** dot 未选中时颜色（全透明，纯 PAINT 切换不重排） */
    private static final int DOT_TRANSPARENT = 0x00000000;

    /** enabled label 文本色（白） */
    private static final int TEXT_ENABLED = 0xFFFFFFFF;
    /** disabled label 文本色（暗灰） */
    private static final int TEXT_DISABLED = 0xFF888888;

    /** circle 固定边长（像素） */
    private static final int CIRCLE_SIZE = 16;
    /** dot 固定边长（像素） */
    private static final int DOT_SIZE = 8;
    /** circle/dot 圆角（足够大呈圆） */
    private static final int CIRCLE_RADIUS = 999;
    /** 边框宽度（像素） */
    private static final int BORDER_WIDTH = 1;
    /** option 行圆角（像素，小圆角） */
    private static final int OPTION_RADIUS = 6;
    /** option 行内边距（像素） */
    private static final int OPTION_PADDING = 4;
    /** option 行内 circle 与 label 间距（像素） */
    private static final int OPTION_GAP = 8;
    /** 各 option 行之间的纵向间距（像素） */
    private static final int ITEM_GAP = 4;

    /** 纯静态工厂，禁止实例化（强制无状态，契约 R1） */
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
            // ① 建树一次（无副作用，I3）—— 纵向容器
            SceneNode root = new SceneNode();
            root.setFlexDirection(FlexDirection.COLUMN);
            root.setCrossAxisAlign(CrossAxisAlign.START);
            root.setGap(ITEM_GAP);

            final List<String> options = props.options();
            final int count = options.size();

            // 缓存各 option 节点引用，供方向键 requestFocus 用
            final List<SceneNode> optionNodes = new ArrayList<>(count);

            for (int idx = 0; idx < count; idx++) {
                final int i = idx; // final 局部副本供 lambda 捕获

                // option[i]：交互单元（hitTestable 默认 true），ROW + 交叉轴 CENTER
                SceneNode option = new SceneNode();
                option.setFlexDirection(FlexDirection.ROW);
                option.setCrossAxisAlign(CrossAxisAlign.CENTER);
                option.setGap(OPTION_GAP);
                option.setPadding(OPTION_PADDING);
                option.setCornerRadius(OPTION_RADIUS);
                option.setBorderWidth(BORDER_WIDTH);
                option.setBorderColor(BORDER_COLOR);
                root.appendChild(option);
                optionNodes.add(option);

                // circle[i]：16×16 圆环，装饰穿透；内含 dot
                SceneNode circle = new SceneNode();
                circle.setFlexDirection(FlexDirection.ROW);
                circle.setCrossAxisAlign(CrossAxisAlign.CENTER);
                circle.setMainAxisAlign(MainAxisAlign.CENTER);
                circle.setPreferredWidth(CIRCLE_SIZE);
                circle.setPreferredHeight(CIRCLE_SIZE);
                circle.setCornerRadius(CIRCLE_RADIUS);
                circle.setBorderWidth(BORDER_WIDTH);
                circle.setBorderColor(BORDER_COLOR);
                circle.setHitTestable(false);
                option.appendChild(circle);

                // dot[i]：8×8 圆点，常驻占位，靠透明背景切换显隐（装饰穿透）
                SceneNode dot = new SceneNode();
                dot.setPreferredWidth(DOT_SIZE);
                dot.setPreferredHeight(DOT_SIZE);
                dot.setCornerRadius(CIRCLE_RADIUS);
                dot.setHitTestable(false);
                circle.appendChild(dot);

                // label[i]：纯文本装饰子节点，装饰穿透（契约 R6）
                SceneNode labelNode = new SceneNode();
                labelNode.setHitTestable(false);
                labelNode.setText(options.get(i));
                option.appendChild(labelNode);

                // ② 各 option 各取自己的 interactionState（契约 R5）
                SceneInteractionState is = rt.interactionState(option);

                // ③ 动态外观全走 bind（契约 R4）
                //    circle 背景：enabled × selectedIndex==i × pressed × hovered 四态
                rt.bind(Invalidation.PAINT,
                        Computed.create(() -> resolveCircleBackground(
                                props.enabled().get(),
                                isSelected(props.selectedIndex().get(), i),
                                is.pressed().get(),
                                is.hovered().get())),
                        circle::setBackgroundColor);

                // dot 显隐：选中→实心、未选中→透明（纯 PAINT 级零重排）
                rt.bind(Invalidation.PAINT,
                        Computed.create(() -> isSelected(props.selectedIndex().get(), i)
                                ? DOT_COLOR : DOT_TRANSPARENT),
                        dot::setBackgroundColor);

                // label 文本色：enabled 白、disabled 暗灰
                rt.bind(Invalidation.PAINT, props.enabled(),
                        e -> labelNode.setTextColor(Boolean.TRUE.equals(e) ? TEXT_ENABLED : TEXT_DISABLED));

                // cursor 声明式附着：enabled 指针手型、disabled 禁止符号（挂在交互单元 option 上）
                rt.bind(Invalidation.PAINT, props.enabled(),
                        e -> option.setCursor(Boolean.TRUE.equals(e) ? SceneCursor.POINTER : SceneCursor.NOT_ALLOWED));

                // ④ 交互经 on → 只调 onSelect 上抛期望选中下标（受控 R8，绝不自改 selectedIndex）
                rt.on(option, SceneEventType.CLICK, (ev, ctx) -> {
                    if (Boolean.TRUE.equals(props.enabled().get())) {
                        props.onSelect().accept(i);
                    }
                });

                // 键盘可达：登记进 Tab 焦点环
                rt.focusable(option);
                rt.on(option, SceneEventType.KEY_DOWN, (ev, ctx) -> {
                    if (!Boolean.TRUE.equals(props.enabled().get())) {
                        return;
                    }
                    SceneKey key = ev.getKey();
                    if (key == SceneKey.ENTER || key == SceneKey.SPACE) {
                        // Enter/Space 激活当前 option
                        props.onSelect().accept(i);
                    } else if (key == SceneKey.ARROW_UP || key == SceneKey.ARROW_DOWN) {
                        // 方向键导航：读当前 selectedIndex 算 nextIndex（读 signal 合法 I11），
                        // 上抛 + 焦点移动（requestFocus 是受控逃生舱合法）
                        Integer curObj = props.selectedIndex().get();
                        int cur = (curObj == null) ? 0 : curObj.intValue();
                        int next = (key == SceneKey.ARROW_UP) ? cur - 1 : cur + 1;
                        // 边界裁剪
                        if (next < 0) {
                            next = 0;
                        } else if (next > count - 1) {
                            next = count - 1;
                        }
                        props.onSelect().accept(next);
                        rt.requestFocus(optionNodes.get(next));
                    }
                });
            }

            return root;
        };
    }

    /**
     * 判断指定下标是否为当前选中项（null 安全）。
     *
     * @param selected 当前选中下标（可能为 null）
     * @param i        待判定下标
     * @return true 表示 i 是当前选中项
     */
    private static boolean isSelected(Integer selected, int i) {
        return selected != null && selected.intValue() == i;
    }

    /**
     * 解析 circle 四态背景色（纯函数，无副作用）。
     *
     * <p>优先级：disabled &gt; pressed &gt; hover &gt; default；
     * 同一态下选中与未选中用不同色系区分（选中亮蓝、未选中深灰）。</p>
     *
     * @param enabled  是否启用
     * @param selected 是否为当前选中项
     * @param pressed  是否按压中
     * @param hovered  是否悬停中
     * @return 当前态对应的 ARGB 背景色
     */
    private static int resolveCircleBackground(Boolean enabled, boolean selected, Boolean pressed, Boolean hovered) {
        if (!Boolean.TRUE.equals(enabled)) {
            return CIRCLE_DISABLED;
        }
        if (Boolean.TRUE.equals(pressed)) {
            return selected ? CIRCLE_SEL_PRESSED : CIRCLE_UNSEL_PRESSED;
        }
        if (Boolean.TRUE.equals(hovered)) {
            return selected ? CIRCLE_SEL_HOVER : CIRCLE_UNSEL_HOVER;
        }
        return selected ? CIRCLE_SEL_ENABLED : CIRCLE_UNSEL_ENABLED;
    }
}
