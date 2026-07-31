package club.heiqi.uilib.ui.scene.control;

import java.util.function.Consumer;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventContext;
import club.heiqi.uilib.ui.scene.input.SceneEventHandler;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.Transform;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;

/**
 * SceneScrollbar —— scene 控件库纵向滚动条控件，叠加在可滚动视口右侧反映滚动位置。
 *
 * <h3>定位：纯派生显示控件 + 拖动/track page 交互（契约 R4 外观随状态经 bind 派生）</h3>
 * <p>滚动条不持有任何滚动位置状态——它只<b>读</b> viewport 的几何
 * （LayoutBox，只读 I11 逃生舱①）与外部传入的 {@code scrollOffsetSignal}，派生 thumb 的几何
 * （高度 + Y 偏移 + 颜色 + column 宽）并经 bind 写入节点属性。滚动位置唯一权威源是外部
 * scroll state（由 {@link club.heiqi.uilib.ui.scene.runtime.SceneScrolls#attach} 创建并维护）。</p>
 *
 * <p>Props 拆 read/write：{@code scrollOffsetSignal} 为只读显示源（可派生，如 per-section 派生），
 * {@code setScrollOffset} 为写入回调（handler 调）。拖动/track page/滚轮 handler 只调
 * {@code setScrollOffset.accept(v)}（守 I1/I11），绝不直接写节点。
 * thumb 的 hovered/pressed 交互态经 {@link SceneInteractionState} 暴露，PAINT bind 据此派生三态颜色。</p>
 *
 * <h3>结构</h3>
 * <pre>
 * column (COLUMN, preferredWidth=barWidth, fillParentHeight, bg=派生透明/trackColor, clipChildren=true, cornerRadius, hitTestable=true)
 *   └─ thumb (preferredWidth=barWidth, preferredHeight=派生, bg=派生三态色, cornerRadius,
 *             transform.translateY=派生, hitTestable=true)   ← COMPOSITE 级平移，零重排
 * </pre>
 *
 * <h3>派生几何算法</h3>
 * <ul>
 *   <li><b>content 总高</b> = viewport 可见高 + maxScrollY（{@link SceneGeometry#maxScrollY} 闭式）。</li>
 *   <li><b>thumb 高</b> = max(viewHeight² / contentHeight, minThumbHeight)；无溢出时返回 0（thumb 不可见）。</li>
 *   <li><b>thumb Y</b> = (trackHeight - thumbHeight) * (scrollOffset / maxScroll)，浮点中间量防截断，无溢出时为 0。</li>
 *   <li><b>column 宽</b>恒为 barWidth；无溢出时 track/thumb 透明，避免跨帧宽度变化扰动父级 ROW 求解。</li>
 * </ul>
 *
 * <h3>失效级别（守 I7 / I4 双轨核对）</h3>
 * <ul>
 *   <li><b>thumb 位置</b>用 {@link Transform#translate(float, float)}（COMPOSITE 级）平移，
 *       由声明 COMPOSITE 的 bind 写入——滚动时只标 compositeDirty，零重排零重绘（守信条五）。</li>
 *   <li><b>thumb 高度</b>用 {@code setPreferredHeight}（LAYOUT 级），由声明 LAYOUT 的 bind 写入。</li>
 *   <li><b>column 宽</b>固定为 barWidth，不随 overflow 状态变化。</li>
 *   <li><b>track/thumb 颜色</b>用 {@code setBackgroundColor}（PAINT 级），由声明 PAINT 的 bind 写入。</li>
 *   <li><b>订阅源</b>：LAYOUT bind（thumb 高）订阅 {@code rt.layoutDoneSignal()}；
 *       COMPOSITE bind 订阅 {@code scrollOffsetSignal} + {@code rt.layoutDoneSignal()}；
 *       PAINT bind 订阅 hovered/pressed + {@code rt.layoutDoneSignal()}。
 *       滚动时只有 COMPOSITE bind 跑，LAYOUT/PAINT bind 不跑——按需重算，守 I7。</li>
 * </ul>
 *
 * <h3>layoutDoneSignal 契约（P0 去外泄）</h3>
 * <p>scrollbar 不再要求调用方手传 layout 完成通知——直接订阅 {@link SceneRuntime#layoutDoneSignal()}。
 * host 在 post-flush 主树与 overlay 完成布局后通过 {@link SceneRuntime#__bridgeLayoutEpoch(int)}
 * 桥接最终主树 epoch，scrollbar 据此在同帧 flush 内重跑 effect 读最新 LayoutBox，
 * 消除「content 高度变化滞后一帧」缺陷。
 * 作者无需传任何 signal，外泄消除。</p>
 *
 * <h3>拖动公式（行业公式）</h3>
 * <p>拖动 thumb 时按 {@code scrollDelta = pointerDelta * (content - viewport) / (track - thumb)}
 * 反推滚动量，使 thumb 严格跟随指针。track page 点击 thumb 上/下方按 viewportHeight 翻页。</p>
 *
 * <h3>守不变量</h3>
 * <ul>
 *   <li><b>I1</b>：拖动/track handler 只 {@code setScrollOffset.accept(v)}，不直接写节点。</li>
 *   <li><b>I3</b>：create 只跑一次，bind/on 注册在 create 内。</li>
 *   <li><b>I4</b>：thumb 高 LAYOUT / track 与 thumb 颜色 PAINT / thumb Y COMPOSITE。</li>
 *   <li><b>I6</b>：paint 层只读 thumb 节点属性；effect 在数据层写 node 属性。</li>
 *   <li><b>I7</b>：滚动只触发 COMPOSITE 级 transform 变化，零重排。</li>
 *   <li><b>I11 逃生舱①</b>：effect body 与 track page handler 读 viewport/thumb LayoutBox（只读几何，不写节点、不标脏）。</li>
 * </ul>
 */
