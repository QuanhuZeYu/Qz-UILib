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
    private final String truncatedResults;
    private final String hoverHint;
    private final String infoBarIdPattern;
    private final String alreadyConfiguredBadge;
    private final String memberAddingBanner;
    private final String memberEditingBanner;
    private final String keyboardHint;
    private final String scrollHint;
    private final String densityLabel;
    private final String removedToast;
    private final String undoAction;

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
        truncatedResults = required(builder.truncatedResults, "truncatedResults");
        hoverHint = required(builder.hoverHint, "hoverHint");
        infoBarIdPattern = required(builder.infoBarIdPattern, "infoBarIdPattern");
        alreadyConfiguredBadge = required(builder.alreadyConfiguredBadge, "alreadyConfiguredBadge");
        memberAddingBanner = required(builder.memberAddingBanner, "memberAddingBanner");
        memberEditingBanner = required(builder.memberEditingBanner, "memberEditingBanner");
        keyboardHint = required(builder.keyboardHint, "keyboardHint");
        scrollHint = required(builder.scrollHint, "scrollHint");
        densityLabel = required(builder.densityLabel, "densityLabel");
        removedToast = required(builder.removedToast, "removedToast");
        undoAction = required(builder.undoAction, "undoAction");
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
    /** @return 结果被搜索上限截断时的提示文案（与顶栏统计同行） */
    public String truncatedResults() { return truncatedResults; }

    /** @return 信息条空闲态的操作提示（P5 §3.3：不允许空条） */
    public String hoverHint() { return hoverHint; }

    /**
     * @return 「已配置」候选标记文案（T5 UX-18 / D-P4-3）
     *
     * <p>SPI 路径不再排除「已在当前规则中的候选」，结果单元必须能区分出这一状态：
     * 单元挂一颗主题强调色圆点，信息条追加本文案（形态见 {@code SearchResultList} 的
     * {@code configuredKeys} 分量）。SPI 路径下点击仍走既有激活语义（不做静默丢弃）。</p>
     */
    public String alreadyConfiguredBadge() { return alreadyConfiguredBadge; }

    /** @return 成员区「新增模式」横幅文案（点击候选即添加、Esc 结束） */
    public String memberAddingBanner() { return memberAddingBanner; }

    /**
     * 成员区「编辑模式」横幅文案（{@code {name}} 会替换为成员展示名）。
     *
     * @param name 正在编辑的成员展示名（可为 null，按空串处理）
     * @return 横幅文案
     */
    public String memberEditingBanner(String name) {
        return memberEditingBanner.replace("{name}", name == null ? "" : name);
    }

    /** @return 信息条键盘态提示（网格持有焦点时替代 hover 提示） */
    public String keyboardHint() { return keyboardHint; }

    /** @return 信息条滚动提示（结果区仍有未显示的滚动余量时） */
    public String scrollHint() { return scrollHint; }

    /** @return 密度档位标签（与生效密度名拼成状态文本，见 CategoryNavPane 底部状态行） */
    public String densityLabel() { return densityLabel; }

    /**
     * @param name 被删除成员的展示名（可为 null，按空串处理）
     * @return 删除后的撤销条文案（占位符 {@code {name}}）
     */
    public String removedToast(String name) {
        return removedToast.replace("{name}", name == null ? "" : name);
    }

    /** @return 撤销动作文案（撤销条按钮） */
    public String undoAction() { return undoAction; }

    /**
     * 信息条悬停态单行文案：{@code <label> · <id>}（P5 §3.3 / Q2 单行取法）。
     *
     * <p>用 {@code {label}} / {@code {id}} 两个占位符而不是在控件里拼字符串：拼接形态属文案，
     * 应可被注入方完全控制（中文语境可能想用「名称：X（ID：Y）」这类排布）。</p>
     *
     * @param label 候选完整标签
     * @param id    稳定 ID（可能已含 {@link #tooltipPrefix()} 前缀）
     * @return 单行信息条文案
     */
    public String infoBarIdLabel(String label, String id) {
        return infoBarIdPattern.replace("{label}", label == null ? "" : label)
                .replace("{id}", id == null ? "" : id);
    }

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
        private String truncatedResults = "Results truncated — refine your search";
        private String hoverHint = "Hover a result to see its full name and ID";
        private String infoBarIdPattern = "{label} · {id}";
        private String alreadyConfiguredBadge = "In group";
        private String memberAddingBanner = "Click items to keep adding (Esc to finish)";
        private String memberEditingBanner = "Editing: {name}";
        private String keyboardHint = "Arrow keys to move, Enter to activate";
        private String scrollHint = "Scroll for more results";
        private String densityLabel = "Density";
        private String removedToast = "Removed {name}";
        private String undoAction = "Undo";

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
        /** 设置结果截断提示文案（P5 §3.3：限量必须渲染提示）。 */
        public Builder truncatedResults(String value) { truncatedResults = value; return this; }

        /** 设置信息条空闲态操作提示（P5 §3.3：不允许空条）。 */
        public Builder hoverHint(String value) { hoverHint = value; return this; }

        /** 设置信息条悬停态单行模板（占位符 {label} / {id}）。 */
        public Builder infoBarIdPattern(String value) { infoBarIdPattern = value; return this; }

        /** 设置「已配置」候选标记文案（T5 UX-18）。 */
        public Builder alreadyConfiguredBadge(String value) {
            alreadyConfiguredBadge = value; return this;
        }

        /** 设置成员区新增模式横幅文案。 */
        public Builder memberAddingBanner(String value) { memberAddingBanner = value; return this; }

        /** 设置成员区编辑模式横幅文案（占位符 {name}）。 */
        public Builder memberEditingBanner(String value) { memberEditingBanner = value; return this; }

        /** 设置信息条键盘态提示文案。 */
        public Builder keyboardHint(String value) { keyboardHint = value; return this; }

        /** 设置信息条滚动提示文案。 */
        public Builder scrollHint(String value) { scrollHint = value; return this; }

        /** 设置密度档位标签文案。 */
        public Builder densityLabel(String value) { densityLabel = value; return this; }

        /** 设置删除撤销条文案（占位符 {name}）。 */
        public Builder removedToast(String value) { removedToast = value; return this; }

        /** 设置撤销动作文案。 */
        public Builder undoAction(String value) { undoAction = value; return this; }

        /** 构建不可变扩展文案。 */
        public SearchPickerPanelPresentation build() { return new SearchPickerPanelPresentation(this); }
    }
}
