package club.heiqi.uilib.ui.scene.paint;
import java.util.Objects;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.text.TextLayout;
/** 封闭值载荷：不引用节点、不读回布局、无可变文本段或嵌套样式。 */
public final class PaintCommand {
    public enum Kind { RECT, TEXT, IMAGE }
    public final Kind kind;
    public final LayoutBox box, clip;
    public final int color;
    public final TextLayout text;
    public final club.heiqi.uilib.ui.scene.image.ImagePlacement image;
    private PaintCommand(Kind kind, LayoutBox box, LayoutBox clip, int color, TextLayout text) { this(kind, box, clip, color, text, null); }
    private PaintCommand(Kind kind, LayoutBox box, LayoutBox clip, int color, TextLayout text, club.heiqi.uilib.ui.scene.image.ImagePlacement image) {
        this.kind = kind; this.box = Objects.requireNonNull(box, "box"); this.clip = clip; this.color = color; this.text = text; this.image = image;
    }
    public static PaintCommand image(LayoutBox box, LayoutBox clip, club.heiqi.uilib.ui.scene.image.ImagePlacement image) { return image(box,clip,image,null); }
    public static PaintCommand image(LayoutBox box, LayoutBox clip, club.heiqi.uilib.ui.scene.image.ImagePlacement image, TextLayout alt) { return new PaintCommand(Kind.IMAGE, box, clip, 0xFFFFFFFF, alt, Objects.requireNonNull(image, "image")); }
    public static PaintCommand rect(LayoutBox box, LayoutBox clip, int color) { return new PaintCommand(Kind.RECT, box, clip, color, null); }
    public static PaintCommand text(LayoutBox box, LayoutBox clip, TextLayout text) { return new PaintCommand(Kind.TEXT, box, clip, text.style.color, text); }
}
