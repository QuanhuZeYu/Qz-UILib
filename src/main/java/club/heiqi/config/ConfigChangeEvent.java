package club.heiqi.config;

/**
 * 配置变更事件。
 *
 * <p>{@link ChangeType#BATCH_SAVE}：{@code ConfigManager.save} 成功写盘并提交 Authority 后发布。
 * {@link ChangeType#RELOAD}：{@code ConfigManager.reloadDraftFromDisk} 成功校验并原子更新
 * Authority/expected 后发布——<b>不得</b>伪装为 BATCH_SAVE；消费者须显式处理 RELOAD
 *（例如从 Authority 回灌运行态），与保存语义区分。</p>
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
        /**
         * 从磁盘成功 reload（Authority/expected 已原子更新）。
         * 非 save；监听方应从 Authority 回灌运行态，但不得与 BATCH_SAVE 混淆。
         */
        RELOAD,
        /**
         * 批量保存（ConfigManager.save 三阶段事务提交并释放锁后发布）。
         * 监听方应从 Authority 回灌运行态。
         */
        BATCH_SAVE
    }
}
