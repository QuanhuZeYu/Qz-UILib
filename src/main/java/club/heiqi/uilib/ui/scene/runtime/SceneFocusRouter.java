package club.heiqi.uilib.ui.scene.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** runtime唯一焦点权威。范围进入/返回按spec6.3.1选择；普通失活、清除不补选。
 * 复用声明树和挂载代际，恢复记录只在覆盖期间持有，不持有第二棵输入树。 */
public final class SceneFocusRouter {
    private static final class Range {
        final MountHandle modal; // null为页面；页面非模态根共享同一输入范围。
        MountHandle saved;
        boolean pending = true, returning;
        Range(MountHandle modal) { this.modal = modal; }
    }
    private final SceneRuntime runtime;
    private final Signal<SceneNode> value;
    private final ReadableSignal<SceneNode> view;
    private final List<Range> ranges = new ArrayList<>();
    private MountHandle owner;
    private boolean closed, rangesDirty = true;
    SceneFocusRouter(SceneRuntime runtime) {
        this.runtime = runtime;
        value = runtime.domain().signal(null);
        view = value::get; // 只读包装保留依赖追踪，不暴露可强转的写端。
        ranges.add(new Range(null));
    }
    /** 无焦点为null，读取只观察，不结算声明或执行选择。 */
    public ReadableSignal<SceneNode> focused() { runtime.domain().checkAccess(); return view; }
    /** 域内受控请求，可从handler调用；无效/异runtime/旧token返回false，不激活业务。 */
    public boolean request(MountHandle target) {
        runtime.domain().checkWrite(); Objects.requireNonNull(target, "target");
        if (closed) return false;
        revalidate();
        if (!local(target) || !runtime.interactive(target.node())) return false;
        cancelPending();
        setOwner(target);
        return true;
    }
    /** 清除当前焦点并取消当前待进入选择；不取消被覆盖外层的合法恢复记录。 */
    public void clear() {
        runtime.domain().checkWrite();
        if (!closed) cancelPending();
        setOwner(null);
    }
    private void cancelPending() { Range range = top(); range.pending = false; range.saved = null; range.returning = false; }
    private void setOwner(MountHandle target) { owner = target; value.set(target == null ? null : target.node()); }
    private Range top() { return ranges.get(ranges.size() - 1); }
    private boolean local(MountHandle target) {
        return target != null && target.belongsTo(runtime) && target.active() && target.node().focusable() && runtime.locallyInteractive(target.node());
    }
    private boolean contains(Range range, SceneNode target) {
        if (range.modal == null) return true;
        for (SceneNode node = target; node != null; node = node.parent()) if (node == range.modal.node()) return true;
        return false;
    }
    void rangeChanged() { runtime.domain().checkAccess(); rangesDirty = true; }
    /** 真实资格变化即时释放及取消保存记录；恢复资格不复活已取消的token。 */
    void revalidate() {
        runtime.domain().checkAccess(); if (closed) return;
        for (Range range : ranges) if (range.saved != null && (!local(range.saved) || !contains(range, range.saved.node()))) range.saved = null;
        if (rangesDirty) { rangesDirty = false; syncRanges(); }
        if (owner != null && (!local(owner) || !runtime.interactive(owner.node()))) setOwner(null);
    }
    private boolean alive(Range range) {
        return range.modal.active() && range.modal.node().modal() && runtime.visibleInTree(range.modal.node());
    }
    private int index(SceneNode modal) {
        for (int i = 1; i < ranges.size(); i++) if (ranges.get(i).modal.node() == modal && ranges.get(i).modal.active()) return i;
        return -1;
    }
    private void syncRanges() {
        // 隐藏或卸载外层时，树归属使其所属内层也失活；独立根模态可各自结束。
        Range previousTop = top();
        for (int i = ranges.size() - 1; i > 0; i--) if (!alive(ranges.get(i))) ranges.remove(i);
        SceneNode active = runtime.activeModal();
        boolean entering = false;
        if (active == null) {
            if (ranges.size() > 1) ranges.subList(1, ranges.size()).clear();
        } else {
            int found = index(active);
            if (found >= 0) {
                if (ranges.size() > found + 1) ranges.subList(found + 1, ranges.size()).clear();
            } else {
                Range previous = top();
                // 未显示的暂态范围没有owner时，不能覆盖合法旧记录；已结束范围的owner也不能写进外层。
                if (previous == previousTop && local(owner) && contains(previous, owner.node())) previous.saved = owner;
                List<SceneNode> chain = new ArrayList<>();
                for (SceneNode node = active; node != null; node = node.parent()) if (node.modal() && index(node) < 0) chain.add(0, node);
                for (SceneNode node : chain) ranges.add(new Range(runtime.captureMount(node)));
                entering = true;
            }
        }
        // 只删除被覆盖记录不会改变前台范围，不能重选其焦点或覆盖主动clear。
        // modal属性关闭后目标可能仍是非模态可交互节点，但真正返回仍释放旧owner。
        if (top() != previousTop) {
            top().pending = true; top().returning = !entering;
            if (owner != null) setOwner(null);
        }
        if (runtime.roots().isEmpty()) { ranges.clear(); ranges.add(new Range(null)); }
    }
    /** 声明结算结束才选目标；无待转换则不遍历候选树，清除不会被下一帧补回。 */
    void prepareScopes() {
        runtime.domain().checkWrite(); if (closed) return;
        revalidate(); Range range = top();
        if (!range.pending || runtime.roots().isEmpty()) return;
        range.pending = false;
        SceneNode policy = range.modal == null ? pagePolicy() : range.modal.node();
        MountHandle next = null;
        if (owner != null) next = owner; // 尚未prepare前的显式请求优先保留。
        if (next == null && range.returning && policy != null) next = candidate(range, policy.restoreFocus());
        if (next == null && range.returning && local(range.saved) && contains(range, range.saved.node()) && runtime.interactive(range.saved.node())) next = range.saved;
        if (next == null && policy != null) next = candidate(range, policy.initialFocus());
        if (next == null) {
            if (range.modal != null) next = first(range, range.modal.node());
            else for (SceneNode root : runtime.roots()) { next = first(range, root); if (next != null) break; }
        }
        range.saved = null; range.returning = false;
        setOwner(next);
    }
    private SceneNode pagePolicy() {
        for (SceneNode root : runtime.roots()) if (!root.modal()) return root;
        return null;
    }
    private MountHandle candidate(Range range, SceneNode node) {
        return node != null && node.runtime() == runtime && node.focusable() && contains(range, node) && runtime.interactive(node) ? runtime.captureMount(node) : null;
    }
    private MountHandle first(Range range, SceneNode node) {
        if (!runtime.locallyInteractive(node)) return null;
        MountHandle found = candidate(range, node); if (found != null) return found;
        for (SceneNode child : node.children()) { found = first(range, child); if (found != null) return found; }
        return null;
    }
    void close() {
        runtime.domain().checkWrite(); if (closed) return;
        setOwner(null); ranges.clear(); closed = true;
    }
}
