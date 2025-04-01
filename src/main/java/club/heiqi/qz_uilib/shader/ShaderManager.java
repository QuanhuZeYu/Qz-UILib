package club.heiqi.qz_uilib.shader;

import club.heiqi.qz_uilib.jarFile.Reader;
import club.heiqi.qz_uilib.skija.state.SkiaStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL20.*;

public class ShaderManager {
    public static Logger LOG = LogManager.getLogger();
    public static Map<String, ShaderManager> GLOBAL = new HashMap<>();

    public final int shaderID;
    public final String shaderName;
    public int cachedCurrentShader;

    public ShaderManager(String shaderName, String vertexPath, String fragmentPath) {
        this.shaderName = shaderName;
        this.shaderID = createShaderProgram(vertexPath, fragmentPath);

        if (shaderID != 0) {
            GLOBAL.put(shaderName, this);
            LOG.info("✅ 成功创建着色器程序: {}", shaderName);
        } else {
            LOG.error("❌ 着色器初始化失败: {}", shaderName);
        }
    }

    //=============== 生命周期管理 ================//
    public void bind() {
        cachedCurrentShader = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        glUseProgram(shaderID);
    }

    public void unbind() {
        GL20.glUseProgram(cachedCurrentShader);
    }

    public void cleanUp() {
        GLOBAL.remove(shaderName);
        if (shaderID != 0) {
            GL20.glDeleteProgram(shaderID);
            LOG.info("🗑️ 已清理着色器程序: {}", shaderName);
        }
    }

    //=============== 核心编译逻辑 ================//
    public int createShaderProgram(String vertPath, String fragPath) {
        final int vertexID = compileShader(vertPath, GL_VERTEX_SHADER);
        if (vertexID == 0) return 0;

        final int fragmentID = compileShader(fragPath, GL_FRAGMENT_SHADER);
        if (fragmentID == 0) {
            GL20.glDeleteShader(vertexID);
            return 0;
        }

        return linkProgram(vertexID, fragmentID);
    }

    public int compileShader(String filePath, int type) {
        final String code = Reader.readFile(filePath);
        if (code.isEmpty()) {
            LOG.error("📁 着色器文件不存在: {}", filePath);
            return 0;
        }

        final int shaderID = GL20.glCreateShader(type);
        GL20.glShaderSource(shaderID, code);
        GL20.glCompileShader(shaderID);

        if (GL20.glGetShaderi(shaderID, GL_COMPILE_STATUS) == GL_FALSE) {
            final String log = GL20.glGetShaderInfoLog(shaderID, 2048);
            LOG.error("🔥 {} 着色器编译失败: {}", getShaderTypeName(type), filePath);
            LOG.error("🚧 错误信息:\n{}", log);
            GL20.glDeleteShader(shaderID);
            return 0;
        }
        return shaderID;
    }

    public int linkProgram(int vertexID, int fragmentID) {
        final int programID = GL20.glCreateProgram();
        GL20.glAttachShader(programID, vertexID);
        GL20.glAttachShader(programID, fragmentID);
        GL20.glLinkProgram(programID);

        // 清理着色器对象
        GL20.glDetachShader(programID, vertexID);
        GL20.glDetachShader(programID, fragmentID);
        GL20.glDeleteShader(vertexID);
        GL20.glDeleteShader(fragmentID);

        // 检查链接状态
        if (GL20.glGetProgrami(programID, GL_LINK_STATUS) == GL_FALSE) {
            final String log = GL20.glGetProgramInfoLog(programID, 512);
            LOG.error("🔗 程序链接失败: {}", shaderName);
            LOG.error("🚧 错误信息:\n{}", log);
            GL20.glDeleteProgram(programID);
            return 0;
        }
        return programID;
    }

    //=============== 工具方法 ================//
    public String getShaderTypeName(int type) {
        return switch (type) {
            case GL_VERTEX_SHADER -> "顶点";
            case GL_FRAGMENT_SHADER -> "片段";
            default -> "未知";
        };
    }

    public int getUniformLocation(String name) {
        return GL20.glGetUniformLocation(shaderID, name);
    }

    public int getAttributeLocation(String name) {
        return GL20.glGetAttribLocation(shaderID, name);
    }

    //=============== Getter ================//
    public int getShaderID() {
        return shaderID;
    }

    public String getShaderName() {
        return shaderName;
    }
}
