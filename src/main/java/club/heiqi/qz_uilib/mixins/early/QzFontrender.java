package club.heiqi.qz_uilib.mixins.early;

import club.heiqi.qz_uilib.MyMod;
import club.heiqi.qz_uilib.fontsystem.FontManager;
import net.minecraft.client.gui.FontRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Random;

@Mixin(
    value = FontRenderer.class,
    priority = 5000
)
public abstract class QzFontrender {
    @Shadow
    public float posX;
    @Shadow
    public float posY;
    @Shadow
    public boolean randomStyle;
    @Shadow
    public boolean boldStyle;
    @Shadow
    public boolean strikethroughStyle;
    @Shadow
    public boolean underlineStyle;
    @Shadow
    private boolean italicStyle;
    @Shadow
    public int[] colorCode;
    @Shadow
    public int textColor;
    @Shadow
    public float alpha;
    @Shadow
    public float red;
    @Shadow
    public float blue;
    @Shadow
    public float green;
    @Shadow
    public Random fontRandom;
    @Shadow
    public int[] charWidth;
    @Shadow
    public boolean unicodeFlag;

    @Inject(
        method = "renderCharAtPos",
        at = @At("HEAD"),
        cancellable = true,
        remap = true
    )
    public void qzuilib$renderCharAtPos(int index, char c, boolean b, CallbackInfoReturnable<Float> ci) {
        String ch = Character.toString(c);
        FontManager fontManager = MyMod.fontManager;
        if (fontManager == null) return;
        if (fontManager.genDone){
            float f = fontManager.renderCharAt(ch, this.posX, this.posY, 0d, 8f);
            ci.setReturnValue(f);
            ci.cancel();
        }
    }

//    @Inject(
//        method = "renderStringAtPos",
//        at = @At("HEAD"),
//        cancellable = true,
//        remap = true
//    )
//    public void qzuilib$renderStringAtPos(String text, boolean isShadow, CallbackInfo ci) {
//        for (int charIndex = 0; charIndex < text.length(); ++charIndex) {
//            char currentChar = text.charAt(charIndex);
//            int formatCodeIndex; // 格式代码标识符（如颜色/样式代码的索引）
//            int mappedCharIndex; // 字符在字体映射表中的索引
//            // 处理格式代码（以 § 开头，例如 §a 表示绿色）
//            if (currentChar == 167 /* § 符号的ASCII值 */ && charIndex + 1 < text.length()) {
//                // 获取格式代码的类型（小写字母或符号在预设字符串中的位置）
//                formatCodeIndex = "0123456789abcdefklmnor".indexOf( // 总共21位 颜色代码16位 控制符代码5位
//                    Character.toLowerCase(text.charAt(charIndex + 1)));
//                // 处理颜色代码（0-9,a-f）
//                if (formatCodeIndex < 16) {
//                    this.randomStyle = false;
//                    this.boldStyle = false;
//                    this.strikethroughStyle = false;
//                    this.underlineStyle = false;
//                    this.italicStyle = false;
//
//                    // 非法颜色代码默认重置为白色（索引15）
//                    if (formatCodeIndex < 0 || formatCodeIndex > 15) {
//                        formatCodeIndex = 15;
//                    }
//                    // 阴影模式下使用暗色版本（索引+16）
//                    if (isShadow) {
//                        formatCodeIndex += 16;
//                    }
//
//                    int colorValue = this.colorCode[formatCodeIndex];
//                    this.textColor = colorValue;
//                    ((FontRenderer)((Object)this)).setColor(
//                        (float) (colorValue >> 16) / 255.0F, // R
//                        (float) (colorValue >> 8 & 255) / 255.0F, // G
//                        (float) (colorValue & 255) / 255.0F, // B
//                        this.alpha);
//                }
//                // 处理样式代码（k=随机，l=粗体，m=删除线等）
//                else if (formatCodeIndex == 16) {
//                    this.randomStyle = true;
//                } else if (formatCodeIndex == 17) {
//                    this.boldStyle = true;
//                } else if (formatCodeIndex == 18) {
//                    this.strikethroughStyle = true;
//                } else if (formatCodeIndex == 19) {
//                    this.underlineStyle = true;
//                } else if (formatCodeIndex == 20) {
//                    this.italicStyle = true;
//                }
//                // 重置所有样式（r）
//                else if (formatCodeIndex == 21) {
//                    this.randomStyle = false;
//                    this.boldStyle = false;
//                    this.strikethroughStyle = false;
//                    this.underlineStyle = false;
//                    this.italicStyle = false;
//                    ((FontRenderer)((Object)this)).setColor(this.red, this.blue, this.green, this.alpha);
//                }
//                ++charIndex; // 跳过已处理的格式代码字符（例如 § 后的字母）
//            }
//            // 处理普通字符渲染
//            else {
//                // 查找字符在字体映射表中的位置（支持特殊符号）
//                mappedCharIndex = ("ÀÁÂÈÊËÍÓÔÕÚßãõğİıŒœŞşŴŵžȇ\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"" +
//                    "#$%%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~" +
//                    "\u0000ÇüéâäàåçêëèïîìÄÅÉæÆôöòûùÿÖÜø£Ø×ƒáíóúñÑªº¿®¬½¼¡«»░▒▓│┤╡╢╖╕╣║╗╝╜╛┐└┴┬├─┼╞╟╚╔╩╦╠═╬╧╨╤╥╙╘╒╓╫╪┘" +
//                    "┌█▄▌▐▀αβΓπΣσμτΦΘΩδ∞∅∈∩≡±≥≤⌠⌡÷≈°∙·√ⁿ²■\u0000")
//                    .indexOf(currentChar);
//                // 随机样式：选择相同宽度的随机字符
//                if (this.randomStyle && mappedCharIndex != -1) {
//                    int randomCharIndex;
//                    do {
//                        randomCharIndex = this.fontRandom.nextInt(this.charWidth.length);
//                    } while (this.charWidth[mappedCharIndex] != this.charWidth[randomCharIndex]);
//                    mappedCharIndex = randomCharIndex;
//                }
//                // 调整阴影位置（Unicode字体缩小一半）
//                float scaleFactor = this.unicodeFlag ? 0.5F : 1.0F;
//                boolean isUnsupportedChar = (currentChar == 0 || mappedCharIndex == -1 || this.unicodeFlag) && isShadow;
//                if (isUnsupportedChar) {
//                    this.posX -= scaleFactor;
//                    this.posY -= scaleFactor;
//                }
//                // 渲染字符并获取宽度
//                float charWidth = ((FontRenderer)((Object)this)).renderCharAtPos(mappedCharIndex, currentChar, this.italicStyle);
//
//                // 还原阴影位置调整
//                if (isUnsupportedChar) {
//                    this.posX += scaleFactor;
//                    this.posY += scaleFactor;
//                }
//                // 粗体样式：向右重复渲染一次
//                if (this.boldStyle) {
//                    this.posX += scaleFactor;
//
//                    if (isUnsupportedChar) {
//                        this.posX -= scaleFactor;
//                        this.posY -= scaleFactor;
//                    }
//                    ((FontRenderer)((Object)this)).renderCharAtPos(mappedCharIndex, currentChar, this.italicStyle);
//                    this.posX -= scaleFactor;
//
//                    if (isUnsupportedChar) {
//                        this.posX += scaleFactor;
//                        this.posY += scaleFactor;
//                    }
//                    ++charWidth; // 粗体总宽度+1
//                }
//                ((FontRenderer)((Object)this)).doDraw(charWidth);
//            }
//        }
//        ci.cancel();
//    }
}
