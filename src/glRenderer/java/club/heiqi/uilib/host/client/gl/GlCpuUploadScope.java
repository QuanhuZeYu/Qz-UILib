package club.heiqi.uilib.host.client.gl;

import static club.heiqi.uilib.host.client.gl.GlTokens.*;

import java.util.Set;

/** CPU 像素上传的状态边界；调用方显式声明会绑定的纹理目标，未声明的目标不查询或恢复。 */
final class GlCpuUploadScope implements AutoCloseable {
    private static final int[] UNPACK_NAMES = {
        GL_UNPACK_ALIGNMENT,
        GL_UNPACK_ROW_LENGTH,
        GL_UNPACK_IMAGE_HEIGHT,
        GL_UNPACK_SKIP_PIXELS,
        GL_UNPACK_SKIP_ROWS,
        GL_UNPACK_SKIP_IMAGES,
        GL_UNPACK_SWAP_BYTES,
        GL_UNPACK_LSB_FIRST
    };

    private final GlApi gl;
    private final int unit, pbo;
    private final int[] unpackValues = new int[UNPACK_NAMES.length];
    private final int[] textureTargets, textures;

    GlCpuUploadScope(GlApi gl, int... textureTargets) {
        this.gl = gl;
        this.textureTargets = textureTargets.clone();
        textures = new int[textureTargets.length];
        unit = gl.glGetInteger(GL_ACTIVE_TEXTURE);
        pbo = gl.glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        for (int i = 0; i < UNPACK_NAMES.length; i++)
            unpackValues[i] = gl.glGetInteger(UNPACK_NAMES[i]);
        gl.glActiveTexture(GL_TEXTURE0);
        for (int i = 0; i < textureTargets.length; i++)
            textures[i] = gl.glGetInteger(bindingName(textureTargets[i]));
        gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
        for (int name : UNPACK_NAMES)
            gl.glPixelStorei(name, name == GL_UNPACK_ALIGNMENT ? 1 : 0);
    }

    private static int bindingName(int target) {
        switch (target) {
            case GL_TEXTURE_2D:
                return GL_TEXTURE_BINDING_2D;
            case GL_TEXTURE_2D_ARRAY:
                return GL_TEXTURE_BINDING_2D_ARRAY;
            default:
                throw new IllegalArgumentException("不支持的上传纹理目标: " + target);
        }
    }

    /** 上传暂时覆盖 unit0 绑定后，淘汰仍须保护进入态所引用的纹理。 */
    void addSavedTexturesTo(Set<Integer> bound) {
        for (int texture : textures) bound.add(texture);
    }

    @Override
    public void close() {
        for (int i = 0; i < textureTargets.length; i++)
            gl.glBindTexture(textureTargets[i], textures[i]);
        gl.glActiveTexture(unit);
        gl.glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pbo);
        for (int i = 0; i < UNPACK_NAMES.length; i++)
            gl.glPixelStorei(UNPACK_NAMES[i], unpackValues[i]);
    }
}
