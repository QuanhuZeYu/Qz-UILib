package club.heiqi.uilib.internal.devtools;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraftforge.client.ClientCommandHandler;
/** 仅开发 classpath 中注册；场景不进入发布包。 */
public final class CoreLoopBootstrap {
    private static long metrics;
    private CoreLoopBootstrap() { }
    public static long metricsRevision() { return metrics; }
    public static void install() {
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(new IResourceManagerReloadListener() {
            @Override public void onResourceManagerReload(IResourceManager manager) { metrics++; }
        });
        ClientCommandHandler.instance.registerCommand(new CommandBase() {
            @Override public String getCommandName() { return "qzcore"; }
            @Override public String getCommandUsage(ICommandSender sender) { return "/qzcore"; }
            @Override public int getRequiredPermissionLevel() { return 0; }
            @Override public void processCommand(ICommandSender sender, String[] args) { Minecraft.getMinecraft().displayGuiScreen(new CoreLoopScreen()); }
        });
    }
}
