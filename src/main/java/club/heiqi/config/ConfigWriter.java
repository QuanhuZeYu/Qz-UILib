package club.heiqi.config;

/**
 * 配置写入器接口，负责将配置保存到配置源。
 */
public interface ConfigWriter {

    /**
     * 写入配置到配置源
     * 
     * @param node 配置节点
     * @param target 目标配置源
     * @throws ConfigException 如果写入失败
     */
    void write(ConfigNode node, ConfigSource target) throws ConfigException;

    /**
     * 获取写入器支持的配置格式
     * 
     * @return 配置格式
     */
    ConfigFormat getFormat();
}
