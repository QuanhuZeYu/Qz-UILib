package club.heiqi.uilib.host.client.image;

import java.util.Objects;
import club.heiqi.uilib.host.text.MinecraftFormat;
import club.heiqi.uilib.ui.scene.text.TextContent;
import club.heiqi.uilib.ui.scene.text.TextInput;
import club.heiqi.uilib.ui.scene.text.TextEffects;
import club.heiqi.uilib.ui.scene.text.TextDecoration;

/** 取得物品事实时将 tooltip 转为中性内容，主题变化复用解析结果进行 resolve。
 * 混淆意图交给正式属性布局，沿其 UNSUPPORTED 诊断与原正文显示处理。
 */
public final class MinecraftTooltipContent {
    private MinecraftTooltipContent() {}

    public static TextContent fromMinecraft(String raw, int[] palette) {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(palette, "palette");
        if (palette.length < 16) throw new IllegalArgumentException("物品提示palette不足");
        return TextContent.styled(
                MinecraftFormat.styled(
                        TextInput.string(raw),
                        raw,
                        new MinecraftFormat.Context() {
                            @Override
                            public int paletteColor(int index) {
                                return palette[index];
                            }

                            @Override
                            public TextEffects effects(
                                    boolean bold,
                                    boolean italic,
                                    boolean underline,
                                    boolean strike,
                                    boolean obfuscated) {
                                return new TextEffects(
                                        bold, italic, underline, strike, obfuscated, 0, 0);
                            }

                            @Override
                            public TextDecoration decoration() {
                                return TextDecoration.NONE;
                            }
                        }));
    }
}
