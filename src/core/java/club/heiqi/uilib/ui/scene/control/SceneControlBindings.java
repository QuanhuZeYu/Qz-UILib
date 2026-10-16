package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.function.BiConsumer;

/** 观测只读声明来源；apply 读取的临时呈现不能反过来重启结构或有限时间线。 */
final class SceneControlBindings {
    private SceneControlBindings() {}
    static <V> NodeBinding declaration(ReadableSignal<V> source, BiConsumer<SceneNode, V> apply) {
        return (runtime, node, owner) -> new BindingActivation() {
            private V desired;
            private boolean pending;
            private final Observation observation = runtime.domain().observe(owner,
                    () -> { desired = source.get(); pending = true; }, () -> runtime.invalidateBinding(node));
            @Override public void refresh() {
                observation.refresh();
                if (pending) { pending = false; apply.accept(node, desired); }
            }
            @Override public void close() { observation.close(); }
        };
    }
}
