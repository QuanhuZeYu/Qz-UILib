package club.heiqi.uilib.mixin.early;

import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.internal.font.PlayerNameTagReplayQueue;
import club.heiqi.uilib.internal.font.PlayerNameTagReplayQueue.ReplayTarget;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 将启用全局字体替换时的普通玩家名称标签原方法延后到 world-last 回放。
 */
@Mixin(Render.class)
public abstract class MixinRenderPlayerNameTag implements ReplayTarget {

    /**
     * 只捕获玩家实体对原版完整名称标签方法的调用。
     *
     * @param entity 名称标签所属实体
     * @param text 标签文本
     * @param x 相对渲染坐标 X
     * @param y 相对渲染坐标 Y
     * @param z 相对渲染坐标 Z
     * @param maxDistance 最大绘制距离
     * @param ci Mixin 回调信息
     */
    @Inject(method = "func_147906_a", at = @At("HEAD"), cancellable = true)
    private void qzuilib$deferPlayerNameTag(Entity entity, String text,
            double x, double y, double z, int maxDistance, CallbackInfo ci) {
        if (!FontConfig.replaceOrigin || !(entity instanceof EntityPlayer)
                || PlayerNameTagReplayQueue.isReplaying()) {
            return;
        }
        if (PlayerNameTagReplayQueue.defer(this, entity, text, x, y, z, maxDistance)) {
            ci.cancel();
        }
    }

    /**
     * 暴露同一个原版完整名称标签方法供队列回放，不复制其几何或 GL 状态逻辑。
     */
    @Override
    @Invoker("func_147906_a")
    public abstract void qzuilib$invokeNameTag(Entity entity, String text,
            double x, double y, double z, int maxDistance);
}
