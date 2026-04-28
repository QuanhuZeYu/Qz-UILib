package club.heiqi.uilib.ui.animation;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;
import club.heiqi.uilib.ui.layout.DocumentLayoutBox;
import club.heiqi.uilib.ui.layout.DocumentLayoutEngine;
import club.heiqi.uilib.ui.style.UiStyleLength;

/**
 * `DocumentAnimationTimeline` 的 paint-only transition 契约测试。
 */
public class DocumentAnimationTimelineTest {

    /**
     * 验证颜色 transition 会基于 computed style 变化创建动画覆盖，不污染 inline style。
     */
    @Test
    public void shouldTransitionBackgroundColorWithoutMutatingInlineStyle() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        root.style()
                .setWidth(UiStyleLength.px(40))
                .setHeight(UiStyleLength.px(20))
                .setBackgroundColor(0xFF000000)
                .setTransition(DocumentAnimationProperty.BACKGROUND_COLOR, 1000L);
        DocumentAnimationTimeline timeline = new DocumentAnimationTimeline();

        DocumentLayoutBox firstLayout = DocumentLayoutEngine.layout(root, 80, 0);
        Assert.assertTrue(timeline.updateFromLayout(firstLayout, 0L));
        Assert.assertEquals(0xFF000000, timeline.resolveColor(root, DocumentAnimationProperty.BACKGROUND_COLOR,
                0xFF000000, 0L));

        root.style().setBackgroundColor(0xFFFFFFFF);
        DocumentLayoutBox secondLayout = DocumentLayoutEngine.layout(root, 80, 0);
        Assert.assertTrue(timeline.updateFromLayout(secondLayout, 0L));

        Assert.assertEquals(1, timeline.getActiveAnimationCount(500_000_000L));
        Assert.assertEquals(0xFF808080, timeline.resolveColor(root, DocumentAnimationProperty.BACKGROUND_COLOR,
                0xFFFFFFFF, 500_000_000L));
        Assert.assertEquals(Integer.valueOf(0xFFFFFFFF), root.style().getBackgroundColor());
        Assert.assertEquals(0xFFFFFFFF, timeline.resolveColor(root, DocumentAnimationProperty.BACKGROUND_COLOR,
                0xFFFFFFFF, 1_000_000_000L));
        Assert.assertTrue(timeline.pruneFinishedAnimations(1_000_000_000L));
        Assert.assertFalse(timeline.hasAnimationWork());
    }
}
