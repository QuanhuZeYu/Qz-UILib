package club.heiqi.uilib.ui.scene.paint;

import club.heiqi.uilib.ui.scene.image.ImagePlacement;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.text.TextLayout;

import java.util.Objects;

/** 封闭值载荷：不引用节点、不读回布局；嵌套计划均为同代已定位值。 */
public final class PaintCommand {
    public enum Kind {
        RECT,
        LINE,
        TEXT,
        IMAGE,
        VIDEO,
        IMAGE_BRANCH_OVERLAY
    }

    public final Kind kind;
    public final LayoutBox box, clip;
    public final int color;
    public final TextLayout text;
    public final ImagePlacement image;
    public final Line line;
    public final club.heiqi.uilib.ui.scene.video.VideoPlacement video;

    /** 平端点线段，线宽沿法线居中；零长度不绘制，clip不改变端点或方向。 */
    public static final class Line {
        public final float x0, y0, x1, y1, thickness;

        private Line(float x0, float y0, float x1, float y1, float thickness) {
            if (!Float.isFinite(x0)
                    || !Float.isFinite(y0)
                    || !Float.isFinite(x1)
                    || !Float.isFinite(y1)
                    || !Float.isFinite(thickness)
                    || thickness <= 0) throw new IllegalArgumentException("无效线段");
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            this.thickness = thickness;
        }

        public boolean empty() {
            return x0 == x1 && y0 == y1;
        }

        private LayoutBox bounds() {
            double half = empty() ? 0 : (double) thickness / 2;
            double left = Math.floor(Math.min(x0, x1) - half);
            double top = Math.floor(Math.min(y0, y1) - half);
            double right = Math.ceil(Math.max(x0, x1) + half);
            double bottom = Math.ceil(Math.max(y0, y1) + half);
            if (left < Integer.MIN_VALUE
                    || top < Integer.MIN_VALUE
                    || right > Integer.MAX_VALUE
                    || bottom > Integer.MAX_VALUE
                    || right - left > Integer.MAX_VALUE
                    || bottom - top > Integer.MAX_VALUE)
                throw new IllegalArgumentException("线段超出布局坐标范围");
            // 仅作保守包络；消费者必须使用line端点生成四角，不能绘制此box。
            return new LayoutBox((int) left, (int) top, (int) (right - left), (int) (bottom - top));
        }
    }

    /** IMAGE的呈现分支由控件定义，backend仅依据资源事实选择；null表示该分支留空。 */
    public final PaintPlan pendingPaint, failedPaint, readyOverlay;

    /** 只引用本计划中先执行的IMAGE不可变命令；延后overlay复用它已作出的选择，不重画原图。 */
    public final PaintCommand branchSource;

    private PaintCommand(
            Kind kind,
            LayoutBox box,
            LayoutBox clip,
            int color,
            TextLayout text,
            ImagePlacement image,
            PaintPlan pendingPaint,
            PaintPlan failedPaint,
            PaintPlan readyOverlay) {
        this(kind, box, clip, color, text, image, pendingPaint, failedPaint, readyOverlay, null);
    }

    private PaintCommand(
            Kind kind,
            LayoutBox box,
            LayoutBox clip,
            int color,
            TextLayout text,
            ImagePlacement image,
            PaintPlan pendingPaint,
            PaintPlan failedPaint,
            PaintPlan readyOverlay,
            PaintCommand branchSource) {
        this(
                kind,
                box,
                clip,
                color,
                text,
                image,
                pendingPaint,
                failedPaint,
                readyOverlay,
                branchSource,
                null);
    }

    private PaintCommand(
            Kind kind,
            LayoutBox box,
            LayoutBox clip,
            int color,
            TextLayout text,
            ImagePlacement image,
            PaintPlan pendingPaint,
            PaintPlan failedPaint,
            PaintPlan readyOverlay,
            PaintCommand branchSource,
            Line line) {
        this(kind, box, clip, color, text, image, pendingPaint, failedPaint, readyOverlay, branchSource, line, null);
    }

    private PaintCommand(Kind kind, LayoutBox box, LayoutBox clip, int color, TextLayout text,
            ImagePlacement image, PaintPlan pendingPaint, PaintPlan failedPaint, PaintPlan readyOverlay,
            PaintCommand branchSource, Line line, club.heiqi.uilib.ui.scene.video.VideoPlacement video) {
        this.video = video;
        this.line = line;
        this.kind = kind;
        this.box = Objects.requireNonNull(box, "box");
        this.clip = clip;
        this.color = color;
        this.text = text;
        this.image = image;
        this.pendingPaint = pendingPaint;
        this.failedPaint = failedPaint;
        this.readyOverlay = readyOverlay;
        this.branchSource = branchSource;
    }

