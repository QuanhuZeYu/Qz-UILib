package club.heiqi.uilib.ui.scene.runtime;

import java.util.Objects;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** runtime唯一焦点权威；当前挂载身份与只读响应投影分别承担资格和呈现。
 * 显式请求不激活业务，失活释放不提交编辑；初始选择、导航和恢复策略留给后续切片。 */
public final class SceneFocusRouter {
    private final SceneRuntime runtime;
    private final Signal<SceneNode> value;
    private final ReadableSignal<SceneNode> view;
    private MountHandle owner;
    private boolean closed;

    SceneFocusRouter(SceneRuntime runtime) {
        this.runtime = runtime;
        value = runtime.domain().signal(null);
        // 不直接返回Signal，防止调用者强转取得焦点写端。
        view = value::get;
    }
    /** 域内观察当前焦点；无焦点为null，读取不推进声明或布局。 */
    public ReadableSignal<SceneNode> focused() { runtime.domain().checkAccess(); return view; }
    /** 域内受控请求，可从输入handler调用；无效/异runtime/旧挂载返回false。
     * 资格检查使用已生效声明，拒绝请求保留原有合法焦点。null是调用错误。 */
    public boolean request(MountHandle target) {
        runtime.domain().checkWrite();
        Objects.requireNonNull(target, "target");
        if (closed) return false;
        revalidate();
        if (!target.belongsTo(runtime) || !target.active() || !target.node().focusable() || !runtime.interactive(target.node())) return false;
        owner = target;
        value.set(target.node());
        return true;
    }
    /** 域内明确清除；重复清除不通知，不激活/提交业务。 */
    public void clear() {
        runtime.domain().checkWrite();
        owner = null;
        value.set(null);
    }
    /** 只重验当前owner，避免为了焦点扫描/复制声明树；真实失活后恢复不会复活。 */
    void revalidate() {
        runtime.domain().checkAccess();
        if (!closed && owner != null && (!owner.active() || !owner.node().focusable() || !runtime.interactive(owner.node()))) clear();
    }
    void close() {
        runtime.domain().checkWrite();
        if (closed) return;
        clear();
        closed = true;
    }
}
