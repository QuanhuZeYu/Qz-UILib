package club.heiqi.uilib.host.client.gl;

import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch;

/**
 * 宿主 program 的有界顶点提交；接收同一有序载荷，不拥有 program/pass 或资源纹理。
 * provider 每次创建独立实例，由单个 backend 缓存并在有效 GL 上下文线程关闭。
 */
public interface GlVertexProducer extends AutoCloseable {
    /** 每个短 pass 内匹配当前顶点布局；宿主虚拟状态通过 scope 同步。 */
    void configure(GlProgramScope scope);

    /** 一次上传并按原 draw 顺序提交，返回实际上传字节数。 */
    long consume(
            OrderedQuadBatch batch,
            GlProgramScope scope,
            FontRenderContext context,
            GlQuadRenderer.ResourceProvider resources);

    /** 只释放自身 GPU 资源，不关闭短 pass；必须在所属有效上下文中调用。 */
    @Override
    void close();
}
