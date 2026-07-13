package club.heiqi.config.ui.editor;

import java.util.Objects;

/** 搜索选择器的不可变领域文案快照。 */
public final class SearchPickerPresentation {
    /** 结果摘要格式化器。 */
    public interface ResultSummaryFormatter {
        /** @return 当前结果数量对应的摘要文案 */
        String format(int count);
    }

    /** 当前列表成员格式化器。 */
    public interface CurrentMemberFormatter {
        /** @return 当前成员区域使用的展示文案 */
        String format(SearchPickerData.CurrentMember member);
    }

    private static final SearchPickerPresentation DEFAULT_ENGLISH = builder().build();

    private final String title;
    private final String placeholder;
    private final String all;
    private final String selected;
    private final String unavailableVariant;
    private final String cancel;
    private final String confirm;
    private final String empty;
    private final String truncated;
    private final String currentMembersTitle;
    private final CurrentMemberFormatter currentMemberFormatter;
    private final ResultSummaryFormatter resultSummaryFormatter;
    private final String decodeError;
    private final String searchError;
    private final String encodeError;

    private SearchPickerPresentation(Builder builder) {
        title = required(builder.title, "title");
        placeholder = required(builder.placeholder, "placeholder");
        all = required(builder.all, "all");
        selected = required(builder.selected, "selected");
        unavailableVariant = required(builder.unavailableVariant, "unavailableVariant");
        cancel = required(builder.cancel, "cancel");
        confirm = required(builder.confirm, "confirm");
        empty = required(builder.empty, "empty");
        truncated = required(builder.truncated, "truncated");
        currentMembersTitle = required(builder.currentMembersTitle, "currentMembersTitle");
        currentMemberFormatter = Objects.requireNonNull(builder.currentMemberFormatter, "currentMemberFormatter");
        resultSummaryFormatter = Objects.requireNonNull(builder.resultSummaryFormatter, "resultSummaryFormatter");
        decodeError = required(builder.decodeError, "decodeError");
        searchError = required(builder.searchError, "searchError");
        encodeError = required(builder.encodeError, "encodeError");
    }

    /** @return 默认英文文案 */
    public static SearchPickerPresentation defaultEnglish() { return DEFAULT_ENGLISH; }
    /** @return 英文默认值 builder */
    public static Builder builder() { return new Builder(); }
    /** @return 标题 */ public String title() { return title; }
    /** @return 输入占位文案 */ public String placeholder() { return placeholder; }
    /** @return ALL 模式文案 */ public String all() { return all; }
    /** @return SELECTED 模式文案 */ public String selected() { return selected; }
    /** @return 当前候选未枚举变体的通用文案 */
    public String unavailableVariant(String key) { return unavailableVariant.replace("{key}", key); }
    /** @return 取消文案 */ public String cancel() { return cancel; }
    /** @return 确认文案 */ public String confirm() { return confirm; }
    /** @return 空结果文案 */ public String empty() { return empty; }
    /** @return 截断提示文案 */ public String truncated() { return truncated; }
    /** @return 当前列表成员区域标题 */ public String currentMembersTitle() { return currentMembersTitle; }
    /** @return 当前列表成员的展示文案 */
    public String currentMember(SearchPickerData.CurrentMember member) {
        return required(currentMemberFormatter.format(Objects.requireNonNull(member, "member")), "currentMember");
    }
    /** @return 结果摘要 */ public String resultSummary(int count) { return required(resultSummaryFormatter.format(count), "resultSummary"); }
    /** @return 解码失败文案 */ public String decodeError() { return decodeError; }
    /** @return 搜索失败文案 */ public String searchError() { return searchError; }
    /** @return 编码失败文案 */ public String encodeError() { return encodeError; }

    private static String required(String value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " must not be null");
        return value;
    }

    /** 搜索选择器领域文案 builder。 */
    public static final class Builder {
        private String title = "Select a value";
        private String placeholder = "Search";
        private String all = "All";
        private String selected = "Selected";
        private String unavailableVariant = "Currently unavailable ({key})";
        private String cancel = "Cancel";
        private String confirm = "Confirm";
        private String empty = "No results";
        private String truncated = "Results truncated";
        private String currentMembersTitle = "Current values";
        private CurrentMemberFormatter currentMemberFormatter = member -> {
            if (member.selection() == null) return "Unable to read this value";
            return member.enumerated() ? member.candidate().label() : member.selection().candidateKey();
        };
        private ResultSummaryFormatter resultSummaryFormatter = count -> count + (count == 1 ? " result" : " results");
        private String decodeError = "Unable to read the current value";
        private String searchError = "Unable to search values";
        private String encodeError = "Unable to save the selected value";

        /** 设置标题。 */ public Builder title(String value) { title = value; return this; }
        /** 设置占位文案。 */ public Builder placeholder(String value) { placeholder = value; return this; }
        /** 设置 ALL 文案。 */ public Builder all(String value) { all = value; return this; }
        /** 设置 SELECTED 文案。 */ public Builder selected(String value) { selected = value; return this; }
        /** 设置当前未枚举变体文案，{key} 会替换为稳定 key。 */
        public Builder unavailableVariant(String value) { unavailableVariant = value; return this; }
        /** 设置取消文案。 */ public Builder cancel(String value) { cancel = value; return this; }
        /** 设置确认文案。 */ public Builder confirm(String value) { confirm = value; return this; }
        /** 设置空结果文案。 */ public Builder empty(String value) { empty = value; return this; }
        /** 设置截断文案。 */ public Builder truncated(String value) { truncated = value; return this; }
        /** 设置当前列表成员区域标题。 */
        public Builder currentMembersTitle(String value) { currentMembersTitle = value; return this; }
        /** 设置当前列表成员格式化器。 */
        public Builder currentMemberFormatter(CurrentMemberFormatter value) { currentMemberFormatter = value; return this; }
        /** 设置结果摘要格式化器。 */ public Builder resultSummaryFormatter(ResultSummaryFormatter value) { resultSummaryFormatter = value; return this; }
        /** 设置解码错误文案。 */ public Builder decodeError(String value) { decodeError = value; return this; }
        /** 设置搜索错误文案。 */ public Builder searchError(String value) { searchError = value; return this; }
        /** 设置编码错误文案。 */ public Builder encodeError(String value) { encodeError = value; return this; }
        /** 构建不可变领域文案。 */ public SearchPickerPresentation build() { return new SearchPickerPresentation(this); }
    }
}
