package club.heiqi.uilib.internal.chat3.viewmodel;

/**
 * 聊天 3.0 发送者名配色(纯函数):名字哈希映射固定 7 色板(Telegram 同款思路),
 * 自己的名字恒为主题蓝。
 */
public final class SenderColorPalette {

    /** 自己的发送者名颜色(设计稿 §2.1 text-name-self,比正文暗 20% 不抢眼)。 */
    public static final int SELF_NAME_ARGB = 0xFFAAB3BC;

    /** 7 色板(设计稿 §2.1 name-1..7:柔红/橙/紫/绿/青/蓝/粉,暗底调优整体提亮降饱和)。 */
    private static final int[] PALETTE = {
        0xFFFF6B64, 0xFFFF9E57, 0xFFC07BF8, 0xFF6BCB77, 0xFF4DD0E1, 0xFF6FA8FF, 0xFFF06292
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
