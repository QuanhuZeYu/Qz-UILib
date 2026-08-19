package club.heiqi.uilib.font.render;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.font.FontType;
import sun.misc.Unsafe;

/** 字体批渲染器原版尾状态记录（末字形色、最后页纹理、flush 序号）的行为测试。 */
public class FontBatchRendererTailStateTest {

    /** 收集侧按字形顺序记录末字形色，flush 侧记录后经 getter 暴露。 */
    @Test
    public void recordsLastGlyphColorAndBoundTextureAtFlush() throws Exception {
        FontBatchRenderer renderer = allocateRenderer();

        renderer.collectBaselineAlignedGlyph(FontType.NORMAL, 0, 5, 128, 10, 20, 30, 40, 6, 18, 50, 100, 24, 32, 2,
                -10, 0.0F, 0.0F, 50.0F, 0xFF112233, false, (byte) 0, 100.0F);
        renderer.collectBaselineAlignedGlyph(FontType.NORMAL, 0, 5, 128, 10, 20, 30, 40, 6, 18, 50, 100, 24, 32, 2,
                -10, 10.0F, 0.0F, 50.0F, 0xFF445566, false, (byte) 0, 100.0F);
        Assert.assertEquals(2, renderer.getQuadCount());

        invokeRecordLastFlushTailState(renderer, 777);

        Assert.assertEquals("flush 末字形色应为最后一个收集字形", 0xFF445566, renderer.getLastFlushGlyphColor());
        Assert.assertEquals("flush 最后页纹理应为 flush 侧记录值", 777, renderer.getLastFlushBoundTextureId());
    }

    /** clearFrame 清空收集侧字形色后，flush 侧不再报告任何字形色。 */
    @Test
    public void reportsNoGlyphColorWhenFrameHasNoGlyph() throws Exception {
        FontBatchRenderer renderer = allocateRenderer();

        renderer.collectBaselineAlignedGlyph(FontType.NORMAL, 0, 5, 128, 10, 20, 30, 40, 6, 18, 50, 100, 24, 32, 2,
                -10, 0.0F, 0.0F, 50.0F, 0xFF112233, false, (byte) 0, 100.0F);
        renderer.clearFrame();
        invokeRecordLastFlushTailState(renderer, 5);

        Assert.assertEquals("无字形 flush 不得报告字形色", FontBatchRenderer.NO_GLYPH_COLOR,
                renderer.getLastFlushGlyphColor());
        Assert.assertEquals(5, renderer.getLastFlushBoundTextureId());
    }

    /** 装饰线不参与末字形色记录。 */
    @Test
    public void decorationDoesNotUpdateLastGlyphColor() throws Exception {
        FontBatchRenderer renderer = allocateRenderer();

        renderer.collectDecoration(0.0F, 0.0F, 4.0F, 1.0F, 0xFFFFFFFF);
        invokeRecordLastFlushTailState(renderer, 9);

        Assert.assertEquals("纯装饰线 flush 不得报告字形色", FontBatchRenderer.NO_GLYPH_COLOR,
                renderer.getLastFlushGlyphColor());
    }

    /** flush 序号随每次 flush 统计单调递增。 */
    @Test
    public void flushSequenceIncrementsOnEveryFlushStatsRecording() throws Exception {
        FontBatchRenderer renderer = allocateRenderer();
        long before = renderer.getLastFlushSequence();

        invokeRecordLastFlushStats(renderer, 1, 2, 3);

        Assert.assertEquals(before + 1, renderer.getLastFlushSequence());
        Assert.assertEquals(1, renderer.getLastFlushPageSubmitCount());
        Assert.assertEquals(2, renderer.getLastFlushDrawCallCount());
        Assert.assertEquals(3, renderer.getLastFlushTextureBindCount());
    }

    /**
     * 以 Unsafe 分配实例并补齐 collect 路径所需字段，避免触发 org.lwjgl.BufferUtils（不在测试 classpath）。
     *
     * <p>initialized 预置为 true，使 collect 路径跳过 GL 初始化。</p>
     */
    private static FontBatchRenderer allocateRenderer() throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        FontBatchRenderer renderer = FontBatchRenderer.class
                .cast(((Unsafe) unsafeField.get(null)).allocateInstance(FontBatchRenderer.class));
        setField(renderer, "normalPageBatches", new GlyphRenderBatch[4]);
        setField(renderer, "boldPageBatches", new GlyphRenderBatch[4]);
        setField(renderer, "activePageIndices", new int[8]);
        setField(renderer, "activePageTypes", new byte[8]);
        setField(renderer, "activeNormalPages", new boolean[4]);
        setField(renderer, "activeBoldPages", new boolean[4]);
        setField(renderer, "decorationBatch", new GlyphRenderBatch());
        setField(renderer, "markBackgroundBatch", new GlyphRenderBatch());
        setField(renderer, "initialized", new AtomicBoolean(true));
        setField(renderer, "lastCollectedGlyphColor", Integer.valueOf(FontBatchRenderer.NO_GLYPH_COLOR));
        setField(renderer, "lastFlushGlyphColor", Integer.valueOf(FontBatchRenderer.NO_GLYPH_COLOR));
        setField(renderer, "lastFlushBoundTextureId", Integer.valueOf(FontBatchRenderer.NO_TEXTURE));
        return renderer;
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = FontBatchRenderer.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static void invokeRecordLastFlushTailState(FontBatchRenderer renderer, int boundTextureId)
            throws Exception {
        Method method = FontBatchRenderer.class.getDeclaredMethod("recordLastFlushTailState", int.class);
        method.setAccessible(true);
        method.invoke(renderer, Integer.valueOf(boundTextureId));
    }

    private static void invokeRecordLastFlushStats(FontBatchRenderer renderer, int pageSubmitCount, int drawCallCount,
            int textureBindCount) throws Exception {
        Method method = FontBatchRenderer.class.getDeclaredMethod("recordLastFlushStats", int.class, int.class,
                int.class);
        method.setAccessible(true);
        method.invoke(renderer, Integer.valueOf(pageSubmitCount), Integer.valueOf(drawCallCount),
                Integer.valueOf(textureBindCount));
    }
}
