package club.heiqi.uilib.client.hud;

import club.heiqi.uilib.ui.hud.api.HudSpec;

/** 通用 HUD 的字号、行盒与间距 token。 */
final class HudTokens {
    static final int MAX_EMPHASIS_FONT_SIZE = 18;
    static final HudTokens COMPACT = new HudTokens(12, 14, 16, 5, 4, 3, 24);
    static final HudTokens NORMAL = new HudTokens(14, 16, 19, 7, 6, 4, 32);
    static final int STACK_GAP = 4;

    final int fontSize;
    final int lineBox;
    final int lineHeight;
    final int paddingX;
    final int paddingY;
    final int progressHeight;
    final int minWidth;

    private HudTokens(int fontSize, int lineBox, int lineHeight, int paddingX, int paddingY, int progressHeight,
            int minWidth) {
        this.fontSize = fontSize;
        this.lineBox = lineBox;
        this.lineHeight = lineHeight;
        this.paddingX = paddingX;
        this.paddingY = paddingY;
        this.progressHeight = progressHeight;
        this.minWidth = minWidth;
    }

    /** 返回规格对应的不可变 token 集。 */
    static HudTokens forSpec(HudSpec spec) {
        return spec.isCompact() ? COMPACT : NORMAL;
    }
}
