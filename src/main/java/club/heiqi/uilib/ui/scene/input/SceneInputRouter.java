package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 场景输入路由器 —— I2 路由主入口 + I4a 键盘/焦点路由。
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
 *   <li><b>I4a 键盘/文本路由</b>：持有 {@link FocusManager}，key 事件投给焦点节点走 bubble；
 *       text 事件投给焦点节点；Tab 触发焦点遍历。</li>
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

    /** 可选浮层宿主：存在时指针命中先按 top-first 检查 overlay roots。 */
    private final SceneOverlayHost overlayHost;

    /** 隐式按压捕获：当前按下的节点 */
    private SceneNode pressedNode;
    /** 隐式按压捕获：当前按下的按钮 */
    private SceneMouseButton pressedButton;
    /** 显式指针捕获节点（requestPointerCapture 设置，UP 后自动释放） */
    private SceneNode capturedNode;

    /**
     * I3 交互状态：当前 hover 的节点（单节点，最深命中目标）。
     *
     * <p>I3 仅跟踪最深命中目标的 hover 切换；整条祖先链 :hover 留 I4。</p>
     */
    private SceneNode hoveredNode;

    /**
     * B8 滚动后 hover 重算标记（Router 内部状态机变量，类比 pressedNode）。
     *
     * <p>route 内检测到本帧含 SCROLL 事件时置 true；由 host 在 flush + layout 后
     * 调用 {@link #reconcileHoverAfterScroll} 消费并清零。纯内部协议状态，
     * 不在 EventContext 上，handler 碰不到（不扩 I11）。</p>
     */
    private boolean pendingHoverReconcile = false;

    /**
     * I3 交互状态外挂表：SceneNode → {@link SceneInteractionState}。
     *
     * <p>强引用 Map（禁止 WeakHashMap），靠 {@link Owner#onCleanup} 回收，
     * 与 handler registry 同款生命周期。</p>
     */
    private final Map<SceneNode, SceneInteractionState> interactionStates = new HashMap<>();

    /**
     * I4a 焦点管理器：全局唯一焦点 + focusable 注册表 + Tab 遍历。
     * 构造注入 interactionStates 引用，焦点切换时通过它写 focused signal。
     */
    private final FocusManager focusManager;

    /**
     * I4c 全局光标 signal：Router 在 hover 切换时写入解析后的 {@link SceneCursor}，
     * cursor effect 订阅它驱动 {@link CursorBackend#apply}。初始值 {@link SceneCursor#DEFAULT}。
     *
     * <p>写操作 = {@code cursorSignal.set(SceneCursorResolver.resolve(hoveredNode))}，
     * 走 queueWrite，同帧末 flush 生效（I9 同帧一次 flush）。</p>
     */
    private final Signal<SceneCursor> cursorSignal = Signal.create(SceneCursor.DEFAULT);

    public SceneInputRouter() {
        this(null);
    }

    /**
     * 创建带浮层宿主的输入路由器。
     *
     * @param overlayHost 浮层宿主，可为 null；为 null 或为空时完全退化为主树路由
     */
    public SceneInputRouter(SceneOverlayHost overlayHost) {
        this.registry = new HashMap<SceneNode, EnumMap<SceneEventType, List<SceneEventHandler>>>();
        this.hitTester = new SceneHitTester();
        this.overlayHost = overlayHost;
        this.pressedNode = null;
        this.pressedButton = null;
        this.hoveredNode = null;
        this.focusManager = new FocusManager(interactionStates);
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

            // hit-test：overlay top-first 优先；未命中时退回主树（hitTester 全程只读，守 I7）。
            HitResult hitResult = hitTestWithOverlays(root, canvasX, canvasY, rootAbsX, rootAbsY);
            List<SceneNode> hitChain = hitResult.chain;

            if (type == SceneEventType.POINTER_DOWN) {
                requestOutsidePointerDismiss(canvasX, canvasY, hitResult.overlayEntry, hitChain);
            }

            // 原始命中目标：null 表示指针在整树 bounds 外
            SceneNode hitTarget = hitChain.isEmpty() ? null : hitChain.get(hitChain.size() - 1);

            // === hover 状态更新（仅 MOVE 驱动，在 dispatch 之前、continue 之前，确保移出整树时也能检测 leave）===
            // 历史瑕疵已根治（2026-06-18）：曾存在"同帧内 hover 在节点间往返（A→B→A）时，
            // 中间节点因 Signal 基于未 flush 旧值去重而残留 true 一帧"的问题。该瑕疵的根因是
            // 旧 Signal.set 拿"已 flush 旧值"去重；reactive 地基已把去重移到 flush 阶段、
            // 改为比对帧初值与合并末值，终值==帧初值（往返回原值）会被正确吸收为无净变化，
            // 不再残留。权威 hoveredNode 真值在任何时刻都正确，无需额外维护本帧 touched 集。
            //
            // ★ capture 只改 dispatch effectiveTarget，绝不改 newHover = hitTarget（守 I3 边界③）
            if (type == SceneEventType.POINTER_MOVE) {
                // 复用统一 hover 切换逻辑（与 reconcileHoverAfterScroll 同源）
                updateHoverFromTarget(hitTarget);
            }

            // === B8：SCROLL 事件标记本帧需要 flush 后 hover 重算 ===
            // route 内 scrollOffsetY 尚未生效（SceneScrolls handler 走 queueWrite，帧末 flush 才生效），
            // 此处只置标记，真正重算由 host 在 flush + layout 后调 reconcileHoverAfterScroll 完成。
            if (type == SceneEventType.SCROLL) {
                pendingHoverReconcile = true;
            }

            // === POINTER_CANCEL 收口（I4d）：在 effectiveTarget 判定之前走专属投递块，绝不触达通用 dispatch ===
            // CANCEL 目标是 pressedNode/capturedNode，不依赖 hit-test 命中；
            // 提前处理 + continue 确保跳过通用 effectiveTarget dispatch，消除 double-dispatch。
            if (type == SceneEventType.POINTER_CANCEL) {
                dispatchPointerCancel(pe, canvasX, canvasY, rootAbsX, rootAbsY);
                continue; // 跳过通用 effectiveTarget dispatch + DOWN/UP 块
            }

            // 指针主体：隐式聚焦/失焦 + effectiveTarget 判定 + 派发 + 按压捕获 + CLICK 合成
            dispatchPointerMain(pe, type, canvasX, canvasY, hitResult, rootAbsX, rootAbsY);
        }

        // === I4a 键盘/文本分发（指针循环结束之后，先 text 后 key，用户拍板 D4-A） ===
        dispatchKeyboardAndText(frame, root);
    }

    /**
     * POINTER_CANCEL 专属投递（I4d 收口）。
     *
     * <p>CANCEL 目标是 pressedNode/capturedNode，不依赖 hit-test 命中；
     * 在 route 中提前处理 + continue 确保跳过通用 effectiveTarget dispatch，消除 double-dispatch。</p>
     *
     * <p>CANCEL 目标在主树，treeAbs=rootAbs；SceneEvent 只传 raw（canvasX/Y），local 由 ctx 每级重算。
     * 投递完成后写入 pressed=false 并清空所有按压/捕获状态（收口 I3 边界① 的 pressedNode 失焦泄漏）。</p>
     *
     * <p>零标脏（I7）：只读 interactionStates，不碰任何 SceneNode setter。</p>
     *
     * @param pe       指针事件（取 button/wheelDelta/修饰键/timeNanos）
     * @param canvasX  画布逻辑 X
     * @param canvasY  画布逻辑 Y
     * @param rootAbsX 根节点屏幕绝对 X 偏移
     * @param rootAbsY 根节点屏幕绝对 Y 偏移
     */
    private void dispatchPointerCancel(ScenePointerEvent pe, int canvasX, int canvasY,
                                       int rootAbsX, int rootAbsY) {
        boolean hasCaptured = capturedNode != null;
        boolean hasPressed = pressedNode != null;
        SceneEventType type = SceneEventType.POINTER_CANCEL;

        // CANCEL 目标在主树，treeAbs=rootAbs；SceneEvent 只传 raw（canvasX/Y），local 由 ctx 每级重算。
        if (hasCaptured) {
            SceneEvent cancelEvt = new SceneEvent(type, capturedNode, canvasX, canvasY,
                    pe.getButton(), pe.getWheelDelta(),
                    pe.isControlDown(), pe.isShiftDown(), pe.isAltDown(), pe.isMetaDown(),
                    pe.getTimeNanos());
            SceneEventContext cancelCtx = new SceneEventContext(this, capturedNode,
                    canvasX, canvasY, rootAbsX, rootAbsY);
            dispatchTargetAndBubble(cancelEvt, cancelCtx, capturedNode);
        }
        if (hasPressed && pressedNode != capturedNode) {
            SceneEvent cancelEvt = new SceneEvent(type, pressedNode, canvasX, canvasY,
                    pe.getButton(), pe.getWheelDelta(),
                    pe.isControlDown(), pe.isShiftDown(), pe.isAltDown(), pe.isMetaDown(),
                    pe.getTimeNanos());
            SceneEventContext cancelCtx = new SceneEventContext(this, pressedNode,
                    canvasX, canvasY, rootAbsX, rootAbsY);
            dispatchTargetAndBubble(cancelEvt, cancelCtx, pressedNode);
        }

        // 写入 pressed=false 并清空所有按压/捕获状态（收口 I3 边界① 的 pressedNode 失焦泄漏）
        if (pressedNode != null) {
            SceneInteractionState st = interactionStates.get(pressedNode);
            if (st != null) st.writePressed(false);
        }
        pressedNode = null;
        pressedButton = null;
        capturedNode = null;
    }

    /**
     * 指针事件主体路由：隐式聚焦/失焦 + effectiveTarget 判定 + target/bubble 派发
     * + 按压捕获状态更新 + CLICK 合成。
     *
     * <p>覆盖 DOWN/MOVE/UP/SCROLL（CANCEL 已由 {@link #dispatchPointerCancel} 专属处理）。</p>
     *
     * <h3>隐式聚焦（Bug1）</h3>
     * <p>POINTER_DOWN 时焦点完全由"这一下点在哪"决定：命中 focusable（含沿命中链向 root 的祖先）
     * → 聚焦；命中非 focusable 或点在树外 → 失焦（clearFocus，用户拍板反转语义）。放 dispatch 之前，
     * 使 handler 内 ctx.requestFocus() 可覆盖隐式结果（命中非 focusable 先 clearFocus，事件仍 dispatch，
     * handler 内 requestFocus 在后覆盖）。无条件进入（去掉 hitTarget != null 守卫）：树外点击 hitTarget==null
     * 时本块先执行 clearFocus，再走到下方 hitTarget==null→return。</p>
     *
     * <p>★判定只看 hitTarget（命中真值），与 capturedNode/pressedNode 正交——失焦是焦点机制、capture 是指针机制。
     * 零标脏（I7）：clearFocus 内部 writeFocused(false)→queueWrite，focusedNode==null 时短路安全；requestFocus 同款零标脏。</p>
     *
     * <p>★N1 守卫：显式 capture 持有期抑制隐式聚焦——capture 已把指针归属锁定到 capturedNode，
     * 此时同一 DOWN 若再走隐式聚焦（命中非 focusable/树外 → clearFocus）会与 capture 投递形成相反归属，
     * capture 持有期焦点机制让位指针 capture，跳过本块。</p>
     *
     * <h3>effectiveTarget 判定（I4d）</h3>
     * <p>显式 capture 优先 ＞ 隐式 pressedNode（MOVE/UP）＞ hitTarget。非捕获且未命中（hitTarget==null）
     * 直接 return 跳过此事件（原 route 循环中的 continue，因后续逻辑全在本方法内，return 等价）。</p>
     *
     * <h3>CLICK 合成</h3>
     * <p>UP 时若原始命中 hitTarget==pressedNode，在 UP 派发完成后合成 CLICK 派发到同一 target+bubble 链。
     * 出界 UP（hitTarget=null 或 != pressedNode）不合成 CLICK。</p>
     *
     * @param pe        指针事件
     * @param type      已映射的事件类型（非 null，非 CANCEL）
     * @param canvasX   画布逻辑 X
     * @param canvasY   画布逻辑 Y
     * @param hitResult hit-test 结果（含命中链与 overlay entry）
     * @param rootAbsX  根节点屏幕绝对 X 偏移
     * @param rootAbsY  根节点屏幕绝对 Y 偏移
     */
    private void dispatchPointerMain(ScenePointerEvent pe, SceneEventType type,
                                     int canvasX, int canvasY,
                                     HitResult hitResult, int rootAbsX, int rootAbsY) {
        List<SceneNode> hitChain = hitResult.chain;
        // 原始命中目标：null 表示指针在整树 bounds 外
        SceneNode hitTarget = hitChain.isEmpty() ? null : hitChain.get(hitChain.size() - 1);

        // === Bug1：POINTER_DOWN 隐式聚焦/失焦——焦点完全由"这一下点在哪"决定 ===
        if (type == SceneEventType.POINTER_DOWN && capturedNode == null) {
            SceneNode implicitFocus = (hitTarget != null)
                    ? focusManager.findDeepestFocusable(hitChain)
                    : null;
            if (implicitFocus != null) {
                focusManager.requestFocus(implicitFocus);   // 命中 focusable（含祖先链）→ 聚焦
            } else {
                focusManager.clearFocus();                  // 命中非 focusable 或树外(null) → 失焦
            }
        }

        // ===== 显式 capture 优先于隐式 pressedNode（I4d effectiveTarget 判定） =====
        SceneNode effectiveTarget;
        if (capturedNode != null) {
            // 显式捕获：MOVE/UP/DOWN 都强制投 capturedNode，即使 hitTarget 为 null
            effectiveTarget = capturedNode;
        } else if (pressedNode != null
                && (type == SceneEventType.POINTER_MOVE || type == SceneEventType.POINTER_UP)) {
            // 隐式按压捕获：DOWN→UP 自动捕获，即使 hitTarget 为 null
            effectiveTarget = pressedNode;
        } else {
            // 非捕获且未命中 → 跳过此事件（原 route 循环 continue，本方法内 return 等价）
            if (hitTarget == null) return;
            effectiveTarget = hitTarget;
        }

        // 构造事件（两层坐标 I12）：
        //   rawPointerX/Y = 屏幕绝对（raw，含 rootAbs），SceneEvent 只携带 raw
        //   local 由 ctx 每级 bubble 重算（rawPointer - absoluteBox(currentNode, treeAbs)）
        // overlay 命中时 treeAbs=overlay anchor，主树命中时 treeAbs=rootAbs，local 自动正确。
        int treeAbsX = hitResult.overlayEntry != null ? hitResult.overlayEntry.getAnchorX() : rootAbsX;
        int treeAbsY = hitResult.overlayEntry != null ? hitResult.overlayEntry.getAnchorY() : rootAbsY;
        SceneEvent event = new SceneEvent(type, effectiveTarget, canvasX, canvasY,
                pe.getButton(), pe.getWheelDelta(),
                pe.isControlDown(), pe.isShiftDown(), pe.isAltDown(), pe.isMetaDown(),
                pe.getTimeNanos());

        // 派发：target → bubble（CANCEL 已在 route 专属块中 continue，永不触达此处）
        SceneEventContext ctx = new SceneEventContext(this, effectiveTarget,
                canvasX, canvasY, treeAbsX, treeAbsY);
        dispatchTargetAndBubble(event, ctx, effectiveTarget);

        // === 按压捕获状态更新 ===
        if (type == SceneEventType.POINTER_DOWN) {
            // 仅指针在树内命中时才记录 pressedNode（但 capturedNode 已由显式 requestPointerCapture 设置，两者独立）
            if (hitTarget != null) {
                pressedNode = hitTarget;
                pressedButton = pe.getButton();
                // I3: 记 pressedNode 之后写入 pressed signal
                SceneInteractionState st = interactionStates.get(hitTarget);
                if (st != null) st.writePressed(true);
            }
        }

        if (type == SceneEventType.POINTER_UP) {
            // CLICK 合成判定使用原始 hitTarget（非 effectiveTarget）
            // 出界 UP（hitTarget=null 或 != pressedNode）不合成 CLICK
            if (pressedNode != null && hitTarget != null && hitTarget == pressedNode) {
                // CLICK 合成：treeAbs 按 hitResult.overlayEntry 定（与主 dispatch 同源）。
                int clickTreeAbsX = hitResult.overlayEntry != null ? hitResult.overlayEntry.getAnchorX() : rootAbsX;
                int clickTreeAbsY = hitResult.overlayEntry != null ? hitResult.overlayEntry.getAnchorY() : rootAbsY;
                SceneEvent clickEvent = new SceneEvent(SceneEventType.CLICK, hitTarget,
                        canvasX, canvasY,
                        pe.getButton(), 0, // wheelDelta=0 for CLICK
                        pe.isControlDown(), pe.isShiftDown(), pe.isAltDown(), pe.isMetaDown(),
                        pe.getTimeNanos());
                SceneEventContext clickCtx = new SceneEventContext(this, hitTarget,
                        canvasX, canvasY, clickTreeAbsX, clickTreeAbsY);
                dispatchTargetAndBubble(clickEvent, clickCtx, hitTarget);
            }
            // I3: 清空 pressedNode 之前写入 pressed=false
            if (pressedNode != null) {
                SceneInteractionState st = interactionStates.get(pressedNode);
                if (st != null) st.writePressed(false);
            }
            // 无论是否出界，UP 后一律清空按压捕获状态
            pressedNode = null;
            pressedButton = null;
            // I4d: 显式 capture 释放（D7-A 最小版）：UP 投递后自动清 capturedNode，杜绝永久劫持
            if (capturedNode != null) {
                capturedNode = null;
            }
        }
    }

    /**
     * I4a 键盘/文本分发（指针循环结束之后，先 text 后 key，用户拍板 D4-A）。
     *
     * <p>设置当前帧根节点（供 FocusManager 做 DOM 前序遍历）后，先派发文本事件到焦点节点，
     * 再派发键盘事件；键盘事件含 ESC 优先 dismiss 与 Tab 默认焦点遍历。</p>
     *
     * @param frame 输入帧快照
     * @param root  场景树根节点
     */
    private void dispatchKeyboardAndText(SceneInputFrame frame, SceneNode root) {
        // 设置当前帧根节点，供 FocusManager#focusNext/focusPrevious 做 DOM 前序遍历
        focusManager.setRoot(root);

        // 文本分发（先于 key）
        for (SceneTextEvent te : frame.getTextEvents()) {
            SceneNode focusTarget = focusManager.getFocusedNode();
            if (focusTarget == null) continue; // 无焦点丢弃
            SceneEvent ev = SceneEvent.ofText(SceneEventType.TEXT_INPUT, focusTarget,
                    te.getText(), te.getTimeNanos());
            SceneEventContext ctx = new SceneEventContext(this, focusTarget, 0, 0, 0, 0);
            dispatchTargetAndBubble(ev, ctx, focusTarget);
        }

        // 键盘分发
        for (SceneKeyEvent ke : frame.getKeyEvents()) {
            if (ke.getAction() != SceneKeyAction.RELEASED && ke.getKey() == SceneKey.ESCAPE
                    && requestTopEscapeDismiss()) {
                continue;
            }
            // ★每事件重读焦点：前一事件 handler 可能 requestFocus 改了焦点
            SceneNode target = focusManager.getFocusedNode();
            if (target != null) {
                SceneEventType type = (ke.getAction() == SceneKeyAction.RELEASED)
                        ? SceneEventType.KEY_UP : SceneEventType.KEY_DOWN;
                boolean repeat = false; // D5 最小版不区分 repeat，恒 false
                SceneEvent ev = SceneEvent.ofKey(type, target, ke.getKey(), ke.getAction(), repeat,
                        ke.isControlDown(), ke.isShiftDown(), ke.isAltDown(), ke.isMetaDown(),
                        ke.getTimeNanos());
                SceneEventContext ctx = new SceneEventContext(this, target, 0, 0, 0, 0);
                dispatchTargetAndBubble(ev, ctx, target);

                // ★Tab 默认遍历：dispatch 之后 + isPropagationStopped 之后（handler 可拦截）
                if (type == SceneEventType.KEY_DOWN && ke.getKey() == SceneKey.TAB
                        && !ctx.isPropagationStopped()) {
                    if (ke.isShiftDown()) {
                        focusManager.focusPrevious();
                    } else {
                        focusManager.focusNext();
                    }
                }
            } else {
                // 无焦点时 Tab 进首个 focusable（D2-A）
                if (ke.getAction() != SceneKeyAction.RELEASED && ke.getKey() == SceneKey.TAB) {
                    if (ke.isShiftDown()) {
                        focusManager.focusPrevious();
                    } else {
                        focusManager.focusNext();
                    }
                }
            }
        }
    }

    /**
     * 统一 hover 切换逻辑：MOVE 分支与 {@link #reconcileHoverAfterScroll} 共用。
     *
     * <p>给定本帧最深命中目标 newHover（可能 null），与当前 hoveredNode 比较：
     * 不同则对旧节点 writeHovered(false)、新节点 writeHovered(true)，更新 hoveredNode，
     * 并写 cursorSignal（均走 queueWrite，同帧末 flush 生效，守 I9）。</p>
     *
     * <p>零标脏（I7）：只读 interactionStates / cursorSignal，不碰任何 SceneNode setter。
     * capture 只改 dispatch effectiveTarget，绝不改 newHover（守 I3 边界③）。</p>
     *
     * @param newHover 本帧最深命中目标，可能 null（指针移出整树）
     */
    private void updateHoverFromTarget(SceneNode newHover) {
        if (newHover != hoveredNode) {
            if (hoveredNode != null) {
                SceneInteractionState old = interactionStates.get(hoveredNode);
                if (old != null) old.writeHovered(false);
            }
            if (newHover != null) {
                SceneInteractionState cur = interactionStates.get(newHover);
                if (cur != null) cur.writeHovered(true);
            }
            hoveredNode = newHover;
            // I4c: hover 切换后更新全局 cursor signal（queueWrite，同帧末 flush 生效）
            cursorSignal.set(SceneCursorResolver.resolve(hoveredNode));
        }
    }

    /**
     * flush 后滚动 hover 重算（B8 修复，内部协议方法，非 EventContext 命令）。
     *
     * <h3>背景</h3>
     * <p>滚动容器内容滚动后，指针下方的实际节点已变，但纯滚轮滚动不触发 POINTER_MOVE，
     * 故 hover 状态不更新（B8 滞留）。route 内 SCROLL 派发完成时 scrollOffsetY 仍是旧值
     * （SceneScrolls handler 走 queueWrite，帧末 flush 才生效），route 内重算无意义；
     * 下一帧若无新事件是空帧，host 不调 route，标记永远等不到。故重算必须在 flush 之后、
     * scrollOffsetY 已生效时由 host 显式调用本方法。</p>
     *
     * <h3>协议纪律（不扩 I11）</h3>
     * <p>本方法是 Router↔host 内部协议，与 route 同级，不在 {@link SceneEventContext} 上，
     * handler 碰不到。hover 重算是 Router 内部职责（与 MOVE 触发 hover 同源），不需要外部命令。</p>
     *
     * <h3>时序</h3>
     * <p>host render：route → flush → layout → <b>reconcileHoverAfterScroll</b>。
     * 调用时 scrollOffsetY 已生效，hit-test 几何正确。hover signal 写入走 queueWrite，
     * 下一帧 flush 生效（flush 每帧无条件执行，空帧也 flush）。</p>
     *
     * <p>若本帧不含 SCROLL 事件（pendingHoverReconcile==false），直接返回无副作用。</p>
     *
     * @param root     场景树根节点
     * @param pointerX 末次指针逻辑 X 坐标（帧末粘滞，来自 SceneInputFrame.getPointerX）
     * @param pointerY 末次指针逻辑 Y 坐标（帧末粘滞，来自 SceneInputFrame.getPointerY）
     * @param absX     根节点屏幕绝对 X 偏移（沙箱传 0）
     * @param absY     根节点屏幕绝对 Y 偏移（沙箱传 0）
     */
    public void reconcileHoverAfterScroll(SceneNode root, int pointerX, int pointerY, int absX, int absY) {
        if (!pendingHoverReconcile) return;
        pendingHoverReconcile = false;
        if (root == null) return;
        // 用末次指针坐标重做 hit-test（scrollOffsetY 已生效，几何正确）
        HitResult hitResult = hitTestWithOverlays(root, pointerX, pointerY, absX, absY);
        List<SceneNode> hitChain = hitResult.chain;
        SceneNode newHover = hitChain.isEmpty() ? null : hitChain.get(hitChain.size() - 1);
        // 复用统一 hover 切换逻辑（与 POINTER_MOVE 同源）
        updateHoverFromTarget(newHover);
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

    /**
     * 执行 overlay 优先命中；overlay host 为空或 root 缺布局时自动退回主树。
     */
    private HitResult hitTestWithOverlays(SceneNode root, int canvasX, int canvasY, int rootAbsX, int rootAbsY) {
        if (overlayHost != null && !overlayHost.isEmpty()) {
            for (SceneOverlayHost.Entry entry : overlayHost.topFirst()) {
                List<SceneNode> overlayChain = hitTester.hitTest(entry.getRoot(), canvasX, canvasY,
                        entry.getAnchorX(), entry.getAnchorY());
                if (!overlayChain.isEmpty()) {
                    return new HitResult(overlayChain, entry);
                }
            }
        }
        return new HitResult(hitTester.hitTest(root, canvasX, canvasY, rootAbsX, rootAbsY), null);
    }

    /**
     * 对 pointer down 触发外部点击关闭请求；只调用 requestDismiss，不直接摘除 entry。
     */
    private void requestOutsidePointerDismiss(int canvasX,
                                              int canvasY,
                                              SceneOverlayHost.Entry hitEntry,
                                              List<SceneNode> hitChain) {
        if (overlayHost == null || overlayHost.isEmpty()) {
            return;
        }
        for (SceneOverlayHost.Entry entry : overlayHost.topFirst()) {
            if (!entry.getDismissPolicy().isDismissOnOutsidePointerDown()) {
                continue;
            }
            boolean outside = entry != hitEntry
                    && hitTester.hitTest(entry.getRoot(), canvasX, canvasY,
                    entry.getAnchorX(), entry.getAnchorY()).isEmpty()
                    && Collections.disjoint(hitChain, entry.getProtectedNodes());
            if (outside) {
                entry.requestDismiss();
            }
        }
    }

    /**
     * ESC 优先请求关闭栈顶可 ESC dismiss 的 overlay。
     *
     * @return true 表示 ESC 已被 overlay 消费，不应继续派发给主树焦点
     */
    private boolean requestTopEscapeDismiss() {
        if (overlayHost == null || overlayHost.isEmpty()) {
            return false;
        }
        for (SceneOverlayHost.Entry entry : overlayHost.topFirst()) {
            if (entry.getDismissPolicy().isDismissOnEscape()) {
                entry.requestDismiss();
                return true;
            }
        }
        return false;
    }

    /** 指针命中的节点链及其所属 overlay entry。 */
    private static final class HitResult {
        private final List<SceneNode> chain;
        private final SceneOverlayHost.Entry overlayEntry;

        private HitResult(List<SceneNode> chain, SceneOverlayHost.Entry overlayEntry) {
            this.chain = chain;
            this.overlayEntry = overlayEntry;
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

    // ==================== interactionState() 交互状态 ====================

    /**
     * 获取或创建指定节点的交互状态容器。
     *
     * <p>与 {@link #on(SceneNode, SceneEventType, SceneEventHandler)} 同构生命周期：
     * 若当前处于 {@link Owner} 作用域内，自动登记退订回调，
     * 随组件卸载一并从 {@link #interactionStates} 中移除。</p>
     *
     * <p><b>注意</b>：本方法只创建空状态容器，<b>不创建任何 signal</b>；
     * signal 在首次调用 {@link SceneInteractionState#hovered()} /
     * {@link SceneInteractionState#pressed()} 时才懒创建。</p>
     *
     * @param node 目标节点
     * @return 交互状态容器（同一节点多次调用返回同一实例）
     */
    public SceneInteractionState interactionState(SceneNode node) {
        SceneInteractionState st = interactionStates.get(node);
        if (st == null) {
            st = new SceneInteractionState();
            interactionStates.put(node, st);
            Owner owner = Owner.current();
            if (owner != null) {
                owner.onCleanup(() -> interactionStates.remove(node));
            }
        }
        return st;
    }

    // ==================== I4d 显式指针捕获 ====================

    /**
     * 请求显式指针捕获：将指定节点设为捕获目标。
     *
     * <p>捕获后 MOVE/UP/DOWN 均强制投递给 capturedNode，
     * 直至下一次 POINTER_UP 后自动释放（D7-A 最小版）。
     * 由 {@link SceneEventContext#requestPointerCapture()} 调用。</p>
     *
     * @param node 要捕获的目标节点
     */
    public void requestPointerCapture(SceneNode node) {
        this.capturedNode = node;
    }

    /**
     * 手动释放指针捕获（预留接口，供未来长期捕获用）。
     *
     * <p>当前最小版 UP 后自动释放已覆盖主流场景；
     * 此接口供需要提前释放的场景使用。</p>
     */
    public void releasePointerCapture() {
        this.capturedNode = null;
    }

    // ==================== I4a 焦点/键盘委托 ====================

    /**
     * 请求将焦点切换到指定节点（薄委托到 {@link FocusManager#requestFocus}）。
     *
     * @param node 要聚焦的节点
     * @return true 表示焦点切换成功
     */
    public boolean requestFocus(SceneNode node) {
        return focusManager.requestFocus(node);
    }

    /**
     * 将节点登记为可聚焦（薄委托到 {@link FocusManager#registerFocusable}）。
     *
     * @param node 目标节点
     */
    public void registerFocusable(SceneNode node) {
        focusManager.registerFocusable(node);
    }

    /**
     * 仅注册 focusable 不登记 onCleanup（薄委托到 {@link FocusManager#addFocusable}）。
     *
     * <p>供 {@link club.heiqi.uilib.ui.scene.runtime.SceneRuntime#focusable} 的 signal
     * 驱动重载使用，避免 effect 重跑时重复登记 cleanup。</p>
     *
     * @param node 目标节点
     */
    public void registerFocusableRaw(SceneNode node) {
        focusManager.addFocusable(node);
    }

    /**
     * 将节点从可聚焦注册表移除（薄委托到 {@link FocusManager#unregisterFocusable}）。
     *
     * <p>用于 enabled=false 时让控件退出 Tab 环（守 package-info R9「disabled 不可聚焦」）。
     * 若该节点是当前焦点，会立即清失焦点。</p>
     *
     * @param node 目标节点
     */
    public void unregisterFocusable(SceneNode node) {
        focusManager.unregisterFocusable(node);
    }

    /**
     * @return 当前焦点节点（薄委托到 {@link FocusManager#getFocusedNode}）
     */
    public SceneNode getFocusedNode() {
        return focusManager.getFocusedNode();
    }

    // ==================== I4c cursor 暴露 ====================

    /**
     * 暴露全局 cursor signal（只读），供 {@code SceneRuntime.bindCursor} 创建 cursor effect。
     *
     * <p>signal 值由 Router 在 hover 切换时写入 {@link SceneCursorResolver#resolve} 结果，
     * 走 queueWrite → 同帧末 flush 生效（I9）。</p>
     *
     * @return 全局光标样式 signal（只读）
     */
    public ReadableSignal<SceneCursor> cursorSignal() {
        return cursorSignal;
    }

    // ==================== 测试探针（包级可见性） ====================

    /**
     * @return 当前按压捕获节点（测试探针）
     */
    SceneNode __getPressedNode() {
        return pressedNode;
    }

    /**
     * @return 当前显式捕获节点（测试探针，I4d）
     */
    SceneNode __getCapturedNode() {
        return capturedNode;
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

    /**
     * @param node 目标节点
     * @return interactionStates 中是否包含该节点的状态容器（测试探针）
     */
    boolean __hasInteractionState(SceneNode node) {
        return interactionStates.containsKey(node);
    }

    /**
     * @return 当前 hover 节点（测试探针）
     */
    SceneNode __getHoveredNode() {
        return hoveredNode;
    }

    /**
     * @return interactionStates 的不可变视图（测试探针，供断言 onCleanup 回收）
     */
    Map<SceneNode, SceneInteractionState> __getInteractionStates() {
        return Collections.unmodifiableMap(interactionStates);
    }

    /**
     * @return 当前焦点节点（测试探针，委托 FocusManager）
     */
    SceneNode __getFocusedNode() {
        return focusManager.__getFocusedNode();
    }

    /**
     * @return 节点是否在 focusables 注册表中（测试探针，委托 FocusManager）
     */
    boolean __isFocusable(SceneNode node) {
        return focusManager.__isFocusable(node);
    }

    /**
     * @return FocusManager 引用（测试探针）
     */
    FocusManager __getFocusManager() {
        return focusManager;
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
            case CANCEL:      return SceneEventType.POINTER_CANCEL;
            default:          return null;
        }
    }
}
