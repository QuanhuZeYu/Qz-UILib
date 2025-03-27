package club.heiqi.qz_uilib.skija.state;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL32.*;

public class GLStateStore {
    // 着色器
    private int currentProgram;

    // 存储顶点属性数组的启用状态
    private final boolean[] vertexAttribArrayStates;

    // 矩阵状态
    private int matrixMode;
    private final FloatBuffer projectionMatrix = BufferUtils.createFloatBuffer(16);
    private final FloatBuffer modelViewMatrix = BufferUtils.createFloatBuffer(16);

    // 启用状态
    private boolean depthTest, cullFace, stencilTest, scissorTest;
    private boolean multisample, polygonSmooth, lineSmooth, programPointSize;
    private boolean vertexArray, colorArray, normalArray;

    // 混合
    private int blendSrcRGB, blendDstRGB, blendSrcAlpha, blendDstAlpha;
    private boolean isBlend;

    // 深度
    private boolean depthMask;
    private int depthFunc;

    // 模板
    private int stencilFuncFront, stencilRefFront, stencilMaskFront;
    private int stencilFuncBack, stencilRefBack, stencilMaskBack;
    private int stencilFailFront, zfailFront, zpassFront;
    private int stencilFailBack, zfailBack, zpassBack;

    // 点线状态 states
    private float lineWidth, pointSize;

    // 视口/Scissor
    private int viewportX, viewportY, viewportW, viewportH;
    private int scissorX, scissorY, scissorW, scissorH;

    // 数组绑定
    private int vao, arrayBuffer, elementArrayBuffer;

    // frame
    private int readFramebuffer, drawFramebuffer;


    public GLStateStore() {
        // 获取支持的顶点属性最大数量
        IntBuffer maxAttribs = BufferUtils.createIntBuffer(1);
        glGetInteger(GL20.GL_MAX_VERTEX_ATTRIBS, maxAttribs);
        vertexAttribArrayStates = new boolean[maxAttribs.get(0)];
    }

