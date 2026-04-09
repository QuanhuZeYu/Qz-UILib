package club.heiqi.uilib.font.layout;

/**
 * Minecraft 格式码颜色表。
 */
public final class MinecraftColorTable {

    private static final int[] COLORS = new int[] {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
            0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
            0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
            0x000000, 0x00002A, 0x002A00, 0x002A2A,
            0x2A0000, 0x2A002A, 0x2A2A00, 0x2A2A2A,
            0x151515, 0x15153F, 0x153F15, 0x153F3F,
            0x3F1515, 0x3F153F, 0x3F3F15, 0x3F3F3F
    };

    private MinecraftColorTable() {}

    /**
     * 根据格式码获取颜色值。
     *
     * @param code 格式码
     * @param shadow 是否阴影色
     * @param alpha Alpha 通道
     * @return ARGB 颜色值
     */
    public static int getColor(char code, boolean shadow, int alpha) {
        int index = "0123456789abcdefklmnor".indexOf(code);
        if (index < 0) {
            return (alpha << 24) | 0xFFFFFF;
        }
        if (shadow) {
            index += 16;
        }
        return (alpha << 24) | COLORS[index];
    }
}
