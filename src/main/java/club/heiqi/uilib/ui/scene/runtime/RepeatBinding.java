package club.heiqi.uilib.ui.scene.runtime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 键映射属于逻辑节点，挂载激活只拥有订阅；同一个描述可用于多个节点。 */
final class RepeatBinding<T, K> implements NodeBinding {
    private static final class State<T, K> {
        final Map<K, Signal<T>> values = new LinkedHashMap<>();
        final Map<K, SceneNode> nodes = new LinkedHashMap<>();
    }

    private final ReadableSignal<List<T>> source;
    private final Function<T, K> key;
    private final Function<ReadableSignal<T>, SceneNode> declaration;

    RepeatBinding(
            ReadableSignal<List<T>> source,
            Function<T, K> key,
            Function<ReadableSignal<T>, SceneNode> declaration) {
        this.source = source;
        this.key = key;
        this.declaration = declaration;
    }

    private static void releaseUnattached(State<?, ?> state) {
        Throwable failure = null;
        for (SceneNode entry : state.nodes.values()) {
            if (entry.logicalOwner() == state && entry.runtime() == null) {
                try {
                    entry.destroy();
                } catch (RuntimeException | Error error) {
                    if (failure == null) failure = error;
                    else if (failure != error) failure.addSuppressed(error);
                }
            }
        }
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
    }

    @Override
    @SuppressWarnings("unchecked")
    public BindingActivation activate(SceneRuntime runtime, SceneNode node, Owner owner) {
        State<T, K> state = (State<T, K>) node.declarationState(this, () -> {
            State<T, K> created = new State<>();
            node.own(() -> releaseUnattached(created));
            return created;
        });
        Observation observation =
                runtime.domain()
                        .observe(
                                owner,
                                () -> {
                                    try {
                                        List<T> current = new ArrayList<>(source.get());
                                        Set<K> keys = new HashSet<>();
                                        for (T value : current)
                                            if (!keys.add(key.apply(value)))
                                                throw new IllegalArgumentException("业务列表键重复: " + node.id() + " / " + key.apply(value));
                                        List<SceneNode> next = new ArrayList<>();
                                        for (T value : current) {
                                            K identity = key.apply(value);
                                            Signal<T> cell = state.values.get(identity);
                                            if (cell == null) {
                                                cell = runtime.domain().signal(value);
                                                state.values.put(identity, cell);
                                                SceneNode created = declaration.apply(cell);
                                                if (created == null || created.destroyed()
                                                        || created.runtime() != null || created.parent() != null)
                                                    throw new IllegalArgumentException("条目声明不可拥有: " + node.id() + " / " + identity);
                                                created.frameworkOwnership(runtime, state);
                                                state.nodes.put(identity, created);
                                            } else cell.set(value);
                                            SceneNode entry = state.nodes.get(identity);
                                            // 已明确迁移的条目不再被旧集合重新取得或销毁。
                                            if (entry.logicalOwner() == state) next.add(entry);
                                        }
                                        runtime.reconcile(node, next, state);
                                        state.values.keySet().retainAll(keys);
                                        state.nodes.keySet().retainAll(keys);
                                    } catch (RuntimeException | Error failure) {
                                        try {
                                            releaseUnattached(state);
                                        } catch (RuntimeException | Error cleanup) {
                                            if (cleanup != failure) failure.addSuppressed(cleanup);
                                        }
                                        throw failure;
                                    }
                                },
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
    }
}
