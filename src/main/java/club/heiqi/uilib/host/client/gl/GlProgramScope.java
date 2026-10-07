package club.heiqi.uilib.host.client.gl;

import java.util.List;

/** 可选宿主program pass；有效矩阵、实体、纹理缓存及Pass.use由宿主实现并短scope恢复。 */
public interface GlProgramScope extends AutoCloseable {
    int programId();List<GlProgramAttribute> attributes();float[] normal();int packedLight();float[] extended(String name,int type);
    void configure(int vao,int vbo,int ebo,boolean uv,boolean color,boolean lightmap,boolean normal);
    void pointer(int location,int size,int type,boolean normalized,int stride,long offset);void disable(int location);
    void bindTexture2D(int texture);void beforeDraw();void close();
}
