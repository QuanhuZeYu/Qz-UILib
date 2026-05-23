package club.heiqi.uilib.net.transport.forge;

import java.util.EnumMap;

import club.heiqi.uilib.net.api.NetService;
import club.heiqi.uilib.net.core.NetPayloadLimits;
import club.heiqi.uilib.net.transport.FrameHandler;
import club.heiqi.uilib.net.transport.ITransport;
import club.heiqi.uilib.net.transport.NetReceiveOrigin;
import club.heiqi.uilib.net.transport.NetSide;
import cpw.mods.fml.common.network.FMLEmbeddedChannel;
import cpw.mods.fml.common.network.FMLOutboundHandler;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;

/**
 * Forge/FML 兼容传输适配器。
 *
 * <p>该适配器作为排障与兼容退路保留；默认路径仍是 {@code VanillaMixinTransport}。</p>
 */
public final class ForgeTransport implements ITransport {

    private EnumMap<Side, FMLEmbeddedChannel> channels;

    @Override
    public String getName() {
        return "forge-fml";
    }

    @Override
    public void bootstrap(FrameHandler frameHandler) {
        channels = NetworkRegistry.INSTANCE.newChannel(NetService.PHYSICAL_CHANNEL, new InboundHandler(frameHandler));
    }

    @Override
    public void shutdown() {
        channels = null;
    }

    @Override
    public void sendToServer(String channelName, byte[] payload) {
        FMLProxyPacket packet = new FMLProxyPacket(Unpooled.wrappedBuffer(payload), channelName);
        packet.setTarget(Side.SERVER);
        FMLEmbeddedChannel channel = channels.get(Side.CLIENT);
        channel.attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.TOSERVER);
        channel.writeAndFlush(packet);
    }

    @Override
    public void sendToPlayer(Object player, String channelName, byte[] payload) {
        FMLProxyPacket packet = new FMLProxyPacket(Unpooled.wrappedBuffer(payload), channelName);
        packet.setTarget(Side.CLIENT);
        FMLEmbeddedChannel channel = channels.get(Side.SERVER);
        channel.attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.PLAYER);
        channel.attr(FMLOutboundHandler.FML_MESSAGETARGETARGS).set(player);
        channel.writeAndFlush(packet);
    }

    @Override
    public void sendToAll(String channelName, byte[] payload) {
        FMLProxyPacket packet = new FMLProxyPacket(Unpooled.wrappedBuffer(payload), channelName);
        packet.setTarget(Side.CLIENT);
        FMLEmbeddedChannel channel = channels.get(Side.SERVER);
        channel.attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.ALL);
        channel.writeAndFlush(packet);
    }

    @Override
    public void sendToDimension(int dimensionId, String channelName, byte[] payload) {
        FMLProxyPacket packet = new FMLProxyPacket(Unpooled.wrappedBuffer(payload), channelName);
        packet.setTarget(Side.CLIENT);
        FMLEmbeddedChannel channel = channels.get(Side.SERVER);
        channel.attr(FMLOutboundHandler.FML_MESSAGETARGET).set(FMLOutboundHandler.OutboundTarget.DIMENSION);
        channel.attr(FMLOutboundHandler.FML_MESSAGETARGETARGS).set(Integer.valueOf(dimensionId));
        channel.writeAndFlush(packet);
    }

    @Override
    public int getPhysicalFrameLimit(NetSide targetSide) {
        return targetSide == NetSide.SERVER
                ? NetPayloadLimits.COMPAT_PHYSICAL_FRAME_LIMIT
                : NetPayloadLimits.GTNH_DEFAULT_PHYSICAL_LIMIT;
    }

    @ChannelHandler.Sharable
    private static final class InboundHandler extends SimpleChannelInboundHandler<FMLProxyPacket> {

        private final FrameHandler frameHandler;

        InboundHandler(FrameHandler frameHandler) {
            this.frameHandler = frameHandler;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FMLProxyPacket packet) {
            byte[] payload = new byte[packet.payload().readableBytes()];
            packet.payload().getBytes(packet.payload().readerIndex(), payload);
            if (packet.getTarget() == Side.SERVER) {
                frameHandler.handleFrame(packet.channel(), payload, NetReceiveOrigin.server(resolveSender(packet)));
            } else {
                frameHandler.handleFrame(packet.channel(), payload, NetReceiveOrigin.client());
            }
        }

        private static Object resolveSender(FMLProxyPacket packet) {
            if (packet.handler() instanceof NetHandlerPlayServer) {
                return ((NetHandlerPlayServer) packet.handler()).playerEntity;
            }
            if (packet.handler() instanceof EntityPlayerMP) {
                return packet.handler();
            }
            return null;
        }
    }
}
