package club.heiqi.uilib.ui.scene.runtime;
import club.heiqi.uilib.ui.scene.node.SceneNode;
/** 本次挂载句柄，detach 后结束；永久销毁通过 destroy 明确表达。 */
public final class MountHandle {
    private final SceneRuntime runtime;
    private final SceneNode node;
    private final Object token;
    MountHandle(SceneRuntime runtime, SceneNode node, Object token) { this.runtime = runtime; this.node = node; this.token = token; }
    public SceneNode node() { return node; }
    boolean belongsTo(SceneRuntime candidate) { return runtime == candidate; }
    /** 当前仍是同一次挂载；UUID或同节点重挂载不能使旧句柄恢复。域内读取。 */
    public boolean active() { return runtime.isCurrentMount(node, token); }
    public void detach() { runtime.detach(node, token); }
    public void moveTo(SceneNode parent) { runtime.move(node, token, parent); }
    public void destroy() { runtime.destroy(node, token); }
}
