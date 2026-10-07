package club.heiqi.uilib.host.client.font.compat;

/** 延期计划的shader来源；保pipeline身份/pack代际/phase，不持program或UV。
 * 回放不能跨pack reload；旧context不能强行选回已销毁的FBO/program。 */
final class HostProgramSnapshot {
    private static final ThreadLocal<HostProgramSnapshot> CURRENT=new ThreadLocal<HostProgramSnapshot>();
    final Object pipeline,phase;final int generation;final boolean active;final int entityId,itemId;final float[] lightmapMatrix;final SourceExtendedAttributes extended;
    private final ClassLoader loader;
    private HostProgramSnapshot(ClassLoader loader,Object pipeline,Object phase,int generation,boolean active,int entityId,int itemId,float[] lightmapMatrix,SourceExtendedAttributes extended){this.extended=extended;this.loader=loader;this.pipeline=pipeline;this.phase=phase;this.generation=generation;this.active=active;this.entityId=entityId;this.itemId=itemId;this.lightmapMatrix=lightmapMatrix.clone();}
    static HostProgramSnapshot capture(ClassLoader loader){
        try{
            Class<?> iris=Class.forName("net.coderbot.iris.Iris",false,loader);
            Object manager=iris.getMethod("getPipelineManagerNullable").invoke(null);
            Object pipeline=manager==null?null:manager.getClass().getMethod("getPipelineNullable").invoke(manager);
            int generation=((Number)iris.getMethod("getShaderPackLoadId").invoke(null)).intValue();
            Class<?> type=Class.forName("net.coderbot.iris.pipeline.DeferredWorldRenderingPipeline",false,loader);
            boolean active=type.isInstance(pipeline)&&(Boolean)type.getMethod("shouldOverrideShaders").invoke(pipeline);
            Object phase=active?type.getMethod("getPhase").invoke(pipeline):null;
            int entity=0,item=0;float[] lightmap=new float[16];lightmap[0]=lightmap[5]=lightmap[10]=lightmap[15]=1;
            if(active){
                Class<?> capturedType=Class.forName("net.coderbot.iris.uniforms.CapturedRenderingState",false,loader);Object captured=capturedType.getField("INSTANCE").get(null);
                entity=((Number)capturedType.getMethod("getCurrentRenderedEntity").invoke(captured)).intValue();item=((Number)capturedType.getMethod("getCurrentRenderedItem").invoke(captured)).intValue();
                Class<?> glsm=Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager",false,loader);Object textures=glsm.getMethod("getTextures").invoke(null);
                Object matrix=textures.getClass().getMethod("getTextureUnitMatrix",int.class).invoke(textures,1);
                java.nio.FloatBuffer buffer=org.lwjgl.BufferUtils.createFloatBuffer(16);matrix.getClass().getMethod("get",java.nio.FloatBuffer.class).invoke(matrix,buffer);
                for(int i=0;i<16;i++)lightmap[i]=buffer.get(i);
            }
            return new HostProgramSnapshot(loader,pipeline,phase,generation,active,entity,item,lightmap,active?SourceExtendedAttributes.capture():null);
        }catch(ClassNotFoundException absent){if("net.coderbot.iris.Iris".equals(absent.getMessage()))return null;throw new IllegalStateException("Iris存在但必需ABI类缺失",absent);}
        catch(ReflectiveOperationException|LinkageError error){throw new IllegalStateException("Iris来源snapshot失败",error);}
    }
    static HostProgramSnapshot current(){return CURRENT.get();}
    static void replay(HostProgramSnapshot captured,Runnable draw){HostProgramSnapshot previous=CURRENT.get();
        if(captured!=null){HostProgramSnapshot actual=capture(captured.loader);
            if(actual==null||actual.pipeline!=captured.pipeline||actual.generation!=captured.generation)throw new IllegalStateException("延期font来源Iris pipeline/pack已失效，请丢弃旧pass而非绘制到新program");
        }
        CURRENT.set(captured);try{draw.run();}finally{if(previous==null)CURRENT.remove();else CURRENT.set(previous);}
    }
}
