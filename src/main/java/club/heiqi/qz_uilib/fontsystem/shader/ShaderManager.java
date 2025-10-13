package club.heiqi.qz_uilib.fontsystem.shader;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ShaderManager {
    public Logger LOG = LogManager.getLogger();
    public int shaderProgramID;
    public int vertexShaderID;
    public int fragmentShaderID;
    public int geometryShaderID;

    public Map<String, Integer> uniforms;
    public Map<String, Integer> attributes;
    public Map<String, Integer> attribLocations;
    /** 用于记录已经警告过的不存在的 uniform 变量，避免重复警告 */
    private final Set<String> missingUniforms;

    /** 用于复用矩阵缓冲区 */
    private final FloatBuffer matrixBuffer = BufferUtils.createFloatBuffer(16);

    /** 静态变量记录当前绑定的着色器 */
    private static int currentShaderID = 0;

    public ShaderManager() {
        uniforms    = new HashMap<>();
        attributes  = new HashMap<>();
        attribLocations = new HashMap<>();
        missingUniforms = new HashSet<>(); // 初始化
        initShaderProgram();
    }

    public void initShaderProgram() {
        shaderProgramID = GL20.glCreateProgram();
    }

    public Runnable setLocation = () -> {};
    public ShaderManager setCustomLocation(Runnable setLocation) {
        this.setLocation = setLocation;
        return this;
    }
    public ShaderManager loadShader(String vertexShaderSource, String fragmentShaderSource, @Nullable String geometrySource) {
        // 清除旧缓存
        uniforms.clear();
        attributes.clear();
        attribLocations.clear();
        missingUniforms.clear(); // 在新加载着色器时也清除不存在的 uniform 记录

        LOG.info("🚀开始加载着色器⚙");
        vertexShaderID = createShader(vertexShaderSource, GL20.GL_VERTEX_SHADER);
        fragmentShaderID = createShader(fragmentShaderSource, GL20.GL_FRAGMENT_SHADER);
        if (geometrySource != null)
            geometryShaderID = createShader(geometrySource, GL32.GL_GEOMETRY_SHADER);

        // 附加着色器到着色器程序
        GL20.glAttachShader(shaderProgramID, vertexShaderID);
        GL20.glAttachShader(shaderProgramID, fragmentShaderID);
        if (geometrySource != null)
            GL20.glAttachShader(shaderProgramID, geometryShaderID);

        // 自定义位置
        setLocation.run();

        // 链接着色器程序
        linkAndValidate();

        // 清理
        GL20.glDeleteShader(vertexShaderID);
        GL20.glDeleteShader(fragmentShaderID);
        if (geometrySource != null)
            GL20.glDeleteShader(geometryShaderID);

        // 提示创建成功
        LOG.info("🚀着色器创建成功⚙");

        return this;
    }

    public ShaderManager loadFromJar(String vertexPath, String fragmentPath, @Nullable String geometryPath) {
        String vertexText = readJar(vertexPath);
        String fragmentText = readJar(fragmentPath);
        String geometryText = null;
        if (geometryPath != null) geometryText = readJar(geometryPath);

        return loadShader(vertexText, fragmentText, geometryText);
    }

    public String readJar(String path) {
        if (!path.startsWith("/")) path = "/" + path;
        StringBuilder content = new StringBuilder();

        try (InputStream is = this.getClass().getResourceAsStream(path);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8)
             )
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append(System.lineSeparator());
            }
        } catch (IOException | NullPointerException e) {
            LOG.error(("读取文件失败:" + path + e));
            return "";
        }
        return content.toString();
    }

    public int createShader(String source, int shaderType) {
        int shaderID = GL20.glCreateShader(shaderType);
        if (shaderID == 0) {
            throw new RuntimeException("创建着色器失败");
        }

        // 编译着色器
        GL20.glShaderSource(shaderID, source);
        GL20.glCompileShader(shaderID);

        // 添加详细的错误日志
        if (GL20.glGetShaderi(shaderID, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shaderID, 4096);
            throw new RuntimeException("着色器编译失败:\n" + log);
        }

        return shaderID;
    }

    public void linkAndValidate() {
        GL20.glLinkProgram(shaderProgramID);
        if (GL20.glGetProgrami(shaderProgramID, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new RuntimeException("着色器程序链接错误: " + GL20.glGetProgramInfoLog(shaderProgramID, 4096));
        }
        GL20.glValidateProgram(shaderProgramID);
        if (GL20.glGetProgrami(shaderProgramID, GL20.GL_VALIDATE_STATUS) == GL11.GL_FALSE) {
            throw new RuntimeException("着色器程序验证错误: " + GL20.glGetProgramInfoLog(shaderProgramID, 4096));
        }
    }

    public int getUniformLocation(String name) {
        // 1. 先从缓存中获取 (存在则直接返回)
        if (uniforms.containsKey(name)) {
            return uniforms.get(name);
        }

        // 2. 检查是否是已知的不存在的 uniform
        if (missingUniforms.contains(name)) {
            return -1; // 已知不存在，直接返回 -1
        }

        // 3. 缓存中不存在，向 OpenGL 请求 location
        int location = GL20.glGetUniformLocation(shaderProgramID, name);

        if (location == -1) {
            // 4. uniform 不存在
            LOG.warn("Uniform 【{}】不存在或未被使用，已忽略该变量的设置。请检查着色器代码。", name);
            // 5. 记录到 missingUniforms，避免下次再次警告
            missingUniforms.add(name);
            return -1;
        }

        // 6. uniform 存在，记录到缓存
        uniforms.put(name, location);
        return location;
    }

    public void setUniformI(String name, int value) {
        int location = getUniformLocation(name);
        if (location != -1) {
            GL20.glUniform1i(location, value);
        }
    }

    public void setUniformF(String name, float value) {
        int location = getUniformLocation(name);
        if (location != -1) {
            GL20.glUniform1f(location, value);
        }
    }

    public void setUniformVec2(String name, Vector2f value) {
        int location = getUniformLocation(name);
        if (location != -1) {
            GL20.glUniform2f(location, value.x, value.y);
        }
    }

    public void setUniformVec3(String name, Vector3f value) {
        int location = getUniformLocation(name);
        if (location != -1) {
            GL20.glUniform3f(location, value.x, value.y, value.z);
        }
    }

    public void setUniformVec4(String name, Vector4f value) {
        int location = getUniformLocation(name);
        if (location != -1) {
            GL20.glUniform4f(location, value.x, value.y, value.z, value.w);
        }
    }

    public void setUniformM4f(String name, Matrix4f value) {
        int location = getUniformLocation(name);
        if (location != -1) {
            matrixBuffer.clear();
            matrixBuffer.put(value.get(new float[16]));
            matrixBuffer.flip();
            GL20.glUniformMatrix4(location, false, matrixBuffer);
        }
    }

    public int getAttribLocation(String name) {
        Integer result = attribLocations.get(name);
        if (result != null) {
            return result;
        }
        else {
            int location = GL20.glGetAttribLocation(shaderProgramID, name);
            attribLocations.put(name, location);
            return location;
        }
    }

    public void bind() {
        // 避免不必要的状态切换
        if (shaderProgramID == currentShaderID) {
            return;
        }

        GL20.glUseProgram(shaderProgramID);
        currentShaderID = shaderProgramID;
    }

    public void unbind() {
        GL20.glUseProgram(0);
        currentShaderID = 0;
    }

    public void destroy() {
        unbind();
        if (shaderProgramID != 0) {
            GL20.glDeleteProgram(shaderProgramID);
            shaderProgramID = 0;
        }
        uniforms.clear();
        attributes.clear();
        attribLocations.clear();
        missingUniforms.clear();
    }
}
