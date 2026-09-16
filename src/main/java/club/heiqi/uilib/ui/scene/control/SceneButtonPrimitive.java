package club.heiqi.uilib.ui.scene.control;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneButtonPrimitive —— 无样式按钮交互行为核心。
 *
 * <p>该 primitive 只负责结构、文本布局绑定、交互态、焦点与激活行为，不设置背景、边框、
 * 文本色、cursor、padding 或圆角等 chrome。外观由上层 wrapper 自行组合。</p>
 */
public final class SceneButtonPrimitive {

    /**
     * 纯静态工厂，禁止实例化。
     */
    private SceneButtonPrimitive() {
    }

    /**
     * Button primitive 输入契约 —— 只包含行为所需数据，不包含 chrome 字段。
     *
     * @param label                文本内容（响应式只读）
     * @param enabled              是否启用
     * @param onClick              动作输出回调
     * @param stopClickPropagation CLICK 是否止于本控件（true = 不向祖先链冒泡）。
     *                             <b>复合容器内的按钮应声明 true</b>：CLICK 沿命中链
     *                             target→bubble 派发，容器常在祖先上挂「整行 / 整卡点击」
     *                             处理器，不声明就会「点按钮同时触发容器动作」（真机案例：
     *                             列表行的行内删除按钮同时选中该行）。语义与 enabled 无关
     *                             ——按钮区域的事件边界由本声明决定，不由禁用态决定。
     */
    @Desugar
    public record Props(
        ReadableSignal<String> label,
        ReadableSignal<Boolean> enabled,
        Runnable onClick,
        boolean stopClickPropagation
    ) {

        /** 三参便捷构造：CLICK 照常冒泡（既有行为，既有调用点零改动）。 */
        public Props(ReadableSignal<String> label, ReadableSignal<Boolean> enabled, Runnable onClick) {
            this(label, enabled, onClick, false);
        }
    }

    /**
     * Button primitive 创建结果，暴露无样式结构节点和派生交互状态。
     *
     * @param root        根节点
     * @param label       文本节点
     * @param interaction 交互状态
     */
    @Desugar
    public record Result(
        SceneNode root,
        SceneNode label,
        SceneInteractionState interaction
    ) {
    }

    /**
     * 创建无样式 Button primitive。
     *
     * @param rt    场景运行时
     * @param props primitive 输入契约
     * @return 创建结果，供 wrapper 或高级控件挂载样式
     */
    public static Result create(SceneRuntime rt, Props props) {
        SceneNode root = SceneNode.row();
        root.setMainAxisAlign(MainAxisAlign.CENTER);
        root.setCrossAxisAlign(CrossAxisAlign.CENTER);
        root.setClipChildren(true);

        SceneNode labelNode = new SceneNode();
        labelNode.setHitTestable(false);
        root.appendChild(labelNode);

        // 标签文字字号沿父链继承控件根的层 2 声明（不再逐点接线），此处只绑定文本内容。
        rt.bindText(labelNode, props.label());

        SceneInteractionState is = rt.interactionState(root);
        // ★ 时序契约（见 SceneInteractionState#focused javadoc）：focused() 是懒创建，必须在任何
        // writeFocused 之前声明关心，否则 Router 的写入因 signal 尚未创建而 null 短路，
        // 现象是「requestFocus 调了，focused 却恒 false」。焦点是本 primitive 的职责，
        // 声明就归它，不能让每个 wrapper 各自补一次。
        is.focused();
        is.pressed();
        rt.__registerButtonKeyboardPress(root, props.enabled());

        rt.focusable(root, props.enabled());
        rt.on(root, SceneEventType.CLICK, (ev, ctx) -> {
            if (Boolean.TRUE.equals(props.enabled().get())) {
                props.onClick().run();
            }
            // 止冒泡在动作之后无条件执行：同一节点的 handler 全部执行完才判定是否继续冒泡，
            // 故按钮自身动作不丢，只是祖先（行 / 卡容器）不再收到 CLICK。
            if (props.stopClickPropagation()) {
                ctx.stopPropagation();
            }
        });

        rt.on(root, SceneEventType.KEY_DOWN, (ev, ctx) -> {
            SceneKey key = ev.getKey();
            if ((key == SceneKey.ENTER || key == SceneKey.SPACE)
                && Boolean.TRUE.equals(props.enabled().get())) {
                props.onClick().run();
            }
        });

        return new Result(root, labelNode, is);
    }
}
