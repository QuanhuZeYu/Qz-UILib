package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.ThemeMode;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** 邮箱视觉角色由明确的模式派生，不从文字颜色猜测主题。 */
final class MailPalette {
    final int canvas, surface, read, unread, search, navigation, receive, subtleIcon, selected, selectedHover, selectedPressed, hover, pressed, outline, mutedText, text, accent;
    final SceneTheme theme;

    private MailPalette(SceneTheme base, boolean dark) {
        club.heiqi.uilib.ui.scene.control.SceneAppearance.Palette p=new club.heiqi.uilib.ui.scene.control.SceneAppearance.Palette(dark);
        canvas=p.canvas;
        surface=p.background;
        read=p.surface;
        unread=p.background;
        search=p.surface;
        navigation=p.selected;
        receive=p.selected;
        subtleIcon=p.muted;
        selected=p.selected;
        selectedHover=p.hover;
        selectedPressed=p.border;
        hover=p.hover;
        pressed=p.selected;
        outline=p.border;
        mutedText=p.muted;
        text=p.text;
        accent=p.accent;
        TextStyle style = MailTypography.style(base.textStyle).withColor(text);
        theme = new SceneTheme(style, surface, 0x00000000, hover, selected, accent,
                p.surface,mutedText,p.primary,p.onPrimary,
                base.padding, base.gap);
    }

    static MailPalette from(SceneTheme base, ThemeMode mode) {
        return new MailPalette(base, mode.isDark());
    }
}
