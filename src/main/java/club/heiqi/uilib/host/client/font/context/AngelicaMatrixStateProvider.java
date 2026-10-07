package club.heiqi.uilib.host.client.font.context;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import club.heiqi.uilib.host.client.font.context.FontRenderContext.MatrixState;

/** 可选Angelica 2.2.10有效状态读取，仅缓存反射入口、不持mutable JOML实例。
 * 固定来源：GLSM glGetFloat(int,FloatBuffer)、getEffectiveAlphaState、texture-unit1 matrix；
 * LWJGL实际FBO/viewport仍由LwjglFontContextCapture读取。存在但不匹配时显式失败，不假装driver等价。
 * provider不拥有GL/字体对象；构造只解析入口，capture在宿主GL线程执行。 */
public final class AngelicaMatrixStateProvider implements LwjglFontContextCapture.MatrixStateProvider {
    private final Method getFloat,getAlpha,alphaEnabled,depthWrite,getNormal,getTextures,unitMatrix,unitBinding,bindingValue,
            matrixGet,packedLight,blockEntity,lightingFactor,unitState,unitEnabled;
    private final Constructor<?> alphaConstructor,vectorConstructor;
    private final Method alphaFunction,alphaReference;
    private final Field brightnessX,brightnessY,vectorX,vectorY,vectorZ,instance;
    public static LwjglFontContextCapture.MatrixStateProvider optional(ClassLoader loader) {
        final Class<?> glsm;
        try { glsm=Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager",false,loader); }
        catch(ClassNotFoundException absent) { return LwjglFontContextCapture.compatibility(); }
        catch(LinkageError mismatch) {throw new IllegalStateException("Angelica有效状态类无法加载，不能回退legacy矩阵",mismatch);}
        return new AngelicaMatrixStateProvider(glsm,loader);
    }
    private AngelicaMatrixStateProvider(Class<?> glsm,ClassLoader loader) {
        try {
            getFloat=glsm.getMethod("glGetFloat",int.class,FloatBuffer.class);
            Class<?> alpha=Class.forName("com.gtnewhorizons.angelica.glsm.states.AlphaState",false,loader);
            alphaConstructor=alpha.getConstructor();getAlpha=glsm.getMethod("getEffectiveAlphaState",alpha);
            alphaFunction=alpha.getMethod("getFunction");alphaReference=alpha.getMethod("getReference");
            alphaEnabled=glsm.getMethod("isEffectiveAlphaTestEnabled");depthWrite=glsm.getMethod("isEffectiveDepthMaskEnabled");
            Class<?> shader=Class.forName("com.gtnewhorizons.angelica.glsm.ffp.ShaderManager",false,loader);getNormal=shader.getMethod("getCurrentNormal");
            Class<?> vector=getNormal.getReturnType();vectorX=vector.getField("x");vectorY=vector.getField("y");vectorZ=vector.getField("z");vectorConstructor=vector.getConstructor();
            getTextures=glsm.getMethod("getTextures");Class<?> textures=getTextures.getReturnType();
            unitMatrix=textures.getMethod("getTextureUnitMatrix",int.class);matrixGet=unitMatrix.getReturnType().getMethod("get",FloatBuffer.class);
            unitBinding=textures.getMethod("getTextureUnitBindings",int.class);bindingValue=unitBinding.getReturnType().getMethod("getBinding");
            unitState=textures.getMethod("getTextureUnitStates",int.class);unitEnabled=unitState.getReturnType().getMethod("isEnabled");
            Class<?> config=Class.forName("com.gtnewhorizons.angelica.glsm.hooks.GLSMConfig",false,loader);
            brightnessX=config.getField("lastBrightnessX");brightnessY=config.getField("lastBrightnessY");packedLight=config.getMethod("packedLastBrightness");
            Class<?> captured=Class.forName("net.coderbot.iris.uniforms.CapturedRenderingState",false,loader);instance=captured.getField("INSTANCE");blockEntity=captured.getMethod("getCurrentRenderedBlockEntity");
            Class<?> lighting=Class.forName("com.gtnewhorizons.angelica.glsm.ffp.FFPVertexLighting",false,loader);lightingFactor=lighting.getMethod("modulatesVertexColor",vector);
        }catch(ReflectiveOperationException|LinkageError error){throw new IllegalStateException("Angelica 2.2.10有效状态ABI不匹配",error);}
    }
    @Override public MatrixState capture() {
        try {
            float[] p=read(GL11.GL_PROJECTION_MATRIX,16),mv=read(GL11.GL_MODELVIEW_MATRIX,16),color=read(GL11.GL_CURRENT_COLOR,4);
            Object alpha=alphaConstructor.newInstance();getAlpha.invoke(null,alpha);
            Object textures=getTextures.invoke(null);int texture=((Number)bindingValue.invoke(unitBinding.invoke(textures,1))).intValue();
            // 与宿主font的world判据一致；orthographic UI不接收世界lightmap。
            boolean lit=p[15]==0 && texture!=0 && (Boolean)unitEnabled.invoke(unitState.invoke(textures,1));float u=0,v=0;int packed=0;
            if(lit){float[] m=new float[16];FloatBuffer out=BufferUtils.createFloatBuffer(16);matrixGet.invoke(unitMatrix.invoke(textures,1),out);for(int i=0;i<16;i++)m[i]=out.get(i);
                float x=brightnessX.getFloat(null),y=brightnessY.getFloat(null);u=m[0]*x+m[4]*y+m[12];v=m[1]*x+m[5]*y+m[13];packed=((Number)packedLight.invoke(null)).intValue();}
            Object n=getNormal.invoke(null);float[] normal=vector(n);float[] factor={1,1,1};
            if(p[15]==0){Object dest=vectorConstructor.newInstance();if((Boolean)lightingFactor.invoke(null,dest))factor=vector(dest);}
            return new MatrixState(p,mv,color,(Boolean)alphaEnabled.invoke(null),((Number)alphaFunction.invoke(alpha)).intValue(),
                    ((Number)alphaReference.invoke(alpha)).floatValue(),lit,lit?texture:0,u,v,packed,normal,factor,
                    ((Number)blockEntity.invoke(instance.get(null))).intValue());
        }catch(ReflectiveOperationException|LinkageError error){throw new IllegalStateException("Angelica有效context捕捉失败；未使用driver假矩阵",error);}
    }
    @Override public boolean depthWrite() {try{return (Boolean)depthWrite.invoke(null);}catch(ReflectiveOperationException error){throw new IllegalStateException("Angelica effective depthWrite读取失败",error);}}
    private float[] vector(Object v)throws IllegalAccessException{return new float[]{vectorX.getFloat(v),vectorY.getFloat(v),vectorZ.getFloat(v)};}
    private float[] read(int name,int length)throws ReflectiveOperationException {FloatBuffer buffer=BufferUtils.createFloatBuffer(length);getFloat.invoke(null,name,buffer);float[] result=new float[length];for(int i=0;i<length;i++)result[i]=buffer.get(i);return result;}
}
