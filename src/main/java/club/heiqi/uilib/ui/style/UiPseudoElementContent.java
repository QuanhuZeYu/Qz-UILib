package club.heiqi.uilib.ui.style;

import java.util.Objects;

/**
 * `::before` / `::after` 的最小内容声明。
 */
public final class UiPseudoElementContent {

    private final String text;
    private final boolean none;

    private UiPseudoElementContent(String text, boolean none) {
        this.text = text == null ? "" : text;
        this.none = none;
    }

    /**
     * 创建文本内容。
     *
     * @param text 文本内容
     * @return 内容对象
     */
    public static UiPseudoElementContent text(String text) {
        return new UiPseudoElementContent(text, false);
    }

    /**
     * 创建 `content:none`。
     *
     * @return 内容对象
     */
    public static UiPseudoElementContent none() {
        return new UiPseudoElementContent("", true);
    }

    /**
     * 返回文本内容。
     *
     * @return 文本内容
     */
    public String getText() {
        return text;
    }

    /**
     * 返回是否为 `none`。
     *
     * @return 是否为 none
     */
    public boolean isNone() {
        return none;
    }

    /**
     * 返回是否有可绘制文本。
     *
     * @return 是否有文本内容
     */
    public boolean hasRenderableContent() {
        return !none && !text.isEmpty();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof UiPseudoElementContent)) {
            return false;
        }
        UiPseudoElementContent other = (UiPseudoElementContent) obj;
        return none == other.none && Objects.equals(text, other.text);
    }

    @Override
    public int hashCode() {
        return 31 * text.hashCode() + (none ? 1 : 0);
    }
}
