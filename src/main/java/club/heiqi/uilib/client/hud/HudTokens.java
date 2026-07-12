package club.heiqi.uilib.client.hud;

import club.heiqi.uilib.ui.hud.api.HudSpec;

/** 通用 HUD 的字号、行盒与间距 token。 */
final class HudTokens {
    static final HudTokens COMPACT = new HudTokens(10, 12, 14, 5, 4, 3);
    static final HudTokens NORMAL = new HudTokens(12, 14, 17, 7, 6, 4);
    static final int STACK_GAP = 4;

    final int fontSize;
    final int lineBox;
    final int lineHeight;
    final int paddingX;
    final int paddingY;
    final int progressHeight;

    private HudTokens(int fontSize, int lineBox, int lineHeight, int paddingX, int paddingY, int progressHeight) {
        this.fontSize = fontSize;
        this.lineBox = lineBox;
        this.lineHeight = lineHeight;
        this.paddingX = paddingX;
        this.paddingY = paddingY;
        this.progressHeight = progressHeight;
    }

    /** 返回规格对应的不可变 token 集。 */
    static HudTokens forSpec(HudSpec spec) {
        return spec.isCompact() ? COMPACT : NORMAL;
    }
}
