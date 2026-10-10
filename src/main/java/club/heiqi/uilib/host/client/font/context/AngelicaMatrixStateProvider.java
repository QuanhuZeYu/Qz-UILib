package club.heiqi.uilib.host.client.font.context;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.gl.GlTokens;
import club.heiqi.uilib.host.client.font.context.FontRenderContext.MatrixState;

/** 可选Angelica 2.2.10有效状态读取，仅缓存反射入口、不持mutable JOML实例。
 * 固定来源：GLSM glGetFloat(int,FloatBuffer)、getEffectiveAlphaState、texture-unit1 matrix；
 * raster/viewport/depth/scissor统一经GLSM有效query；无缓存特例的枚举由GLSM回退driver。
 * 存在但不匹配时显式失败，不假装driver等价。
 * provider不拥有GL/字体对象；构造只解析入口，capture在宿主GL线程执行。 */
public final class AngelicaMatrixStateProvider
        implements LwjglFontContextCapture.MatrixStateProvider {
    private final GlApi gl;

    @Override
    public GlApi gl() {
        return gl;
    }

    private final Method getFloat,
            getAlpha,
            alphaEnabled,
            depthWrite,
            getNormal,
            getTextures,
            unitMatrix,
            unitBinding,
            bindingValue,
            matrixGet,
            packedLight,
            blockEntity,
            lightingFactor,
            unitState,
            unitEnabled;
    private final Constructor<?> alphaConstructor, vectorConstructor;
    private final Method alphaFunction, alphaReference;
    private final Method getInteger,
            getIntegers,
            getBooleans,
            isEnabled,
            effectiveBlendEnabled,
            effectiveBlend;
    private final Constructor<?> blendConstructor;
    private final Method[] blendGetters = new Method[10], backStencilGetters = new Method[7];
    private final Method caching, getStencil, getPolygon, offsetFactor, offsetUnits;
    private static final int[] BACK_STENCIL_NAMES = {
        GlTokens.GL_STENCIL_BACK_FUNC,
        GlTokens.GL_STENCIL_BACK_REF,
        GlTokens.GL_STENCIL_BACK_VALUE_MASK,
        GlTokens.GL_STENCIL_BACK_WRITEMASK,
        GlTokens.GL_STENCIL_BACK_FAIL,
        GlTokens.GL_STENCIL_BACK_PASS_DEPTH_FAIL,
        GlTokens.GL_STENCIL_BACK_PASS_DEPTH_PASS
    };
    private final Field brightnessX, brightnessY, vectorX, vectorY, vectorZ, instance;

    public static LwjglFontContextCapture.MatrixStateProvider optional(
            ClassLoader loader, GlApi gl) {
        final Class<?> glsm;
        try {
            glsm = Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager", false, loader);
        } catch (ClassNotFoundException absent) {
            return LwjglFontContextCapture.compatibility(gl);
        } catch (LinkageError mismatch) {
            throw new IllegalStateException("Angelica有效状态类无法加载，不能回退legacy矩阵", mismatch);
        }
        return new AngelicaMatrixStateProvider(glsm, loader, gl);
    }

    private AngelicaMatrixStateProvider(Class<?> glsm, ClassLoader loader, GlApi gl) {
        this.gl = java.util.Objects.requireNonNull(gl, "gl");
        try {
            getFloat = glsm.getMethod("glGetFloat", int.class, FloatBuffer.class);
            getInteger = glsm.getMethod("glGetInteger", int.class);
            getIntegers = glsm.getMethod("glGetInteger", int.class, java.nio.IntBuffer.class);
            getBooleans = glsm.getMethod("glGetBoolean", int.class, java.nio.ByteBuffer.class);
            isEnabled = glsm.getMethod("glIsEnabled", int.class);
            Class<?> blend =
                    Class.forName(
                            "com.gtnewhorizons.angelica.glsm.states.BlendState", false, loader);
            blendConstructor = blend.getConstructor();
            effectiveBlend = glsm.getMethod("getEffectiveBlendState", blend);
            effectiveBlendEnabled = glsm.getMethod("isEffectiveBlendEnabled");
            String[] getters = {
                "getSrcRgb",
                "getDstRgb",
                "getSrcAlpha",
                "getDstAlpha",
                "getEquationRgb",
                "getEquationAlpha",
                "getBlendColorR",
                "getBlendColorG",
                "getBlendColorB",
                "getBlendColorA"
            };
            for (int i = 0; i < getters.length; i++) blendGetters[i] = blend.getMethod(getters[i]);
            caching = glsm.getMethod("isCachingEnabled");
            getStencil = glsm.getMethod("getStencilState");
            getPolygon = glsm.getMethod("getPolygonState");
            String[] stencilGetters = {
                "getFuncBack",
                "getRefBack",
                "getValueMaskBack",
                "getWriteMaskBack",
                "getFailOpBack",
                "getZFailOpBack",
                "getZPassOpBack"
            };
            for (int i = 0; i < stencilGetters.length; i++)
                backStencilGetters[i] = getStencil.getReturnType().getMethod(stencilGetters[i]);
            offsetFactor = getPolygon.getReturnType().getMethod("getOffsetFactor");
            offsetUnits = getPolygon.getReturnType().getMethod("getOffsetUnits");
            Class<?> alpha =
                    Class.forName(
                            "com.gtnewhorizons.angelica.glsm.states.AlphaState", false, loader);
            alphaConstructor = alpha.getConstructor();
            getAlpha = glsm.getMethod("getEffectiveAlphaState", alpha);
            alphaFunction = alpha.getMethod("getFunction");
            alphaReference = alpha.getMethod("getReference");
            alphaEnabled = glsm.getMethod("isEffectiveAlphaTestEnabled");
            depthWrite = glsm.getMethod("isEffectiveDepthMaskEnabled");
            Class<?> shader =
                    Class.forName(
                            "com.gtnewhorizons.angelica.glsm.ffp.ShaderManager", false, loader);
            getNormal = shader.getMethod("getCurrentNormal");
            Class<?> vector = getNormal.getReturnType();
            vectorX = vector.getField("x");
            vectorY = vector.getField("y");
            vectorZ = vector.getField("z");
            vectorConstructor = vector.getConstructor();
            getTextures = glsm.getMethod("getTextures");
            Class<?> textures = getTextures.getReturnType();
            unitMatrix = textures.getMethod("getTextureUnitMatrix", int.class);
            matrixGet = unitMatrix.getReturnType().getMethod("get", FloatBuffer.class);
            unitBinding = textures.getMethod("getTextureUnitBindings", int.class);
            bindingValue = unitBinding.getReturnType().getMethod("getBinding");
            unitState = textures.getMethod("getTextureUnitStates", int.class);
            unitEnabled = unitState.getReturnType().getMethod("isEnabled");
            Class<?> config =
                    Class.forName(
                            "com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig", false, loader);
            brightnessX = config.getField("lastBrightnessX");
            brightnessY = config.getField("lastBrightnessY");
            packedLight = config.getMethod("packedLastBrightness");
            Class<?> captured =
                    Class.forName(
                            "net.coderbot.iris.uniforms.CapturedRenderingState", false, loader);
            instance = captured.getField("INSTANCE");
            blockEntity = captured.getMethod("getCurrentRenderedBlockEntity");
            Class<?> lighting =
                    Class.forName(
                            "com.gtnewhorizons.angelica.glsm.ffp.FFPVertexLighting", false, loader);
            lightingFactor = lighting.getMethod("modulatesVertexColor", vector);
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new IllegalStateException("Angelica 2.2.10有效状态ABI不匹配", error);
        }
    }

    @Override
    public MatrixState capture() {
        try {
            float[] p = read(GlTokens.GL_PROJECTION_MATRIX, 16),
                    mv = read(GlTokens.GL_MODELVIEW_MATRIX, 16),
                    color = read(GlTokens.GL_CURRENT_COLOR, 4);
            Object alpha = alphaConstructor.newInstance();
            getAlpha.invoke(null, alpha);
            Object textures = getTextures.invoke(null);
            int texture =
                    ((Number) bindingValue.invoke(unitBinding.invoke(textures, 1))).intValue();
            // 专用宿主font判据：BatchingFontRenderer.isWorldSpaceText以m33()==0识别透视。
            // 它不是通用world分类；custom projection应使用显式MatrixStateProvider。
            boolean lit =
                    p[15] == 0
                            && texture != 0
                            && (Boolean) unitEnabled.invoke(unitState.invoke(textures, 1));
            float u = 0, v = 0;
            int packed = 0;
            if (lit) {
                float[] m = new float[16];
                FloatBuffer out = buffer(64).asFloatBuffer();
                matrixGet.invoke(unitMatrix.invoke(textures, 1), out);
                for (int i = 0; i < 16; i++) m[i] = out.get(i);
                float x = brightnessX.getFloat(null), y = brightnessY.getFloat(null);
                u = m[0] * x + m[4] * y + m[12];
                v = m[1] * x + m[5] * y + m[13];
                packed = ((Number) packedLight.invoke(null)).intValue();
            }
            Object n = getNormal.invoke(null);
            float[] normal = vector(n);
            float[] factor = {1, 1, 1};
            if (p[15] == 0) {
                Object dest = vectorConstructor.newInstance();
                if ((Boolean) lightingFactor.invoke(null, dest)) factor = vector(dest);
            }
            return new MatrixState(
                    p,
                    mv,
                    color,
                    (Boolean) alphaEnabled.invoke(null),
                    ((Number) alphaFunction.invoke(alpha)).intValue(),
                    ((Number) alphaReference.invoke(alpha)).floatValue(),
                    lit,
                    lit ? texture : 0,
                    u,
                    v,
                    packed,
                    normal,
                    factor,
                    ((Number) blockEntity.invoke(instance.get(null))).intValue());
        } catch (ReflectiveOperationException | LinkageError error) {
            throw new IllegalStateException("Angelica有效context捕捉失败；未使用driver假矩阵", error);
        }
    }

    @Override
    public boolean depthWrite() {
        try {
            return (Boolean) depthWrite.invoke(null);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Angelica effective depthWrite读取失败", error);
        }
    }

    @Override
    public int integer(int name) {
        try {
            if ((Boolean) caching.invoke(null))
                for (int i = 0; i < BACK_STENCIL_NAMES.length; i++)
                    if (name == BACK_STENCIL_NAMES[i])
                        return ((Number) backStencilGetters[i].invoke(getStencil.invoke(null)))
                                .intValue();
            return ((Number) getInteger.invoke(null, name)).intValue();
        } catch (ReflectiveOperationException error) {
            throw failure(error);
        }
    }

    @Override
    public int[] integers(int name, int count) {
        try {
            java.nio.IntBuffer out = buffer(count * 4).asIntBuffer();
            getIntegers.invoke(null, name, out);
            int[] a = new int[count];
            for (int i = 0; i < count; i++) a[i] = out.get(i);
            return a;
        } catch (ReflectiveOperationException error) {
            throw failure(error);
        }
    }

    @Override
    public boolean enabled(int name) {
        try {
            return (Boolean) isEnabled.invoke(null, name);
        } catch (ReflectiveOperationException error) {
            throw failure(error);
        }
    }

    @Override
    public boolean[] booleans(int name, int count) {
        try {
            java.nio.ByteBuffer out = buffer(count);
            getBooleans.invoke(null, name, out);
            boolean[] a = new boolean[count];
            for (int i = 0; i < count; i++) a[i] = out.get(i) != 0;
            return a;
        } catch (ReflectiveOperationException error) {
            throw failure(error);
        }
    }

    @Override
    public float[] floats(int name, int count) {
        try {
            if (count == 1
                    && (name == GlTokens.GL_POLYGON_OFFSET_FACTOR
                            || name == GlTokens.GL_POLYGON_OFFSET_UNITS)
                    && (Boolean) caching.invoke(null))
                return new float[] {
                    ((Number)
                                    (name == GlTokens.GL_POLYGON_OFFSET_FACTOR
                                                    ? offsetFactor
                                                    : offsetUnits)
                                            .invoke(getPolygon.invoke(null)))
                            .floatValue()
                };
            return read(name, count);
        } catch (ReflectiveOperationException error) {
            throw failure(error);
        }
    }

    @Override
    public FontRenderContext.RasterState rasterState() {
        // front stencil/colorMask/depthRange/viewport/cull走GLSM query缓存；back stencil/offset
        // glGet未覆盖，额外读取公开state getter（caching关闭则仍走GLSM query）。
        // scissor box的宿主setter直接写driver，query真实fallback。Iris锁blend须取readEffective。
        try {
            Object blend = effectiveBlend.invoke(null, blendConstructor.newInstance());
            FontRenderContext.RasterState raw = LwjglFontContextCapture.captureRaster(this);
            int[] values = new int[6];
            float[] color = new float[4];
            for (int i = 0; i < 6; i++)
                values[i] = ((Number) blendGetters[i].invoke(blend)).intValue();
            for (int i = 0; i < 4; i++)
                color[i] = ((Number) blendGetters[i + 6].invoke(blend)).floatValue();
            return new FontRenderContext.RasterState(
                    (Boolean) effectiveBlendEnabled.invoke(null),
                    values[0],
                    values[1],
                    values[2],
                    values[3],
                    values[4],
                    values[5],
                    color,
                    raw.stencilEnabled,
                    raw.frontStencil,
                    raw.backStencil,
                    raw.colorMask(),
                    raw.depthRange(),
                    raw.cullEnabled,
                    raw.cullFace,
                    raw.frontFace,
                    raw.polygonOffsetFill,
                    raw.polygonOffsetFactor,
                    raw.polygonOffsetUnits);
        } catch (ReflectiveOperationException error) {
            throw failure(error);
        }
    }

    private static ByteBuffer buffer(int size) {
        if (size < 0 || size > 1024) throw new IllegalArgumentException("有效状态buffer越界");
        return ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder());
    }

    private static IllegalStateException failure(Exception error) {
        return new IllegalStateException("Angelica有效光栅状态读取失败；未降级driver", error);
    }

    private float[] vector(Object v) throws IllegalAccessException {
        return new float[] {vectorX.getFloat(v), vectorY.getFloat(v), vectorZ.getFloat(v)};
    }

    private float[] read(int name, int length) throws ReflectiveOperationException {
        FloatBuffer buffer = buffer(length * 4).asFloatBuffer();
        getFloat.invoke(null, name, buffer);
        float[] result = new float[length];
        for (int i = 0; i < length; i++) result[i] = buffer.get(i);
        return result;
    }
}
