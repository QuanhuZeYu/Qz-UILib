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
    RepeatBinding(ReadableSignal<List<T>> source, Function<T, K> key, Function<ReadableSignal<T>, SceneNode> declaration) {
        this.source = source; this.key = key; this.declaration = declaration;
    }
    @Override @SuppressWarnings("unchecked")
    public BindingActivation activate(SceneRuntime runtime, SceneNode node, Owner owner) {
        State<T, K> state = (State<T, K>) node.declarationState(this, () -> new State<T, K>());
        Observation observation = runtime.domain().observe(owner, () -> {
            List<T> current = new ArrayList<>(source.get());
            Set<K> keys = new HashSet<>();
            for (T value : current) if (!keys.add(key.apply(value))) throw new IllegalArgumentException("业务列表键重复");
            List<SceneNode> next = new ArrayList<>();
            for (T value : current) {
                K identity = key.apply(value);
                Signal<T> cell = state.values.get(identity);
                if (cell == null) {
                    cell = runtime.domain().signal(value);
                    state.values.put(identity, cell); state.nodes.put(identity, declaration.apply(cell));
                } else cell.set(value);
                next.add(state.nodes.get(identity));
            }
            runtime.reconcile(node, next);
            state.values.keySet().retainAll(keys); state.nodes.keySet().retainAll(keys);
        }, () -> runtime.invalidateBinding(node));
        return new BindingActivation() {
            @Override public void refresh() { observation.refresh(); }
            @Override public void close() { observation.close(); }
        };
    }
}
