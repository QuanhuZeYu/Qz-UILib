package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 事件派发上下文。
 *
 * <p>一条 route 派发链共享同一 context 实例。handler 可通过
 * {@link #stopPropagation()} 阻止事件继续向上冒泡，通过
 * {@link #getCurrentNode()} 获取当前派发游标所在的祖先节点。</p>
 *
 * <p>{@link #requestFocus()} 将聚焦 ctx 构造时指定的事件 target（最深命中/焦点节点），
 * 不受 bubble 游标 currentNode 影响。{@link #requestPointerCapture()} 为 I4d 预留。
 * 这两个命令改的是 Router 的权威状态机，结果仍经 signal 暴露（I11 白名单②）。</p>
 */
public class SceneEventContext {

    /** 关联的路由器，用于 requestFocus 等受控命令 */
    private final SceneInputRouter router;
    /** 事件原始 target（最深命中/焦点节点），不随 bubble 游标变化 */
    private final SceneNode target;
    /** 当前派发游标（target 阶段 = 最深目标，bubble 阶段 = 逐级祖先） */
    private SceneNode currentNode;

    /** 是否已停止冒泡 */
    private boolean propagationStopped;

    /**
     * 包级构造器，由 {@link SceneInputRouter} 创建。
     *
     * @param router 输入路由器（用于受控命令如 requestFocus）
     * @param target 事件原始目标节点（requestFocus 聚焦此节点，非 currentNode）
     */
    SceneEventContext(SceneInputRouter router, SceneNode target) {
        this.router = router;
        this.target = target;
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
     * 请求焦点：将焦点赋予当前事件的目标节点（非 bubble 游标 currentNode）。
     *
     * <p>若 router 或 target 为 null，则无副作用短路。此命令改 Router 权威状态机，
     * 结果经 focus signal 暴露（I11 白名单②）。</p>
     */
    public void requestFocus() {
        if (router != null && target != null) {
            router.requestFocus(target);
        }
    }

    /**
     * 请求指针捕获（I4d 占位，当前调用无副作用）。
     */
    public void requestPointerCapture() {
        // I4d 实现
    }
}
