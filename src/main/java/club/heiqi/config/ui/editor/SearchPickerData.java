package club.heiqi.config.ui.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** 搜索选择器的平台无关不可变数据契约。 */
public final class SearchPickerData {
    /** 候选变体的选择模式。 */
    public enum SelectionMode { ALL, SELECTED }

    private SearchPickerData() { }

    /** 候选项。 */
    public static final class Candidate {
        private final String key;
        private final String label;
        private final List<Variant> variants;

        /** 创建候选项并深拷贝变体列表。 */
        public Candidate(String key, String label, List<Variant> variants) {
            this.key = requireText(key, "candidate key");
            this.label = requireText(label, "candidate label");
            List<Variant> copy = immutableCopy(variants, "variants");
            Set<String> variantKeys = new HashSet<String>();
            for (Variant variant : copy) {
                if (!variantKeys.add(variant.key())) {
                    throw new IllegalArgumentException("variant keys must be unique");
                }
            }
            this.variants = copy;
        }

        /** @return 稳定 key */
        public String key() { return key; }
        /** @return 展示文本 */
        public String label() { return label; }
        /** @return 只读变体快照 */
        public List<Variant> variants() { return variants; }
    }

    /** 候选变体。 */
    public static final class Variant {
        private final String key;
        private final String label;

        /** 创建候选变体。 */
        public Variant(String key, String label) {
            this.key = requireText(key, "variant key");
            this.label = requireText(label, "variant label");
        }

        /** @return 候选内稳定 key */
        public String key() { return key; }
        /** @return 展示文本 */
        public String label() { return label; }
    }

    /** 当前选择。 */
    public static final class Selection {
        private final String candidateKey;
        private final SelectionMode mode;
        private final List<String> variantKeys;

        /** 兼容旧契约：null 映射 ALL，非 null 映射 SELECTED。 */
        public Selection(String candidateKey, String variantKey) {
            this(candidateKey, variantKey == null ? SelectionMode.ALL : SelectionMode.SELECTED,
                    variantKey == null ? Collections.<String>emptyList() : Collections.singletonList(variantKey));
        }

        /** 创建并强校验不可变选择。 */
        public Selection(String candidateKey, SelectionMode mode, List<String> variantKeys) {
            this.candidateKey = requireText(candidateKey, "candidate key");
            this.mode = Objects.requireNonNull(mode, "mode");
            if (variantKeys == null) throw new IllegalArgumentException("variant keys must not be null");
            ArrayList<String> copy = new ArrayList<String>(variantKeys.size());
            Set<String> unique = new HashSet<String>();
            for (String key : variantKeys) {
                String checked = requireText(key, "variant key");
                if (!unique.add(checked)) throw new IllegalArgumentException("variant keys must be unique");
                copy.add(checked);
            }
            if (mode == SelectionMode.ALL && !copy.isEmpty()) {
                throw new IllegalArgumentException("ALL selection must not contain variant keys");
            }
            if (mode == SelectionMode.SELECTED && copy.isEmpty()) {
                throw new IllegalArgumentException("SELECTED selection must contain at least one variant key");
            }
            this.variantKeys = Collections.unmodifiableList(copy);
        }

