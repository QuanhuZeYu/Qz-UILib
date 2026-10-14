package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReactiveDomain;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneLayer;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/** 呈现与挂载生命周期；业务状态不归属于运行时。 */
public final class SceneRuntime implements AutoCloseable {
    private static final class Mount {
        final Object token = new Object();
        final Owner owner;
        final List<BindingActivation> bindings = new ArrayList<>();
        final List<BindingActivation> transfers = new ArrayList<>();

        long measureRevision;

        Mount(Owner owner, long measureRevision) {
            this.owner = owner;
            this.measureRevision = measureRevision;
        }
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
    private long revision, geometryRevision, measureRevision, wholeMeasureRevision;
    private long nodeMeasureRevisionClock;
    // 只引用布局发布的唯一几何；声明结算与来源事实未齐时不推进范围转换。
    private SceneGeometryView inputGeometry;
    private boolean inputGeometryReady;
    private long layerPresenceRevision = -1, layerValidationRevision = -1;
    private boolean layersDeclared;
    private SceneNode logicalModal;
    private boolean logicalModalKnown;
    private club.heiqi.uilib.ui.scene.input.SceneCompositionSource compositionSource;
    private MountHandle compositionMount;
    private club.heiqi.uilib.ui.scene.edit.SceneEditState compositionState;
    private Object compositionBaseline;

    public SceneRuntime(ReactiveDomain domain) {
        this.domain = domain;
        domain.checkAccess();
        owner = domain.owner();
        pointerRouter = new ScenePointerRouter(this);
        focusRouter = new SceneFocusRouter(this);
    }

    public ReactiveDomain domain() {
        return domain;
    }

    /** 迟到输入与宿主结果只读检查生命周期，不推进声明。 */
    public boolean alive() {
        domain.checkAccess();
        return !disposed;
    }

    /** 同runtime的帧管线共享唯一pointer owner；不在宿主建立另一套手势权威。 */
    public ScenePointerRouter pointerRouter() {
        domain.checkAccess();
        return pointerRouter;
    }

    /** 同runtime唯一焦点权威；宿主与业务只观察投影或发受控命令。 */
    public SceneFocusRouter focusRouter() {
        domain.checkAccess();
        return focusRouter;
    }

    /** 焦点资格声明变化不改变几何，仍立即释放已经失活的焦点。 */
    public void invalidateInteraction() {
        domain.checkAccess();
        revision++;
        pointerRouter.revalidate();
        focusRouter.revalidate();
    }

    /** 为输入所有权捕获当前挂载身份；同runtime移动保留，detach/remount更换token。 */
    public MountHandle captureMount(SceneNode node) {
        checkAlive();
        Mount mount = mounts.get(node);
        if (mount == null) throw new IllegalArgumentException("节点未挂载");
        return new MountHandle(this, node, mount.token);
    }

    boolean isCurrentMount(SceneNode node, Object token) {
        domain.checkAccess();
        Mount mount = mounts.get(node);
        return !disposed && mount != null && mount.token == token;
    }

    public List<SceneNode> roots() {
        domain.checkAccess();
        return Collections.unmodifiableList(roots);
    }

    public long revision() {
        domain.checkAccess();
        return revision;
    }

    public long geometryRevision() {
        domain.checkAccess();
        return geometryRevision;
    }

    /** 供帧管线区分内容测量输入与纯视图位置；未知声明变化仍按测量失效。 */
    public long measureRevision() {
        domain.checkAccess();
        return measureRevision;
    }

    /** 无来源通知保留整体测量语义，供不能报告来源的扩展使用。 */
    public void invalidate() {
        domain.checkAccess();
        wholeMeasureRevision++;
        measureRevision++;
        invalidateArrangement();
    }

    /** 局部内容变更影响来源及祖先；共同几何仍由下一批完整控件回调建立。 */
    public void invalidate(SceneNode origin) {
        domain.checkAccess();
        if (origin == null || !mounts.containsKey(origin)) {
            invalidate();
            return;
        }
        long next = ++nodeMeasureRevisionClock;
        for (SceneNode node = origin; node != null; node = node.parent()) {
            Mount mount = mounts.get(node);
            if (mount == null) break;
            mount.measureRevision = next;
        }
        measureRevision++;
        invalidateArrangement();
    }

    public long wholeMeasureRevision() {
        domain.checkAccess();
        return wholeMeasureRevision;
    }

