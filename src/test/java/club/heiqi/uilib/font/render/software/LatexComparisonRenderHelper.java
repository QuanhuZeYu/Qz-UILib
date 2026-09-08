package club.heiqi.uilib.font.render.software;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.font.FontType;
import club.heiqi.uilib.font.GlyphRuntimeTablesView;
import club.heiqi.uilib.font.api.DefaultFontRendererAdapter;
import club.heiqi.uilib.font.latex.layout.MathBox;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.render.GlyphBatchCollector;
import club.heiqi.uilib.font.render.GlyphCollector;
import club.heiqi.uilib.font.render.GlyphRenderBatch;
import club.heiqi.uilib.ui.text.TextContentMode;

/** 对照工具专用画布：复用 testkit 和生产 collector，只旁录基线、不改绘制计划。 */
final class LatexComparisonRenderHelper {
    static final int BACKGROUND = 0xFFFFFFFF;
    static final int PAD = 12;

    private LatexComparisonRenderHelper() {}

    static final class Sample {
        final BufferedImage image;
        final MathBox box;
        final float originX;
        final float originY;
        final float baseline;
        final int endX;
        final float[] bounds;

        Sample(BufferedImage image, MathBox box, float originX, float originY, Recorder recorder) {
            this.image = image;
            this.box = box;
            this.originX = originX;
            this.originY = originY;
            this.baseline = recorder.baseline;
            this.endX = recorder.endX;
            this.bounds = bounds(recorder.batch);
        }
    }

    static Sample render(String source, int size, float scale, Float targetBaseline) {
        return render(source, size, scale, targetBaseline, 0.0F, null);
    }

    /** 像素起点分数可显式指定；目标共享基线与固定 Y 分数不能同时指定。 */
    static Sample render(String source, int size, float scale, Float targetBaseline,
            float originFractionX, Float originFractionY) {
        if (!Float.isFinite(originFractionX) || originFractionX < 0 || originFractionX >= 1
                || originFractionY != null && (!Float.isFinite(originFractionY.floatValue())
                || originFractionY.floatValue() < 0 || originFractionY.floatValue() >= 1 || targetBaseline != null)) {
            throw new IllegalArgumentException("Invalid fractional origin/baseline combination");
        }
        LatexSoftwareRenderKit.Shared shared = LatexSoftwareRenderKit.shared();
        List<TextSegment> segments = shared.service.parseSegments("<latex>" + source + "</latex>",
                0xFF000000, TextContentMode.RICH_TAGS);
        LatexSoftwareRenderKit.assembleGlyphs(shared, segments, size);
        MathBox box = LatexSoftwareRenderKit.layout(source, size);
        if (box.getGlyphs().isEmpty()) {
            throw new IllegalArgumentException("Comparison sample requires an observable first glyph: " + source);
        }
        Recorder initial = collect(segments, box, size, scale, 0, 0);
        float[] initialBounds = bounds(initial.batch);
        float x = (float) Math.ceil(PAD - Math.min(0, initialBounds[0])) + originFractionX;
        // First pass chooses an integer baseline; the second pass can share the reference baseline exactly.
        float baseline = targetBaseline == null
                ? (float) Math.ceil(PAD + initial.baseline - Math.min(0, initialBounds[1]))
                : targetBaseline.floatValue();
        float y = baseline - initial.baseline;
        if (originFractionY != null) {
            y = (float) Math.ceil(y) + originFractionY.floatValue();
            baseline = initial.baseline + y;
        }
        Recorder finalRecorder = collect(segments, box, size, scale, x, y);
        float[] b = bounds(finalRecorder.batch);
        if (b[0] < 1 || b[1] < 1 || Math.abs(finalRecorder.baseline - baseline) > 0.01F) {
            throw new AssertionError("Invalid canvas/baseline for " + source);
        }
        int width = dimension(Math.max(b[2], x + box.getWidth() * scale) + PAD);
        int height = dimension(Math.max(b[3], baseline + box.getDepth() * scale) + PAD);
        SoftwareRenderFrame frame = new SoftwareRenderFrame(width, height, BACKGROUND);
        for (GlyphRenderBatch batch : batches(finalRecorder.batch)) {
            if (!batch.isEmpty()) frame.addBatch(batch);
        }
        int[] pixels = FontSoftwareRasterizer.render(frame, shared.gl);
        BufferedImage image = FontSoftwareRasterizer.toImage(pixels, width, height);
        requireUnclippedInk(image, source);
        return new Sample(image, box, x, y, finalRecorder);
    }

    static int dimension(float value) {
        if (!Float.isFinite(value) || value < 1 || value > 32768) {
            throw new IllegalArgumentException("Invalid/oversized comparison canvas: " + value);
        }
        return (int) Math.ceil(value);
    }

