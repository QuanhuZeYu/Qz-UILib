package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.DoubleUnaryOperator;

/** 原有限接口沿同一求解与用户限制；时长、值续接与反向缩时保持。 */
final class TransitionBinding implements NodeBinding {
    private final NodeBinding binding;
    TransitionBinding(ReadableSignal<Float> source, long duration, DoubleUnaryOperator easing,
            BiConsumer<SceneNode, Float> apply) {
        MotionSpec spec = MotionSpec.tween(duration, easing);
        binding = NodeBinding.motion(Objects.requireNonNull(source, "过渡目标"), () -> spec, apply);
    }
    public BindingActivation activate(SceneRuntime runtime, SceneNode node, Owner owner) {
        return binding.activate(runtime, node, owner);
    }
}
