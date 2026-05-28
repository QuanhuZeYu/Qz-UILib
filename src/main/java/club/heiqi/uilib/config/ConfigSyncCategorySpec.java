package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 配置同步用的轻量分类描述。
 */
public final class ConfigSyncCategorySpec {

    private final String categoryName;
    private final String displayTitle;
    private final String description;
    private final List<String> aliases = new ArrayList<String>();

    /**
     * 创建分类描述。
     *
     * @param categoryName 分类名
     * @param displayTitle 展示标题
     * @param description 描述文本
     */
    public ConfigSyncCategorySpec(String categoryName, String displayTitle, String description) {
        this.categoryName = categoryName == null ? "" : categoryName.trim();
        this.displayTitle = displayTitle == null ? "" : displayTitle.trim();
        this.description = description == null ? "" : description.trim();
    }

    /**
     * 声明显式分类 alias。
     *
     * <p>分类匹配默认大小写敏感；只有通过 alias 声明的历史名称才会参与查找。</p>
     *
     * @param alias 分类 alias
     * @return 当前分类描述
     */
    public ConfigSyncCategorySpec addAlias(String alias) {
        String normalized = alias == null ? "" : alias.trim();
        if (!normalized.isEmpty() && !aliases.contains(normalized) && !categoryName.equals(normalized)) {
            aliases.add(normalized);
        }
        return this;
    }

    /**
     * 批量声明分类 alias。
     *
     * @param aliases 分类 alias 数组
     * @return 当前分类描述
     */
    public ConfigSyncCategorySpec addAliases(String... aliases) {
        if (aliases == null) {
            return this;
        }
        for (String alias : aliases) {
            addAlias(alias);
        }
        return this;
    }

    /**
     * 批量声明分类 alias。
     *
     * @param aliases 分类 alias 列表
     * @return 当前分类描述
     */
    public ConfigSyncCategorySpec addAliases(List<String> aliases) {
        if (aliases == null) {
            return this;
        }
        for (String alias : aliases) {
            addAlias(alias);
        }
        return this;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public String getDisplayTitle() {
        return displayTitle;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 返回显式声明的分类 alias。
     *
     * @return 只读 alias 列表
     */
    public List<String> getAliases() {
        return Collections.unmodifiableList(aliases);
    }
}
