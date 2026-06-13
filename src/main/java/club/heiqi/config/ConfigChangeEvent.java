package club.heiqi.config;

/**
 * 配置变更事件
 */
public class ConfigChangeEvent {

    private final String path;
    private final Object oldValue;
    private final Object newValue;
    private final ChangeType type;

    public ConfigChangeEvent(String path, Object oldValue, Object newValue, ChangeType type) {
        this.path = path;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.type = type;
    }

    /**
     * 获取变更路径
     * 
     * @return 路径
     */
    public String getPath() {
        return path;
    }

    /**
     * 获取旧值
     * 
     * @return 旧值，如果是新增则为 null
     */
    public Object getOldValue() {
        return oldValue;
    }

    /**
     * 获取新值
     * 
     * @return 新值，如果是删除则为 null
     */
    public Object getNewValue() {
        return newValue;
    }

    /**
     * 获取变更类型
     * 
     * @return 变更类型
     */
    public ChangeType getType() {
        return type;
    }

    /**
     * 变更类型枚举
     */
    public enum ChangeType {
        /** 设置值（新增或修改） */
        SET,
        /** 移除值 */
        REMOVE,
        /** 清空所有 */
        CLEAR,
        /** 重新加载 */
        RELOAD
    }
}
