package club.heiqi.uilib.ui.scene.component;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 场景滚动能力封装，一行替换 signal、bind、on 三件套。
 *
 * <p>内部固定使用 {@link SceneGeometry#maxScrollY(SceneNode)} 闭式与条件性 stopPropagation 标准策略，
 * 消除调用方手写滚动样板的复发风险。</p>
 */
public final class SceneScrolls {

    private SceneScrolls() {
    }

    /**
     * 将纵向滚动能力附加到视口节点。
     *
     * <p>内部通过 {@link SceneRuntime#bind(Invalidation, club.heiqi.uilib.ui.reactive.ReadableSignal,
     * java.util.function.Consumer)} 以 {@link Invalidation#COMPOSITE} 绑定 scrollSignal 到
     * {@link SceneNode#setScrollOffsetY(int)}，并注册 {@link SceneEventType#SCROLL} handler。
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
        runtime.bind(Invalidation.COMPOSITE, scrollSignal, v -> viewport.setScrollOffsetY(v.intValue()));
        runtime.on(viewport, SceneEventType.SCROLL, (ev, ctx) -> {
            int maxScroll = SceneGeometry.maxScrollY(viewport);
            int current = scrollSignal.get().intValue();
            int next = current - ev.getWheelDelta();
            int clamped = Math.max(0, Math.min(maxScroll, next));
            if (clamped != current) {
                scrollSignal.set(Integer.valueOf(clamped));
                ctx.stopPropagation();
            }
        });
        return scrollSignal;
    }
}
