package club.heiqi.uilib.ui.layout;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.ui.dom.DocumentNode;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.TextNode;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine.LayoutContext;
import club.heiqi.uilib.ui.layout.TextLayoutHelper.TextWrapSegment;
import club.heiqi.uilib.ui.style.cascade.ComputedStyle;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiTextAlign;
import club.heiqi.uilib.ui.style.props.UiTextOverflow;
import club.heiqi.uilib.ui.style.props.UiVerticalAlign;
import club.heiqi.uilib.ui.style.props.UiWhiteSpace;
import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.text.TextMeasureService;

/**
 * block 容器内的 inline formatting 辅助布局器。
 */
final class InlineLayoutHelper {

    private InlineLayoutHelper() {}

    static boolean hasVisibleInlineElementChild(ElementNode element, LayoutContext layoutContext) {
        for (DocumentNode child : DocumentLayoutEngine.getGeneratedChildNodes(element, layoutContext)) {
            if (!(child instanceof ElementNode)) {
                continue;
            }
            ElementNode childElement = (ElementNode) child;
            ComputedStyle childStyle = layoutContext.computeStyle(childElement);
            if (isInlineFormattingDisplay(childStyle.getDisplay())
                    && !DocumentLayoutEngine.isOutOfFlowPositioned(childStyle)) {
                return true;
            }
        }
        return false;
    }

    static int appendTextRun(TextNode textNode, ElementNode ownerElement, ComputedStyle ownerStyle,
            List<DocumentLayoutTextRun> textRuns, int left, int top, int availableWidth, int firstLineIndent,
            TextMeasureService textMeasureService) {
        TextContentMode textContentMode = textNode.getTextContentMode();
        String text = TextLayoutHelper.normalizeTextForLayout(textNode.getText(), ownerStyle, textContentMode);
        if (text == null || text.isEmpty()) {
            return top;
        }
        int lineHeight = TextLayoutHelper.resolveTextLineHeight(textMeasureService, ownerStyle);
        UiWhiteSpace whiteSpace = ownerStyle != null ? ownerStyle.getWhiteSpace() : UiWhiteSpace.NORMAL;
        UiTextOverflow textOverflow = ownerStyle != null ? ownerStyle.getTextOverflow() : UiTextOverflow.CLIP;
        UiTextAlign textAlign = ownerStyle != null ? ownerStyle.getTextAlign() : UiTextAlign.START;

        String remainingText = text;
        int lineTop = top;
        int lineIndex = 0;
        while (!remainingText.isEmpty()) {
            int lineIndent = lineIndex == 0 ? firstLineIndent : 0;
            int lineAvailableWidth = Math.max(0, availableWidth - lineIndent);
            int segmentAvailableWidth = TextLayoutHelper.allowsSoftWrapping(whiteSpace)
                    ? lineAvailableWidth
                    : Integer.MAX_VALUE / 2;
            TextWrapSegment segment = TextLayoutHelper.takeNextTextSegment(remainingText, segmentAvailableWidth,
                    ownerStyle, textContentMode, textMeasureService);
            if (segment.consumedLength <= 0) {
                break;
            }
            String resolvedLine = segment.text == null ? "" : segment.text;
            int rawWidth = TextLayoutHelper.toUiTextSize(TextLayoutHelper.measureTextWidth(textMeasureService,
                    resolvedLine, textContentMode, ownerStyle));
            if (whiteSpace == UiWhiteSpace.NOWRAP && textOverflow == UiTextOverflow.ELLIPSIS
                    && rawWidth > availableWidth && availableWidth > 0) {
                String ellipsis = "\u2026";
                int ellipsisWidth = TextLayoutHelper.toUiTextSize(TextLayoutHelper.measureTextWidth(textMeasureService,
                        ellipsis, textContentMode, ownerStyle));
                int targetWidth = Math.max(0, availableWidth - ellipsisWidth);
                String trimmed = TextLayoutHelper.trimTextToWidth(textMeasureService, resolvedLine,
                        TextLayoutHelper.toRawTextSize(targetWidth), textContentMode, ownerStyle);
                if (trimmed == null) {
                    trimmed = "";
                }
                resolvedLine = trimmed + ellipsis;
                rawWidth = Math.min(availableWidth,
                        TextLayoutHelper.toUiTextSize(TextLayoutHelper.measureTextWidth(textMeasureService,
                                resolvedLine, textContentMode, ownerStyle)));
            }
            int width = Math.max(0, Math.min(lineAvailableWidth, rawWidth));
            int lineLeft = TextLayoutHelper.resolveTextAlignOffset(textAlign, lineAvailableWidth, width)
                    + left + lineIndent;
            textRuns.add(new DocumentLayoutTextRun(textNode, ownerElement, resolvedLine, textContentMode, lineLeft,
                    lineTop, width, lineHeight));
            lineTop += lineHeight;
            lineIndex++;
            remainingText = remainingText.substring(Math.min(segment.consumedLength, remainingText.length()));
        }
        return lineTop;
    }

