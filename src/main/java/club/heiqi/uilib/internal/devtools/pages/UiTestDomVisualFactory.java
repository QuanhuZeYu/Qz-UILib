package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.dom.DocumentFragmentNode;
import club.heiqi.uilib.ui.dom.DocumentLinkActivationEvent;
import club.heiqi.uilib.ui.dom.DocumentLinkActivationHandler;
import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.style.props.UiAlignItems;
import club.heiqi.uilib.ui.style.props.UiBorderStyle;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiFlexDirection;
import club.heiqi.uilib.ui.style.props.UiFlexWrap;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.props.UiJustifyContent;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * `/qzuilib test` DOM 分组视觉样例工厂。
 */
final class UiTestDomVisualFactory {

    static final String ROLE_ATTRIBUTE = "data-ui-dom-role";
    private static final String TOKEN_ATTRIBUTE = "data-ui-dom-token";

    /**
     * 判断是否支持指定 DOM 样例。
     *
     * @param caseId 样例编号
     * @return 是否支持
     */
    boolean supports(String caseId) {
        return "VIS-DOM-001".equals(caseId)
                || "VIS-DOM-002".equals(caseId)
                || "VIS-DOM-003".equals(caseId)
                || "VIS-DOM-004".equals(caseId)
                || "VIS-DOM-005".equals(caseId)
                || "VIS-DOM-006".equals(caseId)
                || "VIS-DOM-007".equals(caseId);
    }

    /**
     * 追加 DOM 样例视觉舞台。
     *
     * @param document 文档实例
     * @param stage 样例舞台
     * @param testCase 样例规格
     */
    void appendCaseDemo(UiDocument document, ElementNode stage, UiTestCaseSpec testCase) {
        String id = testCase.getId();
        if ("VIS-DOM-001".equals(id)) {
            appendAppendInsertDemo(document, stage);
        } else if ("VIS-DOM-002".equals(id)) {
            appendReplaceRemoveDemo(document, stage);
        } else if ("VIS-DOM-003".equals(id)) {
            appendFragmentDemo(document, stage);
        } else if ("VIS-DOM-004".equals(id)) {
            appendTextContentDemo(document, stage);
        } else if ("VIS-DOM-005".equals(id)) {
            appendClassListDemo(document, stage);
        } else if ("VIS-DOM-006".equals(id)) {
            appendSelectorDemo(document, stage);
        } else if ("VIS-DOM-007".equals(id)) {
            appendLinkDefaultDemo(document, stage);
        }
    }

    private void appendAppendInsertDemo(UiDocument document, ElementNode stage) {
        ElementNode panel = createStack(document);
        ElementNode list = createDomList(document, "append-insert-list");
        ElementNode a = createDomChip(document, "append-insert-a", "A", 0xFF2563EB);
        ElementNode b = createDomChip(document, "append-insert-b", "B", 0xFF0F766E);
        ElementNode c = createDomChip(document, "append-insert-c", "C", 0xFF7C3AED);
        list.append(a).append(b).append(c);
        DocumentNode appendReturn = list.appendChild(a);
        DocumentNode insertReturn = list.insertBefore(a, c);
        String finalOrder = collectDomTokens(list);
        list.setAttribute("data-final-order", finalOrder);
        panel.append(list);
        panel.append(createLogLine(document, "append-insert-log", "appendReturn=" + tokenOf(appendReturn)
                + "; insertReturn=" + tokenOf(insertReturn) + "; final=" + finalOrder));
        stage.append(panel);
        appendMutedText(document, stage, "appendChild 移动已有节点到末尾，insertBefore 再把 A 移到 C 前。 ");
    }

    private void appendReplaceRemoveDemo(UiDocument document, ElementNode stage) {
        ElementNode panel = createStack(document);
        ElementNode list = createDomList(document, "replace-remove-list");
        ElementNode old = createDomChip(document, "replace-remove-old", "old", 0xFFDC2626);
        ElementNode keep = createDomChip(document, "replace-remove-keep", "keep", 0xFF2563EB);
        ElementNode remove = createDomChip(document, "replace-remove-target", "remove", 0xFF7C2D12);
        ElementNode replacement = createDomChip(document, "replace-remove-new", "new", 0xFF059669);
        list.append(old).append(keep).append(remove);
        DocumentNode replaceReturn = list.replaceChild(replacement, old);
        DocumentNode removeReturn = list.removeChild(remove);
        String finalOrder = collectDomTokens(list);
        list.setAttribute("data-final-order", finalOrder);
        list.setAttribute("data-replace-return", tokenOf(replaceReturn));
        list.setAttribute("data-remove-return", tokenOf(removeReturn));
        list.setAttribute("data-old-parent", old.getParent() == null ? "null" : "attached");
        list.setAttribute("data-removed-parent", remove.getParent() == null ? "null" : "attached");
        panel.append(list);
        panel.append(createLogLine(document, "replace-remove-log", "replaceReturn=" + tokenOf(replaceReturn)
                + "; removeReturn=" + tokenOf(removeReturn) + "; final=" + finalOrder
                + "; oldParent=" + list.getAttribute("data-old-parent")
                + "; removedParent=" + list.getAttribute("data-removed-parent")));
        stage.append(panel);
        appendMutedText(document, stage, "replaceChild 返回旧节点，removeChild 返回被移除节点，二者均脱离父节点。 ");
    }

