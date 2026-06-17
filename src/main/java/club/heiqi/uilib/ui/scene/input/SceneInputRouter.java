package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.scene.node.SceneNode;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景输入路由器 —— I2 路由主入口。
 *
 * <h3>核心职责</h3>
 * <ul>
 *   <li><b>外挂注册表</b>：{@code Map<SceneNode, EnumMap<SceneEventType, List<SceneEventHandler>>>}
 *       —— SceneNode 零字段，handler 全挂路由器。</li>
 *   <li><b>隐式按压捕获</b>：BUTTON_DOWN 记 pressedNode；MOVE/BUTTON_UP 期间
 *       派发目标强制为 pressedNode；BUTTON_UP 后清空。</li>
 *   <li><b>CLICK 合成</b>：UP 时若原始命中 target==pressedNode，在 UP 派发完成后
 *       合成 CLICK 事件派发到同一 target+bubble 链。</li>
 *   <li><b>hit-test → target+bubble</b>：每 POINTER 事件 hit-test 得命中链，
 *       映射 action→type，先 target 阶段再沿链向 root 反向 bubble。</li>
 * </ul>
 *
 * <h3>零标脏硬不变量</h3>
 * <p>route 过程中绝不调用任何 node.setXxx()/markXxx()/appendChild/removeChild，
 * hit-test 的绝对坐标仅在遍历时临时累加绝不回写（I7/I11）。</p>
 */
public class SceneInputRouter {

    /**
     * 外挂 handler 注册表：SceneNode → (事件类型 → handler 列表)。
     * SceneNode 自身无 handler 字段，所有注册挂在路由器。
     */
    private final Map<SceneNode, EnumMap<SceneEventType, List<SceneEventHandler>>> registry;

    /** 命中测试器（无状态，共享） */
    private final SceneHitTester hitTester;

    /** 隐式按压捕获：当前按下的节点 */
    private SceneNode pressedNode;
    /** 隐式按压捕获：当前按下的按钮 */
    private SceneMouseButton pressedButton;

    public SceneInputRouter() {
        this.registry = new HashMap<SceneNode, EnumMap<SceneEventType, List<SceneEventHandler>>>();
        this.hitTester = new SceneHitTester();
        this.pressedNode = null;
        this.pressedButton = null;
    }

    // ==================== route 主入口 ====================

