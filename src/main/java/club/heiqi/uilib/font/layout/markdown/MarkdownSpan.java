package club.heiqi.uilib.font.layout.markdown;

import club.heiqi.uilib.font.layout.TextStyle;

/**
 * markdown 解析输入单元：一段文本 + 其基础样式。
 *
 * <p>本类型是<b>通用样式锚点通道</b>的输入单元（{@link MarkdownDocument#parseSpans(List)}）：
 * 每个 span 的 {@link TextStyle} 承载该段文本的基础样式（颜色/粗体/斜体/下划线/删除线/链接），
 * markdown 标记只在拼接文本上解析并把样式位叠加回各区间，不改变 span 决定的颜色。
 * 计划的调用方是 IChatComponent 树展开与业务 mod 富文本；<b>chat3 自 C7（2026-09-07 划界）起
 * 不再经本通道</b>——它曾用于把 § 码流转成样式锚点，该转换器已随「markdown 路径不解释 §」删除，
 * 本通道不得再被任何 § 相关代码使用（守卫 {@code MarkdownL1ZeroSectionKnowledgeGuardTest}）。
 * 入口与能力保留在案，等富文本 component 通道接线时启用。</p>
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
