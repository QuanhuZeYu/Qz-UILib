package club.heiqi.uilib.ui.scene.input;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.util.LogThrottle;

/**
 * 场景输入路由器 —— 指针/滚动/键盘/文本事件的统一路由入口，兼任 handler 注册表、
 * hover/按压/捕获交互状态与焦点/cursor 投影的权威状态机。
 *
 * <h3>核心职责</h3>
 * <ul>
 *   <li><b>外挂注册表</b>：{@code Map<SceneNode, EnumMap<SceneEventType, List<HandlerRegistration>>>}
 *       —— SceneNode 零字段，handler 全挂路由器。</li>
 *   <li><b>隐式按压捕获</b>：BUTTON_DOWN 记 pressedNode；MOVE/BUTTON_UP 期间
 *       派发目标强制为 pressedNode；BUTTON_UP 后清空。</li>
 *   <li><b>CLICK 合成</b>：UP 时基于 LCA（最近公共祖先）容差合成——DOWN 的 pressedNode
 *       与 UP 的原始命中 hitTarget 的最近公共祖先非空时，在 UP 派发完成后合成 CLICK 派发到
 *       该 LCA target+bubble 链。LCA 容差对 keyed diff 重建节点 / layout 位移 / hover 命中盒变化
 *       导致的 hitTarget 身份变化鲁棒（P1 升级，参考旧栈 DocumentClickEventDispatcher）；
 *       跨 overlay/主树（不同 paint root）时 LCA=null 不合成（浮层卸载场景正确）。</li>
 *   <li><b>hit-test → target+bubble</b>：每 POINTER 事件 hit-test 得命中链，
 *       映射 action→type，先 target 阶段再沿链向 root 反向 bubble。</li>
 *   <li><b>键盘/文本路由</b>：持有 {@link FocusManager}，key 事件投给焦点节点走 bubble；
 *       text 事件投给焦点节点；Tab 触发焦点遍历。</li>
 * </ul>
 *
 * <h3>零标脏硬不变量</h3>
 * <p>route 过程中绝不调用任何 node.setXxx()/markXxx()/appendChild/removeChild，
 * hit-test 的绝对坐标仅在遍历时临时累加绝不回写（路由全程只读，不写节点也不标脏）。</p>
 *
 * <h3>真机文本通道诊断（零行为变更）</h3>
 * <p>TEXT_INPUT 是平台文本通道的终点：事件已到达路由器却无人接收时，现象与"事件根本没来"
 * 完全一样（输入框无反应）。故本类在文本分发入口补三条互斥观测（限流，仅读注册表与焦点真值，
 * 不碰任何 setter，不扩大 handler 边界）：</p>
 * <ul>
 *   <li>无焦点目标 → warn（限流）：事件到了，但焦点为空；</li>
 *   <li>有焦点但冒泡链上无 TEXT_INPUT handler → warn（限流）：事件到了但没有控件接受；</li>
 *   <li>正常投递 → info（限流）：给出实际投递目标，作为链路正锚。</li>
 * </ul>
 */
public class SceneInputRouter {

    /**
     * 外挂 handler 注册表：SceneNode → (事件类型 → handler 列表)。
     * SceneNode 自身无 handler 字段，所有注册挂在路由器。
     */
    private final Map<SceneNode, EnumMap<SceneEventType, List<HandlerRegistration>>> registry;
    /** 单次事件固定注册上限，派发期间新增的 handler 从后续事件开始参与。 */
    private long handlerRegistrationOrder;

    /** 命中测试器（无状态，共享） */
    private final SceneHitTester hitTester;

    /** 可选浮层宿主：存在时指针命中先按 top-first 检查 overlay roots。 */
    private final SceneOverlayHost overlayHost;

    /** 真机文本通道诊断日志（与 McScreenBridge/输入源同一前缀，便于一次 grep 全链路）。 */
    private static final Logger LOG = LogManager.getLogger("QzUiLib/SceneInputRouter");

    /** TEXT_INPUT 无接收者告警限流：每 5 秒窗口最多 2 条（首两条即时可见，之后每窗口补一条）。 */
    private final LogThrottle textNoReceiverThrottle = new LogThrottle(2, 5000);

    /** TEXT_INPUT 成功投递日志限流：每 5 秒窗口最多 1 条（链路正锚，默认级别可见但不刷屏）。 */
    private final LogThrottle textDeliveryThrottle = new LogThrottle(1, 5000);

    /** 隐式按压捕获：当前按下的节点 */
    private SceneNode pressedNode;
    /** 隐式按压捕获：当前按下的按钮 */
    private SceneMouseButton pressedButton;
    /** 仅显式登记的按钮参与键盘 pressed；不从 KEY_DOWN handler 或 focusable 身份推断。 */
    private final Map<SceneNode, ReadableSignal<Boolean>> keyboardPressButtons = new HashMap<>();
    private SceneNode keyboardPressedNode;
    private final Set<SceneKey> keyboardPressedKeys = EnumSet.noneOf(SceneKey.class);
    /** 按压节点所属 overlay；null 表示主树。保留 entry 以便 anchor 变化后重算原点。 */
    private SceneOverlayHost.Entry pressedOverlayEntry;
    /** 显式指针捕获节点（requestPointerCapture 设置，UP 后自动释放） */
    private SceneNode capturedNode;
    /** 最近一次 BUTTON_DOWN 的合成点击计数（CLICK 合成透传用；UP 的 clickCount 恒 0，手势清理时重置） */
    private int lastDownClickCount;
    /** 显式捕获节点所属 overlay；null 表示主树。 */
    private SceneOverlayHost.Entry capturedOverlayEntry;

    /**
     * 交互状态：当前 hover 的节点（单节点，最深命中目标）。
     *
     * <p><b>语义 = leaf hover（现行规范：{@code docs/开发者文档/规格文档/UI投影宿主语义.md} 的
     * Input Scope 节），不是浏览器 CSS {@code :hover} 的祖先链匹配。</b>只有最深命中节点收到
     * hovered=true，其祖先不会因此收到 hover；命中链其余节点只在事件 target/bubble 阶段被派发
     * ——故 CLICK 仍能沿祖先链冒泡到容器，而 hover/pressed 不能。</p>
     *
     * <p>需要「容器级 hover」（列表行、卡片等复合交互单元的整体悬停反馈）时的正解不是改本语义，
     * 而是让纯装饰/布局子节点 {@code setHitTestable(false)} 使命中穿透到交互单元根：契约见
     * {@code ui.scene.control} package-info 的 R6；交互单元内真正的独立交互子单元（按钮/输入框）
     * 保持可命中，其自身区域不属于外层容器的 hover。</p>
     *
     * <p><b>何时才做祖先链 hover</b>：仅当出现「同一交互单元内嵌独立交互子单元、且外层仍必须有
     * 整体 hover 反馈」的真实需求、且该需求无法用命中穿透表达时，才另立「子树 hover 投影」——
     * 那会改变全库 hover 观感与 cursor/focus 派生，属公共行为变更，须先取得用户确认；在此之前
     * 本条语义即为现行规范，不做过渡实现。</p>
     */
    private SceneNode hoveredNode;

