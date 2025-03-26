package club.heiqi.skija.state;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_3D;
import static org.lwjgl.opengl.GL12.GL_TEXTURE_BINDING_3D;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED;

public class SkiaStore {
    private int program;
    // 顶点
    private boolean[] vertexAttribArrayStates;
    private int vaoBinding;
    private int arrayBufferBinding;
    private int elementArrayBufferBinding;

    // 纹理 Binding
    private int activeTextureUnit;
    private final Map<Integer, Integer> textureBindings = new HashMap<>();
    // 视图
    private final int[] viewport = new int[4];
    private final int[] scissorBox = new int[4];
    // 混合
    private boolean blendEnabled;
    private int blendSrcRGB, blendDstRGB, blendSrcAlpha, blendDstAlpha;
    private int blendEquationRGB, blendEquationAlpha;
    // MSAA
    private boolean msaaEnabled;
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
    // 需要添加状态保存[depthMask/cullFace/polygonMode/colorMask]


    public void backup() {
        program = glGetInteger(GL_CURRENT_PROGRAM);

        // 获取支持的顶点属性最大数量
        IntBuffer maxAttribs = BufferUtils.createIntBuffer(1);
        glGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS, maxAttribs);
        vertexAttribArrayStates = new boolean[maxAttribs.get(0)];
        // 顶点状态
        IntBuffer enable = BufferUtils.createIntBuffer(1);
        for (int i = 0; i < vertexAttribArrayStates.length; i++) {
            glGetVertexAttrib(i,GL_VERTEX_ATTRIB_ARRAY_ENABLED, enable);
            vertexAttribArrayStates[i] = enable.get(0) == GL_TRUE;
        }
        vaoBinding = glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        arrayBufferBinding = glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        elementArrayBufferBinding = glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        // 纹理
        activeTextureUnit = glGetInteger(GL_ACTIVE_TEXTURE);
        // 备份纹理目标的绑定状态
        Map<Integer, Integer> bindingToTarget = new HashMap<>();
        bindingToTarget.put(GL_TEXTURE_BINDING_2D, GL_TEXTURE_2D);
        bindingToTarget.put(GL_TEXTURE_BINDING_3D, GL_TEXTURE_3D);
        bindingToTarget.put(GL_TEXTURE_BINDING_CUBE_MAP, GL_TEXTURE_CUBE_MAP);
        for (Map.Entry<Integer, Integer> entry : bindingToTarget.entrySet()) {
            int bindingEnum = entry.getKey();
            int target = entry.getValue();
            int value = glGetInteger(bindingEnum);
            textureBindings.put(target, value);
        }
        // 视图
        IntBuffer buffer = BufferUtils.createIntBuffer(4);
        glGetInteger(GL_VIEWPORT, buffer);
        buffer.get(viewport); buffer.clear();
        glGetInteger(GL_SCISSOR_BOX, buffer);
        buffer.get(scissorBox); buffer.clear();
        // 混合
        blendEnabled = glIsEnabled(GL_BLEND);
        glGetInteger(GL_BLEND_SRC_RGB, buffer);
        buffer.get(blendSrcRGB); buffer.clear();
        glGetInteger(GL_BLEND_DST_RGB, buffer);
        buffer.get(blendDstRGB); buffer.clear();
        glGetInteger(GL_BLEND_SRC_ALPHA, buffer);
        buffer.get(blendSrcAlpha); buffer.clear();
        glGetInteger(GL_BLEND_DST_ALPHA, buffer);
        buffer.get(blendDstAlpha); buffer.clear();
        glGetInteger(GL_BLEND_EQUATION_RGB, buffer);
        buffer.get(blendEquationRGB); buffer.clear();
        glGetInteger(GL_BLEND_EQUATION_ALPHA, buffer);
        buffer.get(blendEquationAlpha); buffer.clear();
        // MSAA
        msaaEnabled = GL11.glIsEnabled(GL13.GL_MULTISAMPLE);
        // 模板
        {
            stencilTestEnabled = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
            // Front
            GL11.glGetInteger(GL11.GL_STENCIL_FUNC, buffer);
            stencilFuncFront = buffer.get(0); buffer.clear();
            GL11.glGetInteger(GL11.GL_STENCIL_REF, buffer);
            stencilRefFront = buffer.get(0); buffer.clear();
            GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK, buffer);
            stencilMaskFront = buffer.get(0); buffer.clear();
            GL11.glGetInteger(GL11.GL_STENCIL_FAIL, buffer);
            stencilFailFront = buffer.get(0); buffer.clear();
            GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL, buffer);
            depthFailFront = buffer.get(0); buffer.clear();
            GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS, buffer);
            passFront = buffer.get(0); buffer.clear();
            // Back（需要OpenGL 2.0+）
            GL11.glGetInteger(GL_STENCIL_BACK_FUNC, buffer);
            stencilFuncBack = buffer.get(0); buffer.clear();
            GL11.glGetInteger(GL_STENCIL_BACK_REF, buffer);
            stencilRefBack = buffer.get(0); buffer.clear();
            GL11.glGetInteger(GL_STENCIL_BACK_VALUE_MASK, buffer);
            stencilMaskBack = buffer.get(0); buffer.clear();
            GL11.glGetInteger(GL_STENCIL_BACK_FAIL, buffer);
            stencilFailBack = buffer.get(0); buffer.clear();
            GL11.glGetInteger(GL_STENCIL_BACK_PASS_DEPTH_FAIL, buffer);
            depthFailBack = buffer.get(0); buffer.clear();
            GL11.glGetInteger(GL_STENCIL_BACK_PASS_DEPTH_PASS, buffer);
            passBack = buffer.get(0); buffer.clear();
        }
        // fixed
        lightingEnabled = GL11.glIsEnabled(GL11.GL_LIGHTING);
        fogEnabled = GL11.glIsEnabled(GL11.GL_FOG);
        if(fogEnabled) glGetFloat(GL11.GL_FOG_COLOR, fogColor);

        glPushMatrix();
        glPushAttrib(GL_ALL_ATTRIB_BITS);
        glPushClientAttrib(GL_ALL_CLIENT_ATTRIB_BITS);
    }

    public void restore() {
        glUseProgram(program);
        glPopClientAttrib();
        glPopAttrib();
        glPopMatrix();

        // 顶点数组
        for (int i = 0; i < vertexAttribArrayStates.length; i++) {
            if (vertexAttribArrayStates[i]) glEnableVertexAttribArray(i);
            else glDisableVertexAttribArray(i);
        }
        GL30.glBindVertexArray(vaoBinding);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBufferBinding);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementArrayBufferBinding);
        // 纹理
        glActiveTexture(activeTextureUnit); // 恢复活动的纹理单元
        // 恢复各纹理目标绑定
        for (Map.Entry<Integer, Integer> entry : textureBindings.entrySet()) {
            int target = entry.getKey();
            int value = entry.getValue();
            glBindTexture(target, value);
        }
        // 恢复视图
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);
        // 恢复混合
        if (blendEnabled) {
            glBlendFuncSeparate(blendSrcRGB, blendDstRGB, blendSrcAlpha, blendDstAlpha);
            glBlendEquationSeparate(blendEquationRGB, blendEquationAlpha);
        }
        // MSAA
        setCapability(GL13.GL_MULTISAMPLE, msaaEnabled);
        // 模板
        setCapability(GL11.GL_STENCIL_TEST, stencilTestEnabled);
        glStencilFuncSeparate(GL11.GL_FRONT, stencilFuncFront, stencilRefFront, stencilMaskFront);
        glStencilOpSeparate(GL11.GL_FRONT, stencilFailFront, depthFailFront, passFront);
        glStencilFuncSeparate(GL11.GL_BACK, stencilFuncBack, stencilRefBack, stencilMaskBack);
        glStencilOpSeparate(GL11.GL_BACK, stencilFailBack, depthFailBack, passBack);
        // fixed
        setCapability(GL11.GL_LIGHTING, lightingEnabled);
        setCapability(GL11.GL_FOG, fogEnabled);
        if(fogEnabled) glFogf(GL11.GL_FOG_COLOR, fogColor.get());
    }


    // 辅助方法：统一设置开关状态
    private void setCapability(int glEnum, boolean enabled) {
        if(enabled) GL11.glEnable(glEnum);
        else GL11.glDisable(glEnum);
    }
}
