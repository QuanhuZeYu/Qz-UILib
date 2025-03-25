package club.heiqi.skija.state;

import java.util.HashMap;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE;
import static org.lwjgl.opengl.GL13.glActiveTexture;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.GL_VERTEX_ARRAY_BINDING;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL33.GL_SAMPLER_BINDING;
import static org.lwjgl.opengl.GL33.glBindSampler;

public class GLStateUtil {
    // 像素存储参数备份（用于glPixelStorei相关设置）
    private final HashMap<PixelStoreParam, Integer> pixelStores = new HashMap<>();

    // 纹理相关状态备份
    private int lastActiveTexture = 0;    // 最后激活的纹理单元（GL_ACTIVE_TEXTURE）
    private int lastProgram = 0;          // 当前使用的着色器程序（GL_CURRENT_PROGRAM）
    private int lastSampler = 0;          // 绑定的采样器对象（GL_SAMPLER_BINDING）
    private int lastVertexArray = 0;      // VAO绑定状态（GL_VERTEX_ARRAY_BINDING）
    private int lastArrayBuffer = 0;      // VBO绑定状态（GL_ARRAY_BUFFER_BINDING）

    // 混合状态备份
    private int lastBlendSrcRgb = 0;      // RGB混合源因子（GL_BLEND_SRC_RGB）
    private int lastBlendDstRgb = 0;      // RGB混合目标因子（GL_BLEND_DST_RGB）
    private int lastBlendSrcAlpha = 0;    // Alpha混合源因子（GL_BLEND_SRC_ALPHA）
    private int lastBlendDstAlpha = 0;    // Alpha混合目标因子（GL_BLEND_DST_ALPHA）
    private int lastBlendEquationRgb = 0; // RGB混合方程（GL_BLEND_EQUATION_RGB）
    private int lastBlendEquationAlpha = 0; // Alpha混合方程（GL_BLEND_EQUATION_ALPHA）

    /**
     * 备份当前OpenGL状态
     * <p>
     * 执行流程：
     * 1. 保存客户端和服务端的属性堆栈（深度测试、面剔除等状态）
     * 2. 备份纹理相关状态（激活纹理单元、着色器程序等）
     * 3. 备份所有像素存储参数（像素传输设置）
     * 4. 备份混合状态参数（混合因子和混合方程）
     */
    public void backupCurrentState() {
        // 保存属性堆栈（最大范围）
        glPushClientAttrib(GL_ALL_CLIENT_ATTRIB_BITS);
        glPushAttrib(GL_ALL_ATTRIB_BITS);

        // 备份纹理相关状态
        lastActiveTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        lastProgram = glGetInteger(GL_CURRENT_PROGRAM);
        lastSampler = glGetInteger(GL_SAMPLER_BINDING);
        lastArrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        lastVertexArray = glGetInteger(GL_VERTEX_ARRAY_BINDING);

        // 备份所有像素存储参数
        for (PixelStoreParam parameter : PixelStoreParam.values()) {
            pixelStores.put(parameter, glGetInteger(parameter.getValue()));
        }

        // 备份混合状态
        lastBlendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB);
        lastBlendDstRgb = glGetInteger(GL_BLEND_DST_RGB);
        lastBlendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA);
        lastBlendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        lastBlendEquationRgb = glGetInteger(GL_BLEND_EQUATION_RGB);
        lastBlendEquationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
    }

    /**
     * 恢复之前备份的OpenGL状态
     * <p>
     * 执行流程：
     * 1. 恢复属性堆栈
     * 2. 恢复纹理相关状态
     * 3. 恢复混合状态
     * 4. 恢复像素存储参数
     */
    public void restorePreviousState() {
        // 恢复属性堆栈
        glPopAttrib();
        glPopClientAttrib();

        // 恢复着色器程序
        glUseProgram(lastProgram);
        // 重新绑定采样器对象到纹理单元0
        glBindSampler(0, lastSampler);
        // 恢复之前激活的纹理单元
        glActiveTexture(lastActiveTexture);
        // 重新绑定顶点数组和缓冲
        glBindVertexArray(lastVertexArray);
        glBindBuffer(GL_ARRAY_BUFFER, lastArrayBuffer);

        // 恢复混合方程和混合函数
        glBlendEquationSeparate(lastBlendEquationRgb, lastBlendEquationAlpha);
        glBlendFuncSeparate(
            lastBlendSrcRgb, lastBlendDstRgb,
            lastBlendSrcAlpha, lastBlendDstAlpha
        );

        // 恢复所有像素存储参数
        for (PixelStoreParam parameter : PixelStoreParam.values()) {
            glPixelStorei(parameter.getValue(), pixelStores.get(parameter));
        }
    }
}