    private void appendFragmentDemo(UiDocument document, ElementNode stage) {
        ElementNode panel = createStack(document);
        ElementNode list = createDomList(document, "fragment-target");
        DocumentFragmentNode fragment = document.createDocumentFragment();
        fragment.appendChild(createDomChip(document, "fragment-a", "F1", 0xFF2563EB));
        fragment.appendChild(createDomChip(document, "fragment-b", "F2", 0xFF059669));
        fragment.appendChild(createDomChip(document, "fragment-c", "F3", 0xFFF59E0B));
        list.appendChild(fragment);
        String finalOrder = collectDomTokens(list);
        list.setAttribute("data-final-order", finalOrder);
        list.setAttribute("data-fragment-count", String.valueOf(fragment.getChildCount()));
        panel.append(list);
        panel.append(createLogLine(document, "fragment-log", "fragmentCount=" + fragment.getChildCount()
                + "; target=" + finalOrder));
        stage.append(panel);
        appendMutedText(document, stage, "DocumentFragment 自身不进入最终树，插入后子节点展开且 fragment 清空。 ");
    }

    private void appendTextContentDemo(UiDocument document, ElementNode stage) {
        ElementNode panel = createStack(document);
        ElementNode target = createDomPanel(document, "textcontent-target", 0xFF1E293B);
        target.append(createDomChip(document, "textcontent-old-a", "old span", 0xFF7C2D12));
        target.append(createDomChip(document, "textcontent-old-b", "old child", 0xFF7C3AED));
        target.setTextContent("textContent 已替换");
        panel.append(target);
        panel.append(createLogLine(document, "textcontent-log", "childCount=" + target.getChildCount()
                + "; text=" + target.getTextContent()));
        stage.append(panel);
        appendMutedText(document, stage, "setTextContent 移除旧子树，只保留一个纯文本节点。 ");
    }

    private void appendClassListDemo(UiDocument document, ElementNode stage) {
        ElementNode panel = createStack(document);
        ElementNode card = createDomPanel(document, "classlist-card", 0xFF0F766E);
        card.appendText("classList token card");
        card.getClassList().add("vis-dom-token", "active");
        boolean containsActiveBefore = card.getClassList().contains("active");
        card.getClassList().remove("active");
        boolean toggleSelected = card.getClassList().toggle("vis-dom-selected");
        boolean containsSelected = card.getClassList().contains("vis-dom-selected");
        card.setAttribute("data-active-before", String.valueOf(containsActiveBefore));
        card.setAttribute("data-toggle-selected", String.valueOf(toggleSelected));
        card.setAttribute("data-contains-selected", String.valueOf(containsSelected));
        panel.append(card);
        panel.append(createLogLine(document, "classlist-log", "className=" + card.getClassName()
                + "; activeBefore=" + containsActiveBefore + "; selected=" + containsSelected));
        stage.append(panel);
        appendMutedText(document, stage, "add/remove/toggle/contains 同步 className，样式重算由 token 变更触发。 ");
    }

    private void appendSelectorDemo(UiDocument document, ElementNode stage) {
        ElementNode panel = createStack(document);
        ElementNode selectorScope = createDomList(document, "selector-scope");
        ElementNode first = createDomChip(document, "selector-first", "S1 .item", 0xFF334155);
        first.setId("vis-dom-selector-first");
        first.setClassName("vis-dom-selector-item");
        ElementNode target = createDomChip(document, "selector-target", "S2 .target", 0xFF059669);
        target.setId("vis-dom-selector-target");
        target.setClassName("vis-dom-selector-item vis-dom-selector-target");
        ElementNode third = createDomChip(document, "selector-third", "S3 .item", 0xFF2563EB);
        third.setId("vis-dom-selector-third");
        third.setClassName("vis-dom-selector-item");
        selectorScope.append(first).append(target).append(third);
        panel.append(selectorScope);
        panel.append(createLogLine(document, "selector-log",
                "querySelector=.vis-dom-selector-target; querySelectorAll=.vis-dom-selector-item x3"));
        stage.append(panel);
        appendMutedText(document, stage, "selector 断言在样例挂载后读取 document.querySelector 与 querySelectorAll 顺序。 ");
    }

