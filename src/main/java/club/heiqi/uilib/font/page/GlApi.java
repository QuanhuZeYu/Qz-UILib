package club.heiqi.uilib.font.page;

import java.nio.ByteBuffer;

/**
 * 字符页 GL 操作门面。
 *
 * <p>字符页的全部 GL 触点经本接口进入，使上传/回滚/纹理生命周期与具体 GL 绑定解耦：
 * 真机走 LWJGL 实现；headless 场地（软件渲染验收）注入软件纹理实现，字形像素保留在
 * CPU 侧供软件光栅化器采样。实现必须单线程使用（渲染主线程专用）。</p>
 */
public interface GlApi {

    void pushAttrib(int mask);

    void pushClientAttrib(int mask);

    void popClientAttrib();

    void popAttrib();

    int genTexture();

    void bindTexture(int target, int texture);

    void pixelStore(int parameter, int value);

    void texImage2D(int target, int level, int internalFormat, int width, int height, int border, int format,
            int type, ByteBuffer pixels);

    void texParameter(int target, int parameter, int value);

    void texSubImage2D(int target, int level, int x, int y, int width, int height, int format, int type,
            ByteBuffer pixels);

    void generateMipmap(int target);

    boolean isTexture(int texture);

    void deleteTexture(int texture);

    int getError();
}
