package club.heiqi.uilib.internal.devtools.pages;

import java.util.List;

import club.heiqi.uilib.ui.document.HtmlLikeDocumentWidget;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.DocumentNodeType;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;

/**
 * `/qzuilib test` DOM 分组自动断言。
 */
final class UiTestDomAssertionRunner {

    /**
     * 判断指定样例是否具备自动断言。
     *
     * @param caseId 样例编号
     * @return 是否自动断言样例
     */
    boolean isAutomatic(String caseId) {
        return "VIS-DOM-001".equals(caseId)
                || "VIS-DOM-002".equals(caseId)
                || "VIS-DOM-003".equals(caseId)
                || "VIS-DOM-004".equals(caseId)
                || "VIS-DOM-005".equals(caseId)
                || "VIS-DOM-006".equals(caseId)
                || "VIS-DOM-007".equals(caseId);
    }

    /**
     * 执行 DOM 自动断言。
     *
     * @param widget 文档组件
     * @param scope 样例舞台
     * @param testCase 样例规格
     * @param diagnostics 诊断摘要
     * @return 是否通过
     */
    boolean runAutomatic(HtmlLikeDocumentWidget widget, ElementNode scope, UiTestCaseSpec testCase,
            List<String> diagnostics) {
        ensureWidgetBounds(widget);
        String id = testCase.getId();
        if ("VIS-DOM-001".equals(id)) {
            return assertAppendInsert(scope, diagnostics);
        }
        if ("VIS-DOM-002".equals(id)) {
            return assertReplaceRemove(scope, diagnostics);
        }
        if ("VIS-DOM-003".equals(id)) {
            return assertFragment(scope, diagnostics);
        }
        if ("VIS-DOM-004".equals(id)) {
            return assertTextContent(scope, diagnostics);
        }
        if ("VIS-DOM-005".equals(id)) {
            return assertClassList(scope, diagnostics);
        }
        if ("VIS-DOM-006".equals(id)) {
            return assertSelector(widget, scope, diagnostics);
        }
        if ("VIS-DOM-007".equals(id)) {
            return assertLinkDefault(widget, scope, diagnostics);
        }
        diagnostics.add("未知 DOM 自动样例：" + id);
        return false;
    }

    private boolean assertAppendInsert(ElementNode scope, List<String> diagnostics) {
        ElementNode list = findByRole(scope, "append-insert-list");
        ElementNode a = findByRole(scope, "append-insert-a");
        ElementNode c = findByRole(scope, "append-insert-c");
        ElementNode log = findByRole(scope, "append-insert-log");
        if (list == null || a == null || c == null || log == null) {
            diagnostics.add("append/insert 节点缺失");
            return false;
        }
        String order = collectChildText(list);
        String logText = log.getTextContent();
        diagnostics.add("appendInsertOrder=" + order);
        diagnostics.add("appendInsertLog=" + logText);
        diagnostics.add("appendInsertDiff=expected appendReturn=A, insertReturn=A and final order B,A,C");
        return "B,A,C".equals(order)
                && a.getParent() == list
                && c.getPreviousSibling() == a
                && logText.contains("appendReturn=A")
                && logText.contains("insertReturn=A")
                && logText.contains("final=B,A,C");
    }

    private boolean assertReplaceRemove(ElementNode scope, List<String> diagnostics) {
        ElementNode list = findByRole(scope, "replace-remove-list");
        ElementNode replacement = findByRole(scope, "replace-remove-new");
        ElementNode log = findByRole(scope, "replace-remove-log");
        if (list == null || replacement == null || log == null) {
            diagnostics.add("replace/remove 节点缺失");
            return false;
        }
        String order = collectChildText(list);
        String logText = log.getTextContent();
        diagnostics.add("replaceRemoveOrder=" + order);
        diagnostics.add("replaceRemoveLog=" + logText);
        diagnostics.add("replaceRemoveDiff=expected replaceReturn=old, removeReturn=remove, final order new,keep");
        return "new,keep".equals(order)
                && replacement.getParent() == list
                && "null".equals(list.getAttribute("data-old-parent"))
                && "null".equals(list.getAttribute("data-removed-parent"))
                && logText.contains("replaceReturn=old")
                && logText.contains("removeReturn=remove")
                && logText.contains("oldParent=null")
                && logText.contains("removedParent=null");
    }

    private boolean assertFragment(ElementNode scope, List<String> diagnostics) {
        ElementNode target = findByRole(scope, "fragment-target");
        ElementNode log = findByRole(scope, "fragment-log");
        if (target == null || log == null) {
            diagnostics.add("fragment 节点缺失");
            return false;
        }
        String order = collectChildText(target);
        String logText = log.getTextContent();
        diagnostics.add("fragmentOrder=" + order);
        diagnostics.add("fragmentLog=" + logText);
        diagnostics.add("fragmentDiff=expected fragmentCount=0 and target children F1,F2,F3");
        return target.getChildCount() == 3
                && "F1,F2,F3".equals(order)
                && "0".equals(target.getAttribute("data-fragment-count"))
                && logText.contains("fragmentCount=0");
    }

    private boolean assertTextContent(ElementNode scope, List<String> diagnostics) {
        ElementNode target = findByRole(scope, "textcontent-target");
        ElementNode log = findByRole(scope, "textcontent-log");
        if (target == null || log == null) {
            diagnostics.add("textContent 节点缺失");
            return false;
        }
        DocumentNode firstChild = target.getFirstChild();
        diagnostics.add("textContentValue=" + target.getTextContent());
        diagnostics.add("textContentChildCount=" + target.getChildCount());
        diagnostics.add("textContentLog=" + log.getTextContent());
        diagnostics.add("textContentDiff=expected single text node with textContent 已替换");
        return target.getChildCount() == 1
                && firstChild != null
                && firstChild.getNodeType() == DocumentNodeType.TEXT
                && "textContent 已替换".equals(target.getTextContent())
                && log.getTextContent().contains("childCount=1");
    }

