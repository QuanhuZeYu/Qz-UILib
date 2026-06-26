package club.heiqi.uilib.font;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import club.heiqi.uilib.Config;
import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.glyph.GlyphGenerationTask;
import club.heiqi.uilib.font.glyph.GlyphInfo;

/**
 * 字体运行时诊断日志。
 */
public final class FontRuntimeDiagnostics {

    private static final int MAX_GENERATED_LOGS = 16;
    private static final int MAX_UPLOAD_LOGS = 16;
    private static final int MAX_FLUSH_LOGS = 32;

    private static final AtomicInteger generatedLogCount = new AtomicInteger(0);
    private static final AtomicInteger uploadLogCount = new AtomicInteger(0);
    private static final AtomicInteger flushLogCount = new AtomicInteger(0);

    /** render_tick 运行统计采样间隔（毫秒），避免每帧刷屏。 */
    private static final long RENDER_TICK_STATS_INTERVAL_MS = 1000L;
    /** 字体批次提交日志采样间隔（毫秒），避免每帧多次 flush 刷屏。 */
    private static final long FLUSH_BATCH_STATS_INTERVAL_MS = 1000L;

    private static final AtomicLong lastRenderTickStatsLogMs = new AtomicLong(0L);
    private static final AtomicLong lastFlushBatchStatsLogMs = new AtomicLong(0L);

    private FontRuntimeDiagnostics() {}

    /**
     * 记录字形生成结果的 alpha 分布。
     *
     * @param task 字形生成任务
     * @param image 字形图像
     * @param glyphInfo 字形度量
     */
    public static void logGeneratedGlyph(GlyphGenerationTask task, BufferedImage image, GlyphInfo glyphInfo) {
        if (!Config.fontRuntimeDebug) {
            return;
        }
        int index = generatedLogCount.getAndIncrement();
        if (index >= MAX_GENERATED_LOGS || task == null || image == null) {
            return;
        }

        AlphaStats alphaStats = collectAlphaStats(image);
        MyMod.LOG.info("字体诊断[生成] thread={} runtime={} codepoint={} type={} image={}x{} transparent={} "
                + "opaque={} partial={} advance={} colored={}",
                Thread.currentThread().getName(),
                Integer.valueOf(task.getRuntimeVersion()),
                Integer.valueOf(task.getCodepoint()),
                task.getFontType(),
                Integer.valueOf(image.getWidth()),
                Integer.valueOf(image.getHeight()),
                Integer.valueOf(alphaStats.transparentCount),
                Integer.valueOf(alphaStats.opaqueCount),
                Integer.valueOf(alphaStats.partialCount),
                glyphInfo == null ? null : Float.valueOf(glyphInfo.getAdvance()),
                glyphInfo == null ? null : Boolean.valueOf(glyphInfo.isColoredGlyph()));
    }

    /**
     * 记录字形纹理上传状态。
     *
     * @param runtimeVersion 运行时版本
     * @param codepoint 字符码点
     * @param fontType 字体类型
     * @param textureId 纹理 ID
     * @param textureValid GL 是否认为纹理有效
     * @param glError GL 错误码
     * @param image 字形图像
     */
    public static void logGlyphUpload(int runtimeVersion, int codepoint, FontType fontType, int textureId,
            boolean textureValid, int glError, BufferedImage image) {
        if (!Config.fontRuntimeDebug) {
            return;
        }
        int index = uploadLogCount.getAndIncrement();
        if (index >= MAX_UPLOAD_LOGS) {
            return;
        }

        AlphaStats alphaStats = image == null ? new AlphaStats() : collectAlphaStats(image);
        MyMod.LOG.info("字体诊断[上传] thread={} runtime={} codepoint={} type={} textureId={} valid={} "
                + "glError={} transparent={} opaque={} partial={}",
                Thread.currentThread().getName(),
                Integer.valueOf(runtimeVersion),
                Integer.valueOf(codepoint),
                fontType,
                Integer.valueOf(textureId),
                Boolean.valueOf(textureValid),
                Integer.valueOf(glError),
                Integer.valueOf(alphaStats.transparentCount),
                Integer.valueOf(alphaStats.opaqueCount),
                Integer.valueOf(alphaStats.partialCount));
    }