    private void appendLinkDefaultDemo(final UiDocument document, ElementNode stage) {
        ElementNode panel = createStack(document);
        ElementNode link = document.a();
        link.setAttribute(ROLE_ATTRIBUTE, "link-anchor");
        link.setAttribute("href", "https://example.test/qz-dom");
        link.style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setWidth(UiStyleLength.px(228))
                .setHeight(UiStyleLength.px(34))
                .setPadding(UiStyleLength.px(8))
                .setBackgroundColor(0xFF1D4ED8)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF93C5FD)
                .setBorderRadius(UiStyleLength.px(8))
                .setFontWeight(UiFontWeight.BOLD)
                .setTextColor(0xFFFFFFFF);
        link.appendText("Docs link default");
        final ElementNode log = createLogLine(document, "link-log", "等待点击链接默认行为");
        document.setLinkActivationHandler(new DocumentLinkActivationHandler() {
            @Override
            public void onLinkActivated(DocumentLinkActivationEvent event) {
                log.setTextContent("activated:" + event.getHref());
            }
        });
        panel.append(link).append(log);
        stage.append(panel);
        appendMutedText(document, stage, "a[href] 具备默认激活行为，点击后回调写入 href 日志。 ");
    }

    private ElementNode createStack(UiDocument document) {
        ElementNode stack = document.div();
        stack.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setRowGap(UiStyleLength.px(7));
        return stack;
    }

    private ElementNode createDomList(UiDocument document, String role) {
        ElementNode list = createDomPanel(document, role, 0xFF020617);
        list.style()
                .setDisplay(UiDisplay.FLEX)
                .setFlexDirection(UiFlexDirection.ROW)
                .setFlexWrap(UiFlexWrap.WRAP)
                .setAlignItems(UiAlignItems.CENTER)
                .setColumnGap(UiStyleLength.px(8))
                .setRowGap(UiStyleLength.px(8));
        return list;
    }

    private ElementNode createDomPanel(UiDocument document, String role, int color) {
        ElementNode panel = document.div();
        panel.setAttribute(ROLE_ATTRIBUTE, role);
        panel.style()
                .setMinWidth(UiStyleLength.px(220))
                .setPadding(UiStyleLength.px(9))
                .setBackgroundColor(color)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF475569)
                .setBorderRadius(UiStyleLength.px(9))
                .setTextColor(0xFFEAF1FF);
        return panel;
    }

    private ElementNode createDomChip(UiDocument document, String role, String token, int color) {
        ElementNode chip = document.div();
        chip.setAttribute(ROLE_ATTRIBUTE, role);
        chip.setAttribute(TOKEN_ATTRIBUTE, token);
        chip.style()
                .setDisplay(UiDisplay.FLEX)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.CENTER)
                .setMinWidth(UiStyleLength.px(54))
                .setHeight(UiStyleLength.px(30))
                .setPadding(UiStyleLength.px(6))
                .setBackgroundColor(color)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF93C5FD)
                .setBorderRadius(UiStyleLength.px(8))
                .setFontWeight(UiFontWeight.BOLD)
                .setTextColor(0xFFFFFFFF);
        chip.appendText(token);
        return chip;
    }

    private ElementNode createLogLine(UiDocument document, String role, String text) {
        ElementNode line = document.div();
        line.setAttribute(ROLE_ATTRIBUTE, role);
        line.style()
                .setPadding(UiStyleLength.px(6))
                .setBackgroundColor(0xAA0F172A)
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderStyle(UiBorderStyle.SOLID)
                .setBorderColor(0xFF334155)
                .setBorderRadius(UiStyleLength.px(6))
                .setTextColor(0xFFEAF1FF);
        line.appendText(text);
        return line;
    }

    private String collectDomTokens(ElementNode parent) {
        StringBuilder builder = new StringBuilder();
        for (DocumentNode child : parent.getChildren()) {
            if (builder.length() > 0) {
                builder.append(',');
            }
            builder.append(tokenOf(child));
        }
        return builder.toString();
    }

    private String tokenOf(DocumentNode node) {
        if (node instanceof ElementNode) {
            String token = ((ElementNode) node).getAttribute(TOKEN_ATTRIBUTE);
            if (token != null) {
                return token;
            }
            String id = ((ElementNode) node).getId();
            return id == null ? ((ElementNode) node).getTagName() : id;
        }
        return node == null ? "null" : node.getNodeType().name();
    }

    private void appendMutedText(UiDocument document, ElementNode parent, String text) {
        ElementNode line = document.div();
        line.style().setTextColor(0xFFC9D8F8);
        line.appendText(text);
        parent.append(line);
    }
}
