package club.heiqi.uilib.ui.hud.api;

/**
 * 在客户端 render 主线程读取 HUD 的无副作用快照。
 * 实现不得写 signal、访问 Forge 事件或返回后继续修改快照。
 */
@FunctionalInterface
public interface HudSnapshotProvider {
    /** 返回当前不可变内容；返回 null 等价于空快照。 */
    HudSnapshot snapshot();
}
