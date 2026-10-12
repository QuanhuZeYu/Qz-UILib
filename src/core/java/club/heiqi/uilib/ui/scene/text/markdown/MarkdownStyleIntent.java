package club.heiqi.uilib.ui.scene.text.markdown;

import club.heiqi.uilib.ui.scene.text.FontStyle;
import club.heiqi.uilib.ui.scene.text.TextStyleOverride;
import club.heiqi.uilib.ui.scene.text.font.MathTextFontSources;

/** 本转换器实际支持的样式声明组合；未声明属性仍在共同内容 resolve 时继承主题。 */
final class MarkdownStyleIntent {
    private static final int BOLD = 1, ITALIC = 2, UNDERLINE = 4, STRIKE = 8, MONO = 16;
    private static final MarkdownStyleIntent[] VALUES = values();
    static final MarkdownStyleIntent INHERIT = VALUES[0];
    final int key;
    final TextStyleOverride declaration;

    private MarkdownStyleIntent(int key) {
        this.key = key;
        TextStyleOverride.Builder builder = TextStyleOverride.builder();
        if ((key & BOLD) != 0) builder.weight(700);
        if ((key & ITALIC) != 0) builder.posture(FontStyle.Posture.ITALIC);
        if ((key & UNDERLINE) != 0) builder.underline(true);
        if ((key & STRIKE) != 0) builder.strike(true);
        if ((key & (UNDERLINE | STRIKE)) != 0) builder.decorationFromEm(true);
        if ((key & MONO) != 0) builder.font(MathTextFontSources.MONO);
        declaration = builder.build();
    }

    private static MarkdownStyleIntent[] values() {
        MarkdownStyleIntent[] result = new MarkdownStyleIntent[1 << 5];
        for (int key = 0; key < result.length; key++) result[key] = new MarkdownStyleIntent(key);
        return result;
    }

    MarkdownStyleIntent bold() {
        return VALUES[key | BOLD];
    }

    MarkdownStyleIntent italic() {
        return VALUES[key | ITALIC];
    }

    MarkdownStyleIntent underline() {
        return VALUES[key | UNDERLINE];
    }

    MarkdownStyleIntent strike() {
        return VALUES[key | STRIKE];
    }

    MarkdownStyleIntent mono() {
        return VALUES[key | MONO];
    }
}