    /** 同节点卸载后重挂也不能复用上一次挂载的文字请求。 */
    public long nodeMeasureRevision(SceneNode node) {
        domain.checkAccess();
        Mount mount = mounts.get(node);
        if (mount == null) throw new IllegalArgumentException("节点未挂载到此运行时");
        return mount.measureRevision;
    }

    private void invalidateArrangement() {
        domain.checkAccess();
        clearLogicalModal();
        revision++;
        geometryRevision++;
        inputGeometryReady = false;
        // attach/move/detach及可见/启用/模态声明生效时取消失活owner，不等下一帧。
        pointerRouter.revalidate();
        focusRouter.revalidate();
    }

    /** 结构/显隐/启用/模态变化才重验范围；普通文案和布局不触发重新选择。 */
    public void invalidateRange() {
        domain.checkAccess();
        focusRouter.rangeChanged();
        invalidate();
    }

    public void invalidateRange(SceneNode origin) {
        domain.checkAccess();
        focusRouter.rangeChanged();
        invalidate(origin);
    }

    private void invalidateRemovedRange(SceneNode formerParent) {
        if (formerParent != null) invalidateRange(formerParent);
        else {
            // 移除根没有仍挂载的祖先；仍更新共同几何与输入范围，不能清理其他根的文字。
            focusRouter.rangeChanged();
            measureRevision++;
            invalidateArrangement();
        }
    }

    public void invalidatePaint() {
        domain.checkAccess();
        revision++;
    }

    public void invalidateBinding(SceneNode node) {
        domain.checkAccess();
        dirtyBindings.add(node);
    }

    public void checkMutation() {
        domain.checkWrite();
        checkAlive();
        if (routing || !mutation) throw new IllegalStateException("挂载后声明仅由呈现运行时更新");
    }

    private void checkAlive() {
        domain.checkAccess();
        if (disposed) throw new IllegalStateException("场景运行时已释放");
    }

    private void command() {
        domain.checkWrite();
        checkAlive();
        if (routing) throw new IllegalStateException("输入操作仅发布状态或意图");
    }

    public MountHandle attach(SceneNode parent, SceneNode root) {
        return attach(parent, root, null);
    }

    /** 根的逻辑拥有者参与同一迁移合同，挂载 Owner 只管理接入资源。 */
    public MountHandle attachOwned(SceneNode root, Object logicalScope) {
        return attach(null, root, Objects.requireNonNull(logicalScope, "logicalScope"));
    }

