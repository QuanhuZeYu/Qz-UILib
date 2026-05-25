package club.heiqi.uilib.config;

/**
 * 配置同步用的轻量分类描述。
 */
public final class ConfigSyncCategorySpec {

    private final String categoryName;
    private final String displayTitle;
    private final String description;

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

    public String getCategoryName() {
        return categoryName;
    }

    public String getDisplayTitle() {
        return displayTitle;
    }

    public String getDescription() {
        return description;
    }
}
