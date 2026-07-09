package club.heiqi.config.ui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 字段恢复默认策略。
 *
 * <p>用于给通用 {@link ConfigScreen} 注入逐字段恢复默认行为，避免框架层硬编码具体业务 path。
 * 策略优先级固定为：skip 跳过 &gt; custom 自定义动作 &gt; 默认
 * {@link DraftSignalAdapter#resetFieldToDefault(String)}。</p>
 */
public final class FieldRestorePolicy {

    /** 恢复默认时跳过的字段 path 集合。 */
    private final Set<String> skippedPaths = new HashSet<String>();
    /** 字段 path 到自定义恢复动作的映射。 */
    private final Map<String, Consumer<DraftSignalAdapter>> customActions =
            new HashMap<String, Consumer<DraftSignalAdapter>>();

    /**
     * 标记字段在恢复默认时跳过。
     *
     * @param path 字段全路径
     * @return 当前策略实例，便于链式配置
     */
    public FieldRestorePolicy skip(String path) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        skippedPaths.add(path);
        return this;
    }

    /**
     * 为字段注册自定义恢复默认动作。
     *
     * @param path   字段全路径
     * @param action 自定义动作，入参为当前草稿适配器
     * @return 当前策略实例，便于链式配置
     */
    public FieldRestorePolicy custom(String path, Consumer<DraftSignalAdapter> action) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }
        customActions.put(path, action);
        return this;
    }

    /**
     * 查询字段是否应跳过恢复默认。
     *
     * @param path 字段全路径
     * @return true 表示跳过
     */
    public boolean isSkipped(String path) {
        return skippedPaths.contains(path);
    }

    /**
     * 查询字段自定义恢复动作。
     *
     * @param path 字段全路径
     * @return 自定义动作；不存在时返回 null
     */
    public Consumer<DraftSignalAdapter> getCustom(String path) {
        return customActions.get(path);
    }
}
