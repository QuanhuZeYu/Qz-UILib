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
    public enum SelectionMode { ALL, SINGLE, MULTIPLE }

    /** 结果硬上限，避免无预算列表进入 UI。 */
    public static final int MAX_RESULTS = 64;

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
            this.variants = immutableCopy(variants, "variants");
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

        /** 兼容旧契约：null 映射 ALL，非 null 映射 SINGLE。 */
        public Selection(String candidateKey, String variantKey) {
            this(candidateKey, variantKey == null ? SelectionMode.ALL : SelectionMode.SINGLE,
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
            if (mode == SelectionMode.SINGLE && copy.size() != 1) {
                throw new IllegalArgumentException("SINGLE selection must contain exactly one variant key");
            }
            if (mode == SelectionMode.MULTIPLE && copy.size() < 2) {
                throw new IllegalArgumentException("MULTIPLE selection must contain at least two variant keys");
            }
            this.variantKeys = Collections.unmodifiableList(copy);
        }

        /** @return 候选 key */
        public String candidateKey() { return candidateKey; }
        /** @return 选择模式 */
        public SelectionMode mode() { return mode; }
        /** @return 按候选变体顺序排列的只读 key */
        public List<String> variantKeys() { return variantKeys; }
        /** @return 单选变体 key；ALL 为 null，MULTIPLE 会快速失败 */
        public String variantKey() {
            if (mode == SelectionMode.MULTIPLE) {
                throw new IllegalStateException("variantKey is unavailable for MULTIPLE selection");
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

    /** 去重、截断后的搜索结果。 */
    public static final class SearchResult {
        private static final SearchResult EMPTY = new SearchResult(Collections.<Candidate>emptyList());
        private final List<Candidate> candidates;
        private final boolean truncated;

        /**
         * 创建结果快照；候选 key 重复时首项胜，最多保留 64 项。
         *
         * @param candidates 原始候选
         */
        public SearchResult(List<Candidate> candidates) {
            if (candidates == null) throw new IllegalArgumentException("candidates must not be null");
            Map<String, Candidate> unique = new LinkedHashMap<String, Candidate>();
            boolean overflow = false;
            for (Candidate candidate : candidates) {
                if (candidate == null) throw new IllegalArgumentException("candidate must not be null");
                if (unique.containsKey(candidate.key())) continue;
                if (unique.size() == MAX_RESULTS) {
                    overflow = true;
                    continue;
                }
                unique.put(candidate.key(), copyCandidate(candidate));
            }
            this.candidates = Collections.unmodifiableList(new ArrayList<Candidate>(unique.values()));
            this.truncated = overflow;
        }

        /** @return 不含候选且未截断的共享空结果 */
        public static SearchResult empty() { return EMPTY; }

        /**
         * 按调用方预算创建结果快照。
         *
         * @param candidates 原始候选
         * @param maxResults 最大保留数，范围 0..64
         * @return 去重并按预算截断的结果
         */
        public static SearchResult limitedTo(List<Candidate> candidates, int maxResults) {
            if (maxResults < 0 || maxResults > MAX_RESULTS) {
                throw new IllegalArgumentException("maxResults must be between 0 and 64");
            }
            if (candidates == null) throw new IllegalArgumentException("candidates must not be null");
            ArrayList<Candidate> limited = new ArrayList<Candidate>();
            Map<String, Boolean> seen = new LinkedHashMap<String, Boolean>();
            boolean overflow = false;
            for (Candidate candidate : candidates) {
                if (candidate == null) throw new IllegalArgumentException("candidate must not be null");
                if (seen.put(candidate.key(), Boolean.TRUE) != null) continue;
                if (limited.size() < maxResults) limited.add(candidate); else overflow = true;
            }
            SearchResult result = new SearchResult(limited);
            return new SearchResult(result.candidates, overflow);
        }

        /**
         * 在保留上游截断标志的前提下按调用方预算限制当前快照。
         *
         * @param maxResults 最大保留数，范围 0..64
         * @return 受预算限制且保留上游截断语义的新快照
         */
        public SearchResult limitedTo(int maxResults) {
            SearchResult limited = limitedTo(candidates, maxResults);
            return new SearchResult(limited.candidates, truncated || limited.truncated);
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
