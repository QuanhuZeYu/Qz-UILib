package club.heiqi.uilib.font.layout;

/**
 * 原版格式色表。
 */
public final class MinecraftColorTable {

    private static final char[] COLOR_CODES = "0123456789abcdef".toCharArray();

    private MinecraftColorTable() {}

    public static int getColor(char code, boolean shadow, int alpha) {
        int index = "0123456789abcdef".indexOf(Character.toLowerCase(code));
        if (index < 0) {
            return alpha << 24;
        }

        int offset = (index >> 3 & 1) * 85;
        int red = (index >> 2 & 1) * 170 + offset;
        int green = (index >> 1 & 1) * 170 + offset;
        int blue = (index & 1) * 170 + offset;
        if (index == 6) {
            red += 85;
        }
        if (shadow) {
            red /= 4;
            green /= 4;
            blue /= 4;
        }
        return alpha << 24 | red << 16 | green << 8 | blue;
    }

    public static char findCodeByColor(int color, int alpha) {
        int normalizedColor = color & 0x00FFFFFF;
        for (char code : COLOR_CODES) {
            int candidate = getColor(code, false, alpha) & 0x00FFFFFF;
            if (candidate == normalizedColor) {
                return code;
            }
        }
        return 0;
    }
}
