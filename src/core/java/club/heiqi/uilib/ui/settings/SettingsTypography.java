package club.heiqi.uilib.ui.settings;

import java.io.IOException;
import java.util.Collections;
import java.util.ArrayList;
import club.heiqi.uilib.ui.scene.text.font.MathTextFontSources;
import java.util.List;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.FontStyle;
import club.heiqi.uilib.ui.scene.text.font.FontSource;

/** 工作区界面显式使用随包无衬线字族；不会追加到用户的 font.candidates。
 * 原始 Noto Sans SC 来自 google/fonts a85815a42757630ce188fdad368c2dfc444d4773，OFL 与资源一起发行。
 * 样本另用当前生效候选，因此空配置仍能操作界面且不会伪造字体覆盖。 */
final class SettingsTypography {
    private static final List<FontSource> UI = sources();
    private static final List<FontSource> PATH = pathSources();
    private SettingsTypography() {}
    private static List<FontSource> sources() {
        try {
            return Collections.singletonList(FontSource.resource("uilib-settings-ui",1,
                    SettingsTypography.class.getClassLoader(),"assets/qz_uilib/fonts/ui/NotoSansSC-VF.ttf",
                    false,Collections.<String>emptySet()));
        } catch(IOException failure) { throw new ExceptionInInitializerError(failure); }
    }
    private static List<FontSource> pathSources() {
        List<FontSource> sources = new ArrayList<>(MathTextFontSources.namedSources(1).get(MathTextFontSources.MONO));
        sources.addAll(UI);
        return Collections.unmodifiableList(sources);
    }
    static TextStyle pathStyle(int em,int color) {
        return new TextStyle("settings-path",em,color,PATH);
    }
    static TextStyle style(int em,int color,int weight) {
        return new TextStyle("settings-ui",em,color,UI)
                .withFontStyle(new FontStyle(weight,FontStyle.Posture.NORMAL));
    }
}
