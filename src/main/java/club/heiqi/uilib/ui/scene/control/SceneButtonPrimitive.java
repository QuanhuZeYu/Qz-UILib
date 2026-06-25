package club.heiqi.uilib.ui.scene.control;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
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
     * @param label   文本内容（响应式只读）
     * @param enabled 是否启用
     * @param onClick 动作输出回调
     */
    @Desugar
    public record Props(
        ReadableSignal<String> label,
        ReadableSignal<Boolean> enabled,
        Runnable onClick
    ) {
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
        SceneNode root = new SceneNode();
        root.setFlexDirection(FlexDirection.ROW);
        root.setMainAxisAlign(MainAxisAlign.CENTER);
        root.setCrossAxisAlign(CrossAxisAlign.CENTER);
        root.setClipChildren(true);

        SceneNode labelNode = new SceneNode();
        labelNode.setHitTestable(false);
        root.appendChild(labelNode);

        rt.bindText(labelNode, props.label());

        SceneInteractionState is = rt.interactionState(root);

        rt.focusable(root, props.enabled());
        rt.on(root, SceneEventType.CLICK, (ev, ctx) -> {
            if (Boolean.TRUE.equals(props.enabled().get())) {
                props.onClick().run();
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
