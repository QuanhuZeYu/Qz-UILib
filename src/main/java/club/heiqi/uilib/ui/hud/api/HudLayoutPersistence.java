package club.heiqi.uilib.ui.hud.api;

/**
 * HUD 布局持久化窄入口（下游宿主接线面）。
 *
 * <p>下游 Mod 的架构守卫通常禁止生产代码引用 {@link HudLayoutService}/{@link HudLayoutResolver}
 * 等布局类；本类把「安装/卸载持久化端口」收成三个静态方法，宿主只实现
 * {@link HudLayoutStore}（纯文本 IO）并从客户端初始化点调用 {@link #install(HudLayoutStore)} 即可，
 * 不接触任何布局数学、坐标或缩放类型。</p>
 *
 * <p>契约：安装即加载（语义见 {@link HudLayoutService#attachStore(HudLayoutStore)}）；重复安装
 * 以最后一次为准；{@link #uninstall()} 幂等且保留内存布局。</p>
 */
public final class HudLayoutPersistence {

    private HudLayoutPersistence() {
    }

    /**
     * 安装持久化端口并立即加载持久记录（限客户端主线程）。
     *
     * @param store 宿主端口实现（不可为 null）
     */
    public static void install(HudLayoutStore store) {
        HudLayoutService.getInstance().attachStore(store);
    }

    /** 卸载持久化端口（幂等；内存布局保留）。 */
    public static void uninstall() {
        HudLayoutService.getInstance().detachStore();
    }

    /** @return 当前是否已安装持久化端口 */
    public static boolean isInstalled() {
        return HudLayoutService.getInstance().hasStore();
    }
}