    private MountHandle attach(SceneNode parent, SceneNode root, Object logicalScope) {
        command();
        Objects.requireNonNull(root, "root");
        if (parent != null && !mounts.containsKey(parent))
            throw new IllegalArgumentException("父节点未挂载");
        if (root.parent() != null || root.runtime() != null || root.destroyed())
            throw new IllegalArgumentException("节点不可重复挂载");
        validateSubtree(root);
        mutation = true;
        try {
            root.frameworkParent(this, parent);
            if (parent != null) root.frameworkOwnership(this, parent);
            else if (logicalScope != null) root.frameworkOwnership(this, logicalScope);
            if (parent == null) roots.add(root);
            mountSubtree(root, parent == null ? owner : mounts.get(parent).owner);
            invalidateRange(parent == null ? root : parent);
            return new MountHandle(this, root, mounts.get(root).token);
        } catch (RuntimeException | Error failure) {
            Mount partial = mounts.get(root);
            if (partial != null) {
                try {
                    partial.owner.dispose();
                } catch (RuntimeException | Error cleanup) {
                    if (cleanup != failure) failure.addSuppressed(cleanup);
                }
                releaseSubtree(root);
            }
            roots.remove(root);
            root.frameworkParent(this, null);
            // 声明激活可在失败前显露模态；卸载部分挂载后也要结束该范围，不能留下幽灵输入范围。
            try {
                invalidateRemovedRange(parent);
            } catch (RuntimeException | Error cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        } finally {
            mutation = false;
        }
    }

    private void validateSubtree(SceneNode node) {
        if (node.destroyed() || node.runtime() != null)
            throw new IllegalArgumentException("子树已挂载或销毁");
        for (SceneNode child : node.children()) validateSubtree(child);
    }

    private void mountSubtree(SceneNode node, Owner parentOwner) {
        Mount mount = new Mount(parentOwner.child(), ++nodeMeasureRevisionClock);
        mounts.put(node, mount);
        clearLogicalModal();
        node.frameworkRuntime(this, true);
        for (NodeBinding binding : node.bindings()) {
            BindingActivation active = binding.activate(this, node, mount.owner);
            if (binding instanceof MoveBinding) {
                if (!mount.transfers.isEmpty())
                    throw new IllegalArgumentException("节点具有多个移动声明: " + node.id());
                mount.transfers.add(active);
            }
            else mount.bindings.add(active);
            mount.owner.own(active);
        }
        if (node.control() != null) {
            observeControl(node, mount, () -> node.control().measureDependencies(node), () -> invalidate(node));
            observeControl(node, mount, () -> node.control().arrangementDependencies(node), this::invalidateArrangement);
            observeControl(node, mount, () -> node.control().paintDependencies(node), this::invalidatePaint);
        }
        dirtyBindings.add(node);
        for (SceneNode child : node.children()) mountSubtree(child, mount.owner);
    }

    private void observeControl(SceneNode node, Mount mount,
            Supplier<List<ReadableSignal<?>>> sources, Runnable changed) {
        Observation dependencies = domain.observe(mount.owner,
                () -> { for (ReadableSignal<?> source : sources.get()) source.get(); },
                () -> { invalidateBinding(node); changed.run(); });
        mount.bindings.add(new BindingActivation() {
            public void refresh() { dependencies.refresh(); }
            public void close() { dependencies.close(); }
        });
    }

    public void preparePresentation() {
        command();
        mutation = true;
        try {
            do {
                while (!dirtyBindings.isEmpty()) {
                    List<SceneNode> batch = new ArrayList<>(dirtyBindings);
                    dirtyBindings.clear();
                    // 同批显式转移先于旧声明退出；不让订阅注册顺序决定条目生死。
                    for (SceneNode node : batch) {
                        Mount mount = mounts.get(node);
                        if (mount != null)
                            for (BindingActivation active : mount.transfers) active.refresh();
                    }
                    for (SceneNode node : batch) {
                        Mount mount = mounts.get(node);
                        if (mount != null)
                            for (BindingActivation active : mount.bindings) active.refresh();
                    }
                }
                if (layerValidationRevision != geometryRevision) {
                    if (hasLayers()) for (SceneNode root : roots) validateLayerPolicies(root, null);
                    layerValidationRevision = geometryRevision;
                }
                // 进入选择依赖已结算声明；选择的响应投影再结算绑定，首次回放即可显示。
                if (scopeFactsReady()) focusRouter.prepareScopes();
            } while (!dirtyBindings.isEmpty());
        } finally {
            mutation = false;
        }
    }

    private Mount requireMount(SceneNode node, Object token) {
        command();
        Mount mount = mounts.get(node);
        if (mount == null || mount.token != token) throw new IllegalStateException("挂载句柄已结束");
        return mount;
    }

    void detach(SceneNode node, Object token) {
        Mount mount = requireMount(node, token);
        SceneNode formerParent = node.parent();
        mutation = true;
        try {
            try {
                mount.owner.dispose();
            } finally {
                releaseSubtree(node);
                roots.remove(node);
                node.frameworkParent(this, null);
                invalidateRemovedRange(formerParent);
            }
        } finally {
            mutation = false;
        }
    }

    private void releaseSubtree(SceneNode node) {
        for (SceneNode child : node.children()) releaseSubtree(child);
        mounts.remove(node);
        clearLogicalModal();
        scrolling.remove(node);
        dirtyBindings.remove(node);
        node.frameworkRuntime(this, false);
    }

    void move(SceneNode node, Object token, SceneNode next) {
        Mount mount = requireMount(node, token);
        if (node.parent() == next) return;
        if (next != null && !mounts.containsKey(next))
            throw new IllegalArgumentException("目标父节点未挂载");
        for (SceneNode cursor = next; cursor != null; cursor = cursor.parent())
            if (cursor == node) throw new IllegalArgumentException("节点不能包含自身");
        mutation = true;
        try {
            transferMounted(node, mount, next);
        } finally {
            mutation = false;
        }
    }

    void destroy(SceneNode node, Object token) {
        // 核心规格第 5 节：有效的永久删除即使释放失败也必须终止子树。
        // 先核验句柄，旧句柄不能借 finally 销毁后来重新挂载的同一实例。
        requireMount(node, token);
        Throwable failure = null;
        try {
            detach(node, token);
        } catch (RuntimeException | Error error) {
            failure = error;
        }
        mutation = true;
        try {
            destroySubtree(node);
        } catch (RuntimeException | Error error) {
            failure = collectFailure(failure, error);
        } finally {
            mutation = false;
        }
        throwFailure(failure);
    }

    private void destroySubtree(SceneNode node) {
        node.frameworkDestroy(this);
    }

    /** 状态驱动的同页连续移动，不重建绑定或挂载身份。 */
    public void relocate(SceneNode node, SceneNode next) {
        checkMutation();
        Mount mount = mounts.get(node);
        if (mount == null || (next != null && !mounts.containsKey(next)))
            throw new IllegalArgumentException("移动节点或目标父未挂载: " + node.id());
        if (node.parent() == next) return;
        for (SceneNode cursor = next; cursor != null; cursor = cursor.parent())
            if (cursor == node) throw new IllegalArgumentException("移动形成环: " + node.id());
        transferMounted(node, mount, next);
    }

    /** 两种授权入口共用迁移步骤；保留挂载token与绑定，逻辑拥有权另行交接。 */
    private void transferMounted(SceneNode node, Mount mount, SceneNode next) {
        SceneNode formerParent = node.parent();
        mount.owner.moveTo(next == null ? owner : mounts.get(next).owner);
        roots.remove(node);
        node.frameworkParent(this, next);
        node.frameworkTransfer(this, next);
        if (next == null) roots.add(node);
        // 移动保留子树挂载；新旧父链都重新建立内容度量，移动节点按实际新约束匹配文字。
        invalidateRange(formerParent == null ? next : formerParent);
        if (formerParent != null && next != null) invalidate(next);
    }

    /** 声明子序列更新；保留节点不卸载，移除节点结束挂载，新增节点激活当前来源。 */
    public void reconcile(SceneNode parent, List<SceneNode> declared) {
        reconcile(parent, declared, null);
    }

    /** 非 null 的声明作用域拥有条目；null 明示调用方保留。 */
    public void reconcile(SceneNode parent, List<SceneNode> declared, Object scope) {
        reconcile(parent, declared, scope, scope != null);
    }

    public void reconcile(SceneNode parent, List<SceneNode> declared, Object scope, boolean owned) {
        checkMutation();
        if (!mounts.containsKey(parent)) throw new IllegalArgumentException("父节点未挂载");
        List<SceneNode> next = new ArrayList<>(declared);
        Set<SceneNode> unique =
                Collections.newSetFromMap(new IdentityHashMap<SceneNode, Boolean>());
        for (SceneNode node : next) {
            if (!unique.add(node)) throw new IllegalArgumentException("声明子序列重复节点");
            if (node.parent() != parent) {
                if (node.parent() != null || node.runtime() != null)
                    throw new IllegalArgumentException("新增节点已有归属");
                validateSubtree(node);
                if (node == parent) throw new IllegalArgumentException("节点不能包含自身");
            }
        }
        for (SceneNode node : next) {
            if (node.parent() == parent && node.logicalOwner() != scope
                    && !(node.logicalOwner() == parent && !node.transferred()))
                throw new IllegalArgumentException("声明拥有权冲突: " + parent.id() + " -> " + node.id());
        }
        if (parent.children().equals(next)) {
            for (SceneNode node : next) node.frameworkOwnership(this, scope, owned);
            return;
        }
        Throwable failure = null;
        for (SceneNode child : new ArrayList<>(parent.children())) {
            if (!unique.contains(child) && (child.logicalOwner() == scope
                    || (child.logicalOwner() == parent && !child.transferred()))) {
                try {
                    removeDeclared(child, owned);
                } catch (RuntimeException | Error error) {
                    failure = collectFailure(failure, error);
                }
            }
        }
        throwFailure(failure);
        for (SceneNode node : next) {
            boolean fresh = node.runtime() == null;
            node.frameworkParent(this, parent);
            node.frameworkOwnership(this, scope, owned);
            if (fresh) {
                try {
                    mountSubtree(node, mounts.get(parent).owner);
                } catch (RuntimeException | Error error) {
                    try {
                        removeDeclared(node, owned);
                    } catch (RuntimeException | Error cleanup) {
                        if (cleanup != error) error.addSuppressed(cleanup);
                    }
                    throw error;
                }
            }
        }
        invalidateRange(parent);
    }

    private void removeDeclared(SceneNode node, boolean owned) {
        SceneNode formerParent = node.parent();
        Throwable failure = null;
        Mount mount = mounts.get(node);
        try {
            if (mount != null) mount.owner.dispose();
        } catch (RuntimeException | Error error) {
            failure = error;
        }
        releaseSubtree(node);
        node.frameworkParent(this, null);
        if (owned) {
            try {
                destroySubtree(node);
            } catch (RuntimeException | Error error) {
                failure = collectFailure(failure, error);
            }
        } else node.frameworkOwnership(this, null);
        try {
            invalidateRemovedRange(formerParent);
        } catch (RuntimeException | Error error) {
            failure = collectFailure(failure, error);
        }
        throwFailure(failure);
    }

    private static Throwable collectFailure(Throwable failure, Throwable error) {
        if (failure == null) return error;
        if (failure != error) failure.addSuppressed(error);
        return failure;
    }

    private static void throwFailure(Throwable failure) {
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure instanceof Error) throw (Error) failure;
    }

