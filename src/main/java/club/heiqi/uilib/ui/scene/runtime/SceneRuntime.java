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
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import java.util.function.Supplier;

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
    /** 迟到输入与宿主结果只读检查生命周期，不推进声明。 */
    public boolean alive() { domain.checkAccess(); return !disposed; }
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
    /** 结构/显隐/启用/模态变化才重验范围；普通文案和布局不触发重新选择。 */
    public void invalidateRange() { domain.checkAccess(); focusRouter.rangeChanged(); invalidate(); }
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
            invalidateRange();
            return new MountHandle(this, root, mounts.get(root).token);
        } catch (RuntimeException | Error failure) {
            Mount partial = mounts.get(root);
            if (partial != null) {
                try { partial.owner.dispose(); } catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
                releaseSubtree(root);
            }
            roots.remove(root); root.frameworkParent(this, null);
            // 声明激活可在失败前显露模态；卸载部分挂载后也要结束该范围，不能留下幽灵输入范围。
            try { invalidateRange(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
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
            do {
                while (!dirtyBindings.isEmpty()) {
                    SceneNode node = dirtyBindings.iterator().next(); dirtyBindings.remove(node);
                    Mount mount = mounts.get(node);
                    if (mount != null) for (BindingActivation active : mount.bindings) active.refresh();
                }
                // 进入选择依赖已结算声明；选择的响应投影再结算绑定，首次回放即可显示。
                focusRouter.prepareScopes();
            } while (!dirtyBindings.isEmpty());
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
                releaseSubtree(node); roots.remove(node); node.frameworkParent(this, null); invalidateRange();
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
            invalidateRange();
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
        invalidateRange();
    }
    public boolean interactive(SceneNode node) {
        checkAlive(); if (!locallyInteractive(node)) return false;
        SceneNode modal = activeModal();
        if (modal != null) {
            for (SceneNode cursor = node; cursor != null; cursor = cursor.parent()) if (cursor == modal) return true;
            return false;
        }
        return true;
    }
    /** 被模态覆盖仍可保留恢复资格，显隐/禁用失活则立即取消；不把隔离当作禁用。 */
    boolean locallyInteractive(SceneNode node) {
        domain.checkAccess(); if (!mounts.containsKey(node)) return false;
        for (SceneNode cursor = node; cursor != null; cursor = cursor.parent()) if (!cursor.enabled() || !cursor.visible()) return false;
        return true;
    }
    boolean visibleInTree(SceneNode node) {
        domain.checkAccess(); if (!mounts.containsKey(node)) return false;
        for (SceneNode cursor = node; cursor != null; cursor = cursor.parent()) if (!cursor.visible()) return false;
        return true;
    }
    SceneNode activeModal() {
        for (int i = roots.size() - 1; i >= 0; i--) { SceneNode found = findModal(roots.get(i)); if (found != null) return found; }
        return null;
    }
    private SceneNode findModal(SceneNode node) {
        // 挂载阶段声明树可以含尚未取得token的子节点，它们不能成为有效输入范围。
        if (!mounts.containsKey(node) || !node.visible()) return null;
        for (int i = node.children().size() - 1; i >= 0; i--) { SceneNode found = findModal(node.children().get(i)); if (found != null) return found; }
        return node.modal() ? node : null;
    }
    /** 已知逻辑目标的完整synthetic activation兼容入口；物理输入应经帧管线的typed事件。
     * 有物理按钮占位时吞掉，不能绕过已有手势；仍按当前挂载与输入范围检查目标。 */
    public void dispatchClick(SceneNode node) {
        command(); if (!pointerRouter.idle()) return;
        invokeClick(node);
    }
    // 只有同包唯一router可在UP时激活第一owner；第二键仍按下不影响第一owner正常结束。
    void dispatchPointerClick(SceneNode node) {
        command(); if (!interactive(node) || node.clickAction() == null) return;
        if (node.clickFocus() && node.focusable()) focusRouter.request(captureMount(node));
        invokeClick(node);
    }
    // 业务激活不改变焦点；clickFocus仅属于物理UP，synthetic与键盘命令保留owner。
    private void invokeClick(SceneNode node) {
        if (!interactive(node) || node.clickAction() == null) return;
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
    /** spec6.2：在最终布局后消费一次导航意图；重排由管线提供，不递归结算声明。
     * 每个祖先至多处理一次，滚动沿用唯一ScrollPosition，不持有第二套几何或滚动权威。 */
    public SceneGeometry revealNavigation(SceneGeometry geometry, Supplier<SceneGeometry> relayout) {
        domain.checkWrite(); command();
        MountHandle target = focusRouter.takeReveal();
        if (target == null) return geometry;
        for (SceneNode ancestor = target.node().parent(); ancestor != null; ancestor = ancestor.parent()) {
            if (!focusRouter.owns(target)) break;
            if (!ancestor.scrollable()) continue;
            SceneGeometryView.Entry entry = geometry.get(ancestor);
            LayoutBox region = revealRegion(target.node(), ancestor, geometry);
            if (entry == null || region == null || region.width == 0 || region.height == 0) continue;
            LayoutBox window = revealWindow(ancestor, geometry);
            if (window.width == 0 || window.height == 0) continue;
            boolean row = ancestor.axis() == SceneNode.Axis.ROW;
            long delta = revealDelta(row ? region.x : region.y, row ? region.width : region.height,
                row ? window.x : window.y, row ? window.width : window.height);
            ScrollPosition position = scrolling.get(ancestor);
            if (position == null || !mounts.containsKey(ancestor)) continue;
            int next = (int) Math.max(0L, Math.min(entry.scrollMaximum, (long) entry.scrollOffset + delta));
            if (next == position.value) continue;
            position.value = next; invalidate();
            // 内层移动改变目标及裁剪后的可见部分；必须用重排后的同代几何求外层，
            // 最后一次也重排，确保paint和下一事件hit均消费实际偏移后的区域。
            geometry = Objects.requireNonNull(relayout.get(), "geometry");
        }
        return geometry;
    }
    private static LayoutBox innerBox(SceneGeometryView.Entry entry) {
        int padding = entry.node.padding();
        return new LayoutBox(entry.box.x + padding, entry.box.y + padding,
            (int) Math.max(0L, (long) entry.box.width - 2L * padding),
            (int) Math.max(0L, (long) entry.box.height - 2L * padding));
    }
    private static LayoutBox revealRegion(SceneNode target, SceneNode ancestor, SceneGeometryView geometry) {
        SceneGeometryView.Entry entry = geometry.get(target);
        if (entry == null) return null;
        LayoutBox region = entry.box;
        // 下层裁剪随当前祖先的内容一起移动，外层只能显露其中尚可见的部分。
        for (SceneNode node = target.parent(); node != ancestor; node = node.parent()) {
            if (node == null) return null;
            SceneGeometryView.Entry clip = geometry.get(node);
            if (clip == null) return null;
            if (node.scrollable()) region = region.intersect(innerBox(clip));
            else if (node.clip()) region = region.intersect(clip.box);
        }
        return region;
    }
    private static LayoutBox revealWindow(SceneNode ancestor, SceneGeometryView geometry) {
        LayoutBox window = innerBox(geometry.get(ancestor));
        boolean movableX = false, movableY = false;
        for (SceneNode node = ancestor.parent(); node != null; node = node.parent()) {
            // 外层只能修复自身滚动轴的遮挡；正交轴仍需约束内层窗口。
            // 某轴可由外层移动后，其自身及更上层当前clip不提前卡住屏外内层。
            if (node.scrollable()) {
                if (node.axis() == SceneNode.Axis.ROW) movableX = true;
                else movableY = true;
            }
            SceneGeometryView.Entry entry = geometry.get(node);
            if (entry != null && (node.clip() || node.scrollable()))
                window = limitWindow(window, node.scrollable() ? innerBox(entry) : entry.box, !movableX, !movableY);
        }
        return limitWindow(window, geometry.viewport(), !movableX, !movableY);
    }
    private static LayoutBox limitWindow(LayoutBox window, LayoutBox clip, boolean x, boolean y) {
        int left = x ? Math.max(window.x, clip.x) : window.x;
        int top = y ? Math.max(window.y, clip.y) : window.y;
        long right = x ? Math.min((long) window.x + window.width, (long) clip.x + clip.width) : (long) window.x + window.width;
        long bottom = y ? Math.min((long) window.y + window.height, (long) clip.y + clip.height) : (long) window.y + window.height;
        return new LayoutBox(left, top, (int) Math.max(0L, right - left), (int) Math.max(0L, bottom - top));
    }
    private static long revealDelta(long start, long size, long windowStart, long windowSize) {
        long end = start + size, windowEnd = windowStart + windowSize;
        // 大目标没有内部焦点位置时显露起始边缘；已经跨越整个视口则保持，
        // 避免反复导航在两端跳动。普通目标仅补足被遮住的一侧。
        if (size > windowSize) return start <= windowStart && end >= windowEnd ? 0 : start - windowStart;
        if (start < windowStart) return start - windowStart;
        return end > windowEnd ? end - windowEnd : 0;
    }
    /** spec6.1：当前模态先处理，未消费才进入页面策略。调用前结算声明；
     * 页面采用与初始焦点相同的首个非模态根，后台模态不接收本事件。
     * 本接口只返回消费结果，宿主fallback由管线在Effects成功后执行。 */
    public boolean dispatchEscape() {
        domain.checkWrite(); command();
        SceneNode modal = activeModal();
        if (modal != null && locallyInteractive(modal) && escape(modal)) return true;
        for (SceneNode root : roots) if (!root.modal()) {
            return locallyInteractive(root) && escape(root);
        }
        return false;
    }
    private boolean escape(SceneNode node) {
        if (node.escapeAction() == null) return false;
        routing = true;
        try { return node.escapeAction().getAsBoolean(); } finally { routing = false; }
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
        Throwable failure = null;
        for (SceneNode root : new ArrayList<>(roots)) {
            try { detach(root, mounts.get(root).token); }
            catch (RuntimeException | Error e) { if (failure == null) failure = e; else if (e != failure) failure.addSuppressed(e); }
        }
        try { owner.dispose(); }
        catch (RuntimeException | Error e) { if (failure == null) failure = e; else if (e != failure) failure.addSuppressed(e); }
        finally { disposed = true; }
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
    }
}
