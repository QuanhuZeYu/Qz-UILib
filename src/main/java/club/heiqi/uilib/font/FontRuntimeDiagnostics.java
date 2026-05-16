package club.heiqi.uilib.font;

import java.awt.image.BufferedImage;
import java.util.concurrent.atomic.AtomicInteger;

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

    private FontRuntimeDiagnostics() {}

    /**
     * 记录字形生成结果的 alpha 分布。
     *
     * @param task 字形生成任务
     * @param image 字形图像
     * @param glyphInfo 字形度量
     */
    public static void logGeneratedGlyph(GlyphGenerationTask task, BufferedImage image, GlyphInfo glyphInfo) {
        if (!Config.useDebug) {
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
        if (!Config.useDebug) {
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
        if (!Config.useDebug) {
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
        return Config.useDebug && flushLogCount.get() < MAX_FLUSH_LOGS;
    }

    /**
     * 判断当前是否需要收集字形上传阶段 GL 诊断状态。
     *
     * @return 是否需要查询并记录 GL 状态
     */
    public static boolean shouldLogGlyphUpload() {
        return Config.useDebug && uploadLogCount.get() < MAX_UPLOAD_LOGS;
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
