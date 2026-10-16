package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** 逻辑 Controller 只持有全 scope 的键、当前值与节点；结构仅由 value binding 落实。 */
final class SceneSortableModel<T, K, C> {
    static final class ContainerEntry<T, K, C> {
        final C key;
        final SceneSortable.Container declaration;
        List<RowEntry<T, K, C>> rows = new ArrayList<>(), displayed = new ArrayList<>();
        ContainerEntry(C key, SceneSortable.Container declaration) { this.key = key; this.declaration = declaration; }
    }
    static final class RowEntry<T, K, C> {
        final K key;
        final Signal<T> value;
        final Signal<ContainerEntry<T, K, C>> container, placed;
        final Signal<SceneSortableMotion.Offset> offset;
        final SceneSortable.Row declaration;
        SceneNode node;
        RowEntry(club.heiqi.uilib.ui.reactive.ReactiveDomain domain, K key, Signal<T> value,
                 Signal<ContainerEntry<T, K, C>> container, SceneSortable.Row declaration) {
            this.key = key; this.value = value; this.container = container; this.declaration = declaration;
            placed = domain.signal(container.get());
            offset = domain.signal(SceneSortableMotion.Offset.ZERO);
        }
    }
    final ScenePageContext context;
    final SceneRuntime runtime;
    final SceneNode root;
    final ReadableSignal<SceneSortable.Options<T, C>> options;
    final Function<T, K> key;
    final Function<C, SceneSortable.Container> containerFactory;
    final Function<ReadableSignal<T>, SceneSortable.Row> rowFactory;
    final Function<ReadableSignal<T>, SceneNode> previewFactory;
    final Consumer<SceneSortable.Move<K, C>> onMove;
    final Map<K, RowEntry<T, K, C>> rows = new LinkedHashMap<>();
    final Map<C, ContainerEntry<T, K, C>> containers = new LinkedHashMap<>();
    final SceneDragDrop<Object> scope;
    final SceneSortableInteraction<T, K, C> interaction;
    final SceneSortableMotion<T, K, C> motion;
    final SceneSortableProjection<T, K, C> projection;
    final Signal<Long> revision;
    private long modelRevision;
    final SceneNode layer;

