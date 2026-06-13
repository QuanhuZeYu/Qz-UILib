package club.heiqi.config;

import java.io.File;
import java.io.InputStream;

/**
 * 配置源接口，表示配置数据的来源。
 * 可以是文件、输入流、字符串等。
 */
public interface ConfigSource {

    /**
     * 读取配置内容
     * 
     * @return 配置内容字符串
     * @throws ConfigException 如果读取失败
     */
    String read() throws ConfigException;

    /**
     * 获取配置源描述，用于错误信息和日志
     * 
     * @return 配置源描述
     */
    String getDescription();

    /**
     * 从文件创建配置源
     * 
     * @param file 文件
     * @return 配置源
     */
    static ConfigSource fromFile(File file) {
        return new FileConfigSource(file);
    }

    /**
     * 从输入流创建配置源
     * 
     * @param inputStream 输入流
     * @param description 描述信息
     * @return 配置源
     */
    static ConfigSource fromInputStream(InputStream inputStream, String description) {
        return new InputStreamConfigSource(inputStream, description);
    }

    /**
     * 从字符串创建配置源
     * 
     * @param content 配置内容
     * @param description 描述信息
     * @return 配置源
     */
    static ConfigSource fromString(String content, String description) {
        return new StringConfigSource(content, description);
    }
}
