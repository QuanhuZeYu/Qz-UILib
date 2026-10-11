package club.heiqi.uilib.ui.scene.text;

import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.text.math.MathContent;
import club.heiqi.uilib.ui.scene.text.math.MathStyle;

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
    public final Integer width, height;
    public final ImageOptions.Fit fit;

    InlineContent(int offset, int start, int end, MathContent math, MathStyle style) {
        this.offset = offset;
        sourceStart = start;
        sourceEnd = end;
        kind = Kind.MATH;
        this.math = math;
        mathStyle = style;
        source = math.source;
        alt = "";
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
        this.offset = offset;
        sourceStart = start;
        sourceEnd = end;
        kind = Kind.IMAGE;
        this.source = source;
        this.alt = alt;
        this.width = width;
        this.height = height;
        this.fit = fit;
        math = null;
        mathStyle = null;
    }
}