    private SceneLayer.Policy layerPolicy(SceneNode node) {
        return node.control() == null ? null : node.control().layerPolicy(node);
    }

    private void validateLayerPolicies(SceneNode node, SceneNode passive) {
        SceneLayer.Policy policy = layerPolicy(node);
        if (policy != null && policy.participation == SceneLayer.Participation.PASSIVE) passive = node;
        if (passive != null && node.modal())
            throw new IllegalArgumentException("PASSIVE 所属层不能嵌套 modal: layer=" + passive.id() + " node=" + node.id());
        // 合法性属于声明，隐藏或来源无 Entry 不能规避诊断。
        for (SceneNode child : node.children()) validateLayerPolicies(child, passive);
    }

    private boolean hasLayers(SceneNode node) {
        if (layerPolicy(node) != null) return true;
        for (SceneNode child : node.children()) if (hasLayers(child)) return true;
        return false;
    }

    private boolean hasLayers() {
        // 只缓存声明是否包含层；实际输入资格仍逐目标消费唯一几何与逻辑祖先。
        if (layerPresenceRevision != geometryRevision) {
            layersDeclared = false;
            for (SceneNode root : roots) if (hasLayers(root)) { layersDeclared = true; break; }
            layerPresenceRevision = geometryRevision;
        }
        return layersDeclared;
    }