    /**
     * B8 滚动后 hover 重算标记（Router 内部状态机变量，类比 pressedNode）。
     *
     * <p>route 内检测到本帧含 SCROLL 事件时置 true；由 host 在 flush + layout 后
     * 调用 {@link #reconcileHoverAfterScroll} 消费并清零。纯内部协议状态，
     * 不在 EventContext 上，handler 碰不到。</p>
     */
    private boolean pendingHoverReconcile = false;

    /**
     * 交互状态外挂表：SceneNode → {@link SceneInteractionState}。
     *
     * <p>强引用 Map（禁止 WeakHashMap），靠 {@link Owner#onCleanup} 回收，
     * 与 handler registry 同款生命周期。</p>
     */
    private final Map<SceneNode, SceneInteractionState> interactionStates = new HashMap<>();

    /**
     * 焦点管理器：全局唯一焦点 + focusable 注册表 + Tab 遍历。
     * 构造注入 interactionStates 引用，焦点切换时通过它写 focused signal。
     */
    private final FocusManager focusManager;

    /**
     * 全局光标 signal：Router 在 hover 切换时写入解析后的 {@link SceneCursor}，
     * cursor effect 订阅它驱动 {@link CursorBackend#apply}。初始值 {@link SceneCursor#DEFAULT}。
     *
     * <p>写操作 = {@code cursorSignal.set(SceneCursorResolver.resolve(hoveredNode))}，
     * 走 queueWrite，同帧末 flush 生效（一帧一次 flush）。</p>
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
        this.registry = new HashMap<SceneNode, EnumMap<SceneEventType, List<HandlerRegistration>>>();
        this.hitTester = new SceneHitTester();
        this.overlayHost = overlayHost;
        this.pressedNode = null;
        this.pressedButton = null;
        this.hoveredNode = null;
        this.focusManager = new FocusManager(interactionStates);
        this.focusManager.setFocusChangeListener(this::dispatchFocusChange);
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

        boolean cancelled = false;
        for (ScenePointerEvent pe : frame.getPointerEvents()) {
            SceneEventType type = mapActionToType(pe.getAction());
            if (type == null) continue;

            // 指针画布逻辑坐标（不预先加 rootAbsX，整树平移完全交给 hitTester 内部处理）
            int canvasX = pe.getLogicalX();
            int canvasY = pe.getLogicalY();

            // hit-test：overlay top-first 优先；未命中时退回主树（hitTester 全程只读，不写节点也不标脏）。
            HitResult hitResult = hitTestWithOverlays(root, canvasX, canvasY, rootAbsX, rootAbsY);
            List<SceneNode> hitChain = hitResult.chain;

            if (type == SceneEventType.POINTER_DOWN) {
                requestOutsidePointerDismiss(canvasX, canvasY, rootAbsX, rootAbsY,
                        hitResult.overlayEntry, hitChain);
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
            // ★ capture 只改 dispatch effectiveTarget，绝不改 newHover = hitTarget（hover 权威真值只由 hit-test 决定）
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

            // === POINTER_CANCEL 收口：在 effectiveTarget 判定之前走专属投递块，绝不触达通用 dispatch ===
            // CANCEL 目标是 pressedNode/capturedNode，不依赖 hit-test 命中；
            // 提前处理 + continue 确保跳过通用 effectiveTarget dispatch，消除 double-dispatch。
            if (type == SceneEventType.POINTER_CANCEL) {
                cancelled = true;
                dispatchPointerCancel(pe, canvasX, canvasY, rootAbsX, rootAbsY);
                continue; // 跳过通用 effectiveTarget dispatch + DOWN/UP 块
            }

            // 指针主体：隐式聚焦/失焦 + effectiveTarget 判定 + 派发 + 按压捕获 + CLICK 合成
            dispatchPointerMain(pe, type, canvasX, canvasY, hitResult, rootAbsX, rootAbsY);

            // MOVE 派发后重解析光标：事件 handler 可能在 MOVE 中动态改写 cursor 声明
            // （如链接命中切手型），而上方的 hover 更新只在节点切换时写 cursorSignal；
            // 静止悬停时不再有 MOVE 事件，必须同帧补写才能生效（值不变时信号去重零开销）。
            if (type == SceneEventType.POINTER_MOVE) {
                cursorSignal.set(SceneCursorResolver.resolve(hoveredNode));
            }
        }

        // 保持先 pointer、再 text/key 的既有派发顺序；取消帧不留下稍后 KEY_DOWN 建立的反馈。
        try {
            dispatchKeyboardAndText(frame, root);
        } finally {
            if (cancelled) clearKeyboardPress();
        }
    }

    /**
     * POINTER_CANCEL 专属投递（取消帧收口）。
     *
     * <p>CANCEL 目标是 pressedNode/capturedNode，不依赖 hit-test 命中；
     * 在 route 中提前处理 + continue 确保跳过通用 effectiveTarget dispatch，消除 double-dispatch。</p>
     *
     * <p>CANCEL 沿捕获时锁定的 paint root 派发；overlay anchor 与 occurrence placement 在派发时重算。
     * 投递完成后写入 pressed=false 并清空所有按压/捕获状态（收口 pressedNode 因失焦而未被清理的泄漏）。</p>
     *
     * <p>零标脏：只读 interactionStates，不碰任何 SceneNode setter。</p>
     *
     * @param pe       指针事件（取 button/wheelDelta/修饰键/timeNanos）
     * @param canvasX  画布逻辑 X
     * @param canvasY  画布逻辑 Y
     * @param rootAbsX 根节点屏幕绝对 X 偏移
     * @param rootAbsY 根节点屏幕绝对 Y 偏移
     */
    private void dispatchPointerCancel(ScenePointerEvent pe, int canvasX, int canvasY,
                                       int rootAbsX, int rootAbsY) {
        SceneNode capturedAtStart = capturedNode;
        SceneNode pressedAtStart = pressedNode;
        SceneOverlayHost.Entry capturedEntryAtStart = capturedOverlayEntry;
        SceneOverlayHost.Entry pressedEntryAtStart = pressedOverlayEntry;
        Throwable firstFailure = null;

        try {
            if (capturedAtStart != null) {
                try {
                    dispatchPointerCancelTo(pe, capturedAtStart, capturedEntryAtStart,
                            canvasX, canvasY, rootAbsX, rootAbsY);
                } catch (RuntimeException | Error failure) {
                    firstFailure = appendFailure(firstFailure, failure);
                }
            }
            if (pressedAtStart != null && pressedAtStart != capturedAtStart) {
                try {
                    dispatchPointerCancelTo(pe, pressedAtStart, pressedEntryAtStart,
                            canvasX, canvasY, rootAbsX, rootAbsY);
                } catch (RuntimeException | Error failure) {
                    firstFailure = appendFailure(firstFailure, failure);
                }
            }
        } finally {
            clearKeyboardPress();
            clearPointerGestureState();
        }
        rethrowFailure(firstFailure);
    }

