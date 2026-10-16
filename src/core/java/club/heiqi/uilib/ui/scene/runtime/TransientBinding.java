package club.heiqi.uilib.ui.scene.runtime;

import java.util.Objects;
import java.util.function.BiConsumer;
import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 离散结果的挂载期呈现期限；与数值过渡共用有限Step，不执行业务动作。 */
final class TransientBinding<T> implements NodeBinding {
    private final ReadableSignal<T> source;
    private final long duration;
    private final BiConsumer<SceneNode, T> apply;

    TransientBinding(ReadableSignal<T> source, long duration, BiConsumer<SceneNode, T> apply) {
        this.source = Objects.requireNonNull(source, "短暂呈现来源");
        if (duration < 0) throw new IllegalArgumentException("呈现时长不能为负");
        this.duration = duration;
        this.apply = Objects.requireNonNull(apply, "短暂呈现映射");
    }

    @Override public BindingActivation activate(SceneRuntime runtime, SceneNode node, Owner owner) {
        return new Active(runtime, node, owner);
    }

    private final class Active implements BindingActivation, SceneTransitions.Step {
        private final SceneRuntime runtime;
        private final SceneNode node;
        private final Observation observation;
        private T desired, current;
        private long started;
        private boolean initialized, invalidated, shown, closed;

        Active(SceneRuntime runtime, SceneNode node, Owner owner) {
            this.runtime = runtime;
            this.node = node;
            observation = runtime.domain().observe(owner, () -> desired = source.get(), () -> {
                invalidated = true;
                runtime.invalidateBinding(node);
            });
        }

        @Override public void refresh() {
            if (closed) return;
            observation.refresh();
            invalidated = false;
            if (initialized && desired == current) return;
            initialized = true;
            current = desired;
            runtime.transitions().stop(this);
            shown = current != null && duration != 0;
            apply.accept(node, shown ? current : null);
            if (shown) {
                started = runtime.transitions().now();
                runtime.transitions().start(this);
            }
        }

        @Override public boolean advance(long now) {
            if (closed) return false;
            // 帧先推进再结算来源；失效期间不能用旧期限清掉新结果。
            if (invalidated) return true;
            if (now - started < duration) return true;
            shown = false;
            apply.accept(node, null);
            return false;
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            runtime.transitions().stop(this);
            observation.close();
            if (shown) { shown = false; apply.accept(node, null); }
        }
    }
}
