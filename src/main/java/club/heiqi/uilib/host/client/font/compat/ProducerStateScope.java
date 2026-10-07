package club.heiqi.uilib.host.client.font.compat;

import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.host.client.gl.GlTokens;

import java.lang.reflect.Method;
import java.nio.FloatBuffer;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;

/** 自有producer与Angelica有效matrix/VAO缓存的短scope。不拥有VAO/VBO，不生成几何。
 * 必须在producer第一次raw bind之前建立；任何pointer通过本接口，generic enable与FFP flags分别同步。
 * 保存effective和实际driver bindings各自入口值；外层backend guard仍负责完整driver raster/pass状态。 */
public final class ProducerStateScope implements AutoCloseable {
    private final GlApi gl;
    private final Method getFloat,getInteger,matrixMode,loadMatrix,bindVao,bindBuffer,pointer,integerPointer,enable,disable,
            setFlags,activeTexture,bindTexture,color,normalSetter,blockSetter,entitySetter;
    private final Object captured;
    private final int effectiveTexture0,actualTexture0,effectiveTexture1,actualTexture1,actualActiveTexture;
    private final int effectiveVao,effectiveVbo,effectiveEbo,actualVao,actualVbo,actualEbo,oldMode,oldActiveTexture,oldFlags,oldBlock,oldEntity,oldItem;
    private final float[] oldProjection,oldModelView,oldLightmapMatrix,oldColor,oldNormal;
    private final int positionBit,colorBit,textureBit,normalBit,brightnessBit;
    private boolean closed;
    private int producerVao;
    ProducerStateScope(ClassLoader loader,GlApi gl,FontRenderContext context,HostProgramSnapshot source)throws ReflectiveOperationException {
        this.gl=java.util.Objects.requireNonNull(gl,"gl");
        Class<?> glsm=Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager",false,loader);
        getFloat=glsm.getMethod("glGetFloat",int.class,FloatBuffer.class);getInteger=glsm.getMethod("glGetInteger",int.class);
        matrixMode=glsm.getMethod("glMatrixMode",int.class);loadMatrix=glsm.getMethod("glLoadMatrix",FloatBuffer.class);
        bindVao=glsm.getMethod("glBindVertexArray",int.class);bindBuffer=glsm.getMethod("glBindBuffer",int.class,int.class);
        pointer=glsm.getMethod("glVertexAttribPointer",int.class,int.class,int.class,boolean.class,int.class,long.class);
        integerPointer=glsm.getMethod("glVertexAttribIPointer",int.class,int.class,int.class,int.class,long.class);
        enable=glsm.getMethod("glEnableVertexAttribArray",int.class);disable=glsm.getMethod("glDisableVertexAttribArray",int.class);
        bindTexture=glsm.getMethod("glBindTexture",int.class,int.class);
        activeTexture=glsm.getMethod("glActiveTexture",int.class);color=glsm.getMethod("glColor4f",float.class,float.class,float.class,float.class);
        Class<?> vaoManager=Class.forName("com.gtnewhorizons.angelica.glsm.ffp.VAOManager",false,loader);
        setFlags=vaoManager.getMethod("setCurrentVertexFlags",int.class);oldFlags=((Number)vaoManager.getMethod("getCurrentVertexFlags").invoke(null)).intValue();
        Class<?> flags=Class.forName("com.gtnewhorizon.gtnhlib.client.renderer.vertex.VertexFlags",false,loader);
        positionBit=flags.getField("POSITION_BIT").getInt(null);colorBit=flags.getField("COLOR_BIT").getInt(null);textureBit=flags.getField("TEXTURE_BIT").getInt(null);
        normalBit=flags.getField("NORMAL_BIT").getInt(null);brightnessBit=flags.getField("BRIGHTNESS_BIT").getInt(null);
        Class<?> shader=Class.forName("com.gtnewhorizons.angelica.glsm.ffp.ShaderManager",false,loader);
        normalSetter=glsm.getMethod("glNormal3f",float.class,float.class,float.class);
        Object normal=shader.getMethod("getCurrentNormal").invoke(null);Class<?> vector=normal.getClass();
        oldNormal=new float[]{vector.getField("x").getFloat(normal),vector.getField("y").getFloat(normal),vector.getField("z").getFloat(normal)};
        Class<?> capturedType=Class.forName("net.coderbot.iris.uniforms.CapturedRenderingState",false,loader);captured=capturedType.getField("INSTANCE").get(null);
        blockSetter=capturedType.getMethod("setCurrentBlockEntity",int.class);entitySetter=capturedType.getMethod("setCurrentEntityAndItem",int.class,int.class);
        oldBlock=((Number)capturedType.getMethod("getCurrentRenderedBlockEntity").invoke(captured)).intValue();
        oldEntity=((Number)capturedType.getMethod("getCurrentRenderedEntity").invoke(captured)).intValue();oldItem=((Number)capturedType.getMethod("getCurrentRenderedItem").invoke(captured)).intValue();
        effectiveVao=integer(GlTokens.GL_VERTEX_ARRAY_BINDING);effectiveVbo=integer(GlTokens.GL_ARRAY_BUFFER_BINDING);effectiveEbo=integer(GlTokens.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        actualVao=gl.glGetInteger(GlTokens.GL_VERTEX_ARRAY_BINDING);actualVbo=gl.glGetInteger(GlTokens.GL_ARRAY_BUFFER_BINDING);actualEbo=gl.glGetInteger(GlTokens.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        oldMode=integer(GlTokens.GL_MATRIX_MODE);oldActiveTexture=integer(GlTokens.GL_ACTIVE_TEXTURE);
        actualActiveTexture=gl.glGetInteger(GlTokens.GL_ACTIVE_TEXTURE);
        call(activeTexture,GlTokens.GL_TEXTURE0);try{effectiveTexture0=integer(GlTokens.GL_TEXTURE_BINDING_2D);gl.glActiveTexture(GlTokens.GL_TEXTURE0);actualTexture0=gl.glGetInteger(GlTokens.GL_TEXTURE_BINDING_2D);}finally{call(activeTexture,oldActiveTexture);gl.glActiveTexture(actualActiveTexture);}
        oldProjection=read(GlTokens.GL_PROJECTION_MATRIX,16);oldModelView=read(GlTokens.GL_MODELVIEW_MATRIX,16);oldColor=read(GlTokens.GL_CURRENT_COLOR,4);
        call(activeTexture,GlTokens.GL_TEXTURE1);try{oldLightmapMatrix=read(GlTokens.GL_TEXTURE_MATRIX,16);effectiveTexture1=integer(GlTokens.GL_TEXTURE_BINDING_2D);gl.glActiveTexture(GlTokens.GL_TEXTURE1);actualTexture1=gl.glGetInteger(GlTokens.GL_TEXTURE_BINDING_2D);}finally{call(activeTexture,oldActiveTexture);gl.glActiveTexture(actualActiveTexture);}
        try {
            matrix(GlTokens.GL_PROJECTION,context.state.projection());matrix(GlTokens.GL_MODELVIEW,context.state.modelView());
            float[] n=context.state.normal(),c=context.state.color();call(normalSetter,n[0],n[1],n[2]);call(color,c[0],c[1],c[2],c[3]);
            callOn(blockSetter,captured,context.state.blockEntityId);
            // 源lightmap矩阵和历史纹理必须成对，GLSM绑定保留通知，driver双写确保采样源。
            call(activeTexture,GlTokens.GL_TEXTURE1);gl.glActiveTexture(GlTokens.GL_TEXTURE1);call(bindTexture,GlTokens.GL_TEXTURE_2D,context.state.lightmapTexture);gl.glBindTexture(GlTokens.GL_TEXTURE_2D,context.state.lightmapTexture);
            call(activeTexture,oldActiveTexture);gl.glActiveTexture(actualActiveTexture);
            if(source!=null&&source.active){callOn(entitySetter,captured,source.entityId,source.itemId);
                call(activeTexture,GlTokens.GL_TEXTURE1);matrix(GlTokens.GL_TEXTURE,source.lightmapMatrix);call(activeTexture,oldActiveTexture);}
            call(matrixMode,oldMode);
        }catch(RuntimeException error){try{close();}catch(RuntimeException cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    private int integer(int name)throws ReflectiveOperationException{return ((Number)getInteger.invoke(null,name)).intValue();}
    private float[] read(int name,int length)throws ReflectiveOperationException{FloatBuffer b=CompatBuffers.floats(length);getFloat.invoke(null,name,b);float[] result=new float[length];for(int i=0;i<length;i++)result[i]=b.get(i);return result;}
    private static void call(Method method,Object... args){callOn(method,null,args);}
    private static void callOn(Method method,Object target,Object... args){try{method.invoke(target,args);}catch(ReflectiveOperationException error){throw new IllegalStateException("Angelica producer状态操作失败："+method,error);}}
    private void matrix(int mode,float[] value){FloatBuffer b=CompatBuffers.floats(16);b.put(value).flip();call(matrixMode,mode);call(loadMatrix,b);}
    private void check(){if(closed)throw new IllegalStateException("producer状态scope已关闭");}
    public void configure(int vao,int vbo,int ebo,boolean uv,boolean color,boolean lightmap,boolean normal){check();if(vao==0||vbo==0)throw new IllegalArgumentException("producer须自有非零VAO/VBO");
        if(vao==effectiveVao)throw new IllegalArgumentException("producer不得改写回放入口宿主VAO");
        producerVao=vao;call(bindVao,vao);gl.glBindVertexArray(vao);call(bindBuffer,GlTokens.GL_ARRAY_BUFFER,vbo);gl.glBindBuffer(GlTokens.GL_ARRAY_BUFFER,vbo);
        call(bindBuffer,GlTokens.GL_ELEMENT_ARRAY_BUFFER,ebo);gl.glBindBuffer(GlTokens.GL_ELEMENT_ARRAY_BUFFER,ebo);
        call(setFlags,positionBit|(uv?textureBit:0)|(color?colorBit:0)|(lightmap?brightnessBit:0)|(normal?normalBit:0));
    }
    public void pointer(int location,int size,int type,boolean normalized,int stride,long offset){check();if(producerVao==0)throw new IllegalStateException("先configure producer");call(pointer,location,size,type,normalized,stride,offset);call(enable,location);}
    public void integerPointer(int location,int size,int type,int stride,long offset){check();if(producerVao==0)throw new IllegalStateException("先configure producer");call(integerPointer,location,size,type,stride,offset);call(enable,location);}
    /** 在raw atlas绑定之前捕获的入口texture0；GLSM通知Iris材质/PBR状态，driver双写确认页面。 */
    public void bindTexture2D(int texture){check();if(texture<=0)throw new IllegalArgumentException("无效atlas texture");call(activeTexture,GlTokens.GL_TEXTURE0);gl.glActiveTexture(GlTokens.GL_TEXTURE0);call(bindTexture,GlTokens.GL_TEXTURE_2D,texture);gl.glBindTexture(GlTokens.GL_TEXTURE_2D,texture);}
    public void disable(int location){check();call(disable,location);}
    public static final class VertexAttribute {
        public final int location,size,type,stride;public final long offset;public final boolean normalized,integer;
        public VertexAttribute(int location,int size,int type,boolean normalized,int stride,long offset,boolean integer){
            if(location<0||size<1||size>4||stride<0||offset<0)throw new IllegalArgumentException("无效producer属性");
            this.location=location;this.size=size;this.type=type;this.normalized=normalized;this.stride=stride;this.offset=offset;this.integer=integer;
        }
    }
    public void attribute(VertexAttribute a){if(a.integer)integerPointer(a.location,a.size,a.type,a.stride,a.offset);else pointer(a.location,a.size,a.type,a.normalized,a.stride,a.offset);}
    @Override public void close(){if(closed)return;closed=true;
        matrix(GlTokens.GL_PROJECTION,oldProjection);matrix(GlTokens.GL_MODELVIEW,oldModelView);
        call(activeTexture,GlTokens.GL_TEXTURE1);gl.glActiveTexture(GlTokens.GL_TEXTURE1);matrix(GlTokens.GL_TEXTURE,oldLightmapMatrix);
        call(bindTexture,GlTokens.GL_TEXTURE_2D,effectiveTexture1);gl.glBindTexture(GlTokens.GL_TEXTURE_2D,actualTexture1);
        call(activeTexture,GlTokens.GL_TEXTURE0);gl.glActiveTexture(GlTokens.GL_TEXTURE0);call(bindTexture,GlTokens.GL_TEXTURE_2D,effectiveTexture0);gl.glBindTexture(GlTokens.GL_TEXTURE_2D,actualTexture0);
        call(activeTexture,oldActiveTexture);gl.glActiveTexture(actualActiveTexture);call(matrixMode,oldMode);
        call(normalSetter,oldNormal[0],oldNormal[1],oldNormal[2]);call(color,oldColor[0],oldColor[1],oldColor[2],oldColor[3]);
        callOn(entitySetter,captured,oldEntity,oldItem);callOn(blockSetter,captured,oldBlock);
        call(bindVao,effectiveVao);call(setFlags,oldFlags);call(bindBuffer,GlTokens.GL_ARRAY_BUFFER,effectiveVbo);call(bindBuffer,GlTokens.GL_ELEMENT_ARRAY_BUFFER,effectiveEbo);
        gl.glBindVertexArray(actualVao);gl.glBindBuffer(GlTokens.GL_ARRAY_BUFFER,actualVbo);gl.glBindBuffer(GlTokens.GL_ELEMENT_ARRAY_BUFFER,actualEbo);
    }
}
