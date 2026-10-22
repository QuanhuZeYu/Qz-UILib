package club.heiqi.uilib.host.client.font.compat;

import java.util.Objects;
import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;

/** 原版adapter可选宿主桥；不需要scene，不拥有字体engine/atlas。
 * submit接管sourceLease，父owner负责需求union和回放短Prepared。关闭替代开关由adapter决定。 */
public interface OptionalFontHostBridge extends AutoCloseable {
    interface ReplaySink {
        void draw(PaintPlan plan, FontRenderContext context);

        /** 仅供延期回放：提交时先把可见字形并入本帧需求。即时draw自带需求准备，不应再先登记。 */
        void registerVisible(PaintPlan plan, FontRenderContext context);

        FontRenderContext capture();
    }

    void submit(PaintPlan plan, FontRenderContext context, AutoCloseable sourceLease);

    void abort();

    int pending();

    @Override
    void close();

    static OptionalFontHostBridge create(ClassLoader loader, ReplaySink sink, int queueCapacity) {
        return create(loader, null, sink, queueCapacity);
    }

    static OptionalFontHostBridge create(
            ClassLoader loader, GlApi gl, ReplaySink sink, int queueCapacity) {
        Objects.requireNonNull(loader, "loader");
        Objects.requireNonNull(sink, "sink");
        for (club.heiqi.uilib.host.client.extension.MinecraftHostExtension service :
                club.heiqi.uilib.host.client.extension.MinecraftHostExtensions.services()) {
            OptionalFontHostBridge bridge = service.fontBridge(loader, gl, sink, queueCapacity);
            if (bridge != null) return bridge;
        }
        return new Direct(sink);
    }

    final class Direct implements OptionalFontHostBridge {
        private final ReplaySink sink;
        private boolean closed;

        Direct(ReplaySink sink) {
            this.sink = sink;
        }

        @Override
        public void submit(PaintPlan plan, FontRenderContext context, AutoCloseable lease) {
            Objects.requireNonNull(lease, "lease");
            try {
                if (closed) throw new IllegalStateException("font bridge已关闭");
                // #81 即时回放的prepare已按同一plan/context并入整帧需求并请求光栅；
                // registerVisible只服务延期回放，在此重复会使每次drawString采样与可见性准备各做两遍。
                sink.draw(plan, context);
            } finally {
                FontHostOrderQueue.release(lease);
            }
        }

        @Override
        public void abort() {}

        @Override
        public int pending() {
            return 0;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
