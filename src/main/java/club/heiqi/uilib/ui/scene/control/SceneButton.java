package club.heiqi.uilib.ui.scene.control;

import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.SceneStateColors;

/**
 * SceneButton —— scene 新栈控件层参考实现，第 0 段地基总验收试金石。
 *
 * <h3>定位：缺口浓缩器试金石，非真实迁移目标</h3>
 * <p>本控件用一个文件撞齐 scene 全部新地基能力：水平居中 flex（ROW + 主/交叉轴 CENTER）、
 * padding、边框、胶囊圆角、子节点裁剪（overflow:hidden）、非白文字色、四态背景切换。
 * 证明「裸 SceneNode + 新属性槽 + SceneRuntime」真能拼出完整控件，并确立后续所有控件
 * 照抄的契约范本（契约红线见 {@code package-info.java} R1-R5）。</p>
 *
 * <h3>组件函数形态（信条一）</h3>
 * <p>纯静态工厂 + 私有构造，控件类自身无任何实例字段（强制无状态）。
 * {@link #create} 返回 {@code Supplier<SceneNode>}，交 {@link SceneRuntime#mount} 执行一次（I3）：
 * 建树 + 设静态样式 + 绑定响应式派生。不是 fluent builder，不是持有节点的 setter 对象
 * （那是旧栈 Qt/GTK 老路，信条一明确拒绝）。</p>
 */
public final class SceneButton {

    /**
     * 内边距（像素）
     */
    private static final int PADDING = SceneChromeTokens.PAD_MD;
    /**
     * 边框宽度（像素）
     */
    private static final int BORDER_WIDTH = 1;
    /**
     * 标准圆角半径（像素）
     */
    private static final int BUTTON_RADIUS = SceneChromeTokens.RADIUS_MD;

    /**
     * 纯静态工厂，禁止实例化（强制无状态，契约 R1）
     */
    private SceneButton() {
    }

    /**
     * Button 输入契约 —— 全部只读 signal + 输出回调（契约 R2）。
     *
     * @param label   文本内容（响应式只读）
     * @param enabled 是否启用（响应式只读），false 时禁用点击/键盘并切灰态
     * @param onClick 动作输出回调，点击或 Enter/Space 激活时触发
     * @param variant 视觉变体，STANDARD 灰底 / PRIMARY ACCENT 蓝底白字
     */
    @Desugar
    public record Props(
        ReadableSignal<String> label,
        ReadableSignal<Boolean> enabled,
        Runnable onClick,
        SceneButtonVariant variant
    ) {
        /**
         * 兼容三参构造器：variant 默认 STANDARD，保持旧调用方零改动。
         *
         * @param label   文本内容
         * @param enabled 启用信号
         * @param onClick 点击回调
         */
        public Props(ReadableSignal<String> label, ReadableSignal<Boolean> enabled, Runnable onClick) {
            this(label, enabled, onClick, SceneButtonVariant.STANDARD);
        }
    }

    /**
     * 工厂：构建按钮组件函数。
     *
     * <p>返回的 {@code Supplier} 体由 {@link SceneRuntime#mount} 执行一次（I3）：
     * 只建 SceneNode 树 + 设静态属性 + {@code rt.bind/bindText/on/focusable}，
     * 动态外观全落 {@code bind(computed(...))}，交互只经 {@code on} 调回调（契约 R3/R4/R5）。</p>
     *
     * @param rt    场景运行时（提供 bind/on/interactionState/focusable）
     * @param props 按钮输入契约
     * @return 组件函数，交 {@code rt.mount(parent, ...)} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            SceneButtonPrimitive.Props primitiveProps = new SceneButtonPrimitive.Props(
                props.label(), props.enabled(), props.onClick());
            SceneButtonPrimitive.Result result = SceneButtonPrimitive.create(rt, primitiveProps);
            SceneNode root = result.root();
            SceneInteractionState interaction = result.interaction();
            root.setPadding(PADDING);
            root.setBorderWidth(BORDER_WIDTH);
            root.setBorderColor(SceneChromeTokens.BORDER_DEFAULT);
            root.setCornerRadius(BUTTON_RADIUS);

            final boolean primary = props.variant() == SceneButtonVariant.PRIMARY;

            // 背景：primary 走 ACCENT 通道（selectedBackground），standard 走标准灰通道
            rt.bindComputed(() -> primary
                    ? SceneStateColors.selectedBackground(
                        Boolean.TRUE.equals(props.enabled().get()),
                        Boolean.TRUE.equals(interaction.hovered().get()),
                        Boolean.TRUE.equals(interaction.pressed().get()))
                    : SceneStateColors.standardBackground(
                        Boolean.TRUE.equals(props.enabled().get()),
                        Boolean.TRUE.equals(interaction.hovered().get()),
                        Boolean.TRUE.equals(interaction.pressed().get())),
                root::setBackgroundColor);

            SceneControlChrome.bindStandardBorder(rt, root, props.enabled(), interaction);

            // 文本色：primary 用 TEXT_ON_ACCENT（白），standard 用 TEXT_PRIMARY
            rt.bindComputed(() -> primary
                    ? SceneStateColors.standardText(Boolean.TRUE.equals(props.enabled().get()), true)
                    : SceneStateColors.standardText(Boolean.TRUE.equals(props.enabled().get()), false),
                result.label()::setTextColor);

            SceneControlChrome.bindCursor(rt, root, props.enabled(), SceneCursor.POINTER, SceneCursor.NOT_ALLOWED);

            return root;
        };
    }
}
