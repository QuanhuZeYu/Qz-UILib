package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.dom.TextNode;

/**
 * `/qzuilib test` 当前页面动态文本绑定。
 */
final class UiTestPageBindings {

    private static final UiTestPageBindings EMPTY = new UiTestPageBindings(null);

    private final TextNode environmentText;

    /**
     * 创建空绑定。
     *
     * @return 空绑定
     */
    static UiTestPageBindings empty() {
        return EMPTY;
    }

    /**
     * 创建页面绑定。
     *
     * @param environmentText 环境信息文本节点
     */
    UiTestPageBindings(TextNode environmentText) {
        this.environmentText = environmentText;
    }

    /**
     * 返回环境信息文本节点。
     *
     * @return 环境信息文本节点；不存在时为 null
     */
    TextNode getEnvironmentText() {
        return environmentText;
    }
}
