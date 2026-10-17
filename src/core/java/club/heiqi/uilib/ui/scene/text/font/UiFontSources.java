package club.heiqi.uilib.ui.scene.text.font;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/** 随包界面无衬线字族的唯一来源；不会修改应用的 font.candidates。
 * 原始 Noto Sans SC 来自 google/fonts a85815a42757630ce188fdad368c2dfc444d4773，OFL 与资源一起发行。 */
public final class UiFontSources {
    private static final List<FontSource> SANS=load();
    private UiFontSources(){}
    public static List<FontSource> sans(){return SANS;}
    private static List<FontSource> load(){
        try{
            java.util.ArrayList<FontSource> sources=new java.util.ArrayList<>();
            sources.add(FontSource.resource("uilib-settings-ui",1,UiFontSources.class.getClassLoader(),
                    "assets/qz_uilib/fonts/ui/NotoSansSC-VF.ttf",false,Collections.<String>emptySet()));
            // 其他文字系统沿既有随包候选覆盖，不把局部UI字族误作应用默认配置。
            sources.addAll(FontTextService.defaultSources(UiFontSources.class.getClassLoader(),1));
            return Collections.unmodifiableList(sources);
        }catch(IOException failure){throw new ExceptionInInitializerError(failure);}
    }
}