public final class SceneScrollbar {

    /**
     * 纯静态工厂，禁止实例化（强制无状态，契约 R1）。
     */
    private SceneScrollbar() {
    }

    /**
     * Scrollbar 输入契约 —— 纯只读受控源 + 视口节点引用 + 视觉常量（契约 R2）。
     *
     * <p>Props 拆 read/write：{@code scrollOffsetSignal} 为只读显示源（可派生，如 per-section 派生），
     * {@code setScrollOffset} 为写入回调（handler 调）。可选 {@code onDragStart} 在捕获拖动前收到当前
     * 显示 offset，供有平滑滚动的宿主先取消 Motion。拆分后支持 ConfigScreen 的 per-section scroll state
     * 方案——显示源为当前可见 scroll，写入回调写当前 active section 的 authority。</p>
     *
     * @param viewport      被反映滚动位置的可滚动视口节点（isScrollable==true，构建期固定引用）
     * @param scrollOffsetSignal 滚动偏移只读显示源（由 SceneScrolls.attach 创建的 signal 或其派生 Computed；
     *                       scrollbar 据此派生 thumb Y，handler 读此值做拖动起点）
     * @param setScrollOffset 滚动偏移写入回调（handler 调用此回调写 scroll state；
     *                       拖动/track page/滚轮 handler 只调此回调，守 I1）
     * @param trackColor    轨道背景色（ARGB），0 表示透明轨道
     * @param thumbColor    滑块默认态背景色（ARGB，idle 态）
     * @param barWidth      滚动条宽度（像素，建议 6-8）
     * @param minThumbHeight 滑块最小高度（像素，避免内容过多时滑块消失）
     * @param onDragStart 拖动开始回调；接收当前显示 offset，可用于取消尚未完成的平滑滚动，可为 null
     */
    @Desugar
    public record Props(
        SceneNode viewport,
        ReadableSignal<Integer> scrollOffsetSignal,
        Consumer<Integer> setScrollOffset,
        int trackColor,
        int thumbColor,
        int barWidth,
        int minThumbHeight,
        Consumer<Integer> onDragStart
    ) {
        /** 保留无需拖动接管回调的常用构造形态。 */
        public Props(SceneNode viewport,
                     ReadableSignal<Integer> scrollOffsetSignal,
                     Consumer<Integer> setScrollOffset,
                     int trackColor,
                     int thumbColor,
                     int barWidth,
                     int minThumbHeight) {
            this(viewport, scrollOffsetSignal, setScrollOffset, trackColor, thumbColor,
                    barWidth, minThumbHeight, null);
        }
    }

    /**
     * Scrollbar 创建结果，暴露列节点与滑块节点供调用方挂载与测试探针。
     *
     * @param column 滚动条列节点（调用方 appendChild 到与 viewport 同级的 ROW 容器）
     * @param thumb  滑块节点（已挂载到 column，几何由 bind 派生）
     */
    @Desugar
    public record Result(
        SceneNode column,
        SceneNode thumb
    ) {
    }