    static void requireUnclippedInk(BufferedImage image, String name) {
        boolean ink = false;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != BACKGROUND) {
                    ink = true;
                    if (x == 0 || y == 0 || x == image.getWidth() - 1 || y == image.getHeight() - 1) {
                        throw new AssertionError("Ink reaches canvas edge: " + name);
                    }
                }
            }
        }
        if (!ink) throw new AssertionError("No rendered ink: " + name);
    }

    private static Recorder collect(List<TextSegment> segments, MathBox box, int size, float scale,
            float x, float y) {
        LatexSoftwareRenderKit.Shared shared = LatexSoftwareRenderKit.shared();
        Recorder recorder = new Recorder(box.getGlyphs().get(0).getY() * scale);
        recorder.endX = DefaultFontRendererAdapter.getInstance().renderSegmentsToCollector(segments,
                shared.settings, shared.service, GlyphRuntimeTablesView.snapshot(shared.tables, shared.manager, shared.runtimeVersion),
                x, y, false, scale, size, recorder);
        if (!Float.isFinite(recorder.baseline)) throw new AssertionError("First glyph baseline unavailable");
        return recorder;
    }

    private static List<GlyphRenderBatch> batches(GlyphBatchCollector collector) {
        List<GlyphRenderBatch> result = new ArrayList<GlyphRenderBatch>();
        result.add(collector.getMarkBackgroundBatch());
        for (int i = 0; i < collector.getActivePageCount(); i++) result.add(collector.getActiveBatch(i));
        result.add(collector.getDecorationBatch());
        return result;
    }

    private static float[] bounds(GlyphBatchCollector collector) {
        float[] b = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (GlyphRenderBatch batch : batches(collector)) {
            float[] vertices = batch.copyVertexData();
            for (int i = 0; i < vertices.length; i += GlyphRenderBatch.VERTEX_STRIDE_FLOATS) {
                b[0] = Math.min(b[0], vertices[i]);
                b[1] = Math.min(b[1], vertices[i + 1]);
                b[2] = Math.max(b[2], vertices[i]);
                b[3] = Math.max(b[3], vertices[i + 1]);
            }
        }
        for (float value : b) if (!Float.isFinite(value)) throw new AssertionError("Non-finite/empty frame bounds");
        return b;
    }

    private static final class Recorder implements GlyphCollector {
        final GlyphBatchCollector batch = new GlyphBatchCollector();
        final float firstGlyphOffsetY;
        float baseline = Float.NaN;
        int endX;

        Recorder(float firstGlyphOffsetY) {
            this.firstGlyphOffsetY = firstGlyphOffsetY;
        }

        @Override
        public void collectBaselineAlignedGlyph(FontType fontType, int pageIndex, int textureId, int textureSize,
                int slotX, int slotY, int slotWidth, int slotHeight, int atlasBaselineX, int atlasBaselineY,
                int lineBaselineY, int defaultGlyphSize, int inkWidth, int inkHeight, int bearingX, int bearingY,
                float x, float y, float charSize, int color, boolean italic, byte glyphFlags, float baseCharSize) {
            if (Float.isNaN(baseline)) {
                // Same conversion as FontBatchRenderer.resolveGlyphQuadMetrics. The formula box uses y-down
                // offsets from its baseline; observing the first glyph avoids copying the line-layout algorithm.
                baseline = y + lineBaselineY * (baseCharSize / Math.max(1.0F, defaultGlyphSize)) - firstGlyphOffsetY;
            }
            batch.collectBaselineAlignedGlyph(fontType, pageIndex, textureId, textureSize, slotX, slotY,
                    slotWidth, slotHeight, atlasBaselineX, atlasBaselineY, lineBaselineY, defaultGlyphSize,
                    inkWidth, inkHeight, bearingX, bearingY, x, y, charSize, color, italic, glyphFlags, baseCharSize);
        }

        @Override
        public void collectBaselineAlignedGlyphClipped(FontType fontType, int pageIndex, int textureId, int textureSize,
                int slotX, int slotY, int slotWidth, int slotHeight, int atlasBaselineX, int atlasBaselineY,
                int lineBaselineY, int defaultGlyphSize, int inkWidth, int inkHeight, int bearingX, int bearingY,
                float x, float y, float charSize, int color, boolean italic, byte glyphFlags, float baseCharSize,
                float clipLeft, float clipTop, float clipRight, float clipBottom) {
            if (Float.isNaN(baseline)) {
                baseline = y + lineBaselineY * (baseCharSize / Math.max(1.0F, defaultGlyphSize)) - firstGlyphOffsetY;
            }
            batch.collectBaselineAlignedGlyphClipped(fontType, pageIndex, textureId, textureSize, slotX, slotY,
                    slotWidth, slotHeight, atlasBaselineX, atlasBaselineY, lineBaselineY, defaultGlyphSize,
                    inkWidth, inkHeight, bearingX, bearingY, x, y, charSize, color, italic, glyphFlags, baseCharSize,
                    clipLeft, clipTop, clipRight, clipBottom);
        }

        @Override
        public void collectDecoration(float x, float y, float width, float height, int color) {
            batch.collectDecoration(x, y, width, height, color);
        }

        @Override
        public void collectMarkBackground(float x, float y, float width, float height, int color) {
            batch.collectMarkBackground(x, y, width, height, color);
        }
    }
}
