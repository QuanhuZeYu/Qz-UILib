package club.heiqi.uilib.ui.scene.control;

import java.util.Objects;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 正式标签入口；自然高度依赖文字、字体及最终分配宽度。 */
public final class SceneLabel {
    private SceneLabel() {}

    public static SceneNode create(ScenePageContext context, ReadableSignal<String> text,
            ReadableSignal<SceneTheme> theme) {
        return create(context, text, theme, SceneTextPolicy.WRAP);
    }

    public static SceneNode create(ScenePageContext context, ReadableSignal<String> text,
            ReadableSignal<SceneTheme> theme, SceneTextPolicy policy) {
        Objects.requireNonNull(context, "context").domain().checkWrite();
        return SceneTextControl.create(text, theme, policy, false);
    }
}
