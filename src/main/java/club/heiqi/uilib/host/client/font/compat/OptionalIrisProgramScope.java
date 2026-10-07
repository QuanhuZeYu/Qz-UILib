package club.heiqi.uilib.host.client.font.compat;

import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;

/** Iris pack program与Pass.use真实scope；不生成任何quad、不调用Tessellator。
 * producer必须使用自己的VAO/VBO，并依据requiredAttributes给完整vertex ABI。
 * shaderpack标准sampler2D要求STRAIGHT_RGBA采样view，不能重解释共享premult页。
 * 若无world pipeline返回null；存在且ABI错误明确失败。scope只在有效宿主GL线程短期使用。 */
public final class OptionalIrisProgramScope implements AutoCloseable {
    public enum SamplingView { STRAIGHT_RGBA }
    public static final class Attribute {
        public final String name;public final int location,type,size;
        Attribute(String name,int location,int type,int size){this.name=name;this.location=location;this.type=type;this.size=size;}
    }
    public final int programId;
    public final SamplingView samplingView=SamplingView.STRAIGHT_RGBA;
    public final int packedLight;
    private final float[] normal;private final boolean lightmapActive;
    public final List<Attribute> requiredAttributes;
    private final Object pipeline,oldInputs,oldPhase,oldTranslucency;
    private final Method setInputs,setPhase,setTranslucency,rebind,preDraw;
    private boolean closed;
    private final ProducerStateScope producer;
    public final int entityId,itemId,blockEntityId;
    private final SourceExtendedAttributes sourceExtended;
    private final Method refreshUniforms;private final Class<?> pipelineType;
    /** prepare选择Atlas view前只读查询，不改inputs/pass/program/FBO。null表示没有Iris world pass。 */
    public static SamplingView preview(ClassLoader loader,FontRenderContext context) {
        try {
            Class<?> iris=Class.forName("net.coderbot.iris.Iris",false,loader);
            Object manager=iris.getMethod("getPipelineManagerNullable").invoke(null);if(manager==null)return null;
            Object pipeline=manager.getClass().getMethod("getPipelineNullable").invoke(manager);if(pipeline==null)return null;
            Class<?> type=Class.forName("net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline",false,loader);
            HostProgramSnapshot captured=HostProgramSnapshot.current();
            if(captured!=null)return captured.active?SamplingView.STRAIGHT_RGBA:null;
            return type.isInstance(pipeline)&&(Boolean)type.getMethod("shouldOverrideShaders").invoke(pipeline)?SamplingView.STRAIGHT_RGBA:null;
        }catch(ClassNotFoundException absent){if("net.coderbot.iris.Iris".equals(absent.getMessage()))return null;throw new IllegalStateException("Iris存在但必需ABI类缺失",absent);}
        catch(ReflectiveOperationException|LinkageError error){throw new IllegalStateException("Iris采样view查询失败，未静默使用premult atlas",error);}
    }
    public static OptionalIrisProgramScope open(ClassLoader loader,FontRenderContext context) {
        HostProgramSnapshot source=HostProgramSnapshot.current();
        if(source!=null&&!source.active)return null;
        final Class<?> iris;
        try{iris=Class.forName("net.coderbot.iris.Iris",false,loader);}catch(ClassNotFoundException absent){if("net.coderbot.iris.Iris".equals(absent.getMessage()))return null;throw new IllegalStateException("Iris存在但必需ABI类缺失",absent);}
        try{
            Object manager=iris.getMethod("getPipelineManagerNullable").invoke(null);if(manager==null)return null;
            Object pipeline=manager.getClass().getMethod("getPipelineNullable").invoke(manager);if(pipeline==null)return null;
            Class<?> deferred=Class.forName("net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline",false,loader);
            if(!deferred.isInstance(pipeline))return null;
            if(!(Boolean)deferred.getMethod("shouldOverrideShaders").invoke(pipeline)){
                HostProgramSnapshot captured=HostProgramSnapshot.current();
                if(captured!=null&&captured.active)throw new IllegalStateException("延期Iris文字回放已离开有效world pass，禁止own shader回退");
                return null;
            }
            return new OptionalIrisProgramScope(pipeline,deferred,loader,context);
        }catch(ReflectiveOperationException|LinkageError error){throw new IllegalStateException("Iris world program接口失配，禁止own shader伪兼容",error);}
    }
    private OptionalIrisProgramScope(Object pipeline,Class<?> type,ClassLoader loader,FontRenderContext context)throws ReflectiveOperationException {
        this.pipeline=pipeline;pipelineType=type;
        Class<?> inputsType=Class.forName("net.coderbot.iris.gbuffer_overrides.matching.InputAvailability",false,loader);
        Constructor<?> inputsConstructor=inputsType.getConstructor(boolean.class,boolean.class);
        setInputs=type.getMethod("setInputs",inputsType);
        Method getPhase=type.getMethod("getPhase");setPhase=type.getMethod("setPhase",getPhase.getReturnType());
        setTranslucency=type.getMethod("setDeclaredTranslucency",Boolean.class);rebind=type.getMethod("rebindCurrentPass");
        Field inputs=type.getDeclaredField("inputs");inputs.setAccessible(true);oldInputs=inputs.get(pipeline);
        Field translucent=type.getDeclaredField("declaredTranslucent");translucent.setAccessible(true);oldTranslucency=translucent.get(pipeline);oldPhase=getPhase.invoke(pipeline);
        Class<?> glsm=Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager",false,loader);preDraw=glsm.getMethod("preDraw",int.class);
        packedLight=context.state.packedLight;normal=context.state.normal();lightmapActive=context.state.lightmapActive;
        HostProgramSnapshot source=HostProgramSnapshot.current();if(source==null)source=HostProgramSnapshot.capture(loader);
        sourceExtended=source==null?null:source.extended;
        entityId=source==null?0:source.entityId;itemId=source==null?0:source.itemId;blockEntityId=context.state.blockEntityId;
        refreshUniforms=Class.forName("com.gtnewhorizons.angelica.glsm.CompatUniformManager",false,loader).getMethod("refreshCompatUniforms",int.class);
        producer=new ProducerStateScope(loader,context,source);
        try{
            HostProgramSnapshot captured=HostProgramSnapshot.current();
            if(captured!=null&&captured.active)setPhase.invoke(pipeline,captured.phase);
            setTranslucency.invoke(pipeline,Boolean.TRUE);setInputs.invoke(pipeline,inputsConstructor.newInstance(true,context.state.lightmapActive));rebind.invoke(pipeline);
            programId=((Number)type.getMethod("getActivePassProgramId").invoke(pipeline)).intValue();
            if(programId<=0)throw new IllegalStateException("Iris当前font pass没有可提交program");
            if(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING)!=context.drawFramebuffer)throw new IllegalStateException("Iris font pass目标FBO与来源caller target不一致，不能覆盖pack pass强行回放");
            List<Attribute> attributes=new ArrayList<Attribute>();int count=GL20.glGetProgrami(programId,GL20.GL_ACTIVE_ATTRIBUTES);
            for(int i=0;i<count;i++){IntBuffer size=BufferUtils.createIntBuffer(1),attributeType=BufferUtils.createIntBuffer(1);String name=GL20.glGetActiveAttrib(programId,i,size,attributeType);attributes.add(new Attribute(name,GL20.glGetAttribLocation(programId,name),attributeType.get(0),size.get(0)));}
            requiredAttributes=Collections.unmodifiableList(attributes);
        }catch(ReflectiveOperationException|RuntimeException error){try{producer.close();}catch(RuntimeException cleanup){error.addSuppressed(cleanup);}try{restore();}catch(ReflectiveOperationException restoreFailure){error.addSuppressed(restoreFailure);}throw error;}
    }
    public float[] normal(){return normal.clone();}
    /** 来源时间冻结的FFP generic属性；延期回放不会读取当前mesh属性。 */
    public SourceExtendedAttributes freezeSourceExtendedAttributes(){if(sourceExtended==null)throw new IllegalStateException("缺少来源FFP extended属性");return sourceExtended;}
    public float[] entityAttribute(){return freezeSourceExtendedAttributes().mcEntity();}
    public float[] midBlockAttribute(){return freezeSourceExtendedAttributes().midBlock();}
    public void bindTexture2D(int texture){if(closed)throw new IllegalStateException("Iris scope已关闭");producer.bindTexture2D(texture);}
    /** 在任何raw producer绑定前调用；pointer注册及enable必须随后使用返回scope。 */
    public ProducerStateScope configureProducer(int vao,int vbo,int ebo,boolean uv,boolean color,boolean lightmap,boolean normal){
        if(closed)throw new IllegalStateException("Iris scope已关闭");producer.configure(vao,vbo,ebo,uv,color,lightmap,normal);return producer;
    }
    /** typedattrs来自实际active属性匹配，遗漏/未知必须由producer明确拒绝，不能补猜测默认值。 */
    public void bindProducerAttributes(int vao,int vbo,int ebo,java.util.Collection<ProducerStateScope.VertexAttribute> attributes){
        if(attributes==null)throw new NullPointerException("attributes");
        for(Attribute required:requiredAttributes){boolean found=false;for(ProducerStateScope.VertexAttribute supplied:attributes)if(supplied.location==required.location){found=true;break;}if(!found)throw new IllegalStateException("Iris producer缺少属性："+required.name);}
        configureProducer(vao,vbo,ebo,true,true,lightmapActive,true);
        for(ProducerStateScope.VertexAttribute attribute:attributes)producer.attribute(attribute);
    }
    /** producer完成VAO/effective flags同步后调用，刷新当前宿主兼容uniform/扩展属性。
     * 不代替producer设置midUV/entity/tangent，不能只凭此就宣称全pack ABI通过。 */
    public void beforeDraw(){if(closed)throw new IllegalStateException("Iris program scope已关闭");try{preDraw.invoke(null,GL11.GL_TRIANGLES);if(GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)!=programId)throw new IllegalStateException("Iris preDraw改变了已匹配的pack program，拒绝错误vertex ABI提交");}catch(ReflectiveOperationException error){throw new IllegalStateException("Iris draw prehook失败",error);}}
    private void restore()throws ReflectiveOperationException{setPhase.invoke(pipeline,oldPhase);setTranslucency.invoke(pipeline,oldTranslucency);setInputs.invoke(pipeline,oldInputs);rebind.invoke(pipeline);}
    @Override public void close(){if(!closed){closed=true;try{producer.close();}finally{try{restore();int restored=((Number)pipelineType.getMethod("getActivePassProgramId").invoke(pipeline)).intValue();if(restored>0)refreshUniforms.invoke(null,restored);}catch(ReflectiveOperationException error){throw new IllegalStateException("Iris pass scope恢复失败",error);}}}}
}
