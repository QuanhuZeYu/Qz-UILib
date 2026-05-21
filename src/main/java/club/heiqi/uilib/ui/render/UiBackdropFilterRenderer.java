package club.heiqi.uilib.ui.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;

import club.heiqi.uilib.ui.style.cascade.UiBorderRadiusResolver;
import club.heiqi.uilib.ui.style.values.UiSurfaceStyle;

/**
 * UI backdrop-filter 渲染管线。
 *
 * <p>从 {@code UiRenderContext} 抽出的"背后内容滤镜"职责：负责协调
 * 主层快照获取、shader 路径、固定管线 fallback、tint 兜底，以及全局
 * 最近一次渲染路径与诊断说明的记录。</p>
 *
 * <p>本类自身无外部公开 API；最近渲染路径仍由 {@link UiRenderContext} 暴露
 * （静态访问器通过本类 trampoline 读取）。</p>
 */
final class UiBackdropFilterRenderer {

    private static final UiBackdropShaderProgram BACKDROP_SHADER_PROGRAM = new UiBackdropShaderProgram();

    private static final float[][] UI_BACKDROP_BLUR_SAMPLES = new float[][] {
            { -1.0F, 0.0F, 0.18F },
            { 1.0F, 0.0F, 0.18F },
            { 0.0F, -1.0F, 0.18F },
            { 0.0F, 1.0F, 0.18F },
            { -1.0F, -1.0F, 0.12F },
            { 1.0F, -1.0F, 0.12F },
            { -1.0F, 1.0F, 0.12F },
            { 1.0F, 1.0F, 0.12F }
    };

    private static volatile BackdropFilterRenderPath lastRenderPath = BackdropFilterRenderPath.NONE;
    private static volatile String lastDetail = "not-run";

    private UiBackdropFilterRenderer() {}

    /**
     * 返回最近一次 backdrop-filter 实际渲染路径。
     */
    static BackdropFilterRenderPath getLastRenderPath() {
        return lastRenderPath;
    }

    /**
     * 返回最近一次 backdrop-filter 诊断说明。
     */
    static String getLastDetail() {
        return lastDetail;
    }