    public static PaintCommand image(LayoutBox box, LayoutBox clip, ImagePlacement image) {
        return image(box, clip, image, (TextLayout) null);
    }

    /** 旧手工载荷不声明占位；alt仅作文字元数据，backend不据此另造UI。 */
    public static PaintCommand image(
            LayoutBox box, LayoutBox clip, ImagePlacement image, TextLayout alt) {
        return new PaintCommand(
                Kind.IMAGE,
                box,
                clip,
                0xFFFFFFFF,
                alt,
                Objects.requireNonNull(image, "image"),
                null,
                null,
                null);
    }

    public static PaintCommand image(
            LayoutBox box,
            LayoutBox clip,
            ImagePlacement image,
            PaintPlan pendingPaint,
            PaintPlan failedPaint,
            PaintPlan readyOverlay) {
        return new PaintCommand(
                Kind.IMAGE,
                box,
                clip,
                0xFFFFFFFF,
                null,
                Objects.requireNonNull(image, "image"),
                pendingPaint,
                failedPaint,
                readyOverlay);
    }

    /** 全页内容结束后再投影备用overlay；主图ready或未执行时不投影。资源仅遍历所带片段。 */
    public static PaintCommand imageBranchOverlay(
            PaintCommand source, PaintPlan pending, PaintPlan failed) {
        if (Objects.requireNonNull(source, "source").kind != Kind.IMAGE)
            throw new IllegalArgumentException("分支投影必须引用IMAGE");
        return new PaintCommand(
                Kind.IMAGE_BRANCH_OVERLAY,
                source.box,
                null,
                0,
                null,
                null,
                pending,
                failed,
                null,
                source);
    }

    /** 封闭端点/线宽事实；不持有live节点，不以clip裁短后重新计算方向。 */
    public static PaintCommand line(
            float x0, float y0, float x1, float y1, float thickness, LayoutBox clip, int color) {
        Line line = new Line(x0, y0, x1, y1, thickness);
        return new PaintCommand(
                Kind.LINE, line.bounds(), clip, color, null, null, null, null, null, null, line);
    }

    /** 视频是同一有序计划的封闭值，不读取播放会话或包含 GPU 句柄。 */
    public static PaintCommand video(LayoutBox box, LayoutBox clip, club.heiqi.uilib.ui.scene.video.VideoPlacement video) {
        return new PaintCommand(Kind.VIDEO, box, clip, 0xFFFFFFFF, null, null, null, null, null, null, null,
                Objects.requireNonNull(video, "video"));
    }

    PaintCommand presented(int dx, int dy, float opacity, LayoutBox limit,
            java.util.Map<PaintCommand, PaintCommand> mapped) {
        PaintCommand existing = mapped.get(this);
        if (existing != null) return existing;
        LayoutBox moved = new LayoutBox(box.x + dx, box.y + dy, box.width, box.height);
        LayoutBox clipped = clip == null ? null : new LayoutBox(clip.x + dx, clip.y + dy, clip.width, clip.height);
        if (limit != null) clipped = clipped == null ? limit : clipped.intersect(limit);
        PaintPlan pending = pendingPaint == null ? null : pendingPaint.presented(dx, dy, opacity, limit, mapped);
        PaintPlan failed = failedPaint == pendingPaint ? pending
                : failedPaint == null ? null : failedPaint.presented(dx, dy, opacity, limit, mapped);
        PaintPlan ready = readyOverlay == null ? null : readyOverlay.presented(dx, dy, opacity, limit, mapped);
        PaintCommand branch = branchSource == null ? null : branchSource.presented(dx, dy, opacity, limit, mapped);
        Line movedLine = line == null ? null : new Line(line.x0 + dx, line.y0 + dy, line.x1 + dx, line.y1 + dy, line.thickness);
        PaintCommand result = new PaintCommand(kind, moved, clipped,
                club.heiqi.uilib.ui.scene.motion.SceneMotion.alpha(color, opacity),
                text == null ? null : text.withOpacity(opacity), image == null ? null : image.translated(dx, dy),
                pending, failed, ready, branch, movedLine, video == null ? null : video.translated(dx, dy));
        mapped.put(this, result);
        return result;
    }

    public static PaintCommand rect(LayoutBox box, LayoutBox clip, int color) {
        return new PaintCommand(Kind.RECT, box, clip, color, null, null, null, null, null);
    }

    public static PaintCommand text(LayoutBox box, LayoutBox clip, TextLayout text) {
        return new PaintCommand(
                Kind.TEXT, box, clip, text.style.color, text, null, null, null, null);
    }
}
