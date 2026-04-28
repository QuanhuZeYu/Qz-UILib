package club.heiqi.uilib.ui.render;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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
            loadProgram();
            return true;
        } catch (RuntimeException exception) {
            unavailable = true;
            closeProgram();
            return false;
        }
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
        int vertexShaderId = compileShader(readText("shader/uiBackdropV.vert"), GL20.GL_VERTEX_SHADER);
        int fragmentShaderId = compileShader(readText("shader/uiBackdropF.frag"), GL20.GL_FRAGMENT_SHADER);

        GL20.glAttachShader(shaderProgramId, vertexShaderId);
        GL20.glAttachShader(shaderProgramId, fragmentShaderId);
        GL20.glLinkProgram(shaderProgramId);
        if (GL20.glGetProgrami(shaderProgramId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException("UI backdrop 着色器链接失败: "
                    + GL20.glGetProgramInfoLog(shaderProgramId, 4096));
        }

        GL20.glValidateProgram(shaderProgramId);
        if (GL20.glGetProgrami(shaderProgramId, GL20.GL_VALIDATE_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException("UI backdrop 着色器验证失败: "
                    + GL20.glGetProgramInfoLog(shaderProgramId, 4096));
        }

        GL20.glDeleteShader(vertexShaderId);
        GL20.glDeleteShader(fragmentShaderId);
    }

    private int compileShader(String source, int shaderType) {
        int shaderId = GL20.glCreateShader(shaderType);
        GL20.glShaderSource(shaderId, source);
        GL20.glCompileShader(shaderId);
        if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException("UI backdrop 着色器编译失败: " + GL20.glGetShaderInfoLog(shaderId, 4096));
        }
        return shaderId;
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

    private String readText(String resourcePath) {
        String resolvedPath = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
        StringBuilder builder = new StringBuilder();
        try (InputStream inputStream = getClass().getResourceAsStream(resolvedPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append(System.lineSeparator());
            }
        } catch (IOException | NullPointerException exception) {
            throw new IllegalStateException("读取 UI backdrop 着色器失败: " + resolvedPath, exception);
        }
        return builder.toString();
    }
}
