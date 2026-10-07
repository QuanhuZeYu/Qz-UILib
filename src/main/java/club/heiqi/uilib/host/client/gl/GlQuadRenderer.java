package club.heiqi.uilib.host.client.gl;

import java.nio.ByteBuffer;
import java.util.Objects;
import org.lwjgl.system.MemoryUtil;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch.Draw;
import club.heiqi.uilib.ui.render.batch.OrderedQuadBatch.Quad;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import static org.lwjgl.opengl.GL32C.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL32C.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL32C.GL_ARRAY_BUFFER_BINDING;
import static org.lwjgl.opengl.GL32C.GL_BLEND;
import static org.lwjgl.opengl.GL32C.GL_BLEND_DST_ALPHA;
import static org.lwjgl.opengl.GL32C.GL_BLEND_DST_RGB;
import static org.lwjgl.opengl.GL32C.GL_BLEND_EQUATION_ALPHA;
import static org.lwjgl.opengl.GL32C.GL_BLEND_EQUATION_RGB;
import static org.lwjgl.opengl.GL32C.GL_BLEND_SRC_ALPHA;
import static org.lwjgl.opengl.GL32C.GL_BLEND_SRC_RGB;
import static org.lwjgl.opengl.GL32C.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL32C.GL_CULL_FACE;
import static org.lwjgl.opengl.GL32C.GL_CONTEXT_PROFILE_MASK;
import static org.lwjgl.opengl.GL32C.GL_CONTEXT_COMPATIBILITY_PROFILE_BIT;
import static org.lwjgl.opengl.GL32C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL32C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL32C.GL_ELEMENT_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL32C.GL_FALSE;
import static org.lwjgl.opengl.GL32C.GL_FLOAT;
import static org.lwjgl.opengl.GL32C.GL_FRAGMENT_SHADER;
import static org.lwjgl.opengl.GL32C.GL_FUNC_ADD;
import static org.lwjgl.opengl.GL32C.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL32C.GL_ONE;
import static org.lwjgl.opengl.GL32C.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL32C.GL_SCISSOR_BOX;
import static org.lwjgl.opengl.GL32C.GL_SCISSOR_TEST;
import static org.lwjgl.opengl.GL32C.GL_STATIC_DRAW;
import static org.lwjgl.opengl.GL32C.GL_STREAM_DRAW;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_2D_ARRAY;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_BINDING_2D;
import static org.lwjgl.opengl.GL32C.GL_TEXTURE_BINDING_2D_ARRAY;
import static org.lwjgl.opengl.GL32C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL32C.GL_UNSIGNED_INT;
import static org.lwjgl.opengl.GL32C.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL32C.GL_VERTEX_SHADER;
import static org.lwjgl.opengl.GL32C.glActiveTexture;
import static org.lwjgl.opengl.GL32C.glAttachShader;
import static org.lwjgl.opengl.GL32C.glBindAttribLocation;
import static org.lwjgl.opengl.GL32C.glBindBuffer;
import static org.lwjgl.opengl.GL32C.glBindFragDataLocation;
import static org.lwjgl.opengl.GL32C.glBindTexture;
import static org.lwjgl.opengl.GL32C.glBindVertexArray;
import static org.lwjgl.opengl.GL32C.glBlendEquationSeparate;
import static org.lwjgl.opengl.GL32C.glBlendFuncSeparate;
import static org.lwjgl.opengl.GL32C.glBufferData;
import static org.lwjgl.opengl.GL32C.glBufferSubData;
import static org.lwjgl.opengl.GL32C.glCompileShader;
import static org.lwjgl.opengl.GL32C.glCreateProgram;
import static org.lwjgl.opengl.GL32C.glCreateShader;
import static org.lwjgl.opengl.GL32C.glDeleteBuffers;
import static org.lwjgl.opengl.GL32C.glDeleteProgram;
import static org.lwjgl.opengl.GL32C.glDeleteShader;
import static org.lwjgl.opengl.GL32C.glDeleteVertexArrays;
import static org.lwjgl.opengl.GL32C.glDisable;
import static org.lwjgl.opengl.GL32C.glDrawElements;
import static org.lwjgl.opengl.GL32C.glEnable;
import static org.lwjgl.opengl.GL32C.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL32C.glGenBuffers;
import static org.lwjgl.opengl.GL32C.glGenVertexArrays;
import static org.lwjgl.opengl.GL32C.glGetInteger;
import static org.lwjgl.opengl.GL32C.glGetIntegerv;
import static org.lwjgl.opengl.GL32C.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL32C.glGetProgrami;
import static org.lwjgl.opengl.GL32C.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL32C.glGetShaderi;
import static org.lwjgl.opengl.GL32C.glGetUniformLocation;
import static org.lwjgl.opengl.GL32C.glIsEnabled;
import static org.lwjgl.opengl.GL32C.glLinkProgram;
import static org.lwjgl.opengl.GL32C.glScissor;
import static org.lwjgl.opengl.GL32C.glShaderSource;
import static org.lwjgl.opengl.GL32C.glUniform1i;
import static org.lwjgl.opengl.GL32C.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL32C.glUseProgram;
import static org.lwjgl.opengl.GL32C.glVertexAttribPointer;

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
            + " image; uniform int material; in vec2 texUv; flat in float texLayer; in vec4 tint; out vec4 result; "
            + "void main(){if(material==0){result=vec4(tint.rgb*tint.a,tint.a);}else{vec4 s=texture(image,"
            + (array ? "vec3(texUv,texLayer)" : "texUv")
            + ");if(material==1){float a=s.a*tint.a;result=vec4(tint.rgb*a,a);}else{result=s*tint.a;}}}";
    }
    public GlQuadRenderer(int capacity, ResourceProvider resources) {
        if (capacity <= 0 || capacity > Integer.MAX_VALUE / (4 * STRIDE))
            throw new IllegalArgumentException("无效 staging 容量");
        this.capacity=capacity; this.resources=Objects.requireNonNull(resources,"resources");
        int previousVao=glGetInteger(GL_VERTEX_ARRAY_BINDING), previousVbo=glGetInteger(GL_ARRAY_BUFFER_BINDING);
        try {
            staging=MemoryUtil.memAlloc(capacity*4*STRIDE);
            for(int i=0;i<2;i++) {
                programs[i]=program(fragment(i==1));
                matrices[i]=glGetUniformLocation(programs[i],"mvp");
                materials[i]=glGetUniformLocation(programs[i],"material");
                samplers[i]=glGetUniformLocation(programs[i],"image");
            }
            vao=glGenVertexArrays(); vbo=glGenBuffers(); ebo=glGenBuffers();
            glBindVertexArray(vao); glBindBuffer(GL_ARRAY_BUFFER,vbo);
            glBufferData(GL_ARRAY_BUFFER,(long)capacity*4*STRIDE,GL_STREAM_DRAW);
            glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,ebo);
            glBufferData(GL_ELEMENT_ARRAY_BUFFER,OrderedQuadBatch.quadIndices(capacity),GL_STATIC_DRAW); indexUploads++;
            int[] sizes={2,2,1,4}; int[] offsets={0,8,16,20};
            for(int i=0;i<4;i++){glEnableVertexAttribArray(i);glVertexAttribPointer(i,sizes[i],GL_FLOAT,false,STRIDE,offsets[i]);}
        } catch(RuntimeException | Error failure) { release(); closed=true; throw failure; }
        finally {glBindVertexArray(previousVao);glBindBuffer(GL_ARRAY_BUFFER,previousVbo);}
    }
    private static int shader(int type,String source) {
        int id=glCreateShader(type);
        try {glShaderSource(id,source);glCompileShader(id);
            if(glGetShaderi(id,GL_COMPILE_STATUS)==GL_FALSE)throw new IllegalStateException(glGetShaderInfoLog(id));
            return id;
        } catch(RuntimeException | Error e){glDeleteShader(id);throw e;}
    }
    private static int program(String fragment) {
        int vertex=0,pixel=0,id=0;
        try {
            vertex=shader(GL_VERTEX_SHADER,VERTEX); pixel=shader(GL_FRAGMENT_SHADER,fragment); id=glCreateProgram();
            glAttachShader(id,vertex);glAttachShader(id,pixel);
            String[] names={"position","uv","layer","color"};
            for(int i=0;i<names.length;i++)glBindAttribLocation(id,i,names[i]);
            glBindFragDataLocation(id,0,"result");glLinkProgram(id);
            if(glGetProgrami(id,GL_LINK_STATUS)==GL_FALSE)throw new IllegalStateException(glGetProgramInfoLog(id));
            return id;
        }catch(RuntimeException | Error e){if(id!=0)glDeleteProgram(id);throw e;}
        finally {if(vertex!=0)glDeleteShader(vertex);if(pixel!=0)glDeleteShader(pixel);}
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
            glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);
            glEnable(GL_BLEND);glBlendEquationSeparate(GL_FUNC_ADD,GL_FUNC_ADD);
            glBlendFuncSeparate(GL_ONE,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
            enable(GL_CULL_FACE,false);enable(GL_DEPTH_TEST,depthEnabled);
            if (scope.compatibility) enable(org.lwjgl.opengl.GL11.GL_ALPHA_TEST, false);
            return scope;
        }catch(RuntimeException | Error failure){scope.close();throw failure;}
    }
    public final class Scope implements AutoCloseable {
        private final float[] mvp;
        private final int height,program=glGetInteger(GL_CURRENT_PROGRAM),oldVao=glGetInteger(GL_VERTEX_ARRAY_BINDING),
            oldVbo=glGetInteger(GL_ARRAY_BUFFER_BINDING),unit=glGetInteger(GL_ACTIVE_TEXTURE);
        private final int eqRgb=glGetInteger(GL_BLEND_EQUATION_RGB),eqAlpha=glGetInteger(GL_BLEND_EQUATION_ALPHA),
            srcRgb=glGetInteger(GL_BLEND_SRC_RGB),dstRgb=glGetInteger(GL_BLEND_DST_RGB),
            srcAlpha=glGetInteger(GL_BLEND_SRC_ALPHA),dstAlpha=glGetInteger(GL_BLEND_DST_ALPHA);
        private final boolean blend=glIsEnabled(GL_BLEND),scissor=glIsEnabled(GL_SCISSOR_TEST),
            cull=glIsEnabled(GL_CULL_FACE),depth=glIsEnabled(GL_DEPTH_TEST);
        private final int[] box=new int[4];
        private final int texture2d,textureArray,sampler;
        private final boolean compatibility = (glGetInteger(GL_CONTEXT_PROFILE_MASK) & GL_CONTEXT_COMPATIBILITY_PROFILE_BIT) != 0;
        private final boolean alphaTest = compatibility && glIsEnabled(org.lwjgl.opengl.GL11.GL_ALPHA_TEST);
        private final boolean samplerObjects = org.lwjgl.opengl.GL.getCapabilities().OpenGL33
            || org.lwjgl.opengl.GL.getCapabilities().GL_ARB_sampler_objects;
        private boolean done;
        private int currentProgram=-1,bound2d,boundArray;
        private Scope(float[] mvp,int height) {
            this.mvp=mvp;this.height=height;glGetIntegerv(GL_SCISSOR_BOX,box);
            glActiveTexture(GL_TEXTURE0);texture2d=glGetInteger(GL_TEXTURE_BINDING_2D);
            textureArray=glGetInteger(GL_TEXTURE_BINDING_2D_ARRAY);bound2d=texture2d;boundArray=textureArray;
            // 外部sampler会覆盖图集过滤，范围内使用资源纹理参数，并还原unit0的sampler。
            sampler = samplerObjects ? glGetInteger(org.lwjgl.opengl.GL33C.GL_SAMPLER_BINDING) : 0;
            if (samplerObjects) org.lwjgl.opengl.GL33C.glBindSampler(0, 0);
        }
        /** 一段恰好一次顶点上传；逐 Draw 使用原 firstQuad/quadCount，不合并或排序。 */
        public void consumeBatch(OrderedQuadBatch batch) {
            if(done || active!=this)throw new IllegalStateException("范围已结束");
            Objects.requireNonNull(batch,"batch");
            if(batch.quads().size()>capacity)throw new IllegalArgumentException("提交段超过 backend 容量");
            if(batch.quads().isEmpty())return;
            staging.clear();
            for(Quad q:batch.quads()) {
                vertex(q,q.x,q.y,q.u0,q.v0);vertex(q,q.x+q.width,q.y,q.u1,q.v0);
                vertex(q,q.x+q.width,q.y+q.height,q.u1,q.v1);vertex(q,q.x,q.y+q.height,q.u0,q.v1);
            }
            staging.flip();glBufferSubData(GL_ARRAY_BUFFER,0,staging);uploads++;bytes+=staging.remaining();
            for(Draw draw:batch.draws()) {
                int material=draw.state.material;
                if(material<SOLID || material>COLOR_PREMULT)throw new IllegalArgumentException("未知材质: "+material);
                Texture t=material==SOLID ? null : Objects.requireNonNull(resources.resolve(draw.state.resource,draw.state.generation),"texture");
                int variant=t!=null && t.target==GL_TEXTURE_2D_ARRAY ? 1 : 0;
                if(currentProgram!=variant){glUseProgram(programs[variant]);glUniformMatrix4fv(matrices[variant],false,mvp);
                    glUniform1i(samplers[variant],0);currentProgram=variant;}
                glUniform1i(materials[variant],material);
                if(t!=null && (variant==0 ? bound2d : boundArray)!=t.id) {
                    glBindTexture(t.target,t.id);if(variant==0)bound2d=t.id;else boundArray=t.id;bindings++;
                }
                LayoutBox clip=draw.state.clip;
                enable(GL_SCISSOR_TEST,clip!=null);
                if(clip!=null)glScissor(clip.x,Math.toIntExact((long)height-clip.y-clip.height),clip.width,clip.height);
                glDrawElements(GL_TRIANGLES,draw.quadCount*6,GL_UNSIGNED_INT,(long)draw.firstQuad*6*4);draws++;
            }
        }
        @Override public void close() {
            if(done)return;done=true;
            glUseProgram(program);glBindVertexArray(oldVao);glBindBuffer(GL_ARRAY_BUFFER,oldVbo);
            glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture2d);glBindTexture(GL_TEXTURE_2D_ARRAY,textureArray);glActiveTexture(unit);
            glBlendEquationSeparate(eqRgb,eqAlpha);glBlendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);enable(GL_BLEND,blend);
            glScissor(box[0],box[1],box[2],box[3]);enable(GL_SCISSOR_TEST,scissor);enable(GL_CULL_FACE,cull);enable(GL_DEPTH_TEST,depth);
            if (compatibility) enable(org.lwjgl.opengl.GL11.GL_ALPHA_TEST, alphaTest);
            if (samplerObjects) org.lwjgl.opengl.GL33C.glBindSampler(0, sampler);
            active=null;
        }
    }
    private void vertex(Quad q,float x,float y,float u,float v) {
        staging.putFloat(x).putFloat(y).putFloat(u).putFloat(v).putFloat(q.layer);
        staging.putFloat((q.color>>>16 & 255)/255f).putFloat((q.color>>>8 & 255)/255f)
            .putFloat((q.color & 255)/255f).putFloat((q.color>>>24)/255f);
    }
    private static void enable(int capability,boolean value){if(value)glEnable(capability);else glDisable(capability);}
    private void release() {
        if(ebo!=0)glDeleteBuffers(ebo);if(vbo!=0)glDeleteBuffers(vbo);if(vao!=0)glDeleteVertexArrays(vao);
        for(int p:programs)if(p!=0)glDeleteProgram(p);
        if(staging!=null){MemoryUtil.memFree(staging);staging=null;}
    }
    @Override public void close(){if(closed)return;if(active!=null)throw new IllegalStateException("先结束提交范围");release();closed=true;}
}
