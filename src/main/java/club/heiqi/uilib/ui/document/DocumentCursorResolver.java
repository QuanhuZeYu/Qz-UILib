package club.heiqi.uilib.ui.document;

import java.util.EnumSet;
import java.util.List;

import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.style.UiStyleProperty;
import club.heiqi.uilib.ui.style.cascade.UiStyleDeclaration;
import club.heiqi.uilib.ui.style.cascade.UiStyleRule;
import club.heiqi.uilib.ui.style.props.UiCursor;
import club.heiqi.uilib.ui.style.selector.UiPseudoClass;
import club.heiqi.uilib.ui.style.values.UiStyleKeyword;

/**
 * 光标解析协作类。
 *
 * <p>从 {@link HtmlLikeDocumentWidget} 提取的 cursor 级联解析逻辑。负责：</p>
 * <ul>
 *     <li>沿命中链向上查找首个声明的 {@code cursor} 值</li>
 *     <li>按 important / inline / 样式表特异性级联（与 widget 中其他属性一致的级联模型）</li>
 *     <li>按当前 hover / focus / focus-visible / active / disabled 状态构造伪类集合</li>
 *     <li>{@code <a href>} 元素回退到 {@code pointer} 默认光标</li>
 * </ul>
 *
 * <p>本协作类不持有 widget 状态，所需的运行态（hovered / focused / pressed 元素和 focus-visible 标记）
 * 通过 {@link #resolve(...)} 与 {@link #buildPseudoStates(...)} 入参显式传入，便于单元测试与跨 widget 复用。</p>
 */
final class DocumentCursorResolver {

    private DocumentCursorResolver() {}

    /**
     * 沿当前 hovered 元素向上查找最近声明的光标，返回业务侧最终应用的 {@link UiCursor}。
     *
     * @param hoveredElement 当前命中元素（可为 null）
     * @param focusedElement 当前活动焦点元素（可为 null）
     * @param focusVisible 焦点是否处于 focus-visible 状态
     * @param pressedElement 当前按下元素（可为 null）
     * @param attachedToDocumentChecker 用于判断元素是否仍挂载在当前文档
     * @return 解析结果；若未声明任何光标且非 a[href] 链接，回退 {@link UiCursor#DEFAULT}
     */
    static UiCursor resolve(ElementNode hoveredElement, ElementNode focusedElement, boolean focusVisible,
            ElementNode pressedElement, AttachedChecker attachedToDocumentChecker) {
        if (hoveredElement == null || !attachedToDocumentChecker.isAttached(hoveredElement)) {
            return UiCursor.DEFAULT;
        }
        for (DocumentNode current = hoveredElement; current instanceof ElementNode; current = current.getParent()) {
            ElementNode currentElement = (ElementNode) current;
            UiCursor declaredCursor = resolveDeclaredCursor(currentElement,
                    buildPseudoStates(currentElement, hoveredElement, focusedElement, focusVisible, pressedElement));
            if (declaredCursor != null) {
                return declaredCursor;
            }
        }
        return UiCursor.DEFAULT;
    }

    /**
     * 解析单个元素声明的光标，遵循 important > inline > 后规则的级联顺序。
     */
    static UiCursor resolveDeclaredCursor(ElementNode element, java.util.Set<UiPseudoClass> activeStates) {
        if (element == null) {
            return null;
        }
        CursorCascadeValue inlineValue = readCursorCascadeValue(element.style());
        if (inlineValue != null && element.style().isImportant(UiStyleProperty.CURSOR)) {
            return inlineValue.resolveDeclaredCursor();
        }
        List<UiStyleRule> matchingRules = element.getOwnerDocument().findMatchingRules(element, activeStates);
        for (int index = matchingRules.size() - 1; index >= 0; index--) {
            UiStyleDeclaration declaration = matchingRules.get(index).getDeclaration();
            if (declaration.isImportant(UiStyleProperty.CURSOR)) {
                CursorCascadeValue cursorValue = readCursorCascadeValue(declaration);
                if (cursorValue != null) {
                    return cursorValue.resolveDeclaredCursor();
                }
            }
        }
        if (inlineValue != null) {
            return inlineValue.resolveDeclaredCursor();
        }
        for (int index = matchingRules.size() - 1; index >= 0; index--) {
            UiStyleDeclaration declaration = matchingRules.get(index).getDeclaration();
            if (!declaration.isImportant(UiStyleProperty.CURSOR)) {
                CursorCascadeValue cursorValue = readCursorCascadeValue(declaration);
                if (cursorValue != null) {
                    return cursorValue.resolveDeclaredCursor();
                }
            }
        }
        if ("a".equals(element.getTagName()) && hasLinkHref(element)) {
            return UiCursor.POINTER;
        }
        return null;
    }