    /**
     * 渲染一次 backdrop-filter，并记录最终路径。
     *
     * @param context 调用方渲染上下文
     * @param left 左侧坐标
     * @param top 顶部坐标
     * @param right 右侧坐标
     * @param bottom 底部坐标
     * @param blurRadius 模糊半径像素
     * @param saturation 饱和度倍率，1.0 表示不改变
     * @param cornerRadii 四角圆角
     */
    static void render(UiRenderContext context, int left, int top, int right, int bottom, int blurRadius,
            float saturation, UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {
        if (right <= left || bottom <= top || (blurRadius <= 0 && Float.compare(saturation, 1.0F) == 0)) {
            recordPath(BackdropFilterRenderPath.NONE, "skipped");
            return;
        }
        String pendingFallbackDetail = drawCurrentUiBackdropFilter(context, left, top, right, bottom, blurRadius,
                saturation, cornerRadii);
        if (pendingFallbackDetail == null) {
            return;
        }
        drawTintFallback(context, left, top, right, bottom, blurRadius, saturation, cornerRadii,
                pendingFallbackDetail);
    }

    /**
     * 走主流程：当前 UI 主层快照 + shader / 固定管线模糊。
     *
     * @return 当走完仍未完成绘制时返回 fallback 诊断字符串；成功完成绘制时返回 {@code null}
     */
    private static String drawCurrentUiBackdropFilter(UiRenderContext context, int left, int top, int right,
            int bottom, int blurRadius, float saturation, UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii) {
        int screenWidth = context.getScreenWidth();
        int screenHeight = context.getScreenHeight();
        UiMainLayerSnapshotService snapshotService = context.getMainLayerSnapshotService();

        UiMainLayerSnapshotService.SampleRegion sampleRegion = UiMainLayerSnapshotService.resolveSampleRegion(
                screenWidth, screenHeight, left, top, right, bottom, blurRadius);
        if (sampleRegion == null) {
            return "texture-copy-unavailable";
        }

        int backdropReadFramebufferId = context.getCurrentBackdropReadFramebufferId();
        UiMainLayerSnapshotService.Snapshot snapshot = snapshotService.acquireSnapshot(screenWidth,
                screenHeight, backdropReadFramebufferId, context.getMainLayerContentRevisionForDiagnostics(),
                sampleRegion, blurRadius);
        if (snapshot == null) {
            return "snapshot-unavailable: " + snapshotService.getLastFailureDetail();
        }

        context.pushClip(left, top, right, bottom, cornerRadii);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        boolean drewBackdrop = false;
        try {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, snapshot.getTextureId());
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glDisable(GL11.GL_BLEND);

            if (drawBackdropTextureWithShader(left, top, right, bottom, snapshot.getSampleLeft(),
                    snapshot.getSampleTop(), snapshot.getWidth(), snapshot.getHeight(), snapshot.getTextureWidth(),
                    snapshot.getTextureHeight(), snapshot.getDownsampleFactor(), blurRadius, saturation, snapshot)) {
                drewBackdrop = true;
                return null;
            }
            if (blurRadius <= 0) {
                return "shader-and-blur-unavailable";
            }

            drawBackdropTextureQuad(left, top, right, bottom, snapshot.getSampleLeft(), snapshot.getSampleTop(),
                    snapshot.getWidth(), snapshot.getHeight(), 0.0F, 0.0F);
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            float sampleStep = (float) resolveBackdropSampleStep(blurRadius);
            for (float[] sample : UI_BACKDROP_BLUR_SAMPLES) {
                GL11.glColor4f(1.0F, 1.0F, 1.0F, sample[2]);
                drawBackdropTextureQuad(left, top, right, bottom, snapshot.getSampleLeft(), snapshot.getSampleTop(),
                        snapshot.getWidth(), snapshot.getHeight(), sample[0] * sampleStep, sample[1] * sampleStep);
            }
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            recordPath(BackdropFilterRenderPath.FIXED_PIPELINE,
                    "shader-unavailable, snapshot=" + formatSnapshotState(snapshot));
            drewBackdrop = true;
            return null;
        } finally {
            GL20.glUseProgram(previousProgram);
            GL13.glActiveTexture(previousActiveTexture);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL11.glPopAttrib();
            context.popClip();
            snapshotService.releaseSnapshot(snapshot);
            if (drewBackdrop) {
                context.notifyMainLayerContentChanged();
            }
        }
    }

    private static boolean drawBackdropTextureWithShader(int left, int top, int right, int bottom, int sampleLeft,
            int sampleTop, int sampleWidth, int sampleHeight, int textureWidth, int textureHeight,
            int downsampleFactor, int blurRadius, float saturation, UiMainLayerSnapshotService.Snapshot snapshot) {
        if (!BACKDROP_SHADER_PROGRAM.ensureInitialized()) {
            recordPath(BackdropFilterRenderPath.FIXED_PIPELINE,
                    "shader unavailable: " + BACKDROP_SHADER_PROGRAM.getLastFailureMessage());
            return false;
        }
        BACKDROP_SHADER_PROGRAM.bind();
        BACKDROP_SHADER_PROGRAM.setUniformI("mainTex", 0);
        BACKDROP_SHADER_PROGRAM.setUniform2f("texelSize", 1.0F / (float) textureWidth, 1.0F / (float) textureHeight);
        BACKDROP_SHADER_PROGRAM.setUniformF("blurRadius", resolveBackdropShaderRadius(blurRadius,
                downsampleFactor));
        BACKDROP_SHADER_PROGRAM.setUniformF("saturation", Math.max(0.0F, saturation));
        drawBackdropTextureQuad(left, top, right, bottom, sampleLeft, sampleTop, sampleWidth, sampleHeight,
                0.0F, 0.0F);
        BACKDROP_SHADER_PROGRAM.unbind();
        recordPath(BackdropFilterRenderPath.SHADER, "blur=" + blurRadius + ", saturation="
                + String.format(java.util.Locale.ROOT, "%.2f", Float.valueOf(Math.max(0.0F, saturation)))
                + ", snapshot=" + formatSnapshotState(snapshot));
        return true;
    }