    boolean scopeFactsReady() {
        return !hasLayers() || inputGeometryReady;
    }

    /** 同批来源事实齐备后由唯一路由器选择范围；无层直接 runtime 消费仍按逻辑合同。 */
    public void prepareInputGeometry(SceneGeometryView geometry) {
        command();
        boolean changed = inputGeometry != geometry || !inputGeometryReady;
        inputGeometry = Objects.requireNonNull(geometry, "geometry");
        inputGeometryReady = true;
        if (changed) focusRouter.rangeChanged();
        focusRouter.prepareScopes();
        pointerRouter.prepare(geometry);
    }

    public boolean interactive(SceneNode node) {
        checkAlive();
        if (!locallyInteractive(node)) return false;
        for (SceneNode cursor = node; cursor != null; cursor = cursor.parent()) {
            if (layerPolicy(cursor) != null) {
                if (inputGeometry == null || inputGeometryReady && inputGeometry.get(node) == null) return false;
                break;
            }
        }
        SceneNode modal = activeModal();
        if (modal != null) {
            for (SceneNode cursor = node; cursor != null; cursor = cursor.parent())
                if (cursor == modal) return true;
            return false;
        }
        return true;
    }

    /** 被模态覆盖仍可保留恢复资格，显隐/禁用失活则立即取消；不把隔离当作禁用。 */
    boolean locallyInteractive(SceneNode node) {
        domain.checkAccess();
        if (!mounts.containsKey(node)) return false;
        for (SceneNode cursor = node; cursor != null; cursor = cursor.parent())
            if (!cursor.enabled() || !cursor.visible() || cursor.destroyed()) return false;
            else {
                SceneLayer.Policy policy = layerPolicy(cursor);
                if (policy != null && (policy.participation == SceneLayer.Participation.PASSIVE
                        || inputGeometryReady && !inputGeometry.layerSourceAvailable(cursor))) return false;
            }
        return true;
    }

    boolean visibleInTree(SceneNode node) {
        domain.checkAccess();
        if (!mounts.containsKey(node)) return false;
        for (SceneNode cursor = node; cursor != null; cursor = cursor.parent())
            if (!cursor.visible()) return false;
        return true;
    }

    private void clearLogicalModal() {
        logicalModal = null;
        logicalModalKnown = false;
    }

    SceneNode activeModal() {
        if (hasLayers()) return inputGeometry == null ? null : inputGeometry.activeModal();
        // 资格与提示对每个控件查询范围；null也是有效结果，不能每次重扫整棵挂载树。
        // 核心第5—6节：声明/成员变化立即结束该事实；无几何时仍按逻辑树求解。
        if (!logicalModalKnown) {
            for (int i = roots.size() - 1; i >= 0; i--) {
                logicalModal = findModal(roots.get(i));
                if (logicalModal != null) break;
            }
            logicalModalKnown = true;
        }
        return logicalModal;
    }

