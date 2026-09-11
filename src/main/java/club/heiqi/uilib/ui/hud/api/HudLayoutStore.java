package club.heiqi.uilib.ui.hud.api;

/**
 * HUD 布局与缩放的持久化端口（纯文本 IO，平台无关）。
 *
 * <p>UILib 负责「文本 ↔ 相对记录」的编解码、schemaVersion 判定与损坏降级；宿主只负责
 * 「读取原文 + 原子写回原文」。因此本接口不出现任何布局数学、坐标、UI 或缩放类型——
 * 下游 Mod 的架构守卫（不碰布局数学与 UI 装配）可以安全实现它。</p>
 *
 * <p>契约：</p>
 * <ul>
 *   <li>{@link #load()} 返回 {@code null} 或空白 = 无数据（首次运行）→ UILib 保持默认布局并允许写回；
 *       返回非空文本 = UILib 解析：无法识别（未知 schemaVersion/损坏）时降级为默认布局并
 *       <b>禁用本次会话自动写回</b>，保护无法识别的数据；实现不得抛异常，读取失败返回 {@code null}；</li>
 *   <li>{@link #save(String)} 由 UILib 在「编辑提交」或「缩放变更合并」后调用，宿主不需要主动 flush；
 *       实现须自行保证原子写（临时文件 + move 覆盖），内部失败静默；</li>
 *   <li>两个方法都限客户端主线程调用（UILib 只在主线程的加载/提交/度量上报路径调用）。</li>
 * </ul>
 */
public interface HudLayoutStore {

    /**
     * 读取持久化文本。
     *
     * @return 持久化原文；无数据/不可读时返回 {@code null} 或空串（UILib 按首次运行处理）
     */
    String load();

    /**
     * 写入持久化文本（格式由 UILib 冻结；宿主不得解析或手工构造）。
     *
     * @param text 完整快照文本
     */
    void save(String text);
}
