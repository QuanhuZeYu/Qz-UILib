package club.heiqi.config.ui.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 全屏 picker 面板的不可变扩展文案快照。
 *
 * <p>与 {@link SearchPickerPresentation} 组合使用：基础文案（标题、确认/取消、成员徽章等）
 * 继续取自 SearchPickerPresentation，本类只承载全屏面板新增区域的文案，全部带英文默认值
 * （中文由 Miner 侧 builder 覆盖）。</p>
 */
public final class SearchPickerPanelPresentation {

    private static final SearchPickerPanelPresentation DEFAULT_ENGLISH = builder().build();

    private final String panelTitle;
    private final List<String> categoryDimensions;
    private final String categoryDimensionTitle;
    private final String allCategoryLabel;
    private final String tooltipPrefix;
    private final String emptyCategory;
    private final String variantPanelTitle;
    private final String variantSearchPlaceholder;
    private final String back;
    private final String close;
    private final String addMember;

    private SearchPickerPanelPresentation(Builder builder) {
        panelTitle = required(builder.panelTitle, "panelTitle");
        categoryDimensions = immutableCopy(builder.categoryDimensions, "categoryDimensions");
        categoryDimensionTitle = required(builder.categoryDimensionTitle, "categoryDimensionTitle");
        allCategoryLabel = required(builder.allCategoryLabel, "allCategoryLabel");
        tooltipPrefix = required(builder.tooltipPrefix, "tooltipPrefix");
        emptyCategory = required(builder.emptyCategory, "emptyCategory");
        variantPanelTitle = required(builder.variantPanelTitle, "variantPanelTitle");
        variantSearchPlaceholder = required(builder.variantSearchPlaceholder, "variantSearchPlaceholder");
        back = required(builder.back, "back");
        close = required(builder.close, "close");
        addMember = required(builder.addMember, "addMember");
    }

    /** @return 默认英文扩展文案 */
    public static SearchPickerPanelPresentation defaultEnglish() { return DEFAULT_ENGLISH; }
    /** @return 英文默认值 builder */
    public static Builder builder() { return new Builder(); }

    /** @return 全屏面板标题 */
    public String panelTitle() { return panelTitle; }
    /** @return 分类维度名列表；空列表表示无维度切换（不渲染分段控件） */
    public List<String> categoryDimensions() { return categoryDimensions; }
    /** @return 分类维度切换区域的标题 */
    public String categoryDimensionTitle() { return categoryDimensionTitle; }
    /** @return 「全部」分类行文案 */
    public String allCategoryLabel() { return allCategoryLabel; }
    /** @return 悬浮提示中稳定 key 的前缀（如 "ID: "），空串表示不显示前缀 */
    public String tooltipPrefix() { return tooltipPrefix; }
    /** @return 空分类文案（无任何分类行时的占位） */
    public String emptyCategory() { return emptyCategory; }
    /** @return 变体浮层面板标题 */
    public String variantPanelTitle() { return variantPanelTitle; }
    /** @return 变体搜索输入占位文案 */
    public String variantSearchPlaceholder() { return variantSearchPlaceholder; }
    /** @return 变体浮层返回主面板按钮文案 */
    public String back() { return back; }
    /** @return 关闭面板按钮文案 */
    public String close() { return close; }
    /** @return 当前成员区新增按钮文案 */
    public String addMember() { return addMember; }

    private static String required(String value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " must not be null");
        return value;
    }

    private static List<String> immutableCopy(List<String> values, String name) {
        Objects.requireNonNull(values, name);
        ArrayList<String> copy = new ArrayList<String>(values.size());
        for (String value : values) {
            if (value == null) throw new IllegalArgumentException(name + " must not contain null");
            copy.add(value);
        }
        return Collections.unmodifiableList(copy);
    }

    /** 全屏面板扩展文案 builder。 */
    public static final class Builder {
        private String panelTitle = "Select a value";
        private List<String> categoryDimensions = Collections.emptyList();
        private String categoryDimensionTitle = "Browse";
        private String allCategoryLabel = "All";
        private String tooltipPrefix = "";
        private String emptyCategory = "No categories";
        private String variantPanelTitle = "Choose variants";
        private String variantSearchPlaceholder = "Filter variants";
        private String back = "Back";
        private String close = "Close";
        private String addMember = "Add";

        /** 设置全屏面板标题。 */
        public Builder panelTitle(String value) { panelTitle = value; return this; }
        /** 设置分类维度名列表；空列表表示无维度切换。 */
        public Builder categoryDimensions(List<String> value) {
            categoryDimensions = value == null ? Collections.<String>emptyList() : value; return this;
        }
        /** 设置分类维度切换区域标题。 */
        public Builder categoryDimensionTitle(String value) { categoryDimensionTitle = value; return this; }
        /** 设置「全部」分类行文案。 */
        public Builder allCategoryLabel(String value) { allCategoryLabel = value; return this; }
        /** 设置悬浮提示中稳定 key 的前缀。 */
        public Builder tooltipPrefix(String value) { tooltipPrefix = value; return this; }
        /** 设置空分类占位文案。 */
        public Builder emptyCategory(String value) { emptyCategory = value; return this; }
        /** 设置变体浮层面板标题。 */
        public Builder variantPanelTitle(String value) { variantPanelTitle = value; return this; }
        /** 设置变体搜索输入占位文案。 */
        public Builder variantSearchPlaceholder(String value) { variantSearchPlaceholder = value; return this; }
        /** 设置变体浮层返回主面板按钮文案。 */
        public Builder back(String value) { back = value; return this; }
        /** 设置关闭面板按钮文案。 */
        public Builder close(String value) { close = value; return this; }
        /** 设置当前成员区新增按钮文案。 */
        public Builder addMember(String value) { addMember = value; return this; }

        /** 构建不可变扩展文案。 */
        public SearchPickerPanelPresentation build() { return new SearchPickerPanelPresentation(this); }
    }
}
