package club.heiqi.uilib.ui.scene.paint;
import java.util.Objects;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.text.TextLayout;
/** 封闭值载荷：不引用节点、不读回布局、无可变文本段或嵌套样式。 */
public final class PaintCommand {
    public enum Kind { RECT, TEXT }
    public final Kind kind;
    public final LayoutBox box, clip;
    public final int color;
    public final TextLayout text;
    private PaintCommand(Kind kind, LayoutBox box, LayoutBox clip, int color, TextLayout text) {
        this.kind = kind; this.box = Objects.requireNonNull(box, "box"); this.clip = clip; this.color = color; this.text = text;
    }
    public static PaintCommand rect(LayoutBox box, LayoutBox clip, int color) { return new PaintCommand(Kind.RECT, box, clip, color, null); }
    public static PaintCommand text(LayoutBox box, LayoutBox clip, TextLayout text) { return new PaintCommand(Kind.TEXT, box, clip, text.style.color, text); }
}