    static void appendInlineElementTextRuns(ElementNode inlineElement, InlineLayoutContext inlineLayoutContext,
            LayoutContext layoutContext) {
        appendInlineElementTextRuns(inlineElement, inlineLayoutContext, layoutContext,
                new ArrayList<InlineFragmentOwner>());
    }

    static void appendInlineTextRun(TextNode textNode, ElementNode ownerElement,
            InlineLayoutContext inlineLayoutContext, LayoutContext layoutContext) {
        appendInlineTextRun(textNode, ownerElement, new ArrayList<InlineFragmentOwner>(), inlineLayoutContext,
                layoutContext);
    }

    static List<DocumentLayoutInlineFragment> mergeInlineFragments(
            List<DocumentLayoutInlineFragment> inlineFragments) {
        List<DocumentLayoutInlineFragment> mergedFragments = new ArrayList<DocumentLayoutInlineFragment>();
        for (DocumentLayoutInlineFragment inlineFragment : inlineFragments) {
            int mergeIndex = findMergeableInlineFragment(mergedFragments, inlineFragment);
            if (mergeIndex < 0) {
                mergedFragments.add(inlineFragment);
                continue;
            }
            DocumentLayoutInlineFragment existingFragment = mergedFragments.get(mergeIndex);
            int left = Math.min(existingFragment.getLeft(), inlineFragment.getLeft());
            int top = Math.min(existingFragment.getTop(), inlineFragment.getTop());
            int right = Math.max(existingFragment.getRight(), inlineFragment.getRight());
            int bottom = Math.max(existingFragment.getBottom(), inlineFragment.getBottom());
            mergedFragments.set(mergeIndex, new DocumentLayoutInlineFragment(existingFragment.getOwnerElement(), left,
                    top, right - left, bottom - top, existingFragment.isFirstForElement(),
                    existingFragment.isLastForElement()));
        }
        return mergedFragments;
    }

    static List<DocumentLayoutInlineFragment> markInlineFragmentSequence(
            List<DocumentLayoutInlineFragment> inlineFragments) {
        List<DocumentLayoutInlineFragment> markedFragments = new ArrayList<DocumentLayoutInlineFragment>();
        for (DocumentLayoutInlineFragment inlineFragment : inlineFragments) {
            boolean first = true;
            boolean last = true;
            for (DocumentLayoutInlineFragment otherFragment : inlineFragments) {
                if (otherFragment == inlineFragment || otherFragment.getOwnerElement() != inlineFragment.getOwnerElement()) {
                    continue;
                }
                if (isInlineFragmentBefore(otherFragment, inlineFragment)) {
                    first = false;
                }
                if (isInlineFragmentBefore(inlineFragment, otherFragment)) {
                    last = false;
                }
            }
            markedFragments.add(new DocumentLayoutInlineFragment(inlineFragment.getOwnerElement(), inlineFragment.getLeft(),
                    inlineFragment.getTop(), inlineFragment.getWidth(), inlineFragment.getHeight(), first, last));
        }
        return markedFragments;
    }

