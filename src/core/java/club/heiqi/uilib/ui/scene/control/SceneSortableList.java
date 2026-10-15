package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.input.ScenePointerEvent;
import club.heiqi.uilib.ui.scene.layout.ChildPlacements;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.MeasuredSize;
import club.heiqi.uilib.ui.scene.layout.SceneChildMeasurer;
import club.heiqi.uilib.ui.scene.layout.SceneFlowLayout;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.text.SceneTextMeasurer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/** 稳定键纵向排序；拖中只维护临时候选，业务列表与行挂载保持原状。 */
public final class SceneSortableList {
    public static final class Row {
        public final SceneNode content;
        public final SceneNode[] handles;
        public Row(SceneNode content, SceneNode... handles) {
            this.content = Objects.requireNonNull(content, "content");
            this.handles = Objects.requireNonNull(handles, "handles").clone();
            for (SceneNode handle : this.handles) Objects.requireNonNull(handle, "handle");
        }
    }
    /** 将 key 移到 beforeKey 之前；null 表示末尾，排序和持久化由业务执行。 */
    public static final class Move<K> {
        public final K key, beforeKey;
        private Move(K key, K beforeKey) { this.key = key; this.beforeKey = beforeKey; }
    }
    private SceneSortableList() {}

    public static <T, K> SceneNode create(ScenePageContext context, ReadableSignal<List<T>> items,
            Function<T, K> key, Function<ReadableSignal<T>, Row> rowFactory,
            Function<ReadableSignal<T>, SceneNode> previewFactory, Consumer<Move<K>> onMove) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        Controller<T, K> control = new Controller<>(context, Objects.requireNonNull(items, "items"),
                Objects.requireNonNull(key, "key"), Objects.requireNonNull(rowFactory, "rowFactory"),
                Objects.requireNonNull(previewFactory, "previewFactory"), Objects.requireNonNull(onMove, "onMove"));
        SceneNode root = new SceneNode().axis(SceneNode.Axis.COLUMN).size(Size.fill(1), Size.natural()).control(control);
        control.root = root;
        SceneNode layer = SceneLayer.create(context, () -> new SceneLayer.Policy(0, SceneLayer.Participation.PASSIVE),
                (node, source, viewport, measured) -> {
                    control.version.get();
                    Session<T, K> session = control.session;
                    return new LayoutBox(session == null ? source.x : session.overlayX,
                            session == null ? source.y : session.overlayY, measured.width, measured.height);
                }, Collections.singletonList(control.version));
        control.layer = layer;
        layer.bind(NodeBinding.value(() -> { control.version.get(); return control.session != null && control.session.active; }, SceneNode::visible));
        layer.bind(NodeBinding.value(() -> { control.version.get(); return control.session == null ? 0 : control.session.width; }, (node, width) -> {
            if (node.width().mode != Size.Mode.FIXED || node.width().value != width)
                node.size(Size.fixed(width), Size.natural());
        }));
        layer.bind(NodeBinding.repeat(() -> {
            control.version.get();
            return control.session != null && control.session.active ? Collections.singletonList(control.session) : Collections.<Session<T, K>>emptyList();
        }, value -> value, source -> Objects.requireNonNull(previewFactory.apply(source.get().row.value), "preview")));
        // repeat 是根的唯一孩子声明。私有层条目与业务键隔离，flowChildren 正式排除它，不污染 gap/自然高。
        Object layerKey = new Object();
        root.bind(NodeBinding.repeat(() -> {
            List<Item<T, K>> values = new ArrayList<>();
            for (T value : items.get()) values.add(new Item<>(new ItemKey<>(Objects.requireNonNull(key.apply(value), "sortable key")), value));
            values.add(new Item<>(layerKey, null));
            return values;
        }, item -> item.identity, source -> {
            if (source.get().identity == layerKey) return layer;
            ReadableSignal<T> value = () -> source.get().value;
            K identity = key.apply(value.get());
            Row row = Objects.requireNonNull(rowFactory.apply(value), "row");
            RowControl<T, K> rowControl = new RowControl<>(control, identity, value, row);
            SceneNode node = new SceneNode().size(Size.fill(1), Size.natural()).focusable(true).clickFocus(true)
                    .control(rowControl).add(row.content);
            rowControl.node = node;
            node.bind((runtime, self, owner) -> {
                owner.own(() -> control.cancel(rowControl));
                return new BindingActivation() { public void refresh() {} public void close() {} };
            });
            return node;
        }));
        root.bind((runtime, node, owner) -> {
            owner.own(() -> control.cancel(null));
            return new BindingActivation() { public void refresh() {} public void close() {} };
        });
        return root;
    }
    private static final class Item<T, K> {
        final Object identity;
        final T value;
        Item(Object identity, T value) { this.identity = identity; this.value = value; }
    }
    private static final class ItemKey<K> {
        final K key;
        ItemKey(K key) { this.key = key; }
        @Override public int hashCode() { return key.hashCode(); }
        @Override public boolean equals(Object other) { return other instanceof ItemKey && key.equals(((ItemKey<?>) other).key); }
    }
    private static final class Session<T, K> {
        final RowControl<T, K> row;
        final boolean keyboard;
        final int startX, startY, grabY;
        final long pressed;
        final SceneDragSupport.AutoScroll scrolling = new SceneDragSupport.AutoScroll();
        boolean active;
        int pointerX, pointerY, overlayX, overlayY, width, slot;
        K beforeKey;
        Session(RowControl<T, K> row, boolean keyboard, int x, int y, LayoutBox box, long now) {
            this.row = row; this.keyboard = keyboard; startX = pointerX = x; startY = pointerY = y;
            grabY = y - box.y; overlayX = box.x; overlayY = box.y; width = box.width; pressed = now;
            scrolling.reset(now);
        }
    }
    private static final class Controller<T, K> implements SceneControl {
        final SceneRuntime runtime;
        final ReadableSignal<List<T>> items;
        final Function<T, K> key;
        final Consumer<Move<K>> onMove;
        final Signal<Long> version;
        SceneNode root, layer;
        Session<T, K> session;
        long counter;
        Controller(ScenePageContext context, ReadableSignal<List<T>> items, Function<T, K> key,
                Function<ReadableSignal<T>, Row> rowFactory, Function<ReadableSignal<T>, SceneNode> previewFactory, Consumer<Move<K>> onMove) {
            runtime = context.runtime(); this.items = items; this.key = key; this.onMove = onMove;
            version = context.domain().signal(0L);
        }
        void changed() { version.set(++counter); }
        void cancel(RowControl<T, K> row) {
            if (session != null && (row == null || session.row == row)) { session = null; changed(); }
        }
        boolean valid(Session<T, K> current, SceneGeometryView geometry) {
            return current.row.node.parent() == root && SceneDragSupport.eligible(current.row.row.content, current.row.node)
                    && runtime.interactive(current.row.node) && geometry.get(current.row.node) != null
                    && SceneDragSupport.visible(geometry, root);
        }
        @SuppressWarnings("unchecked")
        List<RowControl<T, K>> rows(SceneGeometryView geometry) {
            List<RowControl<T, K>> result = new ArrayList<>();
            for (SceneNode node : root.children()) if (node.control() instanceof RowControl && node.visible() && geometry.get(node) != null)
                result.add((RowControl<T, K>) node.control());
            return result;
        }
        List<RowControl<T, K>> others(SceneGeometryView geometry, Session<T, K> current) {
            List<RowControl<T, K>> result = rows(geometry); result.remove(current.row); return result;
        }
        void begin(RowControl<T, K> row, boolean keyboard, int x, int y, SceneGeometryView geometry) {
            cancel(null);
            SceneGeometryView.Entry entry = geometry.get(row.node);
            if (entry == null) return;
            Session<T, K> next = new Session<>(row, keyboard, x, y, entry.box, runtime.inputNanos());
            session = next;
            next.active = keyboard || row.row.handles.length > 0;
            List<RowControl<T, K>> all = rows(geometry);
            next.slot = Math.max(0, all.indexOf(row));
            List<RowControl<T, K>> others = others(geometry, next);
            next.beforeKey = next.slot < others.size() ? others.get(next.slot).key : null;
            changed();
        }
        void activate(Session<T, K> current, long now) {
            if (session != current) return;
            current.active = true; current.scrolling.reset(now); changed();
        }
        void pending(Session<T, K> current, long now) {
            double distance = Math.hypot((long) current.pointerX - current.startX, (long) current.pointerY - current.startY);
            // 默认排序是距离或延时；距离先达到就已激活，不保留不可达的容差失效态。
            if (distance >= 5 || now - current.pressed >= 200_000_000L) activate(current, now);
        }
        void refresh(Session<T, K> current, SceneGeometryView geometry, SceneDragSupport.AutoScroll.Shift shift) {
            if (session != current || !current.active) return;
            List<RowControl<T, K>> others = others(geometry, current);
            K before = current.beforeKey;
            int oldX = current.overlayX, oldY = current.overlayY, oldWidth = current.width;
            SceneGeometryView.Entry source = geometry.get(current.row.node);
            current.width = source.box.width;
            current.overlayX = SceneDragSupport.integer((long) source.box.x + shift.x);
            if (current.keyboard) {
                int index = -1;
                for (int i = 0; i < others.size(); i++) if (Objects.equals(others.get(i).key, current.beforeKey)) { index = i; break; }
                current.slot = current.beforeKey == null ? others.size() : index >= 0 ? index : Math.min(current.slot, others.size());
                current.beforeKey = current.slot < others.size() ? others.get(current.slot).key : null;
                int original = rows(geometry).indexOf(current.row);
                RowControl<T, K> anchor = current.slot == original ? current.row
                        : current.slot < original ? others.get(current.slot) : others.get(current.slot - 1);
                current.overlayY = geometry.get(anchor.node).box.y;
            } else {
                current.overlayY = SceneDragSupport.integer((long) current.pointerY - current.grabY);
                current.slot = others.size(); current.beforeKey = null;
                // 原行保持原槽，以当前真实变高行的中点求插入槽，不以等高索引估算。
                for (int i = 0; i < others.size(); i++) {
                    LayoutBox box = geometry.get(others.get(i).node).box;
                    if (current.pointerY < (double) box.y + shift.y + box.height / 2.0) {
                        current.slot = i; current.beforeKey = others.get(i).key; break;
                    }
                }
            }
            if (!Objects.equals(before, current.beforeKey) || oldX != current.overlayX || oldY != current.overlayY || oldWidth != current.width) changed();
        }
        void finish(RowControl<T, K> row, SceneGeometryView geometry) {
            Session<T, K> current = session;
            if (current == null || current.row != row) return;
            if (!valid(current, geometry) || !current.active) { cancel(row); return; }
            refresh(current, geometry, SceneDragSupport.AutoScroll.Shift.NONE);
            Move<K> move = new Move<>(row.key, current.beforeKey);
            K successor = null;
            boolean sourceFound = false;
            for (T value : items.get()) {
                K identity = key.apply(value);
                if (sourceFound) { successor = identity; break; }
                if (Objects.equals(identity, row.key)) sourceFound = true;
            }
            cancel(row); // 先结束再执行业务，回调失败或迟到 UP 都不能再次提交。
            // handle 的点击同样即时拾取；槽位未变只结束手势，不制造无操作的业务排序。
            if (sourceFound && !Objects.equals(successor, move.beforeKey)) onMove.accept(move);
        }
        void step(Session<T, K> current, int amount, SceneGeometryView geometry) {
            List<RowControl<T, K>> others = others(geometry, current);
            int index = current.beforeKey == null ? others.size() : -1;
            for (int i = 0; i < others.size(); i++) if (Objects.equals(others.get(i).key, current.beforeKey)) { index = i; break; }
            current.slot = Math.max(0, Math.min(others.size(), (index < 0 ? current.slot : index) + amount));
            current.beforeKey = current.slot < others.size() ? others.get(current.slot).key : null;
            int original = rows(geometry).indexOf(current.row);
            RowControl<T, K> reveal = current.slot == original ? current.row
                    : current.slot < original ? others.get(current.slot) : others.get(current.slot - 1);
            runtime.requestInputReveal(reveal.node);
            changed();
            refresh(current, geometry, SceneDragSupport.AutoScroll.Shift.NONE);
        }
        @Override public List<ReadableSignal<?>> arrangementDependencies(SceneNode node) { return Collections.singletonList(version); }
        @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
        @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text, SceneChildMeasurer children) {
            return SceneFlowLayout.measure(node, body, children);
        }
        @Override public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) { return SceneFlowLayout.arrange(node, box, children); }
        @Override public long nextInputNanos(SceneNode node) {
            version.get();
            return session == null ? Long.MAX_VALUE : session.active ? 0 : session.pressed + 200_000_000L;
        }
        @Override public void inputFrame(SceneNode node, SceneGeometryView geometry, long now) {
            Session<T, K> current = session;
            if (current == null) return;
            if (!valid(current, geometry)) { cancel(null); return; }
            if (current.keyboard) {
                SceneNode focused = runtime.focusRouter().focused().get();
                if (focused != current.row.node && !SceneDragSupport.handleFocus(current.row.node, current.row.row.handles, focused)) { cancel(null); return; }
            }
            if (!current.active) pending(current, now);
            if (!current.active) return;
            SceneDragSupport.AutoScroll.Shift shift = current.keyboard ? SceneDragSupport.AutoScroll.Shift.NONE
                    : current.scrolling.advance(runtime, root, geometry, current.pointerX, current.pointerY, now, false, true);
            refresh(current, geometry, shift);
        }
        @Override public void inputFrameCancelled(SceneNode node) { cancel(null); }
        @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {}
        @Override public void paintAfterChildren(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {
            Session<T, K> current = session;
            if (current == null || !current.active) return;
            SceneGeometryView.Entry rootEntry = geometry.get(node);
            if (rootEntry == null) return;
            List<RowControl<T, K>> others = others(geometry, current);
            int y = geometry.get(current.row.node).box.y;
            boolean found = false;
            for (RowControl<T, K> row : others) if (Objects.equals(row.key, current.beforeKey)) {
                y = geometry.get(row.node).box.y - node.gap() / 2; found = true; break;
            }
            if (!found && current.beforeKey == null && !others.isEmpty()) {
                LayoutBox last = geometry.get(others.get(others.size() - 1).node).box;
                y = SceneDragSupport.integer((long) last.y + last.height + node.gap() / 2);
            }
            LayoutBox marker = new LayoutBox(rootEntry.box.x + node.padding(), y,
                    Math.max(0, rootEntry.box.width - node.padding() * 2), 2);
            paint.add(PaintCommand.rect(marker, rootEntry.clip, node.style().color));
        }
    }
    private static final class RowControl<T, K> implements SceneControl {
        final Controller<T, K> list;
        final K key;
        final ReadableSignal<T> value;
        final Row row;
        final Object bodyTarget = new Object();
        SceneNode node;
        RowControl(Controller<T, K> list, K key, ReadableSignal<T> value, Row row) {
            this.list = list; this.key = key; this.value = value; this.row = row;
        }
        @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text) { return new MeasuredSize(0, 0); }
        @Override public MeasuredSize measure(SceneNode node, Constraints body, SceneTextMeasurer text, SceneChildMeasurer children) { return SceneFlowLayout.measure(node, body, children); }
        @Override public ChildPlacements arrange(SceneNode node, LayoutBox box, SceneChildMeasurer children) { return SceneFlowLayout.arrange(node, box, children); }
        @Override public void paint(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {}
        @Override public List<ReadableSignal<?>> paintDependencies(SceneNode node) {
            ReadableSignal<Boolean> focused = () -> list.runtime.focusRouter().focusVisible(node);
            return Collections.singletonList(focused);
        }
        @Override public void paintAfterChildren(SceneNode node, SceneGeometryView geometry, PaintPlanBuilder paint) {
            SceneGeometryView.Entry entry = geometry.get(node);
            if (entry != null && list.runtime.interactive(node) && list.runtime.focusRouter().focusVisible(node))
                SceneControlPaint.border(paint, entry.box, entry.clip, list.root.style().color);
        }
        @Override public boolean capturesPointer() { return true; }
        @Override public Object pointerTarget(SceneNode node, SceneGeometryView geometry, int x, int y) {
            if (!SceneDragSupport.eligible(row.content, node)) return null;
            if (row.handles.length > 0) return SceneDragSupport.handle(node, row.handles, geometry, x, y);
            return SceneDragSupport.inside(geometry.get(row.content), x, y) ? bodyTarget : null;
        }
        @Override public Object pointerActivator(SceneNode node, SceneNode hit, SceneGeometryView geometry, int x, int y) {
            SceneNode handle = SceneDragSupport.handle(node, row.handles, geometry, x, y);
            return handle != null && SceneDragSupport.within(hit, handle) ? handle : null;
        }
        @Override public boolean pointerTargetValid(SceneNode node, Object target) {
            if (!SceneDragSupport.eligible(row.content, node)) return false;
            if (target == bodyTarget) return row.handles.length == 0;
            for (SceneNode handle : row.handles) if (target == handle && SceneDragSupport.eligible(handle, node)) return true;
            return false;
        }
        @Override public boolean editActivator(SceneNode node, SceneNode focused, SceneEditEvent event) {
            return SceneDragSupport.dragCommand(event) && SceneDragSupport.handleFocus(node, row.handles, focused);
        }
        @Override public void pointer(SceneNode node, ScenePointerEvent event, SceneGeometryView geometry) {
            switch (event.kind) {
                case DOWN: list.begin(this, false, event.x, event.y, geometry); break;
                case MOVE:
                    Session<T, K> current = list.session;
                    if (current == null || current.row != this || current.keyboard) return;
                    current.pointerX = event.x; current.pointerY = event.y;
                    if (!current.active) list.pending(current, list.runtime.inputNanos());
                    list.refresh(current, geometry, SceneDragSupport.AutoScroll.Shift.NONE); break;
                case UP:
                    Session<T, K> releasing = list.session;
                    if (releasing == null || releasing.row != this || releasing.keyboard) return;
                    releasing.pointerX = event.x; releasing.pointerY = event.y;
                    list.finish(this, geometry); break;
                default: break;
            }
        }
        @Override public boolean edit(SceneNode node, SceneEditEvent event, SceneGeometryView geometry) {
            if (!SceneDragSupport.dragCommand(event) || geometry == null) return false;
            Session<T, K> current = list.session;
            switch (event.command) {
                case ACTIVATE_ONLY: case SPACE:
                    if (current == null) {
                        SceneGeometryView.Entry entry = geometry.get(node);
                        if (entry != null) list.begin(this, true, entry.box.x, entry.box.y, geometry);
                    } else if (current.row == this && current.keyboard) list.finish(this, geometry);
                    return true;
                case ESCAPE:
                    if (current == null || current.row != this || !current.keyboard) return false;
                    list.cancel(this); return true;
                case UP: case DOWN:
                    if (current == null || current.row != this || !current.keyboard) return false;
                    list.step(current, event.command == SceneEditEvent.Command.UP ? -1 : 1, geometry); return true;
                default: return false;
            }
        }
        @Override public void focusChanged(SceneNode node, boolean focused, boolean eligible) { if (!focused) list.cancel(this); }
        @Override public void captureLost(SceneNode node) { list.cancel(this); }
        @Override public boolean cancelCaptureOnEscape(SceneNode node) { return list.session != null && list.session.row == this && !list.session.keyboard; }
    }
}
