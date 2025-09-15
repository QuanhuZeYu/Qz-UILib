package club.heiqi.qz_uilib.client;

import club.heiqi.qz_uilib.GUIManager;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import org.lwjgl.input.Keyboard;

public class KeyListener {

    @SubscribeEvent
    public void onInputEvent(InputEvent event) {
        if (Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)) {
            GUIManager.openGUIByClient(new BaseGUI());
        }
    }

    public void registrar() {
        FMLCommonHandler.instance().bus().register(this);
        MinecraftForge.EVENT_BUS.register(this);
    }
}