    private static void appendInlineElementTextRuns(ElementNode inlineElement, InlineLayoutContext inlineLayoutContext,
            LayoutContext layoutContext,
            List<InlineFragmentOwner> ancestorInlineElements) {
        InlineElementEdges edges = resolveInlineElementEdges(inlineElement, inlineLayoutContext.getLineWidth(),
                layoutContext);
        List<InlineFragmentOwner> fragmentOwners = new ArrayList<InlineFragmentOwner>(ancestorInlineElements);
        fragmentOwners.add(new InlineFragmentOwner(inlineElement, edges.getVerticalTop(),
                edges.getVerticalBottom(), layoutContext.computeStyle(inlineElement).getVerticalAlign()));
        appendInlineSpacing(ancestorInlineElements, inlineLayoutContext, edges.margin.getLeft());
        appendInlineSpacing(fragmentOwners, inlineLayoutContext, edges.border.getLeft() + edges.padding.getLeft());
        for (DocumentNode child : DocumentLayoutEngine.getGeneratedChildNodes(inlineElement, layoutContext)) {
            if (child instanceof TextNode) {
                appendInlineTextRun((TextNode) child, inlineElement, fragmentOwners, inlineLayoutContext,
                        layoutContext);
                continue;
            }
            if (!(child instanceof ElementNode)) {
                continue;
            }
            ElementNode childElement = (ElementNode) child;
            ComputedStyle childStyle = layoutContext.computeStyle(childElement);
            if (childStyle.getDisplay() == UiDisplay.NONE || DocumentLayoutEngine.isOutOfFlowPositioned(childStyle)) {
                continue;
            }
            appendInlineElementTextRuns(childElement, inlineLayoutContext, layoutContext, fragmentOwners);
        }
        appendInlineSpacing(fragmentOwners, inlineLayoutContext, edges.padding.getRight() + edges.border.getRight());
        appendInlineSpacing(ancestorInlineElements, inlineLayoutContext, edges.margin.getRight());
    }

    private static void appendInlineTextRun(TextNode textNode, ElementNode ownerElement,
            List<InlineFragmentOwner> fragmentOwners, InlineLayoutContext inlineLayoutContext,
            LayoutContext layoutContext) {
        ComputedStyle ownerStyle = layoutContext.computeStyle(ownerElement);
        TextMeasureService textMeasureService = layoutContext.textMeasureService;
        TextContentMode textContentMode = textNode.getTextContentMode();
        String remainingText = TextLayoutHelper.normalizeTextForLayout(textNode.getText(), ownerStyle, textContentMode);
        if (remainingText == null || remainingText.isEmpty() || inlineLayoutContext.getLineWidth() <= 0) {
            return;
        }
        UiWhiteSpace whiteSpace = ownerStyle.getWhiteSpace();
        while (!remainingText.isEmpty()) {
            if (TextLayoutHelper.allowsSoftWrapping(whiteSpace)
                    && inlineLayoutContext.getRemainingWidth() <= 0 && inlineLayoutContext.hasLineContent()) {
                inlineLayoutContext.nextLine();
            }
            int remainingWidth = inlineLayoutContext.getRemainingWidth();
            if (remainingWidth <= 0 && TextLayoutHelper.allowsSoftWrapping(whiteSpace)) {
                return;
            }
            int segmentAvailableWidth = TextLayoutHelper.allowsSoftWrapping(whiteSpace)
                    ? remainingWidth : Integer.MAX_VALUE / 2;
            TextWrapSegment segmentResult = TextLayoutHelper.takeNextTextSegment(remainingText, segmentAvailableWidth,
                    ownerStyle, textContentMode, textMeasureService);
            String segment = segmentResult.text;
            if (segment.isEmpty()) {
                remainingText = remainingText.substring(Math.min(segmentResult.consumedLength, remainingText.length()));
                if (segmentResult.forceLineBreak) {
                    inlineLayoutContext.forceLineBreak();
                }
                continue;
            }
            int width = Math.max(0, TextLayoutHelper.toUiTextSize(TextLayoutHelper.measureTextWidth(textMeasureService,
                    segment, textContentMode, ownerStyle)));
            if (TextLayoutHelper.allowsSoftWrapping(whiteSpace)
                    && width > remainingWidth && inlineLayoutContext.hasLineContent()) {
                inlineLayoutContext.nextLine();
                continue;
            }
            if (TextLayoutHelper.allowsSoftWrapping(whiteSpace)) {
                width = Math.min(width, inlineLayoutContext.getRemainingWidth());
            }
            inlineLayoutContext.appendTextRun(textNode, ownerElement, segment, inlineLayoutContext.getCursorLeft(),
                    width, fragmentOwners);
            appendInlineFragments(fragmentOwners, inlineLayoutContext.getCursorLeft(), width, inlineLayoutContext);
            inlineLayoutContext.advance(width);
            remainingText = remainingText.substring(Math.min(segmentResult.consumedLength, remainingText.length()));
            if (segmentResult.forceLineBreak) {
                inlineLayoutContext.forceLineBreak();
                continue;
            }
            if (TextLayoutHelper.allowsSoftWrapping(whiteSpace)
                    && !remainingText.isEmpty() && inlineLayoutContext.getRemainingWidth() <= 0) {
                inlineLayoutContext.nextLine();
            }
        }
    }

