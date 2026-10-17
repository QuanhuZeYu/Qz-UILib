package club.heiqi.uilib.ui.scene.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 声明对应关系与挂载订阅分离，旧声明不再取得已转移条目。 */
final class ChildrenBinding implements NodeBinding {
    private static final class State {
        final Set<SceneNode> associated =
                Collections.newSetFromMap(new IdentityHashMap<SceneNode, Boolean>());
    }

    private final ReadableSignal<List<SceneNode>> source;
    private final boolean owned;
    private final ReadableSignal<club.heiqi.uilib.ui.scene.motion.PresenceOptions> exits;

    ChildrenBinding(ReadableSignal<List<SceneNode>> source, boolean owned) {
        this(source, owned, null);
    }
    ChildrenBinding(ReadableSignal<List<SceneNode>> source, boolean owned,
            ReadableSignal<club.heiqi.uilib.ui.scene.motion.PresenceOptions> exits) {
        this.exits = exits;
        this.source = source;
        this.owned = owned;
    }

    @Override
    public BindingActivation activate(SceneRuntime runtime, SceneNode node, Owner owner) {
        State state = (State) node.declarationState(this, State::new);
        AutoCloseable registration = exits == null ? () -> {} : runtime.registerPresenceSource(node, state);
        owner.own(registration);
        BindingActivation value = NodeBinding.value(source, (parent, children) -> {
            List<SceneNode> next = new ArrayList<>();
            for (SceneNode child : children) {
                if (state.associated.contains(child) && child.transferred()
                        && child.logicalOwner() != state) continue;
                next.add(child);
            }
            runtime.reconcile(parent, next, state, owned, exits == null ? null : exits.get(), null);
            state.associated.clear();
            state.associated.addAll(children);
        }).activate(runtime, node, owner);
        return new BindingActivation() {
            public void refresh() { value.refresh(); }
            public void close() {
                try { value.close(); } finally {
                    try { registration.close(); }
                    catch (RuntimeException | Error failure) { throw failure; }
                    catch (Exception failure) { throw new IllegalStateException("退场声明释放失败", failure); }
                }
            }
        };
    }
}