    private SceneNode findModal(SceneNode node) {
        // 挂载阶段声明树可以含尚未取得token的子节点，它们不能成为有效输入范围。
        if (!mounts.containsKey(node) || !node.visible() || !node.enabled()) return null;
        for (int i = node.children().size() - 1; i >= 0; i--) {
            SceneNode found = findModal(node.children().get(i));
            if (found != null) return found;
        }
        return node.modal() ? node : null;
    }

    /** 普通编辑按当前焦点；真实组字只投向 BEGIN 建立的仍有效联系。 */
    public boolean dispatchEdit(club.heiqi.uilib.ui.scene.input.SceneEditEvent event) {
        domain.checkWrite();
        command();
        revalidateComposition();
        SceneNode node = focusRouter.focused().get();
        if (event.compositionSource != null) {
            if (event.compositionPhase
                    == club.heiqi.uilib.ui.scene.input.SceneEditEvent.CompositionPhase.BEGIN) {
                if (!event.compositionSource.beginOnce()) return true;
                invalidateComposition(null);
                if (node == null || !interactive(node) || node.control() == null
                        || !node.control().acceptsTextInput(node)
                        || !(node.control() instanceof club.heiqi.uilib.ui.scene.edit.SceneCompositionTarget))
                    return true;
                compositionSource = event.compositionSource;
                compositionMount = captureMount(node);
                compositionState = ((club.heiqi.uilib.ui.scene.edit.SceneCompositionTarget)
                        node.control()).compositionState();
                compositionBaseline = compositionState.inputBaseline();
            } else if (event.compositionSource != compositionSource) return true;
            node = compositionMount.node();
        }
        if (node == null || !interactive(node) || node.control() == null) return false;
        boolean previous = routing;
        routing = true;
        boolean terminal = event.compositionSource != null
                && (event.compositionPhase
                        == club.heiqi.uilib.ui.scene.input.SceneEditEvent.CompositionPhase.COMMIT
                    || event.compositionPhase
                        == club.heiqi.uilib.ui.scene.input.SceneEditEvent.CompositionPhase.CANCEL);
        try {
            boolean consumed = node.control().edit(node, event);
            if (event.command
                    == club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.ESCAPE && consumed)
                invalidateComposition(null);
            return consumed;
        } finally {
            try {
                // 最终来源事件即使业务回调失败也结束；异常仍沿页面既有错误通道暴露。
                if (terminal && compositionSource == event.compositionSource)
                    invalidateComposition(null);
            } finally {
                routing = previous;
            }
        }
    }

    /** 焦点离开及已生效只读资格变化只结束组字，不提交或取消独立草稿。 */
    public void invalidateComposition(SceneNode target) {
        domain.checkWrite();
        if (compositionSource == null
                || target != null && compositionMount.node() != target) return;
        club.heiqi.uilib.ui.scene.edit.SceneEditState state = compositionState;
        Object baseline = compositionBaseline;
        compositionSource = null;
        compositionMount = null;
        compositionState = null;
        compositionBaseline = null;
        // reset/cancel 已自行清理；不能反过来修改新基线的组字呈现。
        if (state.inputBaseline() == baseline) state.composition("", false);
    }

    /** 核验已有联系；同范围合法移动不改变挂载或基线身份。 */
    void revalidateComposition() {
        domain.checkAccess();
        if (compositionSource == null) return;
        SceneNode node = compositionMount.node();
        if (!compositionMount.active() || focusRouter.focused().get() != node
                || !interactive(node) || node.control() == null
                || !node.control().acceptsTextInput(node)
                || !(node.control() instanceof club.heiqi.uilib.ui.scene.edit.SceneCompositionTarget)
                || ((club.heiqi.uilib.ui.scene.edit.SceneCompositionTarget)
                        node.control()).compositionState() != compositionState
                || compositionState.inputBaseline() != compositionBaseline)
            invalidateComposition(null);
    }

    void dispatchControlFocus(SceneNode node, boolean focused, boolean eligible) {
        domain.checkWrite();
        if (node.control() == null) return;
        boolean previous = routing;
        routing = true;
        try {
            node.control().focusChanged(node, focused, eligible);
        } finally {
            routing = previous;
        }
    }

