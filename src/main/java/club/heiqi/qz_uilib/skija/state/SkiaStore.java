package club.heiqi.qz_uilib.skija.state;

import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.Surface;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;


public class SkiaStore {
    public static Logger LOG = LogManager.getLogger();
    // 着色器ID
    private int program;
    // 顶点
    private boolean vertexProgramPointSize,pointSmooth;
    private boolean[] vertexAttribArrayStates;
    private int vaoBinding;
    private int arrayBufferBinding;
    private int elementArrayBufferBinding;
    // 线
    private float lineWidth;
    private boolean lineSmooth;
    // 纹理 Binding
    private int activeTextureUnit;
    private final Map<Integer, Integer> textureBindings = new HashMap<>();
    // 视图
    private final int[] viewport = new int[4];
    private final int[] scissorBox = new int[4];
    private boolean scissorTest;
    // MSAA 多重采样
    private boolean multisampleEnabled;
    // 模板
    private boolean stencilTestEnabled;
    private int stencilFuncFront, stencilRefFront, stencilMaskFront;
    private int stencilFuncBack, stencilRefBack, stencilMaskBack;
    private int stencilFailFront, depthFailFront, passFront;
    private int stencilFailBack, depthFailBack, passBack;
    // FIXED_FUNCTION
    private boolean lightingEnabled;
    private boolean fogEnabled;
    private final FloatBuffer fogColor = BufferUtils.createFloatBuffer(1);
    // 面剔除
    private boolean cullFaceEnabled;
    private int cullFaceMode, frontFace;
    // 颜色+混合
    private final FloatBuffer currentColor = BufferUtils.createFloatBuffer(4);
    private boolean blendEnabled,frameBufferSRGBEnabled;
    private int blendSrcRGB, blendDstRGB, blendSrcAlpha, blendDstAlpha;
    private int blendEquation, blendEquationRGB, blendEquationAlpha;
    private boolean colorMaskR, colorMaksG, colorMaskB, colorMaskA;
    // 深度
    private boolean depthTest,depthMask;
    private int depthFunc;
    private final FloatBuffer depthRange = BufferUtils.createFloatBuffer(2);
    // 多边形
    private boolean polygonStipple,polygonOffsetFill,polygonSmooth;
    // LOGIC OP
    private boolean colorLogicOp,indexLogicOp;
    // MISC
    private boolean ditherEnabled;

