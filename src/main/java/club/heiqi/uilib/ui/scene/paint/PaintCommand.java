package club.heiqi.uilib.ui.scene.paint;
import java.util.Objects;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.image.ImagePlacement;
/** 封闭值载荷：不引用节点、不读回布局；嵌套计划均为同代已定位值。 */
public final class PaintCommand {
    public enum Kind { RECT, TEXT, IMAGE, IMAGE_BRANCH_OVERLAY }
    public final Kind kind;
    public final LayoutBox box, clip;
    public final int color;
    public final TextLayout text;
    public final ImagePlacement image;
    /** IMAGE的呈现分支由控件定义，backend仅依据资源事实选择；null表示该分支留空。 */
    public final PaintPlan pendingPaint, failedPaint, readyOverlay;
    /** 只引用本计划中先执行的IMAGE不可变命令；延后overlay复用它已作出的选择，不重画原图。 */
    public final PaintCommand branchSource;
    private PaintCommand(Kind kind, LayoutBox box, LayoutBox clip, int color, TextLayout text, ImagePlacement image,
            PaintPlan pendingPaint, PaintPlan failedPaint, PaintPlan readyOverlay) {
        this(kind,box,clip,color,text,image,pendingPaint,failedPaint,readyOverlay,null);
    }
    private PaintCommand(Kind kind, LayoutBox box, LayoutBox clip, int color, TextLayout text, ImagePlacement image,
            PaintPlan pendingPaint, PaintPlan failedPaint, PaintPlan readyOverlay, PaintCommand branchSource) {
        this.kind = kind; this.box = Objects.requireNonNull(box, "box"); this.clip = clip; this.color = color; this.text = text; this.image = image;
        this.pendingPaint = pendingPaint; this.failedPaint = failedPaint; this.readyOverlay = readyOverlay; this.branchSource = branchSource;
    }
    public static PaintCommand image(LayoutBox box, LayoutBox clip, ImagePlacement image) { return image(box,clip,image,(TextLayout)null); }
    /** 旧手工载荷不声明占位；alt仅作文字元数据，backend不据此另造UI。 */
    public static PaintCommand image(LayoutBox box, LayoutBox clip, ImagePlacement image, TextLayout alt) {
        return new PaintCommand(Kind.IMAGE, box, clip, 0xFFFFFFFF, alt, Objects.requireNonNull(image,"image"), null, null, null);
    }
    public static PaintCommand image(LayoutBox box, LayoutBox clip, ImagePlacement image, PaintPlan pendingPaint, PaintPlan failedPaint, PaintPlan readyOverlay) {
        return new PaintCommand(Kind.IMAGE, box, clip, 0xFFFFFFFF, null, Objects.requireNonNull(image,"image"), pendingPaint, failedPaint, readyOverlay);
    }
    /** 全页内容结束后再投影备用overlay；主图ready或未执行时不投影。资源仅遍历所带片段。 */
    public static PaintCommand imageBranchOverlay(PaintCommand source, PaintPlan pending, PaintPlan failed) {
        if (Objects.requireNonNull(source,"source").kind!=Kind.IMAGE) throw new IllegalArgumentException("分支投影必须引用IMAGE");
        return new PaintCommand(Kind.IMAGE_BRANCH_OVERLAY,source.box,null,0,null,null,pending,failed,null,source);
    }
    public static PaintCommand rect(LayoutBox box, LayoutBox clip, int color) { return new PaintCommand(Kind.RECT, box, clip, color, null, null, null, null, null); }
    public static PaintCommand text(LayoutBox box, LayoutBox clip, TextLayout text) { return new PaintCommand(Kind.TEXT, box, clip, text.style.color, text, null, null, null, null); }
}