    SceneSortableModel(ScenePageContext context, ReadableSignal<List<SceneSortable.Group<C, T>>> groups,
            Function<T, K> key, Function<C, SceneSortable.Container> containerFactory,
            Function<ReadableSignal<T>, SceneSortable.Row> rowFactory, Function<ReadableSignal<T>, SceneNode> previewFactory,
            Consumer<SceneSortable.Move<K, C>> onMove, ReadableSignal<SceneSortable.Options<T, C>> options) {
        this.context = context; runtime = context.runtime(); this.key = key; this.containerFactory = containerFactory;
        this.rowFactory = rowFactory; this.previewFactory = previewFactory; this.onMove = onMove; this.options = options;
        // Scope 只负责正式挂载、资格和会话；最终业务只由排序的显式 normalDrop 发布 Move。
        scope = SceneDragDrop.create(context, SceneDragDrop.Collision.CLOSEST_CENTER, drop -> {});
        root = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.fill(1), Size.natural());
        revision = context.domain().signal(0L);
        interaction = new SceneSortableInteraction<>(this);
        motion = new SceneSortableMotion<>(this); projection = new SceneSortableProjection<>(this);
        root.control(new SceneSortablePresentation<>(this));
        layer = SceneSortablePresentation.layer(this);
        root.add(layer);
        root.bind(SceneControlBindings.declaration(groups, (node, value) -> apply(value)));
        root.bind(SceneControlBindings.declaration(() -> { revision.get(); return interaction.feedback.get(); },
                (node, value) -> projection.apply(value)));
        root.bind((runtime, node, owner) -> {
            owner.own(() -> { interaction.clear(); motion.clear(); });
            return new BindingActivation() { public void refresh() {} public void close() {} };
        });
        root.own(() -> { rows.clear(); containers.clear(); });
    }
    SceneSortable.Options<T, C> options() { return Objects.requireNonNull(options.get(), "sortable options"); }
    RowEntry<T, K, C> source(Object key) {
        return key instanceof SceneSortableKeys.SourceKey ? rows.get(((SceneSortableKeys.SourceKey<?>) key).key) : null;
    }
    boolean accepts(ContainerEntry<T, K, C> container, Object sourceKey) {
        RowEntry<T, K, C> row = source(sourceKey);
        return row != null && options().draggable.test(row.value.get()) && options().accepts.test(container.key, row.value.get());
    }
    void bindTarget(SceneNode node, Object key, java.util.function.Predicate<Object> accepts) {
        ReadableSignal<SceneDroppable.Options<Object>> policy = () -> new SceneDroppable.Options<>().accepts(accepts);
        node.bind((runtime, self, owner) -> {
            owner.own(scope.target(runtime, key, self, policy));
            return new BindingActivation() { public void refresh() {} public void close() {} };
        });
    }
    void buildRow(RowEntry<T, K, C> row) {
        SceneDraggable.State state = SceneDraggable.state(context.domain());
        ReadableSignal<SceneDraggable.Options> policy = () -> {
            SceneSortable.Options<T, C> current = options();
            SceneDraggable.Options base = scope.draggable(new SceneSortableKeys.SourceKey<>(row.key),
                    new SceneDraggable.Options().handles(row.declaration.handles).sourceContent(row.declaration.content).preserveSlot()
                            .sourceAnchor(row.placed.get().declaration.root)
                            .autoScroll(current.autoScroll).autoScrollSpeed(current.autoScrollSpeed));
            return base.observer(interaction.observer(row, base.observer()));
        };
        SceneNode visual = new SceneNode().size(Size.fill(1), Size.natural())
                .control(new SceneSortableRow<>(this, row)).add(row.declaration.content);
        visual.bind(NodeBinding.value(() -> row.placed.get().declaration.layout, (node, layout) ->
                node.size(layout == SceneSortable.Layout.HORIZONTAL ? Size.natural() : Size.fill(1), Size.natural())));
        row.node = SceneDraggable.create(context, visual, state, policy);
        row.node.size(row.placed.get().declaration.layout == SceneSortable.Layout.HORIZONTAL ? Size.natural() : Size.fill(1), Size.natural());
        row.node.bind(NodeBinding.value(() -> options().draggable.test(row.value.get()), SceneNode::enabled));
        row.node.bind(NodeBinding.value(() -> row.placed.get().declaration.layout, (node, layout) ->
                node.size(layout == SceneSortable.Layout.HORIZONTAL ? Size.natural() : Size.fill(1), Size.natural())));
        bindTarget(row.node, new SceneSortableKeys.RowTarget<>(row.key), source -> accepts(row.placed.get(), source));
    }
    private void apply(List<SceneSortable.Group<C, T>> groups) {
        SceneSortableDeclarations.Plan<T, K, C> plan = SceneSortableDeclarations.prepare(this, groups);
        motion.prepareIfShowing();
        try {
            mountDestinations(plan);
            syncValues(plan);
            relocateAll(plan);
            reconcileRows(plan);
            finishContainers(plan);
            revision.set(++modelRevision);
        } catch (RuntimeException | Error failure) {
            plan.releaseUnattached(failure);
            throw failure;
        }
    }
    private void mountDestinations(SceneSortableDeclarations.Plan<T, K, C> plan) {
        // 旧容器完整保留到全部跨父迁移完成，防止源销毁存续行。
        List<SceneNode> mounted = new ArrayList<>();
        for (ContainerEntry<T, K, C> value : containers.values()) mounted.add(value.declaration.root);
        for (ContainerEntry<T, K, C> value : plan.containers.values())
            if (!containers.containsKey(value.key)) mounted.add(value.declaration.root);
        mounted.add(layer);
        runtime.reconcile(root, mounted, root, true);
    }
    private void syncValues(SceneSortableDeclarations.Plan<T, K, C> plan) {
        rows.clear(); rows.putAll(plan.rows);
        for (Map.Entry<K, T> value : plan.values.entrySet()) rows.get(value.getKey()).value.set(value.getValue());
    }
    private void relocateAll(SceneSortableDeclarations.Plan<T, K, C> plan) {
        for (ContainerEntry<T, K, C> container : plan.containers.values()) {
            for (RowEntry<T, K, C> row : plan.order.get(container.key)) {
                row.container.set(container); row.placed.set(container);
                if (row.node.runtime() != null && row.node.parent() != container.declaration.items)
                    runtime.relocate(row.node, container.declaration.items);
            }
        }
    }
    private void reconcileRows(SceneSortableDeclarations.Plan<T, K, C> plan) {
        for (ContainerEntry<T, K, C> container : plan.containers.values()) {
            List<RowEntry<T, K, C>> ordered = plan.order.get(container.key);
            List<SceneNode> children = new ArrayList<>();
            for (RowEntry<T, K, C> row : ordered) children.add(row.node);
            runtime.reconcile(container.declaration.items, children, container.declaration.items, true);
            container.rows = ordered; container.displayed = ordered;
        }
    }
    private void finishContainers(SceneSortableDeclarations.Plan<T, K, C> plan) {
        containers.clear(); containers.putAll(plan.containers);
        List<SceneNode> finalRoots = new ArrayList<>();
        for (ContainerEntry<T, K, C> container : containers.values()) finalRoots.add(container.declaration.root);
        finalRoots.add(layer);
        runtime.reconcile(root, finalRoots, root, true);
    }
    K successor(RowEntry<T, K, C> source) {
        List<RowEntry<T, K, C>> ordered = source.container.get().rows;
        int index = ordered.indexOf(source);
        return index >= 0 && index + 1 < ordered.size() ? ordered.get(index + 1).key : null;
    }
}
