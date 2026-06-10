package club.heiqi.uilib.config;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.font.config.FontCharacterRule;
import net.minecraftforge.common.config.Property;

/**
 * 字符字体覆盖规则草稿的解析与校验工具。
 */
final class FontCharacterRuleDrafts {

    private FontCharacterRuleDrafts() {}

    /**
     * 从远程草稿文本拆分字符字体规则。
     *
     * @param draftValue 草稿文本
     * @return 规则数组
     */
    static String[] splitDraftValue(String draftValue) {
        return ForgeConfigTemplatePropertyDrafts.splitListDraft(draftValue);
    }

    /**
     * 校验远程同步中的字符字体规则草稿。
     *
     * @param property Forge 配置属性
     * @param draftValue 草稿文本
     * @return 错误文本，合法时返回 null
     */
    static String validateRemoteDraft(Property property, String draftValue) {
        if (property == null) {
            return "属性不存在。";
        }
        if (!property.isList()) {
            return "字符字体规则必须是列表配置。";
        }
        return validateRules(toList(splitDraftValue(draftValue)));
    }

    /**
     * 校验字符字体规则列表。
     *
     * @param rules 规则文本列表
     * @return 错误文本，合法时返回 null
     */
    static String validateRules(List<String> rules) {
        List<String> normalizedRules = normalizeRules(rules);
        for (int index = 0; index < normalizedRules.size(); index++) {
            FontCharacterRule rule = FontCharacterRule.parse(normalizedRules.get(index));
            if (!rule.isValid()) {
                return "第 " + (index + 1) + " 条规则无效：" + rule.getErrorMessage();
            }
        }
        return null;
    }

    /**
     * 返回首个启用规则重叠提示。
     *
     * @param rules 规则文本列表
     * @return 提示文本，无重叠时返回 null
     */
    static String findOverlapWarning(List<String> rules) {
        List<IndexedRule> parsedRules = new ArrayList<IndexedRule>();
        List<String> normalizedRules = normalizeRules(rules);
        for (int index = 0; index < normalizedRules.size(); index++) {
            FontCharacterRule rule = FontCharacterRule.parse(normalizedRules.get(index));
            if (rule.isActive()) {
                parsedRules.add(new IndexedRule(index, rule));
            }
        }
        for (int leftIndex = 0; leftIndex < parsedRules.size(); leftIndex++) {
            IndexedRule left = parsedRules.get(leftIndex);
            for (int rightIndex = leftIndex + 1; rightIndex < parsedRules.size(); rightIndex++) {
                IndexedRule right = parsedRules.get(rightIndex);
                if (rangesOverlap(left.rule, right.rule)) {
                    return "第 " + (left.originalIndex + 1) + " 条规则与第 "
                            + (right.originalIndex + 1) + " 条规则范围重叠，运行时会优先使用靠前规则";
                }
            }
        }
        return null;
    }

    private static List<String> normalizeRules(List<String> rules) {
        List<String> normalizedRules = new ArrayList<String>();
        if (rules == null) {
            return normalizedRules;
        }
        for (String rule : rules) {
            String normalized = rule == null ? "" : rule.trim();
            if (!normalized.isEmpty()) {
                normalizedRules.add(normalized);
            }
        }
        return normalizedRules;
    }

    private static List<String> toList(String[] values) {
        List<String> list = new ArrayList<String>();
        if (values == null) {
            return list;
        }
        for (String value : values) {
            list.add(value);
        }
        return list;
    }

    private static boolean rangesOverlap(FontCharacterRule left, FontCharacterRule right) {
        return Math.max(left.getStartCodepoint(), right.getStartCodepoint())
                <= Math.min(left.getEndCodepoint(), right.getEndCodepoint());
    }

    private static final class IndexedRule {

        private final int originalIndex;
        private final FontCharacterRule rule;

        private IndexedRule(int originalIndex, FontCharacterRule rule) {
            this.originalIndex = originalIndex;
            this.rule = rule;
        }
    }
}
