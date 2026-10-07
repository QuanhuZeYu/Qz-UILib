package club.heiqi.uilib.host.client.font;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.common.config.Configuration;

/** 原版替代的显式开关；关闭只退出标准入口，scene始终使用共享自有引擎。
 * 安装前的FML启动字体尚不接管；安装成功后的运行错误直接报告，不暗中切回原版。 */
public final class VanillaFontOptions {
    private static final VanillaFontOptions LISTENER=new VanillaFontOptions();
    private static Configuration configuration;
    private static volatile boolean enabled=true;
    private static volatile List<String> systemFontFiles=Collections.emptyList();
    private static volatile long revision;
    private VanillaFontOptions() {}
    public static void load(File file) {
        if(configuration!=null)throw new IllegalStateException("字体配置重复安装");
        configuration=new Configuration(file);
        reload();
        FMLCommonHandler.instance().bus().register(LISTENER);
    }
    private static void reload() {
        configuration.load();
        enabled=configuration.getBoolean("replaceVanillaFont","font",true,
            "默认接管标准FontRenderer的绘制、测量和换行。关闭不影响UILib自己的页面字体。");
        String[] paths=configuration.getStringList("systemFontFiles","font",new String[0],
            "按顺序优先尝试的字体文件路径（支持Windows路径）。每个文件使用faceIndex 0；空列表使用内置候选。"
            +"系统文件不声明复杂脚本或彩色emoji能力，相关文字继续按同一列表尝试内置字体。");
        systemFontFiles=Collections.unmodifiableList(Arrays.asList(paths.clone()));
        if(configuration.hasChanged())configuration.save();
        revision++;
        MinecraftFontServices.configurationChanged();
    }
    public static boolean enabled() { return enabled; }
    public static long revision() { return revision; }
    /** 不可变有序配置快照；不枚举系统目录，也不写入本机默认字体。 */
    public static List<String> systemFontFiles() { return systemFontFiles; }
    @SubscribeEvent public void changed(ConfigChangedEvent.OnConfigChangedEvent event) {
        if("qz_uilib".equals(event.modID))reload();
    }
}
