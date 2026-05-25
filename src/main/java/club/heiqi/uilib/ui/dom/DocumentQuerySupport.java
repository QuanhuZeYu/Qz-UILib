package club.heiqi.uilib.ui.dom;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import club.heiqi.uilib.ui.style.selector.UiSelector;

/**
 * 文档 DOM 查询辅助器。
 */
final class DocumentQuerySupport {

    private DocumentQuerySupport() {}

    /**
     * 按 id 属性查找第一个元素。
     *
     * @param root 查询根节点
     * @param id 目标 id
     * @return 匹配元素；未找到时返回 null
     */
    static ElementNode getElementById(DocumentNode root, String id) {
        if (root == null || id == null || id.isEmpty()) {
            return null;
        }
        return findElementById(root, id);
    }

    /**
     * 按选择器查找第一个匹配元素。
     *
     * @param root 查询根节点
     * @param selectorText 选择器文本
     * @return 匹配元素；未找到时返回 null
     */
    static ElementNode querySelector(DocumentNode root, String selectorText) {
        if (root == null || selectorText == null || selectorText.isEmpty()) {
            return null;
        }
        return findFirstMatch(root, UiSelector.parse(selectorText));
    }

    /**
     * 按选择器查找全部匹配元素。
     *
     * @param root 查询根节点
     * @param selectorText 选择器文本
     * @return 匹配元素列表
     */
    static List<ElementNode> querySelectorAll(DocumentNode root, String selectorText) {
        if (root == null || selectorText == null || selectorText.isEmpty()) {
            return Collections.emptyList();
        }
        List<ElementNode> results = new ArrayList<ElementNode>();
        collectMatches(root, UiSelector.parse(selectorText), results);
        return results;
    }

    /**
     * 按标签名收集元素。
     *
     * @param root 查询根节点
     * @param tagName 标签名
     * @return 匹配元素列表
     */
    static List<ElementNode> getElementsByTagName(DocumentNode root, String tagName) {
        if (root == null || tagName == null || tagName.isEmpty()) {
            return Collections.emptyList();
        }
        List<ElementNode> results = new ArrayList<ElementNode>();
        collectByTagName(root, tagName.trim().toLowerCase(Locale.ROOT), results);
        return results;
    }

    /**
     * 按 class 名收集元素。
     *
     * @param root 查询根节点
     * @param className class 名
     * @return 匹配元素列表
     */
    static List<ElementNode> getElementsByClassName(DocumentNode root, String className) {
        if (root == null || className == null || className.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<ElementNode> results = new ArrayList<ElementNode>();
        collectByClassName(root, className.trim(), results);
        return results;
    }

    private static ElementNode findElementById(DocumentNode node, String id) {
        if (node instanceof ElementNode) {
            ElementNode element = (ElementNode) node;
            if (id.equals(element.getId())) {
                return element;
            }
        }
        for (DocumentNode child : node.getChildren()) {
            ElementNode found = findElementById(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static ElementNode findFirstMatch(DocumentNode node, UiSelector selector) {
        if (node instanceof ElementNode) {
            ElementNode element = (ElementNode) node;
            if (selector.matches(element)) {
                return element;
            }
        }
        for (DocumentNode child : node.getChildren()) {
            ElementNode found = findFirstMatch(child, selector);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static void collectMatches(DocumentNode node, UiSelector selector, List<ElementNode> results) {
        if (node instanceof ElementNode) {
            ElementNode element = (ElementNode) node;
            if (selector.matches(element)) {
                results.add(element);
            }
        }
        for (DocumentNode child : node.getChildren()) {
            collectMatches(child, selector, results);
        }
    }

    private static void collectByTagName(DocumentNode node, String tagName, List<ElementNode> results) {
        if (node instanceof ElementNode) {
            ElementNode element = (ElementNode) node;
            if (tagName.equals(element.getTagName())) {
                results.add(element);
            }
        }
        for (DocumentNode child : node.getChildren()) {
            collectByTagName(child, tagName, results);
        }
    }

    private static void collectByClassName(DocumentNode node, String className, List<ElementNode> results) {
        if (node instanceof ElementNode) {
            ElementNode element = (ElementNode) node;
            if (element.getClassList().contains(className)) {
                results.add(element);
            }
        }
        for (DocumentNode child : node.getChildren()) {
            collectByClassName(child, className, results);
        }
    }
}
