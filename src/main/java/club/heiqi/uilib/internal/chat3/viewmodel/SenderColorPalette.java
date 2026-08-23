package club.heiqi.uilib.internal.chat3.viewmodel;

/**
 * 聊天 3.0 发送者名配色(纯函数):名字哈希映射固定 7 色板(Telegram 同款思路),
 * 自己的名字恒为主题蓝。
 */
public final class SenderColorPalette {

    /** 自己的发送者名颜色(主题蓝,Telegram #3390EC)。 */
    public static final int SELF_NAME_ARGB = 0xFF3390EC;

    /** 7 色板(红/橙/紫/绿/青/蓝/粉,Telegram 同款思路)。 */
    private static final int[] PALETTE = {
        0xFFE53935, 0xFFF4511E, 0xFF8E24AA, 0xFF43A047, 0xFF00ACC1, 0xFF1E88E5, 0xFFD81B60
    };

    private SenderColorPalette() {
    }

    /**
     * @param name 发送者名(空/空串 → 白色)
     * @return 名字颜色(ARGB,哈希色板)
     */
    public static int colorFor(String name) {
        if (name == null || name.isEmpty()) {
            return 0xFFFFFFFF;
        }
        return PALETTE[Math.floorMod(name.hashCode(), PALETTE.length)];
    }
}
