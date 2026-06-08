package club.heiqi.uilib.ui.style.cascade;

import club.heiqi.uilib.ui.style.selector.UiSelector;
import club.heiqi.uilib.ui.style.UiStyleChangeImpact;
import club.heiqi.uilib.ui.style.UiStyleChangeListener;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.style.selector.UiPseudoElement;
import club.heiqi.uilib.ui.style.selector.UiPseudoClass;

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
    private final List<UiStyleChangeListener> changeListeners = new ArrayList<UiStyleChangeListener>();
    private final UiStyleChangeListener ruleDeclarationChangeListener = new UiStyleChangeListener() {
        @Override
        public void onStyleChanged(UiStyleChangeImpact impact) {
            notifyChange(impact);
        }
    };
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
        UiStyleRule rule = new UiStyleRule(selector, declaration, nextSourceOrder++);
        rule.addDeclarationChangeListener(ruleDeclarationChangeListener);
        rules.add(rule);
        notifyChange(UiStyleChangeImpact.LAYOUT);
        return this;
    }

    /**
     * 增加样式表变更监听器。
     *
     * @param listener 监听器
     * @apiNote 框架内部 API，供文档挂载样式表后监听增量变更。
     */
    public void __addChangeListener(UiStyleChangeListener listener) {
        UiStyleChangeListener resolvedListener = Objects.requireNonNull(listener, "listener");
        if (!changeListeners.contains(resolvedListener)) {
            changeListeners.add(resolvedListener);
        }
    }

    /**
     * 移除样式表变更监听器。
     *
     * @param listener 监听器
     * @apiNote 框架内部 API，供文档移除样式表时解除监听。
     */
    public void __removeChangeListener(UiStyleChangeListener listener) {
        changeListeners.remove(listener);
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
        return findMatchingRules(element, activeStates, null);
    }

    public List<UiStyleRule> findMatchingRules(ElementNode element, java.util.Set<UiPseudoClass> activeStates,
            UiPseudoElement pseudoElement) {
        if (element == null) {
            return Collections.emptyList();
        }
        List<UiStyleRule> matched = new ArrayList<UiStyleRule>();
        for (UiStyleRule rule : rules) {
            if (rule.getSelector().matches(element, activeStates, pseudoElement)) {
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
        boolean changed = !rules.isEmpty();
        for (UiStyleRule rule : rules) {
            rule.removeDeclarationChangeListener(ruleDeclarationChangeListener);
        }
        rules.clear();
        nextSourceOrder = 0;
        if (changed) {
            notifyChange(UiStyleChangeImpact.LAYOUT);
        }
        return this;
    }

    private void notifyChange(UiStyleChangeImpact impact) {
        for (UiStyleChangeListener listener : new ArrayList<UiStyleChangeListener>(changeListeners)) {
            listener.onStyleChanged(impact);
        }
    }

    @Override
    public String toString() {
        return "UiStyleSheet[rules=" + rules.size() + "]";
    }
}
