package club.heiqi.uilib.host;
import java.lang.reflect.InvocationTargetException;
import net.minecraft.launchwrapper.Launch;
/** 开发场景采用已有整包排除规则，发布包不会携带场景或其命令。 */
public final class ClientProxy extends CommonProxy {
    @Override public void init() {
        if (!Boolean.TRUE.equals(Launch.blackboard.get("fml.deobfuscatedEnvironment"))) return;
        try { Class.forName("club.heiqi.uilib.internal.devtools.CoreLoopBootstrap").getMethod("install").invoke(null); }
        catch (ClassNotFoundException excludedDevelopmentTools) { /* 发布包没有开发工具。 */ }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("核心开发场景安装失败", e instanceof InvocationTargetException ? e.getCause() : e); }
    }
}
