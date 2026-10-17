package club.heiqi.uilib.host.embedded;

import java.util.function.IntSupplier;
import club.heiqi.uilib.host.client.font.FramebufferSceneRenderer;
import club.heiqi.uilib.ui.media.MediaService;
import club.heiqi.uilib.ui.scene.control.ThemeConfiguration;
import club.heiqi.uilib.ui.scene.host.SceneHost;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import club.heiqi.uilib.ui.scene.video.MediaViewPump;

/** 一次页面的 GPU consumer；应用服务借用到页面结束，页面不释放应用配置及字体缓存。 */
public final class EmbeddedSceneHost implements SceneHost, AutoCloseable {
    private final EmbeddedSceneServices services;
    private final IntSupplier width, height;
    private final FramebufferSceneRenderer renderer;
    private final MediaViewPump mediaViews;

    public EmbeddedSceneHost(EmbeddedSceneServices services, IntSupplier width, IntSupplier height, MediaService media) {
        this.services = java.util.Objects.requireNonNull(services, "services");
        this.width = java.util.Objects.requireNonNull(width, "width");
        this.height = java.util.Objects.requireNonNull(height, "height");
        renderer = new FramebufferSceneRenderer(services.fonts(), services.clientImages(), media, false);
        mediaViews = media == null ? null : new MediaViewPump();
    }
    @Override public int width() { return width.getAsInt(); }
    @Override public int height() { return height.getAsInt(); }
    @Override public long metricsRevision() { return services.fonts().fonts().generation(); }
    @Override public ThemeConfiguration themeConfiguration() { return services.themes(); }
    @Override public MediaViewPump mediaViews() { return mediaViews; }
    @Override public void prepareResources() {
        services.clientImages().prepareResources();
        if (mediaViews != null) mediaViews.prepareResources();
    }
    @Override public void replay(PaintPlan plan) { renderer.replay(plan, width(), height()); }
    @Override public void close() {
        Throwable failure = null;
        try { renderer.close(); } catch (RuntimeException | Error next) { failure = next; }
        try { if (mediaViews != null) mediaViews.close(); }
        catch (RuntimeException | Error next) { if (failure == null) failure = next; else if (next != failure) failure.addSuppressed(next); }
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
    }
}
