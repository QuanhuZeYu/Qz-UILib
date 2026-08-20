package club.heiqi.uilib.font.render.software;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.font.render.GlyphRenderBatch;

/**
 * 软件渲染帧：一帧字体绘制指令流的不可变快照。
 *
 * <p>批次顺序即绘制顺序：高亮背景批次 → 各字符页字形批次 → 装饰线批次
 * （与真机 {@code FontBatchRenderer.flushWithinActiveState} 的提交顺序一致）。</p>
 */
public final class SoftwareRenderFrame {

    /** 一批同纹理 quad 的顶点快照。 */
    public static final class BatchSnapshot {

        private final int textureId;
        private final float[] vertexData;
        private final int quadCount;

        private BatchSnapshot(int textureId, float[] vertexData, int quadCount) {
            this.textureId = textureId;
            this.vertexData = vertexData;
            this.quadCount = quadCount;
        }

        public int getTextureId() {
            return textureId;
        }

        public float[] getVertexData() {
            return vertexData;
        }

        public int getQuadCount() {
            return quadCount;
        }

        /** 从收集批次冻结快照（顶点数据拷贝，不共享内部数组）。 */
        public static BatchSnapshot of(GlyphRenderBatch batch) {
            return new BatchSnapshot(batch.getTextureId(), batch.copyVertexData(), batch.getQuadCount());
        }
    }

    private final int width;
    private final int height;
    private final int backgroundArgb;
    private final List<BatchSnapshot> batches = new ArrayList<BatchSnapshot>();

    /**
     * @param width          渲染目标宽度（像素）
     * @param height         渲染目标高度（像素）
     * @param backgroundArgb 背景色（ARGB，混合底）
     */
    public SoftwareRenderFrame(int width, int height, int backgroundArgb) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("软件渲染帧尺寸必须为正");
        }
        this.width = width;
        this.height = height;
        this.backgroundArgb = backgroundArgb;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getBackgroundArgb() {
        return backgroundArgb;
    }

    /** 追加一个批次快照（绘制顺序 = 追加顺序）。 */
    public void addBatch(GlyphRenderBatch batch) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        batches.add(BatchSnapshot.of(batch));
    }

    /** 批次快照列表（有序）。 */
    public List<BatchSnapshot> getBatches() {
        return batches;
    }
}
