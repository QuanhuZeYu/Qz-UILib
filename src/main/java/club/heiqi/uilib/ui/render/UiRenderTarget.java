package club.heiqi.uilib.ui.render;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;


import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

/**
 * UI 原生分辨率离屏渲染目标。
 */
public class UiRenderTarget {

    private IntBuffer previousViewport;

    private int framebufferId;
    private int colorTextureId;
    private int depthStencilRenderbufferId;
    private int width;
    private int height;
    private int previousDrawFramebufferId;
    private int previousReadFramebufferId;
    private boolean attribStatePushed;

    /**
     * 确保离屏目标尺寸与当前窗口一致。
     *
     * @param width 原生窗口宽度
     * @param height 原生窗口高度
     */
    public void ensureSize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (framebufferId == 0) {
            initialize(width, height);
            return;
        }
        if (this.width != width || this.height != height) {
            resize(width, height);
        }
    }

    /**
     * 绑定离屏目标并记录先前状态。
     */
    public void begin() {
        if (previousViewport == null) {
            previousViewport = BufferUtils.createIntBuffer(16);
        }
        previousViewport.clear();
        GL11.glGetInteger(GL11.GL_VIEWPORT, previousViewport);
        previousViewport.limit(4);
        previousViewport.rewind();
        previousDrawFramebufferId = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        previousReadFramebufferId = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try {
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            attribStatePushed = true;
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferId);
            GL11.glViewport(0, 0, width, height);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glStencilMask(0xFF);
            GL11.glColorMask(true, true, true, true);
            GL11.glDepthMask(true);
            GL11.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
        } catch (RuntimeException exception) {
            restoreAfterBeginFailure(exception);
            throw exception;
        } catch (LinkageError error) {
            restoreAfterBeginFailure(error);
            throw error;
        } catch (Error error) {
            restoreAfterBeginFailure(error);
            throw error;
        }
    }

    /**
     * 恢复先前 FBO 与视口。
     */
    public void end() {
        restoreAfterBegin();
    }

    private void restoreAfterBegin() {
        Throwable[] failure = new Throwable[1];
        restoreStep(failure,
                () -> GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebufferId));
        restoreStep(failure,
                () -> GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebufferId));
        restoreStep(failure, () -> GL11.glViewport(previousViewport.get(0), previousViewport.get(1),
                previousViewport.get(2), previousViewport.get(3)));
        if (attribStatePushed) {
            restoreStep(failure, () -> {
                GL11.glPopAttrib();
                attribStatePushed = false;
            });
        }
        if (failure[0] == null) {
            previousDrawFramebufferId = 0;
            previousReadFramebufferId = 0;
        }
        rethrowCloseFailure(failure[0]);
    }

    private void restoreAfterBeginFailure(Throwable primaryFailure) {
        try {
            restoreAfterBegin();
        } catch (RuntimeException cleanupFailure) {
            rethrowCloseFailure(appendCloseFailure(primaryFailure, cleanupFailure));
        } catch (Error cleanupFailure) {
            rethrowCloseFailure(appendCloseFailure(primaryFailure, cleanupFailure));
        }
    }

    /**
     * 将离屏结果按 MC 当前 GUI 视口绘制到屏幕。
     *
     * @param guiWidth GUI 逻辑宽度
     * @param guiHeight GUI 逻辑高度
     */
    public void drawToScreen(int guiWidth, int guiHeight) {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            // 离屏纹理中的 RGB 已经是在透明背景上完成过一次合成的结果。
            // 若这里仍按 straight alpha 做 SRC_ALPHA 混合，会让半透明像素在 present 时再次乘 alpha，
            // 进而压暗带透明叠层的物品贴图。这里按已完成合成的 UI 层进行回贴，只使用目标端衰减。
            GL11.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTextureId);

            // 架构禁令:不使用原版包装类(Tessellator),直接 GL 立即模式
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0F, 0.0F);
            GL11.glVertex2f(0.0F, guiHeight);
            GL11.glTexCoord2f(1.0F, 0.0F);
            GL11.glVertex2f(guiWidth, guiHeight);
            GL11.glTexCoord2f(1.0F, 1.0F);
            GL11.glVertex2f(guiWidth, 0.0F);
            GL11.glTexCoord2f(0.0F, 1.0F);
            GL11.glVertex2f(0.0F, 0.0F);
            GL11.glEnd();

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        } finally {
            GL11.glPopAttrib();
        }
    }

    /**
     * 将当前离屏纹理合成到已绑定的目标 FBO，同时保留目标端既有 alpha。
     *
     * <p>该方法假设当前纹理内容已经在透明背景上完成过一次内部合成，
     * 因而 RGB 需要按预乘结果直接回贴；alpha 则完全保留目标端，
     * 避免物品层覆盖主 UI 层已经建立好的最终 coverage。</p>
     */
    public void compositeToCurrentFramebuffer() {
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTextureId);

            // 架构禁令:不使用原版包装类(Tessellator),直接 GL 立即模式
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(0.0F, 0.0F);
            GL11.glVertex2f(0.0F, height);
            GL11.glTexCoord2f(1.0F, 0.0F);
            GL11.glVertex2f(width, height);
            GL11.glTexCoord2f(1.0F, 1.0F);
            GL11.glVertex2f(width, 0.0F);
            GL11.glTexCoord2f(0.0F, 1.0F);
            GL11.glVertex2f(0.0F, 0.0F);
            GL11.glEnd();

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        } finally {
            GL11.glPopAttrib();
        }
    }

    /**
     * 将当前离屏纹理按指定 group opacity 合成回已绑定的目标 FBO。
     *
     * <p>该路径保留当前 scissor/stencil 裁剪状态，让 paint context 可以继承外层 overflow clip。
     * 纹理内容按预乘 alpha 处理，回贴时同时更新目标 alpha。</p>
     *
     * @param left 回贴左边界
     * @param top 回贴上边界
     * @param right 回贴右边界
     * @param bottom 回贴下边界
     * @param opacity group opacity
     */
    public void compositeToCurrentFramebuffer(int left, int top, int right, int bottom, float opacity) {
        int clippedLeft = clampInt(Math.min(left, right), 0, width);
        int clippedTop = clampInt(Math.min(top, bottom), 0, height);
        int clippedRight = clampInt(Math.max(left, right), 0, width);
        int clippedBottom = clampInt(Math.max(top, bottom), 0, height);
        float clampedOpacity = Math.max(0.0F, Math.min(1.0F, opacity));
        if (clippedRight <= clippedLeft || clippedBottom <= clippedTop || clampedOpacity <= 0.0F) {
            return;
        }

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glColor4f(clampedOpacity, clampedOpacity, clampedOpacity, clampedOpacity);
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTextureId);

            float leftU = (float) clippedLeft / (float) width;
            float rightU = (float) clippedRight / (float) width;
            float topV = 1.0F - (float) clippedTop / (float) height;
            float bottomV = 1.0F - (float) clippedBottom / (float) height;

            // 架构禁令:不使用原版包装类(Tessellator),直接 GL 立即模式
            GL11.glBegin(GL11.GL_QUADS);
            GL11.glTexCoord2f(leftU, bottomV);
            GL11.glVertex2f(clippedLeft, clippedBottom);
            GL11.glTexCoord2f(rightU, bottomV);
            GL11.glVertex2f(clippedRight, clippedBottom);
            GL11.glTexCoord2f(rightU, topV);
            GL11.glVertex2f(clippedRight, clippedTop);
            GL11.glTexCoord2f(leftU, topV);
            GL11.glVertex2f(clippedLeft, clippedTop);
            GL11.glEnd();

            GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
            GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        } finally {
            GL11.glPopAttrib();
        }
    }

    /**
     * 释放离屏目标资源。
     */
    public void close() {
        Throwable firstFailure = null;
        if (colorTextureId != 0) {
            try {
                GL11.glDeleteTextures(colorTextureId);
                colorTextureId = 0;
            } catch (RuntimeException failure) {
                firstFailure = appendCloseFailure(firstFailure, failure);
            } catch (LinkageError failure) {
                firstFailure = appendCloseFailure(firstFailure, failure);
            } catch (Error failure) {
                firstFailure = appendCloseFailure(firstFailure, failure);
            }
        }
        if (depthStencilRenderbufferId != 0) {
            try {
                GL30.glDeleteRenderbuffers(depthStencilRenderbufferId);
                depthStencilRenderbufferId = 0;
            } catch (RuntimeException failure) {
                firstFailure = appendCloseFailure(firstFailure, failure);
            } catch (LinkageError failure) {
                firstFailure = appendCloseFailure(firstFailure, failure);
            } catch (Error failure) {
                firstFailure = appendCloseFailure(firstFailure, failure);
            }
        }
        if (framebufferId != 0) {
            try {
                GL30.glDeleteFramebuffers(framebufferId);
                framebufferId = 0;
            } catch (RuntimeException failure) {
                firstFailure = appendCloseFailure(firstFailure, failure);
            } catch (LinkageError failure) {
                firstFailure = appendCloseFailure(firstFailure, failure);
            } catch (Error failure) {
                firstFailure = appendCloseFailure(firstFailure, failure);
            }
        }
        width = 0;
        height = 0;
        if (firstFailure instanceof RuntimeException) throw (RuntimeException) firstFailure;
        if (firstFailure instanceof LinkageError) throw (LinkageError) firstFailure;
        if (firstFailure instanceof Error) throw (Error) firstFailure;
    }

    private static Throwable appendCloseFailure(Throwable firstFailure, Throwable nextFailure) {
        if (firstFailure == null) return nextFailure;
        if (isFatal(nextFailure) && !isFatal(firstFailure)) {
            if (firstFailure != nextFailure) nextFailure.addSuppressed(firstFailure);
            return nextFailure;
        }
        if (firstFailure != nextFailure) firstFailure.addSuppressed(nextFailure);
        return firstFailure;
    }

    private static void restoreStep(Throwable[] firstFailure, Runnable step) {
        try {
            step.run();
        } catch (RuntimeException failure) {
            firstFailure[0] = appendCloseFailure(firstFailure[0], failure);
        } catch (Error failure) {
            firstFailure[0] = appendCloseFailure(firstFailure[0], failure);
        }
    }

    private static void rethrowCloseFailure(Throwable failure) {
        if (failure == null) return;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure instanceof Error) throw (Error) failure;
        throw new IllegalStateException("render target state restore failed", failure);
    }

    private static boolean isFatal(Throwable failure) {
        return failure instanceof Error && !(failure instanceof LinkageError);
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /**
     * 返回当前颜色附件纹理 id。
     *
     * @return 颜色纹理 id
     */
    public int getColorTextureId() {
        return colorTextureId;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }

    private void initialize(int width, int height) {
        framebufferId = GL30.glGenFramebuffers();
        colorTextureId = GL11.glGenTextures();
        depthStencilRenderbufferId = GL30.glGenRenderbuffers();
        this.width = width;
        this.height = height;
        allocateAttachments();
    }

    private void resize(int width, int height) {
        this.width = width;
        this.height = height;
        allocateAttachments();
    }

    private void allocateAttachments() {
        int previousDrawFramebufferId = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousReadFramebufferId = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousTextureId = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousRenderbufferId = GL11.glGetInteger(GL30.GL_RENDERBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebufferId);

        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorTextureId);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D,
                    colorTextureId, 0);

            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, depthStencilRenderbufferId);
            GL30.glRenderbufferStorage(GL30.GL_RENDERBUFFER, GL30.GL_DEPTH24_STENCIL8, width, height);
            GL30.glFramebufferRenderbuffer(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT,
                    GL30.GL_RENDERBUFFER, depthStencilRenderbufferId);

            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("UI 离屏渲染目标创建失败，状态码=" + status);
            }
        } finally {
            GL30.glBindRenderbuffer(GL30.GL_RENDERBUFFER, previousRenderbufferId);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTextureId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDrawFramebufferId);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousReadFramebufferId);
        }
    }
}
