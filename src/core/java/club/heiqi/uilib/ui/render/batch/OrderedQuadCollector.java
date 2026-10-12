package club.heiqi.uilib.ui.render.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch.Draw;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch.Quad;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch.State;

/** 只合并相邻兼容图元；A/B/A绝不全局页分组。容量/宿主屏障自动输出封闭提交段。 */
public final class OrderedQuadCollector {
    private final int capacity;
    private final Consumer<OrderedQuadBatch> submit;
    private final List<Quad> quads = new ArrayList<>();
    private final List<Draw> draws = new ArrayList<>();
    private State active;
    private int first;
    private boolean submitting;

    public OrderedQuadCollector(int capacity, Consumer<OrderedQuadBatch> submit) {
        if (capacity <= 0 || capacity > Integer.MAX_VALUE / 6)
            throw new IllegalArgumentException("无效提交容量");
        this.capacity = capacity;
        this.submit = Objects.requireNonNull(submit, "submit");
    }

    public void add(State state, Quad quad) {
        if (submitting) throw new IllegalStateException("提交回调不能重入收集器");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(quad, "quad");
        if (!visible(state.clip, quad)) return;
        if (quads.size() == capacity) flush();
        if (!state.equals(active)) {
            endDraw();
            active = state;
            first = quads.size();
        }
        quads.add(quad);
    }

    private static boolean visible(LayoutBox clip, Quad q) {
        if (q.width == 0 || q.height == 0) return false;
        return clip == null
                || clip.width > 0
                        && clip.height > 0
                        && (double) q.x < (long) clip.x + clip.width
                        && (double) q.y < (long) clip.y + clip.height
                        && (double) q.x + q.width > clip.x
                        && (double) q.y + q.height > clip.y;
    }

    private void endDraw() {
        if (active != null) draws.add(new Draw(active, first, quads.size() - first));
        active = null;
    }

    /** 调用方在变换/深度/pass变化及委托其他渲染之前调用；业务组件不管理它。 */
    public void flush() {
        if (submitting) throw new IllegalStateException("提交回调不能重入收集器");
        if (quads.isEmpty()) return;
        endDraw();
        OrderedQuadBatch output = new OrderedQuadBatch(quads, draws);
        quads.clear();
        draws.clear();
        first = 0;
        // 回调只消费已封闭段，不能填入下一段；防止满容量提交后重入突破预算。
        submitting = true;
        try {
            submit.accept(output);
        } finally {
            submitting = false;
        }
    }
}