    /**
     * 按当前命中、焦点、按下状态构造目标元素的伪类集合，用于驱动 cursor 级联匹配。
     */
    static java.util.Set<UiPseudoClass> buildPseudoStates(ElementNode element, ElementNode hoveredElement,
            ElementNode focusedElement, boolean focusVisible, ElementNode pressedElement) {
        EnumSet<UiPseudoClass> activeStates = EnumSet.noneOf(UiPseudoClass.class);
        if (element == null) {
            return activeStates;
        }
        if (hoveredElement != null && isAncestorOrSelf(element, hoveredElement)) {
            activeStates.add(UiPseudoClass.HOVER);
        }
        if (element == focusedElement) {
            activeStates.add(UiPseudoClass.FOCUS);
            if (focusVisible) {
                activeStates.add(UiPseudoClass.FOCUS_VISIBLE);
            }
        }
        if (element == pressedElement) {
            activeStates.add(UiPseudoClass.ACTIVE);
        }
        if (element.isDisabled()) {
            activeStates.add(UiPseudoClass.DISABLED);
        }
        return activeStates;
    }

    /**
     * 判断 ancestor 是否是 descendant 的祖先节点（或就是 descendant 本身）。
     */
    static boolean isAncestorOrSelf(ElementNode ancestor, ElementNode descendant) {
        if (ancestor == null || descendant == null) {
            return false;
        }
        for (DocumentNode current = descendant; current != null; current = current.getParent()) {
            if (current == ancestor) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLinkHref(ElementNode element) {
        if (element == null) {
            return false;
        }
        String href = element.getAttribute("href");
        return href != null && !href.trim().isEmpty();
    }

    private static CursorCascadeValue readCursorCascadeValue(UiStyleDeclaration declaration) {
        if (declaration.getCursor() != null) {
            return CursorCascadeValue.value(declaration.getCursor());
        }
        UiStyleKeyword keyword = declaration.getKeyword(UiStyleProperty.CURSOR);
        return keyword == null ? null : CursorCascadeValue.keyword(keyword);
    }

    /**
     * 检查元素是否仍挂载于当前文档，回避使用游离节点的状态查询。
     */
    interface AttachedChecker {
        boolean isAttached(ElementNode element);
    }

    /**
     * 单值或关键字两种 cursor 级联结果的载体。
     *
     * <p>{@code keyword=initial} 解析为 {@link UiCursor#DEFAULT}；其他关键字（inherit/unset）当前
     * 退到 null 由调用方继续向上查找。</p>
     */
    private static final class CursorCascadeValue {

        private final UiCursor cursor;
        private final UiStyleKeyword keyword;

        private CursorCascadeValue(UiCursor cursor, UiStyleKeyword keyword) {
            this.cursor = cursor;
            this.keyword = keyword;
        }

        private static CursorCascadeValue value(UiCursor cursor) {
            return new CursorCascadeValue(cursor, null);
        }

        private static CursorCascadeValue keyword(UiStyleKeyword keyword) {
            return new CursorCascadeValue(null, keyword);
        }

        private UiCursor resolveDeclaredCursor() {
            if (cursor != null) {
                return cursor;
            }
            if (keyword == UiStyleKeyword.INITIAL) {
                return UiCursor.DEFAULT;
            }
            return null;
        }
    }
}
