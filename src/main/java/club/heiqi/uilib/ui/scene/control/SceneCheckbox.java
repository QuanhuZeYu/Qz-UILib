package club.heiqi.uilib.ui.scene.control;

import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneCheckbox —— scene 新栈控件层 Phase 4 批 1 首个真实迁移控件。
 *
 * <h3>定位：受控双向控件范本（契约 R7 确立者）</h3>
 * <p>本控件是 strangler 模式下控件层重建的首批真实控件之一，确立「受控双向控件」契约 R7：
 * 控件<b>零内部状态</b>，当前勾选态完全由外部 {@code checked} 只读 signal 驱动；点击时
 * <b>绝不自己翻转</b>，而是经 {@code onChange.accept(!checked.get())} 把「期望的新值」交还外部，
 * 由外部 set 回 checked signal（守 R1/R5/I11，避免双向状态源不一致）。</p>
 *
 * <h3>结构</h3>
 * <p>root（交互单元，hitTestable 默认 true，ROW + 交叉轴 CENTER + gap）
 * + box 子节点（16×16 固定方块，装饰穿透 hitTestable=false）
 * + label 子节点（文本，装饰穿透 hitTestable=false）。</p>
 *
 * <h3>契约</h3>
 * <p>纯静态工厂 + 私有构造，无实例字段（R1）。Props 全只读 signal + 回调（R2）。
 * {@link #create} 返回 {@code Supplier<SceneNode>} 只执行一次（R3）。动态外观全走
 * {@code rt.bind(Invalidation, Computed, setter)}（R4）。交互态只读 interactionState
 * signal（R5）。装饰子节点 hitTestable=false 命中穿透到根（R6）。受控双向零内部状态（R7）。</p>
 */
public final class SceneCheckbox {

    // ==================== box 四态背景配色（grounded 常量，复用 SceneButton 深灰系） ====================

    /** 未勾选 + 默认态背景（深灰） */
    private static final int BOX_UNCHECKED_ENABLED = 0xFF3A3A3A;
    /** 未勾选 + hover 态背景（稍亮） */
    private static final int BOX_UNCHECKED_HOVER = 0xFF505050;
    /** 未勾选 + pressed 态背景（更暗） */
    private static final int BOX_UNCHECKED_PRESSED = 0xFF2A2A2A;
    /** 勾选 + 默认态背景（亮色实心，区分勾选） */
    private static final int BOX_CHECKED_ENABLED = 0xFF4A90D9;
    /** 勾选 + hover 态背景（更亮蓝） */
    private static final int BOX_CHECKED_HOVER = 0xFF5BA0E9;
    /** 勾选 + pressed 态背景（暗蓝） */
    private static final int BOX_CHECKED_PRESSED = 0xFF3A7BC8;
    /** disabled 态背景（灰，勾选与否同色） */
    private static final int BOX_DISABLED = 0xFF2F2F2F;

    /** box 边框色（中灰） */
    private static final int BORDER_COLOR = 0xFF808080;

    /** enabled label 文本色（白） */
    private static final int TEXT_ENABLED = 0xFFFFFFFF;
    /** disabled label 文本色（暗灰） */
    private static final int TEXT_DISABLED = 0xFF888888;

    /** box 固定边长（像素） */
    private static final int BOX_SIZE = 16;
    /** box 边框宽度（像素） */
    private static final int BORDER_WIDTH = 1;
    /** box 圆角（像素，小圆角） */
    private static final int BOX_RADIUS = 3;
    /** root 行内间距（box 与 label 之间，像素） */
    private static final int GAP = 8;

    /** 纯静态工厂，禁止实例化（强制无状态，契约 R1） */
    private SceneCheckbox() {
    }

    /**
     * Checkbox 输入契约 —— 受控双向：当前值由外部只读 signal 驱动，交互经 onChange 交还期望新值（契约 R2/R7）。
     *
     * @param checked  勾选态（响应式只读，受控源），控件绝不自己翻转或缓存此值
     * @param label    标签文本（响应式只读）
     * @param enabled  是否启用（响应式只读），false 时禁用点击/键盘并切灰态
     * @param onChange 切换回调，激活时以 {@code !checked.get()}（期望新值）调用，由外部 set 回 checked signal
     */
    @Desugar
    public record Props(
            ReadableSignal<Boolean> checked,
            ReadableSignal<String> label,
            ReadableSignal<Boolean> enabled,
            Consumer<Boolean> onChange
    ) {
    }

    /**
     * 工厂：构建 Checkbox 组件函数。
     *
     * <p>返回的 {@code Supplier} 体由 {@link SceneRuntime#mount} 执行一次（R3）：建树 + 设静态属性 +
     * {@code rt.bind/bindText/on/focusable}，动态外观全落 {@code bind(computed(...))}，
     * 交互只经 {@code on} 调 {@code onChange}（R4/R5/R7）。</p>
     *
     * @param rt    场景运行时
     * @param props Checkbox 输入契约
     * @return 组件函数，交 {@code rt.mount(parent, ...)} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            SceneToggleablePrimitive.Props primitiveProps = new SceneToggleablePrimitive.Props(
                    props.checked(), props.label(), props.enabled(), props.onChange());
            SceneToggleablePrimitive.Result result = SceneToggleablePrimitive.create(rt, primitiveProps);

            SceneNode root = result.root();
            root.setGap(GAP);

            SceneNode box = result.indicator();
            box.setPreferredWidth(BOX_SIZE);
            box.setPreferredHeight(BOX_SIZE);
            box.setBorderWidth(BORDER_WIDTH);
            box.setBorderColor(BORDER_COLOR);
            box.setCornerRadius(BOX_RADIUS);

            //    box 背景：checked × 四态优先级 disabled > pressed > hover > default
            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> resolveBoxBackground(
                            props.enabled().get(),
                            props.checked().get(),
                            result.pressed().get(),
                            result.hovered().get())),
                    box::setBackgroundColor);

            // label 文本色：enabled 白、disabled 暗灰
            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> result.labelNode().setTextColor(Boolean.TRUE.equals(e) ? TEXT_ENABLED : TEXT_DISABLED));

            // cursor 声明式附着：enabled 指针手型、disabled 禁止符号
            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> root.setCursor(Boolean.TRUE.equals(e) ? SceneCursor.POINTER : SceneCursor.NOT_ALLOWED));

            return root;
        };
    }

    /**
     * 解析 box 四态背景色（纯函数，无副作用）。
     *
     * <p>优先级：disabled &gt; pressed &gt; hover &gt; default；
     * 同一态下 checked 与未 checked 用不同色系区分（checked 亮蓝实心、未 checked 深灰）。</p>
     *
     * @param enabled 是否启用
     * @param checked 是否勾选
     * @param pressed 是否按压中
     * @param hovered 是否悬停中
     * @return 当前态对应的 ARGB 背景色
     */
    private static int resolveBoxBackground(Boolean enabled, Boolean checked, Boolean pressed, Boolean hovered) {
        if (!Boolean.TRUE.equals(enabled)) {
            return BOX_DISABLED;
        }
        boolean on = Boolean.TRUE.equals(checked);
        if (Boolean.TRUE.equals(pressed)) {
            return on ? BOX_CHECKED_PRESSED : BOX_UNCHECKED_PRESSED;
        }
        if (Boolean.TRUE.equals(hovered)) {
            return on ? BOX_CHECKED_HOVER : BOX_UNCHECKED_HOVER;
        }
        return on ? BOX_CHECKED_ENABLED : BOX_UNCHECKED_ENABLED;
    }
}