    private static void appendInlineSpacing(List<InlineFragmentOwner> fragmentOwners,
            InlineLayoutContext inlineLayoutContext, int width) {
        int remainingSpacing = Math.max(0, width);
        while (remainingSpacing > 0 && inlineLayoutContext.getLineWidth() > 0) {
            if (inlineLayoutContext.getRemainingWidth() <= 0 && inlineLayoutContext.hasLineContent()) {
                inlineLayoutContext.nextLine();
            }
            int remainingWidth = inlineLayoutContext.getRemainingWidth();
            if (remainingWidth <= 0) {
                return;
            }
            int chunkWidth = Math.min(remainingSpacing, remainingWidth);
            appendInlineFragments(fragmentOwners, inlineLayoutContext.getCursorLeft(), chunkWidth, inlineLayoutContext);
            inlineLayoutContext.advance(chunkWidth);
            remainingSpacing -= chunkWidth;
        }
    }

    private static void appendInlineFragments(List<InlineFragmentOwner> fragmentOwners, int left, int width,
            InlineLayoutContext inlineLayoutContext) {
        if (width <= 0) {
            return;
        }
        for (InlineFragmentOwner fragmentOwner : fragmentOwners) {
            inlineLayoutContext.appendInlineFragment(fragmentOwner, left, width);
        }
    }

    private static boolean isInlineFragmentBefore(DocumentLayoutInlineFragment first,
            DocumentLayoutInlineFragment second) {
        if (first.getTop() != second.getTop()) {
            return first.getTop() < second.getTop();
        }
        return first.getLeft() < second.getLeft();
    }

    private static int findMergeableInlineFragment(List<DocumentLayoutInlineFragment> mergedFragments,
            DocumentLayoutInlineFragment inlineFragment) {
        for (int index = mergedFragments.size() - 1; index >= 0; index--) {
            DocumentLayoutInlineFragment existingFragment = mergedFragments.get(index);
            if (existingFragment.getOwnerElement() != inlineFragment.getOwnerElement()
                    || existingFragment.getTop() != inlineFragment.getTop()
                    || existingFragment.getBottom() != inlineFragment.getBottom()) {
                continue;
            }
            if (inlineFragment.getLeft() <= existingFragment.getRight()
                    && inlineFragment.getRight() >= existingFragment.getLeft()) {
                return index;
            }
        }
        return -1;
    }

    private static InlineElementEdges resolveInlineElementEdges(ElementNode inlineElement, int lineWidth,
            LayoutContext layoutContext) {
        ComputedStyle style = layoutContext.computeStyle(inlineElement);
        return new InlineElementEdges(DocumentLayoutEngine.resolveMarginInsets(inlineElement, style, lineWidth,
                layoutContext.layoutValueResolver), DocumentLayoutEngine.resolveBorderInsets(style, lineWidth),
                DocumentLayoutEngine.resolvePaddingInsets(inlineElement, style, lineWidth,
                        layoutContext.layoutValueResolver));
    }

    private static boolean isInlineFormattingDisplay(UiDisplay display) {
        return display == UiDisplay.INLINE || display == UiDisplay.INLINE_BLOCK;
    }

    /**
     * block 容器内的首期 inline 文本排版游标。
     */
    static final class InlineLayoutContext {

        private final int baseLineLeft;
        private final int baseLineWidth;
        private final int baseLineHeight;
        private final int firstLineIndent;
        private final List<DocumentLayoutTextRun> textRuns;
        private final List<DocumentLayoutInlineFragment> inlineFragments;
        private final List<PendingInlineTextRun> pendingTextRuns = new ArrayList<PendingInlineTextRun>();
        private final List<PendingInlineFragment> pendingInlineFragments = new ArrayList<PendingInlineFragment>();
        private int lineLeft;
        private int lineWidth;
        private int lineTop;
        private int cursorLeft;
        private int maxBaselineTopEdge;
        private int maxBaselineBottomEdge;
        private int maxAlignedItemHeight;
        private boolean lineContentPresent;

        InlineLayoutContext(int lineLeft, int lineTop, int lineWidth, int lineHeight, int firstLineIndent,
                List<DocumentLayoutTextRun> textRuns, List<DocumentLayoutInlineFragment> inlineFragments) {
            this.baseLineLeft = lineLeft;
            this.baseLineWidth = Math.max(0, lineWidth);
            this.lineTop = lineTop;
            this.baseLineHeight = Math.max(1, lineHeight);
            this.firstLineIndent = firstLineIndent;
            this.textRuns = textRuns;
            this.inlineFragments = inlineFragments;
            applyLineStart(true);
        }

