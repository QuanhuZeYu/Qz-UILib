package club.heiqi.uilib.ui.scene.input;

/**
 * 指针事件旁路源解耦接口 —— 宿主基类据此判定输入源是否支持指针按钮事件旁路，
 * 不再认识具体平台实现类（守 I10：基类不依赖平台侧端点）。
 *
 * <h3>设计动机（Bug3：下拉 item 点击失效真因止血）</h3>
 * <p>{@code LwjglInputSource} 的 poll-based 差分对"点开下拉后紧接着点 item"的连续操作系统性
 * 丢失 DOWN/UP 边沿：浮层 mount 引发该帧布局/绘制负载骤增，紧接的 DOWN+UP 落在长帧的
 * 单次 drainFrame 间隔内，{@code curButtons==false && lastButtons==false} → 差分看不到边沿
 * → CLICK 合成条件不满足。</p>
 *
 * <p>MC 的 {@code GuiScreen.mouseClicked/mouseMovedOrUp} 是事件驱动（每次物理点击必回调一次），
 * 不会丢边沿。宿主把回调 push 进输入源，与 poll 差分合并；启用旁路时输入源跳过 button poll 差分，
 * 避免双路 double-dispatch（MOVE/SCROLL/CANCEL 仍走 poll）。</p>
 *
 * <h3>坐标系契约</h3>
 * <p>调用方（如 {@code McScreenBridge}）必须将 MC 传入的 scaled 逻辑像素换算为物理像素
 * （与 {@code LwjglStateReader.mouseX/Y} 同量纲）后再 push，否则 GUI Scale≠1 时 hit-test 系统性偏移。</p>
 */
public interface PointerEventInputSource {

    /**
     * 推入指针按钮事件（DOWN/UP），由宿主 mouseClicked/mouseReleased 回调驱动。
     *
     * @param action    POINTER_DOWN 或 POINTER_UP
     * @param physicalX 物理像素 X（调用方负责逻辑→物理换算）
     * @param physicalY 物理像素 Y（调用方负责逻辑→物理换算）
     * @param button    鼠标按钮
     * @param timeNanos 事件时间戳（纳秒）
     */
    void pushPointerButton(ScenePointerAction action, int physicalX, int physicalY,
                           SceneMouseButton button, long timeNanos);

    /**
     * 切换外部指针模式：true=按钮事件由宿主回调接管（poll 停产 button 边沿）；
     * false=回归 poll 差分。
     *
     * @param external true 表示按钮事件走宿主回调旁路
     */
    void setExternalPointerMode(boolean external);
}