    // 反射方法
    public static MethodHandle glGetInteger, glGetIntegerBuffer, glGetVertexAttrib, glIsEnabled, glGetFloatBuffer;
    public static MethodHandle glUseProgram, glEnableVertexAttribArray, glDisableVertexAttribArray, glBindVertexArray, glBindBuffer,
            glActiveTexture, glBindTexture, glEnable, glDisable, glCullFace, glFrontFace, glColor4f, glBlendFuncSeparate, glColorMask,
            glBlendFunc,glDepthFunc,glDepthMask,glDepthRange,glStencilFuncSeparate,glStencilOpSeparate,glBindSampler,glSamplerParameteri,
            glGetSamplerParameteri,glGetBoolean,glGetBooleanBuffer,glBindFramebuffer,glPixelStorei,glPushAttrib,glMatrixMode,
            glPushMatrix,glGetFloat,glLineWidth,glBlendEquation,glBlendEquationSeparate,glTexCoord2f,glVertex3f;
    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            glGetInteger = lookup.findStatic(GL11.class, "glGetInteger", MethodType.methodType(int.class, int.class));
            glGetIntegerBuffer = lookup.findStatic(GL11.class, "glGetInteger", MethodType.methodType(void.class,int.class,IntBuffer.class));
            glGetVertexAttrib = lookup.findStatic(GL20.class, "glGetVertexAttrib", MethodType.methodType(void.class,int.class,int.class,IntBuffer.class));
            glIsEnabled = lookup.findStatic(GL11.class, "glIsEnabled", MethodType.methodType(boolean.class,int.class));
            glGetFloatBuffer = lookup.findStatic(GL11.class, "glGetFloat", MethodType.methodType(void.class,int.class,FloatBuffer.class));
            glGetFloat = lookup.findStatic(GL11.class, "glGetFloat", MethodType.methodType(float.class,int.class));
            glUseProgram = lookup.findStatic(GL20.class, "glUseProgram", MethodType.methodType(void.class,int.class));
            glEnableVertexAttribArray = lookup.findStatic(GL20.class, "glEnableVertexAttribArray", MethodType.methodType(void.class,int.class));
            glDisableVertexAttribArray = lookup.findStatic(GL20.class, "glDisableVertexAttribArray", MethodType.methodType(void.class,int.class));
            glBindVertexArray = lookup.findStatic(GL30.class, "glBindVertexArray", MethodType.methodType(void.class,int.class));
            glBindBuffer = lookup.findStatic(GL15.class, "glBindBuffer", MethodType.methodType(void.class,int.class,int.class));
            glActiveTexture = lookup.findStatic(GL13.class, "glActiveTexture", MethodType.methodType(void.class,int.class));
            glBindTexture = lookup.findStatic(GL11.class, "glBindTexture", MethodType.methodType(void.class,int.class,int.class));
            glEnable = lookup.findStatic(GL11.class, "glEnable", MethodType.methodType(void.class,int.class));
            glDisable = lookup.findStatic(GL11.class, "glDisable", MethodType.methodType(void.class,int.class));
            glCullFace = lookup.findStatic(GL11.class, "glCullFace", MethodType.methodType(void.class,int.class));
            glFrontFace = lookup.findStatic(GL11.class, "glFrontFace", MethodType.methodType(void.class,int.class));
            glColor4f = lookup.findStatic(GL11.class, "glColor4f", MethodType.methodType(void.class,float.class,float.class,float.class,float.class));
            glBlendFuncSeparate = lookup.findStatic(GL14.class, "glBlendFuncSeparate", MethodType.methodType(void.class,int.class,int.class,int.class,int.class));
            glColorMask = lookup.findStatic(GL11.class, "glColorMask", MethodType.methodType(void.class,boolean.class,boolean.class,boolean.class,boolean.class));
            glDepthFunc = lookup.findStatic(GL11.class, "glDepthFunc", MethodType.methodType(void.class,int.class));
            glDepthMask = lookup.findStatic(GL11.class, "glDepthMask", MethodType.methodType(void.class,boolean.class));
            glDepthRange = lookup.findStatic(GL11.class, "glDepthRange", MethodType.methodType(void.class,double.class,double.class));
            glStencilFuncSeparate = lookup.findStatic(GL20.class, "glStencilFuncSeparate", MethodType.methodType(void.class,int.class,int.class,int.class,int.class));
            glStencilOpSeparate = lookup.findStatic(GL20.class, "glStencilOpSeparate", MethodType.methodType(void.class,int.class,int.class,int.class,int.class));
            glGetBoolean = lookup.findStatic(GL11.class, "glGetBoolean", MethodType.methodType(boolean.class,int.class));
            glGetBooleanBuffer = lookup.findStatic(GL11.class, "glGetBoolean", MethodType.methodType(void.class,int.class,ByteBuffer.class));
            glLineWidth = lookup.findStatic(GL11.class, "glLineWidth", MethodType.methodType(void.class,float.class));
            glBlendEquation = lookup.findStatic(GL14.class, "glBlendEquation", MethodType.methodType(void.class,int.class));
            glBlendEquationSeparate = lookup.findStatic(GL20.class, "glBlendEquationSeparate", MethodType.methodType(void.class,int.class,int.class));

