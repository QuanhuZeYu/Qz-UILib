package club.heiqi.uilib.ui.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import club.heiqi.uilib.ui.dom.ElementNode;

/**
 * CSS-like 选择器。
 *
 * <p>支持以下选择器类型：</p>
 * <ul>
 *   <li>标签选择器：{@code div}、{@code button}</li>
 *   <li>类选择器：{@code .my-class}</li>
 *   <li>ID 选择器：{@code #my-id}</li>
 *   <li>通配符选择器：{@code *}</li>
 *   <li>复合选择器：{@code div.my-class#my-id}（同时匹配多个条件）</li>
 * </ul>
 *
 * <p>特异性（specificity）按 CSS 标准计算：(id数, class数, tag数)。</p>
 */
public final class UiSelector {

    private final String tagName;
    private final List<String> classNames;
    private final String id;
    private final boolean universal;
    private final int specificityId;
    private final int specificityClass;
    private final int specificityTag;

    private UiSelector(String tagName, List<String> classNames, String id, boolean universal) {
        this.tagName = tagName;
        this.classNames = classNames == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(classNames));
        this.id = id;
        this.universal = universal;
        this.specificityId = id != null ? 1 : 0;
        this.specificityClass = this.classNames.size();
        this.specificityTag = (tagName != null && !universal) ? 1 : 0;
    }

    /**
     * 创建标签选择器。
     *
     * @param tagName 标签名
     * @return 选择器
     */
    public static UiSelector tag(String tagName) {
        Objects.requireNonNull(tagName, "tagName");
        return new UiSelector(tagName.trim().toLowerCase(java.util.Locale.ROOT), null, null, false);
    }

    /**
     * 创建类选择器。
     *
     * @param className 类名（不含前导点号）
     * @return 选择器
     */
    public static UiSelector className(String className) {
        Objects.requireNonNull(className, "className");
        List<String> list = new ArrayList<String>(1);
        list.add(className.trim());
        return new UiSelector(null, list, null, false);
    }

    /**
     * 创建 ID 选择器。
     *
     * @param id ID 值（不含前导井号）
     * @return 选择器
     */
    public static UiSelector id(String id) {
        Objects.requireNonNull(id, "id");
        return new UiSelector(null, null, id.trim(), false);
    }

    /**
     * 创建通配符选择器（匹配所有元素）。
     *
     * @return 通配符选择器
     */
    public static UiSelector universal() {
        return new UiSelector(null, null, null, true);
    }

    /**
     * 从简易选择器字符串解析。
     *
     * <p>支持格式：{@code tag.class1.class2#id}、{@code .class}、{@code #id}、{@code *}。
     * 不支持后代/子代组合器、伪类和属性选择器。</p>
     *
     * @param selectorText 选择器文本
     * @return 解析后的选择器
     * @throws IllegalArgumentException 格式无效时
     */
    public static UiSelector parse(String selectorText) {
        Objects.requireNonNull(selectorText, "selectorText");
        String text = selectorText.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Selector text cannot be empty");
        }
        if ("*".equals(text)) {
            return universal();
        }

        String parsedTag = null;
        String parsedId = null;
        List<String> parsedClasses = new ArrayList<String>();

        // 简易解析器：逐字符扫描 tag、.class、#id 片段
        int i = 0;
        int len = text.length();

        // 解析开头的 tag 部分（不以 . 或 # 开头的部分）
        if (text.charAt(0) != '.' && text.charAt(0) != '#') {
            int start = 0;
            while (i < len && text.charAt(i) != '.' && text.charAt(i) != '#') {
                i++;
            }
            parsedTag = text.substring(start, i).toLowerCase(java.util.Locale.ROOT);
            if (parsedTag.isEmpty()) {
                throw new IllegalArgumentException("Invalid selector: " + selectorText);
            }
        }

        // 解析后续的 .class 和 #id 片段
        while (i < len) {
            char ch = text.charAt(i);
            if (ch == '.') {
                i++;
                int start = i;
                while (i < len && text.charAt(i) != '.' && text.charAt(i) != '#') {
                    i++;
                }
                String cls = text.substring(start, i);
                if (cls.isEmpty()) {
                    throw new IllegalArgumentException("Empty class name in selector: " + selectorText);
                }
                parsedClasses.add(cls);
            } else if (ch == '#') {
                i++;
                int start = i;
                while (i < len && text.charAt(i) != '.' && text.charAt(i) != '#') {
                    i++;
                }
                String idValue = text.substring(start, i);
                if (idValue.isEmpty()) {
                    throw new IllegalArgumentException("Empty id in selector: " + selectorText);
                }
                if (parsedId != null) {
                    throw new IllegalArgumentException("Multiple IDs in selector: " + selectorText);
                }
                parsedId = idValue;
            } else {
                throw new IllegalArgumentException("Unexpected character '" + ch + "' in selector: " + selectorText);
            }
        }

        return new UiSelector(parsedTag, parsedClasses.isEmpty() ? null : parsedClasses, parsedId, false);
    }

    /**
     * 判断选择器是否匹配指定元素。
     *
     * @param element 目标元素
     * @return 是否匹配
     */
    public boolean matches(ElementNode element) {
        if (element == null) {
            return false;
        }
        // 通配符匹配所有
        if (universal && id == null && classNames.isEmpty()) {
            return true;
        }
        // 检查 tag
        if (tagName != null && !tagName.equals(element.getTagName())) {
            return false;
        }
        // 检查 id
        if (id != null && !id.equals(element.getId())) {
            return false;
        }
        // 检查 class
        if (!classNames.isEmpty()) {
            for (String cls : classNames) {
                if (!element.getClassList().contains(cls)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 返回特异性的 ID 分量。
     *
     * @return ID 特异性分量
     */
    public int getSpecificityId() {
        return specificityId;
    }

    /**
     * 返回特异性的 class 分量。
     *
     * @return class 特异性分量
     */
    public int getSpecificityClass() {
        return specificityClass;
    }

    /**
     * 返回特异性的 tag 分量。
     *
     * @return tag 特异性分量
     */
    public int getSpecificityTag() {
        return specificityTag;
    }

    /**
     * 比较两个选择器的特异性。
     *
     * <p>返回值含义：正数表示 this 特异性更高，负数表示 other 更高，0 表示相同。</p>
     *
     * @param other 另一个选择器
     * @return 特异性比较结果
     */
    public int compareSpecificity(UiSelector other) {
        int cmp = Integer.compare(this.specificityId, other.specificityId);
        if (cmp != 0) return cmp;
        cmp = Integer.compare(this.specificityClass, other.specificityClass);
        if (cmp != 0) return cmp;
        return Integer.compare(this.specificityTag, other.specificityTag);
    }

    /**
     * 返回标签名条件。
     *
     * @return 标签名；无标签条件时返回 null
     */
    public String getTagName() {
        return tagName;
    }

    /**
     * 返回类名条件列表。
     *
     * @return 类名列表（只读）
     */
    public List<String> getClassNames() {
        return classNames;
    }

    /**
     * 返回 ID 条件。
     *
     * @return ID 值；无 ID 条件时返回 null
     */
    public String getId() {
        return id;
    }

    /**
     * 判断是否为通配符选择器。
     *
     * @return 是否为通配符
     */
    public boolean isUniversal() {
        return universal;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (universal && tagName == null && id == null && classNames.isEmpty()) {
            return "*";
        }
        if (tagName != null) {
            sb.append(tagName);
        }
        if (id != null) {
            sb.append('#').append(id);
        }
        for (String cls : classNames) {
            sb.append('.').append(cls);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof UiSelector)) return false;
        UiSelector other = (UiSelector) obj;
        return universal == other.universal
                && Objects.equals(tagName, other.tagName)
                && Objects.equals(id, other.id)
                && classNames.equals(other.classNames);
    }

    @Override
    public int hashCode() {
        int result = tagName != null ? tagName.hashCode() : 0;
        result = 31 * result + classNames.hashCode();
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (universal ? 1 : 0);
        return result;
    }
}
