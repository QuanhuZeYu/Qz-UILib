package club.heiqi.uilib.mixin.early.network;

import club.heiqi.uilib.net.transport.vanilla.VanillaMixinTransport;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 服务端 vanilla custom payload 入口注入。
 */
@Mixin(NetHandlerPlayServer.class)
public abstract class MixinNetHandlerPlayServer {

    /**
     * 玩家 NetHandler 建立后通知网络层记录 Play handler 就绪。
     *
     * @param server Minecraft 服务端
     * @param networkManager 网络管理器
     * @param player 玩家
     * @param ci 回调
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void qzuilib$onServerPlayHandlerReady(MinecraftServer server, NetworkManager networkManager,
            EntityPlayerMP player, CallbackInfo ci) {
        VanillaMixinTransport.onServerPlayHandlerReady(networkManager, player);
    }

    /**
     * 只拦截 Qz 物理 channel，其它 custom payload 交回 vanilla/Forge 原逻辑。
     *
     * @param packetIn custom payload 包
     * @param ci 回调
     */
    @Inject(method = "processVanilla250Packet", at = @At("HEAD"), cancellable = true)
    private void qzuilib$handleQzCustomPayload(C17PacketCustomPayload packetIn, CallbackInfo ci) {
        EntityPlayerMP player = ((NetHandlerPlayServer) (Object) this).playerEntity;
        if (VanillaMixinTransport.onServerCustomPayload(player, packetIn.func_149559_c(), packetIn.func_149558_e())) {
            ci.cancel();
        }
    }

    /**
     * 玩家离开时通知网络层清理临时状态。
     *
     * @param reason 断连原因
     * @param ci 回调
     */
    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void qzuilib$onServerPlayerLeft(IChatComponent reason, CallbackInfo ci) {
        EntityPlayerMP player = ((NetHandlerPlayServer) (Object) this).playerEntity;
        VanillaMixinTransport.onServerPlayerLeft(player);
    }
}
