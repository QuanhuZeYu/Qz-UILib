package club.heiqi.uilib.client.hud;

/** 通用 HUD 窗口外壳的默认皮肤 token（字号、行盒与间距）。 */
final class HudTokens {
    static final int MAX_EMPHASIS_FONT_SIZE = 18;
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
}
