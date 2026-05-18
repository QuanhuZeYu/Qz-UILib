package club.heiqi.uilib.ui.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * CSS-like 样式表容器。
 *
 * <p>一个样式表包含有序的样式规则列表。文档可以挂载多个样式表，
 * 按挂载顺序参与级联计算。</p>
 *
 * <p>样式表提供 builder 模式，方便链式声明规则：</p>
 * <pre>{@code
 * UiStyleSheet sheet = UiStyleSheet.create()
 *     .addRule(".header", new UiStyleDeclaration()
 *         .setBackgroundColor(0xFF333333)
 *         .setPadding(UiStyleLength.px(8)))
 *     .addRule("#title", new UiStyleDeclaration()
 *         .setTextColor(0xFFFFFF00));
 * }</pre>
 */
public final class UiStyleSheet {

    private final List<UiStyleRule> rules = new ArrayList<UiStyleRule>();
    private int nextSourceOrder = 0;

    private UiStyleSheet() {}

    /**
     * 创建空样式表。
     *
     * @return 样式表实例
     */
    public static UiStyleSheet create() {
        return new UiStyleSheet();
    }

    /**
     * 添加一条样式规则。
     *
     * @param selector 选择器
     * @param declaration 样式声明块
     * @return 当前样式表（链式调用）
     */
    public UiStyleSheet addRule(UiSelector selector, UiStyleDeclaration declaration) {
        Objects.requireNonNull(selector, "selector");
        Objects.requireNonNull(declaration, "declaration");
        rules.add(new UiStyleRule(selector, declaration, nextSourceOrder++));
        return this;
    }

    /**
     * 通过选择器文本添加一条样式规则。
     *
     * <p>选择器文本会通过 {@link UiSelector#parse(String)} 解析。</p>
     *
     * @param selectorText 选择器文本（如 ".my-class"、"div#id"）
     * @param declaration 样式声明块
     * @return 当前样式表（链式调用）
     */
    public UiStyleSheet addRule(String selectorText, UiStyleDeclaration declaration) {
        return addRule(UiSelector.parse(selectorText), declaration);
    }

    /**
     * 返回所有规则的只读列表。
     *
     * @return 规则列表
     */
    public List<UiStyleRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    /**
     * 返回规则数量。
     *
     * @return 规则数量
     */
    public int getRuleCount() {
        return rules.size();
    }

    /**
     * 查找所有匹配指定元素的规则，按优先级升序排列。
     *
     * <p>返回的列表按特异性和声明顺序排序，最后一个元素优先级最高。
     * 调用方在合并属性时应按正序遍历，后遍历到的属性覆盖先遍历到的。</p>
     *
     * @param element 目标元素
     * @return 匹配规则列表（按优先级升序）
     */
    public List<UiStyleRule> findMatchingRules(ElementNode element) {
        return findMatchingRules(element, null);
    }

    /**
     * 查找所有匹配指定元素的规则（考虑伪类状态），按优先级升序排列。
     *
     * @param element 目标元素
     * @param activeStates 元素当前激活的伪类状态集合；为 null 时伪类选择器不匹配
     * @return 匹配规则列表（按优先级升序）
     */
    public List<UiStyleRule> findMatchingRules(ElementNode element, java.util.Set<UiPseudoClass> activeStates) {
        if (element == null) {
            return Collections.emptyList();
        }
        List<UiStyleRule> matched = new ArrayList<UiStyleRule>();
        for (UiStyleRule rule : rules) {
            if (rule.getSelector().matches(element, activeStates)) {
                matched.add(rule);
            }
        }
        if (matched.size() > 1) {
            Collections.sort(matched, new java.util.Comparator<UiStyleRule>() {
                @Override
                public int compare(UiStyleRule a, UiStyleRule b) {
                    return a.comparePriority(b);
                }
            });
        }
        return matched;
    }

    /**
     * 移除所有规则。
     *
     * @return 当前样式表（链式调用）
     */
    public UiStyleSheet clear() {
        rules.clear();
        nextSourceOrder = 0;
        return this;
    }

    @Override
    public String toString() {
        return "UiStyleSheet[rules=" + rules.size() + "]";
    }
}
