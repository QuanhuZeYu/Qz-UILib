package club.heiqi.uilib.host.client.font;

import club.heiqi.uilib.ui.media.MediaPresentation;
import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.gl.GlPaintPlanRenderer;
import club.heiqi.uilib.host.client.gl.GlVideoSubmissions;
import club.heiqi.uilib.host.client.gl.GlVideoResources;



import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import java.util.List;
import java.util.Objects;
import static club.heiqi.uilib.host.client.gl.GlTokens.*;

/** 自有 framebuffer consumer；视频资源同级拥有，字体/图像和媒体会话继续按原归属借用。 */
public final class FramebufferSceneRenderer implements AutoCloseable {
    private final ClientFontServices owner;
    private GlPaintPlanRenderer renderer;
    private boolean closed;
    private final club.heiqi.uilib.host.client.image.ClientImageServices images;
    private final GlVideoResources videos;
    private final boolean deferredPresentation;
    private final GlVideoSubmissions submissions = new GlVideoSubmissions(16);
    public FramebufferSceneRenderer(ClientFontServices owner) { this(owner, null); }
    public FramebufferSceneRenderer(ClientFontServices owner, club.heiqi.uilib.host.client.image.ClientImageServices images) {
        this(owner, images, null, false);
    }
    /** deferredPresentation 由拥有交换边界的宿主选择；MC只承诺当前framebuffer提交。 */
    public FramebufferSceneRenderer(ClientFontServices owner, club.heiqi.uilib.host.client.image.ClientImageServices images,
            MediaService media, boolean deferredPresentation) {
        this.owner = Objects.requireNonNull(owner, "owner"); this.images = images;
        this.videos = media == null ? null : new GlVideoResources(owner.gl(), media);
        this.deferredPresentation = deferredPresentation;
    }
    public void replay(PaintPlan plan, int width, int height) {
        if (closed) throw new IllegalStateException("screen renderer已关闭");
        owner.pumpReady();
        if (width <= 0 || height <= 0) return;
        if (renderer == null) renderer = owner.createPlanRenderer(images == null ? null : images.textures(), videos);
        GlApi gl = owner.gl(); int[] scissor = new int[4], viewport = new int[4];
        gl.glGetIntegerv(GL_SCISSOR_BOX, scissor); gl.glGetIntegerv(GL_VIEWPORT, viewport);
        LayoutBox visible = FramebufferCoordinates.viewportClip(width, height, gl.glIsEnabled(GL_SCISSOR_TEST), scissor);
        try {
            try (GlPaintPlanRenderer.Prepared prepared = owner.prepare(renderer, plan, visible)) {
                gl.glViewport(0, 0, width, height);
                try { prepared.render(FramebufferCoordinates.orthographic(width, height), height, false); }
                finally { gl.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]); }
                submissions.add(prepared.takeVideoSubmissions());
            }
            if (!deferredPresentation) completePresentation(System.nanoTime());
        } catch (RuntimeException | Error failure) {
            try { discardPresentation(); } catch (Throwable next) { if (next != failure) failure.addSuppressed(next); }
            throw failure;
        }
    }
    /** swap成功或明确的framebuffer提交边界调用；只报告已flush的key，finally释放全部票据。 */
    public List<MediaPresentation> completePresentation(long sampledNanos) {
        if (closed) throw new IllegalStateException("screen renderer已关闭");
        return submissions.complete(sampledNanos);
    }
    public void discardPresentation() { submissions.close(); }
    public int pendingPresentations() { return submissions.pendingCount(); }
    public GlVideoResources.Snapshot mediaResources() { return videos==null?GlVideoResources.Snapshot.EMPTY:videos.snapshot(); }
    @Override public void close() {
        if (closed) return; closed = true; Throwable failure = null;
        AutoCloseable[] resources = {submissions, () -> { if (renderer != null) owner.releasePlanRenderer(renderer); }, videos};
        for (AutoCloseable resource : resources) if (resource != null) try { resource.close(); }
        catch (Throwable next) { if (failure == null) failure = next; else if (failure != next) failure.addSuppressed(next); }
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
        if (failure != null) throw new IllegalStateException("framebuffer资源释放失败", failure);
    }
}
