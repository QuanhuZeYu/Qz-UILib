package club.heiqi.uilib.font.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 字符字体覆盖规则集合。
 */
public final class FontCharacterRuleSet {

    private static final FontCharacterRuleSet EMPTY = new FontCharacterRuleSet(Collections.<FontCharacterRule>emptyList());

    private final List<FontCharacterRule> rules;

    private FontCharacterRuleSet(List<FontCharacterRule> rules) {
        this.rules = Collections.unmodifiableList(new ArrayList<FontCharacterRule>(rules));
    }

    /**
     * 从配置数组解析规则集合。
     *
     * @param rawRules 原始配置数组
     * @return 规则集合
     */
    public static FontCharacterRuleSet parse(String[] rawRules) {
        if (rawRules == null || rawRules.length == 0) {
            return EMPTY;
        }
        List<FontCharacterRule> parsedRules = new ArrayList<FontCharacterRule>();
        for (String rawRule : rawRules) {
            if (rawRule == null || rawRule.trim().isEmpty()) {
                continue;
            }
            parsedRules.addAll(FontCharacterRule.parse(rawRule));
        }
        if (parsedRules.isEmpty()) {
            return EMPTY;
        }
        return new FontCharacterRuleSet(parsedRules);
    }

    /**
     * 返回空规则集合。
     *
     * @return 空规则集合
     */
    public static FontCharacterRuleSet empty() {
        return EMPTY;
    }

    /**
     * 根据码点查找首个启用且有效的目标字体名。
     *
     * @param codepoint 字符码点
     * @return 字体名，未命中时返回 null
     */
    public String resolveFontName(int codepoint) {
        for (FontCharacterRule rule : rules) {
            if (rule.matches(codepoint)) {
                return rule.getFontName();
            }
        }
        return null;
    }

    /**
     * 返回规则快照。
     *
     * @return 规则快照
     */
    public List<FontCharacterRule> getRules() {
        return rules;
    }

    /**
     * 判断规则集合是否为空。
     *
     * @return 是否为空
     */
    public boolean isEmpty() {
        return rules.isEmpty();
    }
}
