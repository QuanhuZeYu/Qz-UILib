package club.heiqi.uilib.testing.gallery.mail;

import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.font.FontSource;
import club.heiqi.uilib.ui.scene.text.font.FontTextService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 案例显式选择现代字体，不改变宿主字体配置；显式候选仍由调用方决定。
 * 资源来自 google/fonts a85815a42757630ce188fdad368c2dfc444d4773，原 OFL 随资源分发。
 * classpath 字节归当前加载器的不变资源代际，真实样式匹配及失效仍归正式字体服务。 */
final class MailTypography {
    private static final List<FontSource> CANDIDATES = candidates();
    private MailTypography() {}

    private static List<FontSource> candidates() {
        ClassLoader loader = MailTypography.class.getClassLoader();
        try {
            List<FontSource> result = new ArrayList<>();
            result.add(FontSource.resource("mail-noto-sans-sc", 1, loader,
                    "assets/qz_uilib/gallery/fonts/noto-sans-sc/NotoSansSC-VF.ttf", false,
                    Collections.<String>emptySet()));
            result.addAll(FontTextService.defaultSources(loader, 1));
            return Collections.unmodifiableList(result);
        } catch (IOException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    static TextStyle style(TextStyle base) {
        return new TextStyle(base.font, base.logicalEm, base.color,
                base.candidates() == null ? CANDIDATES : base.candidates(),
                base.effects, base.decoration, base.fontStyle, base.baselineShift, base.letterSpacing,
                base.highlightColor, base.defaultHighlightColor, base.script);
    }
}
