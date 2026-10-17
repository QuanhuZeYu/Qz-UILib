package club.heiqi.uilib.ui.settings;

import java.util.ArrayList;
import java.util.Collections;
import club.heiqi.uilib.ui.scene.text.font.MathTextFontSources;
import java.util.List;
import club.heiqi.uilib.ui.scene.text.TextStyle;
import club.heiqi.uilib.ui.scene.text.FontStyle;
import club.heiqi.uilib.ui.scene.text.font.FontSource;

/** 工作区界面显式使用随包无衬线字族；不会追加到用户的 font.candidates。
 * 样本另用当前生效候选，因此空配置仍能操作界面且不会伪造字体覆盖。 */
final class SettingsTypography {
    private static final List<FontSource> UI = club.heiqi.uilib.ui.scene.text.font.UiFontSources.sans();
    private static final List<FontSource> PATH = pathSources();
    private SettingsTypography() {}
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
