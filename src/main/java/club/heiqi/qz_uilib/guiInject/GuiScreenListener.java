package club.heiqi.qz_uilib.guiInject;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import org.jetbrains.annotations.Nullable;

public class GuiScreenListener {

    public GuiScreenListener() {

    }


    public boolean showedDebug = false;
    @SubscribeEvent
    public void ClientRenderTick(TickEvent.RenderTickEvent event) {
        if (showedDebug) showedDebug = false;
    }

    public void _registerClient(
        @Nullable FMLPreInitializationEvent pre,
        @Nullable FMLInitializationEvent init,
        @Nullable FMLPostInitializationEvent post) {
        if (pre != null) {
            FMLCommonHandler.instance().bus().register(this);
        }
        if (init != null) {
        }
        if (post != null) {
        }
    }
    public void _registerCommon(
        @Nullable FMLPreInitializationEvent pre,
        @Nullable FMLInitializationEvent init,
        @Nullable FMLPostInitializationEvent post) {
        if (pre != null) {
        }
    }
}
