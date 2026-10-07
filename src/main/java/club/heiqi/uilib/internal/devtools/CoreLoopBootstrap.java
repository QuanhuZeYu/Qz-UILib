package club.heiqi.uilib.internal.devtools;
import net.minecraft.client.Minecraft;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraftforge.client.ClientCommandHandler;
/** 仅开发 classpath 中注册；场景不进入发布包。 */
public final class CoreLoopBootstrap {
    private static long metrics;
    private static boolean installed, pendingOpen;
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !pendingOpen) return;
        pendingOpen = false;
        Minecraft.getMinecraft().displayGuiScreen(new CoreLoopScreen());
    }
    private CoreLoopBootstrap() { }
    public static long metricsRevision() { return metrics; }
    public static void install() {
        if (installed) return;
        installed = true;
        FMLCommonHandler.instance().bus().register(new CoreLoopBootstrap());
        ((IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager()).registerReloadListener(new IResourceManagerReloadListener() {
            @Override public void onResourceManagerReload(IResourceManager manager) { metrics++; }
        });
        ClientCommandHandler.instance.registerCommand(new CommandBase() {
            @Override public String getCommandName() { return "qzcore"; }
            @Override public String getCommandUsage(ICommandSender sender) { return "/qzcore"; }
            @Override public int getRequiredPermissionLevel() { return 0; }
            @Override public void processCommand(ICommandSender sender, String[] args) { // GuiChat 在命令返回后会关闭当前界面，必须等聊天输入收尾后再打开。
                pendingOpen = true; }
        });
    }
}
