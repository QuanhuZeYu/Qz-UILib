package club.heiqi.uilib.client.hud;

/**
 * HUD 宿主层的放置令牌：默认最小宽与同锚点堆叠间隙。
 *
 * <p><b>外壳皮肤不在这里</b>：内边距与底色是「窗口外壳」的事实，已收敛到
 * {@link club.heiqi.uilib.ui.scene.host.SceneHostWindow.Shell#HUD_DEFAULT}
 * （ui 层唯一一份，客户端宿主与 headless 出图页共用，避免两处各写一份内边距）。
 * 本类只保留「放置 / 堆叠」这一层的事实。</p>
 */
final class HudTokens {
    /** 未显式指定 minWidth 时的默认最小宽（px）。 */
    static final int MIN_WIDTH = 32;
    /** 同锚点相邻窗口的堆叠间隙（px）。 */
    static final int STACK_GAP = 4;

    private HudTokens() {
    }
}
