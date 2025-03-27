package club.heiqi.qz_uilib.jarFile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class Reader {
    public static Logger LOG = LogManager.getLogger();

    /**
     * 从JAR内读取文件
     * @param resourcePath 资源路径，自动处理开头的斜杠
     * @return 文件内容字符串
     * @throws RuntimeException 如果读取文件失败
     */
    public static String readFile(String resourcePath) {
        // 规范化资源路径（去掉开头的斜杠）
        String normalizedPath = resourcePath.startsWith("/")
            ? resourcePath.substring(1)
            : resourcePath;

        // 获取当前类的类加载器
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = ClassLoader.getSystemClassLoader();
        }

        try (InputStream inputStream = classLoader.getResourceAsStream(normalizedPath)) {
            if (inputStream == null) {
                throw new RuntimeException("Resource not found: " + normalizedPath);
            }

            // Java 8 兼容的读取方式
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            byte[] bytes = result.toByteArray();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read resource: " + normalizedPath, e);
        }
    }
}
