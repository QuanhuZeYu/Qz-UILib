package club.heiqi.uilib.ui.scene.control;

import java.util.function.Consumer;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Effect;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventContext;
import club.heiqi.uilib.ui.scene.input.SceneEventHandler;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.layout.AlignSelf;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.Transform;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;


public final class SceneScrollbar {


    private SceneScrollbar() {
    }


    @Desugar
    public record Props(
        SceneNode viewport,
        ReadableSignal<Integer> scrollOffsetSignal,
        Consumer<Integer> setScrollOffset,
        int trackColor,
        int thumbColor,
        int barWidth,
        int minThumbHeight,
        Consumer<Integer> onDragStart,
        Integer hoverColor,
        Integer dragColor,
        int hitBandWidth,
        int thumbVisualWidth,
        ReadableSignal<Integer> barWidthSignal
    ) {
        /**
         * 保留 12 参旧 canonical 签名（宽度信号缺省 = 常量 {@code barWidth}，逐值不变）。
         *
         * <p>P5 第六轮 U-P5-13（滚动条宽度动态派生）为纯加法：既有调用方源码零改动，
         * 行为与「barWidthSignal == null」完全一致。</p>
         */
        public Props(SceneNode viewport,
                     ReadableSignal<Integer> scrollOffsetSignal,
                     Consumer<Integer> setScrollOffset,
                     int trackColor,
                     int thumbColor,
                     int barWidth,
                     int minThumbHeight,
                     Consumer<Integer> onDragStart,
                     Integer hoverColor,
                     Integer dragColor,
                     int hitBandWidth,
                     int thumbVisualWidth) {
            this(viewport, scrollOffsetSignal, setScrollOffset, trackColor, thumbColor,
                    barWidth, minThumbHeight, onDragStart, hoverColor, dragColor,
                    hitBandWidth, thumbVisualWidth, null);
        }

        /** 保留无需拖动接管回调的常用构造形态。 */
        public Props(SceneNode viewport,
                     ReadableSignal<Integer> scrollOffsetSignal,
                     Consumer<Integer> setScrollOffset,
                     int trackColor,
                     int thumbColor,
                     int barWidth,
                     int minThumbHeight) {
            this(viewport, scrollOffsetSignal, setScrollOffset, trackColor, thumbColor,
                    barWidth, minThumbHeight, null, null, null, 0, 0);
        }

        /** 保留 8 参形态（含 onDragStart，无自定义三态色/命中带/可视宽）。 */
        public Props(SceneNode viewport,
                     ReadableSignal<Integer> scrollOffsetSignal,
                     Consumer<Integer> setScrollOffset,
                     int trackColor,
                     int thumbColor,
                     int barWidth,
                     int minThumbHeight,
                     Consumer<Integer> onDragStart) {
            this(viewport, scrollOffsetSignal, setScrollOffset, trackColor, thumbColor,
                    barWidth, minThumbHeight, onDragStart, null, null, 0, 0);
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
        // 宽度权威源：声明了宽度信号 ⇒ 取信号当前值（≤0/null 回退常量）；否则常量（逐值不变）。
        ReadableSignal<Integer> barWidthSignal = props.barWidthSignal();
        int barWidth = resolveBarWidth(barWidthSignal, props.barWidth());
        // 命中带宽：0 = 缺省用 barWidth（旧行为）；非 0 时 column 加宽为隐性命中带，
        // 可视滑块贴右缘（thumb AlignSelf.END），命中带向左扩出。显式覆盖优先于宽度信号。
        int hitBandWidth = props.hitBandWidth() > 0 ? props.hitBandWidth() : barWidth;
        // 可视滑块宽：0 = 缺省用 barWidth（旧行为）。显式覆盖优先于宽度信号。
        int thumbVisualWidth = props.thumbVisualWidth() > 0 ? props.thumbVisualWidth() : barWidth;
        int radius = Math.max(1, barWidth / 2);

        // ---- 默认配色：主题语义色派生（显式色值完全覆盖主题）----
        // 构造期捕获来源主题信号（此处 Owner.current() 是来源作用域）；派生期只读这些信号，
        // 主题切换自动重算、不重建节点。仅在参数是「跟随主题」时才构造派生信号：
        // 显式色路径零主题依赖，优先级仍是「显式 > 主题」。
        boolean thumbFollowsTheme = props.thumbColor() == THEME_COLOR;
        boolean trackFollowsTheme = props.trackColor() == THEME_COLOR;
        // idle/track 共用同一 mutedForeground 派生（同一主题色只派生一次）。
        ReadableSignal<Integer> themeMuted = (thumbFollowsTheme || trackFollowsTheme)
                ? SceneThemes.mutedForeground(rt) : null;
        // hover/drag 需要更高强度：只在对应显式色为 null 时构造 foreground 派生。
        ReadableSignal<Integer> themeStrong = (props.hoverColor() == null || props.dragColor() == null)
                ? SceneThemes.foreground(rt) : null;

        // 滚动条列固定占位宽度（命中带宽），overflow 只控制透明度，避免父级 ROW 跨帧重新分类。
        SceneNode column = SceneNode.column();
        column.setPreferredWidth(hitBandWidth);
        column.setFillParentHeight(true);
        column.setClipChildren(true);
        // 首帧色：显式值原样，主题派生值取构造期已求值的派生初值（flush 前即有可读色，不写哨兵）。
        int initialTrackColor = trackFollowsTheme
                ? withAlpha(themeMuted.get().intValue(), TRACK_TINT_ALPHA)
                : props.trackColor();
        if (initialTrackColor != 0) {
            column.setBackgroundColor(initialTrackColor);
        }
        column.setCornerRadius(radius);
        column.setHitTestable(true); // M2：column 可命中，注册 SCROLL handler 转发滚轮

        // 滑块：固定可视宽，高度/Y/颜色由 bind 派生。
        // AlignSelf.END：COLUMN 交叉轴（水平）末端对齐 → 可视滑块贴右缘，
        // column 左扩部分（hitBandWidth - thumbVisualWidth）为隐形命中带。
        SceneNode thumb = new SceneNode();
        thumb.setPreferredWidth(thumbVisualWidth);
        thumb.setPreferredHeight(0); // C5：首帧初始 0，避免首帧闪烁（effect 物化后覆盖）
        thumb.setBackgroundColor(thumbFollowsTheme
                ? withAlpha(themeMuted.get().intValue(), THUMB_IDLE_ALPHA)
                : props.thumbColor());
        thumb.setCornerRadius(radius);
        thumb.setAlignSelf(AlignSelf.END);
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

        // ---- PAINT bind：track 颜色随 overflow 显隐（默认取主题极淡 tint）----
        rt.bindComputed(() -> {
                rt.layoutDoneSignal().get();
                Object cached = props.viewport().getCachedLayout();
                if (!(cached instanceof LayoutBox)) {
                    return 0x00000000;
                }
                if (SceneGeometry.maxScrollY(props.viewport()) <= 0) {
                    return 0x00000000;
                }
                return trackFollowsTheme
                        ? withAlpha(themeMuted.get().intValue(), TRACK_TINT_ALPHA)
                        : props.trackColor();
            },
            (Integer c) -> column.setBackgroundColor(c.intValue()));

        // ---- PAINT bind：thumb 颜色三态派生（默认主题派生 + hover/drag 更强反馈）----
        rt.__bindAnimatedColor(() -> {
                hoveredSignal.get(); // 订阅 hover
                pressedSignal.get(); // 订阅 pressed
                rt.layoutDoneSignal().get();
                Object cached = props.viewport().getCachedLayout();
                // idle 色：主题派生（mutedForeground + 中性 alpha）或显式色（chat3 传入设计令牌）。
                int idleColor = thumbFollowsTheme
                        ? withAlpha(themeMuted.get().intValue(), THUMB_IDLE_ALPHA)
                        : props.thumbColor();
                if (!(cached instanceof LayoutBox)) {
                    return idleColor; // flush 前兜底
                }
                int maxScroll = SceneGeometry.maxScrollY(props.viewport());
                if (maxScroll <= 0) {
                    return 0x00000000; // B1：无溢出透明
                }
                boolean pressed = Boolean.TRUE.equals(pressedSignal.get());
                boolean hovered = Boolean.TRUE.equals(hoveredSignal.get());
                if (pressed) {
                    return props.dragColor() != null ? props.dragColor().intValue()
                            : withAlpha(themeStrong.get().intValue(), THUMB_DRAG_ALPHA);
                }
                if (hovered) {
                    return props.hoverColor() != null ? props.hoverColor().intValue()
                            : withAlpha(themeStrong.get().intValue(), THUMB_HOVER_ALPHA);
                }
                return idleColor;
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
            // 坐标系（两层：raw 屏幕绝对 / local 当前节点局部）：ctx.getLocalPointerY() = 当前 capture target（thumb 或 column）局部 Y。
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
            // 坐标系（两层：raw 屏幕绝对 / local 当前节点局部）：ctx.getLocalPointerY() = column 局部 Y；thumb 局部 Y=0，transformY 即 thumb 视觉在 column 局部的 Y，同系。
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

        // ---- LAYOUT bind：宽度信号派生（P5 第六轮 U-P5-13，滚动条宽度动态化）----
        // 只重派生几何，不重建控件：写入 column 命中带宽（column 的 preferredWidth 即指针可达面）、
        // thumb 可视宽、两处圆角（半径 = barWidth/2 与宽度同源）。拖动/track 判定不缓存宽度
        // （handler 每次读节点盒与 transform），故宽度变化后命中面与拖动判定天然同步。
        // 有界：一个 create 只建一条 effect（create 只跑一次），信号重复变化不新增订阅。
        // 显式 hitBandWidth/thumbVisualWidth 覆盖优先（与 create 期同口径），不被信号抹掉。
        if (barWidthSignal != null) {
            rt.bind(barWidthSignal, w -> Effect.untrack(() -> {
                int derived = resolveBarWidth(barWidthSignal, props.barWidth());
                column.setPreferredWidth(props.hitBandWidth() > 0 ? props.hitBandWidth() : derived);
                thumb.setPreferredWidth(props.thumbVisualWidth() > 0 ? props.thumbVisualWidth() : derived);
                int derivedRadius = Math.max(1, derived / 2);
                column.setCornerRadius(derivedRadius);
                thumb.setCornerRadius(derivedRadius);
            }));
        }

        return new Result(column, thumb);
    }

    /**
     * 解析生效滚动条宽度：信号缺省（null）或值 ≤ 0/null 时回退常量 {@code fallback}。
     *
     * <p>「0 = 缺省」与 {@code hitBandWidth}/{@code thumbVisualWidth} 的既有缺省约定同口径；
     * 信号存在但读数不可用时同样回退常量，避免出现零宽滚动条（不可命中、不可拖动）。</p>
     *
     * @param signal   宽度信号，可为 null
     * @param fallback 常量宽度（{@link Props#barWidth()}）
     * @return 生效宽度（像素）
     */
    private static int resolveBarWidth(ReadableSignal<Integer> signal, int fallback) {
        if (signal == null) {
            return fallback;
        }
        Integer declared = signal.get();
        return declared != null && declared.intValue() > 0 ? declared.intValue() : fallback;
    }

    /**
     * 便捷重载：用默认（跟随主题的）track/thumb 颜色 + 默认 bar 宽/最小 thumb 高构造。
     *
     * <p>消除 4 控件/demo 等调用方重复手写 7 参 Props 的样板。
     * 行为与 {@code create(rt, new Props(viewport, scrollSignal, scrollSignal::set,
     * DEFAULT_TRACK_COLOR, DEFAULT_THUMB_COLOR, DEFAULT_BAR_WIDTH, DEFAULT_MIN_THUMB_HEIGHT))} 完全等价——
     * 两个默认色常量都是 {@link #THEME_COLOR} 哨兵，故默认路径的配色由主题派生
     * （见类文档「默认配色」），主题切换自动更新且不重建节点。</p>
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
     * 主题派生哨兵：{@code trackColor}/{@code thumbColor} 传本值表示「颜色跟随主题派生」，
     * 不表示某个具体颜色。
     *
     * <p>取 alpha=0 的不可见值——任何未及处理就把它当颜色写入节点的路径都等同全透明，
     * 不会出现哨兵色泄漏；显式传 {@code 0}（全透明）仍是「显式透明轨道」，不触发主题派生。
     * 传其它具体 ARGB 即完全覆盖主题派生（与迁移前「显式参数优先」语义一致）。</p>
     */
    public static final int THEME_COLOR = 0x00000001;
    /**
     * 默认滑块颜色（跟随主题：idle 取 {@code SceneThemes.mutedForeground} + {@link #THUMB_IDLE_ALPHA}）。
     */
    public static final int DEFAULT_THUMB_COLOR = THEME_COLOR;
    /**
     * 默认轨道颜色（跟随主题：{@code SceneThemes.mutedForeground} + {@link #TRACK_TINT_ALPHA} 极淡 tint）。
     */
    public static final int DEFAULT_TRACK_COLOR = THEME_COLOR;
    /**
     * 默认滚动条宽度（像素，M2 加宽后 8px，原 4px）。
     */
    public static final int DEFAULT_BAR_WIDTH = 8;
    /**
     * 默认滑块最小高度（像素，避免内容过多时滑块缩到不可见）。
     */
    public static final int DEFAULT_MIN_THUMB_HEIGHT = 20;

    /**
     * 轨道 tint 的中性 alpha（约 14%）：比旧默认 {@code 0x44FFFFFF} 更淡，槽底只做弱提示，
     * 不喧宾夺主；无溢出时轨道仍全透明。
     */
    static final int TRACK_TINT_ALPHA = 0x24;
    /**
     * 滑块 idle 态 alpha（约 60%，与旧默认 {@code SCROLLBAR_THUMB_IDLE} 同强度）。
     */
    static final int THUMB_IDLE_ALPHA = 0x99;
    /**
     * 滑块 hover 态 alpha（约 80%）：比 idle 明显提亮，反馈可感知。
     */
    static final int THUMB_HOVER_ALPHA = 0xCC;
    /**
     * 滑块拖动态 alpha（100%）：拖动是最强交互态，取主题正文色实心。
     */
    static final int THUMB_DRAG_ALPHA = 0xFF;

    /**
     * 把主题语义色的 RGB 与给定中性 alpha 组合（主题色自带 alpha 不参与，避免叠乘出不可见值）。
     *
     * @param argb  主题语义色
     * @param alpha 目标 alpha（0..255，越界 clamp）
     * @return 组合后的 ARGB
     */
    private static int withAlpha(int argb, int alpha) {
        int clamped = Math.max(0, Math.min(0xFF, alpha));
        return (clamped << 24) | (argb & 0x00FFFFFF);
    }
}
