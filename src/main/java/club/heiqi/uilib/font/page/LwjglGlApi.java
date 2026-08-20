package club.heiqi.uilib.font.page;

import java.nio.ByteBuffer;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 * LWJGL 实现的 {@link GlApi}（渲染主线程专用，包内单例）。
 */
final class LwjglGlApi implements GlApi {

    static final LwjglGlApi INSTANCE = new LwjglGlApi();

    private LwjglGlApi() {}

    @Override
    public void pushAttrib(int mask) {
        GL11.glPushAttrib(mask);
    }

    @Override
    public void pushClientAttrib(int mask) {
        GL11.glPushClientAttrib(mask);
    }

    @Override
    public void popClientAttrib() {
        GL11.glPopClientAttrib();
    }

    @Override
    public void popAttrib() {
        GL11.glPopAttrib();
    }

    @Override
    public int genTexture() {
        return GL11.glGenTextures();
    }

    @Override
    public void bindTexture(int target, int texture) {
        GL11.glBindTexture(target, texture);
    }

    @Override
    public void pixelStore(int parameter, int value) {
        GL11.glPixelStorei(parameter, value);
    }

    @Override
    public void texImage2D(int target, int level, int internalFormat, int width, int height, int border,
            int format, int type, ByteBuffer pixels) {
        GL11.glTexImage2D(target, level, internalFormat, width, height, border, format, type, pixels);
    }

    @Override
    public void texParameter(int target, int parameter, int value) {
        GL11.glTexParameteri(target, parameter, value);
    }

    @Override
    public void texSubImage2D(int target, int level, int x, int y, int width, int height, int format, int type,
            ByteBuffer pixels) {
        GL11.glTexSubImage2D(target, level, x, y, width, height, format, type, pixels);
    }

    @Override
    public void generateMipmap(int target) {
        GL30.glGenerateMipmap(target);
    }

    @Override
    public boolean isTexture(int texture) {
        return GL11.glIsTexture(texture);
    }

    @Override
    public void deleteTexture(int texture) {
        GL11.glDeleteTextures(texture);
    }

    @Override
    public int getError() {
        return GL11.glGetError();
    }
}
