package club.heiqi.uilib.ui.style;

import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * CSS-like 样式规则：选择器 + 声明块。
 *
 * <p>一条规则描述"哪些元素"应该应用"哪些样式"。
 * 在级联计算时，规则按特异性和声明顺序参与优先级排序。</p>
 */
public final class UiStyleRule {

    private final UiSelector selector;
    private final UiStyleDeclaration declaration;
    private final int sourceOrder;

    /**
     * 创建样式规则。
     *
     * @param selector 选择器
     * @param declaration 样式声明块
     * @param sourceOrder 规则在样式表中的声明顺序（用于同特异性时的优先级判定）
     */
    public UiStyleRule(UiSelector selector, UiStyleDeclaration declaration, int sourceOrder) {
        this.selector = Objects.requireNonNull(selector, "selector");
        this.declaration = Objects.requireNonNull(declaration, "declaration");
        this.sourceOrder = sourceOrder;
    }

    /**
     * 返回规则的选择器。
     *
     * @return 选择器
     */
    public UiSelector getSelector() {
        return selector;
    }

    /**
     * 返回规则的样式声明块。
     *
     * @return 样式声明
     */
    public UiStyleDeclaration getDeclaration() {
        return declaration;
    }

    /**
     * 返回规则的声明顺序。
     *
     * <p>同特异性时，声明顺序越大优先级越高（后声明覆盖先声明）。</p>
     *
     * @return 声明顺序
     */
    public int getSourceOrder() {
        return sourceOrder;
    }

    /**
     * 判断规则是否匹配指定元素。
     *
     * @param element 目标元素
     * @return 是否匹配
     */
    public boolean matches(ElementNode element) {
        return selector.matches(element);
    }

    /**
     * 比较两条规则的优先级。
     *
     * <p>先按特异性比较，特异性相同时按声明顺序比较（后声明优先）。</p>
     *
     * @param other 另一条规则
     * @return 正数表示 this 优先级更高，负数表示 other 更高
     */
    public int comparePriority(UiStyleRule other) {
        int cmp = this.selector.compareSpecificity(other.selector);
        if (cmp != 0) return cmp;
        return Integer.compare(this.sourceOrder, other.sourceOrder);
    }

    @Override
    public String toString() {
        return selector.toString() + " { ... }";
    }
}
