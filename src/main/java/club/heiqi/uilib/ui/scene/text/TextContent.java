package club.heiqi.uilib.ui.scene.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** 显式内容声明。实例身份也是链接手势的声明代际，不能以源码相等替代。 语法及失败合同见文本规格 §0 与富文本 DSL 正常内容合同。 */
public final class TextContent {
    public enum Mode {
        PLAIN,
        RICH_TAGS
    }

    public final Mode mode;
    public final String source;
    private final TextInput input;
    private ParagraphContent paragraph;
    private boolean typed;
    private RichTagsParser.Parsed parsed;

    private synchronized RichTagsParser.Parsed parsed() {
        if (parsed == null) parsed = RichTagsParser.parse(this, input, source);
        return parsed;
    }

    private final java.util.Map<Integer, Link> declarations =
            new java.util.HashMap<Integer, Link>();

    synchronized Link linkDeclaration(int sourceOffset, String target) {
        Link link = declarations.get(sourceOffset);
        if (link == null) {
            link = new Link(target);
            declarations.put(sourceOffset, link);
        }
        return link;
    }

    private TextContent(Mode mode, String source, TextInput input) {
        this.mode = Objects.requireNonNull(mode, "mode");
        this.source = Objects.requireNonNull(source, "source");
        this.input = Objects.requireNonNull(input, "input");
    }

    public static TextContent plain(String source) {
        return string(Mode.PLAIN, source);
    }

    public static TextContent richTags(String source) {
        return string(Mode.RICH_TAGS, source);
    }

    private static TextContent string(Mode mode, String source) {
        return new TextContent(mode, source, TextInput.string(source));
    }

    /** 已解码输入保留 byte/UTF16 来源单位；原始字节仍由调用方持有。 */
    public static TextContent of(Mode mode, TextInput input) {
        return new TextContent(mode, input.text, input);
    }

    /** typed 声明使用 PLAIN 源码语法，属性载荷不新增宿主格式模式。 */
    public static TextContent styled(StyledText value) {
        return paragraph(
                new ParagraphContent(
                        value,
                        Collections.<LinkSpan>emptyList(),
                        Collections.<InlineContent>emptyList()));
    }

    /** 完整 typed 声明；来源映射由 value.text.input 显式提供，不经 DSL 转换。 */
    public static TextContent paragraph(ParagraphContent value) {
        Objects.requireNonNull(value, "value");
        StyledText text = value.text;
        TextContent content =
                new TextContent(
                        Mode.PLAIN,
                        text.originalText == null ? text.input.text : text.originalText,
                        text.input);
        content.paragraph = value;
        content.typed = true;
        return content;
    }

    /** 链接声明身份独立于文字、目标值与布局宽度。 */
    public static final class Link {
        public final String target;

        public Link(String target) {
            this.target = Objects.requireNonNull(target, "target");
        }
    }

    public static final class LinkSpan {
        public final int start, end;
        public final Link link;

        public LinkSpan(int start, int end, Link link) {
            if (start < 0 || end <= start) throw new IllegalArgumentException("无效链接范围");
            this.start = start;
            this.end = end;
            this.link = Objects.requireNonNull(link, "link");
        }
    }

    public static final class Resolved {
        public final AttributedText text;
        private final List<LinkSpan> links;
        private final List<InlineContent> inline;
        private final String serialized;

        Resolved(AttributedText text, List<LinkSpan> links, String serialized) {
            this(text, links, serialized, Collections.<InlineContent>emptyList());
        }

        Resolved(
                AttributedText text,
                List<LinkSpan> links,
                String serialized,
                List<InlineContent> inline) {
            this.inline = Collections.unmodifiableList(new ArrayList<>(inline));
            this.text = text;
            this.links = Collections.unmodifiableList(new ArrayList<LinkSpan>(links));
            this.serialized = serialized;
        }

        public List<InlineContent> inlineContents() {
            return inline;
        }

        public List<LinkSpan> links() {
            return links;
        }

        public Link linkAt(int offset) {
            int low = 0, high = links.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (links.get(middle).end <= offset) low = middle + 1;
                else high = middle;
            }
            return low < links.size() && links.get(low).start <= offset
                    ? links.get(low).link
                    : null;
        }

        public boolean containsLink(Object link) {
            for (LinkSpan span : links) if (span.link == link) return true;
            return false;
        }

        public boolean crossesLinkBoundary(int start, int end) {
            int low = 0, high = links.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (links.get(middle).end <= start) low = middle + 1;
                else high = middle;
            }
            for (int i = low; i < links.size() && links.get(i).start < end; i++) {
                LinkSpan span = links.get(i);
                if (span.start > start || span.end < end) return true;
            }
            return false;
        }

        /** 输出规范 RICH_TAGS 源码。保留本阶段文字、样式及链接语义。 */
        public String serializeRichTags() {
            if (serialized == null) throw new UnsupportedOperationException("typed属性意图不提供DSL往返序列化");
            return serialized;
        }
    }

    private synchronized ParagraphContent paragraph() {
        if (paragraph == null) {
            if (mode == Mode.RICH_TAGS) paragraph = parsed().content;
            else
                paragraph =
                        new ParagraphContent(
                                new StyledText(
                                        input,
                                        null,
                                        input.text.isEmpty()
                                                ? Collections.<TextStyleOverrideSpan>emptyList()
                                                : Collections.singletonList(
                                                        new TextStyleOverrideSpan(
                                                                0,
                                                                input.text.length(),
                                                                TextStyleOverride.INHERIT)),
                                        TextStyleOverride.INHERIT,
                                        Collections.<TextLayout.Diagnostic>emptyList()),
                                Collections.<LinkSpan>emptyList(),
                                Collections.<InlineContent>emptyList());
        }
        return paragraph;
    }

    /** 稳定声明身份随源码替换；主题及宽度变化不重新解析或重建子内容。 */
    public List<InlineContent> inlineContents() {
        return paragraph().inlineContents();
    }

    public Resolved resolve(TextStyle style) {
        Objects.requireNonNull(style, "style");
        ParagraphContent value = paragraph();
        String serialized =
                typed
                        ? null
                        : mode == Mode.RICH_TAGS
                                ? parsed().canonical
                                : RichTagsParser.escape(input.text);
        return new Resolved(
                value.text.resolve(style), value.links(), serialized, value.inlineContents());
    }
}