    /**
     * 对一帧内的所有指针事件执行命中测试 + target/bubble 路由。
     *
     * <p>遍历 {@code frame.getPointerEvents()}，每事件独立 hit-test（使用事件的
     * {@code logicalX/Y} 而非帧级粘滞 pointerX/Y），映射 action→type，
     * 先 target 阶段（最深命中节点）再 bubble 阶段（沿命中链向 root 反向逐级祖先）。</p>
     *
     * <h3>按压捕获在移出整树时仍生效</h3>
     * <p>DOWN 后即使指针移出目标乃至移出整树（hitChain 为空），MOVE/UP 仍强制投递给
     * pressedNode。hitTarget 为空只影响 CLICK 合成与 pressedNode 更新，不阻断捕获投递。</p>
     *
     * @param root      场景树根节点
     * @param frame     输入帧快照
     * @param rootAbsX  根节点屏幕绝对 X 偏移（沙箱传 0）
     * @param rootAbsY  根节点屏幕绝对 Y 偏移（沙箱传 0）
     */
    public void route(SceneNode root, SceneInputFrame frame, int rootAbsX, int rootAbsY) {
        if (root == null || frame == null) return;

        for (ScenePointerEvent pe : frame.getPointerEvents()) {
            SceneEventType type = mapActionToType(pe.getAction());
            if (type == null) continue;

            // 指针画布逻辑坐标（不预先加 rootAbsX，整树平移完全交给 hitTester 内部处理）
            int canvasX = pe.getLogicalX();
            int canvasY = pe.getLogicalY();

            // hit-test（hitTester 内部用 rootAbsX 做整树平移）
            List<SceneNode> hitChain = hitTester.hitTest(root, canvasX, canvasY, rootAbsX, rootAbsY);

            // 原始命中目标：null 表示指针在整树 bounds 外
            SceneNode hitTarget = hitChain.isEmpty() ? null : hitChain.get(hitChain.size() - 1);

            // 按压捕获判定：捕获投递优先于 hit-test 空检查
            boolean captured = (pressedNode != null)
                    && (type == SceneEventType.POINTER_MOVE || type == SceneEventType.POINTER_UP);

            SceneNode effectiveTarget;
            if (captured) {
                // 强制投递给 pressedNode，无论 hitTarget 是否为 null
                effectiveTarget = pressedNode;
            } else {
                // 非捕获且未命中 → 跳过此事件
                if (hitTarget == null) continue;
                effectiveTarget = hitTarget;
            }

            // 构造事件（pointerX/Y 存画布逻辑坐标）
            SceneEvent event = new SceneEvent(type, effectiveTarget, canvasX, canvasY,
                    pe.getButton(), pe.getWheelDelta(),
                    pe.isControlDown(), pe.isShiftDown(), pe.isAltDown(), pe.isMetaDown(),
                    pe.getTimeNanos());

            // 派发：target → bubble
            SceneEventContext ctx = new SceneEventContext();
            dispatchTargetAndBubble(event, ctx, effectiveTarget);

            // === 按压捕获状态更新 ===
            if (type == SceneEventType.POINTER_DOWN) {
                // 仅指针在树内命中时才记录 pressedNode
                if (hitTarget != null) {
                    pressedNode = hitTarget;
                    pressedButton = pe.getButton();
                }
            }

            if (type == SceneEventType.POINTER_UP) {
                // CLICK 合成判定使用原始 hitTarget（非 effectiveTarget）
                // 出界 UP（hitTarget=null 或 != pressedNode）不合成 CLICK
                if (pressedNode != null && hitTarget != null && hitTarget == pressedNode) {
                    SceneEvent clickEvent = new SceneEvent(SceneEventType.CLICK, hitTarget,
                            canvasX, canvasY,
                            pe.getButton(), 0, // wheelDelta=0 for CLICK
                            pe.isControlDown(), pe.isShiftDown(), pe.isAltDown(), pe.isMetaDown(),
                            pe.getTimeNanos());
                    SceneEventContext clickCtx = new SceneEventContext();
                    dispatchTargetAndBubble(clickEvent, clickCtx, hitTarget);
                }
                // 无论是否出界，UP 后一律清空按压捕获状态（防止永久泄漏）
                pressedNode = null;
                pressedButton = null;
            }
        }
    }

    /**
     * 执行 target + bubble 两阶段派发。
     *
     * <p>target 阶段：派发到 effectiveTarget；bubble 阶段：沿 parent 链向 root
     * 逐级派发。任一阶段 handler 调用 {@code ctx.stopPropagation()} 后，
     * 不再向更上层祖先派发（但当前节点已注册的多 handler 仍全部跑完）。</p>
     */
    private void dispatchTargetAndBubble(SceneEvent event, SceneEventContext ctx, SceneNode target) {
        // target 阶段
        ctx.setCurrentNode(target);
        dispatchToNode(event, ctx, target);
        if (ctx.isPropagationStopped()) return;

        // bubble 阶段：沿父链向上逐级派发
        SceneNode current = target.__getParent();
        while (current != null && !ctx.isPropagationStopped()) {
            ctx.setCurrentNode(current);
            dispatchToNode(event, ctx, current);
            current = current.__getParent();
        }
    }

