package club.heiqi.uilib.ui.paint;

import java.util.List;

import club.heiqi.uilib.ui.layout.DocumentEffectChain;
import club.heiqi.uilib.ui.layout.DocumentLayoutTextRun;
import club.heiqi.uilib.ui.layout.DocumentVisualTraversal.ClipContext;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.props.UiFontStyle;
import club.heiqi.uilib.ui.style.props.UiFontWeight;
import club.heiqi.uilib.ui.style.values.UiTextShadow;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * 文本绘制阶段的保守可见性裁剪器。
 */
final class DocumentTextPaintClipper {

    private static final float UI_TEXT_SCALE = 2.0F;
    private static final int TEXT_VISIBILITY_PADDING = 8;
    private static final int TEXT_HORIZONTAL_OVERDRAW = 16;
    private static final int LONG_TEXT_CLIP_THRESHOLD = 64;
    private static final int UNBOUNDED_TEXT_RIGHT = Integer.MAX_VALUE / 4;

    private DocumentTextPaintClipper() {}

    /**
     * 根据当前 overflow clip 链解析文本矩形裁剪交集。
     *
     * @param activeClipChain 当前生效的 clip 链
     * @return clip 交集；没有 clip 时返回 null
     */
    static ClipBounds resolveClipBounds(List<ClipContext> activeClipChain) {
        if (activeClipChain == null || activeClipChain.isEmpty()) {
            return null;
        }
        ClipBounds bounds = null;
        for (ClipContext clipContext : activeClipChain) {
            DocumentEffectChain.ClipBounds clipBounds = clipContext.getEffectChain().resolveChildClipBounds(
                    clipContext.getBoxOffsetX(), clipContext.getBoxOffsetY());
            if (bounds == null) {
                bounds = new ClipBounds(clipBounds.getLeft(), clipBounds.getTop(), clipBounds.getRight(),
                        clipBounds.getBottom());
            } else {
                bounds = bounds.intersect(clipBounds.getLeft(), clipBounds.getTop(), clipBounds.getRight(),
                        clipBounds.getBottom());
            }
            if (bounds.isEmpty()) {
                return bounds;
            }
        }
        return bounds;
    }

    /**
     * 解析文本 run 的实际绘制包围盒。
     *
     * @param textRun 文本布局 run
     * @param ownerStyle 文本所属元素样式
     * @param textMeasureService 文本测量服务；为空时只使用布局宽度
     * @param offsetX 当前内容 X 偏移
     * @param offsetY 当前内容 Y 偏移
     * @return 文本绘制包围盒
     */
    static PaintBounds resolvePaintBounds(DocumentLayoutTextRun textRun, ComputedStyle ownerStyle,
            TextMeasureService textMeasureService, int offsetX, int offsetY, boolean clipActive,
            boolean measureLongText) {
        int left = textRun.getLeft() + offsetX;
        int top = textRun.getTop() + offsetY;
        boolean needsMeasuredWidth = clipActive && measureLongText && textMeasureService != null
                && textRun.getText().length() >= LONG_TEXT_CLIP_THRESHOLD;
        int measuredWidth = needsMeasuredWidth ? measureUiTextWidth(textMeasureService, textRun.getText(),
                textRun.getTextContentMode(), ownerStyle) : 0;
        int paintRight;
        if (clipActive && textMeasureService == null && textRun.getText().length() >= LONG_TEXT_CLIP_THRESHOLD) {
            paintRight = UNBOUNDED_TEXT_RIGHT;
        } else {
            paintRight = left + Math.max(textRun.getWidth(), measuredWidth);
        }
        return new PaintBounds(left, top, paintRight, textRun.getBottom() + offsetY, measuredWidth);
    }

    /**
     * 判断文本绘制盒在膨胀后是否与 clip 相交。
     *
     * @param paintBounds 文本绘制包围盒
     * @param clipBounds clip 交集
     * @param expansion 保守膨胀距离
     * @return 是否可能可见
     */
    static boolean intersectsExpanded(PaintBounds paintBounds, ClipBounds clipBounds, int expansion) {
        if (clipBounds == null) {
            return true;
        }
        int resolvedExpansion = Math.max(0, expansion);
        return paintBounds.getRight() + resolvedExpansion > clipBounds.getLeft()
                && paintBounds.getLeft() - resolvedExpansion < clipBounds.getRight()
                && paintBounds.getBottom() + resolvedExpansion > clipBounds.getTop()
                && paintBounds.getTop() - resolvedExpansion < clipBounds.getBottom();
    }

