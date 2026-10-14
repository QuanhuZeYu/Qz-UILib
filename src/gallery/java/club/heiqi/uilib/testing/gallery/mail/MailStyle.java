package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import club.heiqi.uilib.ui.scene.control.SceneTextPolicy;

/** 案例局部视觉词汇，尺寸仍由正式控件测量。 */
final class MailStyle {
    private MailStyle() {}
    static int px(SceneTheme theme, float value) { return Math.round(theme.textStyle.logicalEm * value / 15f); }
    static SceneNode surface(ReadableSignal<SceneTheme> theme, ReadableSignal<Integer> color, float radius) {
        return surface(theme,color,radius,true);
    }
    static SceneNode surface(ReadableSignal<SceneTheme> theme, ReadableSignal<Integer> color, float radius, boolean feedback) {
        return new SceneNode().control(new MailSurface(theme,color,radius,feedback))
                .bind(NodeBinding.value(theme,(n,t)->n.style(t.textStyle)));
    }
    static SceneNode single(ScenePageContext p, ReadableSignal<String> text, ReadableSignal<SceneTheme> theme) {
        return SceneLabel.create(p,text,theme,SceneTextPolicy.ELLIPSIS_SINGLE_LINE);
    }
    static SceneTheme buttonTheme(SceneTheme t, boolean selected) {
        int surface = selected ? t.pressed : 0x00000000;
        return new SceneTheme(t.textStyle, t.panel, surface, t.hover,
                t.pressed, t.focus, t.disabled, t.disabledText,
                t.tooltip, t.tooltipText, Math.max(4, t.padding / 2), t.gap);
    }
    static SceneNode button(ScenePageContext p, ReadableSignal<String> text, ReadableSignal<SceneTheme> theme, Runnable action) {
        return SceneButton.create(p, text, () -> buttonTheme(theme.get(), false), action).size(Size.natural(), Size.natural());
    }
    static SceneNode label(ScenePageContext p, ReadableSignal<String> text, ReadableSignal<SceneTheme> theme, float scale, boolean muted) {
        return SceneLabel.create(p, text, () -> theme.get().withTextStyle(theme.get().textStyle
                .withEm(theme.get().textStyle.logicalEm * scale).withColor(muted ? theme.get().disabledText : theme.get().textStyle.color)));
    }
}
