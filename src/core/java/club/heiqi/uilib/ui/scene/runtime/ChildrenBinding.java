package club.heiqi.uilib.ui.scene.runtime;

import java.util.ArrayList;
import java.util.List;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 声明对应关系与挂载订阅分离，旧声明不再取得已转移条目。 */
final class ChildrenBinding implements NodeBinding {
    private static final class State {
        final List<SceneNode> associated = new ArrayList<>();
    }

    private final ReadableSignal<List<SceneNode>> source;
    private final boolean owned;

    ChildrenBinding(ReadableSignal<List<SceneNode>> source, boolean owned) {
        this.source = source;
        this.owned = owned;
    }

    @Override
    public BindingActivation activate(SceneRuntime runtime, SceneNode node, Owner owner) {
        State state = (State) node.declarationState(this, State::new);
        return NodeBinding.value(source, (parent, children) -> {
            List<SceneNode> next = new ArrayList<>();
            for (SceneNode child : children) {
                if (state.associated.contains(child) && child.transferred()
                        && child.logicalOwner() != state) continue;
                next.add(child);
            }
            runtime.reconcile(parent, next, state, owned);
            state.associated.clear();
            state.associated.addAll(children);
        }).activate(runtime, node, owner);
    }
}
