package club.heiqi.config;

/**
 * 可变配置接口，支持读取和修改配置。
 * 
 * 相比只读的 ConfigNode，MutableConfig 提供了修改配置的能力，
 * 并支持持久化到文件。
 */
public interface MutableConfig extends ConfigNode {

    /**
     * 设置配置值
     * 
     * @param path 路径，使用点号分隔
     * @param value 值（字符串、数字、布尔值、ConfigNode、列表、映射表等）
     * @return 当前配置对象（支持链式调用）
     */
    MutableConfig set(String path, Object value);

    /**
     * 移除配置项
     * 
     * @param path 路径
     * @return 当前配置对象（支持链式调用）
     */
    MutableConfig remove(String path);

    /**
     * 清空所有配置
     * 
     * @return 当前配置对象（支持链式调用）
     */
    MutableConfig clear();

    /**
     * 保存配置到文件
     * 
     * @throws ConfigException 如果保存失败
     */
    void save() throws ConfigException;

    /**
     * 保存配置到指定文件
     * 
     * @param target 目标配置源
     * @throws ConfigException 如果保存失败
     */
    void saveTo(ConfigSource target) throws ConfigException;

    /**
     * 重新加载配置（从文件）
     * 
     * @throws ConfigException 如果加载失败
     */
    void reload() throws ConfigException;

    /**
     * 获取配置格式
     * 
     * @return 配置格式
     */
    ConfigFormat getFormat();

    /**
     * 获取配置源
     * 
     * @return 配置源，如果没有关联文件则返回 null
     */
    ConfigSource getSource();

    /**
     * 判断配置是否已修改（与文件不一致）
     * 
     * @return 如果已修改返回 true
     */
    boolean isDirty();

    /**
     * 标记配置为已保存状态
     */
    void markClean();

    /**
     * 添加配置变更监听器
     * 
     * @param listener 监听器
     */
    void addChangeListener(ConfigChangeListener listener);

    /**
     * 移除配置变更监听器
     * 
     * @param listener 监听器
     */
    void removeChangeListener(ConfigChangeListener listener);

    /**
     * 转换为只读 ConfigNode
     * 
     * @return 只读配置节点
     */
    ConfigNode asImmutable();
}
