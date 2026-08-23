package club.heiqi.uilib.mixin.early.network;

import club.heiqi.uilib.internal.chat3.input.ChatInputScreen;
import club.heiqi.uilib.net.transport.vanilla.VanillaMixinTransport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.S3APacketTabComplete;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraft.util.IChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端 vanilla custom payload 入口注入。
 */
@Mixin(NetHandlerPlayClient.class)
public abstract class MixinNetHandlerPlayClient {

    /**
     * 连接建立后尽早通知 vanilla 适配器可发握手。
     *
     * @param minecraft Minecraft 客户端
     * @param serverScreen 连接来源界面
     * @param networkManager 网络管理器
     * @param ci 回调
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void qzuilib$onClientHandshakeReady(Minecraft minecraft, GuiScreen serverScreen,
            NetworkManager networkManager, CallbackInfo ci) {
        VanillaMixinTransport.onClientHandshakeReady(networkManager);
    }

    /**
     * 拦截 Qz 物理 channel，其它 channel 完全放行。
     *
     * @param packetIn custom payload 包
     * @param ci 回调
     */
    @Inject(method = "handleCustomPayload", at = @At("HEAD"), cancellable = true)
    private void qzuilib$handleQzCustomPayload(S3FPacketCustomPayload packetIn, CallbackInfo ci) {
        if (VanillaMixinTransport.onClientCustomPayload(packetIn.func_149169_c(), packetIn.func_149168_d())) {
            ci.cancel();
        }
    }

    /**
     * 客户端断连时通知网络层清理 pending 请求。
     *
     * @param reason 断连原因
     * @param ci 回调
     */
    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void qzuilib$onClientDisconnected(IChatComponent reason, CallbackInfo ci) {
        VanillaMixinTransport.onClientDisconnected(reason);
    }

    /**
     * 服务端命令补全响应转交聊天输入屏幕(原版只转交 GuiChat,我们的屏幕不是 GuiChat)。
     *
     * @param packetIn 补全响应包
     * @param ci 回调
     */
    @Inject(method = "handleTabComplete", at = @At("HEAD"))
    private void qzuilib$routeTabCompleteToChatInput(S3APacketTabComplete packetIn, CallbackInfo ci) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc != null && mc.currentScreen instanceof ChatInputScreen) {
            ((ChatInputScreen) mc.currentScreen).onAutocompleteResponse(packetIn.func_149630_c());
        }
    }
}
