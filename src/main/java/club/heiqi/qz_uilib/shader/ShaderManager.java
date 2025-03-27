package club.heiqi.qz_uilib.shader;

import club.heiqi.qz_uilib.jarFile.Reader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.GL_FALSE;
import static org.lwjgl.opengl.GL20.*;

public class ShaderManager {
    public static Logger LOG = LogManager.getLogger();
    public static Map<String, ShaderManager> shaderManagers = new HashMap<>();
    public int shaderID;

    public ShaderManager(String name, String vertexShader, String fragmentShader) {
        loadShaderProgram(vertexShader, fragmentShader);
        shaderManagers.put(name, this);
    }

    /**
     * 从JAR内加载shader文件
     * @param vertexShaderPath
     * @param fragmentShaderPath
     */
    public void loadShaderProgram(String vertexShaderPath, String fragmentShaderPath) {
        int vertexShaderID = compileShader(vertexShaderPath, GL_VERTEX_SHADER);
        int fragmentShaderID = compileShader(fragmentShaderPath, GL_FRAGMENT_SHADER);
        createShaderProgram(vertexShaderID, fragmentShaderID);
    }

    public int compileShader(String shaderProgramPath, int shaderType) {
        String shaderCode = Reader.readFile(shaderProgramPath);
        LOG.info("Compiling shader code {}: {}", shaderType, shaderCode);
        int shaderID = GL20.glCreateShader(shaderType);
        if (shaderID == 0) {
            LOG.error("{} 创建shader失败", shaderType);
        }
        GL20.glShaderSource(shaderID, shaderCode);
        GL20.glCompileShader(shaderID);
        if (glGetShaderi(shaderID, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            LOG.error("着色器 {} 编译失败", shaderProgramPath);
            GL20.glDeleteShader(shaderID);
            return -1;
        }
        return shaderID;
    }

    public void createShaderProgram(int vertexShaderID, int fragmentShaderID) {
        int program = glCreateProgram();
        if (program == 0) return;
        glAttachShader(program, vertexShaderID);
        glAttachShader(program, fragmentShaderID);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            LOG.error("着色器链接失败");
            glDeleteProgram(program);
            return;
        }
        glDeleteShader(program);
        glDeleteShader(fragmentShaderID);
        this.shaderID = program;
    }
}
