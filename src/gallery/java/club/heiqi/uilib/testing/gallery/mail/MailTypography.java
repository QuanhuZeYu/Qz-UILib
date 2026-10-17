package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.UiFontSources;

/** 邮箱沿共同UI字体来源；业务仍保留显式样式与原列表密度。 */
final class MailTypography {
    private MailTypography(){}
    static TextStyle style(TextStyle base){return UiFontSources.style(base);}
}
