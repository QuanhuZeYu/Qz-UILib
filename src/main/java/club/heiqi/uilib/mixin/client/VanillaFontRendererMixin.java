package club.heiqi.uilib.mixin.client;

import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import club.heiqi.uilib.host.client.font.FontRendererAccess;
import club.heiqi.uilib.host.client.font.MinecraftVanillaFont;

/** 标准入口仅转交UILib同一定位/资源/backend，无scene节点。
 * HEAD注入在后应用的Mixin中前插；低于Angelica的1000使本入口最后安装、最先判断开关。
 * 不凭priority数字断言接管顺序，以正式包的真实变换字节码验收。 */
@Mixin(value = FontRenderer.class, priority = 500)
public abstract class VanillaFontRendererMixin implements FontRendererAccess {
    @Shadow public int FONT_HEIGHT;
    @Shadow private boolean unicodeFlag;
    @Shadow private boolean bidiFlag;
    @Shadow private int[] colorCode;

    @Shadow(remap = false)
    protected abstract void enableAlpha();

    @Shadow(remap = false)
    protected abstract void setColor(float r, float g, float b, float a);

    @Override
    public int qz$fontHeight() {
        return FONT_HEIGHT;
    }

    @Override
    public boolean qz$unicode() {
        return unicodeFlag;
    }

    @Override
    public boolean qz$bidi() {
        return bidiFlag;
    }

    @Override
    public int[] qz$palette() {
        return colorCode;
    }

    // 此处是标准入口可观察尾状态，沿宿主FontRenderer的有效GL hooks同步缓存。
    @Override
    public void qz$enableAlpha() {
        enableAlpha();
    }

    @Override
    public void qz$tailState(int color) {
        enableAlpha();
        setColor(
                (color >>> 16 & 255) / 255f,
                (color >>> 8 & 255) / 255f,
                (color & 255) / 255f,
                (color >>> 24) / 255f);
    }

    @Inject(method = "drawString(Ljava/lang/String;IIIZ)I", at = @At("HEAD"), cancellable = true)
    private void qz$draw(
            String text,
            int x,
            int y,
            int color,
            boolean shadow,
            CallbackInfoReturnable<Integer> cir) {
        if (MinecraftVanillaFont.active())
            cir.setReturnValue(MinecraftVanillaFont.draw(this, text, x, y, color, shadow));
    }

    @Inject(method = "getStringWidth", at = @At("HEAD"), cancellable = true)
    private void qz$width(String text, CallbackInfoReturnable<Integer> cir) {
        if (MinecraftVanillaFont.active())
            cir.setReturnValue(MinecraftVanillaFont.width(this, text));
    }

    @Inject(method = "getCharWidth", at = @At("HEAD"), cancellable = true)
    private void qz$charWidth(char c, CallbackInfoReturnable<Integer> cir) {
        if (MinecraftVanillaFont.active())
            cir.setReturnValue(MinecraftVanillaFont.charWidth(this, c));
    }

    @Inject(
            method = "trimStringToWidth(Ljava/lang/String;IZ)Ljava/lang/String;",
            at = @At("HEAD"),
            cancellable = true)
    private void qz$trim(
            String text, int width, boolean reverse, CallbackInfoReturnable<String> cir) {
        if (MinecraftVanillaFont.active())
            cir.setReturnValue(MinecraftVanillaFont.trim(this, text, width, reverse));
    }

    @Inject(method = "listFormattedStringToWidth", at = @At("HEAD"), cancellable = true)
    private void qz$list(String text, int width, CallbackInfoReturnable<List<String>> cir) {
        if (MinecraftVanillaFont.active())
            cir.setReturnValue(MinecraftVanillaFont.list(this, text, width));
    }

    @Inject(method = "wrapFormattedStringToWidth", at = @At("HEAD"), cancellable = true)
    private void qz$wrap(String text, int width, CallbackInfoReturnable<String> cir) {
        if (MinecraftVanillaFont.active())
            cir.setReturnValue(MinecraftVanillaFont.wrap(this, text, width));
    }

    @Inject(method = "sizeStringToWidth", at = @At("HEAD"), cancellable = true)
    private void qz$size(String text, int width, CallbackInfoReturnable<Integer> cir) {
        if (MinecraftVanillaFont.active())
            cir.setReturnValue(MinecraftVanillaFont.sizeToWidth(this, text, width));
    }

    @Inject(method = "splitStringWidth", at = @At("HEAD"), cancellable = true)
    private void qz$splitHeight(String text, int width, CallbackInfoReturnable<Integer> cir) {
        if (MinecraftVanillaFont.active())
            cir.setReturnValue(MinecraftVanillaFont.splitHeight(this, text, width));
    }

    @Inject(method = "drawSplitString", at = @At("HEAD"), cancellable = true)
    private void qz$split(String text, int x, int y, int width, int color, CallbackInfo ci) {
        if (MinecraftVanillaFont.active()) {
            MinecraftVanillaFont.drawSplit(this, text, x, y, width, color);
            ci.cancel();
        }
    }

    @Inject(method = "bidiReorder", at = @At("HEAD"), cancellable = true)
    private void qz$bidiText(String text, CallbackInfoReturnable<String> cir) {
        if (MinecraftVanillaFont.active())
            cir.setReturnValue(MinecraftVanillaFont.bidiString(this, text));
    }
}
