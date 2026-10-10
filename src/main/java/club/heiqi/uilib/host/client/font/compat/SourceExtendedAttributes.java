package club.heiqi.uilib.host.client.font.compat;

import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.gl.GlTokens;

/** 固定Angelica 2.2.10非terrain字体的来源generic值副本；没有atlas、VAO或nativebuffer引用。
 * GbufferPrograms.setBlockEntityDefaults实际写attr11=(-1,-1,0,1)、attr14=(0,0,0,0)。
 * ImmediateExtendedAttribHandler只生成12/midUV、13/tangent；entity/item/block ID属于uniform，不能填作mc_Entity。
 * 这里捕实际来源current value，未使用没有来源的block center推算。terrain的uint packedmcEntity是另一ABI。
 * generic setter在GLSM直达backend，无独立logicalcache，glGetVertexAttribfv是该来源事实的读取。 */
public final class SourceExtendedAttributes {
    private final float[] mcEntity, midBlock;

    private SourceExtendedAttributes(float[] mcEntity, float[] midBlock) {
        this.mcEntity = copy(mcEntity);
        this.midBlock = copy(midBlock);
    }

    private static float[] copy(float[] value) {
        float[] result = value.clone();
        for (float x : result)
            if (!Float.isFinite(x)) throw new IllegalStateException("非有限来源extended属性");
        return result;
    }

    static SourceExtendedAttributes capture(GlApi gl) {
        return new SourceExtendedAttributes(current(gl, 11), current(gl, 14));
    }

    private static float[] current(GlApi gl, int location) {
        float[] values = new float[4];
        gl.glGetVertexAttribfv(location, GlTokens.GL_CURRENT_VERTEX_ATTRIB, values);
        return values;
    }

    public float[] mcEntity() {
        return mcEntity.clone();
    }

    public float[] midBlock() {
        return midBlock.clone();
    }

    /** 仅匹配实际FFP浮点ABI；uint/ivec packedterrain以及额外属性须明确来源协议，不能猜值。 */
    public float[] valuesFor(String name, int shaderType) {
        if (shaderType != GlTokens.GL_FLOAT
                && shaderType != GlTokens.GL_FLOAT_VEC2
                && shaderType != GlTokens.GL_FLOAT_VEC3
                && shaderType != GlTokens.GL_FLOAT_VEC4)
            throw new IllegalStateException(
                    "字体来源FFP不提供packed/integer属性：" + name + " type=" + shaderType);
        if ("mc_Entity".equals(name)) return mcEntity();
        if ("at_midBlock".equals(name)) return midBlock();
        throw new IllegalStateException("未知字体extended属性语义：" + name);
    }
}