    /**
     * 解析当前文本 run 最终需要提交给绘制命令的片段。
     *
     * @param textRun 文本布局 run
     * @param paintBounds 文本实际绘制包围盒
     * @param ownerStyle 文本所属元素样式
     * @param textMeasureService 文本测量服务
     * @param clipBounds 当前 clip 交集
     * @param expansion 保守膨胀距离
     * @return 需要提交绘制的文本片段
     */
    static PaintSlice resolveVisibleSlice(DocumentLayoutTextRun textRun, PaintBounds paintBounds,
            ComputedStyle ownerStyle, TextMeasureService textMeasureService, ClipBounds clipBounds, int expansion) {
        String text = textRun.getText();
        if (!canClipTextRunHorizontally(textRun, paintBounds, textMeasureService, clipBounds)) {
            return new PaintSlice(text, paintBounds.getLeft(), paintBounds.getLeft() + textRun.getWidth());
        }

        int textLeft = paintBounds.getLeft();
        int overdraw = Math.max(TEXT_HORIZONTAL_OVERDRAW, expansion);
        int visibleStartOffset = Math.max(0, clipBounds.getLeft() - textLeft - overdraw);
        int visibleEndOffset = Math.min(paintBounds.getMeasuredWidth(), clipBounds.getRight() - textLeft + overdraw);
        if (visibleEndOffset <= visibleStartOffset) {
            return new PaintSlice("", textLeft, textLeft);
        }

        int[] boundaries = collectRawTextBoundaries(text);
        int startIndex = findPrefixBoundaryForWidth(text, boundaries, visibleStartOffset, false, textMeasureService,
                textRun.getTextContentMode(), ownerStyle);
        int endIndex = findPrefixBoundaryForWidth(text, boundaries, visibleEndOffset, true, textMeasureService,
                textRun.getTextContentMode(), ownerStyle);
        if (endIndex <= startIndex) {
            endIndex = nextRawTextBoundary(boundaries, startIndex);
        }
        startIndex = Math.max(0, Math.min(startIndex, text.length()));
        endIndex = Math.max(startIndex, Math.min(endIndex, text.length()));
        String clippedText = text.substring(startIndex, endIndex);
        int prefixWidth = measureUiTextWidth(textMeasureService, text.substring(0, startIndex),
                textRun.getTextContentMode(), ownerStyle);
        int clippedWidth = measureUiTextWidth(textMeasureService, clippedText, textRun.getTextContentMode(),
                ownerStyle);
        int left = textLeft + prefixWidth;
        return new PaintSlice(clippedText, left, left + clippedWidth);
    }

    /**
     * 解析文本阴影和装饰导致的保守可见性膨胀距离。
     *
     * @param ownerStyle 文本所属元素样式
     * @return 膨胀距离
     */
    static int resolveVisualExpansion(ComputedStyle ownerStyle) {
        int expansion = TEXT_VISIBILITY_PADDING;
        if (ownerStyle == null) {
            return expansion;
        }
        UiTextShadow textShadow = ownerStyle.getTextShadow();
        if (textShadow == null || ((textShadow.getColor() >>> 24) & 0xFF) == 0) {
            return expansion;
        }
        int blurRadius = Math.min(Math.max(0, textShadow.getBlurRadius()), 3);
        return expansion + Math.max(Math.abs(textShadow.getOffsetX()), Math.abs(textShadow.getOffsetY()))
                + blurRadius + 2;
    }

    private static boolean canClipTextRunHorizontally(DocumentLayoutTextRun textRun, PaintBounds paintBounds,
            TextMeasureService textMeasureService, ClipBounds clipBounds) {
        if (textMeasureService == null || clipBounds == null
                || textRun.getTextContentMode() != TextContentMode.UILIB_RAW) {
            return false;
        }
        String text = textRun.getText();
        if (text.length() < LONG_TEXT_CLIP_THRESHOLD || paintBounds.getMeasuredWidth() <= 0) {
            return false;
        }
        int clippedWidth = Math.max(0, Math.min(paintBounds.getRight(), clipBounds.getRight())
                - Math.max(paintBounds.getLeft(), clipBounds.getLeft()));
        return clippedWidth + TEXT_HORIZONTAL_OVERDRAW * 2 < paintBounds.getMeasuredWidth();
    }

