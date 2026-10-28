package club.heiqi.uilib.host.client.font.compat;

import java.util.Objects;
import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;

/** 原版 adapter 的可选提交桥；submit 接管 sourceLease，父 owner 持有字体 engine/atlas，
 * 负责需求合并与回放期 Prepared。替代开关由 adapter 决定。 */
public interface OptionalFontHostBridge extends AutoCloseable {
    interface ReplaySink {
        void draw(PaintPlan plan, FontRenderContext context);

        /** 延期回放在提交时登记本帧可见需求；即时 draw 在 prepare 中完成同一步骤。 */
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
                // 即时 draw 的 prepare 统一完成采样、可见需求合并与光栅请求。
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
