package club.heiqi.config;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 配置工厂类，提供配置加载的统一入口。
 */
public final class Config {

    private static final Map<ConfigFormat, ConfigLoader> loaders = new HashMap<ConfigFormat, ConfigLoader>();

    static {
        // 注册默认加载器
        registerLoader(new JsonConfigLoader());
        registerLoader(new YamlConfigLoader());
    }

    private Config() {}

    /**
     * 从文件加载配置
     * 
     * @param file 配置文件
     * @return 配置根节点
     * @throws ConfigException 如果加载失败
     */
    public static ConfigNode load(File file) throws ConfigException {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }

        ConfigFormat format = ConfigFormat.fromFilename(file.getName());
        if (format == null) {
            throw new ConfigException("Cannot determine config format from filename: " + file.getName());
        }

        return load(ConfigSource.fromFile(file), format);
    }

    /**
     * 从配置源加载配置
     * 
     * @param source 配置源
     * @param format 配置格式
     * @return 配置根节点
     * @throws ConfigException 如果加载失败
     */
    public static ConfigNode load(ConfigSource source, ConfigFormat format) throws ConfigException {
        if (source == null) {
            throw new IllegalArgumentException("Source cannot be null");
        }
        if (format == null) {
            throw new IllegalArgumentException("Format cannot be null");
        }

        ConfigLoader loader = loaders.get(format);
        if (loader == null) {
            throw new ConfigException("No loader registered for format: " + format);
        }

        return loader.load(source);
    }

    /**
     * 从字符串加载配置
     * 
     * @param content 配置内容
     * @param format 配置格式
     * @return 配置根节点
     * @throws ConfigException 如果加载失败
     */
    public static ConfigNode parse(String content, ConfigFormat format) throws ConfigException {
        return load(ConfigSource.fromString(content, "string"), format);
    }

    /**
     * 注册配置加载器
     * 
     * @param loader 加载器
     */
    public static void registerLoader(ConfigLoader loader) {
        if (loader == null) {
            throw new IllegalArgumentException("Loader cannot be null");
        }
        loaders.put(loader.getFormat(), loader);
    }

    /**
     * 获取已注册的加载器
     * 
     * @param format 配置格式
     * @return 加载器，如果未注册则返回 null
     */
    public static ConfigLoader getLoader(ConfigFormat format) {
        return loaders.get(format);
    }

    /**
     * 创建可变配置（从文件加载）
     * 
     * @param file 配置文件
     * @return 可变配置对象
     * @throws ConfigException 如果加载失败
     */
    public static MutableConfig loadMutable(File file) throws ConfigException {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }

        ConfigFormat format = ConfigFormat.fromFilename(file.getName());
        if (format == null) {
            throw new ConfigException("Cannot determine config format from filename: " + file.getName());
        }

        ConfigSource source = ConfigSource.fromFile(file);
        
        // 如果文件不存在，创建空配置
        if (!file.exists()) {
            return new DefaultMutableConfig(format, source);
        }

        ConfigNode node = load(source, format);
        return new DefaultMutableConfig(node, format, source);
    }

    /**
     * 创建可变配置（从配置源加载）
     * 
     * @param source 配置源
     * @param format 配置格式
     * @return 可变配置对象
     * @throws ConfigException 如果加载失败
     */
    public static MutableConfig loadMutable(ConfigSource source, ConfigFormat format) throws ConfigException {
        ConfigNode node = load(source, format);
        return new DefaultMutableConfig(node, format, source);
    }

    /**
     * 创建空的可变配置
     * 
     * @param format 配置格式
     * @return 可变配置对象
     */
    public static MutableConfig createMutable(ConfigFormat format) {
        return new DefaultMutableConfig(format, null);
    }

    /**
     * 创建空的可变配置（关联到文件）
     * 
     * @param file 配置文件
     * @param format 配置格式
     * @return 可变配置对象
     */
    public static MutableConfig createMutable(File file, ConfigFormat format) {
        return new DefaultMutableConfig(format, ConfigSource.fromFile(file));
    }
}
