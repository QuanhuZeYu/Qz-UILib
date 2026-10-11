package club.heiqi.uilib.ui.scene.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 共同不可变段落声明；来源与样式由 StyledText 持有，语法序列化及呈现资源各归其入口。 */
public final class ParagraphContent {
    public final StyledText text;
    private final List<TextContent.LinkSpan> links;
    private final List<InlineContent> inline;

    /** 链接和对象按逻辑 UTF-16 位置排序；对象来源使用 text.input.sourceUnit 的坐标。 */
    public ParagraphContent(
            StyledText text, List<TextContent.LinkSpan> links, List<InlineContent> inline) {
        this.text = Objects.requireNonNull(text, "text");
        List<TextContent.LinkSpan> linkCopy =
                new ArrayList<>(Objects.requireNonNull(links, "links"));
        int previousEnd = 0;
        for (TextContent.LinkSpan span : linkCopy) {
            Objects.requireNonNull(span, "link span");
            if (span.start < previousEnd
                    || span.end > text.input.text.length()
                    || splitsSurrogate(text.input.text, span.start)
                    || splitsSurrogate(text.input.text, span.end))
                throw new IllegalArgumentException("链接必须排序、不重叠且不切代理对");
            previousEnd = span.end;
        }
        List<InlineContent> inlineCopy = new ArrayList<>(Objects.requireNonNull(inline, "inline"));
        int previousOffset = -1;
        for (InlineContent item : inlineCopy) {
            Objects.requireNonNull(item, "inline content");
            if (item.offset <= previousOffset
                    || item.offset >= text.input.text.length()
                    || text.input.text.charAt(item.offset) != '\ufffc')
                throw new IllegalArgumentException("行内声明必须排序并唯一关联 U+FFFC");
            if (item.sourceStart != text.input.sourceStart(item.offset)
                    || item.sourceEnd != text.input.sourceEnd(item.offset))
                throw new IllegalArgumentException("行内来源必须匹配占位符来源范围及单位");
            previousOffset = item.offset;
        }
        this.links = Collections.unmodifiableList(linkCopy);
        this.inline = Collections.unmodifiableList(inlineCopy);
    }

    private static boolean splitsSurrogate(String text, int boundary) {
        return boundary > 0
                && boundary < text.length()
                && Character.isHighSurrogate(text.charAt(boundary - 1))
                && Character.isLowSurrogate(text.charAt(boundary));
    }

    public List<TextContent.LinkSpan> links() {
        return links;
    }

    public List<InlineContent> inlineContents() {
        return inline;
    }
}
