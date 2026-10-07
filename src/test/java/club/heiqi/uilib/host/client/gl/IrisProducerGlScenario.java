package club.heiqi.uilib.host.client.gl;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.system.MemoryUtil;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch;
import club.heiqi.uilib.ui.render.batch.OrderedQuadCollector;
import static org.lwjgl.opengl.GL32C.*;

/** 仅合成activeprogram验证production emitter字节/像素合同，不宣称实际Iris engine初始化通过。 */
public final class IrisProducerGlScenario {
    private IrisProducerGlScenario(){}
    public static void run(final FontRenderContext context,final GlGlyphAtlas atlas,int fbo,int width,int height)throws Exception{
        int oldVao=glGetInteger(GL_VERTEX_ARRAY_BINDING),oldVbo=glGetInteger(GL_ARRAY_BUFFER_BINDING),oldProgram=glGetInteger(GL_CURRENT_PROGRAM);
        // 这是world应用具名case复用的producer探针；不是独立空应用。
        final int[] resources=new int[3];
        try(AutoCloseable cleanup=()->{
            glUseProgram(oldProgram);glBindVertexArray(oldVao);glBindBuffer(GL_ARRAY_BUFFER,oldVbo);
            glDeleteProgram(resources[2]);glDeleteShader(resources[0]);glDeleteShader(resources[1]);
        }) {
        String vertex="#version 150\nin vec4 position,color,uv0,uv1;in vec3 normal;in vec4 mc_Entity,at_midBlock;out vec4 c;out vec2 uv;out vec3 light;void main(){gl_Position=position;c=color;c.rgb*=vec3(-mc_Entity.x,-mc_Entity.y/2.0,mc_Entity.z*4.0)*(at_midBlock.x+at_midBlock.y+at_midBlock.z)*(8.0/7.0);c.a*=at_midBlock.w/0.75;uv=uv0.xy;light=vec3(uv1.xy/256.0,normal.z*0.25*mc_Entity.w);}";
        String fragment="#version 150\nuniform sampler2D image;in vec4 c;in vec2 uv;in vec3 light;out vec4 result;void main(){result=texture(image,uv)*c;result.rgb*=light;}";
        int vs=resources[0]=shader(GL_VERTEX_SHADER,vertex),fs=resources[1]=shader(GL_FRAGMENT_SHADER,fragment),program=resources[2]=glCreateProgram();
        glAttachShader(program,vs);glAttachShader(program,fs);String[] names={"position","color","uv0","uv1","normal"};for(int i=0;i<names.length;i++)glBindAttribLocation(program,i,names[i]);glBindFragDataLocation(program,0,"result");glLinkProgram(program);
        if(glGetProgrami(program,GL_LINK_STATUS)==0)throw new AssertionError(glGetProgramInfoLog(program));
        final List<GlProgramAttribute> attributes=new ArrayList<GlProgramAttribute>();
        for(int i=0;i<glGetProgrami(program,GL_ACTIVE_ATTRIBUTES);i++){IntBuffer size=BufferUtils.createIntBuffer(1),type=BufferUtils.createIntBuffer(1);String name=glGetActiveAttrib(program,i,size,type);attributes.add(new GlProgramAttribute(name,glGetAttribLocation(program,name),type.get(0),size.get(0)));}
        final int activeProgram=program;
        GlProgramScope delegate=new GlProgramScope(){
            public int programId(){return activeProgram;}public List<GlProgramAttribute> attributes(){return attributes;}public float[] normal(){return new float[]{0,0,1};}public int packedLight(){return 240|128<<16;}public float[] extended(String name,int type){return "mc_Entity".equals(name)?new float[]{-1,-2,0.25f,1}:new float[]{0.125f,0.25f,0.5f,0.75f};}
            public void configure(int vao,int vbo,int ebo,boolean uv,boolean color,boolean lightmap,boolean normal){glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,ebo);}
            public void pointer(int location,int size,int type,boolean normalized,int stride,long offset){glVertexAttribPointer(location,size,type,normalized,stride,offset);}
            public void disable(int location){glDisableVertexAttribArray(location);}
            public void close(){}
            public void beforeDraw(){glUseProgram(activeProgram);glUniform1i(glGetUniformLocation(activeProgram,"image"),0);}
            public void bindTexture2D(int texture){glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture);}
        };
        try(GlFontIrisProducer producer=new GlFontIrisProducer(new Lwjgl3GlApi(),16);GlGlyphAtlas.Frame frame=atlas.beginFrame(java.util.Collections.emptyList(),true,GlGlyphAtlas.SamplingView.STRAIGHT_RGBA,true)){
            long before=atlas.stats().uploadedBytes;frame.prepare(null,new GlGlyphAtlas.UploadBudget(36,1000000000,1),false);frame.freeze();GlGlyphAtlas.Resident white=frame.solid();if(white==null)throw new AssertionError("pureSOLID whiteview未ready");if(atlas.resolve(white.resource,white.generation).target!=GL_TEXTURE_2D)throw new AssertionError("straightview不能是array sampler");
            if(atlas.stats().uploadedBytes-before>36)throw new AssertionError("white超上传budget");
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,fbo);glViewport(0,0,width,height);glDisable(GL_DEPTH_TEST);glDisable(GL_STENCIL_TEST);glDisable(GL_SCISSOR_TEST);glDisable(GL_CULL_FACE);glClearColor(0,0,0,0);glClear(GL_COLOR_BUFFER_BIT);
            glEnable(GL_BLEND);glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);glBlendEquation(GL_FUNC_ADD);
            producer.configure(delegate);
            OrderedQuadCollector collector=new OrderedQuadCollector(16,batch->producer.consume(batch,delegate,context,atlas));
            collector.add(new OrderedQuadBatch.State(GlQuadRenderer.MONOCHROME_COVERAGE,white.resource,white.generation,null),new OrderedQuadBatch.Quad(-0.5f,-0.5f,1,1,white.u0,white.v0,white.u1,white.v1,0,0x80ff8040));collector.flush();
            glBindFramebuffer(GL_READ_FRAMEBUFFER,fbo);ByteBuffer pixel=MemoryUtil.memAlloc(4);try{glReadPixels(width/2,height/2,1,1,GL_RGBA,GL_UNSIGNED_BYTE,pixel);
                double alpha=128.0/255*context.state.color()[3];double[] expected={255*(240.0/256)*alpha,128*(128.0/256)*alpha,64*0.25*alpha,255*alpha};
                for(int c=0;c<4;c++)if(Math.abs((pixel.get(c)&255)-expected[c])>1.1)throw new AssertionError("Iris synthetic producer ABI像素不符channel="+c+" actual="+(pixel.get(c)&255)+" expected="+expected[c]);
            }finally{MemoryUtil.memFree(pixel);}
            if(glGetError()!=GL_NO_ERROR)throw new AssertionError("Iris producer GL error");
            System.out.println("IRIS_SYNTHETIC_PRODUCER_ABI_PASS productionEmitter=true rawPackedUv1=true straightWhiteSolid=true historicalAlpha=true capturedMcEntityMidBlock=true realIrisEngine=false");
        }
        }
    }
    private static int shader(int type,String source){int id=glCreateShader(type);try{glShaderSource(id,source);glCompileShader(id);if(glGetShaderi(id,GL_COMPILE_STATUS)==0)throw new AssertionError(glGetShaderInfoLog(id));return id;}catch(RuntimeException|Error failure){try{glDeleteShader(id);}catch(Throwable cleanup){failure.addSuppressed(cleanup);}throw failure;}}
}