    /**
     * 计算 thumb 高度（C3 抽公共方法 + C2 浮点/long 防溢出）。
     *
     * <p>公式：{@code thumbH = vpHeight² / contentHeight}，用 long 中间量防大数溢出。
     * 无溢出（maxScroll <= 0）返回 0（B1：thumb 不可见）。
     * clamp 到 [minThumbHeight, vpHeight]。</p>
     *
     * @param vpHeight       视口可见高
     * @param maxScroll      最大滚动偏移（{@link SceneGeometry#maxScrollY}）
     * @param minThumbHeight 滑块最小高度
     * @return thumb 高度像素；无溢出返回 0
     */
    private static int computeThumbHeight(int vpHeight, int maxScroll, int minThumbHeight) {
        if (maxScroll <= 0) {
            return 0; // B1：无溢出 thumb 不可见
        }
        int contentHeight = vpHeight + maxScroll;
        // long 中间量防 vpHeight² 溢出（C2）
        long thumbH = (long) vpHeight * vpHeight / contentHeight;
        int result = (int) Math.min(thumbH, Integer.MAX_VALUE);
        if (result < minThumbHeight) {
            result = minThumbHeight;
        }
        if (result > vpHeight) {
            result = vpHeight;
        }
        return result;
    }

    /**
     * 工厂：构建 Scrollbar 组件函数。
     *
     * @param rt    场景运行时
     * @param props Scrollbar 输入契约
     * @return 创建结果（column + thumb 节点引用）
     */
    public static Result create(SceneRuntime rt, Props props) {
        int barWidth = props.barWidth();
        int radius = Math.max(1, barWidth / 2);

        // 滚动条列固定占位宽度，overflow 只控制透明度，避免父级 ROW 跨帧重新分类。
        SceneNode column = SceneNode.column();
        column.setPreferredWidth(barWidth);
        column.setFillParentHeight(true);
        column.setClipChildren(true);
        if (props.trackColor() != 0) {
            column.setBackgroundColor(props.trackColor());
        }
        column.setCornerRadius(radius);
        column.setHitTestable(true); // M2：column 可命中，注册 SCROLL handler 转发滚轮

        // 滑块：固定宽，高度/Y/颜色由 bind 派生
        SceneNode thumb = new SceneNode();
        thumb.setPreferredWidth(barWidth);
        thumb.setPreferredHeight(0); // C5：首帧初始 0，避免首帧闪烁（effect 物化后覆盖）
        thumb.setBackgroundColor(props.thumbColor());
        thumb.setCornerRadius(radius);
        thumb.setHitTestable(true); // B2：thumb 可命中，注册拖动 handler
        column.appendChild(thumb);

        // ★ 时序契约：必须在 create 阶段立即调用 hovered()/pressed() 触发懒创建，
        // 否则 Router writeHovered/writePressed 因 signal 未创建而 null 短路永远 FALSE。
        SceneInteractionState thumbState = rt.interactionState(thumb);
        ReadableSignal<Boolean> hoveredSignal = thumbState.hovered();
        ReadableSignal<Boolean> pressedSignal = thumbState.pressed();

        // M2 方案 A：column 注册 SCROLL handler，转发滚轮到 setScrollOffset。
        rt.on(column, SceneEventType.SCROLL, (ev, ctx) -> {
            int maxScroll = SceneGeometry.maxScrollY(props.viewport());
            if (maxScroll <= 0) {
                return;
            }
            int current = props.scrollOffsetSignal().get().intValue();
            int next = current - ev.getWheelDelta();
            int clamped = Math.max(0, Math.min(maxScroll, next));
            if (clamped != current) {
                props.setScrollOffset().accept(Integer.valueOf(clamped));
                ctx.stopPropagation();
            }
        });

        // ---- LAYOUT bind：thumb 高度派生（C3 公共方法 + C2 long 防溢出）----
        rt.bindComputed(() -> {
                rt.layoutDoneSignal().get();
                Object cached = props.viewport().getCachedLayout();
                if (!(cached instanceof LayoutBox)) {
                    return 0; // flush 前 layout 未跑时兜底（C5：0 不闪烁）
                }
                LayoutBox vpBox = (LayoutBox) cached;
                int vpHeight = vpBox.getHeight();
                int maxScroll = SceneGeometry.maxScrollY(props.viewport());
                return computeThumbHeight(vpHeight, maxScroll, props.minThumbHeight());
            },
            (Integer h) -> thumb.setPreferredHeight(h.intValue()));

        // ---- COMPOSITE bind：thumb Y 偏移派生（C2 浮点中间量防截断）----
        rt.bindComputed(() -> {
                int scrollOffset = props.scrollOffsetSignal().get().intValue();
                rt.layoutDoneSignal().get();
                Object cached = props.viewport().getCachedLayout();
                if (!(cached instanceof LayoutBox)) {
                    return 0f; // flush 前 layout 未跑时兜底
                }
                LayoutBox vpBox = (LayoutBox) cached;
                int vpHeight = vpBox.getHeight();
                int maxScroll = SceneGeometry.maxScrollY(props.viewport());
                if (maxScroll <= 0) {
                    return 0f; // 无溢出：thumbTop=0
                }
                int thumbH = computeThumbHeight(vpHeight, maxScroll, props.minThumbHeight());
                int trackRange = vpHeight - thumbH;
                // C2：浮点中间量，避免 int 截断导致 thumb 位置不连续
                return (float) trackRange * scrollOffset / maxScroll;
            },
            (Float y) -> thumb.setTransform(Transform.translate(0f, y.floatValue())));

        // ---- PAINT bind：track 颜色随 overflow 显隐 ----
        rt.bindComputed(() -> {
                rt.layoutDoneSignal().get();
                Object cached = props.viewport().getCachedLayout();
                if (!(cached instanceof LayoutBox)) {
                    return 0x00000000;
                }
                return SceneGeometry.maxScrollY(props.viewport()) > 0 ? props.trackColor() : 0x00000000;
            },
            (Integer c) -> column.setBackgroundColor(c.intValue()));

        // ---- PAINT bind：thumb 颜色三态派生（B1 中性灰 + hover/drag 反馈）----
        rt.__bindAnimatedColor(() -> {
                hoveredSignal.get(); // 订阅 hover
                pressedSignal.get(); // 订阅 pressed
                rt.layoutDoneSignal().get();
                Object cached = props.viewport().getCachedLayout();
                if (!(cached instanceof LayoutBox)) {
                    return props.thumbColor(); // flush 前兜底
                }
                int maxScroll = SceneGeometry.maxScrollY(props.viewport());
                if (maxScroll <= 0) {
                    return 0x00000000; // B1：无溢出透明
                }
                boolean pressed = Boolean.TRUE.equals(pressedSignal.get());
                boolean hovered = Boolean.TRUE.equals(hoveredSignal.get());
                if (pressed) {
                    return SceneChromeTokens.SCROLLBAR_THUMB_DRAG;
                }
                if (hovered) {
                    return SceneChromeTokens.SCROLLBAR_THUMB_HOVER;
                }
                return SceneChromeTokens.SCROLLBAR_THUMB_IDLE;
            },
            thumb::setBackgroundColor,
            SceneChromeTokens.MOTION_FAST_MS);

        // ---- B2：thumb 拖动 handler（行业公式，只 setScrollOffset.accept）----
        // 闭包可变状态：dragStart[0]=dragStartScrollY, dragStart[1]=dragStartPointerY（或视觉中心，见 column DOWN）
        int[] dragStart = new int[2];
        boolean[] dragging = {false};

        // BUG1 修复：thumb 因 transform 平移，hit-test 用布局位置（不含 transform），
        // scroll > 0 时用户点击 thumb 视觉位置会命中 column（thumb 布局在顶部，视觉在中间），
        // thumb DOWN handler 不触发。故 column DOWN handler 检测点击在 thumb 视觉区内时
        // 启动拖动，且 column 也注册 MOVE/UP/CANCEL handler（capture target = column 时
        // MOVE/UP 投 column，thumb handler 不触发）。两套 handler 共享 dragStart/dragging 闭包，
        // MOVE 逻辑相同，无论 capture target 是 thumb 还是 column 都正确。

        // 共享 MOVE handler：拖动中按行业公式反推滚动量。
        SceneEventHandler dragMoveHandler = (ev, ctx) -> {
            if (!dragging[0]) {
                return;
            }
            Object cached = props.viewport().getCachedLayout();
            if (!(cached instanceof LayoutBox)) {
                return;
            }
            LayoutBox vpBox = (LayoutBox) cached;
            int vpHeight = vpBox.getHeight();
            int maxScroll = SceneGeometry.maxScrollY(props.viewport());
            if (maxScroll <= 0) {
                return;
            }
            int thumbH = computeThumbHeight(vpHeight, maxScroll, props.minThumbHeight());
            int trackRange = vpHeight - thumbH;
            if (trackRange <= 0) {
                return;
            }
            // 坐标系（I12 两层）：ctx.getLocalPointerY() = 当前 capture target（thumb 或 column）局部 Y。
            // thumb layout Y=0（column 唯一子），absoluteBox(thumb,treeAbs).getY()==absoluteBox(column,treeAbs).getY()，
            // 故 thumb 局部 Y == column 局部 Y，dragStart[1] 无论 capture target 是 thumb 还是 column 都同系。
            int pointerDelta = ctx.getLocalPointerY() - dragStart[1];
            // 行业公式：scrollDelta = pointerDelta * (content - viewport) / (track - thumb)
            long contentMinusVp = (long) maxScroll; // content - viewport = maxScroll
            long scrollDelta = (long) pointerDelta * contentMinusVp / trackRange;
            long newScroll = (long) dragStart[0] + scrollDelta;
            int clamped = (int) Math.max(0, Math.min(maxScroll, newScroll));
            props.setScrollOffset().accept(Integer.valueOf(clamped));
            ctx.stopPropagation();
        };

        // 共享 UP handler：释放拖动 + 停止冒泡。
        SceneEventHandler dragUpHandler = (ev, ctx) -> {
            dragging[0] = false;
            ctx.stopPropagation();
        };

        // 共享 CANCEL handler：释放拖动（无冒泡控制，CANCEL 走专属投递块）。
        SceneEventHandler dragCancelHandler = (ev, ctx) -> {
            dragging[0] = false;
        };

        rt.on(thumb, SceneEventType.POINTER_DOWN, (SceneEvent ev, SceneEventContext ctx) -> {
            int maxScroll = SceneGeometry.maxScrollY(props.viewport());
            if (maxScroll <= 0) {
                return; // 无溢出不响应拖动
            }
            dragStart[0] = props.scrollOffsetSignal().get().intValue();
            if (props.onDragStart() != null) {
                props.onDragStart().accept(Integer.valueOf(dragStart[0]));
            }
            // delta 范式：dragStart[1] 记 thumb 局部 Y 起点，MOVE 时 pointerDelta = localY - dragStart[1]。
            // thumb 局部 Y == column 局部 Y（thumb layout Y=0），与 capture target 无关，rootAbsY≠0 不再错位。
            dragStart[1] = ctx.getLocalPointerY();
            dragging[0] = true;
            ctx.requestPointerCapture(); // 捕获指针，MOVE/UP 强制投递给 thumb
            ctx.stopPropagation();
        });

        rt.on(thumb, SceneEventType.POINTER_MOVE, dragMoveHandler);
        rt.on(thumb, SceneEventType.POINTER_UP, dragUpHandler);
        rt.on(thumb, SceneEventType.POINTER_CANCEL, dragCancelHandler);

        // ---- B2：column track page handler（点击 thumb 上/下方翻页，只 setScrollOffset.accept）----
        // BUG1：点击 thumb 视觉区内时启动拖动（而非 return），因 thumb 布局在顶部、视觉在中间，
        // hit-test 命中 column，thumb DOWN handler 不触发。column 启动拖动后 capture target = column，
        // MOVE/UP 投 column，column 的 dragMoveHandler/dragUpHandler 跑（共享闭包）。
        rt.on(column, SceneEventType.POINTER_DOWN, (SceneEvent ev, SceneEventContext ctx) -> {
            // thumb 的 DOWN handler 已 stopPropagation，点击 thumb 布局区不会冒泡到此处。
            // 此处只处理点击 track 空白区（thumb 视觉上/下方）或 thumb 视觉区（BUG1 转发拖动）。
            Object cached = props.viewport().getCachedLayout();
            if (!(cached instanceof LayoutBox)) {
                return;
            }
            LayoutBox vpBox = (LayoutBox) cached;
            int vpHeight = vpBox.getHeight();
            int maxScroll = SceneGeometry.maxScrollY(props.viewport());
            if (maxScroll <= 0) {
                return;
            }
            // 读 thumb transform 偏移（COMPOSITE 级平移）+ thumb layout 高度，得到 thumb 视觉位置。
            // hit tester 用布局位置（thumb layout Y=0），transform 不计入命中，故需手动叠加 transform 算视觉上/下界。
            // 坐标系（I12 两层）：ctx.getLocalPointerY() = column 局部 Y；thumb 局部 Y=0，transformY 即 thumb 视觉在 column 局部的 Y，同系。
            // 首帧 layout 未完成时 thumb.getCachedLayout() 可能为 null，此时无法判定 thumb 视觉边界，
            // 不启动拖动也不翻页，直接 return（与 vpBox null 守卫同范式）。
            Object thumbCached = thumb.getCachedLayout();
            if (!(thumbCached instanceof LayoutBox)) {
                return;
            }
            float thumbVisualTop = thumb.getTransform().translateY;
            float thumbVisualBottom = thumbVisualTop + ((LayoutBox) thumbCached).getHeight();
            int clickY = ctx.getLocalPointerY();
            if (clickY >= thumbVisualTop && clickY < thumbVisualBottom) {
                // BUG1：点击在 thumb 视觉区内 → 启动拖动（thumb DOWN handler 因 hit-test 几何错位未触发）。
                // delta 范式：dragStart[1] 记点击点（host 局部 Y），首帧 MOVE delta=0 → scroll 不变 → thumb 不跳跃；
                // delta 从 0 增长 → thumb 从当前位置跟随（Flutter/Compose 拖动语义）。
                // 原绝对跟随模式（校准为 thumb 视觉中心）已删除，避免与 delta 范式冲突。
                dragStart[0] = props.scrollOffsetSignal().get().intValue();
                if (props.onDragStart() != null) {
                    props.onDragStart().accept(Integer.valueOf(dragStart[0]));
                }
                dragStart[1] = clickY;
                dragging[0] = true;
                ctx.requestPointerCapture(); // capture target = column，MOVE/UP 投 column
                ctx.stopPropagation();
                return;
            }
            int current = props.scrollOffsetSignal().get().intValue();
            if (clickY < thumbVisualTop) {
                // thumb 上方 → page up
                int next = current - vpHeight;
                props.setScrollOffset().accept(Integer.valueOf(Math.max(0, next)));
            } else {
                // thumb 下方 → page down
                int next = current + vpHeight;
                props.setScrollOffset().accept(Integer.valueOf(Math.min(maxScroll, next)));
            }
            ctx.stopPropagation();
        });

        // BUG1：column 也注册 MOVE/UP/CANCEL handler，capture target = column 时接管拖动。
        rt.on(column, SceneEventType.POINTER_MOVE, dragMoveHandler);
        rt.on(column, SceneEventType.POINTER_UP, dragUpHandler);
        rt.on(column, SceneEventType.POINTER_CANCEL, dragCancelHandler);

        return new Result(column, thumb);
    }

