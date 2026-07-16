package club.heiqi.uilib.ui.hud.api;

/** 更小内边距与行距的紧凑 HUD 预制入口。 */
public final class CompactHud {
    private CompactHud() {}
    /** 注册紧凑 HUD。 */
    public static HudRegistration register(String id, HudAnchor anchor, HudSnapshotProvider provider) {
        return ClientHudService.getInstance().register(HudSpec.builder(id).anchor(anchor).compact(true).build(), provider);
    }
}
