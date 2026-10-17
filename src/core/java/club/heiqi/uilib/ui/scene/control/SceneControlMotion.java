package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.motion.MotionSpec;
import club.heiqi.uilib.ui.scene.motion.SceneMotion;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.function.ToIntFunction;

/** 标准控件的颜色通道只订阅真实交互投影和主题，不永久 tick 或重测文本。 */
final class SceneControlMotion {
    private Signal<Integer> color;
    void install(SceneNode node, ReadableSignal<MotionSpec> spec, ToIntFunction<SceneNode> target) { install(node, spec, target, "control.fill"); }
    void install(SceneNode node, ReadableSignal<MotionSpec> spec, ToIntFunction<SceneNode> target, String channel) {
        node.bind((runtime, self, owner) -> {
            color = runtime.domain().signal(target.applyAsInt(self));
            return SceneMotion.color(() -> {
                runtime.interactionRevision().get();
                runtime.pointerRouter().hoverTarget().get();
                runtime.pointerRouter().pressedTarget().get();
                return target.applyAsInt(self);
            }, () -> self.feedbackMotion() == null ? spec.get() : self.feedbackMotion(),
                    channel, (n, value) -> { color.set(value); runtime.invalidatePaint(); }).activate(runtime, self, owner);
        });
    }
    int color(int fallback) { return color == null ? fallback : color.get(); }
    static boolean enabled(SceneNode node) {
        for (SceneNode at = node; at != null; at = at.parent()) if (!at.enabled()) return false;
        return true;
    }
    static int target(SceneNode node, SceneTheme theme) {
        if (!enabled(node)) return theme.disabled;
        if (node.runtime() == null) return theme.button;
        if (node.runtime().pointerRouter().pressedTarget().get() == node) return theme.pressed;
        return node.runtime().pointerRouter().hoverTarget().get() == node ? theme.hover : theme.button;
    }
}
