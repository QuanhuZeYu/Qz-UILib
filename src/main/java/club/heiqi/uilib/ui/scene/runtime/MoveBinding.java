package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 明确转移在同批结构退出前生效，保留持续挂载身份。 */
final class MoveBinding implements NodeBinding {
    private final ReadableSignal<SceneNode> destination;

    MoveBinding(ReadableSignal<SceneNode> destination) {
        this.destination = destination;
    }

    @Override
    public BindingActivation activate(SceneRuntime runtime, SceneNode node, Owner owner) {
        return NodeBinding.value(destination, (moving, parent) -> runtime.relocate(moving, parent))
                .activate(runtime, node, owner);
    }
}
