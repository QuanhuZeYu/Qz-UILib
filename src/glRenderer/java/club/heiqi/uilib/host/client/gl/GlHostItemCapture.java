package club.heiqi.uilib.host.client.gl;

import static club.heiqi.uilib.host.client.gl.GlTokens.*;

import club.heiqi.uilib.ui.scene.image.ImagePlacement;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;

/** 透明物品的临时合成面；每次执行宿主委托，立即由原 quad backend 消费，不缓存动态外观。
 * 单个 RGBA8 + DEPTH24_STENCIL8 面只覆盖可见图像区域，旧面先释放再调整尺寸。
 */
final class GlHostItemCapture implements AutoCloseable {
    static final long RESOURCE = Long.MIN_VALUE;
    private static final float[] TRANSPARENT = {0, 0, 0, 0};
    private final GlApi gl;
    private final long maxBytes;
    private int framebuffer, texture, depthStencil, width, height;
    private long generation;

    static final class Capture {
        final LayoutBox box;
        final long generation;

        Capture(LayoutBox box, long generation) {
            this.box = box;
            this.generation = generation;
        }

        boolean empty() { return box.width == 0 || box.height == 0; }
    }

    GlHostItemCapture(GlApi gl, long maxBytes) {
        if (maxBytes < 8) throw new IllegalArgumentException("无效物品合成预算");
        this.gl = gl;
        this.maxBytes = maxBytes;
    }