    /** 向单个手势目标派发 CANCEL；local 坐标按其 paint root 当前原点逐级重算。 */
    private void dispatchPointerCancelTo(ScenePointerEvent pe,
                                         SceneNode target,
                                         SceneOverlayHost.Entry overlayEntry,
                                         int canvasX,
                                         int canvasY,
                                         int rootAbsX,
                                         int rootAbsY) {
        // CANCEL 按其所属 overlay 的 s 换算（与主体 dispatch 同一口径）。
        float scale = overlayEntry == null ? 1.0F : overlayEntry.getRelativeScale();
        int dispatchX = toOverlay(canvasX, scale);
        int dispatchY = toOverlay(canvasY, scale);
        SceneEvent event = new SceneEvent(SceneEventType.POINTER_CANCEL, target, dispatchX, dispatchY,
                pe.getButton(), pe.getWheelDelta(),
                pe.isControlDown(), pe.isShiftDown(), pe.isAltDown(), pe.isMetaDown(),
                0, // clickCount：CANCEL 恒 0
                pe.getTimeNanos());
        SceneEventContext context = new SceneEventContext(this, target, dispatchX, dispatchY,
                resolveTreeAbsX(overlayEntry, rootAbsX, scale),
                resolveTreeAbsY(overlayEntry, rootAbsY, scale));
        dispatchTargetAndBubble(event, context, target);
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
     * <p><b>浮层豁免（真因 D1 修复，2026-07）</b>：DOWN 命中的是 active overlay entry 时
     * （{@code hitResult.overlayEntry != null}，即指针落在 select/autocomplete 等弹层内），
     * 即使命中链无 focusable（浮层 item 本身通常不登记 focusable），<b>也不清焦</b>——保持 opener 焦点。
     * 历史背景：autocomplete primitive 的 {@code expanded} 曾从 {@code focused} 派生（Computed），
     * 若此处 clearFocus → 帧末 flush 后 focused=false → expanded=false → 浮层卸载；
     * 真机 DOWN/UP 跨帧，UP 到达时浮层已卸载 → hitTarget != pressedNode → CLICK 不合成 → onSelect 永不触发。
     * 对比 SceneSelect：其 expanded 是独立可写 signal，trigger CLICK 显式翻转，不依赖 focused，故不受 clearFocus 影响。
     * <b>R13 重构后</b>，autocomplete 的 {@code expanded} 已改为独立可写 Signal + focused effect 驱动（守 R13），
     * 不再直接派生自 focused；但本豁免<b>保留作通用框架防御</b>——未来其他 overlay 控件若仍有 focus 耦合，
     * 或 effect 链在极端时序下出现跨帧延迟，本豁免兜底保证 overlay 内点击不掐断 opener 焦点。
     * 豁免条件严格限定为 {@code hitResult.overlayEntry != null}，不扩大到其他场景。</p>
     *
     * <p>★判定只看 hitTarget（命中真值），与 capturedNode/pressedNode 正交——失焦是焦点机制、capture 是指针机制。
     * 零标脏：clearFocus 内部 writeFocused(false)→queueWrite，focusedNode==null 时短路安全；requestFocus 同款零标脏。</p>
     *
     * <p>★N1 守卫：显式 capture 持有期抑制隐式聚焦——capture 已把指针归属锁定到 capturedNode，
     * 此时同一 DOWN 若再走隐式聚焦（命中非 focusable/树外 → clearFocus）会与 capture 投递形成相反归属，
     * capture 持有期焦点机制让位指针 capture，跳过本块。</p>
     *
     * <h3>effectiveTarget 判定（含显式指针捕获）</h3>
     * <p>显式 capture 优先 ＞ 隐式 pressedNode（MOVE/UP）＞ hitTarget。非捕获且未命中（hitTarget==null）
     * 直接 return 跳过此事件（原 route 循环中的 continue，因后续逻辑全在本方法内，return 等价）。</p>
     *
     * <h3>CLICK 合成</h3>
     * <p>UP 时基于 LCA（最近公共祖先）容差合成——DOWN 的 pressedNode 与 UP 的原始命中 hitTarget
     * 的最近公共祖先非空时，在 UP 派发完成后合成 CLICK 派发到该 LCA target+bubble 链。
     * 严格身份相等（{@code hitTarget == pressedNode}）在 keyed diff 重建节点 / layout 位移 /
     * hover 命中盒变化时会丢 CLICK（P1 真因）；LCA 容差对节点身份变化鲁棒，参考旧栈
     * {@code DocumentClickEventDispatcher#findNearestCommonInclusiveAncestor}。出界 UP
     * （hitTarget=null）或跨 overlay/主树无公共祖先时不合成。</p>
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
            } else if (hitResult.overlayEntry == null) {
                // 命中主树非 focusable 或树外(null) → 失焦
                // 豁免：命中 active overlay（浮层 item 通常非 focusable）时不清焦，保持 opener 焦点。
                // R13 重构后 autocomplete expanded 已是独立可写 Signal + effect 驱动，本豁免保留作通用框架
                // 防御（未来其他 overlay 控件 / effect 跨帧延迟兜底），历史 D1 背景见类 Javadoc。
                focusManager.clearFocus();
            }
        }

