package club.heiqi.uilib.config;

import java.util.List;

import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;

/**
 * Forge 配置分类解析工具。
 *
 * <p>Forge 1.7.10 的 {@link Configuration#getCategory(String)} 会在不存在时创建分类，
 * 因此所有查询路径都必须先通过本工具确认分类真实存在。</p>
 */
final class ConfigCategoryResolver {

    private ConfigCategoryResolver() {}

    /**
     * 按主分类名与显式 alias 解析同步分类。
     *
     * @param configuration 配置对象
     * @param categorySpec 分类描述
     * @return 已存在分类；找不到时返回 null
     */
    static ConfigCategory resolve(Configuration configuration, ConfigSyncCategorySpec categorySpec) {
        if (categorySpec == null) {
            return null;
        }
        return resolve(configuration, categorySpec.getCategoryName(), categorySpec.getAliases());
    }

    /**
     * 按主分类名与显式 alias 解析模板分类。
     *
     * @param configuration 配置对象
     * @param categorySpec 分类描述
     * @return 已存在分类；找不到时返回 null
     */
    static ConfigCategory resolve(Configuration configuration, ForgeConfigTemplateScreen.CategorySpec categorySpec) {
        if (categorySpec == null) {
            return null;
        }
        return resolve(configuration, categorySpec.getCategoryName(), categorySpec.getAliases());
    }

    /**
     * 按主分类名与显式 alias 解析分类。
     *
     * @param configuration 配置对象
     * @param categoryName 主分类名
     * @param aliases 显式 alias
     * @return 已存在分类；找不到时返回 null
     */
    static ConfigCategory resolve(Configuration configuration, String categoryName, List<String> aliases) {
        ConfigCategory category = resolveExisting(configuration, categoryName);
        if (category != null) {
            return category;
        }
        if (aliases == null) {
            return null;
        }
        for (String alias : aliases) {
            category = resolveExisting(configuration, alias);
            if (category != null) {
                return category;
            }
        }
        return null;
    }

    /**
     * 严格解析已存在的分类名。
     *
     * @param configuration 配置对象
     * @param categoryName 分类名
     * @return 已存在分类；找不到时返回 null
     */
    static ConfigCategory resolveExisting(Configuration configuration, String categoryName) {
        if (configuration == null || categoryName == null || categoryName.trim().isEmpty()) {
            return null;
        }
        String requestedName = categoryName.trim();
        if (!configuration.hasCategory(requestedName)) {
            return null;
        }
        return configuration.getCategory(requestedName);
    }
}
