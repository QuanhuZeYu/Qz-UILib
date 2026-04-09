package club.heiqi.uilib.font.render;

import java.util.Collections;
import java.util.List;

/**
 * 单帧渲染快照。
 */
public class RenderFrameSnapshot {

    private final List<GlyphRenderBatch> batches;

    /**
     * 创建单帧渲染快照。
     *
     * @param batches 批次列表
     */
    public RenderFrameSnapshot(List<GlyphRenderBatch> batches) {
        this.batches = batches;
    }

    public List<GlyphRenderBatch> getBatches() {
        return Collections.unmodifiableList(batches);
    }

    public boolean isEmpty() {
        return batches.isEmpty();
    }
}