        // ===== 显式 capture 优先于隐式 pressedNode（effectiveTarget 判定） =====
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
            // ★ 后果（逃生舱边界）：指针不在任何节点上时（树外、或命中链为空的空白区）MOVE 不再
            // 派发，因此「容器用 rt.on(node, POINTER_MOVE) 接收后代冒泡来自维护『指针在子树内』」
            // 的做法收不到收尾事件——指针移出后自维护 hover 会滞留。容器级 hover 的正解是让
            // 装饰/布局子节点命中穿透（控件层契约 R6），而不是自维护一份指针状态。
            if (hitTarget == null) return;
            effectiveTarget = hitTarget;
        }

        // 构造事件（两层坐标：raw 屏幕绝对 / local 当前接收 handler 节点局部）：
        //   rawPointerX/Y = 屏幕绝对（raw，含 rootAbs），SceneEvent 只携带 raw
        //   local 由 ctx 每级 bubble 重算（rawPointer - absoluteBox(currentNode, treeAbs)）
        // overlay 命中时 treeAbs=overlay anchor，主树命中时 treeAbs=rootAbs，local 自动正确。
        SceneOverlayHost.Entry effectiveOverlayEntry = effectiveTarget == capturedNode
                ? capturedOverlayEntry
                : (effectiveTarget == pressedNode
                        && (type == SceneEventType.POINTER_MOVE || type == SceneEventType.POINTER_UP)
                        ? pressedOverlayEntry : hitResult.overlayEntry);
        // effective overlay 决定坐标空间：显式 capture / 隐式 pressedNode / 原始命中，三者的
        // entry 来源与现状一致；事件坐标按该 entry 的 s 换算到 overlay 坐标空间，s == 1.0F 恒等。
        float effectiveScale = effectiveOverlayEntry == null ? 1.0F : effectiveOverlayEntry.getRelativeScale();
        int dispatchX = toOverlay(canvasX, effectiveScale);
        int dispatchY = toOverlay(canvasY, effectiveScale);
        int treeAbsX = resolveTreeAbsX(effectiveOverlayEntry, rootAbsX, effectiveScale);
        int treeAbsY = resolveTreeAbsY(effectiveOverlayEntry, rootAbsY, effectiveScale);
        SceneEvent event = new SceneEvent(type, effectiveTarget, dispatchX, dispatchY,
                pe.getButton(), pe.getWheelDelta(),
                pe.isControlDown(), pe.isShiftDown(), pe.isAltDown(), pe.isMetaDown(),
                pe.getClickCount(),
                pe.getTimeNanos());

        try {
            // 派发：target → bubble（CANCEL 已在 route 专属块中 continue，永不触达此处）
            SceneEventContext ctx = new SceneEventContext(this, effectiveTarget,
                    dispatchX, dispatchY, treeAbsX, treeAbsY);
            dispatchTargetAndBubble(event, ctx, effectiveTarget);

            // === 按压捕获状态更新 ===
            if (type == SceneEventType.POINTER_DOWN) {
                // 记录本次 DOWN 的合成点击计数：UP 合成 CLICK 时透传（UP 的 pe.getClickCount() 恒 0）
                lastDownClickCount = pe.getClickCount();
                // 仅指针在树内命中时才记录 pressedNode（但 capturedNode 已由显式 requestPointerCapture 设置，两者独立）
                if (hitTarget != null) {
                    pressedNode = hitTarget;
                    pressedButton = pe.getButton();
                    pressedOverlayEntry = hitResult.overlayEntry;
                    publishPressed(hitTarget);
                }
            }

            if (type == SceneEventType.POINTER_UP) {
                // CLICK 合成：UP 时基于 LCA（最近公共祖先）容差合成，而非严格身份相等。
                // 旧栈 DocumentClickEventDispatcher#findNearestCommonInclusiveAncestor 先例：
                // DOWN/UP 落同一祖先链的不同后代时仍能合成 CLICK 到公共祖先。
                // 严格相等（==）在 keyed diff 重建节点 / layout 位移时会丢 CLICK（P1 真因，2026-07）。
                if (pressedNode != null && hitTarget != null) {
                    SceneNode clickTarget = resolveClickTarget(pressedNode, hitTarget);
                    if (clickTarget != null) {
                        // treeAbs 复用 hitResult.overlayEntry（与主 dispatch 同源）：
                        // LCA 必落在 pressed 与 released 的共同子树内——要么同在 overlay 内，
                        // 要么同在主树内；跨 overlay/主树（不同 paint root）时 LCA=null 不合成（浮层卸载场景正确）。
                        // 故 clickTarget 的 overlay 归属恒等于 hitTarget 的 overlay 归属，可直接复用 hitResult。
                        // CLICK 与其所属 overlay 的主体 dispatch 同口径换算（LCA 归属见上注）。
                        float clickScale = hitResult.overlayEntry == null
                                ? 1.0F : hitResult.overlayEntry.getRelativeScale();
                        int clickCanvasX = toOverlay(canvasX, clickScale);
                        int clickCanvasY = toOverlay(canvasY, clickScale);
                        int clickTreeAbsX = resolveTreeAbsX(hitResult.overlayEntry, rootAbsX, clickScale);
                        int clickTreeAbsY = resolveTreeAbsY(hitResult.overlayEntry, rootAbsY, clickScale);
                        SceneEvent clickEvent = new SceneEvent(SceneEventType.CLICK, clickTarget,
                                clickCanvasX, clickCanvasY,
                                pe.getButton(), 0, // wheelDelta=0 for CLICK
                                pe.isControlDown(), pe.isShiftDown(), pe.isAltDown(), pe.isMetaDown(),
                                lastDownClickCount, // CLICK 透传触发它的 DOWN 点击计数（双击打开等场景）
                                pe.getTimeNanos());
                        SceneEventContext clickCtx = new SceneEventContext(this, clickTarget,
                                clickCanvasX, clickCanvasY, clickTreeAbsX, clickTreeAbsY);
                        dispatchTargetAndBubble(clickEvent, clickCtx, clickTarget);
                    }
                }
            }
        } finally {
            if (type == SceneEventType.POINTER_UP) {
                clearPointerGestureState();
            }
        }
    }

    /** 终态即使 handler 抛错也必须释放隐式按压与显式 capture。 */
    private void clearPointerGestureState() {
        SceneNode oldPressed = pressedNode;
        pressedNode = null;
        publishPressed(oldPressed);
        pressedButton = null;
        pressedOverlayEntry = null;
        capturedNode = null;
        capturedOverlayEntry = null;
        lastDownClickCount = 0;
    }

    /** 内部 primitive 注册桥；只授予固定 Enter/Space 反馈行为，不暴露交互状态写入口。 */
    public InputBinding __registerButtonKeyboardPress(SceneNode node, ReadableSignal<Boolean> enabled) {
        if (node == null || enabled == null) {
            throw new IllegalArgumentException("node 与 enabled 均不可为 null");
        }
        keyboardPressButtons.put(node, enabled);
        return new InputBinding(() -> {
            keyboardPressButtons.remove(node);
            if (keyboardPressedNode == node) {
                clearKeyboardPress();
            }
        });
    }

    /** 在 handler 前更新权威状态；handler 引发的失焦/卸载可以立即清理，返回后不会复活。 */
    private void updateKeyboardPress(SceneNode target, SceneKeyEvent event) {
        SceneKey key = event.getKey();
        if (key != SceneKey.ENTER && key != SceneKey.SPACE) return;
        if (event.getAction() == SceneKeyAction.RELEASED) {
            if (keyboardPressedKeys.remove(key) && keyboardPressedKeys.isEmpty()) {
                clearKeyboardPress();
            }
            return;
        }
        ReadableSignal<Boolean> enabled = keyboardPressButtons.get(target);
        if (enabled == null || !Boolean.TRUE.equals(enabled.get())) return;
        if (keyboardPressedNode != target) {
            clearKeyboardPress();
            keyboardPressedNode = target;
        }
        keyboardPressedKeys.add(key);
        publishPressed(target);
    }

    private void clearKeyboardPress() {
        SceneNode oldPressed = keyboardPressedNode;
        keyboardPressedNode = null;
        keyboardPressedKeys.clear();
        publishPressed(oldPressed);
    }

    /** 两种输入分别拥有权威状态；释放其中一种不能抹掉另一种的按压反馈。 */
    private void publishPressed(SceneNode node) {
        if (node == null) return;
        SceneInteractionState state = interactionStates.get(node);
        if (state != null) {
            state.writePressed(node == pressedNode || node == keyboardPressedNode);
        }
    }

    /**
     * 键盘/文本分发（指针循环结束之后，先 text 后 key）。
     *
     * <p>设置当前帧根节点（供 FocusManager 做 DOM 前序遍历）后，先派发文本事件到焦点节点，
     * 再派发键盘事件；键盘事件含 ESC 优先 dismiss 与 Tab 默认焦点遍历。</p>
     *
     * @param frame 输入帧快照
     * @param root  场景树根节点
     */
    private void dispatchKeyboardAndText(SceneInputFrame frame, SceneNode root) {
        // active overlay 存在时，Tab 环只属于最顶层 paint root；否则保持主树范围。
        // 只取 topFirst 第一项，避免双 overlay 时下层浮层混入当前焦点闭环。
        SceneNode focusScope = resolveFocusScope(root);
        boolean restrictTabToFocusScope = focusScope != root;
        focusManager.setRoot(focusScope);

        // 文本分发（先于 key）
        for (SceneTextEvent te : frame.getTextEvents()) {
            SceneNode focusTarget = focusManager.getFocusedNode();
            if (focusTarget == null) {
                // 真机诊断：事件已到达路由器（平台文本通道通畅）却没有焦点目标 —— 与"事件没来"
                // 现象完全相同，必须留痕区分（限流告警，见 textNoReceiverThrottle）。
                reportTextWithoutReceiver(te, null);
                continue; // 无焦点丢弃
            }
            // 只读注册表判定接收者（不新增任何 setter 调用，不扩大 handler 边界）：TEXT_INPUT handler 可挂在
            // 焦点目标自身或任一祖先（bubble 阶段派发），故沿父链查 active handler。
            if (hasTextInputReceiver(focusTarget)) {
                reportTextDelivery(te, focusTarget);
            } else {
                reportTextWithoutReceiver(te, focusTarget);
            }
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
            updateKeyboardPress(target, ke);
            SceneEventType type = (ke.getAction() == SceneKeyAction.RELEASED)
                    ? SceneEventType.KEY_UP : SceneEventType.KEY_DOWN;
            boolean tabKeyDown = type == SceneEventType.KEY_DOWN && ke.getKey() == SceneKey.TAB;
            boolean targetOutsideTabScope = tabKeyDown && restrictTabToFocusScope
                    && target != null && !isNodeWithinScope(target, focusScope);
            if (target != null && !targetOutsideTabScope) {
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
                // 无焦点或旧焦点不属于栈顶 overlay 时，Tab 直接进入当前 scope，不向范围外派发。
                if (tabKeyDown) {
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
     * 判定焦点目标是否真能收到 TEXT_INPUT：沿自身 → 祖先链查注册表中 active 的 TEXT_INPUT handler
     * （与 {@link #dispatchTargetAndBubble} 的 target + bubble 两阶段同一口径）。
     *
     * <p>纯只读，无副作用，不改派发结果——仅为诊断提供"事件到了有没有人接"的判据。</p>
     *
     * @param target 焦点目标节点
     * @return true 表示该目标的 target+bubble 链上至少有一个 active 的 TEXT_INPUT handler
     */
    private boolean hasTextInputReceiver(SceneNode target) {
        for (SceneNode node = target; node != null; node = node.__getParent()) {
            EnumMap<SceneEventType, List<HandlerRegistration>> typeMap = registry.get(node);
            if (typeMap == null) {
                continue;
            }
            List<HandlerRegistration> handlers = typeMap.get(SceneEventType.TEXT_INPUT);
            if (handlers == null || handlers.isEmpty()) {
                continue;
            }
            for (HandlerRegistration registration : handlers) {
                if (registration.active) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * TEXT_INPUT 无接收者限流告警（真机诊断，零行为变更）。
     *
     * <p>触发条件：本帧文本事件到达且（焦点为 null 或焦点链上无 TEXT_INPUT handler）。
     * 频率上限 = {@code 2 条 / 5s}；每条自带累计条数与折叠次数。</p>
     *
     * @param te          文本事件
     * @param focusTarget 当前焦点目标（null = 无焦点）
     */
    private void reportTextWithoutReceiver(SceneTextEvent te, SceneNode focusTarget) {
        if (!textNoReceiverThrottle.allow(te.getTimeNanos())) {
            return;
        }
        long suppressed = textNoReceiverThrottle.suppressedSinceLastLog();
        if (focusTarget == null) {
            LOG.warn("[文本通道] TEXT_INPUT 丢弃：当前无焦点目标（text.len={}, 累计 {} 条, 折叠 {} 次）"
                            + " ⇒ 文本事件已到达路由器，缺的是焦点，不是平台文本通道",
                    Integer.valueOf(textLength(te)),
                    Long.valueOf(textNoReceiverThrottle.total()), Long.valueOf(suppressed));
        } else {
            LOG.warn("[文本通道] TEXT_INPUT 丢弃：焦点目标 {} 及其祖先链上没有 TEXT_INPUT handler"
                            + "（text.len={}, 累计 {} 条, 折叠 {} 次）⇒ 事件到了但没有控件接受它",
                    describeNode(focusTarget), Integer.valueOf(textLength(te)),
                    Long.valueOf(textNoReceiverThrottle.total()), Long.valueOf(suppressed));
        }
    }

    /**
     * TEXT_INPUT 成功投递限流日志（真机诊断，零行为变更）。
     *
     * <p>触发条件：文本事件到达且有接收者。频率上限 = {@code 1 条 / 5s}；本条是"文本真的进了控件"
     * 的正锚，与 {@code pushText} 到达日志配合即可定位链路断点。</p>
     *
     * @param te          文本事件
     * @param focusTarget 实际投递目标（焦点节点）
     */
    private void reportTextDelivery(SceneTextEvent te, SceneNode focusTarget) {
        if (!textDeliveryThrottle.allow(te.getTimeNanos())) {
            return;
        }
        LOG.info("[文本通道] TEXT_INPUT 投递: 目标={}（text.len={}, 累计投递 {} 条, 折叠 {} 次）",
                describeNode(focusTarget), Integer.valueOf(textLength(te)),
                Long.valueOf(textDeliveryThrottle.total()),
                Long.valueOf(textDeliveryThrottle.suppressedSinceLastLog()));
    }

    /** 文本长度（诊断日志用；text 契约上非 null，此处仍防御，确保日志路径永不抛异常）。 */
    private static int textLength(SceneTextEvent te) {
        String text = te.getText();
        return text == null ? 0 : text.length();
    }

    /** 节点的日志标识（类型 + 身份哈希，便于与控件侧日志对照，无需 toString 契约）。 */
    private static String describeNode(SceneNode node) {
        if (node == null) {
            return "null";
        }
        String type = node.getClass().getSimpleName();
        return (type == null || type.isEmpty() ? "(匿名节点)" : type)
                + "@" + Integer.toHexString(System.identityHashCode(node));
    }

    /** 焦点 authority 切换后同步派发；focused signal 仍按原契约延迟到 flush。 */
    private void dispatchFocusChange(SceneNode oldFocus, SceneNode newFocus) {
        if (oldFocus == keyboardPressedNode) {
            clearKeyboardPress();
        }
        Throwable firstFailure = null;
        if (oldFocus != null) {
            try {
                dispatchFocusEvent(SceneEventType.FOCUS_LOST, oldFocus);
            } catch (RuntimeException | Error failure) {
                firstFailure = appendFailure(firstFailure, failure);
            }
        }
        if (newFocus != null && focusManager.getFocusedNode() == newFocus) {
            try {
                dispatchFocusEvent(SceneEventType.FOCUS_GAINED, newFocus);
            } catch (RuntimeException | Error failure) {
                firstFailure = appendFailure(firstFailure, failure);
            }
        }
        rethrowFailure(firstFailure);
    }

    private void dispatchFocusEvent(SceneEventType type, SceneNode target) {
        SceneEvent event = new SceneEvent(type, target, 0, 0, SceneMouseButton.NONE, 0,
                false, false, false, false, 0, 0L);
        SceneEventContext context = new SceneEventContext(this, target, 0, 0, 0, 0);
        dispatchTargetAndBubble(event, context, target);
    }

    /** 保留首个派发异常，并把后续异常挂为 suppressed。 */
    private static Throwable appendFailure(Throwable firstFailure, Throwable failure) {
        if (firstFailure == null) {
            return failure;
        }
        if (firstFailure != failure) {
            firstFailure.addSuppressed(failure);
        }
        return firstFailure;
    }

    /** 重抛派发路径允许捕获的 unchecked failure。 */
    private static void rethrowFailure(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        throw (Error) failure;
    }

    /** 与真实键盘派发共用 active overlay 优先的 Tab scope 解析。 */
    private SceneNode resolveFocusScope(SceneNode root) {
        if (overlayHost != null && !overlayHost.isEmpty()) {
            List<SceneOverlayHost.Entry> overlays = overlayHost.topFirst();
            if (!overlays.isEmpty()) {
                return overlays.get(0).getRoot();
            }
        }
        return root;
    }

    /**
     * 判断节点是否属于指定焦点遍历根，以真实父链为准，不依赖 focusable/handler 注册表猜测。
     *
     * @param node  待判断节点
     * @param scope 当前帧焦点遍历根
     * @return 节点等于 scope 或其祖先链包含 scope 时返回 true
     */
    private boolean isNodeWithinScope(SceneNode node, SceneNode scope) {
        for (SceneNode current = node; current != null; current = current.__getParent()) {
            if (current == scope) {
                return true;
            }
        }
        return false;
    }

    /**
     * 统一 hover 切换逻辑：MOVE 分支与 {@link #reconcileHoverAfterScroll} 共用。
     *
     * <p>给定本帧最深命中目标 newHover（可能 null），与当前 hoveredNode 比较：
     * 不同则对旧节点 writeHovered(false)、新节点 writeHovered(true)，更新 hoveredNode，
     * 并写 cursorSignal（均走 queueWrite，同帧末 flush 生效，一帧一次 flush）。</p>
     *
     * <p>零标脏：只读 interactionStates / cursorSignal，不碰任何 SceneNode setter。
     * capture 只改 dispatch effectiveTarget，绝不改 newHover（hover 权威真值只由 hit-test 决定）。</p>
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
            // hover 切换后更新全局 cursor signal（queueWrite，同帧末 flush 生效）
            cursorSignal.set(SceneCursorResolver.resolve(hoveredNode));
        }
    }

    /** 标记下一次 host 帧末按最新滚动几何重算 hover；供跨帧平滑滚动内部桥调用。 */
    public void __requestHoverReconcileAfterScroll() {
        __requestHoverReconcile();
    }

    /** internal：输入门禁或几何变化后，标记下一次 host 帧末重算 hover。 */
    public void __requestHoverReconcile() {
        pendingHoverReconcile = true;
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
     * <h3>协议纪律（不扩大 handler 边界）</h3>
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
     * 解析 CLICK 合成目标：返回 pressed 与 released 的最近公共祖先（LCA，含身）。
     *
     * <p>复刻旧栈 {@code DocumentClickEventDispatcher#findNearestCommonInclusiveAncestor} 算法：
     * 沿 pressed 祖先链向上，找第一个也在 released 祖先链上的节点。DOWN/UP 落同一祖先链的
     * 不同后代时（如 keyed diff 重建节点、layout 位移、hover 命中盒变化导致 hitTarget 身份变化），
     * 仍能合成 CLICK 到公共祖先，避免严格身份相等（{@code ==}）造成的 CLICK 丢失。</p>
     *
     * <p><b>边界</b>：pressed/released 为 null 返回 null；跨 overlay/主树（不同 paint root，
     * 无公共祖先）返回 null——这正是浮层卸载场景的正确行为（浮层已卸载则无公共祖先，不合成）。</p>
     *
     * @param pressed  DOWN 时记录的按压节点（pressedNode）
     * @param released UP 时原始命中节点（hitTarget）
     * @return 最近公共祖先；无公共祖先返回 null
     */
    private SceneNode resolveClickTarget(SceneNode pressed, SceneNode released) {
        if (pressed == null || released == null) {
            return null;
        }
        // 建立 released 祖先链集合（含自身）
        Set<SceneNode> releasedAncestors = new HashSet<SceneNode>();
        for (SceneNode n = released; n != null; n = n.__getParent()) {
            releasedAncestors.add(n);
        }
        // 沿 pressed 祖先链向上找第一个在 releasedAncestors 里的节点
        for (SceneNode n = pressed; n != null; n = n.__getParent()) {
            if (releasedAncestors.contains(n)) {
                return n;
            }
        }
        return null;
    }

    /**
     * 执行 target + bubble 两阶段派发。
     *
     * <p>target 阶段：派发到 effectiveTarget；bubble 阶段：沿 parent 链向 root
     * 逐级派发。任一阶段 handler 调用 {@code ctx.stopPropagation()} 后，
     * 不再向更上层祖先派发（但当前节点已注册的多 handler 仍全部跑完）。</p>
     */
    private void dispatchTargetAndBubble(SceneEvent event, SceneEventContext ctx, SceneNode target) {
        long registrationLimit = handlerRegistrationOrder;
        // target 阶段
        ctx.setCurrentNode(target);
        dispatchToNode(event, ctx, target, registrationLimit);
        if (ctx.isPropagationStopped()) return;

        // bubble 阶段：沿父链向上逐级派发
        SceneNode current = target.__getParent();
        while (current != null && !ctx.isPropagationStopped()) {
            ctx.setCurrentNode(current);
            dispatchToNode(event, ctx, current, registrationLimit);
            current = current.__getParent();
        }
    }

    /**
     * 向指定节点派发事件：遍历该节点上注册的该类型所有 handler。
     */
    private void dispatchToNode(SceneEvent event, SceneEventContext ctx, SceneNode node, long registrationLimit) {
        EnumMap<SceneEventType, List<HandlerRegistration>> typeMap = registry.get(node);
        if (typeMap == null) return;
        List<HandlerRegistration> handlers = typeMap.get(event.getType());
        if (handlers == null || handlers.isEmpty()) return;
        // 同步卸载/退订可修改原列表；快照固定遍历项，active 排除事件中途已退订的项。
        // stopPropagation 只阻止祖先派发，当前节点其余有效 handler 仍执行。
        for (HandlerRegistration registration : handlers.toArray(new HandlerRegistration[0])) {
            if (registration.active && registration.order <= registrationLimit) {
                registration.handler.handle(event, ctx);
            }
        }
    }

    /**
     * 把画布逻辑坐标换算到指定 overlay 的坐标空间（÷ s）。
     *
     * <p>s == 1.0F 原样返回，保持既有 overlay 路径逐位等价；s != 1.0F 统一用
     * {@link Math#round(float)}，与渲染侧（pipeline 布局约束与回放原点）同函数同取整——
     * 一处 round 一处 ceil 会让命中与实绘错位。</p>
     *
     * @param value 画布逻辑坐标（或画布逻辑偏移量）
     * @param scale 该 overlay 的相对倍率 s（1.0F = 跟随宿主）
     */
    private static int toOverlay(int value, float scale) {
        return Float.compare(scale, 1F) == 0 ? value : Math.round(value / scale);
    }

    /**
     * 执行 overlay 优先命中；overlay host 为空或 root 缺布局时自动退回主树。
     *
     * <p>每个 entry 用自身 s 把画布逻辑坐标换算到该 overlay 的坐标空间后再命中；
     * s == 1.0F 时换算为恒等，行为与既有实现逐位一致。</p>
     */
    private HitResult hitTestWithOverlays(SceneNode root, int canvasX, int canvasY, int rootAbsX, int rootAbsY) {
        if (overlayHost != null && !overlayHost.isEmpty()) {
            for (SceneOverlayHost.Entry entry : overlayHost.topFirst()) {
                float scale = entry.getRelativeScale();
                List<SceneNode> overlayChain = hitTester.hitTest(entry.getRoot(),
                        toOverlay(canvasX, scale), toOverlay(canvasY, scale),
                        toOverlay(rootAbsX + entry.getAnchorX(), scale),
                        toOverlay(rootAbsY + entry.getAnchorY(), scale));
                if (!overlayChain.isEmpty()) {
                    return new HitResult(overlayChain, entry);
                }
            }
        }
        return new HitResult(hitTester.hitTest(root, canvasX, canvasY, rootAbsX, rootAbsY), null);
    }

    /**
     * <b>只读命中探针</b>：报出「在这一点按下，事件会沿哪条链派发」（root→最深目标）。
     *
     * <p>存在的理由：诊断侧（headless 树投影）要如实回答「点这个节点的中心，事件到不到它」。
     * 这件事只有命中测试能回答 —— 任何几何近似都不等价：被滚动容器裁掉的目标、被模态遮罩盖住的
     * 目标，几何上都「有尺寸、也没有更深的可点后代」，唯独点下去到不了自己。让诊断侧自拼一套判据
     * 等于复制命中语义，且必然与真实派发漂移（独立复核抓到 4 类反例）。</p>
     *
     * <p><b>坐标口径（承重）</b>：{@code localX/localY} 是<b>目标树自己的坐标空间</b>，不是画布坐标。
     * 主树的空间即画布空间；浮层则有自己的锚点偏移与相对倍率，其局部 {@code (0,0)} 不在画布原点。
     * 故调用方只说「在<b>这棵树</b>的这一点」，换算由本方法按 {@code resolveTreeAbsX/Y} 完成 ——
     * 那与真实派发用的是同一处换算。让调用方自己算画布坐标会漏掉锚点，实测表现是：锚定浮层内
     * 每个菜单项都被误报为「点不到」（原始实现只传 {@code 0,0} 当整树平移量）。</p>
     *
     * <p>返回<b>整条链</b>而非最深节点：链上成员资格就是「事件会冒泡到它」的判据，而链尾是「谁是
     * 本次点击的目标」。两者都是投影要报的事实，少一个就得再算一次。</p>
     *
     * <p>零副作用沿用 {@link SceneHitTester} 的硬不变量（只读不写、不标脏），可安全用于查询路径。</p>
     *
     * @param root   主树根；可为 null（浮层由本路由器按 top-first 优先检查，不依赖主树）
     * @param tree   坐标所属的树根：传主树根表示画布坐标；传某个浮层根表示该浮层的局部坐标
     * @param localX 目标树坐标空间下的 X
     * @param localY 目标树坐标空间下的 Y
     * @return 命中链（root→最深目标）；未命中返回空表
     */
    public List<SceneNode> __probeHitChain(SceneNode root, SceneNode tree, int localX, int localY) {
        int[] canvas = __toCanvasPoint(tree, localX, localY);
        return hitTestWithOverlays(root, canvas[0], canvas[1], 0, 0).chain;
    }

    /**
     * <b>局部坐标 → 画布坐标</b>（浮层换算的唯一实现）。
     *
     * <p>{@code node} 所属的树若是浮层根，其坐标空间被锚点与相对倍率平移缩放；主树（或不属于任何
     * 浮层的节点）的坐标空间即画布空间。这与 {@code SceneFramePipeline.toHostLogicalBox} 是同一口径，
     * 是 {@link #toOverlay} 的逆：画布 = 局部 × s + 锚点。</p>
     *
     * <p>存在的理由：命中探针与「按地址取坐标」都要做这次换算。两处各写一份的结果是
     * 其中一处漏掉锚点 —— 实测表现是锚定浮层里的菜单项全部被报成「点不到」。故收敛到这一个方法。</p>
     *
     * @param node   坐标所属的节点；null = 按主树处理
     * @param localX 局部坐标 X
     * @param localY 局部坐标 Y
     * @return 画布坐标 {@code [x, y]}
     */
    public int[] __toCanvasPoint(SceneNode node, int localX, int localY) {
        int[] box = __toCanvasBox(node, localX, localY, 0, 0);
        return new int[] {box[0], box[1]};
    }

    /**
     * <b>局部盒 → 画布盒</b>（浮层换算的唯一实现）：返回 {@code [x, y, w, h]}。
     *
     * <p>宽高同样按相对倍率缩放（渲染侧就是按 s 放大的），漏掉它会让浮层里报出的尺寸偏小。</p>
     *
     * @param node   坐标所属的节点；null = 按主树处理
     * @param localX 局部 X
     * @param localY 局部 Y
     * @param width  局部宽
     * @param height 局部高
     * @return 画布坐标盒 {@code [x, y, w, h]}
     */
    public int[] __toCanvasBox(SceneNode node, int localX, int localY, int width, int height) {
        SceneOverlayHost.Entry entry = findOverlayEntryForNode(node);
        if (entry == null) {
            return new int[] {localX, localY, width, height};
        }
        float scale = entry.getRelativeScale();
        if (Float.compare(scale, 1F) == 0) {
            return new int[] {localX + entry.getAnchorX(), localY + entry.getAnchorY(), width, height};
        }
        return new int[] {Math.round(localX * scale) + entry.getAnchorX(),
                Math.round(localY * scale) + entry.getAnchorY(),
                Math.round(width * scale), Math.round(height * scale)};
    }

    /**
     * 将 occurrence placement 与当前 overlay anchor 合成为派发树绝对原点。
     *
     * <p>overlay 命中时同一按 s 换算（overlay 坐标空间 = 画布逻辑 ÷ s）；主树 entry == null
     * 与 s == 1.0F 时换算恒等，保持既有路径逐位一致。</p>
     */
    private static int resolveTreeAbsX(SceneOverlayHost.Entry entry, int rootAbsX, float scale) {
        return entry == null ? rootAbsX : toOverlay(rootAbsX + entry.getAnchorX(), scale);
    }

    private static int resolveTreeAbsY(SceneOverlayHost.Entry entry, int rootAbsY, float scale) {
        return entry == null ? rootAbsY : toOverlay(rootAbsY + entry.getAnchorY(), scale);
    }

    /** 捕获时锁定节点所属 paint root；entry 即使随后摘除仍保留最后一个有效 anchor。 */
    private SceneOverlayHost.Entry findOverlayEntryForNode(SceneNode node) {
        if (node == null || overlayHost == null || overlayHost.isEmpty()) {
            return null;
        }
        for (SceneOverlayHost.Entry entry : overlayHost.topFirst()) {
            if (isNodeWithinScope(node, entry.getRoot())) {
                return entry;
            }
        }
        return null;
    }

    /**
     * 对 pointer down 触发外部点击关闭请求；只调用 requestDismiss，不直接摘除 entry。
     */
    private void requestOutsidePointerDismiss(int canvasX,
                                              int canvasY,
                                              int rootAbsX,
                                              int rootAbsY,
                                              SceneOverlayHost.Entry hitEntry,
                                              List<SceneNode> hitChain) {
        if (overlayHost == null || overlayHost.isEmpty()) {
            return;
        }
        for (SceneOverlayHost.Entry entry : overlayHost.topFirst()) {
            if (!entry.getDismissPolicy().isDismissOnOutsidePointerDown()) {
                continue;
            }
            // 与 overlay 优先命中共用同一换算：命中链在 overlay 自己坐标空间内判定，
            // protectedNodes 仍是链身份比对，无需坐标。
            float scale = entry.getRelativeScale();
            boolean outside = entry != hitEntry
                    && hitTester.hitTest(entry.getRoot(), toOverlay(canvasX, scale), toOverlay(canvasY, scale),
                    toOverlay(rootAbsX + entry.getAnchorX(), scale),
                    toOverlay(rootAbsY + entry.getAnchorY(), scale)).isEmpty()
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

    /** 每次注册独立拥有身份与存活态，同一 handler 对象可独立注册/退订多次。 */
    private static final class HandlerRegistration {
        private final SceneEventHandler handler;
        private final long order;
        private boolean active = true;

        private HandlerRegistration(SceneEventHandler handler, long order) {
            this.handler = handler;
            this.order = order;
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
        EnumMap<SceneEventType, List<HandlerRegistration>> typeMap = registry.get(node);
        if (typeMap == null) {
            typeMap = new EnumMap<SceneEventType, List<HandlerRegistration>>(SceneEventType.class);
            registry.put(node, typeMap);
        }
        final EnumMap<SceneEventType, List<HandlerRegistration>> finalTypeMap = typeMap;

        List<HandlerRegistration> handlers = typeMap.get(type);
        if (handlers == null) {
            handlers = new ArrayList<HandlerRegistration>();
            typeMap.put(type, handlers);
        }
        HandlerRegistration registration = new HandlerRegistration(handler, ++handlerRegistrationOrder);
        handlers.add(registration);

        // 退订 Runnable（捕获 final 引用确保编译通过）
        Runnable disposeRunnable = new Runnable() {
            @Override
            public void run() {
                if (!registration.active) return;
                registration.active = false;
                List<HandlerRegistration> list = finalTypeMap.get(type);
                if (list != null) {
                    list.remove(registration);
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
                owner.onCleanup(() -> {
                    if (keyboardPressedNode == node) {
                        clearKeyboardPress();
                    }
                    SceneInteractionState removed = interactionStates.remove(node);
                    if (removed != null) removed.writePressed(false);
                });
            }
        }
        return st;
    }

    // ==================== 显式指针捕获 ====================

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
        this.capturedOverlayEntry = findOverlayEntryForNode(node);
    }

    /**
     * 手动释放指针捕获（预留接口，供未来长期捕获用）。
     *
     * <p>当前最小版 UP 后自动释放已覆盖主流场景；
     * 此接口供需要提前释放的场景使用。</p>
     */
    public void releasePointerCapture() {
        this.capturedNode = null;
        this.capturedOverlayEntry = null;
    }

    // ==================== 焦点/键盘委托 ====================

    /**
     * 请求将焦点切换到指定节点（薄委托到 {@link FocusManager#requestFocus}）。切换会同步派发
     * {@link SceneEventType#FOCUS_LOST}/{@link SceneEventType#FOCUS_GAINED}，focused signal 仍延迟到 flush。
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

    // ==================== cursor 暴露 ====================

    /**
     * 暴露全局 cursor signal（只读），供 {@code SceneRuntime.bindCursor} 创建 cursor effect。
     *
     * <p>signal 值由 Router 在 hover 切换时写入 {@link SceneCursorResolver#resolve} 结果，
     * 走 queueWrite → 同帧末 flush 生效（一帧一次 flush）。</p>
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
     * @return 当前显式捕获节点（测试探针）
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
        EnumMap<SceneEventType, List<HandlerRegistration>> typeMap = registry.get(node);
        if (typeMap == null) return 0;
        List<HandlerRegistration> handlers = typeMap.get(type);
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
