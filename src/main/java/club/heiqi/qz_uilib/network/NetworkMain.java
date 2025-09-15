package club.heiqi.qz_uilib.network;

import club.heiqi.qz_uilib.MyMod;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;

public class NetworkMain {
    public final SimpleNetworkWrapper network = NetworkRegistry.INSTANCE.newSimpleChannel(MyMod.MODID);
    public int packetID = 0;

    public void registrar() {
        network.registerMessage(PacketOpenGUI.PacketOpenGUIHandler.class, PacketOpenGUI.class, packetID++, Side.CLIENT);
    }
}
