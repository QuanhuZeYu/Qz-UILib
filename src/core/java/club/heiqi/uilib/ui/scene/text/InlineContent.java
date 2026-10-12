package club.heiqi.uilib.ui.scene.text;

import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.text.math.MathContent;
import club.heiqi.uilib.ui.scene.text.math.MathStyle;

import java.util.Objects;

/** 源内容中的原子声明；尺寸由当前字体/图像测量产生，声明不持有节点或资源租约。 */
public final class InlineContent {
    public enum Kind {
        MATH,
        IMAGE
    }

    public final Kind kind;
    public final int offset, sourceStart, sourceEnd;
    public final MathContent math;
    public final MathStyle mathStyle;
    public final String source, alt;

    /** 原内容的可选说明；不隐含浏览器tooltip或独立输入层。 */
    public final String title;

    public final Integer width, height;
    public final ImageOptions.Fit fit;

    /**
     * 来源范围沿共同段落 TextInput 的 BYTE/UTF16 单位；DSL 覆盖整个 latex 标签。 MathContent 诊断使用公式正文内的局部
     * UTF16，不能直接与此范围相加。
     */
    public static InlineContent formula(
            int offset, int sourceStart, int sourceEnd, MathContent math, MathStyle style) {
        return new InlineContent(offset, sourceStart, sourceEnd, math, style);
    }

    public static InlineContent image(
            int offset,
            int sourceStart,
            int sourceEnd,
            String source,
            String alt,
            Integer width,
            Integer height,
            ImageOptions.Fit fit) {
        return new InlineContent(offset, sourceStart, sourceEnd, source, alt, width, height, fit);
    }

    public static InlineContent image(
            int offset,
            int sourceStart,
            int sourceEnd,
            String source,
            String alt,
            Integer width,
            Integer height,
            ImageOptions.Fit fit,
            String title) {
        return new InlineContent(
                offset, sourceStart, sourceEnd, source, alt, width, height, fit, title);
    }

    private static void validateRange(int offset, int start, int end) {
        if (offset < 0 || start < 0 || end <= start) throw new IllegalArgumentException("无效行内声明范围");
    }

    InlineContent(int offset, int start, int end, MathContent math, MathStyle style) {
        validateRange(offset, start, end);
        this.offset = offset;
        sourceStart = start;
        sourceEnd = end;
        kind = Kind.MATH;
        this.math = Objects.requireNonNull(math, "math");
        mathStyle = Objects.requireNonNull(style, "style");
        source = math.source;
        alt = "";
        title = "";
        width = height = null;
        fit = ImageOptions.Fit.CONTAIN;
    }

    InlineContent(
            int offset,
            int start,
            int end,
            String source,
            String alt,
            Integer width,
            Integer height,
            ImageOptions.Fit fit) {
        this(offset, start, end, source, alt, width, height, fit, "");
    }

    private InlineContent(
            int offset,
            int start,
            int end,
            String source,
            String alt,
            Integer width,
            Integer height,
            ImageOptions.Fit fit,
            String title) {
        validateRange(offset, start, end);
        if (width != null && width < 0 || height != null && height < 0)
            throw new IllegalArgumentException("图像尺寸不能为负数");
        this.offset = offset;
        sourceStart = start;
        sourceEnd = end;
        kind = Kind.IMAGE;
        this.source = Objects.requireNonNull(source, "source");
        this.alt = Objects.requireNonNull(alt, "alt");
        this.title = Objects.requireNonNull(title, "title");
        this.width = width;
        this.height = height;
        this.fit = Objects.requireNonNull(fit, "fit");
        math = null;
        mathStyle = null;
    }
}
