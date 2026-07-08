package club.heiqi.uilib.ui.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 当前渲染帧的主层后置回放队列。
 */
final class DeferredPostMainPassQueue {

    private final List<DeferredPostMainPass> passes = new ArrayList<DeferredPostMainPass>();
    private final List<DeferredPostMainPass> overlayPasses = new ArrayList<DeferredPostMainPass>();

    /**
     * 登记一批主渲染后的补充回放动作。
     *
     * @param replay 主渲染完成后要回放的动作
     * @param clipSnapshot 登记时的裁剪快照
     */
    void enqueue(DeferredPostMainPassReplay replay, ClipSnapshot clipSnapshot) {
        passes.add(new DeferredPostMainPass(Objects.requireNonNull(replay, "replay"), clipSnapshot));
    }

    /**
     * 登记一批主渲染后的顶层 overlay 回放动作。
     *
     * @param replay 主渲染完成后要回放的顶层动作
     */
    void enqueueOverlay(DeferredPostMainPassReplay replay) {
        overlayPasses.add(new DeferredPostMainPass(Objects.requireNonNull(replay, "replay"), null));
    }

    /**
     * 判断当前帧是否存在待回放动作。
     *
     * @return 是否存在待回放动作
     */
    boolean hasPasses() {
        return !passes.isEmpty() || !overlayPasses.isEmpty();
    }

    /**
     * 取出并清空当前帧登记的后置绘制动作。
     *
     * @return 当前帧延迟回放列表
     */
    List<DeferredPostMainPass> drain() {
        if (passes.isEmpty() && overlayPasses.isEmpty()) {
            return Collections.emptyList();
        }
        List<DeferredPostMainPass> drainedPasses = new ArrayList<DeferredPostMainPass>(passes);
        drainedPasses.addAll(overlayPasses);
        passes.clear();
        overlayPasses.clear();
        return drainedPasses;
    }
}
