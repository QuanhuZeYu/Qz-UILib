package club.heiqi.uilib.ui.scene.runtime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Objects;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 呈现与挂载生命周期；业务状态不归属于运行时。 */
public final class SceneRuntime implements AutoCloseable {
    private static final class Mount {
        final Object token = new Object();
        final Owner owner;
        final List<BindingActivation> bindings = new ArrayList<>();
        Mount(Owner owner) { this.owner = owner; }
    }
    /** 视图位置属于挂载激活；声明scrollOffset仅在其值改变时作为显式跳转目标。 */
    private static final class ScrollPosition {
        int declaration, value, maximum;
    }
    private final Map<SceneNode, ScrollPosition> scrolling = new IdentityHashMap<>();
    private final ScenePointerRouter pointerRouter;
    private final SceneFocusRouter focusRouter;
    private final ReactiveDomain domain;
    private final Owner owner;
    private final Map<SceneNode, Mount> mounts = new IdentityHashMap<>();
    private final List<SceneNode> roots = new ArrayList<>();
    private final Set<SceneNode> dirtyBindings = new LinkedHashSet<>();
    private boolean mutation;
    private boolean routing;
    private boolean disposed;
    private long revision, geometryRevision;
    public SceneRuntime(ReactiveDomain domain) { this.domain = domain; domain.checkAccess(); owner = domain.owner(); pointerRouter = new ScenePointerRouter(this); focusRouter = new SceneFocusRouter(this); }
    public ReactiveDomain domain() { return domain; }
    /** 同runtime的帧管线共享唯一pointer owner；不在宿主建立另一套手势权威。 */
    public ScenePointerRouter pointerRouter() { domain.checkAccess(); return pointerRouter; }
    /** 同runtime唯一焦点权威；宿主与业务只观察投影或发受控命令。 */
    public SceneFocusRouter focusRouter() { domain.checkAccess(); return focusRouter; }
    /** 焦点资格声明变化不改变几何，仍立即释放已经失活的焦点。 */
    public void invalidateInteraction() { domain.checkAccess(); revision++; pointerRouter.revalidate(); focusRouter.revalidate(); }
    /** 为输入所有权捕获当前挂载身份；同runtime移动保留，detach/remount更换token。 */
    public MountHandle captureMount(SceneNode node) {
        checkAlive(); Mount mount = mounts.get(node);
        if (mount == null) throw new IllegalArgumentException("节点未挂载");
        return new MountHandle(this, node, mount.token);
    }
    boolean isCurrentMount(SceneNode node, Object token) {
        domain.checkAccess(); Mount mount = mounts.get(node);
        return !disposed && mount != null && mount.token == token;
    }
    public List<SceneNode> roots() { domain.checkAccess(); return Collections.unmodifiableList(roots); }
    public long revision() { domain.checkAccess(); return revision; }
    public long geometryRevision() { domain.checkAccess(); return geometryRevision; }
    public void invalidate() {
        domain.checkAccess(); revision++; geometryRevision++;
        // attach/move/detach及可见/启用/模态声明生效时取消失活owner，不等下一帧。
        pointerRouter.revalidate();
        focusRouter.revalidate();
    }
    public void invalidatePaint() { domain.checkAccess(); revision++; }
    public void invalidateBinding(SceneNode node) { domain.checkAccess(); dirtyBindings.add(node); }
    public void checkMutation() {
        checkAlive(); if (!mutation) throw new IllegalStateException("挂载后声明仅由呈现运行时更新");
    }
    private void checkAlive() { domain.checkAccess(); if (disposed) throw new IllegalStateException("场景运行时已释放"); }
    private void command() { checkAlive(); if (routing) throw new IllegalStateException("输入操作仅发布状态或意图"); }
    public MountHandle attach(SceneNode parent, SceneNode root) {
        command(); Objects.requireNonNull(root, "root");
        if (parent != null && !mounts.containsKey(parent)) throw new IllegalArgumentException("父节点未挂载");
        if (root.parent() != null || root.runtime() != null || root.destroyed()) throw new IllegalArgumentException("节点不可重复挂载");
        validateSubtree(root);
        mutation = true;
        try {
            root.frameworkParent(this, parent);
            if (parent == null) roots.add(root);
            mountSubtree(root, parent == null ? owner : mounts.get(parent).owner);
            invalidate();
            return new MountHandle(this, root, mounts.get(root).token);
        } catch (RuntimeException | Error failure) {
            Mount partial = mounts.get(root);
            if (partial != null) {
                try { partial.owner.dispose(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
                releaseSubtree(root);
            }
            roots.remove(root); root.frameworkParent(this, null);
            throw failure;
        } finally { mutation = false; }
    }
    private void validateSubtree(SceneNode node) {
        if (node.destroyed() || node.runtime() != null) throw new IllegalArgumentException("子树已挂载或销毁");
        for (SceneNode child : node.children()) validateSubtree(child);
    }
    private void mountSubtree(SceneNode node, Owner parentOwner) {
        Mount mount = new Mount(parentOwner.child()); mounts.put(node, mount); node.frameworkRuntime(this, true);
        for (NodeBinding binding : node.bindings()) {
            BindingActivation active = binding.activate(this, node, mount.owner);
            mount.bindings.add(active); mount.owner.own(active);
        }
        if (node.control() != null) {
            Observation dependencies = domain.observe(mount.owner, () -> {
                for (ReadableSignal<?> source : node.control().measureDependencies(node)) source.get();
            }, () -> { invalidateBinding(node); invalidate(); });
            mount.bindings.add(new BindingActivation() {
                @Override public void refresh() { dependencies.refresh(); }
                @Override public void close() { dependencies.close(); }
            });
        }
        dirtyBindings.add(node);
        for (SceneNode child : node.children()) mountSubtree(child, mount.owner);
    }
    public void preparePresentation() {
        command(); mutation = true;
        try {
            while (!dirtyBindings.isEmpty()) {
                SceneNode node = dirtyBindings.iterator().next(); dirtyBindings.remove(node);
                Mount mount = mounts.get(node);
                if (mount != null) for (BindingActivation active : mount.bindings) active.refresh();
            }
        } finally { mutation = false; }
    }
    private Mount requireMount(SceneNode node, Object token) {
        command(); Mount mount = mounts.get(node);
        if (mount == null || mount.token != token) throw new IllegalStateException("挂载句柄已结束");
        return mount;
    }
    void detach(SceneNode node, Object token) {
        Mount mount = requireMount(node, token); mutation = true;
        try {
            try { mount.owner.dispose(); }
            finally {
                releaseSubtree(node); roots.remove(node); node.frameworkParent(this, null); invalidate();
            }
        } finally { mutation = false; }
    }
    private void releaseSubtree(SceneNode node) {
        for (SceneNode child : node.children()) releaseSubtree(child);
        mounts.remove(node); scrolling.remove(node); dirtyBindings.remove(node); node.frameworkRuntime(this, false);
    }
    void move(SceneNode node, Object token, SceneNode next) {
        Mount mount = requireMount(node, token);
        if (next != null && !mounts.containsKey(next)) throw new IllegalArgumentException("目标父节点未挂载");
        for (SceneNode cursor = next; cursor != null; cursor = cursor.parent())
            if (cursor == node) throw new IllegalArgumentException("节点不能包含自身");
        mutation = true;
        try {
            mount.owner.moveTo(next == null ? owner : mounts.get(next).owner);
            roots.remove(node); node.frameworkParent(this, next);
            if (next == null) roots.add(node);
            invalidate();
        } finally { mutation = false; }
    }
    void destroy(SceneNode node, Object token) {
        detach(node, token); mutation = true;
        try { destroySubtree(node); } finally { mutation = false; }
    }
    private void destroySubtree(SceneNode node) {
        for (SceneNode child : new ArrayList<>(node.children())) {
            destroySubtree(child); child.frameworkParent(this, null);
        }
        node.frameworkDestroy(this);
    }
    /** 声明子序列更新；保留节点不卸载，移除节点结束挂载，新增节点激活当前来源。 */
    public void reconcile(SceneNode parent, List<SceneNode> declared) {
        checkMutation();
        if (!mounts.containsKey(parent)) throw new IllegalArgumentException("父节点未挂载");
        List<SceneNode> next = new ArrayList<>(declared);
        Set<SceneNode> unique = Collections.newSetFromMap(new IdentityHashMap<SceneNode, Boolean>());
        for (SceneNode node : next) {
            if (!unique.add(node)) throw new IllegalArgumentException("声明子序列重复节点");
            if (node.parent() != parent) {
                if (node.parent() != null || node.runtime() != null) throw new IllegalArgumentException("新增节点已有归属");
                validateSubtree(node);
                if (node == parent) throw new IllegalArgumentException("节点不能包含自身");
            }
        }
        for (SceneNode child : new ArrayList<>(parent.children())) {
            if (!unique.contains(child)) {
                Mount mount = mounts.get(child);
                try { mount.owner.dispose(); }
                finally { releaseSubtree(child); child.frameworkParent(this, null); }
            }
        }
        for (SceneNode node : next) {
            boolean fresh = node.runtime() == null;
            node.frameworkParent(this, parent);
            if (fresh) mountSubtree(node, mounts.get(parent).owner);
        }
        invalidate();
    }
    public boolean interactive(SceneNode node) {
        checkAlive(); if (!mounts.containsKey(node)) return false;
        for (SceneNode cursor = node; cursor != null; cursor = cursor.parent()) if (!cursor.enabled() || !cursor.visible()) return false;
        SceneNode modal = activeModal();
        if (modal != null) {
            for (SceneNode cursor = node; cursor != null; cursor = cursor.parent()) if (cursor == modal) return true;
            return false;
        }
        return true;
    }
    private SceneNode activeModal() {
        for (int i = roots.size() - 1; i >= 0; i--) { SceneNode found = findModal(roots.get(i)); if (found != null) return found; }
        return null;
    }
    private SceneNode findModal(SceneNode node) {
        if (!node.visible()) return null;
        for (int i = node.children().size() - 1; i >= 0; i--) { SceneNode found = findModal(node.children().get(i)); if (found != null) return found; }
        return node.modal() ? node : null;
    }
    /** 已知逻辑目标的完整synthetic activation兼容入口；物理输入应经帧管线的typed事件。
     * 有物理按钮占位时吞掉，不能绕过已有手势；仍按当前挂载与输入范围检查目标。 */
    public void dispatchClick(SceneNode node) {
        command(); if (!pointerRouter.idle()) return;
        dispatchPointerClick(node);
    }
    // 只有同包唯一router可在UP时激活第一owner；第二键仍按下不影响第一owner正常结束。
    void dispatchPointerClick(SceneNode node) {
        command(); if (!interactive(node) || node.clickAction() == null) return;
        routing = true;
        try { node.clickAction().run(); } finally { routing = false; }
    }
    /** 只读布局取得本次目标；不在测量期间修改状态。 */
    public int scrollOffset(SceneNode node) {
        checkAlive();
        ScrollPosition position = scrolling.get(node);
        return position == null || position.declaration != node.scrollOffset() ? node.scrollOffset() : position.value;
    }
    /** 从最终几何发布边界；内容/窗口变小会立即丢弃越界偏移，不积攒不可见滚动债务。 */
    public void prepareScrollPositions(club.heiqi.uilib.ui.scene.layout.SceneGeometryView geometry) {
        command();
        for (club.heiqi.uilib.ui.scene.layout.SceneGeometryView.Entry entry : geometry.ordered()) {
            if (!entry.node.scrollable() || !mounts.containsKey(entry.node)) continue;
            ScrollPosition position = scrolling.computeIfAbsent(entry.node, ignored -> new ScrollPosition());
            position.declaration = entry.node.scrollOffset(); position.value = entry.scrollOffset; position.maximum = entry.scrollMaximum;
        }
    }
    public void dispatchScroll(SceneNode node, int delta) {
        checkAlive(); if (!interactive(node)) return;
        if (node.scrollable()) {
            ScrollPosition position = scrolling.get(node);
            if (position != null) {
                int next = (int) Math.max(0L, Math.min(position.maximum, (long) position.value - (long) delta * node.scrollStep()));
                if (next != position.value) { position.value = next; invalidate(); }
            }
        }
        if (node.scrollAction() == null) return;
        routing = true;
        try { node.scrollAction().accept(delta); } finally { routing = false; }
    }
    @Override public void close() {
        domain.checkAccess(); if (disposed) return;
        command();
        pointerRouter.close();
        focusRouter.close();
        RuntimeException failure = null;
        for (SceneNode root : new ArrayList<>(roots)) {
            try { detach(root, mounts.get(root).token); }
            catch (RuntimeException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
        }
        try { owner.dispose(); }
        catch (RuntimeException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
        finally { disposed = true; }
        if (failure != null) throw failure;
    }
}
