package club.heiqi.qz_uilib;

import club.heiqi.qz_uilib.client.BaseGUI;
import club.heiqi.qz_uilib.network.PacketOpenGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

/**
 * 打开GUI的入点工具类
 */
public class GUIManager {

    /**
     * 通过服务端通知打开客户端的GUI
     */
    public static void openGUIByServer(EntityPlayerMP playerMP) {
        // 需要发送一个数据包告诉客户端应该打开GUI
        NBTTagCompound compound = new NBTTagCompound();
        compound.setInteger("windowID", 0);
        MyMod.proxy.networkMain.network.sendTo(new PacketOpenGUI(true, compound), playerMP);
    }

    /**
     * 客户端自己打开GUI
     */
    public static void openGUIByClient(BaseGUI gui) {
        Minecraft.getMinecraft().displayGuiScreen(gui);
    }

    public static BaseGUI getGUIByType(int windowID) {
        switch (windowID) {
            default -> { return new BaseGUI(); }
        }
    }
}
