package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.ThemeMode;
import club.heiqi.uilib.ui.scene.text.TextStyle;

/** 邮箱视觉角色由明确的模式派生，不从文字颜色猜测主题。 */
final class MailPalette {
    final int canvas, surface, read, unread, selected, selectedHover, selectedPressed, hover, pressed, outline, mutedText, text, accent;
    final SceneTheme theme;

    private MailPalette(SceneTheme base, boolean dark) {
        canvas = dark ? 0xff111315 : 0xfff6f8fc;
        surface = dark ? 0xff1b1d1f : 0xffffffff;
        read = dark ? 0xff202327 : 0xfff2f6fc;
        unread = surface;
        selected = dark ? 0xff30435e : 0xffc2dbff;
        selectedHover = dark ? 0xff3a5272 : 0xffb7d1f7;
        selectedPressed = dark ? 0xff425d80 : 0xffa8c7fa;
        hover = dark ? 0xff30343b : 0xffe9eef6;
        pressed = dark ? 0xff3e4a5d : 0xffd3e3fd;
        outline = dark ? 0xff45484e : 0xffdce1e8;
        mutedText = dark ? 0xffb6bcc6 : 0xff5f6368;
        text = dark ? 0xffe3e3e3 : 0xff202124;
        accent = dark ? 0xffa8c7fa : 0xff0b57d0;
        TextStyle style = MailTypography.style(base.textStyle).withColor(text);
        theme = new SceneTheme(style, surface, 0x00000000, hover, selected, accent,
                dark ? 0xff26282b : 0xffedf0f4, mutedText,
                dark ? 0xffdfe3e8 : 0xff303134, dark ? 0xff202124 : 0xffffffff,
                base.padding, base.gap);
    }

    static MailPalette from(SceneTheme base, ThemeMode mode) {
        return new MailPalette(base, mode.isDark());
    }
}