    public void backup() {
        // 顶点状态
        IntBuffer enable = BufferUtils.createIntBuffer(1);
        for (int i = 0; i < vertexAttribArrayStates.length; i++) {
            glGetVertexAttrib(i,GL_VERTEX_ATTRIB_ARRAY_ENABLED, enable);
            if (enable.get(0) == GL_TRUE) {
                vertexAttribArrayStates[i] = true;
            }
            else {
                vertexAttribArrayStates[i] = false;
            }
        }
        // 矩阵
        matrixMode = glGetInteger(GL_MATRIX_MODE);

        // 备份投影矩阵
        projectionMatrix.clear();
        glGetFloat(GL_PROJECTION_MATRIX, projectionMatrix);
        projectionMatrix.rewind();

        // 备份模型视图矩阵
        modelViewMatrix.clear();
        glGetFloat(GL_MODELVIEW_MATRIX, modelViewMatrix);
        modelViewMatrix.rewind();

        // Enable states
        depthTest = glIsEnabled(GL_DEPTH_TEST);
        cullFace = glIsEnabled(GL_CULL_FACE);
        stencilTest = glIsEnabled(GL_STENCIL_TEST);
        scissorTest = glIsEnabled(GL_SCISSOR_TEST);
        multisample = glIsEnabled(GL_MULTISAMPLE);
        polygonSmooth = glIsEnabled(GL_POLYGON_SMOOTH);
        lineSmooth = glIsEnabled(GL_LINE_SMOOTH);
        programPointSize = glIsEnabled(GL_PROGRAM_POINT_SIZE);
        vertexArray = glIsEnabled(GL_VERTEX_ARRAY);
        colorArray = glIsEnabled(GL_COLOR_ARRAY);
        normalArray = glIsEnabled(GL_NORMAL_ARRAY);

        // Blend states
        isBlend = glGetBoolean(GL_BLEND);
        blendSrcRGB = glGetInteger(GL_BLEND_SRC_RGB);
        blendDstRGB = glGetInteger(GL_BLEND_DST_RGB);
        blendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA);
        blendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);

        // Depth states
        depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        depthFunc = glGetInteger(GL_DEPTH_FUNC);

        // Stencil states
        stencilFuncFront = glGetInteger(GL_STENCIL_FUNC);
        stencilRefFront = glGetInteger(GL_STENCIL_REF);
        stencilMaskFront = glGetInteger(GL_STENCIL_VALUE_MASK);
        stencilFuncBack = glGetInteger(GL_STENCIL_BACK_FUNC);
        stencilRefBack = glGetInteger(GL_STENCIL_BACK_REF);
        stencilMaskBack = glGetInteger(GL_STENCIL_BACK_VALUE_MASK);
        stencilFailFront = glGetInteger(GL_STENCIL_FAIL);
        zfailFront = glGetInteger(GL_STENCIL_PASS_DEPTH_FAIL);
        zpassFront = glGetInteger(GL_STENCIL_PASS_DEPTH_PASS);
        stencilFailBack = glGetInteger(GL_STENCIL_BACK_FAIL);
        zfailBack = glGetInteger(GL_STENCIL_BACK_PASS_DEPTH_FAIL);
        zpassBack = glGetInteger(GL_STENCIL_BACK_PASS_DEPTH_PASS);

        // Line/Point
        lineWidth = glGetFloat(GL_LINE_WIDTH);
        pointSize = glGetFloat(GL_POINT_SIZE);

        // Viewport/Scissor
        IntBuffer intBuffer = BufferUtils.createIntBuffer(4);
        glGetInteger(GL_VIEWPORT, intBuffer);
        viewportX = intBuffer.get(0);
        viewportY = intBuffer.get(1);
        viewportW = intBuffer.get(2);
        viewportH = intBuffer.get(3);

        glGetInteger(GL_SCISSOR_BOX, intBuffer);
        scissorX = intBuffer.get(0);
        scissorY = intBuffer.get(1);
        scissorW = intBuffer.get(2);
        scissorH = intBuffer.get(3);

        // Bindings
        vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        arrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        elementArrayBuffer = glGetInteger(GL_ELEMENT_ARRAY_BUFFER_BINDING);
        currentProgram = glGetInteger(GL_CURRENT_PROGRAM);

        // Framebuffers
        readFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        drawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
    }

    public void restore() {
        // 着色器
        glUseProgram(currentProgram);
        // 顶点数组
        for (int i = 0; i < vertexAttribArrayStates.length; i++) {
            if (vertexAttribArrayStates[i]) {
                glEnableVertexAttribArray(i);
            }
            else {
                glDisableVertexAttribArray(i);
            }
        }
        // 恢复投影矩阵
        glMatrixMode(GL_PROJECTION);
        glLoadMatrix(projectionMatrix);

        // 恢复模型视图矩阵
        glMatrixMode(GL_MODELVIEW);
        glLoadMatrix(modelViewMatrix);

        // 恢复纹理矩阵
        glMatrixMode(GL_TEXTURE);

        // 恢复原始矩阵模式
        glMatrixMode(matrixMode);

        // Enable states
        setState(GL_DEPTH_TEST, depthTest);
        setState(GL_BLEND, isBlend);
        setState(GL_CULL_FACE, cullFace);
        setState(GL_STENCIL_TEST, stencilTest);
        setState(GL_SCISSOR_TEST, scissorTest);
        setState(GL_MULTISAMPLE, multisample);
        setState(GL_POLYGON_SMOOTH, polygonSmooth);
        setState(GL_LINE_SMOOTH, lineSmooth);
        setState(GL_PROGRAM_POINT_SIZE, programPointSize);
        setState(GL_VERTEX_ARRAY, vertexArray);
        setState(GL_COLOR_ARRAY, colorArray);
        setState(GL_NORMAL_ARRAY, normalArray);

        // 混合
        if (isBlend) {
            glBlendFuncSeparate(blendSrcRGB, blendDstRGB, blendSrcAlpha, blendDstAlpha);
        }


        // Depth states
        glDepthMask(depthMask);
        glDepthFunc(depthFunc);

        // Stencil states
        glStencilFuncSeparate(GL_FRONT, stencilFuncFront, stencilRefFront, stencilMaskFront);
        glStencilFuncSeparate(GL_BACK, stencilFuncBack, stencilRefBack, stencilMaskBack);
        glStencilOpSeparate(GL_FRONT, stencilFailFront, zfailFront, zpassFront);
        glStencilOpSeparate(GL_BACK, stencilFailBack, zfailBack, zpassBack);

        // Line/Point
        glLineWidth(lineWidth);
        glPointSize(pointSize);

        // Viewport/Scissor
        glViewport(viewportX, viewportY, viewportW, viewportH);
        glScissor(scissorX, scissorY, scissorW, scissorH);

        // Bindings
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, arrayBuffer);
        glBindBuffer(GL_ELEMENT_ARRAY_BUFFER, elementArrayBuffer);

        // Framebuffers
        glBindFramebuffer(GL_READ_FRAMEBUFFER, readFramebuffer);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFramebuffer);
    }

    private void setState(int cap, boolean enabled) {
        if (enabled) {
            glEnable(cap);
        } else {
            glDisable(cap);
        }
    }
}
