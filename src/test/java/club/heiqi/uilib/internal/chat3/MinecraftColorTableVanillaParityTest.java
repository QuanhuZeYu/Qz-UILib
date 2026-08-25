package club.heiqi.uilib.internal.chat3;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.layout.MinecraftColorTable;

/**
 * § 颜色码表与 vanilla 完全一致契约(K3 三轮评审任务 D):
 * §2 DARK_GREEN = (0,170,0) = 0x00AA00,与 Minecraft 1.7.10 EnumChatFormatting 同值。
 *
 * <p>真机采样 (6,194,8)/(0,242,0) 属 AA 边缘/阴影混色偏差,色表本身无偏差。</p>
 */
public class MinecraftColorTableVanillaParityTest {

    /** vanilla 16 色表(Minecraft EnumChatFormatting,RGB)。 */
    private static final int[] VANILLA = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    @Test
    public void sectionSignColorTableMatchesVanillaExactly() {
        String codes = "0123456789abcdef";
        for (int i = 0; i < codes.length(); i++) {
            int color = MinecraftColorTable.getColor(codes.charAt(i), false, 0xFF) & 0x00FFFFFF;
            Assert.assertEquals("\u00a7" + codes.charAt(i) + " 必须与 vanilla 同值", VANILLA[i], color);
        }
    }

    @Test
    public void darkGreenSectionTwoIsVanillaGreen() {
        Assert.assertEquals("§2 DARK_GREEN = vanilla (0,170,0)", 0x00AA00,
                MinecraftColorTable.getColor('2', false, 0xFF) & 0x00FFFFFF);
        Assert.assertEquals("§2 红色通道 0", 0, (MinecraftColorTable.getColor('2', false, 0xFF) >> 16) & 0xFF);
        Assert.assertEquals("§2 绿色通道 170", 170,
                (MinecraftColorTable.getColor('2', false, 0xFF) >> 8) & 0xFF);
        Assert.assertEquals("§2 蓝色通道 0", 0, MinecraftColorTable.getColor('2', false, 0xFF) & 0xFF);
    }
}
