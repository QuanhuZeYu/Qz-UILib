package club.heiqi.uilib.internal.devtools.pages;

import java.util.Objects;

/**
 * `/qzuilib test` 首页功能画廊条目。
 */
final class UiTestGalleryItem {

    private final String title;
    private final String description;
    private final String statusText;
    private final int accentColor;

    /**
     * 创建首页功能画廊条目。
     *
     * @param title 标题
     * @param description 展示说明
     * @param statusText 当前状态文本
     * @param accentColor 强调色
     */
    UiTestGalleryItem(String title, String description, String statusText, int accentColor) {
        this.title = requireText(title, "title");
        this.description = requireText(description, "description");
        this.statusText = requireText(statusText, "statusText");
        this.accentColor = accentColor;
    }

    /**
     * 返回标题。
     *
     * @return 标题
     */
    String getTitle() {
        return title;
    }

    /**
     * 返回展示说明。
     *
     * @return 展示说明
     */
    String getDescription() {
        return description;
    }

    /**
     * 返回当前状态文本。
     *
     * @return 当前状态文本
     */
    String getStatusText() {
        return statusText;
    }

    /**
     * 返回强调色。
     *
     * @return 强调色
     */
    int getAccentColor() {
        return accentColor;
    }

    /**
     * 校验并返回必填文本。
     *
     * @param value 待校验文本
     * @param name 字段名
     * @return 非空文本
     */
    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name);
        if (text.length() == 0) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return text;
    }
}
