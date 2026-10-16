package club.heiqi.uilib.ui.scene.video;

import club.heiqi.uilib.ui.media.MediaVideoFrame;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import java.util.Objects;

/** VIDEO 的纯值定位；帧私有像素由媒体合同保持不可变，不引用会话或 GPU。 */
public final class VideoPlacement {
    public enum Fit { CONTAIN, COVER, STRETCH }
    public final MediaVideoFrame frame;
    public final float x, y, width, height;
    private VideoPlacement(MediaVideoFrame frame, float x, float y, float width, float height) {
        this.frame = frame; this.x = x; this.y = y; this.width = width; this.height = height;
    }
    public static VideoPlacement fit(MediaVideoFrame frame, LayoutBox box, Fit fit, float alignX, float alignY) {
        Objects.requireNonNull(frame, "frame"); Objects.requireNonNull(box, "box"); Objects.requireNonNull(fit, "fit");
        if (!Float.isFinite(alignX) || !Float.isFinite(alignY) || alignX < 0 || alignX > 1 || alignY < 0 || alignY > 1)
            throw new IllegalArgumentException("视频对齐须在0到1之间");
        float w = box.width, h = box.height;
        if (fit != Fit.STRETCH) {
            float sx = (float) box.width / frame.width, sy = (float) box.height / frame.height;
            float scale = fit == Fit.COVER ? Math.max(sx, sy) : Math.min(sx, sy);
            w = frame.width * scale; h = frame.height * scale;
        }
        return new VideoPlacement(frame, box.x + (box.width - w) * alignX, box.y + (box.height - h) * alignY, w, h);
    }
}