    private static int[] collectRawTextBoundaries(String text) {
        int boundaryCount = text.codePointCount(0, text.length()) + 1;
        int[] boundaries = new int[boundaryCount];
        int boundaryIndex = 0;
        int textIndex = 0;
        boundaries[boundaryIndex++] = 0;
        while (textIndex < text.length()) {
            textIndex += Character.charCount(text.codePointAt(textIndex));
            boundaries[boundaryIndex++] = textIndex;
        }
        return boundaries;
    }

    private static int findPrefixBoundaryForWidth(String text, int[] boundaries, int targetWidth, boolean ceiling,
            TextMeasureService textMeasureService, TextContentMode textContentMode, ComputedStyle ownerStyle) {
        int low = 0;
        int high = boundaries.length - 1;
        int result = ceiling ? boundaries[boundaries.length - 1] : 0;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            int boundary = boundaries[middle];
            int width = measureUiTextWidth(textMeasureService, text.substring(0, boundary), textContentMode,
                    ownerStyle);
            if (width < targetWidth || !ceiling && width == targetWidth) {
                result = boundary;
                low = middle + 1;
            } else {
                if (ceiling) {
                    result = boundary;
                }
                high = middle - 1;
            }
        }
        return result;
    }

    private static int nextRawTextBoundary(int[] boundaries, int currentBoundary) {
        for (int boundary : boundaries) {
            if (boundary > currentBoundary) {
                return boundary;
            }
        }
        return currentBoundary;
    }

    private static int measureUiTextWidth(TextMeasureService textMeasureService, String text,
            TextContentMode textContentMode, ComputedStyle ownerStyle) {
        if (textMeasureService == null || text == null || text.isEmpty()) {
            return 0;
        }
        UiFontWeight fontWeight = ownerStyle == null ? UiFontWeight.NORMAL : ownerStyle.getFontWeight();
        UiFontStyle fontStyle = ownerStyle == null ? UiFontStyle.NORMAL : ownerStyle.getFontStyle();
        return Math.round(textMeasureService.getStringWidth(text, textContentMode, fontWeight, fontStyle)
                * UI_TEXT_SCALE);
    }

    /**
     * 文本 clip 交集。
     */
    static final class ClipBounds {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        private ClipBounds(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = Math.max(left, right);
            this.bottom = Math.max(top, bottom);
        }

        private ClipBounds intersect(int nextLeft, int nextTop, int nextRight, int nextBottom) {
            return new ClipBounds(Math.max(left, nextLeft), Math.max(top, nextTop), Math.min(right, nextRight),
                    Math.min(bottom, nextBottom));
        }

        private boolean isEmpty() {
            return right <= left || bottom <= top;
        }

        int getLeft() {
            return left;
        }

        int getTop() {
            return top;
        }

        int getRight() {
            return right;
        }

        int getBottom() {
            return bottom;
        }
    }

    /**
     * 文本实际绘制包围盒。
     */
    static final class PaintBounds {

        private final int left;
        private final int top;
        private final int right;
        private final int bottom;
        private final int measuredWidth;

        private PaintBounds(int left, int top, int right, int bottom, int measuredWidth) {
            this.left = left;
            this.top = top;
            this.right = Math.max(left, right);
            this.bottom = Math.max(top, bottom);
            this.measuredWidth = Math.max(0, measuredWidth);
        }

        int getLeft() {
            return left;
        }

        int getTop() {
            return top;
        }

        int getRight() {
            return right;
        }

        int getBottom() {
            return bottom;
        }

        int getMeasuredWidth() {
            return measuredWidth;
        }
    }

    /**
     * 文本最终绘制片段。
     */
    static final class PaintSlice {

        private final String text;
        private final int left;
        private final int right;

        private PaintSlice(String text, int left, int right) {
            this.text = text == null ? "" : text;
            this.left = left;
            this.right = Math.max(left, right);
        }

        String getText() {
            return text;
        }

        int getLeft() {
            return left;
        }

        int getRight() {
            return right;
        }
    }
}
