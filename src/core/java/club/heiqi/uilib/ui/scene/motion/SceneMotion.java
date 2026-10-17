package club.heiqi.uilib.ui.scene.motion;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.BindingActivation;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/** 页面只声明目标与策略；常见属性负责自己的失效归属，业务不维护进度。 */
public final class SceneMotion {
    private SceneMotion() {}
    public static NodeBinding scroll(ReadableSignal<ScrollMotionOptions> options) { return NodeBinding.scrollMotion(options); }
    public static NodeBinding presence(ReadableSignal<Boolean> present, ReadableSignal<PresenceOptions> options) { return NodeBinding.presence(present, options); }
    public static NodeBinding layoutPositions(ReadableSignal<MotionSpec> spec) { return NodeBinding.layoutPositions(spec); }
    public static NodeBinding layoutSize(ReadableSignal<MotionSpec> spec) { return NodeBinding.layoutSize(spec); }
    public static NodeBinding opacity(ReadableSignal<Float> target, ReadableSignal<MotionSpec> spec) {
        return NodeBinding.motion(target, spec, false, "opacity", (node, value) -> node.opacity(Math.max(0, Math.min(1, value))));
    }
    public static NodeBinding translateX(ReadableSignal<Float> target, ReadableSignal<MotionSpec> spec) {
        return NodeBinding.motion(target, spec, true, "translateX", (node, value) -> node.translation(value, node.translationY()));
    }
    public static NodeBinding translateY(ReadableSignal<Float> target, ReadableSignal<MotionSpec> spec) {
        return NodeBinding.motion(target, spec, true, "translateY", (node, value) -> node.translation(node.translationX(), value));
    }
    public static NodeBinding background(ReadableSignal<Integer> target, ReadableSignal<MotionSpec> spec) {
        return color(target, spec, "background", SceneNode::background);
    }
    /** 预乘 alpha 的合法颜色插值；透明端点不带入不可见 RGB。 */
    public static NodeBinding color(ReadableSignal<Integer> target, ReadableSignal<MotionSpec> spec,
            Object channel, BiConsumer<SceneNode, Integer> apply) {
        Objects.requireNonNull(target, "颜色目标"); Objects.requireNonNull(apply, "颜色呈现");
        return (runtime, node, owner) -> {
            float[] components = new float[4]; List<BindingActivation> active = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                final int component = i;
                ReadableSignal<Float> source = () -> {
                    int color = Objects.requireNonNull(target.get(), "颜色目标"), alpha = color >>> 24;
                    return component == 0 ? alpha / 255f : (color >>> (24 - component * 8) & 255) * alpha / 65025f;
                };
                active.add(NodeBinding.motion(source, spec, false, channel + "." + i, (n, value) -> {
                    components[component] = Math.max(0, Math.min(1, value));
                    float a = components[0]; int color = Math.round(a * 255) << 24;
                    if (a > 0) for (int c = 1; c < 4; c++)
                        color |= Math.round(Math.min(1, components[c] / a) * 255) << (24 - c * 8);
                    apply.accept(n, color);
                }).activate(runtime, node, owner));
            }
            return new BindingActivation() {
                public void refresh() { for (BindingActivation binding : active) binding.refresh(); }
                public void close() { for (BindingActivation binding : active) binding.close(); }
            };
        };
    }
    public static int alpha(int color, float opacity) {
        return color & 0x00ffffff | Math.round((color >>> 24) * opacity) << 24;
    }
}