        /** @return 候选 key */
        public String candidateKey() { return candidateKey; }
        /** @return 选择模式 */
        public SelectionMode mode() { return mode; }
        /** @return 按候选变体顺序排列的只读 key */
        public List<String> variantKeys() { return variantKeys; }
        /** @return 唯一变体 key；ALL 为 null，多项 SELECTED 会快速失败 */
        public String variantKey() {
            if (mode == SelectionMode.SELECTED && variantKeys.size() != 1) {
                throw new IllegalStateException("variantKey is unavailable for multi-key SELECTED selection");
            }
            return mode == SelectionMode.ALL ? null : variantKeys.get(0);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Selection)) return false;
            Selection that = (Selection) other;
            return candidateKey.equals(that.candidateKey) && mode == that.mode
                    && variantKeys.equals(that.variantKeys);
        }

        @Override
        public int hashCode() { return Objects.hash(candidateKey, mode, variantKeys); }
    }

    /** 当前列表成员的不可变 picker 快照。 */
    public static final class CurrentMember {
        private final long memberId;
        private final Selection selection;
        private final Candidate candidate;
        private final boolean enumerated;

        /**
         * 创建当前成员快照。
         *
         * @param memberId 列表内稳定成员身份，不等同于 candidate key
         * @param selection 当前选择；原始成员格式错误时可为 null
         * @param candidate 已枚举候选快照；未枚举或格式错误时为 null
         * @param enumerated 当前选择是否已在候选源中枚举
         */
        public CurrentMember(long memberId, Selection selection, Candidate candidate, boolean enumerated) {
            if (memberId < 0L) throw new IllegalArgumentException("memberId must not be negative");
            if (enumerated && (selection == null || candidate == null)) {
                throw new IllegalArgumentException("enumerated member requires selection and candidate");
            }
            if (!enumerated && candidate != null) {
                throw new IllegalArgumentException("non-enumerated member must not contain candidate");
            }
            if (candidate != null && !candidate.key().equals(selection.candidateKey())) {
                throw new IllegalArgumentException("candidate key must match selection");
            }
            this.memberId = memberId;
            this.selection = selection;
            this.candidate = candidate == null ? null : copyCandidate(candidate);
            this.enumerated = enumerated;
        }

        /** @return 列表内稳定成员身份 */
        public long memberId() { return memberId; }
        /** @return 当前选择；格式错误时为 null */
        public Selection selection() { return selection; }
        /** @return 已枚举候选的不可变快照；未枚举时为 null */
        public Candidate candidate() { return candidate; }
        /** @return 当前选择是否已在候选源中枚举 */
        public boolean enumerated() { return enumerated; }
    }

    /** 去重后的完整搜索结果。 */
    public static final class SearchResult {
        private static final SearchResult EMPTY = new SearchResult(Collections.<Candidate>emptyList());
        private final List<Candidate> candidates;
        private final boolean truncated;

        /**
         * 创建完整结果快照；候选 key 重复时首项胜。
         *
         * @param candidates 原始候选
         */
        public SearchResult(List<Candidate> candidates) {
            if (candidates == null) throw new IllegalArgumentException("candidates must not be null");
            Map<String, Candidate> unique = new LinkedHashMap<String, Candidate>();
            for (Candidate candidate : candidates) {
                if (candidate == null) throw new IllegalArgumentException("candidate must not be null");
                if (unique.containsKey(candidate.key())) continue;
                unique.put(candidate.key(), copyCandidate(candidate));
            }
            this.candidates = Collections.unmodifiableList(new ArrayList<Candidate>(unique.values()));
            this.truncated = false;
        }

        /** @return 不含候选且未截断的共享空结果 */
        public static SearchResult empty() { return EMPTY; }

        /**
         * 按调用方预算创建结果快照。
         *
         * @param candidates 原始候选
         * @param maxResults 兼容参数；非负即可，结果不再截断
         * @return 去重并按预算截断的结果
         */
        public static SearchResult limitedTo(List<Candidate> candidates, int maxResults) {
            if (maxResults < 0) throw new IllegalArgumentException("maxResults must not be negative");
            return new SearchResult(candidates);
        }

        /**
         * 在保留上游截断标志的前提下按调用方预算限制当前快照。
         *
         * @param maxResults 兼容参数；非负即可
         * @return 当前完整快照
         */
        public SearchResult limitedTo(int maxResults) {
            if (maxResults < 0) throw new IllegalArgumentException("maxResults must not be negative");
            return this;
        }

        private SearchResult(List<Candidate> candidates, boolean truncated) {
            this.candidates = candidates;
            this.truncated = truncated;
        }

        /** @return 去重、截断后的只读候选 */
        public List<Candidate> candidates() { return candidates; }
        /** @return 是否存在因上限被截断的唯一候选 */
        public boolean truncated() { return truncated; }
    }

    private static Candidate copyCandidate(Candidate candidate) {
        return new Candidate(candidate.key(), candidate.label(), candidate.variants());
    }

    private static <T> List<T> immutableCopy(List<T> values, String name) {
        if (values == null) throw new IllegalArgumentException(name + " must not be null");
        ArrayList<T> copy = new ArrayList<T>(values.size());
        for (T value : values) {
            if (value == null) throw new IllegalArgumentException(name + " must not contain null");
            copy.add(value);
        }
        return Collections.unmodifiableList(copy);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException(name + " must not be empty");
        return value;
    }
}
