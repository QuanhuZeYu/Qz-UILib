package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.DoubleUnaryOperator;

/** 来源观测和时间推进分开；apply不进入目标依赖采集，也不在只读布局或绘制中执行。 */
final class TransitionBinding implements NodeBinding {
    private final ReadableSignal<Float> source;
    private final long duration;
    private final DoubleUnaryOperator easing;
    private final BiConsumer<SceneNode, Float> apply;

    TransitionBinding(ReadableSignal<Float> source, long duration, DoubleUnaryOperator easing,
                      BiConsumer<SceneNode, Float> apply) {
        this.source = Objects.requireNonNull(source, "过渡目标");
        if (duration < 0) throw new IllegalArgumentException("过渡时长不能为负");
        this.duration = duration;
        this.easing = Objects.requireNonNull(easing, "过渡曲线");
        this.apply = Objects.requireNonNull(apply, "呈现映射");
    }

    @Override public BindingActivation activate(SceneRuntime runtime, SceneNode node, Owner owner) {
        return new Active(runtime, node, owner);
    }

    private final class Active implements BindingActivation, SceneTransitions.Step {
        private final SceneRuntime runtime;
        private final SceneNode node;
        private final Observation observation;
        private float desired, from, to, current;
        private long started, activeDuration;
        private float reversingStart;
        private double shortening=1, eased=1;
        private boolean initialized, closed;

        Active(SceneRuntime runtime, SceneNode node, Owner owner) {
            this.runtime = runtime;
            this.node = node;
            observation = runtime.domain().observe(owner, () -> {
                Float value = source.get();
                if (value == null || !Float.isFinite(value))
                    throw new IllegalArgumentException("过渡目标必须是有限数值");
                desired = value;
            }, () -> runtime.invalidateBinding(node));
        }

        @Override public void refresh() {
            if (closed) return;
            observation.refresh();
            if (!initialized) {
                initialized = true;
                from = to = current = reversingStart = desired;
                apply.accept(node, current);
            } else if (desired != to) {
                // 同帧反向沿当前值续接，不能重新从上一个端点开始。
                advance(runtime.transitions().now());
                // CSS反向缩时：回到本段反向起点时按已走曲线比例缩短，避免短途仍拖满时长。
                if(desired==reversingStart) {
                    shortening=Math.abs(eased*shortening+1-shortening);
                    reversingStart=to;
                } else {
                    shortening=1;
                    reversingStart=current;
                }
                from = current;
                to = desired;
                activeDuration=Math.round(duration*shortening);
                started = runtime.transitions().now();
                if (activeDuration == 0 || from == to) {
                    publish(to);
                    runtime.transitions().stop(this);
                } else runtime.transitions().start(this);
            }
        }

        @Override public boolean advance(long now) {
            if (closed) return false;
            long elapsed = now - started;
            if (activeDuration == 0 || elapsed >= activeDuration || current == to && from == to) {
                eased=1;
                publish(to);
                return false;
            }
            double progress = Math.max(0, (double) elapsed / activeDuration);
            double factor = easing.applyAsDouble(progress);
            if (!Double.isFinite(factor) || factor < 0 || factor > 1)
                throw new IllegalArgumentException("过渡曲线必须返回0到1之间的有限值");
            eased=factor;
            publish((float) (from + ((double) to - from) * factor));
            return true;
        }

        private void publish(float value) {
            if (current == value) return;
            current = value;
            apply.accept(node, value);
        }

        @Override public void close() {
            if (closed) return;
            closed = true;
            runtime.transitions().stop(this);
            observation.close();
        }
    }
}