        private void applyLineStart(boolean firstLine) {
            int indent = firstLine ? firstLineIndent : 0;
            lineLeft = baseLineLeft + indent;
            lineWidth = Math.max(0, baseLineWidth - indent);
            cursorLeft = lineLeft;
        }

        int getLineWidth() {
            return lineWidth;
        }

        int getCursorLeft() {
            return cursorLeft;
        }

        int getLineTop() {
            return lineTop;
        }

        int getRemainingWidth() {
            return Math.max(0, lineLeft + lineWidth - cursorLeft);
        }

        boolean hasLineContent() {
            return lineContentPresent;
        }

        private void appendTextRun(TextNode textNode, ElementNode ownerElement, String text, int left, int width,
                List<InlineFragmentOwner> fragmentOwners) {
            if (width <= 0) {
                return;
            }
            InlineFragmentOwner innermostOwner = fragmentOwners.isEmpty() ? null
                    : fragmentOwners.get(fragmentOwners.size() - 1);
            pendingTextRuns.add(new PendingInlineTextRun(textNode, ownerElement, text, left, width, innermostOwner));
        }

        private void appendInlineFragment(InlineFragmentOwner owner, int left, int width) {
            if (width <= 0) {
                return;
            }
            pendingInlineFragments.add(new PendingInlineFragment(owner, left, width));
        }

        private void advance(int width) {
            cursorLeft += Math.max(0, width);
            lineContentPresent = true;
        }

        void appendInlineBlock(int width, int height) {
            cursorLeft += Math.max(0, width);
            maxAlignedItemHeight = Math.max(maxAlignedItemHeight, Math.max(1, height));
            lineContentPresent = true;
        }

        void nextLine() {
            breakLine(false);
        }

        void forceLineBreak() {
            breakLine(true);
        }

        private void breakLine(boolean forceAdvanceIfEmpty) {
            int nextLineTop = lineContentPresent ? getFlowBottom()
                    : forceAdvanceIfEmpty ? lineTop + baseLineHeight : lineTop;
            flushCurrentLine();
            lineTop = nextLineTop;
            resetCurrentLineState();
        }

        int getFlowBottom() {
            return lineContentPresent ? lineTop + getCurrentLineHeight() : lineTop;
        }

        int finishLineAndGetBottom() {
            int bottom = getFlowBottom();
            flushCurrentLine();
            lineTop = bottom;
            resetCurrentLineState();
            return bottom;
        }

        void reset(int nextLineTop) {
            flushCurrentLine();
            lineTop = nextLineTop;
            resetCurrentLineState();
        }

        private int getCurrentLineHeight() {
            int baselineHeight = maxBaselineTopEdge + baseLineHeight + maxBaselineBottomEdge;
            return Math.max(Math.max(baseLineHeight, baselineHeight), maxAlignedItemHeight);
        }

        private void flushCurrentLine() {
            if (!lineContentPresent) {
                return;
            }
            int lineHeight = getCurrentLineHeight();
            for (PendingInlineFragment pendingInlineFragment : pendingInlineFragments) {
                InlineFragmentOwner owner = pendingInlineFragment.owner;
                int itemHeight = owner.getHeight(baseLineHeight);
                int fragmentOffset = owner.verticalAlign == UiVerticalAlign.BASELINE
                        ? maxBaselineTopEdge - owner.topEdge
                        : resolveInlineVerticalOffset(owner.verticalAlign, lineHeight, itemHeight);
                int fragmentTop = lineTop + fragmentOffset;
                int fragmentHeight = pendingInlineFragment.owner.topEdge + baseLineHeight
                        + pendingInlineFragment.owner.bottomEdge;
                inlineFragments.add(new DocumentLayoutInlineFragment(pendingInlineFragment.owner.element,
                        pendingInlineFragment.left, fragmentTop, pendingInlineFragment.width, fragmentHeight));
            }
            for (PendingInlineTextRun pendingTextRun : pendingTextRuns) {
                int textTop = resolveInlineTextTop(lineHeight, pendingTextRun);
                textRuns.add(new DocumentLayoutTextRun(pendingTextRun.textNode, pendingTextRun.ownerElement,
                        pendingTextRun.text, pendingTextRun.left, textTop, pendingTextRun.width, baseLineHeight));
            }
        }