    private static void drawBackdropTextureQuad(int left, int top, int right, int bottom, int sampleLeft, int sampleTop,
            int sampleWidth, int sampleHeight, float sampleOffsetX, float sampleOffsetY) {
        float leftU = clampFloat(((float) left + sampleOffsetX - (float) sampleLeft) / (float) sampleWidth, 0.0F, 1.0F);
        float rightU = clampFloat(((float) right + sampleOffsetX - (float) sampleLeft) / (float) sampleWidth, 0.0F, 1.0F);
        float topV = clampFloat(1.0F - ((float) top + sampleOffsetY - (float) sampleTop) / (float) sampleHeight,
                0.0F, 1.0F);
        float bottomV = clampFloat(1.0F - ((float) bottom + sampleOffsetY - (float) sampleTop) / (float) sampleHeight,
                0.0F, 1.0F);
        net.minecraft.client.renderer.Tessellator tessellator = net.minecraft.client.renderer.Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(left, bottom, 0.0D, leftU, bottomV);
        tessellator.addVertexWithUV(right, bottom, 0.0D, rightU, bottomV);
        tessellator.addVertexWithUV(right, top, 0.0D, rightU, topV);
        tessellator.addVertexWithUV(left, top, 0.0D, leftU, topV);
        tessellator.draw();
    }

    private static void drawTintFallback(UiRenderContext context, int left, int top, int right, int bottom,
            int blurRadius, float saturation, UiBorderRadiusResolver.ResolvedCornerRadii cornerRadii,
            String fallbackDetail) {
        recordPath(BackdropFilterRenderPath.TINT_FALLBACK, fallbackDetail);
        int tintAlpha = clampInt(18 + Math.max(0, blurRadius) * 2 + Math.round(Math.max(0.0F,
                saturation - 1.0F) * 16.0F), 18, 72);
        int highlightAlpha = clampInt(tintAlpha + 22, 32, 96);
        int tintColor = tintAlpha << 24 | 0x00FFFFFF;
        int highlightColor = highlightAlpha << 24 | 0x00FFFFFF;
        context.drawSurface(left, top, right, bottom, tintColor, highlightColor, cornerRadii);
    }

    private static void recordPath(BackdropFilterRenderPath renderPath, String detail) {
        lastRenderPath = renderPath == null ? BackdropFilterRenderPath.NONE : renderPath;
        lastDetail = detail == null ? "" : detail;
    }

    private static int resolveBackdropSampleStep(int blurRadius) {
        return Math.max(1, Math.min(12, Math.round(Math.max(1, blurRadius) / 2.5F)));
    }

    private static float resolveBackdropShaderRadius(int blurRadius, int downsampleFactor) {
        if (blurRadius <= 0) {
            return 0.0F;
        }
        return Math.max(1.0F, Math.min(32.0F, (float) blurRadius * 0.75F
                / (float) Math.max(1, downsampleFactor)));
    }

    private static String formatSnapshotState(UiMainLayerSnapshotService.Snapshot snapshot) {
        if (snapshot == null) {
            return "none";
        }
        return (snapshot.isReused() ? "reused" : "captured") + " " + snapshot.getWidth() + "x"
                + snapshot.getHeight() + " @" + snapshot.getSampleLeft() + "," + snapshot.getSampleTop()
                + " fbo=" + snapshot.getReadFramebufferId() + " rev=" + snapshot.getContentRevision()
                + " region=" + snapshot.getRegionDetail() + " " + snapshot.getTileDetail()
                + " filter=" + snapshot.getFilterDetail();
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private static float clampFloat(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }
}
