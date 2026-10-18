package club.heiqi.uilib.internal.devtools;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraftforge.client.ClientCommandHandler;

/** 仅开发 classpath 中注册；场景不进入发布包。 */
public final class CoreLoopBootstrap {
    private static boolean installed, pendingOpen, pendingPerformance;
    private static club.heiqi.uilib.testing.gallery.TestGallery.Page pendingPage=club.heiqi.uilib.testing.gallery.TestGallery.Page.HOME;

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !pendingOpen) return;
        pendingOpen = false;
        Minecraft.getMinecraft().displayGuiScreen(pendingPerformance ? new PerformanceScreen()
                : new CoreLoopScreen(pendingPage));
        pendingPerformance = false;
    }

    /** 测试附加包提供主菜单入口；打开真实广场无需进入或改动世界。 */
    @SubscribeEvent(priority = cpw.mods.fml.common.eventhandler.EventPriority.LOWEST)
    @SuppressWarnings("unchecked")
    public void menu(net.minecraftforge.client.event.GuiScreenEvent.InitGuiEvent.Post event) {
        net.minecraft.client.gui.GuiScreen gui = event.gui;
        java.util.List<net.minecraft.client.gui.GuiButton> buttons = event.buttonList;
        // CMM 的高优先级钩子把 Post 的 gui/buttonList 换成伪主菜单，并在末尾移除普通附加按钮。
        // 在它完成包装后，对当前真实主菜单公开的按钮列表附加入口；不修改整合包菜单配置。
        if ("lumien.custommainmenu.gui.GuiFakeMain".equals(gui.getClass().getName())) {
            gui = Minecraft.getMinecraft().currentScreen;
            if (gui == null || !isMainMenu(gui)) return;
            try {
                buttons = (java.util.List<net.minecraft.client.gui.GuiButton>) gui.getClass().getMethod("getButtonList").invoke(gui);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("不能读取 CustomMainMenu 的实际按钮列表", failure);
            }
        }
        if (isMainMenu(gui)) buttons.add(new GalleryButton(gui.width - 110, 4));
    }

    private static boolean isMainMenu(net.minecraft.client.gui.GuiScreen gui) {
        if (gui instanceof net.minecraft.client.gui.GuiMainMenu) return true;
        // GTNH 的 CustomMainMenu 替换了 GuiMainMenu；其标准按钮列表仍由 Forge 事件分发。
        // 只识别本次实际主菜单配置，不把广场入口插到该模组的其他自定义页面。
        if (!"lumien.custommainmenu.gui.GuiCustom".equals(gui.getClass().getName())) return false;
        try {
            Object config = gui.getClass().getField("guiConfig").get(gui);
            return "mainmenu".equals(config.getClass().getField("name").get(config));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("不能读取 CustomMainMenu 的主菜单配置", failure);
        }
    }

    @SubscribeEvent
    public void open(net.minecraftforge.client.event.GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (!(event.button instanceof GalleryButton)) return;
        event.setCanceled(true);
        pendingPage = club.heiqi.uilib.testing.gallery.TestGallery.Page.HOME;
        pendingPerformance = false;
        pendingOpen = true;
    }

    private static final class GalleryButton extends net.minecraft.client.gui.GuiButton {
        GalleryButton(int x, int y) { super(0x515a, x, y, 106, 20, "UILib 测试广场"); }
    }

    private CoreLoopBootstrap() {}

    public static void install() {
        if (installed) return;
        installed = true;
        CoreLoopBootstrap hooks = new CoreLoopBootstrap();
        FMLCommonHandler.instance().bus().register(hooks);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(hooks);
        ClientCommandHandler.instance.registerCommand(
                new CommandBase() {
                    @Override
                    public String getCommandName() {
                        return "qzcore";
                    }

                    @Override
                    public String getCommandUsage(ICommandSender sender) {
                        return "/qzcore [mailbox]";
                    }

                    @Override
                    public int getRequiredPermissionLevel() {
                        return 0;
                    }

                    @Override
                    public void processCommand(
                            ICommandSender sender,
                            String[] args) { // GuiChat 在命令返回后会关闭当前界面，必须等聊天输入收尾后再打开。
                        pendingPage=args.length>0&&"mailbox".equalsIgnoreCase(args[0])
                                ?club.heiqi.uilib.testing.gallery.TestGallery.Page.MAILBOX:club.heiqi.uilib.testing.gallery.TestGallery.Page.HOME;
                        pendingPerformance = false;
                        pendingOpen = true;
                    }
                });
        ClientCommandHandler.instance.registerCommand(new CommandBase() {
            @Override public String getCommandName() { return "qzperf"; }
            @Override public String getCommandUsage(ICommandSender sender) { return "/qzperf"; }
            @Override public int getRequiredPermissionLevel() { return 0; }
            @Override public void processCommand(ICommandSender sender, String[] args) {
                pendingPerformance = true; pendingOpen = true;
            }
        });
    }
}
