package club.heiqi.uilib.ui.scene.runtime;

import java.util.function.Consumer;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 场景滚动能力封装，一行替换 signal、bind、on 三件套。
 *
 * <p>内部固定使用 {@link SceneGeometry#maxScrollY(SceneNode)} 闭式与条件性 stopPropagation 标准策略，
 * 消除调用方手写滚动样板的复发风险。</p>
 *
 * <h3>两种 attach 形态</h3>
 * <ul>
 *   <li>{@link #attach(SceneRuntime, SceneNode)}：内部创建并返回 {@code Signal<Integer>}，
 *       适用于单一滚动源场景（scroll state 即该 signal）。</li>
 *   <li>{@link #attach(SceneRuntime, SceneNode, ReadableSignal, Consumer)}：调用方自管 scroll state，
 *       传入只读显示源 + 写入回调。适用于 per-section scroll state 方案——显示源可为派生 Computed
 *       （当前 active section 的 scroll），写入回调写当前 active section 的 signal。</li>
 * </ul>
 */
public final class SceneScrolls {

    private SceneScrolls() {
    }

    /**
     * 将纵向滚动能力附加到视口节点（内部创建 scrollSignal 形态）。
     *
     * <p>内部通过 {@link SceneRuntime#bind(ReadableSignal, java.util.function.Consumer)} 绑定 scrollSignal 到
     * {@link SceneNode#setScrollOffsetY(int)}（{@code setScrollOffsetY} 内部自动打出 GEOMETRY 级失效），
     * 并注册 {@link SceneEventType#SCROLL} handler。
     * handler 使用 {@link SceneGeometry#maxScrollY(SceneNode)} 读取 GEOMETRY 级几何，按
     * {@code current - wheelDelta} 计算下一位置，仅当 clamp 后位置变化时写 signal 并停止冒泡。</p>
     *
     * <p>该方法遵守 I1 signal-first、I7 GEOMETRY 级滚动不重排、I11 逃生舱①只读几何约束。</p>
     *
     * @param runtime 场景运行时
     * @param viewport 滚动视口节点
     * @return 可供调用方观察位置或编程式滚动的 scrollSignal
     */
    public static Signal<Integer> attach(SceneRuntime runtime, SceneNode viewport) {
        if (runtime == null || viewport == null) {
            throw new IllegalArgumentException("runtime 与 viewport 均不可为 null");
        }
        if (!viewport.isScrollable()) {
            throw new IllegalArgumentException(
                "viewport 未设 setScrollable(true)：滚动能力要求视口节点先声明为可滚动容器。"
                + "非 scrollable 节点高度由内容撑大、maxScrollY 恒为 0，attach 的滚轮 handler 将永远无效。"
                + "修复：viewport.setScrollable(true) 后再 attach。");
        }
        Signal<Integer> scrollSignal = Signal.create(Integer.valueOf(0));
        attach(runtime, viewport, scrollSignal, scrollSignal::set);
        return scrollSignal;
    }

    /**
     * 将纵向滚动能力附加到视口节点（调用方自管 scroll state 形态）。
     *
     * <p>适用于 per-section scroll state 方案：调用方持有每个 section 独立的 {@code Signal<Integer>}，
     * 传入当前 active section 的只读显示源（可为派生 Computed，clamp 到当前 maxScroll）与写入回调
     * （写当前 active section 的 signal，不 clamp，显示时 clamp）。</p>
     *
     * <p>内部绑定 {@code scrollOffsetSignal} 到
     * {@link SceneNode#setScrollOffsetY(int)}（{@code setScrollOffsetY} 内部自动打出 GEOMETRY 级失效），
     * 并注册 {@link SceneEventType#SCROLL} handler。
     * handler 读 {@code scrollOffsetSignal} 当前值，按 {@code current - wheelDelta} 计算下一位置，
     * clamp 后经 {@code setScrollOffset} 回调写入，仅当位置变化时停止冒泡。</p>
     *
     * <p>该方法遵守 I1 signal-first、I7 GEOMETRY 级滚动不重排、I11 逃生舱①只读几何约束。</p>
     *
     * @param runtime 场景运行时
     * @param viewport 滚动视口节点（须先 setScrollable(true)）
     * @param scrollOffsetSignal 滚动偏移只读显示源（可为派生 Computed）
     * @param setScrollOffset 滚动偏移写入回调（handler 调此回调写 scroll state）
     */
    public static void attach(SceneRuntime runtime, SceneNode viewport,
                              ReadableSignal<Integer> scrollOffsetSignal,
                              Consumer<Integer> setScrollOffset) {
        if (runtime == null || viewport == null) {
            throw new IllegalArgumentException("runtime 与 viewport 均不可为 null");
        }
        if (!viewport.isScrollable()) {
            throw new IllegalArgumentException(
                "viewport 未设 setScrollable(true)：滚动能力要求视口节点先声明为可滚动容器。"
                + "非 scrollable 节点高度由内容撑大、maxScrollY 恒为 0，attach 的滚轮 handler 将永远无效。"
                + "修复：viewport.setScrollable(true) 后再 attach。");
        }
        if (scrollOffsetSignal == null || setScrollOffset == null) {
            throw new IllegalArgumentException("scrollOffsetSignal 与 setScrollOffset 均不可为 null");
        }
        // 滚动唯一汇点：所有滚动源（滚轮/滚动条拖动/键盘导航/程序滚动）最终都写 scrollOffsetSignal，
        // 此绑定是框架内唯一把滚动值落到节点的地方。滚动是 GEOMETRY 级（不重排），不会走 POINTER_MOVE，
        // 必须在这里请求 hover 重算，否则非滚轮路径（滚动条拖动、键盘滚动）下 hover 滞留：
        // tooltip 等悬浮驱动浮层会残留在旧锚点上。
        runtime.bind(scrollOffsetSignal, v -> {
            int next = v.intValue();
            if (viewport.getScrollOffsetY() != next) {
                viewport.setScrollOffsetY(next);
                runtime.__requestHoverReconcileAfterScroll();
            }
        });
        runtime.on(viewport, SceneEventType.SCROLL, (ev, ctx) -> {
            int maxScroll = SceneGeometry.maxScrollY(viewport);
            int current = scrollOffsetSignal.get().intValue();
            int next = current - ev.getWheelDelta();
            int clamped = Math.max(0, Math.min(maxScroll, next));
            if (clamped != current) {
                setScrollOffset.accept(Integer.valueOf(clamped));
                ctx.stopPropagation();
            }
        });
    }
}
