package club.heiqi.uilib.ui.scene.text;

import java.util.Objects;

/** 共同文本的不可变行内含义；不存视觉样式或几何，来源沿所属 TextInput 映射。 */
public final class TextSemanticSpan {
    public enum Kind {
        IMPORTANCE,
        ATTENTION,
        EMPHASIS,
        ALTERNATE_VOICE,
        ANNOTATION,
        NO_LONGER_ACCURATE,
        DELETION
    }

    public final int start, end;
    public final Kind kind;

    public TextSemanticSpan(int start, int end, Kind kind) {
        if (start < 0 || end <= start) throw new IllegalArgumentException("无效语义范围");
        this.start = start;
        this.end = end;
        this.kind = Objects.requireNonNull(kind, "kind");
    }
}