    /**
     * 便捷重载：用默认 track/thumb 颜色 + 默认 bar 宽/最小 thumb 高构造。
     *
     * <p>消除 4 控件/demo 等调用方重复手写 7 参 Props 的样板。
     * 行为与 {@code create(rt, new Props(viewport, scrollSignal, scrollSignal::set,
     * DEFAULT_TRACK_COLOR, DEFAULT_THUMB_COLOR, DEFAULT_BAR_WIDTH, DEFAULT_MIN_THUMB_HEIGHT))} 完全等价。</p>
     *
     * @param rt           场景运行时
     * @param viewport     被反映滚动位置的可滚动视口节点（必须已 scrollable，构建期固定引用）
     * @param scrollSignal 滚动偏移 signal（{@link club.heiqi.uilib.ui.scene.runtime.SceneScrolls#attach} 返回值，
     *                     既是只读显示源，也是写入目标——{@code scrollSignal::set} 作为 handler 写入回调）
     * @return 创建结果（column + thumb 节点引用）
     */
    public static Result createDefault(SceneRuntime rt, SceneNode viewport, Signal<Integer> scrollSignal) {
        return create(rt, new Props(viewport, scrollSignal, scrollSignal::set,
            DEFAULT_TRACK_COLOR, DEFAULT_THUMB_COLOR, DEFAULT_BAR_WIDTH, DEFAULT_MIN_THUMB_HEIGHT));
    }

    /**
     * 默认滑块颜色（中性灰 idle 态，Slate-400 @ 60%）。
     */
    public static final int DEFAULT_THUMB_COLOR = SceneChromeTokens.SCROLLBAR_THUMB_IDLE;
    /**
     * 默认轨道颜色（半透明白，约 27% 不透明度，在任意底色上微亮可见）。
     */
    public static final int DEFAULT_TRACK_COLOR = 0x44FFFFFF;
    /**
     * 默认滚动条宽度（像素，M2 加宽后 8px，原 4px）。
     */
    public static final int DEFAULT_BAR_WIDTH = 8;
    /**
     * 默认滑块最小高度（像素，避免内容过多时滑块缩到不可见）。
     */
    public static final int DEFAULT_MIN_THUMB_HEIGHT = 20;
}
