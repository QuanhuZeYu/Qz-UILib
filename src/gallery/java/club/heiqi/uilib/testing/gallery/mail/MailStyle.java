package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.SceneLabel;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 案例局部视觉词汇，尺寸仍由正式控件测量。 */
final class MailStyle {
    private MailStyle() {}
    static SceneTheme buttonTheme(SceneTheme t, boolean selected) {
        boolean dark = t.textStyle.color == 0xfffafafa || t.textStyle.color == 0xfff1f4f8;
        int surface = selected ? (dark ? 0xff263c55 : 0xffdceafd) : 0x00000000;
        return new SceneTheme(t.textStyle, t.panel, surface, dark ? 0xff303b49 : 0xffeef3fa,
                dark ? 0xff3b4b60 : 0xffdbe8fa, t.focus, t.disabled, t.disabledText,
                t.tooltip, t.tooltipText, Math.max(4, t.padding / 2), t.gap);
    }
    static SceneNode button(ScenePageContext p, ReadableSignal<String> text, ReadableSignal<SceneTheme> theme, Runnable action) {
        return SceneButton.create(p, text, () -> buttonTheme(theme.get(), false), action).size(Size.natural(), Size.natural());
    }
    static SceneNode label(ScenePageContext p, ReadableSignal<String> text, ReadableSignal<SceneTheme> theme, float scale, boolean muted) {
        return SceneLabel.create(p, text, () -> theme.get().withTextStyle(theme.get().textStyle
                .withEm(theme.get().textStyle.logicalEm * scale).withColor(muted ? 0xff7b8492 : theme.get().textStyle.color)));
    }
}
