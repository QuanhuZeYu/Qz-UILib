package club.heiqi.uilib.client.hud;

/** 通用 HUD 窗口外壳的默认皮肤 token（内边距与最小宽）。 */
final class HudTokens {
    static final HudTokens NORMAL = new HudTokens(7, 6, 32);
    static final int STACK_GAP = 4;

    final int paddingX;
    final int paddingY;
    final int minWidth;

    private HudTokens(int paddingX, int paddingY, int minWidth) {
        this.paddingX = paddingX;
        this.paddingY = paddingY;
        this.minWidth = minWidth;
    }
}
