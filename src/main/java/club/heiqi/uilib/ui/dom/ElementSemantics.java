package club.heiqi.uilib.ui.dom;

import java.util.Locale;

/**
 * 元素语义计算工具。
 *
 * <p>承载 ARIA、可访问名称、tabindex 解析、disabled 解析等纯函数能力，
 * 让 {@link ElementNode} 的本体只保留属性容器与生命周期。</p>
 */
final class ElementSemantics {

    private ElementSemantics() {}

    /**
     * 判断元素是否被 aria-hidden 从语义树隐藏。
     */
    static boolean isAriaHidden(ElementNode element) {
        return "true".equals(element.getAttribute("aria-hidden"));
    }

    /**
     * 判断原生表单控件是否处于 disabled 状态。
     */
    static boolean isDisabled(ElementNode element) {
        if (!isNativeFocusableTag(element.getTagName())) {
            return false;
        }
        String value = element.getAttribute("disabled");
        return value != null && !"false".equals(value.trim().toLowerCase(Locale.ROOT));
    }

    /**
     * 判断原生标签是否默认参与焦点遍历。
     */
    static boolean isNativeFocusableTag(String tagName) {
        return "button".equals(tagName) || "input".equals(tagName) || "textarea".equals(tagName)
                || "select".equals(tagName);
    }

    /**
     * 解析 tabindex 整数值。
     */
    static Integer resolveTabIndex(ElementNode element) {
        String value = element.getAttribute("tabindex");
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * 解析元素面向辅助技术的语义角色。
     */
    static String resolveSemanticRole(ElementNode element) {
        String role = trimToNull(element.getAttribute("role"));
        if (role != null) {
            return role;
        }
        String tagName = element.getTagName();
        if ("button".equals(tagName)) {
            return "button";
        }
        if ("input".equals(tagName)) {
            String type = trimToNull(element.getAttribute("type"));
            if ("checkbox".equals(type)) {
                return "checkbox";
            }
            return "textbox";
        }
        if ("textarea".equals(tagName)) {
            return "textbox";
        }
        if ("select".equals(tagName)) {
            return "combobox";
        }
        if ("option".equals(tagName)) {
            return "option";
        }
        if (isAnchorElement(tagName) && hasLinkHref(element.getAttribute("href"))) {
            return "link";
        }
        if (DocumentImageElementSupport.isImageTag(tagName)) {
            String alt = element.getAttribute("alt");
            return alt != null && alt.isEmpty() ? "presentation" : "img";
        }
        return null;
    }

    /**
     * 解析元素可访问名称。
     */
    static String resolveAccessibleLabel(ElementNode element) {
        if (isAriaHidden(element)) {
            return "";
        }
        String ariaLabel = trimToNull(element.getAttribute("aria-label"));
        if (ariaLabel != null) {
            return ariaLabel;
        }
        if (DocumentImageElementSupport.isImageTag(element.getTagName())) {
            String alt = element.getAttribute("alt");
            return alt == null ? "" : alt;
        }
        return collectTextContent(element).trim();
    }

    /**
     * 判断标签是否为锚元素。
     */
    static boolean isAnchorElement(String tagName) {
        return "a".equals(tagName);
    }

    /**
     * 判断 anchor href 值是否非空（trim 后），用于判定锚是否带可点击链接。
     */
    static boolean hasLinkHref(String href) {
        if (href == null) {
            return false;
        }
        return !href.trim().isEmpty();
    }

    /**
     * 把可能为空白的字符串转为 null（trim 后保留原字符串）。
     */
    static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 递归收集元素子树中文本，跳过 aria-hidden 子树。
     */
    static String collectTextContent(DocumentNode node) {
        StringBuilder builder = new StringBuilder();
        appendTextContent(node, builder);
        return builder.toString();
    }

    private static void appendTextContent(DocumentNode node, StringBuilder builder) {
        if (node instanceof TextNode) {
            builder.append(((TextNode) node).getText());
            return;
        }
        if (node instanceof ElementNode && isAriaHidden((ElementNode) node)) {
            return;
        }
        for (DocumentNode child : node.getChildren()) {
            appendTextContent(child, builder);
        }
    }
}