        private int resolveInlineTextTop(int lineHeight, PendingInlineTextRun pendingTextRun) {
            if (pendingTextRun.verticalAlign == UiVerticalAlign.BASELINE) {
                return lineTop + maxBaselineTopEdge;
            }
            int itemHeight = pendingTextRun.topEdge + baseLineHeight + pendingTextRun.bottomEdge;
            return lineTop + resolveInlineVerticalOffset(pendingTextRun.verticalAlign, lineHeight, itemHeight)
                    + pendingTextRun.topEdge;
        }

        private static int resolveInlineVerticalOffset(UiVerticalAlign verticalAlign, int lineHeight, int itemHeight) {
            int safeLineHeight = Math.max(1, lineHeight);
            int safeItemHeight = Math.max(1, itemHeight);
            int remaining = Math.max(0, safeLineHeight - safeItemHeight);
            if (verticalAlign == UiVerticalAlign.TOP) {
                return 0;
            }
            if (verticalAlign == UiVerticalAlign.MIDDLE) {
                return remaining / 2;
            }
            if (verticalAlign == UiVerticalAlign.BOTTOM) {
                return remaining;
            }
            return 0;
        }

        private void resetCurrentLineState() {
            applyLineStart(false);
            maxBaselineTopEdge = 0;
            maxBaselineBottomEdge = 0;
            maxAlignedItemHeight = 0;
            lineContentPresent = false;
            pendingTextRuns.clear();
            pendingInlineFragments.clear();
        }
    }

    /**
     * inline fragment 所属元素及其垂直表面边距。
     */
    private static final class InlineFragmentOwner {

        private final ElementNode element;
        private final int topEdge;
        private final int bottomEdge;
        private final UiVerticalAlign verticalAlign;

        private InlineFragmentOwner(ElementNode element, int topEdge, int bottomEdge, UiVerticalAlign verticalAlign) {
            this.element = element;
            this.topEdge = Math.max(0, topEdge);
            this.bottomEdge = Math.max(0, bottomEdge);
            this.verticalAlign = verticalAlign == null ? UiVerticalAlign.BASELINE : verticalAlign;
        }

        private int getHeight(int baseLineHeight) {
            return topEdge + Math.max(1, baseLineHeight) + bottomEdge;
        }
    }

    /**
     * 延迟到行高确定后生成的 inline 文本片段。
     */
    private static final class PendingInlineTextRun {

        private final TextNode textNode;
        private final ElementNode ownerElement;
        private final String text;
        private final int left;
        private final int width;
        private final int topEdge;
        private final int bottomEdge;
        private final UiVerticalAlign verticalAlign;

        private PendingInlineTextRun(TextNode textNode, ElementNode ownerElement, String text, int left, int width,
                InlineFragmentOwner innermostOwner) {
            this.textNode = textNode;
            this.ownerElement = ownerElement;
            this.text = text;
            this.left = left;
            this.width = width;
            this.topEdge = innermostOwner == null ? 0 : innermostOwner.topEdge;
            this.bottomEdge = innermostOwner == null ? 0 : innermostOwner.bottomEdge;
            this.verticalAlign = innermostOwner == null ? UiVerticalAlign.BASELINE : innermostOwner.verticalAlign;
        }
    }

    /**
     * 延迟到行高确定后生成的 inline 元素表面片段。
     */
    private static final class PendingInlineFragment {

        private final InlineFragmentOwner owner;
        private final int left;
        private final int width;

        private PendingInlineFragment(InlineFragmentOwner owner, int left, int width) {
            this.owner = owner;
            this.left = left;
            this.width = width;
        }
    }

    /**
     * inline 元素在行内流中参与占位和表面扩展的盒边。
     */
    private static final class InlineElementEdges {

        private final DocumentLayoutEdges margin;
        private final DocumentLayoutEdges border;
        private final DocumentLayoutEdges padding;

        private InlineElementEdges(DocumentLayoutEdges margin, DocumentLayoutEdges border,
                DocumentLayoutEdges padding) {
            this.margin = margin;
            this.border = border;
            this.padding = padding;
        }

        private int getVerticalTop() {
            return border.getTop() + padding.getTop();
        }

        private int getVerticalBottom() {
            return padding.getBottom() + border.getBottom();
        }
    }
}
