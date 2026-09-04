package club.heiqi.uilib.font.layout.markdown;

import club.heiqi.uilib.font.layout.TextStyle;

/**
 * markdown 解析输入单元：一段文本 + 其基础样式。
 *
 * <p>聊天组件桥（阶段二 {@code internal/chat}）把 IChatComponent 树展开为带样式锚点的
 * span 流：每个 span 的 {@link TextStyle} 承载组件样式（颜色/粗体/斜体/下划线/删除线/链接），
 * markdown 标记只在 span 文本内解析并叠加样式位，不改变颜色等组件决定的属性。</p>
 */
public final class MarkdownSpan {

    private final String text;
    private final TextStyle baseStyle;

    /**
     * 创建样式锚点 span。
     *
     * @param text      文本内容（不可为空）
     * @param baseStyle 基础样式（不可为 null，markdown 样式在此之上叠加）
     */
    public MarkdownSpan(String text, TextStyle baseStyle) {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("text 不能为空");
        }
        if (baseStyle == null) {
            throw new IllegalArgumentException("baseStyle 不能为 null");
        }
        this.text = text;
        this.baseStyle = baseStyle;
    }

    /** @return span 文本 */
    public String getText() {
        return text;
    }

    /** @return 基础样式（markdown 叠加基准） */
    public TextStyle getBaseStyle() {
        return baseStyle;
    }

    @Override
    public String toString() {
        return "MarkdownSpan(" + text + ")";
    }
}
