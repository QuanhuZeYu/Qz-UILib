package club.heiqi.uilib.ui.render;

import java.nio.ByteBuffer;
import java.util.function.Consumer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * 负责快照降采样和固定管线 separable blur filter pass。
 */
final class SnapshotFilterPassRenderer {

    private static final float[][] FILTER_BLUR_SAMPLES = new float[][]{
            {0.0F, 0.40F},
            {-1.0F, 0.24F},
            {1.0F, 0.24F},
            {-2.0F, 0.06F},
            {2.0F, 0.06F}
    };

    private SnapshotFilterPassRenderer() {
    }

    /**
     * 将原始快照降采样，并按需要执行双 pass 模糊。
     *
     * @param snapshot         快照槽
     * @param downsampleFactor 降采样倍率
     * @param blurRadius       作者侧模糊半径
     * @param disabledForFrame 当前帧是否已禁用 filter pass
     * @param errorSink        filter pass 失败回调
     * @return 是否成功生成降采样纹理
     */
    static boolean downsampleSnapshot(FrameSnapshot snapshot, int downsampleFactor, int blurRadius,
                                      boolean disabledForFrame, Consumer<String> errorSink) {
        if (disabledForFrame) {
            return false;
        }
        int targetWidth = UiMainLayerSnapshotGeometry.resolveDownsampledSize(snapshot.sourceWidth, downsampleFactor);
        int targetHeight = UiMainLayerSnapshotGeometry.resolveDownsampledSize(snapshot.sourceHeight, downsampleFactor);
        if (targetWidth >= snapshot.sourceWidth && targetHeight >= snapshot.sourceHeight) {
            return false;
        }
        int filterPassRadius = UiMainLayerSnapshotGeometry.resolveFilterPassRadius(blurRadius, downsampleFactor);

        try {
            ensureDownsampleTarget(snapshot, targetWidth, targetHeight);
            if (filterPassRadius <= 0) {
                renderFilterPass(snapshot, snapshot.sourceTextureId, snapshot.filteredTextureId, snapshot.sourceWidth,
                        snapshot.sourceHeight, targetWidth, targetHeight, 0, true, errorSink);
            } else {
                renderFilterPass(snapshot, snapshot.sourceTextureId, snapshot.intermediateTextureId,
                        snapshot.sourceWidth, snapshot.sourceHeight, targetWidth, targetHeight, filterPassRadius, true,
                        errorSink);
                renderFilterPass(snapshot, snapshot.intermediateTextureId, snapshot.filteredTextureId,
                        targetWidth, targetHeight, targetWidth, targetHeight, filterPassRadius, false, errorSink);
            }

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, snapshot.filteredTextureId);
            GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR_MIPMAP_LINEAR);
            snapshot.textureId = snapshot.filteredTextureId;
            snapshot.textureWidth = targetWidth;
            snapshot.textureHeight = targetHeight;
            snapshot.downsampleFactor = downsampleFactor;
            snapshot.filterPassRadius = filterPassRadius;
            return true;
        } catch (RuntimeException exception) {
            disableFilterPass(errorSink, exception.getClass().getSimpleName());
            return false;
        } catch (LinkageError error) {
            disableFilterPass(errorSink, error.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 将输入纹理绘制到输出纹理，filterPassRadius 大于 0 时按方向叠加模糊采样。
     *
     * @param snapshot         快照槽
     * @param inputTextureId   输入纹理
     * @param outputTextureId  输出纹理
     * @param inputWidth       输入宽度
     * @param inputHeight      输入高度
     * @param outputWidth      输出宽度
     * @param outputHeight     输出高度
     * @param filterPassRadius filter pass 半径
     * @param horizontal       是否水平 pass
     * @param errorSink        filter pass 失败回调
     */
    static void renderFilterPass(FrameSnapshot snapshot, int inputTextureId, int outputTextureId, int inputWidth,
                                 int inputHeight, int outputWidth, int outputHeight, int filterPassRadius, boolean horizontal,
                                 Consumer<String> errorSink) {
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, snapshot.filterFramebufferId);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D,
                outputTextureId, 0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            disableFilterPass(errorSink, "fbo-incomplete:" + status);
            throw new IllegalStateException("filter pass fbo incomplete: " + status);
        }

        GL20.glUseProgram(0);
        GL11.glViewport(0, 0, outputWidth, outputHeight);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, inputTextureId);
        if (filterPassRadius <= 0) {
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            drawFilterTextureQuad(outputWidth, outputHeight, 0.0F, 0.0F);
            return;
        }

        GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE);
        for (float[] sample : FILTER_BLUR_SAMPLES) {
            float weight = sample[1];
            float offsetPixels = sample[0] * (float) filterPassRadius;
            float offsetU = horizontal ? offsetPixels / (float) Math.max(1, inputWidth) : 0.0F;
            float offsetV = horizontal ? 0.0F : offsetPixels / (float) Math.max(1, inputHeight);
            GL11.glColor4f(weight, weight, weight, weight);
            drawFilterTextureQuad(outputWidth, outputHeight, offsetU, offsetV);
        }
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /**
     * 确保降采样目标纹理与临时 FBO 已按目标尺寸准备好。
     *
     * @param snapshot     快照槽
     * @param targetWidth  目标宽度
     * @param targetHeight 目标高度
     */
    static void ensureDownsampleTarget(FrameSnapshot snapshot, int targetWidth, int targetHeight) {
        if (snapshot.filteredTextureId == 0) {
            snapshot.filteredTextureId = GL11.glGenTextures();
            if (snapshot.filteredTextureId == 0) {
                throw new IllegalStateException("filter texture allocation failed");
            }
        }
        if (snapshot.intermediateTextureId == 0) {
            snapshot.intermediateTextureId = GL11.glGenTextures();
            if (snapshot.intermediateTextureId == 0) {
                throw new IllegalStateException("filter intermediate texture allocation failed");
            }
        }
        if (snapshot.filterFramebufferId == 0) {
            snapshot.filterFramebufferId = GL30.glGenFramebuffers();
            if (snapshot.filterFramebufferId == 0) {
                throw new IllegalStateException("filter framebuffer allocation failed");
            }
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, snapshot.filteredTextureId);
        configureLinearTexture();
        if (snapshot.filteredWidth != targetWidth || snapshot.filteredHeight != targetHeight) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, targetWidth, targetHeight, 0, GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            snapshot.filteredWidth = targetWidth;
            snapshot.filteredHeight = targetHeight;
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, snapshot.intermediateTextureId);
        configureLinearTexture();
        if (snapshot.intermediateWidth != targetWidth || snapshot.intermediateHeight != targetHeight) {
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, targetWidth, targetHeight, 0, GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            snapshot.intermediateWidth = targetWidth;
            snapshot.intermediateHeight = targetHeight;
        }
    }

    /**
     * 将当前绑定的 2D 纹理配置为线性采样和边缘钳制。
     */
    static void configureLinearTexture() {
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
    }

    /**
     * 在当前 draw framebuffer 上绘制完整滤镜纹理 quad。
     *
     * @param targetWidth  目标宽度
     * @param targetHeight 目标高度
     * @param offsetU      U 偏移
     * @param offsetV      V 偏移
     */
    static void drawFilterTextureQuad(int targetWidth, int targetHeight, float offsetU, float offsetV) {
        int previousMatrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE);
        boolean projectionPushed = false;
        boolean modelViewPushed = false;
        try {
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            projectionPushed = true;
            GL11.glLoadIdentity();
            GL11.glOrtho(0.0D, (double) targetWidth, (double) targetHeight, 0.0D, -1.0D, 1.0D);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
            GL11.glPushMatrix();
            modelViewPushed = true;
            GL11.glLoadIdentity();

            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(offsetU, offsetV);
            GL11.glVertex2f(0.0F, (float) targetHeight);
            GL11.glTexCoord2f(1.0F + offsetU, offsetV);
            GL11.glVertex2f((float) targetWidth, (float) targetHeight);
            GL11.glTexCoord2f(1.0F + offsetU, 1.0F + offsetV);
            GL11.glVertex2f((float) targetWidth, 0.0F);
            GL11.glTexCoord2f(offsetU, 1.0F + offsetV);
            GL11.glVertex2f(0.0F, 0.0F);
            GL11.glEnd();
        } finally {
            if (modelViewPushed) {
                GL11.glMatrixMode(GL11.GL_MODELVIEW);
                GL11.glPopMatrix();
            }
            if (projectionPushed) {
                GL11.glMatrixMode(GL11.GL_PROJECTION);
                GL11.glPopMatrix();
            }
            GL11.glMatrixMode(previousMatrixMode);
        }
    }

    private static void disableFilterPass(Consumer<String> errorSink, String detail) {
        if (errorSink != null) {
            errorSink.accept(detail);
        }
    }
}
