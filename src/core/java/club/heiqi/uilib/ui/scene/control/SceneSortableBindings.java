package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.function.BiConsumer;

/** 与有限过渡一样，观测只读来源；迁移期间的焦点/临时归属不能成为集合声明的依赖。 */
final class SceneSortableBindings {
    private SceneSortableBindings() {}
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