            // 提供给外部内部未使用的
            glBlendFunc = lookup.findStatic(GL11.class, "glBlendFunc", MethodType.methodType(void.class,int.class,int.class));
            glBindSampler = lookup.findStatic(GL33.class, "glBindSampler", MethodType.methodType(void.class,int.class,int.class));
            glSamplerParameteri = lookup.findStatic(GL33.class, "glSamplerParameteri", MethodType.methodType(void.class,int.class,int.class,int.class));
            glGetSamplerParameteri = lookup.findStatic(GL33.class, "glGetSamplerParameteri", MethodType.methodType(int.class,int.class,int.class));
            glBindFramebuffer = lookup.findStatic(GL30.class, "glBindFramebuffer", MethodType.methodType(void.class,int.class,int.class));
            glPixelStorei = lookup.findStatic(GL11.class, "glPixelStorei", MethodType.methodType(void.class,int.class,int.class));
            glPushAttrib = lookup.findStatic(GL11.class, "glPushAttrib", MethodType.methodType(void.class,int.class));
            glMatrixMode = lookup.findStatic(GL11.class, "glMatrixMode", MethodType.methodType(void.class,int.class));
            glPushMatrix = lookup.findStatic(GL11.class, "glPushMatrix", MethodType.methodType(void.class));
            glTexCoord2f = lookup.findStatic(GL11.class,"glTexCoord2f", MethodType.methodType(void.class,float.class,float.class));
            glVertex3f = lookup.findStatic(GL11.class, "glVertex3f", MethodType.methodType(void.class,float.class,float.class,float.class));
        }
        catch (Exception e) {
            throw new RuntimeException("保存器初始化失败" + e);
        }
    }


    public void backup() {
        try {
            program = (int) glGetInteger.invoke(GL20.GL_CURRENT_PROGRAM);
            // 获取支持的顶点属性最大数量
            IntBuffer maxAttribs = BufferUtils.createIntBuffer(1);
            glGetIntegerBuffer.invoke(GL20.GL_MAX_VERTEX_ATTRIBS, maxAttribs);
            vertexAttribArrayStates = new boolean[maxAttribs.get(0)];
            // 顶点状态
            IntBuffer enable = BufferUtils.createIntBuffer(1);
            for (int i = 0; i < vertexAttribArrayStates.length; i++) {
                glGetVertexAttrib.invoke(i, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED, enable);
                vertexAttribArrayStates[i] = enable.get(0) == GL11.GL_TRUE;
            }
            vertexProgramPointSize = (boolean) glGetBoolean.invoke(GL20.GL_VERTEX_PROGRAM_POINT_SIZE);
            pointSmooth = (boolean) glGetBoolean.invoke(GL11.GL_POINT_SMOOTH);
            vaoBinding = (int) glGetInteger.invoke(GL30.GL_VERTEX_ARRAY_BINDING);
            arrayBufferBinding = (int) glGetInteger.invoke(GL15.GL_ARRAY_BUFFER_BINDING);
            elementArrayBufferBinding = (int) glGetInteger.invoke(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
            // 线
            lineWidth = (float) glGetFloat.invoke(GL11.GL_LINE_WIDTH);
            lineSmooth = (boolean) glGetBoolean.invoke(GL11.GL_LINE_SMOOTH);
            // 纹理
            activeTextureUnit = (int) glGetInteger.invoke(GL13.GL_ACTIVE_TEXTURE);
            // 备份纹理目标的绑定状态
            Map<Integer, Integer> bindingToTarget = new HashMap<>();
            bindingToTarget.put(GL11.GL_TEXTURE_BINDING_2D, GL11.GL_TEXTURE_2D);
            bindingToTarget.put(GL12.GL_TEXTURE_BINDING_3D, GL12.GL_TEXTURE_3D);
            bindingToTarget.put(GL13.GL_TEXTURE_BINDING_CUBE_MAP, GL13.GL_TEXTURE_CUBE_MAP);
            for (Map.Entry<Integer, Integer> entry : bindingToTarget.entrySet()) {
                int bindingEnum = entry.getKey();
                int target = entry.getValue();
                int value = (int) glGetInteger.invoke(bindingEnum);
                textureBindings.put(target, value);
            }
            // 视图
            IntBuffer buffer = BufferUtils.createIntBuffer(4);
            glGetIntegerBuffer.invoke(GL11.GL_VIEWPORT, buffer);
            buffer.get(viewport);
            buffer.clear();
            glGetIntegerBuffer.invoke(GL11.GL_SCISSOR_BOX, buffer);
            buffer.get(scissorBox);
            scissorTest = (boolean) glGetBoolean.invoke(GL11.GL_SCISSOR_TEST);
            buffer.clear();
            // MSAA
            multisampleEnabled = (boolean) glIsEnabled.invoke(GL13.GL_MULTISAMPLE);
            // 模板
            {
                stencilTestEnabled = (boolean) glIsEnabled.invoke(GL11.GL_STENCIL_TEST);
                // Front
                glGetIntegerBuffer.invoke(GL11.GL_STENCIL_FUNC, buffer);
                stencilFuncFront = buffer.get(0);
                buffer.clear();
                glGetIntegerBuffer.invoke(GL11.GL_STENCIL_REF, buffer);
                stencilRefFront = buffer.get(0);
                buffer.clear();
                glGetIntegerBuffer.invoke(GL11.GL_STENCIL_VALUE_MASK, buffer);
                stencilMaskFront = buffer.get(0);
                buffer.clear();
                glGetIntegerBuffer.invoke(GL11.GL_STENCIL_FAIL, buffer);
                stencilFailFront = buffer.get(0);
                buffer.clear();
                glGetIntegerBuffer.invoke(GL11.GL_STENCIL_PASS_DEPTH_FAIL, buffer);
                depthFailFront = buffer.get(0);
                buffer.clear();
                glGetIntegerBuffer.invoke(GL11.GL_STENCIL_PASS_DEPTH_PASS, buffer);
                passFront = buffer.get(0);
                buffer.clear();
                // Back（需要OpenGL 2.0+）
                glGetIntegerBuffer.invoke(GL20.GL_STENCIL_BACK_FUNC, buffer);
                stencilFuncBack = buffer.get(0);
                buffer.clear();
                glGetIntegerBuffer.invoke(GL20.GL_STENCIL_BACK_REF, buffer);
                stencilRefBack = buffer.get(0);
                buffer.clear();
                glGetIntegerBuffer.invoke(GL20.GL_STENCIL_BACK_VALUE_MASK, buffer);
                stencilMaskBack = buffer.get(0);
                buffer.clear();
                glGetIntegerBuffer.invoke(GL20.GL_STENCIL_BACK_FAIL, buffer);
                stencilFailBack = buffer.get(0);
                buffer.clear();
                glGetIntegerBuffer.invoke(GL20.GL_STENCIL_BACK_PASS_DEPTH_FAIL, buffer);
                depthFailBack = buffer.get(0);
                buffer.clear();
                glGetIntegerBuffer.invoke(GL20.GL_STENCIL_BACK_PASS_DEPTH_PASS, buffer);
                passBack = buffer.get(0);
                buffer.clear();
            }
            // fixed
            lightingEnabled = (boolean) SkiaStore.glIsEnabled.invoke(GL11.GL_LIGHTING);
            fogEnabled = (boolean) SkiaStore.glIsEnabled.invoke(GL11.GL_FOG);
            if (fogEnabled) glGetFloatBuffer.invoke(GL11.GL_FOG_COLOR, fogColor);
            // 面剔除
            cullFaceEnabled = (boolean) glIsEnabled.invoke(GL11.GL_CULL_FACE);
            cullFaceMode = (int) glGetInteger.invoke(GL11.GL_CULL_FACE_MODE);
            frontFace = (int) glGetInteger.invoke(GL11.GL_FRONT_FACE);
            // 颜色+混合
            glGetFloatBuffer.invoke(GL11.GL_CURRENT_COLOR,currentColor);
            blendEnabled = (boolean) glIsEnabled.invoke(GL11.GL_BLEND);
            frameBufferSRGBEnabled = (boolean) glIsEnabled.invoke(GL30.GL_FRAMEBUFFER_SRGB);
            blendSrcRGB = (int) glGetInteger.invoke(GL14.GL_BLEND_SRC_RGB);
            blendDstRGB = (int) glGetInteger.invoke(GL14.GL_BLEND_DST_RGB);
            blendSrcAlpha = (int) glGetInteger.invoke(GL14.GL_BLEND_SRC_ALPHA);
            blendDstAlpha = (int) glGetInteger.invoke(GL14.GL_BLEND_DST_ALPHA);

            blendEquation = (int) glGetInteger.invoke(GL14.GL_BLEND_EQUATION);
            blendEquationRGB = (int) glGetInteger.invoke(GL20.GL_BLEND_EQUATION_RGB);
            blendEquationAlpha = (int) glGetInteger.invoke(GL20.GL_BLEND_EQUATION_ALPHA);
            ByteBuffer buffer1 = BufferUtils.createByteBuffer(4);
            SkiaStore.glGetBooleanBuffer.invoke(GL11.GL_COLOR_WRITEMASK,buffer1);
            colorMaskR = buffer1.get(0)==1;
            colorMaksG = buffer1.get(1)==1;
            colorMaskB = buffer1.get(2)==1;
            colorMaskA = buffer1.get(3)==1;
            // 深度
            depthTest = (boolean) glIsEnabled.invoke(GL11.GL_DEPTH_TEST);
            depthFunc = (int) glGetInteger.invoke(GL11.GL_DEPTH_FUNC);
            depthMask = (boolean) SkiaStore.glGetBoolean.invoke(GL11.GL_DEPTH_WRITEMASK);
            glGetFloatBuffer.invoke(GL11.GL_DEPTH_RANGE,depthRange);
            // 多边形
            polygonStipple = (boolean) glIsEnabled.invoke(GL11.GL_POLYGON_STIPPLE);
            polygonOffsetFill = (boolean) glIsEnabled.invoke(GL11.GL_POLYGON_OFFSET_FILL);
            polygonSmooth = (boolean) glIsEnabled.invoke(GL11.GL_POLYGON_SMOOTH);
            // LOGIC OP
            colorLogicOp = (boolean) glIsEnabled.invoke(GL11.GL_COLOR_LOGIC_OP);
            indexLogicOp = (boolean) glIsEnabled.invoke(GL11.GL_INDEX_LOGIC_OP);
            // MISC
            ditherEnabled = (boolean) glIsEnabled.invoke(GL11.GL_DITHER);


            // 最后调用一下空着色器提示安洁莉卡切换过着色器
            /*glUseProgram.invoke(ShaderManager.shaderManagers.get(ShaderName.VOID_SHADER.name()).shaderID);*/
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public void restore() {
        try {
            GL20.glUseProgram(program);
            glUseProgram.invoke(program);

            // 顶点数组
            for (int i = 0; i < vertexAttribArrayStates.length; i++) {
                if (vertexAttribArrayStates[i]) {
                    GL20.glEnableVertexAttribArray(i);
                    glEnableVertexAttribArray.invoke(i);
                }
                else {
                    GL20.glDisableVertexAttribArray(i);
                    glDisableVertexAttribArray.invoke(i);
                }
            }
            setCapability(GL20.GL_VERTEX_PROGRAM_POINT_SIZE,vertexProgramPointSize);
            setCapability(GL11.GL_POINT_SMOOTH,pointSmooth);
            GL30.glBindVertexArray(vaoBinding);
            glBindVertexArray.invoke(vaoBinding);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBufferBinding);
            GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementArrayBufferBinding);
            glBindBuffer.invoke(GL15.GL_ARRAY_BUFFER, arrayBufferBinding);
            glBindBuffer.invoke(GL15.GL_ELEMENT_ARRAY_BUFFER, elementArrayBufferBinding);
            // 线
            GL11.glLineWidth(lineWidth);
            SkiaStore.glLineWidth.invoke(lineWidth);
            setCapability(GL11.GL_LINE_SMOOTH,lineSmooth);
            // 纹理
            GL13.glActiveTexture(activeTextureUnit); // 恢复活动的纹理单元
            glActiveTexture.invoke(activeTextureUnit); // 恢复活动的纹理单元
            // 恢复各纹理目标绑定
            for (Map.Entry<Integer, Integer> entry : textureBindings.entrySet()) {
                int target = entry.getKey();
                int value = entry.getValue();
                GL11.glBindTexture(target, value);
                glBindTexture.invoke(target, value);
            }
            // 恢复视图
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GL11.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
            setCapability(GL11.GL_SCISSOR_TEST, scissorTest);
            // MSAA
            setCapability(GL13.GL_MULTISAMPLE, multisampleEnabled);
            // 模板
            setCapability(GL11.GL_STENCIL_TEST, stencilTestEnabled);
            GL20.glStencilFuncSeparate(GL11.GL_FRONT, stencilFuncFront, stencilRefFront, stencilMaskFront);
            GL20.glStencilOpSeparate(GL11.GL_FRONT, stencilFailFront, depthFailFront, passFront);
            GL20.glStencilFuncSeparate(GL11.GL_BACK, stencilFuncBack, stencilRefBack, stencilMaskBack);
            GL20.glStencilOpSeparate(GL11.GL_BACK, stencilFailBack, depthFailBack, passBack);
            glStencilFuncSeparate.invoke(GL11.GL_FRONT, stencilFuncFront, stencilRefFront, stencilMaskFront);
            glStencilOpSeparate.invoke(GL11.GL_FRONT, stencilFailFront, depthFailFront, passFront);
            glStencilFuncSeparate.invoke(GL11.GL_BACK, stencilFuncBack, stencilRefBack, stencilMaskBack);
            glStencilOpSeparate.invoke(GL11.GL_BACK, stencilFailBack, depthFailBack, passBack);
            // fixed
            setCapability(GL11.GL_LIGHTING, lightingEnabled);
            setCapability(GL11.GL_FOG, fogEnabled);
            if(fogEnabled) {
                GL11.glFogf(GL11.GL_FOG_COLOR, fogColor.get());
            }
            // 面剔除
            // 提示安洁莉卡关闭过背面剔除
            setCapability(GL11.GL_CULL_FACE, cullFaceEnabled);
            GL11.glCullFace(cullFaceMode);
            GL11.glFrontFace(frontFace);
            glCullFace.invoke(cullFaceMode);
            glFrontFace.invoke(frontFace);

            // 颜色+混合
            GL11.glColor4f(currentColor.get(0), currentColor.get(1), currentColor.get(2), currentColor.get(3));
            glColor4f.invoke(currentColor.get(0), currentColor.get(1), currentColor.get(2), currentColor.get(3));
            setCapability(GL11.GL_BLEND, blendEnabled);
            setCapability(GL30.GL_FRAMEBUFFER_SRGB, frameBufferSRGBEnabled);
            if (blendEnabled) {
                GL14.glBlendFuncSeparate(blendSrcRGB, blendDstRGB, blendSrcAlpha, blendDstAlpha);
                GL11.glColorMask(colorMaskR, colorMaksG, colorMaskB, colorMaskA);
                glBlendFuncSeparate.invoke(blendSrcRGB, blendDstRGB, blendSrcAlpha, blendDstAlpha);
                glColorMask.invoke(colorMaskR, colorMaksG, colorMaskB, colorMaskA);

                GL14.glBlendEquation(blendEquation);
                GL20.glBlendEquationSeparate(blendEquationRGB, blendEquationAlpha);
                glBlendEquation.invoke(blendEquation);
                glBlendEquationSeparate.invoke(blendEquationRGB, blendEquationAlpha);
            }
            // 深度
            setCapability(GL11.GL_DEPTH_TEST, depthTest);
            GL11.glDepthFunc(depthFunc);
            GL11.glDepthMask(depthMask);
            GL11.glDepthRange(depthRange.get(0), depthRange.get(1));
            glDepthFunc.invoke(depthFunc);
            glDepthMask.invoke(depthMask);
            glDepthRange.invoke(depthRange.get(0), depthRange.get(1));
            // 多边形
            setCapability(GL11.GL_POLYGON_STIPPLE,polygonStipple);
            setCapability(GL11.GL_POLYGON_OFFSET_FILL,polygonOffsetFill);
            setCapability(GL11.GL_POLYGON_SMOOTH,polygonSmooth);
            // LOGIC OP
            setCapability(GL11.GL_COLOR_LOGIC_OP,colorLogicOp);
            setCapability(GL11.GL_INDEX_LOGIC_OP,indexLogicOp);
            // MISC
            setCapability(GL11.GL_DITHER,ditherEnabled);


        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }


    // 辅助方法：统一设置开关状态
    private void setCapability(int glEnum, boolean enabled) {
        try {
            if(enabled) {
                GL11.glEnable(glEnum);
                glEnable.invoke(glEnum);
            }
            else {
                GL11.glDisable(glEnum);
                glDisable.invoke(glEnum);
            }
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
