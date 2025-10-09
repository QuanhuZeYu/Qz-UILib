package club.heiqi.qz_uilib.network;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import java.io.IOException;

public class PacketOpenGUI implements IMessage {
    /**
     * {
     *     windowID: int
     * }
     */
    public boolean open = true;
    public NBTTagCompound compound = new NBTTagCompound();

    public PacketOpenGUI() {}
    public PacketOpenGUI(boolean open, NBTTagCompound compound) {
        this.open = open;
        this.compound = compound;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        open = buf.readBoolean();
        try (ByteBufInputStream is = new ByteBufInputStream(buf)) {
            compound = CompressedStreamTools.readCompressed(is);
        } catch (IOException e) {
            System.out.println("Open GUI Fail In Read Data");
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(open);
        try (ByteBufOutputStream os = new ByteBufOutputStream(buf);) {
            CompressedStreamTools.writeCompressed(compound, os);
        } catch (IOException e) {
            System.out.println("Open GUI Fail In Write Data");
        }
    }

    public static class PacketOpenGUIHandler implements IMessageHandler<PacketOpenGUI, IMessage> {

        @Override
        public IMessage onMessage(PacketOpenGUI message, MessageContext ctx) {
            if (ctx.side.isClient()) {
                int windowID = message.compound.getInteger("windowID");
            }
            return null;
        }
    }
}