    private boolean assertClassList(ElementNode scope, List<String> diagnostics) {
        ElementNode card = findByRole(scope, "classlist-card");
        ElementNode log = findByRole(scope, "classlist-log");
        if (card == null || log == null) {
            diagnostics.add("classList 节点缺失");
            return false;
        }
        String className = card.getClassName();
        diagnostics.add("classListClassName=" + className);
        diagnostics.add("classListLog=" + log.getTextContent());
        diagnostics.add("classListDiff=expected active removed, vis-dom-selected toggled on and contains true");
        return card.getClassList().contains("vis-dom-token")
                && !card.getClassList().contains("active")
                && card.getClassList().contains("vis-dom-selected")
                && "true".equals(card.getAttribute("data-active-before"))
                && "true".equals(card.getAttribute("data-toggle-selected"))
                && "true".equals(card.getAttribute("data-contains-selected"));
    }

    private boolean assertSelector(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode target = widget.getDocument().querySelector(".vis-dom-selector-target");
        List<ElementNode> items = widget.getDocument().querySelectorAll(".vis-dom-selector-item");
        ElementNode log = findByRole(scope, "selector-log");
        diagnostics.add("selectorTarget=" + describeElement(target));
        diagnostics.add("selectorAllCount=" + items.size());
        diagnostics.add("selectorOrder=" + collectElementIds(items));
        diagnostics.add("selectorLog=" + (log == null ? "null" : log.getTextContent()));
        diagnostics.add("selectorDiff=expected target id vis-dom-selector-target and item order first,target,third");
        return target != null
                && "vis-dom-selector-target".equals(target.getId())
                && items.size() == 3
                && "vis-dom-selector-first".equals(items.get(0).getId())
                && "vis-dom-selector-target".equals(items.get(1).getId())
                && "vis-dom-selector-third".equals(items.get(2).getId());
    }

    private boolean assertLinkDefault(HtmlLikeDocumentWidget widget, ElementNode scope, List<String> diagnostics) {
        ElementNode link = findByRole(scope, "link-anchor");
        ElementNode log = findByRole(scope, "link-log");
        if (link == null || log == null) {
            diagnostics.add("link default 节点缺失");
            return false;
        }
        clickElement(widget, link, 71L);
        String logText = log.getTextContent();
        diagnostics.add("linkHref=" + link.getAttribute("href"));
        diagnostics.add("linkFocusable=" + link.isFocusable());
        diagnostics.add("linkLog=" + logText);
        diagnostics.add("linkDefaultDiff=expected a[href] click dispatches document link activation with href");
        return link.isFocusable()
                && "https://example.test/qz-dom".equals(link.getAttribute("href"))
                && logText.contains("activated:https://example.test/qz-dom");
    }

    private void clickElement(HtmlLikeDocumentWidget widget, ElementNode element, long timeNanos) {
        int[] center = resolveElementCenter(widget, element);
        widget.onMouseDown(new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, center[0], center[1], 0, 0, 0, 0,
                timeNanos));
        widget.onMouseUp(new UiMouseEvent(UiMouseEvent.Action.BUTTON_UP, center[0], center[1], 0, 0, 0, 0,
                timeNanos + 1L));
    }

    private int[] resolveElementCenter(HtmlLikeDocumentWidget widget, ElementNode element) {
        DocumentLayoutBox box = resolveBox(widget, element);
        int x = widget.getAbsoluteX() + box.getLeft() + Math.max(1, box.getWidth() / 2);
        int y = widget.getAbsoluteY() + box.getTop() + Math.max(1, box.getHeight() / 2);
        return new int[] { x, y };
    }

    private DocumentLayoutBox resolveBox(HtmlLikeDocumentWidget widget, ElementNode element) {
        DocumentLayoutBox box = findLayoutBox(widget.resolveLayoutBoxForTest(), element);
        if (box == null) {
            throw new IllegalStateException("未找到 DOM 样例布局盒: " + element.getTagName());
        }
        return box;
    }

    private DocumentLayoutBox findLayoutBox(DocumentLayoutBox current, ElementNode element) {
        if (current == null || element == null) {
            return null;
        }
        if (current.getElement() == element) {
            return current;
        }
        for (DocumentLayoutBox child : current.getChildren()) {
            DocumentLayoutBox found = findLayoutBox(child, element);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private ElementNode findByRole(ElementNode current, String role) {
        if (current == null || role == null) {
            return null;
        }
        if (role.equals(current.getAttribute(UiTestDomVisualFactory.ROLE_ATTRIBUTE))) {
            return current;
        }
        for (DocumentNode child : current.getChildren()) {
            if (child instanceof ElementNode) {
                ElementNode found = findByRole((ElementNode) child, role);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private String collectChildText(ElementNode parent) {
        StringBuilder builder = new StringBuilder();
        for (DocumentNode child : parent.getChildren()) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(child.getTextContent());
        }
        return builder.toString();
    }

    private String collectElementIds(List<ElementNode> elements) {
        StringBuilder builder = new StringBuilder();
        for (ElementNode element : elements) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(element.getId());
        }
        return builder.toString();
    }

    private String describeElement(ElementNode element) {
        if (element == null) {
            return "null";
        }
        return element.getTagName() + "#" + element.getId() + "." + element.getClassName();
    }

    private void ensureWidgetBounds(HtmlLikeDocumentWidget widget) {
        if (widget.getWidth() <= 0 || widget.getHeight() <= 0) {
            widget.applyLayoutBounds(0, 0, 760, 520);
        }
    }
}
