package club.heiqi.uilib.host.client.gl;

import club.heiqi.uilib.host.client.font.context.FontRenderContext;

/** preview只读，不改变pass；open负责匹配捕获的program/pipeline代际。 */
public interface GlProgramProvider {
    boolean preview(FontRenderContext context);

    GlProgramScope open(FontRenderContext context);

    /**
     * 首次有效 pass 打开后，由 backend 在所属 GL 上下文内调用。每次返回独立 producer；
     * backend 跨短 scope 缓存并负责关闭，provider 不持有共享 GPU 缓存。
     * 未提供宿主顶点提交的旧实现仍可使用普通路径，不能为有效 pass 猜测顶点 ABI。
     */
    default GlVertexProducer createVertexProducer(GlApi gl, int capacity) {
        throw new UnsupportedOperationException("有效宿主 pass 未提供 vertex producer");
    }

    GlProgramProvider NONE =
            new GlProgramProvider() {
                public boolean preview(FontRenderContext context) {
                    return false;
                }

                public GlProgramScope open(FontRenderContext context) {
                    return null;
                }
            };
}
