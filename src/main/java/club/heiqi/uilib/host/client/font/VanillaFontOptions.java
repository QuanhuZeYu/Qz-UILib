package club.heiqi.uilib.host.client.font;

import java.io.File;
import cpw.mods.fml.client.event.ConfigChangedEvent;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.common.config.Configuration;

/** 原版替代的显式开关；关闭只退出标准入口，scene始终使用共享自有引擎。
 * 安装前的FML启动字体尚不接管；安装成功后的运行错误直接报告，不暗中切回原版。 */
public final class VanillaFontOptions {
    private static final VanillaFontOptions LISTENER=new VanillaFontOptions();
    private static Configuration configuration;
    private static boolean enabled=true;
    private static long revision;
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
        if(configuration.hasChanged())configuration.save();
        revision++;
    }
    public static boolean enabled() { return enabled; }
    public static long revision() { return revision; }
    @SubscribeEvent public void changed(ConfigChangedEvent.OnConfigChangedEvent event) {
        if("qz_uilib".equals(event.modID))reload();
    }
}
