package club.heiqi.uilib.font.shader;

import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import club.heiqi.uilib.gl.shader.ShaderProgramSupport;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * 字体着色器程序。
 */
public class FontShaderProgram {

    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final Map<String, Integer> uniformLocations = new LinkedHashMap<String, Integer>();
    private final Map<String, Integer> attributeLocations = new LinkedHashMap<String, Integer>();
    private final Set<String> missingUniforms = new HashSet<String>();
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);
    private String vertexShaderPath = "shader/fontV.vert";
    private String fragmentShaderPath = "shader/fontF.frag";
    private int shaderProgramId;

    /**
     * 初始化着色器程序。
     */
    public void initialize() {
        if (initialized.compareAndSet(false, true)) {
            try {
                shaderProgramId = GL20.glCreateProgram();
                uniformLocations.clear();
                attributeLocations.clear();
                attributeLocations.put("pos", Integer.valueOf(0));
                attributeLocations.put("tex", Integer.valueOf(1));
                attributeLocations.put("color", Integer.valueOf(2));
                attributeLocations.put("v_uvBounds", Integer.valueOf(3));
                attributeLocations.put("v_glyphFlags", Integer.valueOf(4));
                missingUniforms.clear();
                loadProgram();
            } catch (RuntimeException exception) {
                close();
                initialized.set(false);
                throw exception;
            }
        }
    }

    /**
     * 重新装载着色器状态。
     */
    public void reload() {
        if (!initialized.get()) {
            initialize();
            return;
        }
        close();
        initialized.set(false);
        initialize();
    }

    /**
     * 判断是否已初始化。
     *
     * @return 是否已初始化
     */
    public boolean isInitialized() {
        return initialized.get();
    }

    /**
     * 获取只读 uniform 表。
     *
     * @return uniform 表
     */
    public Map<String, Integer> getUniformLocations() {
        return Collections.unmodifiableMap(uniformLocations);
    }

    /**
     * 获取只读 attribute 表。
     *
     * @return attribute 表
     */
    public Map<String, Integer> getAttributeLocations() {
        return Collections.unmodifiableMap(attributeLocations);
    }

    /**
     * 获取顶点着色器路径。
     *
     * @return 顶点着色器路径
     */
    public String getVertexShaderPath() {
        return vertexShaderPath;
    }

    /**
     * 获取片元着色器路径。
     *
     * @return 片元着色器路径
     */
    public String getFragmentShaderPath() {
        return fragmentShaderPath;
    }

    /**
     * 绑定着色器程序。
     */
    public void bind() {
        GL20.glUseProgram(shaderProgramId);
    }

    /**
     * 解绑着色器程序。
     */
    public void unbind() {
        GL20.glUseProgram(0);
    }

    /**
     * 设置矩阵 uniform。
     *
     * @param name uniform 名称
     * @param value 矩阵值
     */
    public void setUniformM4f(String name, Matrix4f value) {
        int location = getUniformLocation(name);
        if (location == -1) {
            return;
        }
        matrixBuffer.clear();
        matrixBuffer.put(value.get(new float[16]));
        matrixBuffer.flip();
        GL20.glUniformMatrix4(location, false, matrixBuffer);
    }

    /**
     * 设置二维向量 uniform。
     *
     * @param name uniform 名称
     * @param value 向量值
     */
    public void setUniformVec2(String name, Vector2f value) {
        int location = getUniformLocation(name);
        if (location != -1) {
            GL20.glUniform2f(location, value.x, value.y);
        }
    }

    /**
     * 设置浮点 uniform。
     *
     * @param name uniform 名称
     * @param value 数值
     */
    public void setUniformF(String name, float value) {
        int location = getUniformLocation(name);
        if (location != -1) {
            GL20.glUniform1f(location, value);
        }
    }

    /**
     * 设置整型 uniform。
     *
     * @param name uniform 名称
     * @param value 数值
     */
    public void setUniformI(String name, int value) {
        int location = getUniformLocation(name);
        if (location != -1) {
            GL20.glUniform1i(location, value);
        }
    }

    /**
     * 释放着色器资源。
     */
    public void close() {
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
                ShaderProgramSupport.readText(getClass(), vertexShaderPath, "读取字体着色器失败: "),
                GL20.GL_VERTEX_SHADER,
                "字体着色器编译失败: ");
        int fragmentShaderId = ShaderProgramSupport.compileShader(
                ShaderProgramSupport.readText(getClass(), fragmentShaderPath, "读取字体着色器失败: "),
                GL20.GL_FRAGMENT_SHADER,
                "字体着色器编译失败: ");

        GL20.glAttachShader(shaderProgramId, vertexShaderId);
        GL20.glAttachShader(shaderProgramId, fragmentShaderId);
        GL20.glBindAttribLocation(shaderProgramId, 0, "pos");
        GL20.glBindAttribLocation(shaderProgramId, 1, "tex");
        GL20.glBindAttribLocation(shaderProgramId, 2, "color");
        GL20.glBindAttribLocation(shaderProgramId, 3, "v_uvBounds");
        GL20.glBindAttribLocation(shaderProgramId, 4, "v_glyphFlags");
        ShaderProgramSupport.linkAndValidateProgram(shaderProgramId, "字体着色器链接失败: ", "字体着色器验证失败: ");

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
