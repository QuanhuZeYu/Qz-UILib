package club.heiqi.uilib.ui.scene.control;

import java.util.function.Consumer;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * SceneToggleablePrimitive —— 无样式双向布尔控件行为核心。
 *
 * <p>该 primitive 只负责 checkbox/toggle 共有的结构、受控布尔切换行为、焦点注册、
 * 标签文本布局绑定与交互态暴露，不设置任何尺寸、颜色、边框、圆角、padding、cursor 或 gap。</p>
 */
public final class SceneToggleablePrimitive {

    /** 纯静态工厂，禁止实例化。 */
    private SceneToggleablePrimitive() {
    }

    /**
     * Toggleable primitive 输入契约 —— 当前值由外部只读 signal 驱动，交互经 onChange 交还期望新值。
     *
     * @param value    当前布尔值（响应式只读，受控源）
     * @param label    标签文本（响应式只读）
     * @param enabled  是否启用
     * @param onChange 切换回调，激活时以 {@code !value.get()} 调用
     */
    @Desugar
    public record Props(
            ReadableSignal<Boolean> value,
            ReadableSignal<String> label,
            ReadableSignal<Boolean> enabled,
            Consumer<Boolean> onChange
    ) {
    }

    /**
     * Toggleable primitive 创建结果，暴露无样式结构节点和交互派生态。
     *
     * @param root      交互根节点
     * @param indicator 指示器节点，供 wrapper 挂 checkbox box 或 toggle track chrome
     * @param labelNode 标签文本节点
     * @param pressed   是否按压中
     * @param hovered   是否悬停中
     */
    @Desugar
    public record Result(
            SceneNode root,
            SceneNode indicator,
            SceneNode labelNode,
            ReadableSignal<Boolean> pressed,
            ReadableSignal<Boolean> hovered
    ) {
    }

    /**
     * 创建无样式 Toggleable primitive。
     *
     * @param rt    场景运行时
     * @param props primitive 输入契约
     * @return 创建结果，供 wrapper 挂载样式
     */
    public static Result create(SceneRuntime rt, Props props) {
        SceneNode root = new SceneNode();
        root.setFlexDirection(FlexDirection.ROW);
        root.setCrossAxisAlign(CrossAxisAlign.CENTER);

        SceneNode indicator = new SceneNode();
        indicator.setHitTestable(false);
        root.appendChild(indicator);

        SceneNode labelNode = new SceneNode();
        labelNode.setHitTestable(false);
        root.appendChild(labelNode);

        rt.bindText(labelNode, props.label());

        SceneInteractionState is = rt.interactionState(root);

        rt.focusable(root);
        rt.on(root, SceneEventType.CLICK, (ev, ctx) -> {
            if (Boolean.TRUE.equals(props.enabled().get())) {
                props.onChange().accept(!Boolean.TRUE.equals(props.value().get()));
            }
        });
        rt.on(root, SceneEventType.KEY_DOWN, (ev, ctx) -> {
            SceneKey key = ev.getKey();
            if ((key == SceneKey.ENTER || key == SceneKey.SPACE)
                    && Boolean.TRUE.equals(props.enabled().get())) {
                props.onChange().accept(!Boolean.TRUE.equals(props.value().get()));
            }
        });

        return new Result(root, indicator, labelNode, is.pressed(), is.hovered());
    }
}
