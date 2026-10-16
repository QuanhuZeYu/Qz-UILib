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
    private final List<TextSemanticSpan> semantics;

    /** 链接和对象按逻辑 UTF-16 位置排序；对象来源使用 text.input.sourceUnit 的坐标。 */
    public ParagraphContent(
            StyledText text, List<TextContent.LinkSpan> links, List<InlineContent> inline) {
        this(text, links, inline, Collections.<TextSemanticSpan>emptyList());
    }

    /** 语义范围沿同一已投影 UTF-16 文本；保留顺序、重叠与重复嵌套，不从视觉样式反推含义。 */
    public ParagraphContent(StyledText text, List<TextContent.LinkSpan> links,
            List<InlineContent> inline, List<TextSemanticSpan> semantics) {
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
        List<TextSemanticSpan> semanticCopy = new ArrayList<>(Objects.requireNonNull(semantics, "semantics"));
        for (TextSemanticSpan span : semanticCopy) {
            Objects.requireNonNull(span, "semantic span");
            if (span.end > text.input.text.length()
                    || splitsSurrogate(text.input.text, span.start)
                    || splitsSurrogate(text.input.text, span.end))
                throw new IllegalArgumentException("语义范围须属于共同文本且不切代理对");
        }
        this.semantics = Collections.unmodifiableList(semanticCopy);
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

    public List<TextSemanticSpan> semantics() {
        return semantics;
    }

    public List<InlineContent> inlineContents() {
        return inline;
    }
}
