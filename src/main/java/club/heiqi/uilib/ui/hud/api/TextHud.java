package club.heiqi.uilib.ui.hud.api;

/** 常规文本 HUD 预制入口。 */
public final class TextHud {
    private TextHud() {}
    /** 用默认主题和间距注册文本 HUD。 */
    public static HudRegistration register(String id, HudAnchor anchor, HudSnapshotProvider provider) {
        return ClientHudService.getInstance().register(HudSpec.builder(id).anchor(anchor).build(), provider);
    }
}
