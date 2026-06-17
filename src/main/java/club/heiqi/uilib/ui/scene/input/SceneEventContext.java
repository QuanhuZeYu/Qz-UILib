package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 事件派发上下文。
 *
 * <p>一条 route 派发链共享同一 context 实例。handler 可通过
 * {@link #stopPropagation()} 阻止事件继续向上冒泡，通过
 * {@link #getCurrentNode()} 获取当前派发游标所在的祖先节点。</p>
 *
 * <p>{@link #requestFocus()} 与 {@link #requestPointerCapture()} 为 I4 占位，
 * 当前调用无副作用。</p>
 */
public class SceneEventContext {

    /** 当前派发游标（target 阶段 = 最深目标，bubble 阶段 = 逐级祖先） */
    private SceneNode currentNode;

    /** 是否已停止冒泡 */
    private boolean propagationStopped;

    /**
     * 包级构造器，由 {@link SceneInputRouter} 创建。
     */
    SceneEventContext() {
        this.propagationStopped = false;
    }

    /**
     * 停止事件冒泡。
     *
     * <p>调用后，事件不再向更上层祖先派发，但当前节点已注册的多 handler 仍会全部跑完。</p>
     */
    public void stopPropagation() {
        this.propagationStopped = true;
    }

    /**
     * @return 是否已停止冒泡
     */
    public boolean isPropagationStopped() {
        return propagationStopped;
    }

    /**
     * 获取当前派发游标所在的节点。
     *
     * <p>target 阶段返回最深命中节点；bubble 阶段返回当前冒泡到的祖先。</p>
     *
     * @return 当前派发节点
     */
    public SceneNode getCurrentNode() {
        return currentNode;
    }

    /**
     * 设置当前派发节点（包级，仅 router 使用）。
     */
    void setCurrentNode(SceneNode node) {
        this.currentNode = node;
    }

    /**
     * 请求焦点（I4 占位，当前调用无副作用）。
     */
    public void requestFocus() {
        // I4 实现
    }

    /**
     * 请求指针捕获（I4 占位，当前调用无副作用）。
     */
    public void requestPointerCapture() {
        // I4 实现
    }
}
