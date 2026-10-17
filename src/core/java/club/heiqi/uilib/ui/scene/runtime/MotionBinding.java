package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.reactive.Observation;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.motion.MotionValue;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.Objects;
import java.util.function.BiConsumer;

/** 来源收集不执行呈现映射；映射与推进只在框架准备阶段，卸载结束轨迹。 */
final class MotionBinding implements NodeBinding {
    private final ReadableSignal<Float> source;
    private final ReadableSignal<MotionSpec> strategy;
    private final BiConsumer<SceneNode, Float> apply;
    private final Object channel;
    private final boolean spatial;
    MotionBinding(ReadableSignal<Float> source, ReadableSignal<MotionSpec> strategy,
            boolean spatial, Object channel, BiConsumer<SceneNode, Float> apply) {
        this.source = Objects.requireNonNull(source, "运动目标");
        this.strategy = Objects.requireNonNull(strategy, "运动策略来源");
        this.apply = Objects.requireNonNull(apply, "呈现映射");
        this.spatial = spatial; this.channel = channel;
    }
    public BindingActivation activate(SceneRuntime runtime, SceneNode node, Owner owner) {
        return new Active(runtime, node, owner);
    }
    private final class Active implements BindingActivation, SceneTransitions.Step {
        private final SceneRuntime runtime;
        private final SceneNode node;
        private final MotionValue value = new MotionValue();
        private final Observation observation;
        private final AutoCloseable claim;
        private MotionSpec desiredStrategy;
        private float desired, published;
        private boolean initialized, closed;
        Active(SceneRuntime runtime, SceneNode node, Owner owner) {
            this.runtime = runtime; this.node = node;
            claim = channel == null ? () -> {} : runtime.claimMotion(node, channel, this);
            owner.own(claim);
            observation = runtime.domain().observe(owner, () -> {
                Float target = source.get();
                if (target == null || !Float.isFinite(target))
                    throw new IllegalArgumentException("运动目标必须有限: node=" + node.id());
                desired = target;
                desiredStrategy = runtime.motionPolicy().get().resolve(
                        Objects.requireNonNull(strategy.get(), "运动策略"), spatial);
            }, () -> runtime.invalidateBinding(node));
        }
        public void refresh() {
            try { refreshValue(); } catch (RuntimeException | Error failure) { failed(failure); throw failure; }
        }
        private void refreshValue() {
            if (closed) return;
            observation.refresh();
            desiredStrategy = runtime.motionPolicy().get().resolve(Objects.requireNonNull(strategy.get(), "运动策略"), spatial);
            long now = runtime.transitions().now();
            if (!initialized) {
                initialized = true; value.snap(desired, now); published = desired;
                apply.accept(node, desired);
            }
            if (desiredStrategy.kind == MotionSpec.Kind.DECAY) throw new IllegalArgumentException("属性目标不能使用自由惯性: node=" + node.id());
            value.retarget(desired, desiredStrategy, now);
            publish();
            if (value.active()) runtime.transitions().start(this);
            else runtime.transitions().stop(this);
        }
        public boolean advance(long now) {
            if (closed) return false;
            try { boolean active = value.advance(now); publish(); return active; }
            catch (RuntimeException | Error failure) { failed(failure); throw failure; }
        }
        private void failed(Throwable failure) {
            try { close(); } catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
        }
        private void publish() {
            float next = (float) value.value();
            if (published == next) return;
            published = next; apply.accept(node, next);
        }
        public void close() {
            if (closed) return;
            closed = true; runtime.transitions().stop(this); observation.close();
            try { claim.close(); } catch (RuntimeException failure) { throw failure; }
            catch (Exception failure) { throw new IllegalStateException("运动通道释放失败", failure); }
        }
    }
}
