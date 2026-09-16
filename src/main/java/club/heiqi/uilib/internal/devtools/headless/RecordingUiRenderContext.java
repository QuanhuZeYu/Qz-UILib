package club.heiqi.uilib.internal.devtools.headless;

import java.util.List;

import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.ui.render.PaintContextCompositor;
import club.heiqi.uilib.ui.render.UiMainLayerSnapshotService;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.runtime.UiRuntimeAdapters;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;

/**
 * 记录命令面的渲染上下文：子类化生产 {@link UiRenderContext}，「先记账、再转发给 super」。
 *
 * <p>为什么用子类而不是装饰器：仓内多处按 {@code backend instanceof UiRenderContext} 解析像素上下文
 * （玻璃快照 / 圆角 / 离屏层），装饰器会破坏这些解析并让像素路径静默降级；
 * 子类保持同一身份，像素路径仍是生产实现，记录只是旁路副作用。</p>
 *
 * <p>计数去重：{@code drawText} 的 5/6/7 参重载在 {@code UiRenderContext} 内部互相转发，
 * 直接逐个覆写会重复计数，故用重入标志保证同一次绘制只记一次。</p>
 */
final class RecordingUiRenderContext extends UiRenderContext {

    private final HeadlessDrawSummary summary;
    private boolean inText;

    RecordingUiRenderContext(int width, int height, PaintContextCompositor paintContextCompositor,
            UiMainLayerSnapshotService mainLayerSnapshotService) {
        super(width, height, 0, 0, 0f, paintContextCompositor, mainLayerSnapshotService,
                UiRuntimeAdapters.empty());
        this.summary = new HeadlessDrawSummary(width, height);
    }

    /** @return 最近一帧的命令面摘要 */
    HeadlessDrawSummary summary() {
        return summary;
    }

    /** 开始新一帧：清零上一帧的指纹，保证摘要与像素说的是同一帧。 */
    void resetFrame() {
        summary.reset();
        inText = false;
    }

    @Override
    public void fillRect(int left, int top, int right, int bottom, int color) {
        summary.recordFillRect(left, top, right, bottom);
        super.fillRect(left, top, right, bottom, color);
    }

    @Override
    public void drawSurface(int left, int top, int right, int bottom, int fillColor, int borderColor,
            int cornerRadius) {
        summary.recordDrawSurface(left, top, right, bottom);
        super.drawSurface(left, top, right, bottom, fillColor, borderColor, cornerRadius);
    }

    @Override
    public void drawSurface(int left, int top, int right, int bottom, int fillColor, int borderColor,
            int cornerRadiusTopLeft, int cornerRadiusTopRight, int cornerRadiusBottomRight,
            int cornerRadiusBottomLeft) {
        summary.recordDrawSurface(left, top, right, bottom);
        super.drawSurface(left, top, right, bottom, fillColor, borderColor, cornerRadiusTopLeft,
                cornerRadiusTopRight, cornerRadiusBottomRight, cornerRadiusBottomLeft);
    }

    @Override
    public void drawBorder(int left, int top, int right, int bottom, int color) {
        summary.recordDrawBorder(left, top, right, bottom);
        super.drawBorder(left, top, right, bottom, color);
    }

    @Override
    public void pushClip(int left, int top, int right, int bottom, int cornerRadius) {
        summary.recordClipPush(left, top, right, bottom);
        super.pushClip(left, top, right, bottom, cornerRadius);
    }

    @Override
    public void popClip() {
        summary.recordClipPop();
        super.popClip();
    }

    @Override
    public void drawText(String text, int x, int y, int color, boolean shadow) {
        if (!inText) {
            summary.recordText(text);
        }
        inText = true;
        try {
            super.drawText(text, x, y, color, shadow);
        } finally {
            inText = false;
        }
    }

    @Override
    public void drawText(String text, int x, int y, int color, boolean shadow, int fontSizePx) {
        if (!inText) {
            summary.recordText(text);
        }
        inText = true;
        try {
            super.drawText(text, x, y, color, shadow, fontSizePx);
        } finally {
            inText = false;
        }
    }

    @Override
    public void drawText(String text, int x, int y, int color, boolean shadow, int fontSizePx, int textMode) {
        if (!inText) {
            summary.recordText(text);
        }
        inText = true;
        try {
            super.drawText(text, x, y, color, shadow, fontSizePx, textMode);
        } finally {
            inText = false;
        }
    }

    @Override
    public void drawSegments(List<TextSegment> segments, int x, int y, int fontSizePx) {
        summary.recordSegments(segments);
        super.drawSegments(segments, x, y, fontSizePx);
    }

    @Override
    public void drawImage(SceneImageSource source, int left, int top, int right, int bottom) {
        summary.recordImage();
        super.drawImage(source, left, top, right, bottom);
    }

    @Override
    public void pushGroupOpacity(int left, int top, int right, int bottom, float opacity) {
        summary.recordGroupOpacity();
        super.pushGroupOpacity(left, top, right, bottom, opacity);
    }

    @Override
    public void publishTextDemand(List<String> texts) {
        summary.recordTextDemand(texts);
        super.publishTextDemand(texts);
    }
}
