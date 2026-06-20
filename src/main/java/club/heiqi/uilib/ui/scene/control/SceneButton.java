package club.heiqi.uilib.ui.scene.control;

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
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;

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

    // ==================== 四态背景配色（grounded 常量，无控件语义分支） ====================

    /** 默认态背景（深灰） */
    private static final int BG_ENABLED = 0xFF3A3A3A;
    /** hover 态背景（稍亮） */
    private static final int BG_HOVER = 0xFF505050;
    /** pressed 态背景（更暗） */
    private static final int BG_PRESSED = 0xFF2A2A2A;
    /** disabled 态背景（灰） */
    private static final int BG_DISABLED = 0xFF2F2F2F;

    /** 边框色（中灰） */
    private static final int BORDER_COLOR = 0xFF808080;

    /** enabled 文本色（白） */
    private static final int TEXT_ENABLED = 0xFFFFFFFF;
    /** disabled 文本色（暗灰，证明文本色可控非写死白） */
    private static final int TEXT_DISABLED = 0xFF888888;

    /** 内边距（像素） */
    private static final int PADDING = 10;
    /** 边框宽度（像素） */
    private static final int BORDER_WIDTH = 1;
    /** 胶囊圆角半径（像素，足够大使两端呈半圆） */
    private static final int CAPSULE_RADIUS = 999;

    /** 纯静态工厂，禁止实例化（强制无状态，契约 R1） */
    private SceneButton() {
    }

    /**
     * Button 输入契约 —— 全部只读 signal + 输出回调（契约 R2）。
     *
     * @param label   文本内容（响应式只读）
     * @param enabled 是否启用（响应式只读），false 时禁用点击/键盘并切灰态
     * @param onClick 动作输出回调，点击或 Enter/Space 激活时触发
     */
    @Desugar
    public record Props(
            ReadableSignal<String> label,
            ReadableSignal<Boolean> enabled,
            Runnable onClick
    ) {
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
            // ① 建树一次（无副作用，I3）—— 纯结构 + 静态样式
            SceneNode root = new SceneNode();
            root.setFlexDirection(FlexDirection.ROW);
            root.setMainAxisAlign(MainAxisAlign.CENTER);
            root.setCrossAxisAlign(CrossAxisAlign.CENTER);
            root.setPadding(PADDING);
            root.setBorderWidth(BORDER_WIDTH);
            root.setBorderColor(BORDER_COLOR);
            root.setCornerRadius(CAPSULE_RADIUS); // 胶囊圆角
            root.setClipChildren(true);           // overflow:hidden

            SceneNode labelNode = new SceneNode();
            root.appendChild(labelNode);

            // ② 交互态：读 Router 权威 signal，绝不自维护 boolean（契约 R5）
            SceneInteractionState is = rt.interactionState(root);

            // ③ 动态外观全走 bind(computed(交互 signal))——禁止 handler 里命令式改样式（契约 R4）
            //    背景：四态优先级 disabled > pressed > hover > enabled
            rt.bind(Invalidation.PAINT,
                    Computed.create(() -> resolveBackground(
                            props.enabled().get(),
                            is.pressed().get(),
                            is.hovered().get())),
                    root::setBackgroundColor);

            // 文本内容（响应式）
            rt.bindText(labelNode, props.label());

            // 文本色：enabled 白、disabled 暗灰（PAINT 级，证明文本色可控）
            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> labelNode.setTextColor(Boolean.TRUE.equals(e) ? TEXT_ENABLED : TEXT_DISABLED));

            // cursor 声明式附着：enabled 指针手型、disabled 禁止符号
            rt.bind(Invalidation.PAINT, props.enabled(),
                    e -> root.setCursor(Boolean.TRUE.equals(e) ? SceneCursor.POINTER : SceneCursor.NOT_ALLOWED));

            // ④ 交互经 on → 只调回调（I11），绝不在 handler 里 setXxx（契约 R4）
            rt.on(root, SceneEventType.CLICK, (ev, ctx) -> {
                if (Boolean.TRUE.equals(props.enabled().get())) {
                    props.onClick().run();
                }
            });

            // 键盘可达：登记进 Tab 焦点环 + Enter/Space 激活
            rt.focusable(root);
            rt.on(root, SceneEventType.KEY_DOWN, (ev, ctx) -> {
                SceneKey key = ev.getKey();
                if ((key == SceneKey.ENTER || key == SceneKey.SPACE)
                        && Boolean.TRUE.equals(props.enabled().get())) {
                    props.onClick().run();
                }
            });

            return root;
        };
    }

    /**
     * 解析四态背景色（纯函数，无副作用、无控件语义分支）。
     *
     * <p>优先级：disabled &gt; pressed &gt; hover &gt; enabled 默认。</p>
     *
     * @param enabled 是否启用
     * @param pressed 是否按压中
     * @param hovered 是否悬停中
     * @return 当前态对应的 ARGB 背景色
     */
    private static int resolveBackground(Boolean enabled, Boolean pressed, Boolean hovered) {
        if (!Boolean.TRUE.equals(enabled)) {
            return BG_DISABLED;
        }
        if (Boolean.TRUE.equals(pressed)) {
            return BG_PRESSED;
        }
        if (Boolean.TRUE.equals(hovered)) {
            return BG_HOVER;
        }
        return BG_ENABLED;
    }
}
