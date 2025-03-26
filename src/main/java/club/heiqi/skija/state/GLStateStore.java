package club.heiqi.skija.state;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.*;
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
    private boolean depthTest, blend, cullFace, stencilTest, scissorTest;
    private boolean multisample, polygonSmooth, lineSmooth, programPointSize;
    private boolean vertexArray, colorArray, normalArray;

    // 混合
    private int blendSrcRGB, blendDstRGB, blendSrcAlpha, blendDstAlpha;
    private int blendEquationRGB, blendEquationAlpha;
    private FloatBuffer blendColor = BufferUtils.createFloatBuffer(4);

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

    // 纹理（存储纹理ID及参数）
    /*private final Map<Integer, Map<Integer, Map<Integer, Integer>>> textureBindings = new HashMap<>();*/

    // 颜色
    private final boolean[] colorMask = new boolean[4];
    private boolean logicOpEnabled;
    private int logicOpMode;
    /*private final FloatBuffer currentColor = BufferUtils.createFloatBuffer(4);*/
    // 法线可选 [可选项-未完成]

    // 多边形
    private int polygonModeFront, polygonModeBack;
    private boolean polygonOffsetFill, polygonOffsetLine, polygonOffsetPoint;
    private float polygonOffsetFactor, polygonOffsetUnits;
    private int cullFaceMode;


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
        blend = glIsEnabled(GL_BLEND);
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
        blendSrcRGB = glGetInteger(GL_BLEND_SRC_RGB);
        blendDstRGB = glGetInteger(GL_BLEND_DST_RGB);
        blendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA);
        blendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        blendEquationRGB = glGetInteger(GL_BLEND_EQUATION_RGB);
        blendEquationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
        glGetFloat(GL_BLEND_COLOR, blendColor);

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

        // Polygon mode
        IntBuffer buffer = BufferUtils.createIntBuffer(2);
        glGetInteger(GL_POLYGON_MODE, buffer);
        polygonModeFront = buffer.get(0);
        polygonModeBack = buffer.get(1);

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

        // 纹理
        /*int maxUnits = glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
        for (int i = 0; i < maxUnits; i++) {
            glActiveTexture(GL_TEXTURE0 + i);
            Map<Integer, Map<Integer, Integer>> targets = new HashMap<>();

            // 处理GL_TEXTURE_2D
            int tex2D = glGetInteger(GL_TEXTURE_BINDING_2D);
            Map<Integer, Integer> tex2DParams = new HashMap<>();
            tex2DParams.put(GL_TEXTURE_BINDING_2D, tex2D);
            if (tex2D != 0) {
                // 备份环绕参数
                IntBuffer param = BufferUtils.createIntBuffer(1);
                glGetTexParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, param);
                tex2DParams.put(GL_TEXTURE_WRAP_S, param.get(0)); param.clear();
                glGetTexParameter(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, param);
                tex2DParams.put(GL_TEXTURE_WRAP_T, param.get(0));
            }
            targets.put(GL_TEXTURE_2D, tex2DParams);
            textureBindings.put(i, targets);
        }*/

        // 颜色
        IntBuffer colorMaskBuffer = BufferUtils.createIntBuffer(4);
        glGetInteger(GL_COLOR_WRITEMASK, colorMaskBuffer);
        for (int i = 0; i < 4; i++) {
            colorMask[i] = colorMaskBuffer.get(i) == GL_TRUE;
        }
        logicOpEnabled = glIsEnabled(GL_COLOR_LOGIC_OP);
        if (logicOpEnabled) logicOpMode = glGetInteger(GL_LOGIC_OP_MODE);
        /*glGetFloat(GL_CURRENT_COLOR, currentColor);*/

        // 多边形
        polygonOffsetFill = glIsEnabled(GL_POLYGON_OFFSET_FILL);
        polygonOffsetLine = glIsEnabled(GL_POLYGON_OFFSET_LINE);
        polygonOffsetPoint = glIsEnabled(GL_POLYGON_OFFSET_POINT);
        polygonOffsetFactor = glGetFloat(GL_POLYGON_OFFSET_FACTOR);
        polygonOffsetUnits = glGetFloat(GL_POLYGON_OFFSET_UNITS);
        cullFaceMode = glGetInteger(GL_CULL_FACE_MODE);

        // 尽可能多的保存状态栈
        glPushClientAttrib(GL_ALL_CLIENT_ATTRIB_BITS);
    }

    public void restore() {
        // 着色器
        glUseProgram(currentProgram);
        // 弹出保存的栈
        glPopClientAttrib();
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
        setState(GL_BLEND, blend);
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
        glBlendFuncSeparate(blendSrcRGB, blendDstRGB, blendSrcAlpha, blendDstAlpha);
        glBlendEquationSeparate(blendEquationRGB, blendEquationAlpha);


        // Depth states
        glDepthMask(depthMask);
        glDepthFunc(depthFunc);

        // Stencil states
        glStencilFuncSeparate(GL_FRONT, stencilFuncFront, stencilRefFront, stencilMaskFront);
        glStencilFuncSeparate(GL_BACK, stencilFuncBack, stencilRefBack, stencilMaskBack);
        glStencilOpSeparate(GL_FRONT, stencilFailFront, zfailFront, zpassFront);
        glStencilOpSeparate(GL_BACK, stencilFailBack, zfailBack, zpassBack);

        // Polygon mode
        glPolygonMode(GL_FRONT, polygonModeFront);
        glPolygonMode(GL_BACK, polygonModeBack);

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

        // Texture units
        /*int maxUnits = glGetInteger(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS);
        for (int i = 0; i < maxUnits; i++) {
            if (!textureBindings.containsKey(i)) continue;
            glActiveTexture(GL_TEXTURE0 + i);
            Map<Integer, Map<Integer, Integer>> targets = textureBindings.get(i);
            // 恢复GL_TEXTURE_2D
            Map<Integer, Integer> tex2DParams = targets.get(GL_TEXTURE_2D);
            if (tex2DParams != null) {
                int texID = tex2DParams.get(GL_TEXTURE_BINDING_2D);
                if (texID != 0) {
                    glBindTexture(GL_TEXTURE_2D, texID);
                    // 设置环绕参数
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S,
                        tex2DParams.getOrDefault(GL_TEXTURE_WRAP_S, GL_REPEAT));
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T,
                        tex2DParams.getOrDefault(GL_TEXTURE_WRAP_T, GL_REPEAT));
                }
            }
        }
        glActiveTexture(GL_TEXTURE0);*/

        // 颜色
        glBlendColor(blendColor.get(0), blendColor.get(1), blendColor.get(2), blendColor.get(3));
        glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
        if (logicOpEnabled) {
            glEnable(GL_COLOR_LOGIC_OP);
            glLogicOp(logicOpMode);
        } else {
            glDisable(GL_COLOR_LOGIC_OP);
        }
        /*glColor4f(currentColor.get(0), currentColor.get(1), currentColor.get(2), currentColor.get(3));*/

        // 多边形
        setState(GL_POLYGON_OFFSET_FILL, polygonOffsetFill);
        setState(GL_POLYGON_OFFSET_LINE, polygonOffsetLine);
        setState(GL_POLYGON_OFFSET_POINT, polygonOffsetPoint);
        glPolygonOffset(polygonOffsetFactor, polygonOffsetUnits);
        glCullFace(cullFaceMode);
    }

    private void setState(int cap, boolean enabled) {
        if (enabled) {
            glEnable(cap);
        } else {
            glDisable(cap);
        }
    }
}
