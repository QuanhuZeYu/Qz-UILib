package club.heiqi.uilib.ui.render;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import club.heiqi.uilib.gl.shader.ShaderProgramSupport;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * UI 磨玻璃专用着色器程序。
 */
final class UiBackdropShaderProgram {

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Map<String, Integer> uniformLocations = new LinkedHashMap<String, Integer>();
    private final Set<String> missingUniforms = new HashSet<String>();
    private boolean unavailable;
    private String lastFailureMessage = "";
    private int shaderProgramId;

    /**
     * 尝试确保着色器已初始化。
     *
     * @return 是否可用
     */
    boolean ensureInitialized() {
        if (unavailable) {
            return false;
        }
        if (initialized.get()) {
            return shaderProgramId != 0;
        }
        if (!initialized.compareAndSet(false, true)) {
            return shaderProgramId != 0;
        }

        try {
            shaderProgramId = GL20.glCreateProgram();
            uniformLocations.clear();
            missingUniforms.clear();
            lastFailureMessage = "";
            loadProgram();
            return true;
        } catch (RuntimeException exception) {
            unavailable = true;
            lastFailureMessage = exception.getMessage() == null ? exception.getClass().getSimpleName()
                    : exception.getMessage();
            closeProgram();
            return false;
        }
    }

    /**
     * 返回最近一次 shader 初始化失败原因。
     *
     * @return 失败原因；没有失败时为空字符串
     */
    String getLastFailureMessage() {
        return lastFailureMessage;
    }

    /**
     * 绑定着色器程序。
     */
    void bind() {
        GL20.glUseProgram(shaderProgramId);
    }

    /**
     * 解绑着色器程序。
     */
    void unbind() {
        GL20.glUseProgram(0);
    }

    /**
     * 设置整型 uniform。
     *
     * @param name uniform 名称
     * @param value 数值
     */
    void setUniformI(String name, int value) {
        int location = getUniformLocation(name);
        if (location != -1) {
            GL20.glUniform1i(location, value);
        }
    }

    /**
     * 设置浮点 uniform。
     *
     * @param name uniform 名称
     * @param value 数值
     */
    void setUniformF(String name, float value) {
        int location = getUniformLocation(name);
        if (location != -1) {
            GL20.glUniform1f(location, value);
        }
    }

    /**
     * 设置二维向量 uniform。
     *
     * @param name uniform 名称
     * @param x X 分量
     * @param y Y 分量
     */
    void setUniform2f(String name, float x, float y) {
        int location = getUniformLocation(name);
        if (location != -1) {
            GL20.glUniform2f(location, x, y);
        }
    }

    private void closeProgram() {
        unbind();
        if (shaderProgramId != 0) {
            GL20.glDeleteProgram(shaderProgramId);
            shaderProgramId = 0;
        }
        uniformLocations.clear();
        missingUniforms.clear();
    }

    private void loadProgram() {
        int vertexShaderId = ShaderProgramSupport.compileShader(
                ShaderProgramSupport.readText(getClass(), "shader/uiBackdropV.vert", "读取 UI backdrop 着色器失败: "),
                GL20.GL_VERTEX_SHADER,
                "UI backdrop 着色器编译失败: ");
        int fragmentShaderId = ShaderProgramSupport.compileShader(
                ShaderProgramSupport.readText(getClass(), "shader/uiBackdropF.frag", "读取 UI backdrop 着色器失败: "),
                GL20.GL_FRAGMENT_SHADER,
                "UI backdrop 着色器编译失败: ");

        GL20.glAttachShader(shaderProgramId, vertexShaderId);
        GL20.glAttachShader(shaderProgramId, fragmentShaderId);
        ShaderProgramSupport.linkAndValidateProgram(shaderProgramId, "UI backdrop 着色器链接失败: ",
                "UI backdrop 着色器验证失败: ");

        GL20.glDeleteShader(vertexShaderId);
        GL20.glDeleteShader(fragmentShaderId);
    }

    private int getUniformLocation(String name) {
        if (uniformLocations.containsKey(name)) {
            return uniformLocations.get(name).intValue();
        }
        if (missingUniforms.contains(name)) {
            return -1;
        }

        int location = GL20.glGetUniformLocation(shaderProgramId, name);
        if (location == -1) {
            missingUniforms.add(name);
            return -1;
        }
        uniformLocations.put(name, Integer.valueOf(location));
        return location;
    }

}
