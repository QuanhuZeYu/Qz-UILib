package club.heiqi.uilib.host;
import java.lang.reflect.InvocationTargetException;
import net.minecraft.launchwrapper.Launch;
/** 生产client先安装共享字体owner；开发场景仍采用整包排除规则，不进入发布包。 */
public final class ClientProxy extends CommonProxy {
    @Override public void preInit(cpw.mods.fml.common.event.FMLPreInitializationEvent event) {
        club.heiqi.uilib.host.client.font.VanillaFontOptions.load(event.getSuggestedConfigurationFile());
    }
    @Override public void init() {
        club.heiqi.uilib.host.client.font.MinecraftFontServices.installShared();
        club.heiqi.uilib.host.client.font.MinecraftVanillaFont.install();
        club.heiqi.uilib.host.client.image.MinecraftImageServices.installShared();
        if (!Boolean.TRUE.equals(Launch.blackboard.get("fml.deobfuscatedEnvironment"))) return;
        try { Class.forName("club.heiqi.uilib.internal.devtools.CoreLoopBootstrap").getMethod("install").invoke(null); }
        catch (ClassNotFoundException excludedDevelopmentTools) { /* 发布包没有开发工具。 */ }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("核心开发场景安装失败", e instanceof InvocationTargetException ? e.getCause() : e); }
    }
}
