package club.heiqi.uilib.ui.layout;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimeline;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.text.TextMeasureService;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.props.UiPointerEvents;
import club.heiqi.uilib.ui.style.props.UiPosition;
import club.heiqi.uilib.ui.style.props.UiVisibility;
import club.heiqi.uilib.ui.style.values.UiStyleInsets;
import club.heiqi.uilib.ui.style.values.UiStyleLength;
import club.heiqi.uilib.ui.style.values.UiTransform;

/**
 * `DocumentHitTestEngine` 的 HTML-like 命中测试契约。
 */
public class DocumentHitTestEngineTest {

    /**
     * 验证 z-index 更高的 relative 子元素在视觉重叠区域优先命中。
     */
    @Test
    public void shouldHitRaisedRelativeChildFirst() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode raised = document.div();
        ElementNode normal = document.div();

        root.style().setWidth(UiStyleLength.px(100));
        raised.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(20))
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(16))
                .setZIndex(2);
        normal.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(20));
        root.append(raised).append(normal);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 120, 0);

        assertHitElement(raised, rootBox, 10, 22);
    }

    /**
     * 验证 positioned auto 元素在普通流元素上方命中。
     */
    @Test
    public void shouldHitPositionedAutoAboveNormalFlow() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode positioned = document.div();
        ElementNode normal = document.div();

        root.style().setWidth(UiStyleLength.px(100));
        positioned.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(20))
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(20));
        normal.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(20));
        root.append(positioned).append(normal);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 120, 0);

        assertHitElement(positioned, rootBox, 10, 22);
    }

    /**
     * 验证 absolute 子元素按脱流后的视觉位置参与命中。
     */
    @Test
    public void shouldHitAbsolutePositionedChildAtInsetPosition() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode absolute = document.div();
        ElementNode normal = document.div();

        root.style().setWidth(UiStyleLength.px(100));
        absolute.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(12))
                .setPosition(UiPosition.ABSOLUTE)
                .setTop(UiStyleLength.px(8))
                .setLeft(UiStyleLength.px(6));
        normal.style()
                .setWidth(UiStyleLength.px(60))
                .setHeight(UiStyleLength.px(24));
        root.append(absolute).append(normal);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 120, 0);

        assertHitElement(absolute, rootBox, 10, 10);
        assertHitElement(normal, rootBox, 10, 22);
    }

    /**
     * 验证 absolute 子元素相对最近 positioned ancestor 的位置参与命中。
     */
    @Test
    public void shouldHitAbsolutePositionedChildAgainstNearestPositionedAncestor() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode positioned = document.div();
        ElementNode staticParent = document.div();
        ElementNode absolute = document.div();

        root.style().setWidth(UiStyleLength.px(180));
        positioned.style()
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(60))
                .setPosition(UiPosition.RELATIVE)
                .setBorderWidth(UiStyleLength.px(2))
                .setPadding(UiStyleLength.px(10));
        staticParent.style()
                .setHeight(UiStyleLength.px(20))
                .setPadding(UiStyleLength.px(3));
        absolute.style()
                .setWidth(UiStyleLength.px(12))
                .setHeight(UiStyleLength.px(8))
                .setPosition(UiPosition.ABSOLUTE)
                .setTop(UiStyleLength.px(6))
                .setLeft(UiStyleLength.px(8));
        staticParent.append(absolute);
        positioned.append(staticParent);
        root.append(positioned);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 220, 0);

        assertHitElement(absolute, rootBox, 10, 8);
        assertHitElement(staticParent, rootBox, 34, 28);
    }

    /**
     * 验证 fixed 元素在根滚动后仍按视口固定位置参与命中。
     */
    @Test
    public void shouldHitFixedPositionedChildAfterRootScroll() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode spacer = document.div();
        ElementNode fixed = document.div();

        root.style()
                .setWidth(UiStyleLength.px(100))
                .setHeight(UiStyleLength.px(50))
                .setOverflowY(UiOverflow.AUTO);
        spacer.style().setHeight(UiStyleLength.px(140));
        fixed.style()
                .setWidth(UiStyleLength.px(30))
                .setHeight(UiStyleLength.px(12))
                .setPosition(UiPosition.FIXED)
                .setTop(UiStyleLength.px(6))
                .setLeft(UiStyleLength.px(10));
        root.append(spacer).append(fixed);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 100, 50);
        DocumentScrollState scrollState = new DocumentScrollState();
        scrollState.updateFromLayout(rootBox);
        Assert.assertTrue(scrollState.setScrollOffset(root, 0, 36));

        assertHitElement(fixed, rootBox, scrollState, 12, 8);
        assertHitElement(spacer, rootBox, scrollState, 12, 24);
    }

    /**
     * 验证 inline span 文本片段可以作为最深元素被命中。
     */
    @Test
    public void shouldHitInlineSpanTextRun() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode span = document.span();

        root.style().setWidth(UiStyleLength.px(48));
        root.appendText("AA");
        span.appendText("BBBB");
        root.append(span);
        root.appendText("CC");

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 80, 0);

        assertHitElement(root, rootBox, 4, 5);
        assertHitElement(span, rootBox, 20, 5);
        assertHitElement(root, rootBox, 4, 23);
    }

    /**
     * 验证 inline span 的 padding/border fragment 空白区域也会命中 span 本身。
     */
    @Test
    public void shouldHitInlineSpanFragmentSurfaceOutsideText() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode span = document.span();

        root.style().setWidth(UiStyleLength.px(80));
        span.style()
                .setMargin(UiStyleInsets.of(UiStyleLength.px(0), UiStyleLength.px(6), UiStyleLength.px(0),
                        UiStyleLength.px(4)))
                .setPadding(UiStyleInsets.of(UiStyleLength.px(2), UiStyleLength.px(5), UiStyleLength.px(4),
                        UiStyleLength.px(3)))
                .setBorderWidth(UiStyleLength.px(1));
        root.appendText("AA");
        span.appendText("BB");
        root.append(span);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 100, 0);

        assertHitElement(root, rootBox, 18, 0);
        assertHitElement(span, rootBox, 22, 0);
        assertHitElement(span, rootBox, 42, 18);
        assertHitElement(root, rootBox, 48, 0);
    }

    /**
     * 验证负 z-index 元素在普通流元素下方命中。
     */
    @Test
    public void shouldHitNormalFlowAboveNegativeZIndex() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode negative = document.div();
        ElementNode normal = document.div();

        root.style().setWidth(UiStyleLength.px(100));
        negative.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(20))
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(20))
                .setZIndex(-1);
        normal.style()
                .setWidth(UiStyleLength.px(50))
                .setHeight(UiStyleLength.px(20));
        root.append(negative).append(normal);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 120, 0);

        assertHitElement(normal, rootBox, 10, 22);
    }

    /**
     * 验证 positioned 后代可越过非 stacking context 祖先参与最近上下文命中排序。
     */
    @Test
    public void shouldHitPositionedDescendantInNearestStackingContext() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode parent = document.div();
        ElementNode raisedDescendant = document.div();
        ElementNode normalCover = document.div();

        root.style().setWidth(UiStyleLength.px(120));
        parent.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20));
        raisedDescendant.style()
                .setWidth(UiStyleLength.px(70))
                .setHeight(UiStyleLength.px(20))
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(12))
                .setZIndex(5);
        normalCover.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20));
        parent.append(raisedDescendant);
        root.append(parent).append(normalCover);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 140, 0);

        assertHitElement(raisedDescendant, rootBox, 10, 22);
    }

    /**
     * 验证 stacking context 祖先会阻止高 z-index 后代逃出上下文。
     */
    @Test
    public void shouldHitExternalSiblingAboveIsolatedPositionedDescendant() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode isolatedParent = document.div();
        ElementNode raisedDescendant = document.div();
        ElementNode normalCover = document.div();

        root.style().setWidth(UiStyleLength.px(120));
        isolatedParent.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOpacity(0.98F);
        raisedDescendant.style()
                .setWidth(UiStyleLength.px(70))
                .setHeight(UiStyleLength.px(20))
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(12))
                .setZIndex(99);
        normalCover.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20));
        isolatedParent.append(raisedDescendant);
        root.append(isolatedParent).append(normalCover);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 140, 0);

        assertHitElement(normalCover, rootBox, 10, 22);
    }

    /**
     * 验证 overflow clip effect boundary 会阻止高 z-index 后代越界命中。
     */
    @Test
    public void shouldHitExternalSiblingAboveClippedPositionedDescendant() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode clippedParent = document.div();
        ElementNode raisedDescendant = document.div();
        ElementNode normalCover = document.div();

        root.style().setWidth(UiStyleLength.px(120));
        clippedParent.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.HIDDEN);
        raisedDescendant.style()
                .setWidth(UiStyleLength.px(70))
                .setHeight(UiStyleLength.px(20))
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(12))
                .setZIndex(99);
        normalCover.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(20));
        clippedParent.append(raisedDescendant);
        root.append(clippedParent).append(normalCover);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 140, 0);

        assertHitElement(normalCover, rootBox, 10, 22);
    }

    /**
     * 验证声明为命中隐藏的 fixed overlay 不会截获下层元素命中。
     */
    @Test
    public void shouldSkipHitTestHiddenFixedOverlay() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode target = document.div();
        ElementNode overlay = document.div();

        root.style().setWidth(UiStyleLength.px(120));
        target.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        overlay.setAttribute("data-hit-test-hidden", "true");
        overlay.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(80))
                .setPosition(UiPosition.FIXED)
                .setTop(UiStyleLength.px(0))
                .setLeft(UiStyleLength.px(0))
                .setZIndex(1000);
        root.append(target).append(overlay);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 120, 80);

        assertHitElement(target, rootBox, 10, 10);
    }

    /**
     * 验证 pointer-events:none 元素不会截获下层命中。
     */
    @Test
    public void shouldSkipPointerEventsNoneOverlay() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode target = document.div();
        ElementNode overlay = document.div();

        root.style().setWidth(UiStyleLength.px(120));
        target.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        overlay.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(80))
                .setPosition(UiPosition.FIXED)
                .setTop(UiStyleLength.px(0))
                .setLeft(UiStyleLength.px(0))
                .setZIndex(1000)
                .setPointerEvents(UiPointerEvents.NONE);
        root.append(target).append(overlay);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 120, 80);

        assertHitElement(target, rootBox, 10, 10);
    }

    /**
     * 验证 pointer-events:none 会按继承语义让覆盖层后代一并透传命中。
     */
    @Test
    public void shouldSkipPointerEventsInheritedFromOverlayAncestor() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode target = document.div();
        ElementNode overlay = document.div();
        ElementNode overlayChild = document.div();

        root.style().setWidth(UiStyleLength.px(120));
        target.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        overlay.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(80))
                .setPosition(UiPosition.FIXED)
                .setTop(UiStyleLength.px(0))
                .setLeft(UiStyleLength.px(0))
                .setZIndex(1000)
                .setPointerEvents(UiPointerEvents.NONE);
        overlayChild.style()
                .setWidth(UiStyleLength.px(120))
                .setHeight(UiStyleLength.px(80));
        overlay.append(overlayChild);
        root.append(target).append(overlay);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 120, 80);

        assertHitElement(target, rootBox, 10, 10);
    }

    /**
     * 验证共享场景命中会优先返回后注册的 top-layer 根盒。
     */
    @Test
    public void shouldHitLatestTopLayerRootBeforeDocumentTree() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode normal = document.div();
        ElementNode firstTopLayer = document.div();
        ElementNode secondTopLayer = document.div();

        root.style().setWidth(UiStyleLength.px(120)).setHeight(UiStyleLength.px(80));
        normal.style()
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        firstTopLayer.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(8))
                .setTop(UiStyleLength.px(8))
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        secondTopLayer.style()
                .setPosition(UiPosition.FIXED)
                .setLeft(UiStyleLength.px(8))
                .setTop(UiStyleLength.px(8))
                .setWidth(UiStyleLength.px(80))
                .setHeight(UiStyleLength.px(40));
        root.append(normal).append(firstTopLayer).append(secondTopLayer);
        document.__showTopLayerElement(firstTopLayer);
        document.__showTopLayerElement(secondTopLayer);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 120, 80);
        java.util.List<DocumentLayoutBox> topLayerBoxes = java.util.Arrays.asList(
                DocumentLayoutEngine.layoutTopLayerElement(firstTopLayer, 120, 80,
                        club.heiqi.uilib.ui.text.DefaultTextMeasureService.getInstance(), null),
                DocumentLayoutEngine.layoutTopLayerElement(secondTopLayer, 120, 80,
                        club.heiqi.uilib.ui.text.DefaultTextMeasureService.getInstance(), null));

        ElementNode actualElement = DocumentHitTestEngine.hitTest(rootBox, topLayerBoxes, null, 10, 10, 0L, null);

        Assert.assertNotNull(actualElement);
        Assert.assertEquals(secondTopLayer.__getElementUid(), actualElement.__getElementUid());
    }

    /**
     * 验证 visibility:hidden 祖先不会阻断显式 visibility:visible 后代的文本命中。
     */
    @Test
    public void shouldHitVisibleInlineDescendantInsideHiddenAncestor() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode hidden = document.div();
        ElementNode visible = document.span();

        root.style().setWidth(UiStyleLength.px(80));
        hidden.style().setVisibility(UiVisibility.HIDDEN);
        hidden.appendText("AA");
        visible.style().setVisibility(UiVisibility.VISIBLE);
        visible.appendText("BB");
        hidden.append(visible);
        root.append(hidden);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 80, 0, new DeterministicTextMeasureService());

        assertHitElement(root, rootBox, 2, 5);
        assertHitElement(visible, rootBox, 20, 5);
    }

    /**
     * 验证 transform 平移后的视觉区域参与命中，原布局区域不再命中该元素。
     */
    @Test
    public void shouldHitTranslatedElementAtVisualPosition() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode target = document.div();

        root.style().setWidth(UiStyleLength.px(120));
        target.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(10))
                .setTransform(UiTransform.translate(30.0F, 0.0F));
        root.append(target);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 120, 0);

        assertHitElement(target, rootBox, 35, 5);
        assertHitElement(root, rootBox, 5, 5);
    }

    /**
     * 验证 transform 缩放会通过反向坐标映射扩大命中区域。
     */
    @Test
    public void shouldHitScaledElementAtVisualPosition() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode target = document.div();

        root.style().setWidth(UiStyleLength.px(120));
        target.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(10))
                .setTransform(UiTransform.scale(2.0F, 1.0F)
                        .withTransformOrigin(UiStyleLength.px(0), UiStyleLength.px(0)));
        root.append(target);

        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 120, 0);

        assertHitElement(target, rootBox, 35, 5);
    }

    /**
     * 验证动画中的 transform 运行值也会参与命中测试。
     */
    @Test
    public void shouldHitAnimatedTransformAtRuntimeVisualPosition() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode target = document.div();

        root.style().setWidth(UiStyleLength.px(120));
        target.style()
                .setWidth(UiStyleLength.px(20))
                .setHeight(UiStyleLength.px(10))
                .setTransform(UiTransform.identity())
                .setTransition(DocumentAnimationProperty.TRANSLATE_X, 1000L);
        root.append(target);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();
        timeline.updateFromLayout(DocumentLayoutEngine.layout(root, 120, 0), 0L);

        target.style().setTransform(UiTransform.translate(40.0F, 0.0F));
        DocumentLayoutBox rootBox = DocumentLayoutEngine.layout(root, 120, 0);
        timeline.updateFromLayout(rootBox, 0L);

        ElementNode actualElement = DocumentHitTestEngine.hitTest(rootBox, null, 25, 5, 500_000_000L, timeline);
        Assert.assertNotNull(actualElement);
        Assert.assertEquals(target.__getElementUid(), actualElement.__getElementUid());
    }

    private static void assertHitElement(ElementNode expectedElement, DocumentLayoutBox rootBox, int x, int y) {
        ElementNode actualElement = DocumentHitTestEngine.hitTest(rootBox, null, x, y);
        Assert.assertNotNull(actualElement);
        Assert.assertEquals(expectedElement.__getElementUid(), actualElement.__getElementUid());
    }

    private static void assertHitElement(ElementNode expectedElement, DocumentLayoutBox rootBox,
            DocumentScrollState scrollState, int x, int y) {
        ElementNode actualElement = DocumentHitTestEngine.hitTest(rootBox, scrollState, x, y);
        Assert.assertNotNull(actualElement);
        Assert.assertEquals(expectedElement.__getElementUid(), actualElement.__getElementUid());
    }

    private static final class DeterministicTextMeasureService implements TextMeasureService {

        @Override
        public int getEpoch() {
            return 1;
        }

        @Override
        public int getStringWidth(String text) {
            return text == null ? 0 : text.length() * 4;
        }

        @Override
        public int getLineHeight() {
            return 9;
        }

        @Override
        public String trimStringToWidth(String text, int targetWidth) {
            if (text == null || text.isEmpty() || targetWidth <= 0) {
                return "";
            }
            int maxLength = Math.max(0, targetWidth / 4);
            return text.substring(0, Math.min(text.length(), maxLength));
        }

        @Override
        public java.util.List<String> listFormattedStringToWidth(String text, int wrapWidth) {
            java.util.List<String> lines = new java.util.ArrayList<String>();
            if (text == null || text.isEmpty() || wrapWidth <= 0) {
                return lines;
            }
            int maxCharsPerLine = Math.max(1, wrapWidth / 4);
            for (int index = 0; index < text.length(); index += maxCharsPerLine) {
                lines.add(text.substring(index, Math.min(text.length(), index + maxCharsPerLine)));
            }
            return lines;
        }
    }
}
