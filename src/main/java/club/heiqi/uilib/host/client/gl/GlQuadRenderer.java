package club.heiqi.uilib.host.client.gl;

import static club.heiqi.uilib.host.client.gl.GlTokens.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Objects;
import club.heiqi.uilib.host.client.font.context.FontRenderContext;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch.Draw;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch.Quad;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;

/** GL3.2/GLSL150 的正式有序载荷消费者；所有操作及 close 必须在所属上下文线程执行。
 * 宿主在变换、深度、pass 或外部委托屏障处结束 Scope；不捕获 GL 矩阵，不管理 scene。
 * 资源提供者只解析已准备并存活的 resource/generation，不得在消费中修改 GL 状态。
 */
public final class GlQuadRenderer implements AutoCloseable {
    /** ARGB 顶点颜色为直通颜色；输出为 premult RGBA。 */
    public static final int SOLID = 0;
    /** 采样灰度 premult RGBA 的 alpha 为 coverage，RGB 使用顶点着色。 */
    public static final int MONOCHROME_COVERAGE = 1;
    /** 采样已经 premult 的彩色 RGBA，保留其 RGB，只乘顶点整体 alpha。 */
    public static final int COLOR_PREMULT = 2;
    public interface ResourceProvider { Texture resolve(long resource, long generation); }
    /** 纹理由调用者拥有；backend 不上传、换代或删除提供者的资源。 */
    public static final class Texture {
        public final int target, id;
        public Texture(int target, int id) {
            if ((target != GL_TEXTURE_2D && target != GL_TEXTURE_2D_ARRAY) || id <= 0)
                throw new IllegalArgumentException("无效纹理绑定");
            this.target = target; this.id = id;
        }
    }
    public static final class Counters {
        public final long draws, vertexUploads, vertexBytes, textureBindings, indexUploads;
        private Counters(GlQuadRenderer r) {
            draws=r.draws; vertexUploads=r.uploads; vertexBytes=r.bytes;
            textureBindings=r.bindings; indexUploads=r.indexUploads;
        }
    }
    private final GlApi gl;private final GlProgramProvider programProvider;
    private static final int STRIDE = 36;
    private final int capacity;
    private final ResourceProvider resources;
    private ByteBuffer staging;
    private int vao, vbo, ebo;
    private final int[] programs = new int[2], matrices = new int[2], materials = new int[2], samplers = new int[2];
    private Scope active;
    private boolean closed;
    private long draws, uploads, bytes, bindings, indexUploads;
    private static final String VERTEX = "#version 150\n"
        + "in vec2 position; in vec2 uv; in float layer; in vec4 color; uniform mat4 mvp; "
        + "out vec2 texUv; flat out float texLayer; out vec4 tint; "
        + "void main(){gl_Position=mvp*vec4(position,0,1);texUv=uv;texLayer=layer;tint=color;}";
    private static String fragment(boolean array) {
        return "#version 150\n" + "uniform " + (array ? "sampler2DArray" : "sampler2D")
            + " image; uniform int material; uniform vec3 hostLighting; uniform float hostAlpha; "
            + "uniform int alphaEnabled,alphaFunction,lightmapEnabled,outputStraight; uniform float alphaReference; uniform sampler2D lightmapImage; uniform vec2 lightmapUv; "
            + "in vec2 texUv; flat in float texLayer; in vec4 tint; out vec4 result; "
            + "bool alphaPass(float a){if(alphaFunction==512)return false;if(alphaFunction==513)return a<alphaReference;if(alphaFunction==514)return a==alphaReference;"
            + "if(alphaFunction==515)return a<=alphaReference;if(alphaFunction==516)return a>alphaReference;if(alphaFunction==517)return a!=alphaReference;if(alphaFunction==518)return a>=alphaReference;return true;} "
            + "void main(){if(material==0){result=vec4(tint.rgb*tint.a,tint.a);}else{vec4 s=texture(image,"
            + (array ? "vec3(texUv,texLayer)" : "texUv")
            + ");if(material==1){float a=s.a*tint.a;result=vec4(tint.rgb*a,a);}else{result=s*tint.a;}}"
            + "result*=hostAlpha;result.rgb*=hostLighting;if(lightmapEnabled!=0)result.rgb*=texture(lightmapImage,lightmapUv).rgb;"
            + "if(alphaEnabled!=0&&!alphaPass(result.a))discard;if(outputStraight!=0){if(material==0||material==1){result.rgb=tint.rgb*hostLighting;if(lightmapEnabled!=0)result.rgb*=texture(lightmapImage,lightmapUv).rgb;}else result.rgb=result.a>0.0?result.rgb/result.a:vec3(0.0);}}";
    }
    public GlQuadRenderer(GlApi gl,int capacity, ResourceProvider resources,GlProgramProvider provider) {
        this.gl=Objects.requireNonNull(gl,"gl");this.programProvider=Objects.requireNonNull(provider,"programs");if(!gl.capabilities().openGL32)throw new IllegalStateException("renderer要求实际OpenGL3.2/GLSL150");
        if (capacity <= 0 || capacity > Integer.MAX_VALUE / (4 * STRIDE))
            throw new IllegalArgumentException("无效 staging 容量");
        this.capacity=capacity; this.resources=Objects.requireNonNull(resources,"resources");
        int previousVao=gl.glGetInteger(GL_VERTEX_ARRAY_BINDING), previousVbo=gl.glGetInteger(GL_ARRAY_BUFFER_BINDING);
        try {
            staging=ByteBuffer.allocateDirect(capacity*4*STRIDE).order(ByteOrder.nativeOrder());
            for(int i=0;i<2;i++) {
                programs[i]=program(fragment(i==1));
                matrices[i]=gl.glGetUniformLocation(programs[i],"mvp");
                materials[i]=gl.glGetUniformLocation(programs[i],"material");
                samplers[i]=gl.glGetUniformLocation(programs[i],"image");
            }
            vao=gl.glGenVertexArrays(); vbo=gl.glGenBuffers(); ebo=gl.glGenBuffers();
            gl.glBindVertexArray(vao); gl.glBindBuffer(GL_ARRAY_BUFFER,vbo);
            gl.glBufferData(GL_ARRAY_BUFFER,(long)capacity*4*STRIDE,GL_STREAM_DRAW);
            gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,ebo);
            gl.glBufferData(GL_ELEMENT_ARRAY_BUFFER,OrderedQuadBatch.quadIndices(capacity),GL_STATIC_DRAW); indexUploads++;
            int[] sizes={2,2,1,4}; int[] offsets={0,8,16,20};
            for(int i=0;i<4;i++){gl.glEnableVertexAttribArray(i);gl.glVertexAttribPointer(i,sizes[i],GL_FLOAT,false,STRIDE,offsets[i]);}
        } catch(RuntimeException | Error failure) { release(); closed=true; throw failure; }
        finally {gl.glBindVertexArray(previousVao);gl.glBindBuffer(GL_ARRAY_BUFFER,previousVbo);}
    }
    private int shader(int type,String source) {
        int id=gl.glCreateShader(type);
        try {gl.glShaderSource(id,source);gl.glCompileShader(id);
            if(gl.glGetShaderi(id,GL_COMPILE_STATUS)==GL_FALSE)throw new IllegalStateException(gl.glGetShaderInfoLog(id));
            return id;
        } catch(RuntimeException | Error e){gl.glDeleteShader(id);throw e;}
    }
    private int program(String fragment) {
        int vertex=0,pixel=0,id=0;
        try {
            vertex=shader(GL_VERTEX_SHADER,VERTEX); pixel=shader(GL_FRAGMENT_SHADER,fragment); id=gl.glCreateProgram();
            gl.glAttachShader(id,vertex);gl.glAttachShader(id,pixel);
            String[] names={"position","uv","layer","color"};
            for(int i=0;i<names.length;i++)gl.glBindAttribLocation(id,i,names[i]);
            gl.glBindFragDataLocation(id,0,"result");gl.glLinkProgram(id);
            if(gl.glGetProgrami(id,GL_LINK_STATUS)==GL_FALSE)throw new IllegalStateException(gl.glGetProgramInfoLog(id));
            return id;
        }catch(RuntimeException | Error e){if(id!=0)gl.glDeleteProgram(id);throw e;}
        finally {if(vertex!=0)gl.glDeleteShader(vertex);if(pixel!=0)gl.glDeleteShader(pixel);}
    }
    public Counters counters(){return new Counters(this);}
    /** clip 是宿主转换完成的 framebuffer 像素，左上原点；MVP 为列主序快照。
     * framebuffer、viewport、depth function/write mask 与 pass 由宿主固定，本范围仅指定 depth enable。
     */
    public Scope begin(float[] mvp,int framebufferHeight,boolean depthEnabled) {
        if(closed || active!=null)throw new IllegalStateException("backend 已关闭或范围重入");
        if(mvp==null || mvp.length!=16 || framebufferHeight<0)throw new IllegalArgumentException("无效宿主范围");
        for(float f:mvp)if(!Float.isFinite(f))throw new IllegalArgumentException("无效 MVP");
        Scope scope=new Scope(mvp.clone(),framebufferHeight);
        active=scope;
        try {
            scope.selectTextureUnit();gl.glBindVertexArray(vao);gl.glBindBuffer(GL_ARRAY_BUFFER,vbo);gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,ebo);
            gl.glEnable(GL_BLEND);gl.glBlendEquationSeparate(GL_FUNC_ADD,GL_FUNC_ADD);
            gl.glBlendFuncSeparate(GL_ONE,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
            enable(GL_CULL_FACE,false);enable(GL_DEPTH_TEST,depthEnabled);
            if (scope.compatibility) enable(GL_ALPHA_TEST, false);
            return scope;
        }catch(RuntimeException | Error failure){scope.close();throw failure;}
    }
    /** ordinary world producer，caller有效MVP保持透视w/深度；Iris active pass不能走该ownshader。 */
    private GlFontIrisProducer irisProducer;private int fontEbo;
    // 原版字体strip TL/BL/TR/BR在caller XY是负绕序；UI通用quad保持其既有正绕序。
    static int[] fontIndices(int capacity){int[] indices=OrderedQuadBatch.quadIndices(capacity);for(int i=0;i<indices.length;i+=6){int swap=indices[i+1];indices[i+1]=indices[i+2];indices[i+2]=swap;swap=indices[i+4];indices[i+4]=indices[i+5];indices[i+5]=swap;}return indices;}
    private void bindFontIndices(){if(fontEbo==0){fontEbo=gl.glGenBuffers();gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,fontEbo);gl.glBufferData(GL_ELEMENT_ARRAY_BUFFER,fontIndices(capacity),GL_STATIC_DRAW);indexUploads++;}else gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,fontEbo);}
    public Scope begin(FontRenderContext context) {
        Objects.requireNonNull(context,"context");

        if(closed||active!=null)throw new IllegalStateException("backend已关闭或范围重入");
        GlFontWorldState world=new GlFontWorldState(gl,context);Scope scope=null;
        try{
            if(programProvider.preview(context)){
                scope=new Scope(context.projection.mvp(),context.sourceFramebufferHeight);active=scope;scope.world=world;
                scope.iris=programProvider.open(context);
                if(scope.iris==null)throw new IllegalStateException("Iris prepare/render activepass不一致");
                scope.selectTextureUnit();
                if(irisProducer==null)irisProducer=new GlFontIrisProducer(gl,capacity);irisProducer.configure(scope.iris);world.applyIris();
                // Pass.use拥有FBO/blend/alpha overrides；不能再用普通world.apply盖掉pack定义。
                return scope;
            }
            scope=begin(context.projection.mvp(),context.sourceFramebufferHeight,context.depthTest);scope.world=world;world.apply();bindFontIndices();return scope;
        }
        catch(RuntimeException|Error failure){try{if(scope!=null)scope.close();else world.close();}catch(RuntimeException|Error cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    public final class Scope implements AutoCloseable {
        private final float[] mvp;
        private GlFontWorldState world;
        private GlProgramScope iris;
        private final int height,program=gl.glGetInteger(GL_CURRENT_PROGRAM),oldVao=gl.glGetInteger(GL_VERTEX_ARRAY_BINDING),
            oldVbo=gl.glGetInteger(GL_ARRAY_BUFFER_BINDING),unit=gl.glGetInteger(GL_ACTIVE_TEXTURE);
        private final int eqRgb=gl.glGetInteger(GL_BLEND_EQUATION_RGB),eqAlpha=gl.glGetInteger(GL_BLEND_EQUATION_ALPHA),
            srcRgb=gl.glGetInteger(GL_BLEND_SRC_RGB),dstRgb=gl.glGetInteger(GL_BLEND_DST_RGB),
            srcAlpha=gl.glGetInteger(GL_BLEND_SRC_ALPHA),dstAlpha=gl.glGetInteger(GL_BLEND_DST_ALPHA);
        private final boolean blend=gl.glIsEnabled(GL_BLEND),scissor=gl.glIsEnabled(GL_SCISSOR_TEST),
            cull=gl.glIsEnabled(GL_CULL_FACE),depth=gl.glIsEnabled(GL_DEPTH_TEST);
        private final int[] box=new int[4];
        private final int texture2d,textureArray,sampler;
        private final boolean compatibility = (gl.glGetInteger(GL_CONTEXT_PROFILE_MASK) & GL_CONTEXT_COMPATIBILITY_PROFILE_BIT) != 0;
        private final boolean alphaTest = compatibility && gl.glIsEnabled(GL_ALPHA_TEST);
        private final boolean samplerObjects = gl.capabilities().samplerObjects;
        private boolean done;
        private int currentProgram=-1,bound2d,boundArray;
        private Scope(float[] mvp,int height) {
            this.mvp=mvp;this.height=height;gl.glGetIntegerv(GL_SCISSOR_BOX,box);
            gl.glActiveTexture(GL_TEXTURE0);texture2d=gl.glGetInteger(GL_TEXTURE_BINDING_2D);
            textureArray=gl.glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY);bound2d=texture2d;boundArray=textureArray;
            // 外部sampler会覆盖图集过滤，范围内使用资源纹理参数，并还原unit0的sampler。
            sampler = samplerObjects ? gl.glGetInteger(GL_SAMPLER_BINDING) : 0;
            gl.glActiveTexture(unit);
        }
        private void selectTextureUnit(){gl.glActiveTexture(GL_TEXTURE0);if(samplerObjects)gl.glBindSampler(0,0);}
        /** 一段恰好一次顶点上传；逐 Draw 使用原 firstQuad/quadCount，不合并或排序。 */
        public void consumeBatch(OrderedQuadBatch batch) {
            if(done || active!=this)throw new IllegalStateException("范围已结束");
            Objects.requireNonNull(batch,"batch");
            if(batch.quads().size()>capacity)throw new IllegalArgumentException("提交段超过 backend 容量");
            if(batch.quads().isEmpty())return;
            if(iris!=null){bytes+=irisProducer.consume(batch,iris,world.context,resources);uploads++;draws+=batch.draws().size();return;}
            staging.clear();
            for(Quad q:batch.quads()) {
                vertex(q,q.x,q.y,q.u0,q.v0);vertex(q,q.x+q.width,q.y,q.u1,q.v0);
                vertex(q,q.x+q.width,q.y+q.height,q.u1,q.v1);vertex(q,q.x,q.y+q.height,q.u0,q.v1);
            }
            staging.flip();gl.glBufferSubData(GL_ARRAY_BUFFER,0,staging);uploads++;bytes+=staging.remaining();
            for(Draw draw:batch.draws()) {
                int material=draw.state.material;
                if(material<SOLID || material>COLOR_PREMULT)throw new IllegalArgumentException("未知材质: "+material);
                Texture t=material==SOLID ? null : Objects.requireNonNull(resources.resolve(draw.state.resource,draw.state.generation),"texture");
                int variant=t!=null && t.target==GL_TEXTURE_2D_ARRAY ? 1 : 0;
                if(currentProgram!=variant){gl.glUseProgram(programs[variant]);gl.glUniformMatrix4fv(matrices[variant],false,mvp);
                    gl.glUniform1i(samplers[variant],0);if(world==null)GlFontWorldState.uiUniforms(gl,programs[variant]);else world.uniforms(programs[variant]);currentProgram=variant;}
                gl.glUniform1i(materials[variant],material);
                if(t!=null && (variant==0 ? bound2d : boundArray)!=t.id) {
                    gl.glBindTexture(t.target,t.id);if(variant==0)bound2d=t.id;else boundArray=t.id;bindings++;
                }
                LayoutBox clip=world==null?draw.state.clip:GlFontVisibility.viewport(world.context);
                if(world!=null&&world.context.scissorEnabled){int[] s=world.context.scissor();
                    LayoutBox hostClip=new LayoutBox(s[0],Math.toIntExact((long)height-s[1]-s[3]),s[2],s[3]);
                    if(clip==null)clip=hostClip;else{long l=Math.max((long)clip.x,hostClip.x),top=Math.max((long)clip.y,hostClip.y);
                        long r=Math.min((long)clip.x+clip.width,(long)hostClip.x+hostClip.width),bottom=Math.min((long)clip.y+clip.height,(long)hostClip.y+hostClip.height);
                        clip=new LayoutBox(Math.toIntExact(l),Math.toIntExact(top),Math.toIntExact(Math.max(0,r-l)),Math.toIntExact(Math.max(0,bottom-top)));}}

                enable(GL_SCISSOR_TEST,clip!=null);
                if(clip!=null)gl.glScissor(clip.x,Math.toIntExact((long)height-clip.y-clip.height),clip.width,clip.height);
                gl.glDrawElements(GL_TRIANGLES,draw.quadCount*6,GL_UNSIGNED_INT,(long)draw.firstQuad*6*4);draws++;
            }
        }
        @Override public void close() {
            if(done)return;done=true;
            Throwable failure=null;try{
            gl.glUseProgram(program);gl.glBindVertexArray(oldVao);gl.glBindBuffer(GL_ARRAY_BUFFER,oldVbo);
            gl.glActiveTexture(GL_TEXTURE0);gl.glBindTexture(GL_TEXTURE_2D,texture2d);gl.glBindTexture(GL_TEXTURE_2D_ARRAY,textureArray);gl.glActiveTexture(unit);
            gl.glBlendEquationSeparate(eqRgb,eqAlpha);gl.glBlendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);enable(GL_BLEND,blend);
            gl.glScissor(box[0],box[1],box[2],box[3]);enable(GL_SCISSOR_TEST,scissor);enable(GL_CULL_FACE,cull);enable(GL_DEPTH_TEST,depth);
            if (compatibility) enable(GL_ALPHA_TEST, alphaTest);
            if (samplerObjects) gl.glBindSampler(0, sampler);
            }catch(RuntimeException|Error error){failure=error;}
            try{if(world!=null)world.close();}catch(RuntimeException|Error error){if(failure==null)failure=error;else failure.addSuppressed(error);}
            // Irisclose最后重建effective缓存与旧pass；两个cleanup均执行并保留先前错误。
            try{if(iris!=null)iris.close();}catch(RuntimeException|Error error){if(failure==null)failure=error;else failure.addSuppressed(error);}finally{active=null;}
            if(failure instanceof RuntimeException)throw (RuntimeException)failure;if(failure instanceof Error)throw (Error)failure;
        }
    }
    private void vertex(Quad q,float x,float y,float u,float v) {
        staging.putFloat(x).putFloat(y).putFloat(u).putFloat(v).putFloat(q.layer);
        staging.putFloat((q.color>>>16 & 255)/255f).putFloat((q.color>>>8 & 255)/255f)
            .putFloat((q.color & 255)/255f).putFloat((q.color>>>24)/255f);
    }
    private void enable(int capability,boolean value){if(value)gl.glEnable(capability);else gl.glDisable(capability);}
    private void release() {
        if(irisProducer!=null){irisProducer.close();irisProducer=null;}if(fontEbo!=0){gl.glDeleteBuffers(fontEbo);fontEbo=0;}
        if(ebo!=0)gl.glDeleteBuffers(ebo);if(vbo!=0)gl.glDeleteBuffers(vbo);if(vao!=0)gl.glDeleteVertexArrays(vao);
        for(int p:programs)if(p!=0)gl.glDeleteProgram(p);
        if(staging!=null){staging=null;}
    }
    @Override public void close(){if(closed)return;if(active!=null)throw new IllegalStateException("先结束提交范围");release();closed=true;}
}
