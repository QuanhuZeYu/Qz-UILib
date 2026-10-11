package club.heiqi.uilib.ui.scene.runtime;

import java.util.function.BiConsumer;
import java.util.List;
import java.util.function.Function;
import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 可重新激活的逻辑声明。绑定值失效只唤醒呈现，不运行业务 Effect。 */
public interface NodeBinding {
    BindingActivation activate(SceneRuntime runtime, SceneNode node, Owner owner);

    /** 创建并拥有键条目；持续键延续节点，退出生效自动销毁，同键返回重新创建。 */
    static <T, K> NodeBinding repeat(
            ReadableSignal<List<T>> source,
            Function<T, K> key,
            Function<ReadableSignal<T>, SceneNode> declaration) {
        return new RepeatBinding<>(source, key, declaration);
    }

    /** 同页明确移动；null 目的地表示页面根，调用方仍持有根生命周期。 */
    static NodeBinding move(ReadableSignal<SceneNode> destination) {
        return new MoveBinding(destination);
    }

    /** 创建并拥有的子序列，退出生效自动销毁；可用于条件分支。 */
    static NodeBinding ownedChildren(ReadableSignal<List<SceneNode>> source) {
        return new ChildrenBinding(source, true);
    }

    /** 显式引用调用方保留的逻辑树，退出仅解除挂载。 */
    static NodeBinding children(ReadableSignal<List<SceneNode>> source) {
        return new ChildrenBinding(source, false);
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
