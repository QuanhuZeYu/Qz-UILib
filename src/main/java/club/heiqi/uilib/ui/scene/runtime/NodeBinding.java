package club.heiqi.uilib.ui.scene.runtime;

import java.util.function.BiConsumer;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 可重新激活的逻辑声明。绑定值失效只唤醒呈现，不运行业务 Effect。 */
public interface NodeBinding {
    BindingActivation activate(SceneRuntime runtime, SceneNode node, Owner owner);

    /** 按业务键保留条目节点；业务操作只更新逻辑列表，节点由框架维护。 */
    static <T, K> NodeBinding repeat(
            ReadableSignal<List<T>> source,
            Function<T, K> key,
            Function<ReadableSignal<T>, SceneNode> declaration) {
        return new RepeatBinding<>(source, key, declaration);
    }

    static NodeBinding children(ReadableSignal<List<SceneNode>> source) {
        return value(source, (node, children) -> node.runtime().reconcile(node, children));
    }

    static <T> NodeBinding value(ReadableSignal<T> source, BiConsumer<SceneNode, T> apply) {
        return (runtime, node, owner) -> {
            Observation observation =
                    runtime.domain()
                            .observe(
                                    owner,
                                    () -> apply.accept(node, source.get()),
                                    () -> runtime.invalidateBinding(node));
            return new BindingActivation() {
                @Override
                public void refresh() {
                    observation.refresh();
                }

                @Override
                public void close() {
                    observation.close();
                }
            };
        };
    }
}