    void dispatchControlPointer(
            SceneNode node,
            club.heiqi.uilib.ui.scene.input.ScenePointerEvent event,
            SceneGeometryView geometry) {
        command();
        if (!interactive(node) || node.control() == null) return;
        routing = true;
        try {
            node.control().pointer(node, event, geometry);
        } finally {
            routing = false;
        }
    }

    boolean dispatchControlScroll(
            SceneNode node,
            club.heiqi.uilib.ui.scene.input.ScenePointerEvent event,
            SceneGeometryView geometry) {
        command();
        if (!interactive(node) || node.control() == null || !node.control().scrollableContent())
            return false;
        boolean consumed;
        routing = true;
        try {
            consumed = node.control().scroll(node, event, geometry);
        } finally {
            routing = false;
        }
        // 滚动只更新视图位置；唯一分发负责发布同代几何，保留不变的字体与内容度量。
        if (consumed) invalidateArrangement();
        return consumed;
    }

    /** 已知逻辑目标的业务激活入口；物理输入应经帧管线的typed事件。 有物理按钮占位时吞掉，不能绕过已有手势；仍按当前挂载与输入范围检查目标。 */
    public void dispatchClick(SceneNode node) {
        command();
        if (!pointerRouter.idle()) return;
        invokeClick(node);
    }

    // 只有同包唯一router可在UP时激活第一owner；第二键仍按下不影响第一owner正常结束。
    void dispatchPointerClick(SceneNode node) {
        command();
        if (!interactive(node) || node.clickAction() == null) return;
        if (node.clickFocus() && node.focusable()) focusRouter.request(captureMount(node));
        invokeClick(node);
    }

    boolean pointerTargetValid(SceneNode node, Object target) {
        domain.checkAccess();
        return target == node
                ? node.clickAction() != null
                : node.control() != null && node.control().pointerTargetValid(node, target);
    }

    void dispatchPointerTarget(SceneNode node, Object target) {
        command();
        if (!interactive(node) || !pointerTargetValid(node, target)) return;
        if (target == node) {
            dispatchPointerClick(node);
            return;
        }
        routing = true;
        try {
            node.control().activatePointer(node, target);
        } finally {
            routing = false;
        }
    }

    // 业务激活不改变焦点；clickFocus仅属于物理UP，synthetic与键盘命令保留owner。
    private void invokeClick(SceneNode node) {
        if (!interactive(node) || node.clickAction() == null) return;
        routing = true;
        try {
            node.clickAction().run();
        } finally {
            routing = false;
        }
    }

    /** 只读布局取得本次目标；不在测量期间修改状态。 */
    public int scrollOffset(SceneNode node) {
        checkAlive();
        ScrollPosition position = scrolling.get(node);
        return position == null || position.declaration != node.scrollOffset()
                ? node.scrollOffset()
                : position.value;
    }

    /** 从最终几何发布边界；内容/窗口变小会立即丢弃越界偏移，不积攒不可见滚动债务。 */
    public void prepareScrollPositions(
            club.heiqi.uilib.ui.scene.layout.SceneGeometryView geometry) {
        command();
        for (club.heiqi.uilib.ui.scene.layout.SceneGeometryView.Entry entry : geometry.ordered()) {
            if (!entry.node.scrollable() || !mounts.containsKey(entry.node)) continue;
            ScrollPosition position =
                    scrolling.computeIfAbsent(entry.node, ignored -> new ScrollPosition());
            position.declaration = entry.node.scrollOffset();
            position.value = entry.scrollOffset;
            position.maximum = entry.scrollMaximum;
        }
    }

