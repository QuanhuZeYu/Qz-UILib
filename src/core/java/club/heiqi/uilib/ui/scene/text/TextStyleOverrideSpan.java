package club.heiqi.uilib.ui.scene.text;

import java.util.Objects;

/** 解码后的逻辑 UTF16 范围；来源单位由 StyledText.input 保留。 */
public final class TextStyleOverrideSpan {
    public final int start, end;
    public final TextStyleOverride style;

    public TextStyleOverrideSpan(int start, int end, TextStyleOverride style) {
        if (start < 0 || end <= start) throw new IllegalArgumentException("无效属性意图范围");
        this.start = start;
        this.end = end;
        this.style = Objects.requireNonNull(style, "style");
    }
}
