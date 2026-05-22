package club.heiqi.uilib.gl.shader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

/**
 * 着色器程序的共享底层工具。
 */
public final class ShaderProgramSupport {

    private ShaderProgramSupport() {}

    /**
     * 读取类路径下的文本资源。
     *
     * @param owner 资源所属类型
     * @param resourcePath 资源路径
     * @param errorPrefix 失败提示前缀
     * @return 文本内容
     */
    public static String readText(Class<?> owner, String resourcePath, String errorPrefix) {
        String resolvedPath = resourcePath.startsWith("/") ? resourcePath : "/" + resourcePath;
        StringBuilder builder = new StringBuilder();
        try (InputStream inputStream = owner.getResourceAsStream(resolvedPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append(System.lineSeparator());
            }
        } catch (IOException | NullPointerException exception) {
            throw new IllegalStateException(errorPrefix + resolvedPath, exception);
        }
        return builder.toString();
    }

    /**
     * 编译单个 shader。
     *
     * <p>失败路径会显式调用 {@code glDeleteShader} 释放已分配的 shader 对象，
     * 避免 LTS 期间在频繁字体重载或运行时 reload 场景下泄漏 GL 资源。</p>
     *
     * @param source shader 源码
     * @param shaderType shader 类型
     * @param errorPrefix 失败提示前缀
     * @return shader id
     */
    public static int compileShader(String source, int shaderType, String errorPrefix) {
        int shaderId = GL20.glCreateShader(shaderType);
        boolean success = false;
        try {
            GL20.glShaderSource(shaderId, source);
            GL20.glCompileShader(shaderId);
            if (GL20.glGetShaderi(shaderId, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
                String infoLog = GL20.glGetShaderInfoLog(shaderId, 4096);
                throw new IllegalStateException(errorPrefix + infoLog);
            }
            success = true;
            return shaderId;
        } finally {
            if (!success && shaderId != 0) {
                GL20.glDeleteShader(shaderId);
            }
        }
    }

    /**
     * 链接并验证着色器程序。
     *
     * @param programId program id
     * @param linkErrorPrefix 链接失败提示前缀
     * @param validateErrorPrefix 验证失败提示前缀
     */
    public static void linkAndValidateProgram(int programId, String linkErrorPrefix, String validateErrorPrefix) {
        GL20.glLinkProgram(programId);
        if (GL20.glGetProgrami(programId, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException(linkErrorPrefix + GL20.glGetProgramInfoLog(programId, 4096));
        }

        GL20.glValidateProgram(programId);
        if (GL20.glGetProgrami(programId, GL20.GL_VALIDATE_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException(validateErrorPrefix + GL20.glGetProgramInfoLog(programId, 4096));
        }
    }
}