    /** spec6.2：在最终布局后消费一次导航意图；重排由管线提供，不递归结算声明。 每个祖先至多处理一次，滚动沿用唯一ScrollPosition，不持有第二套几何或滚动权威。 */
    public SceneGeometry revealNavigation(
            SceneGeometry geometry, Supplier<SceneGeometry> relayout) {
        domain.checkWrite();
        command();
        MountHandle target = focusRouter.takeReveal();
        if (target == null) return geometry;
        // 内部视口先消费同一次Tab意图；随后只读事实才能用于逐层祖先显露。
        if (target.node().control() != null
                && target.node().control().prepareNavigation(target.node(), geometry)) {
            invalidateArrangement();
            geometry = Objects.requireNonNull(relayout.get(), "geometry");
        }
        SceneNode boundary = geometry.layerBoundary(target.node());
        for (SceneNode ancestor = target.node() == boundary ? null : target.node().parent();
                ancestor != null;
                ancestor = ancestor == boundary ? null : ancestor.parent()) {
            if (!focusRouter.owns(target)) break;
            if (ancestor == boundary && !ancestor.scrollable()) break;
            if (!ancestor.scrollable()) continue;
            SceneGeometryView.Entry entry = geometry.get(ancestor);
            SceneGeometry current = geometry;
            SceneNode container = ancestor;
            LayoutBox region = domain.readOnly(
                    () -> SceneGeometry.revealRegion(target.node(), container, current));
            if (entry == null || region == null || region.width == 0 || region.height == 0)
                continue;
            LayoutBox window = SceneGeometry.revealWindow(ancestor, geometry);
            if (window.width == 0 || window.height == 0) continue;
            boolean row = ancestor.axis() == SceneNode.Axis.ROW;
            long delta =
                    SceneGeometry.revealDelta(
                            row ? region.x : region.y,
                            row ? region.width : region.height,
                            row ? window.x : window.y,
                            row ? window.width : window.height);
            ScrollPosition position = scrolling.get(ancestor);
            if (position == null || !mounts.containsKey(ancestor)) continue;
            int next =
                    (int)
                            Math.max(
                                    0L,
                                    Math.min(
                                            entry.scrollMaximum,
                                            (long) entry.scrollOffset + delta));
            if (next == position.value) continue;
            position.value = next;
            invalidateArrangement();
            // 内层移动改变目标及裁剪后的可见部分；必须用重排后的同代几何求外层，
            // 最后一次也重排，确保paint和下一事件hit均消费实际偏移后的区域。
            geometry = Objects.requireNonNull(relayout.get(), "geometry");
            if (ancestor == boundary) break;
        }
        return geometry;
    }

    /**
     * spec6.1：当前模态先处理，未消费才进入页面策略。调用前结算声明； 页面采用与初始焦点相同的首个非模态根，后台模态不接收本事件。
     * 本接口只返回消费结果，宿主fallback由管线在Effects成功后执行。
     */
    public boolean dispatchEscape() {
        domain.checkWrite();
        command();
        if (dispatchEdit(
                club.heiqi.uilib.ui.scene.input.SceneEditEvent.command(
                        club.heiqi.uilib.ui.scene.input.SceneEditEvent.Command.ESCAPE,
                        false,
                        null))) return true;
        SceneNode modal = activeModal();
        if (modal != null && locallyInteractive(modal) && escape(modal)) return true;
        for (SceneNode root : roots)
            if (!root.modal()) {
                return locallyInteractive(root) && escape(root);
            }
        return false;
    }

    private boolean escape(SceneNode node) {
        if (node.escapeAction() == null) return false;
        routing = true;
        try {
            return node.escapeAction().getAsBoolean();
        } finally {
            routing = false;
        }
    }

    public void dispatchScroll(SceneNode node, int delta) {
        command();
        if (!interactive(node)) return;
        if (node.scrollable()) {
            ScrollPosition position = scrolling.get(node);
            if (position != null) {
                int next =
                        (int)
                                Math.max(
                                        0L,
                                        Math.min(
                                                position.maximum,
                                                (long) position.value
                                                        - (long) delta * node.scrollStep()));
                if (next != position.value) {
                    position.value = next;
                    invalidateArrangement();
                }
            }
        }
        if (node.scrollAction() == null) return;
        routing = true;
        try {
            node.scrollAction().accept(delta);
        } finally {
            routing = false;
        }
    }

    @Override
    public void close() {
        domain.checkAccess();
        if (disposed) return;
        command();
        // 扩展失焦回调也属于关闭错误边界，不能阻断挂载与Owner资源的完整收尾。
        Throwable failure = null;
        try {
            pointerRouter.close();
        } catch (RuntimeException | Error e) {
            failure = e;
        }
        try {
            focusRouter.close();
        } catch (RuntimeException | Error e) {
            if (failure == null) failure = e;
            else if (e != failure) failure.addSuppressed(e);
        }
        for (SceneNode root : new ArrayList<>(roots)) {
            try {
                detach(root, mounts.get(root).token);
            } catch (RuntimeException | Error e) {
                if (failure == null) failure = e;
                else if (e != failure) failure.addSuppressed(e);
            }
        }
        try {
            owner.dispose();
        } catch (RuntimeException | Error e) {
            if (failure == null) failure = e;
            else if (e != failure) failure.addSuppressed(e);
        } finally {
            clearLogicalModal();
            inputGeometry = null;
            inputGeometryReady = false;
            disposed = true;
        }
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
    }
}
