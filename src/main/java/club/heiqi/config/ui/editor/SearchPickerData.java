package club.heiqi.config.ui.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 搜索选择器的平台无关不可变数据契约。 */
public final class SearchPickerData {
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
        private final String variantKey;

        /** 创建选择；variantKey 可为 null。 */
        public Selection(String candidateKey, String variantKey) {
            this.candidateKey = requireText(candidateKey, "candidate key");
            this.variantKey = variantKey == null ? null : requireText(variantKey, "variant key");
        }

        /** @return 候选 key */
        public String candidateKey() { return candidateKey; }
        /** @return 变体 key；未选择时为 null */
        public String variantKey() { return variantKey; }
    }

    /** 去重、截断后的搜索结果。 */
    public static final class SearchResult {
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