    /**
     * 向指定节点派发事件：遍历该节点上注册的该类型所有 handler。
     */
    private void dispatchToNode(SceneEvent event, SceneEventContext ctx, SceneNode node) {
        EnumMap<SceneEventType, List<SceneEventHandler>> typeMap = registry.get(node);
        if (typeMap == null) return;
        List<SceneEventHandler> handlers = typeMap.get(event.getType());
        if (handlers == null || handlers.isEmpty()) return;
        // 遍历期间若 handler 调用 stopPropagation，仍跑完当前节点剩余 handler
        for (SceneEventHandler handler : handlers) {
            handler.handle(event, ctx);
        }
    }

    // ==================== on() 注册 ====================

    /**
     * 为指定节点的指定事件类型注册 handler。
     *
     * <p>handler 存储在路由器外挂注册表中，SceneNode 自身零字段。
     * 若当前处于 {@link Owner} 作用域内，自动登记退订回调
     * （调用 {@code Owner.current().onCleanup(disposeRunnable)}），
     * 随组件卸载一并移除 handler。</p>
     *
     * @param node    目标节点
     * @param type    事件类型
     * @param handler 事件处理器
     * @return 绑定句柄（可手动 dispose 退订）
     */
    public InputBinding on(SceneNode node, SceneEventType type, SceneEventHandler handler) {
        if (node == null || type == null || handler == null) {
            throw new IllegalArgumentException("node/type/handler 均不可为 null");
        }

        // 注册到外挂表
        EnumMap<SceneEventType, List<SceneEventHandler>> typeMap = registry.get(node);
        if (typeMap == null) {
            typeMap = new EnumMap<SceneEventType, List<SceneEventHandler>>(SceneEventType.class);
            registry.put(node, typeMap);
        }
        final EnumMap<SceneEventType, List<SceneEventHandler>> finalTypeMap = typeMap;

        List<SceneEventHandler> handlers = typeMap.get(type);
        if (handlers == null) {
            handlers = new ArrayList<SceneEventHandler>();
            typeMap.put(type, handlers);
        }
        handlers.add(handler);

        // 退订 Runnable（捕获 final 引用确保编译通过）
        Runnable disposeRunnable = new Runnable() {
            private boolean disposed = false;

            @Override
            public void run() {
                if (disposed) return;
                disposed = true;
                List<SceneEventHandler> list = finalTypeMap.get(type);
                if (list != null) {
                    list.remove(handler);
                    if (list.isEmpty()) {
                        finalTypeMap.remove(type);
                    }
                }
                if (finalTypeMap.isEmpty()) {
                    registry.remove(node);
                }
            }
        };

        // 若在 Owner 作用域内，自动登记退订
        Owner current = Owner.current();
        if (current != null) {
            current.onCleanup(disposeRunnable);
        }

        return new InputBinding(disposeRunnable);
    }

    // ==================== 测试探针（包级可见性） ====================

    /**
     * @return 当前按压捕获节点（测试探针）
     */
    SceneNode __getPressedNode() {
        return pressedNode;
    }

    /**
     * @return 当前按压捕获按钮（测试探针）
     */
    SceneMouseButton __getPressedButton() {
        return pressedButton;
    }

    /**
     * @param node 目标节点
     * @param type 事件类型
     * @return 该节点上注册的该类型 handler 数量（测试探针）
     */
    int __handlerCount(SceneNode node, SceneEventType type) {
        EnumMap<SceneEventType, List<SceneEventHandler>> typeMap = registry.get(node);
        if (typeMap == null) return 0;
        List<SceneEventHandler> handlers = typeMap.get(type);
        return handlers == null ? 0 : handlers.size();
    }

    // ==================== 内部映射 ====================

    /**
     * 将 ScenePointerAction 映射为 SceneEventType。
     */
    static SceneEventType mapActionToType(ScenePointerAction action) {
        switch (action) {
            case BUTTON_DOWN: return SceneEventType.POINTER_DOWN;
            case BUTTON_UP:   return SceneEventType.POINTER_UP;
            case MOVE:        return SceneEventType.POINTER_MOVE;
            case SCROLL:      return SceneEventType.SCROLL;
            default:          return null;
        }
    }
}
