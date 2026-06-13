package club.heiqi.config;

/**
 * 配置加载器接口，负责从配置源加载并解析配置。
 */
public interface ConfigLoader {

    /**
     * 从配置源加载配置
     * 
     * @param source 配置源
     * @return 配置根节点
     * @throws ConfigException 如果加载或解析失败
     */
    ConfigNode load(ConfigSource source) throws ConfigException;

    /**
     * 获取加载器支持的配置格式
     * 
     * @return 配置格式
     */
    ConfigFormat getFormat();
}
