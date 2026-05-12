package club.heiqi.uilib.internal.devtools;

import club.heiqi.uilib.CommonProxy;
import club.heiqi.uilib.client.FontRenderTickListener;
import club.heiqi.uilib.client.UiHudRenderListener;
import club.heiqi.uilib.client.UiInputTickListener;
import club.heiqi.uilib.ui.input.UiInputService;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;

/**
 * 客户端代理。
 */
public class ClientProxy extends CommonProxy {

    private final FontRenderTickListener fontRenderTickListener = new FontRenderTickListener();
    private final QzUiLibClientCommand qzUiLibClientCommand = new QzUiLibClientCommand();
    private final UiHudRenderListener uiHudRenderListener = new UiHudRenderListener();
    private final UiInputTickListener uiInputTickListener = new UiInputTickListener();

    /**
     * 客户端预初始化时注册字体渲染 Tick 监听。
     *
     * @param event Forge 预初始化事件
     */
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        UiInputService.getInstance().initialize();
        ClientCommandHandler.instance.registerCommand(qzUiLibClientCommand);
        MinecraftForge.EVENT_BUS.register(fontRenderTickListener);
        MinecraftForge.EVENT_BUS.register(uiHudRenderListener);
        FMLCommonHandler.instance().bus().register(fontRenderTickListener);
        FMLCommonHandler.instance().bus().register(uiInputTickListener);
    }
}
