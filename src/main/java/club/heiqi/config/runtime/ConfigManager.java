package club.heiqi.config.runtime;

import club.heiqi.config.ConfigChangeEvent;
import club.heiqi.config.ConfigException;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.schema.ConfigSchema;

import java.io.File;
import java.util.Map;

/**
 * 配置门面，独占保存事务序列。
 *
 * <p>聚合 {@link Authority}、{@link Persistence}、{@link ConfigEventBus}，对外暴露
 * 启动加载、草稿打开、保存事务、原始态 flush 等入口。</p>
 *
 * <p>保存事务严格按 5 步序列执行，保证写盘失败可回滚、校验失败不写盘：</p>
 * <ol>
 *   <li>{@link DraftBuffer#validateAll()} 有错 → 返回 {@link SaveOutcome#invalid(ValidationResult)}。</li>
 *   <li>snapshot = {@link Authority#snapshotTyped()} 留作回滚备份。</li>
 *   <li>{@link Authority#applyAll(Map)} 应用草稿快照。</li>
 *   <li>{@link Persistence#writeAll} 失败 → {@link Authority#applyAll(Map)} 回滚 → 返回 {@link SaveOutcome#ioFailed(String)}。</li>
 *   <li>成功 → {@link DraftBuffer#commitDraftToCurrent()} + 发布 {@link ConfigChangeEvent.ChangeType#BATCH_SAVE} → 返回 {@link SaveOutcome#ok()}。</li>
 * </ol>
 *
 * <p>本类零依赖 uilib。</p>
 */
public final class ConfigManager {

    private final Persistence persistence;
    private final Authority authority;
    private final ConfigEventBus eventBus;

    private ConfigManager(Persistence persistence, Authority authority, ConfigEventBus eventBus) {
        this.persistence = persistence;
        this.authority = authority;
        this.eventBus = eventBus;
    }

    /**
     * 启动加载：创建持久化器、加载权威态、初始化事件总线。
     *
     * @param file   配置文件
     * @param schema 配置 schema
     * @return 配置管理器
     * @throws ConfigException 文件解析失败
     */
    public static ConfigManager bootstrap(File file, ConfigSchema schema) throws ConfigException {
        if (schema == null) {
            throw new IllegalArgumentException("schema must not be null");
        }
        Persistence persistence = new Persistence(file, ConfigFormat.YAML);
        Authority authority = Authority.load(file, schema);
        ConfigEventBus eventBus = new ConfigEventBus();
        return new ConfigManager(persistence, authority, eventBus);
    }

    /**
     * @return 权威快照
     */
    public Authority authority() {
        return authority;
    }

    /**
     * @return 配置 schema
     */
    public ConfigSchema schema() {
        return authority.schema();
    }

    /**
     * @return 事件总线
     */
    public ConfigEventBus eventBus() {
        return eventBus;
    }

    /**
     * 打开草稿，从权威态深拷贝种子。
     *
     * @return 草稿容器
     */
    public DraftBuffer openDraft() {
        return DraftBuffer.from(authority);
    }

    /**
     * 保存事务，唯一保存路径。
     *
     * @param draft 草稿容器
     * @return 保存结果
     */
    public SaveOutcome save(DraftBuffer draft) {
        if (draft == null) {
            throw new IllegalArgumentException("draft must not be null");
        }

        // 1. 校验
        ValidationResult validation = draft.validateAll();
        if (validation.hasErrors()) {
            return SaveOutcome.invalid(validation);
        }

        // 2. 备份当前权威态
        Map<String, Object> snapshot = authority.snapshotTyped();

        // 3. 应用草稿到权威态
        authority.applyAll(draft.draftSnapshot());

        // 4. 写盘，失败回滚
        try {
            persistence.writeAll(authority.snapshotTyped(), authority.schema());
        } catch (ConfigException e) {
            authority.applyAll(snapshot);
            return SaveOutcome.ioFailed(e.getMessage());
        }

        // 5. 成功：同步草稿 current + 发布事件
        draft.commitDraftToCurrent();
        eventBus.publish(new ConfigChangeEvent("", null, null, ConfigChangeEvent.ChangeType.BATCH_SAVE));
        return SaveOutcome.ok();
    }

    /**
     * 写盘但不经草稿，供 {@link LegacyAdapter#setRawJson} 后显式 flush。
     *
     * @throws ConfigException 写盘失败
     */
    public void flushRaw() throws ConfigException {
        persistence.writeAll(authority.snapshotTyped(), authority.schema());
    }
}
