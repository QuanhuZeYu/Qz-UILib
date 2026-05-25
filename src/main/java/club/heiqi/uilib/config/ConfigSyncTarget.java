package club.heiqi.uilib.config;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import net.minecraftforge.common.config.Configuration;

/**
 * 可由 UILib 配置同步服务管理的服务端权威配置目标。
 *
 * <p>业务方在 preInit 或更早的初始化阶段注册目标后，本地模板页和远程配置页都可以用同一个
 * screenId 打开配置会话、同步草稿并执行显式保存。</p>
 */
public final class ConfigSyncTarget {

    private final String screenId;
    private final String modId;
    private final String title;
    private final String subtitle;
    private final String description;
    private final String configPath;
    private final Configuration configuration;
    private final List<ConfigSyncCategorySpec> categories;
    private final SaveAction saveAction;

    private ConfigSyncTarget(Builder builder) {
        this.screenId = requireText(builder.screenId, "screenId");
        this.modId = normalize(builder.modId);
        this.title = normalize(builder.title).isEmpty() ? this.screenId : normalize(builder.title);
        this.subtitle = normalize(builder.subtitle);
        this.description = normalize(builder.description);
        this.configuration = Objects.requireNonNull(builder.configuration, "configuration");
        this.configPath = resolveConfigPath(builder.configPath, configuration);
        this.categories = Collections.unmodifiableList(new ArrayList<ConfigSyncCategorySpec>(builder.categories));
        this.saveAction = builder.saveAction == null ? defaultSaveAction() : builder.saveAction;
    }

    /**
     * 创建配置同步目标构造器。
     *
     * @param screenId 页面/配置目标标识
     * @param configuration 服务端权威 Forge 配置对象
     * @return 构造器
     */
    public static Builder builder(String screenId, Configuration configuration) {
        return new Builder(screenId, configuration);
    }

    /**
     * 返回配置目标标识。
     *
     * @return screenId
     */
    public String getScreenId() {
        return screenId;
    }

    /**
     * 返回目标所属 Mod ID。
     *
     * @return Mod ID
     */
    public String getModId() {
        return modId;
    }

    /**
     * 返回展示标题。
     *
     * @return 展示标题
     */
    public String getTitle() {
        return title;
    }

    /**
     * 返回展示副标题。
     *
     * @return 副标题
     */
    public String getSubtitle() {
        return subtitle;
    }

    /**
     * 返回展示说明。
     *
     * @return 说明文本
     */
    public String getDescription() {
        return description;
    }

    /**
     * 返回配置文件路径展示文本。
     *
     * @return 配置路径
     */
    public String getConfigPath() {
        return configPath;
    }

    /**
     * 返回服务端权威 Forge 配置对象。
     *
     * @return 配置对象
     */
    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * 返回配置页展示和同步的分类集合。
     *
     * @return 只读分类集合
     */
    public List<ConfigSyncCategorySpec> getCategories() {
        return categories;
    }

    /**
     * 执行目标保存动作。
     */
    void save() {
        saveAction.save(configuration);
    }

    private static SaveAction defaultSaveAction() {
        return new SaveAction() {
            @Override
            public void save(Configuration configuration) {
                if (configuration != null && configuration.hasChanged()) {
                    configuration.save();
                }
            }
        };
    }

    private static String requireText(String value, String label) {
        String resolved = normalize(value);
        if (resolved.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return resolved;
    }

    private static String resolveConfigPath(String configuredPath, Configuration configuration) {
        String normalized = normalize(configuredPath);
        if (!normalized.isEmpty()) {
            return normalized;
        }
        File configFile = configuration.getConfigFile();
        return configFile == null ? "" : configFile.getAbsolutePath();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 服务端保存动作。
     */
    public interface SaveAction {

        /**
         * 保存服务端权威配置，并在需要时触发业务重载。
         *
         * @param configuration 当前权威配置对象
         */
        void save(Configuration configuration);
    }

    /**
     * 配置同步目标构造器。
     */
    public static final class Builder {

        private final String screenId;
        private final Configuration configuration;
        private String modId = "";
        private String title = "";
        private String subtitle = "";
        private String description = "";
        private String configPath = "";
        private final List<ConfigSyncCategorySpec> categories = new ArrayList<ConfigSyncCategorySpec>();
        private SaveAction saveAction;

        private Builder(String screenId, Configuration configuration) {
            this.screenId = screenId;
            this.configuration = configuration;
        }

        /**
         * 设置所属 Mod ID。
         *
         * @param modId Mod ID
         * @return 当前构造器
         */
        public Builder modId(String modId) {
            this.modId = modId;
            return this;
        }

        /**
         * 设置展示标题。
         *
         * @param title 标题
         * @return 当前构造器
         */
        public Builder title(String title) {
            this.title = title;
            return this;
        }

        /**
         * 设置展示副标题。
         *
         * @param subtitle 副标题
         * @return 当前构造器
         */
        public Builder subtitle(String subtitle) {
            this.subtitle = subtitle;
            return this;
        }

        /**
         * 设置展示说明。
         *
         * @param description 说明文本
         * @return 当前构造器
         */
        public Builder description(String description) {
            this.description = description;
            return this;
        }

        /**
         * 设置配置路径展示文本。
         *
         * @param configPath 配置路径
         * @return 当前构造器
         */
        public Builder configPath(String configPath) {
            this.configPath = configPath;
            return this;
        }

        /**
         * 覆盖配置分类集合。
         *
         * @param categories 分类集合
         * @return 当前构造器
         */
        public Builder categories(List<ConfigSyncCategorySpec> categories) {
            this.categories.clear();
            if (categories != null) {
                this.categories.addAll(categories);
            }
            return this;
        }

        /**
         * 追加配置分类。
         *
         * @param category 分类描述
         * @return 当前构造器
         */
        public Builder addCategory(ConfigSyncCategorySpec category) {
            if (category != null) {
                categories.add(category);
            }
            return this;
        }

        /**
         * 设置保存动作。
         *
         * @param saveAction 保存动作；为 null 时使用默认 Configuration.save()
         * @return 当前构造器
         */
        public Builder saveAction(SaveAction saveAction) {
            this.saveAction = saveAction;
            return this;
        }

        /**
         * 创建配置同步目标。
         *
         * @return 配置同步目标
         */
        public ConfigSyncTarget build() {
            return new ConfigSyncTarget(this);
        }
    }
}
