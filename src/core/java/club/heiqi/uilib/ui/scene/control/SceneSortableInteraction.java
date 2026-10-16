package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.input.SceneEditEvent;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import java.util.Objects;

/** 包装正式拖动 Observer；仅维护临时插槽，显式 normalDrop 是唯一业务出口。 */
final class SceneSortableInteraction<T, K, C> {
    static final class Feedback<T, K, C> {
        final Object token;
        final SceneSortableModel.RowEntry<T, K, C> row;
        final SceneSortableSelection.Position<T, K, C> position;
        final LayoutBox box;
        Feedback(Object token, SceneSortableModel.RowEntry<T, K, C> row,
                SceneSortableSelection.Position<T, K, C> position, LayoutBox box) {
            this.token = token; this.row = row; this.position = position; this.box = box;
        }
    }
    final Signal<Feedback<T, K, C>> feedback;
    private final SceneSortableModel<T, K, C> model;
    private final SceneSortableSelection<T, K, C> selection;
    private SceneSortableModel.RowEntry<T, K, C> active;
    private SceneSortableSelection.Position<T, K, C> position;
    private Object token;
    SceneSortableInteraction(SceneSortableModel<T, K, C> model) {
        this.model = model; selection = new SceneSortableSelection<>(model); feedback = model.context.domain().signal(null);
    }
    void clear() {
        if (active != null) model.motion.finish();
        active = null; position = null; token = null; feedback.set(null);
    }
    private void clear(SceneSortableModel.RowEntry<T, K, C> row) { if (active == row) clear(); }
    private void publish(LayoutBox box) {
        Feedback<T, K, C> old = feedback.get();
        if (old != null && old.row == active && SceneSortableSelection.same(old.position, position)
                && old.box.x == box.x && old.box.y == box.y && old.box.width == box.width && old.box.height == box.height) return;
        Feedback<T, K, C> value = new Feedback<>(token, active, position, box);
        feedback.set(value); model.motion.show(value);
    }
    private boolean ownsSession(SceneSortableModel.RowEntry<T, K, C> row) {
        SceneDragDrop.Snapshot<Object> scope = model.scope.snapshot().get();
        return scope.active && Objects.equals(scope.source, new SceneSortableKeys.SourceKey<>(row.key));
    }
    private void begin(SceneSortableModel.RowEntry<T, K, C> row) {
        if (active == row) return;
        clear(); active = row; token = new Object();
        position = new SceneSortableSelection.Position<>(row.container.get(), model.successor(row));
    }
    private SceneSortableSelection.Position<T, K, C> resolvePosition(SceneSortableModel.RowEntry<T, K, C> row,
            SceneGeometryView geometry, boolean keyboard, int x, int y) {
        if (!keyboard && !selection.occupies(row, position, geometry, x, y)) return selection.pointer(row, geometry, x, y);
        return selection.valid(row, position, geometry) ? position : null;
    }
    private void navigate(SceneSortableModel.RowEntry<T, K, C> row, SceneGeometryView geometry, SceneEditEvent.Command direction) {
        SceneSortableSelection.Position<T, K, C> next = selection.step(row, position, geometry, direction);
        if (!SceneSortableSelection.same(position, next)) model.motion.geometryChanged(geometry);
        model.motion.positionChanged(position, next);
        if (next != position) model.runtime.requestInputReveal(row.node);
        position = next;
    }
    private SceneSortable.Move<K, C> moveIntent(SceneSortableModel.RowEntry<T, K, C> row, SceneGeometryView geometry) {
        if (active != row || !selection.valid(row, position, geometry) || model.rows.get(row.key) != row) return null;
        if (position.container == row.container.get() && Objects.equals(position.before, model.successor(row))) return null;
        return new SceneSortable.Move<>(row.key, position.container.key, position.before);
    }
    SceneDragSupport.Observer observer(SceneSortableModel.RowEntry<T, K, C> row, SceneDragSupport.Observer delegate) {
        return new SourceObserver(row, delegate);
    }
    private final class SourceObserver implements SceneDragSupport.Observer {
        private final SceneSortableModel.RowEntry<T, K, C> row;
        private final SceneDragSupport.Observer delegate;
        SourceObserver(SceneSortableModel.RowEntry<T, K, C> row, SceneDragSupport.Observer delegate) { this.row = row; this.delegate = delegate; }
        @Override public boolean same(SceneDragSupport.Observer other) {
            if (!(other instanceof SceneSortableInteraction.SourceObserver)) return false;
            SceneSortableInteraction<?, ?, ?>.SourceObserver that = (SceneSortableInteraction<?, ?, ?>.SourceObserver) other;
            return that.row == row && delegate.same(that.delegate);
        }
        @Override public void configure(SceneNode node, SceneDragSupport.Observer next) {
            SceneSortableInteraction<?, ?, ?>.SourceObserver wrapper = (SceneSortableInteraction<?, ?, ?>.SourceObserver) next;
            // 同键明确迁移只刷新当前容器锚点；既有会话和预览身份继续存续。
            delegate.configure(node, wrapper.delegate);
        }
        @Override public void attach(SceneRuntime runtime, SceneNode node, Runnable cancel) { delegate.attach(runtime, node, cancel); }
        @Override public void detach(SceneNode node) { try { delegate.detach(node); } finally { clear(row); } }
        @Override public void cancel(SceneNode node) { try { delegate.cancel(node); } finally { clear(row); } }
        @Override public void update(SceneNode node, SceneGeometryView geometry, boolean keyboard, LayoutBox box, int x, int y) {
            delegate.update(node, geometry, keyboard, box, x, y);
            if (!ownsSession(row)) { clear(row); return; }
            begin(row);
            model.motion.geometryChanged(geometry);
            SceneSortableSelection.Position<T, K, C> next = resolvePosition(row, geometry, keyboard, x, y);
            model.motion.positionChanged(position, next);
            position = next;
            publish(box);
        }
        @Override public Runnable normalDrop(SceneNode node, SceneGeometryView geometry, boolean keyboard, LayoutBox box, int x, int y) {
            SceneSortable.Move<K, C> move = null;
            try {
                update(node, geometry, keyboard, box, x, y);
                move = moveIntent(row, geometry);
                // 委托 Scope 的正常结束及清理，丢弃其通用 Drop（本控件只发布 Move）。
                delegate.normalDrop(node, geometry, keyboard, box, x, y);
            } finally { clear(row); }
            SceneSortable.Move<K, C> intent = move;
            return intent == null ? null : () -> model.onMove.accept(intent);
        }
        @Override public LayoutBox keyboardTarget(SceneNode node, SceneGeometryView geometry, SceneEditEvent.Command direction) {
            if (active != row) return null;
            if (direction != null) navigate(row, geometry, direction);
            if (position == null || !selection.valid(row, position, geometry)) return null;
            SceneGeometryView.Entry entry = geometry.get(row.declaration.content);
            LayoutBox visible = SceneDragSupport.visibleBox(geometry, entry);
            if (visible != null && visible.width > 0 && visible.height > 0) return visible;
            // 导航请求先由正式 reveal 落实；同批尚未显露时只使用当前目标容器可见片段。
            LayoutBox container = SceneDragSupport.visibleBox(geometry, geometry.get(position.container.declaration.items));
            return container != null && container.width > 0 && container.height > 0 ? container : null;
        }
        @Override public SceneNode scrollSource(SceneNode node, SceneGeometryView geometry) {
            if (active != row || !selection.valid(row, position, geometry)) return null;
            SceneNode target = position.container.declaration.items;
            if (!model.runtime.interactive(target) || geometry.get(target) == null
                    || geometry.layerBoundary(target) != geometry.layerBoundary(node)) return null;
            return target;
        }
    }
}
