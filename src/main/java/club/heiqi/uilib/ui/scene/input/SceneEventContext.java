package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 事件派发上下文。
 *
 * <p>一条 route 派发链共享同一 context 实例。handler 可通过
 * {@link #stopPropagation()} 阻止事件继续向上冒泡，通过
 * {@link #getCurrentNode()} 获取当前派发游标所在的祖先节点。</p>
 *
 * <p>{@link #requestFocus()} 将聚焦 ctx 构造时指定的事件 target（最深命中/焦点节点），
 * 不受 bubble 游标 currentNode 影响。{@link #requestPointerCapture()} 为 I4d 预留。
 * 这两个命令改的是 Router 的权威状态机，结果仍经 signal 暴露（I11 白名单②）。</p>
 *
 * <h3>两层坐标（I12）</h3>
 * <p>ctx 持有 raw 指针坐标与 treeRoot 绝对偏移，提供两层坐标 getter：</p>
 * <ul>
 *   <li>{@link #getRawPointerX()} / {@link #getRawPointerY()}：屏幕绝对（含 rootAbs），仅供跨树辅助，
 *       禁止与 {@link SceneGeometry#absoluteBox} 传 0,0 混比。</li>
 *   <li>{@link #getLocalPointerX()} / {@link #getLocalPointerY()}：当前接收 handler 节点局部 =
 *       {@code rawPointer - absoluteBox(currentNode, treeAbs)}。currentNode 每级 bubble 由 Router
 *       更新，故 local 每级重算。handler 默认消费此层。</li>
 * </ul>
 * <p>host 层已废弃（D2），原 {@code hostPointer = raw - rootAbs} 的几何比对改由 local 直接消费。</p>
 */
public class SceneEventContext {

    /** 关联的路由器，用于 requestFocus 等受控命令 */
    private final SceneInputRouter router;
    /** 事件原始 target（最深命中/焦点节点），不随 bubble 游标变化 */
    private final SceneNode target;
    /** 当前派发游标（target 阶段 = 最深目标，bubble 阶段 = 逐级祖先） */
    private SceneNode currentNode;

    /** 指针屏幕绝对 X（raw 层，含 rootAbs 或 overlay anchor） */
    private final int rawPointerX;
    /** 指针屏幕绝对 Y（raw 层，含 rootAbs 或 overlay anchor） */
    private final int rawPointerY;
    /** 当前派发树根的屏幕绝对 X 偏移（主树=rootAbsX，overlay=overlay anchorX） */
    private final int treeRootAbsX;
    /** 当前派发树根的屏幕绝对 Y 偏移（主树=rootAbsY，overlay=overlay anchorY） */
    private final int treeRootAbsY;

    /** 是否已停止冒泡 */
    private boolean propagationStopped;

    /**
     * 包级构造器，由 {@link SceneInputRouter} 创建。
     *
     * @param router 输入路由器（用于受控命令如 requestFocus）
     * @param target 事件原始目标节点（requestFocus 聚焦此节点，非 currentNode）
     * @param rawPointerX 指针屏幕绝对 X（raw 层）
     * @param rawPointerY 指针屏幕绝对 Y（raw 层）
     * @param treeRootAbsX 当前派发树根屏幕绝对 X 偏移（主树=rootAbsX，overlay=anchorX）
     * @param treeRootAbsY 当前派发树根屏幕绝对 Y 偏移（主树=rootAbsY，overlay=anchorY）
     */
    SceneEventContext(SceneInputRouter router, SceneNode target,
                      int rawPointerX, int rawPointerY,
                      int treeRootAbsX, int treeRootAbsY) {
        this.router = router;
        this.target = target;
        this.rawPointerX = rawPointerX;
        this.rawPointerY = rawPointerY;
        this.treeRootAbsX = treeRootAbsX;
        this.treeRootAbsY = treeRootAbsY;
        this.propagationStopped = false;
    }

    /**
     * 停止事件冒泡。
     *
     * <p>调用后，事件不再向更上层祖先派发，但当前节点已注册的多 handler 仍会全部跑完。</p>
     */
    public void stopPropagation() {
        this.propagationStopped = true;
    }

    /**
     * @return 是否已停止冒泡
     */
    public boolean isPropagationStopped() {
        return propagationStopped;
    }

    /**
     * 获取当前派发游标所在的节点。
     *
     * <p>target 阶段返回最深命中节点；bubble 阶段返回当前冒泡到的祖先。</p>
     *
     * @return 当前派发节点
     */
    public SceneNode getCurrentNode() {
        return currentNode;
    }

    /**
     * 设置当前派发节点（包级，仅 router 使用）。
     */
    void setCurrentNode(SceneNode node) {
        this.currentNode = node;
    }

    /**
     * @return 指针屏幕绝对 X 坐标（raw 层，含 rootAbs/overlay anchor）。
     *         仅供跨窗口/跨树辅助，禁止与 {@link SceneGeometry#absoluteBox} 传 0,0 混比。
     */
    public int getRawPointerX() {
        return rawPointerX;
    }

    /**
     * @return 指针屏幕绝对 Y 坐标（raw 层，含 rootAbs/overlay anchor）。
     *         仅供跨窗口/跨树辅助，禁止与 {@link SceneGeometry#absoluteBox} 传 0,0 混比。
     */
    public int getRawPointerY() {
        return rawPointerY;
    }

    /**
     * @return 当前接收 handler 节点局部 X 坐标（local 层）。
     *         = {@code rawPointerX - absoluteBox(currentNode, treeRootAbsX, treeRootAbsY).getX()}。
     *         currentNode 每级 bubble 由 Router 更新，故每级重算。handler 默认消费此值。
     *         只读 absoluteBox，守 I7/I11/I12。
     */
    public int getLocalPointerX() {
        AnchorRect box = SceneGeometry.absoluteBox(currentNode, treeRootAbsX, treeRootAbsY);
        return rawPointerX - box.getX();
    }

    /**
     * @return 当前接收 handler 节点局部 Y 坐标（local 层）。
     *         = {@code rawPointerY - absoluteBox(currentNode, treeRootAbsX, treeRootAbsY).getY()}。
     *         currentNode 每级 bubble 由 Router 更新，故每级重算。handler 默认消费此值。
     *         只读 absoluteBox，守 I7/I11/I12。
     */
    public int getLocalPointerY() {
        AnchorRect box = SceneGeometry.absoluteBox(currentNode, treeRootAbsX, treeRootAbsY);
        return rawPointerY - box.getY();
    }

    /**
     * @return 当前派发树根（主树 root / overlay root）在屏幕上的绝对 X 偏移。
     *         host 局部坐标 = {@code rawPointer - treeRootAbs}，供锚定浮层（如右键菜单）定位。
     */
    public int getTreeRootAbsX() {
        return treeRootAbsX;
    }

    /**
     * @return 当前派发树根（主树 root / overlay root）在屏幕上的绝对 Y 偏移。
     *         host 局部坐标 = {@code rawPointer - treeRootAbs}，供锚定浮层（如右键菜单）定位。
     */
    public int getTreeRootAbsY() {
        return treeRootAbsY;
    }

    /**
     * 请求焦点：将焦点赋予当前事件的目标节点（非 bubble 游标 currentNode）。
     *
     * <p>若 router 或 target 为 null，则无副作用短路。此命令改 Router 权威状态机，
     * 结果经 focus signal 暴露（I11 白名单②）。</p>
     */
    public void requestFocus() {
        if (router != null && target != null) {
            router.requestFocus(target);
        }
    }

    /**
     * 请求指针捕获：将事件原始 target（非 bubble 游标 currentNode）设为 Router 的显式捕获节点。
     *
     * <p>捕获后 MOVE/UP/DOWN 均强制投递给 capturedNode，直至 UP 后自动释放。
     * 此命令改 Router 权威状态机，结果仍经 signal 暴露（I11 白名单②）。</p>
     */
    public void requestPointerCapture() {
        if (router != null && target != null) {
            router.requestPointerCapture(target);
        }
    }
}