    /**
     * 记录字体批次绘制时的关键 GL 状态。
     *
     * @param shaderProgramId 字体 shader 程序 ID
     * @param currentProgram 当前 GL 程序 ID
     * @param textureId 字形页纹理 ID
     * @param boundTexture 当前绑定纹理 ID
     * @param vao 当前 VAO ID
     * @param glError GL 错误码
     * @param quadCount 批次四边形数量
     */
    public static void logFlushState(int shaderProgramId, int currentProgram, int textureId, int boundTexture, int vao,
            int glError, int quadCount) {
        if (!Config.fontRuntimeDebug) {
            return;
        }
        int index = flushLogCount.getAndIncrement();
        if (index >= MAX_FLUSH_LOGS) {
            return;
        }

        MyMod.LOG.info("字体诊断[绘制] thread={} shader={} currentProgram={} textureId={} boundTexture={} "
                + "vao={} glError={} quadCount={}",
                Thread.currentThread().getName(),
                Integer.valueOf(shaderProgramId),
                Integer.valueOf(currentProgram),
                Integer.valueOf(textureId),
                Integer.valueOf(boundTexture),
                Integer.valueOf(vao),
                Integer.valueOf(glError),
                Integer.valueOf(quadCount));
    }

    /**
     * 判断当前是否需要收集绘制阶段 GL 诊断状态。
     *
     * @return 是否需要查询并记录 GL 状态
     */
    public static boolean shouldLogFlushState() {
        return Config.fontRuntimeDebug && flushLogCount.get() < MAX_FLUSH_LOGS;
    }

    /**
     * 判断当前是否需要收集字形上传阶段 GL 诊断状态。
     *
     * @return 是否需要查询并记录 GL 状态
     */
    public static boolean shouldLogGlyphUpload() {
        return Config.fontRuntimeDebug && uploadLogCount.get() < MAX_UPLOAD_LOGS;
    }

    /**
     * 判断当前是否应输出 render_tick 字体运行统计日志。
     * 受开关、DEBUG 级别与时间窗口采样三重控制，避免每帧刷屏。
     *
     * @return 是否应输出
     */
    public static boolean shouldLogRenderTickStats() {
        if (!Config.fontRuntimeDebug || !MyMod.LOG.isDebugEnabled()) {
            return false;
        }
        long now = System.currentTimeMillis();
        long last = lastRenderTickStatsLogMs.get();
        if (now - last < RENDER_TICK_STATS_INTERVAL_MS) {
            return false;
        }
        return lastRenderTickStatsLogMs.compareAndSet(last, now);
    }

    /**
     * 判断当前是否应输出字体批次提交日志。
     * 受开关、DEBUG 级别与时间窗口采样三重控制，避免每帧多次 flush 刷屏。
     *
     * @return 是否应输出
     */
    public static boolean shouldLogFlushBatchStats() {
        if (!Config.fontRuntimeDebug || !MyMod.LOG.isDebugEnabled()) {
            return false;
        }
        long now = System.currentTimeMillis();
        long last = lastFlushBatchStatsLogMs.get();
        if (now - last < FLUSH_BATCH_STATS_INTERVAL_MS) {
            return false;
        }
        return lastFlushBatchStatsLogMs.compareAndSet(last, now);
    }

    private static AlphaStats collectAlphaStats(BufferedImage image) {
        AlphaStats alphaStats = new AlphaStats();
        int width = image.getWidth();
        int height = image.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int alpha = (image.getRGB(x, y) >> 24) & 0xFF;
                if (alpha == 0) {
                    alphaStats.transparentCount++;
                } else if (alpha == 255) {
                    alphaStats.opaqueCount++;
                } else {
                    alphaStats.partialCount++;
                }
            }
        }
        return alphaStats;
    }

    private static final class AlphaStats {

        private int transparentCount;
        private int opaqueCount;
        private int partialCount;
    }
}
