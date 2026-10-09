package club.heiqi.uilib.host.client.image;

import static club.heiqi.uilib.host.client.gl.GlTokens.*;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import club.heiqi.uilib.host.client.gl.GlApi;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.image.ImagePlacement;

/** 原版 GUI item 的状态边界，独立于字体 producer。Angelica 的有效栈和 driver 栈分别保存、恢复。
 * all attrib 包含 lighting/material/alpha/depth/blend/color/normal；矩阵、现代 bindings 单独保存。
 * legacy客户端数组使用默认 VAO 并另存其client状态，异常漏出的 matrix push 按原深度 unwind；不承诺恢复任意 hook 对全局资源的修改。 */
public final class ItemStateScope implements AutoCloseable {
    private final GlApi gl;
    private final Port raw, effective;
    private final LegacySnapshot driver, cached;
    private final int program, vao, vbo, ebo, drawFbo, readFbo, activeUnit;
    private final int[][] textures;
    private final boolean samplers;
    private final int[] viewport = new int[4];
    private boolean closed;
    private static final int[] TARGETS = {GL_TEXTURE_2D, GL_TEXTURE_2D_ARRAY, 32879, 34067};
    private static final int[] BINDINGS = {GL_TEXTURE_BINDING_2D, GL_TEXTURE_BINDING_2D_ARRAY, 32874, 34068};
    public ItemStateScope(GlApi gl) {
        this.gl = java.util.Objects.requireNonNull(gl, "gl");
        ClassLoader loader = ItemStateScope.class.getClassLoader();
        raw = new Port(loader, false); effective = Port.effective(loader);
        program = gl.glGetInteger(GL_CURRENT_PROGRAM); vao = gl.glGetInteger(GL_VERTEX_ARRAY_BINDING);
        vbo = gl.glGetInteger(GL_ARRAY_BUFFER_BINDING); ebo = gl.glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING);
        drawFbo = gl.glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING); readFbo = gl.glGetInteger(36010);
        activeUnit = gl.glGetInteger(GL_ACTIVE_TEXTURE); gl.glGetIntegerv(GL_VIEWPORT, viewport);
        samplers = gl.capabilities().samplerObjects;
        textures = new int[gl.glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS)][5];
        try { for (int i = 0; i < textures.length; i++) { gl.glActiveTexture(GL_TEXTURE0 + i); for (int j = 0; j < BINDINGS.length; j++) textures[i][j] = gl.glGetInteger(BINDINGS[j]); if (samplers) textures[i][4] = gl.glGetInteger(GL_SAMPLER_BINDING); } }
        finally { gl.glActiveTexture(activeUnit); }
        // 先捕获 driver；effective 查询会改变 raw active unit，但不能覆盖这份入口快照。
        driver = new LegacySnapshot(raw, gl.glGetInteger(34018));
        cached = effective == null ? null : new LegacySnapshot(effective, gl.glGetInteger(34018));
        try {
            driver.push(); if (cached != null) cached.push();
            if(effective!=null)effective.one("glBindVertexArray",0);
            gl.glBindVertexArray(0);
            // compatibility客户端数组禁止非零VAO+CPU指针；先保存默认VAO自身，再清空buffer绑定。
            driver.pushDefaultClient();if(cached!=null)cached.pushDefaultClient();
            if(effective!=null){effective.call("glBindBuffer",new Class<?>[]{int.class,int.class},GL_ARRAY_BUFFER,0);effective.call("glBindBuffer",new Class<?>[]{int.class,int.class},GL_ELEMENT_ARRAY_BUFFER,0);}
            gl.glBindBuffer(GL_ARRAY_BUFFER,0);gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,0);
        } catch (RuntimeException | Error failure) { try { close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); } throw failure; }
    }
    /** framebuffer XY 正交矩阵直接加载到两套矩阵；不乘 GUI Scale。RenderItem 用自身 16px 坐标。 */
    public void place(ImagePlacement image, LayoutBox clip, int framebufferWidth, int framebufferHeight) {
        float[] projection = {2f/framebufferWidth,0,0,0, 0,-2f/framebufferHeight,0,0, 0,0,-.001f,0, -1,1,0,1};
        float[] model = {image.width/image.content.width,0,0,0, 0,image.height/image.content.height,0,0, 0,0,1,0, image.x,image.y,0,1};
        if (effective != null) { effective.matrix(GL_PROJECTION, projection); effective.matrix(GL_MODELVIEW, model); effective.mode(GL_MODELVIEW); }
        raw.matrix(GL_PROJECTION, projection); raw.matrix(GL_MODELVIEW, model); raw.mode(GL_MODELVIEW);
        if(effective!=null)effective.one("glUseProgram",0);
        gl.glUseProgram(0); gl.glViewport(0,0,framebufferWidth,framebufferHeight);
        if (clip != null) { gl.glEnable(GL_SCISSOR_TEST); gl.glScissor(clip.x,framebufferHeight-clip.y-clip.height,clip.width,clip.height); }
        // shader quad不依赖固定纹理enable/env/matrix；原版flat RenderItem只bind atlas，须自备旧GUI入态。
        if(effective!=null)effective.guiTextures(cached.matrices.size()-2);
        raw.guiTextures(driver.matrices.size()-2);
        gl.glActiveTexture(GL_TEXTURE0); if (effective != null) effective.active(GL_TEXTURE0);
        if (samplers) { gl.glBindSampler(0,0); if (textures.length > 1) gl.glBindSampler(1,0); }
    }
    @Override public void close() {
        if (closed) return; closed = true; RuntimeException failure = null;
        // 漏出的嵌套 attrib 先在委托当前 FBO 下 unwind；最后属于本 scope 的快照才在入口 FBO 下 pop。
        driver.unwindDelegatedAttribs();driver.unwindDelegatedClients();
        // cache pop 可写 driver，所以必须先 cache，后独立 driver 恢复。
        if (cached != null) try { cached.restore(); } catch (RuntimeException error) { failure = error; }
        // GL_COLOR_BUFFER_BIT 恢复的 draw/read buffer 属于入口 FBO，必须先重新绑定它，否则 default FBO 拒绝 COLOR_ATTACHMENT0。
        gl.glBindFramebuffer(GL_DRAW_FRAMEBUFFER,drawFbo);gl.glBindFramebuffer(36008,readFbo);
        gl.glBindVertexArray(vao);gl.glBindBuffer(GL_ARRAY_BUFFER,vbo);gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,ebo);
        try { driver.restore(); } catch (RuntimeException error) { if (failure == null) failure = error; else failure.addSuppressed(error); }
        try {
            gl.glBindVertexArray(vao); gl.glBindBuffer(GL_ARRAY_BUFFER,vbo); gl.glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,ebo);
            gl.glBindFramebuffer(GL_DRAW_FRAMEBUFFER,drawFbo); gl.glBindFramebuffer(36008,readFbo); gl.glUseProgram(program);
            gl.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            for (int i=0;i<textures.length;i++) { gl.glActiveTexture(GL_TEXTURE0+i); for(int j=0;j<TARGETS.length;j++)gl.glBindTexture(TARGETS[j],textures[i][j]);if(samplers)gl.glBindSampler(i,textures[i][4]); }
        } finally { gl.glActiveTexture(activeUnit); }
        if (failure != null) throw failure;
    }
    private static final class Matrix {
        final int mode, unit, depth, texture; final float[] top;
        Matrix(Port p,int mode,int unit) { this.mode=mode;this.unit=unit;p.active(unit);p.mode(mode);depth=p.depth(mode);texture=mode==GL_TEXTURE&&p.cached?p.integer(GL_TEXTURE_BINDING_2D):0;top=p.floats(mode==GL_PROJECTION?GL_PROJECTION_MATRIX:mode==GL_MODELVIEW?GL_MODELVIEW_MATRIX:GL_TEXTURE_MATRIX,16); }
        void restore(Port p) { p.active(unit);p.mode(mode);int depthNow=p.depth(mode);if(depthNow<depth)throw new IllegalStateException("物品 hook 弹出了调用方矩阵栈");while(depthNow-->depth)p.none("glPopMatrix");p.matrix(mode,top);if(mode==GL_TEXTURE&&p.cached)p.call("glBindTexture",new Class<?>[]{int.class,int.class},GL_TEXTURE_2D,texture); }
    }
    private static final class LegacySnapshot {
        final Port p; final int mode, active, vao, vbo, ebo, attribDepth,clientDepth,program,drawFbo,readFbo;final Captured captured;
        final float[] normal;final List<Matrix> matrices=new ArrayList<>();
        boolean pushed,clientPushed,defaultClientPushed;
        LegacySnapshot(Port p,int units) {
            this.p=p;mode=p.integer(GL_MATRIX_MODE);active=p.integer(GL_ACTIVE_TEXTURE);attribDepth=p.cached?((Number)p.none("getAttribDepth")).intValue():p.integer(2992);
            vao=p.integer(GL_VERTEX_ARRAY_BINDING);vbo=p.cached?p.integer(GL_ARRAY_BUFFER_BINDING):0;ebo=p.cached?p.integer(GL_ELEMENT_ARRAY_BUFFER_BINDING):0;
            program=p.cached?p.integer(GL_CURRENT_PROGRAM):0;drawFbo=p.cached?p.integer(GL_DRAW_FRAMEBUFFER_BINDING):0;readFbo=p.cached?p.integer(36010):0;
            clientDepth=p.clientDepth();captured=p.cached?Captured.read(p.type.getClassLoader()):null;
            normal=p.cached?p.normal():p.floats(GL_CURRENT_NORMAL,3);
            try { matrices.add(new Matrix(p,GL_PROJECTION,active));matrices.add(new Matrix(p,GL_MODELVIEW,active));for(int i=0;i<units;i++)matrices.add(new Matrix(p,GL_TEXTURE,GL_TEXTURE0+i)); }
            finally { p.active(active);p.mode(mode); }
        }
        void push(){p.one("glPushAttrib",0xfffff);pushed=true;p.one("glPushClientAttrib",-1);clientPushed=true;}
        void pushDefaultClient(){p.one("glPushClientAttrib",-1);defaultClientPushed=true;}
        void unwindDelegatedClients(){if(clientPushed){int owned=clientDepth+1+(defaultClientPushed?1:0);if(p.clientDepth()<owned)throw new IllegalStateException("物品hook弹出了调用方client栈");while(p.clientDepth()>owned)p.none("glPopClientAttrib");}}
        void restoreClients(){
            // Angelica pop只写当前VAO的flags，不绑定push时VAO；不可先bind父然后总whilepop。
            unwindDelegatedClients();
            if(defaultClientPushed){p.bindVao(0);p.none("glPopClientAttrib");defaultClientPushed=false;}
            if(clientPushed){p.bindVao(vao);p.none("glPopClientAttrib");clientPushed=false;}
        }
        void unwindDelegatedAttribs(){if(pushed)while((p.cached?((Number)p.none("getAttribDepth")).intValue():p.integer(2992))>attribDepth+1)p.none("glPopAttrib");}
        void restore(){
            RuntimeException failure=null;
            unwindDelegatedAttribs();
            try { if(p.cached){p.call("glBindFramebuffer",new Class<?>[]{int.class,int.class},GL_DRAW_FRAMEBUFFER,drawFbo);p.call("glBindFramebuffer",new Class<?>[]{int.class,int.class},36008,readFbo);}restoreClients();if(p.cached){p.one("glBindVertexArray",vao);p.call("glBindBuffer",new Class<?>[]{int.class,int.class},GL_ARRAY_BUFFER,vbo);p.call("glBindBuffer",new Class<?>[]{int.class,int.class},GL_ELEMENT_ARRAY_BUFFER,ebo);}if(pushed){if(p.cached){while(((Number)p.none("getAttribDepth")).intValue()>attribDepth)p.none("glPopAttrib");}else{while(p.integer(2992)>attribDepth)p.none("glPopAttrib");}} }
            catch(RuntimeException error){failure=error;}
            try { for(Matrix matrix:matrices)matrix.restore(p);p.call("glNormal3f",new Class<?>[]{float.class,float.class,float.class},normal[0],normal[1],normal[2]);
                if(p.cached){p.one("glUseProgram",program);p.call("glBindFramebuffer",new Class<?>[]{int.class,int.class},GL_DRAW_FRAMEBUFFER,drawFbo);p.call("glBindFramebuffer",new Class<?>[]{int.class,int.class},36008,readFbo);p.one("glBindVertexArray",vao);p.call("glBindBuffer",new Class<?>[]{int.class,int.class},GL_ARRAY_BUFFER,vbo);p.call("glBindBuffer",new Class<?>[]{int.class,int.class},GL_ELEMENT_ARRAY_BUFFER,ebo);}
            }catch(RuntimeException error){if(failure==null)failure=error;else failure.addSuppressed(error);}
            finally{try{if(captured!=null)captured.restore();}finally{p.active(active);p.mode(mode);}}if(failure!=null)throw failure;
        }
    }
    /** Iris item/entity/block uniform 跟随有效宿主上下文，不能从 driver GL 推导。 */
    private static final class Captured {
        final Object state;final int block,entity,item;final Method setBlock,setEntity;
        Captured(Class<?> type)throws ReflectiveOperationException{state=type.getField("INSTANCE").get(null);block=((Number)type.getMethod("getCurrentRenderedBlockEntity").invoke(state)).intValue();entity=((Number)type.getMethod("getCurrentRenderedEntity").invoke(state)).intValue();item=((Number)type.getMethod("getCurrentRenderedItem").invoke(state)).intValue();setBlock=type.getMethod("setCurrentBlockEntity",int.class);setEntity=type.getMethod("setCurrentEntityAndItem",int.class,int.class);}
        static Captured read(ClassLoader loader){try{return new Captured(Class.forName("net.coderbot.iris.uniforms.CapturedRenderingState",false,loader));}catch(ClassNotFoundException absent){return null;}catch(ReflectiveOperationException error){throw new IllegalStateException("无法保存Iris物品上下文",error);}}
        void restore(){try{setEntity.invoke(state,entity,item);setBlock.invoke(state,block);}catch(ReflectiveOperationException error){throw new IllegalStateException("无法归还Iris物品上下文",error);}}
    }
    /** 宿主反射端口同时支持 LWJGL2/3；core GlApi 不携带 MC 固定管线类型。 */
    private static final class Port {
        final Class<?> type,multitexture;final boolean cached;
        Port(ClassLoader loader,boolean cached){this.cached=cached;try{type=Class.forName(cached?"com.gtnewhorizons.angelica.glsm.GLStateManager":"org.lwjgl.opengl.GL11",false,loader);multitexture=cached?type:Class.forName("org.lwjgl.opengl.GL13",false,loader);}catch(ClassNotFoundException error){throw new IllegalStateException("缺少物品固定管线端口",error);}}
        static Port effective(ClassLoader loader){try{Class.forName("com.gtnewhorizons.angelica.glsm.GLStateManager",false,loader);return new Port(loader,true);}catch(ClassNotFoundException absent){return null;}}
        Object call(String name,Class<?>[] signature,Object... args){return invoke(type,name,signature,args);}
        Object none(String name){return call(name,new Class<?>[0]);}
        void one(String name,int value){call(name,new Class<?>[]{int.class},value);}
        int integer(int name){return ((Number)call("glGetInteger",new Class<?>[]{int.class},name)).intValue();}
        void bindVao(int vao){if(cached)one("glBindVertexArray",vao);else try{invoke(Class.forName("org.lwjgl.opengl.GL30",false,type.getClassLoader()),"glBindVertexArray",new Class<?>[]{int.class},vao);}catch(ClassNotFoundException error){throw new IllegalStateException("缺少VAO端口",error);}}
        void mode(int mode){one("glMatrixMode",mode);}
        void active(int unit){invoke(multitexture,"glActiveTexture",new Class<?>[]{int.class},unit);}
        float[] floats(int name,int size){FloatBuffer b=buffer(16);call("glGetFloat",new Class<?>[]{int.class,FloatBuffer.class},name,b);float[] a=new float[size];for(int i=0;i<size;i++)a[i]=b.get(i);return a;}
        void matrix(int mode,float[] top){mode(mode);FloatBuffer b=buffer(16);b.put(top).flip();call("glLoadMatrix",new Class<?>[]{FloatBuffer.class},b);}
        void guiTextures(int units){
            float[] identity={1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1};
            for(int i=0;i<units;i++){active(GL_TEXTURE0+i);one("glDisable",32879);one("glDisable",34067);one("glDisable",3552);one(i==0?"glEnable":"glDisable",GL_TEXTURE_2D);matrix(GL_TEXTURE,identity);}
            invoke(multitexture,"glClientActiveTexture",new Class<?>[]{int.class},GL_TEXTURE0);
            one("glDisableClientState",32885);one("glDisableClientState",32886);
            active(GL_TEXTURE0);call("glTexEnvi",new Class<?>[]{int.class,int.class,int.class},8960,8704,8448);mode(GL_MODELVIEW);
            call("glColor4f",new Class<?>[]{float.class,float.class,float.class,float.class},1f,1f,1f,1f);
            call("glAlphaFunc",new Class<?>[]{int.class,float.class},516,.1f);
        }
        int clientDepth(){if(!cached)return integer(2993);try{Method ctx=type.getDeclaredMethod("ctx");ctx.setAccessible(true);Object context=ctx.invoke(null);java.lang.reflect.Field depth=context.getClass().getDeclaredField("clientAttribStackPointer");depth.setAccessible(true);return depth.getInt(context);}catch(ReflectiveOperationException error){throw new IllegalStateException("无法读取有效client attrib栈",error);}}
        float[] normal(){try{Class<?> shader=Class.forName("com.gtnewhorizons.angelica.glsm.ffp.ShaderManager",false,type.getClassLoader());Object n=shader.getMethod("getCurrentNormal").invoke(null);return new float[]{n.getClass().getField("x").getFloat(n),n.getClass().getField("y").getFloat(n),n.getClass().getField("z").getFloat(n)};}catch(ReflectiveOperationException error){throw new IllegalStateException("无法读取有效normal",error);}}
        int depth(int mode){if(!cached)return integer(mode==GL_PROJECTION?2980:mode==GL_MODELVIEW?2979:2981);Object stack=none("getMatrixStack");return ((Number)call("getMatrixStackDepth",new Class<?>[]{stack.getClass()},stack)).intValue();}
        static FloatBuffer buffer(int count){return ByteBuffer.allocateDirect(count*4).order(ByteOrder.nativeOrder()).asFloatBuffer();}
        static Object invoke(Class<?> target,String name,Class<?>[] signature,Object... args){try{Method method;try{method=target.getMethod(name,signature);}catch(NoSuchMethodException alias){String alternate="glGetFloat".equals(name)&&signature.length==2?"glGetFloatv":"glLoadMatrix".equals(name)?"glLoadMatrixf":null;if(alternate==null)throw alias;method=target.getMethod(alternate,signature);}return method.invoke(null,args);}catch(InvocationTargetException error){Throwable cause=error.getCause();if(cause instanceof RuntimeException)throw (RuntimeException)cause;if(cause instanceof Error)throw (Error)cause;throw new IllegalStateException("物品状态操作失败："+name,cause);}catch(ReflectiveOperationException error){throw new IllegalStateException("物品状态端口不支持："+name,error);}}
    }
}