    Capture capture(ImagePlacement image, LayoutBox clip, int framebufferWidth, int framebufferHeight,
            GlImageResources.HostItems host) {
        int left = (int) Math.min(framebufferWidth, Math.max(0, Math.floor(image.x)));
        int top = (int) Math.min(framebufferHeight, Math.max(0, Math.floor(image.y)));
        int right = (int) Math.max(0, Math.min(framebufferWidth, Math.ceil((double) image.x + image.width)));
        int bottom = (int) Math.max(0, Math.min(framebufferHeight, Math.ceil((double) image.y + image.height)));
        if (clip != null) {
            left = Math.max(left, clip.x);
            top = Math.max(top, clip.y);
            right = (int) Math.min(right, (long) clip.x + clip.width);
            bottom = (int) Math.min(bottom, (long) clip.y + clip.height);
        }
        LayoutBox box = new LayoutBox(left, top, Math.max(0, right - left), Math.max(0, bottom - top));
        if (box.width == 0 || box.height == 0) return new Capture(box, 0);
        long required = (long) box.width * box.height * 8;
        int maximum = gl.glGetInteger(GL_MAX_TEXTURE_SIZE);
        if (box.width > maximum || box.height > maximum || required > maxBytes)
            throw new IllegalArgumentException("物品合成超出GL尺寸/字节预算: " + box.width + "x"
                    + box.height + ", bytes=" + required + ", budget=" + maxBytes);
        if (++generation <= 0) throw new IllegalStateException("物品合成代际耗尽");
        try (TargetScope saved = new TargetScope()) {
            allocate(box.width, box.height);
            gl.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
            gl.glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer);
            gl.glViewport(0, 0, width, height);
            gl.glDisable(GL_SCISSOR_TEST);
            gl.glDisable(GL_STENCIL_TEST);
            gl.glColorMask(true, true, true, true);
            gl.glDepthMask(true);
            gl.glStencilMaskSeparate(GL_FRONT, -1);
            gl.glStencilMaskSeparate(GL_BACK, -1);
            gl.glClearBufferfv(GL_COLOR, 0, TRANSPARENT);
            gl.glClearBufferfi(GL_DEPTH_STENCIL, 0, 1, 0);
            // 中间面必须可写alpha；caller的写掩码仅在最终合成时生效，由外层恢复。
            // 旧 HostItems 接口仍接收完整的 ImagePlacement/目标尺寸；仅透明用法改为局部 framebuffer。
            // 宿主自身的 ItemStateScope 继续隔离原版/Forge hook，外层只恢复合成面触碰的状态。
            host.render(image.translated(-left, -top), new LayoutBox(0, 0, width, height), width, height);
        }
        return new Capture(box, generation);
    }

    private void allocate(int requestedWidth, int requestedHeight) {
        if (texture != 0 && width == requestedWidth && height == requestedHeight) return;
        close();
        try (GlCpuUploadScope ignored = new GlCpuUploadScope(gl, GL_TEXTURE_2D)) {
            framebuffer = gl.glGenFramebuffers();
            texture = gl.glGenTextures();
            depthStencil = gl.glGenRenderbuffers();
            gl.glBindTexture(GL_TEXTURE_2D, texture);
            gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            gl.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            gl.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, requestedWidth, requestedHeight,
                    0, GL_RGBA, GL_UNSIGNED_BYTE, null);
            gl.glBindRenderbuffer(GL_RENDERBUFFER, depthStencil);
            gl.glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH24_STENCIL8, requestedWidth, requestedHeight);
            gl.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
            gl.glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, texture, 0);
            gl.glFramebufferRenderbuffer(GL_DRAW_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, depthStencil);
            int status = gl.glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER);
            if (status != GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("物品合成framebuffer不完整: " + status);
            width = requestedWidth;
            height = requestedHeight;
        } catch (RuntimeException | Error failure) {
            try { close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    GlQuadRenderer.Texture resolve(long expectedGeneration) {
        if (texture == 0 || generation != expectedGeneration)
            throw new IllegalArgumentException("失效物品合成引用");
        return new GlQuadRenderer.Texture(GL_TEXTURE_2D, texture);
    }

    long retainedBytes() { return (long) width * height * 8; }

    private final class TargetScope implements AutoCloseable {
        final int draw = gl.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        final int read = gl.glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        final int renderbuffer = gl.glGetInteger(GL_RENDERBUFFER_BINDING);
        final boolean scissor = gl.glIsEnabled(GL_SCISSOR_TEST);
        final boolean stencil = gl.glIsEnabled(GL_STENCIL_TEST);
        final boolean depthMask = gl.glGetBoolean(GL_DEPTH_WRITEMASK);
        final int stencilFront = gl.glGetInteger(GL_STENCIL_WRITEMASK);
        final int stencilBack = gl.glGetInteger(GL_STENCIL_BACK_WRITEMASK);
        final int[] viewport = new int[4], scissorBox = new int[4], colorMask = new int[4];

        TargetScope() {
            gl.glGetIntegerv(GL_VIEWPORT, viewport);
            gl.glGetIntegerv(GL_SCISSOR_BOX, scissorBox);
            gl.glGetIntegerv(GL_COLOR_WRITEMASK, colorMask);
        }

        void restoreMasks() {
            gl.glColorMask(colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0);
            gl.glDepthMask(depthMask);
            gl.glStencilMaskSeparate(GL_FRONT, stencilFront);
            gl.glStencilMaskSeparate(GL_BACK, stencilBack);
        }

        @Override public void close() {
            gl.glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw);
            gl.glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
            gl.glBindRenderbuffer(GL_RENDERBUFFER, renderbuffer);
            gl.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            gl.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            if (scissor) gl.glEnable(GL_SCISSOR_TEST); else gl.glDisable(GL_SCISSOR_TEST);
            if (stencil) gl.glEnable(GL_STENCIL_TEST); else gl.glDisable(GL_STENCIL_TEST);
            restoreMasks();
        }
    }

    @Override public void close() {
        try { if (framebuffer != 0) gl.glDeleteFramebuffers(framebuffer); }
        finally {
            framebuffer = 0;
            try { if (depthStencil != 0) gl.glDeleteRenderbuffers(depthStencil); }
            finally {
                depthStencil = 0;
                try { if (texture != 0) gl.glDeleteTextures(texture); }
                finally { texture = width = height = 0; }
            }
        }
    }
}
