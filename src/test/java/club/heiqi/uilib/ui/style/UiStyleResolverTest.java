package club.heiqi.uilib.ui.style;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.animation.DocumentAnimationProperty;
import club.heiqi.uilib.ui.animation.DocumentAnimationTimingFunction;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;

/**
 * `UiStyleResolver` 的样式计算契约测试。
 */
public class UiStyleResolverTest {

    /**
     * 验证元素 inline style 会记录文档变更版本。
     */
    @Test
    public void shouldExposeInlineStyleAndRecordMutation() {
        UiDocument document = UiDocument.create();
        ElementNode panel = document.div();
        int initialVersion = document.getMutationVersion();

        panel.style()
                .setDisplay(UiDisplay.FLEX)
                .setWidth(UiStyleLength.percent(0.5F))
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(-4))
                .setLeft(UiStyleLength.px(8))
                .setZIndex(3)
                .setTransition(DocumentAnimationProperty.BACKGROUND_COLOR, 250L)
                .setTransitionTimingFunction(DocumentAnimationTimingFunction.EASE_OUT)
                .setPadding(UiStyleLength.px(12))
                .setBackgroundColor(0xAA101820)
                .setBackdropBlurRadius(UiStyleLength.px(14))
                .setBackdropSaturation(1.35F);

        Assert.assertTrue(document.getMutationVersion() > initialVersion);
        Assert.assertSame(panel.style(), panel.getInlineStyle());
        Assert.assertEquals(UiDisplay.FLEX, panel.style().getDisplay());
        Assert.assertEquals(UiStyleLength.percent(0.5F), panel.style().getWidth());
        Assert.assertEquals(UiPosition.RELATIVE, panel.style().getPosition());
        Assert.assertEquals(UiStyleLength.px(-4), panel.style().getTop());
        Assert.assertEquals(UiStyleLength.px(8), panel.style().getLeft());
        Assert.assertEquals(Integer.valueOf(3), panel.style().getZIndex());
        Assert.assertEquals(DocumentAnimationProperty.BACKGROUND_COLOR,
                panel.style().getTransitionProperties().get(0));
        Assert.assertEquals(Long.valueOf(250_000_000L), panel.style().getTransitionDurationNanos());
        Assert.assertEquals(DocumentAnimationTimingFunction.EASE_OUT, panel.style().getTransitionTimingFunction());
        Assert.assertEquals(UiStyleInsets.all(UiStyleLength.px(12)), panel.style().getPadding());
        Assert.assertEquals(Integer.valueOf(0xAA101820), panel.style().getBackgroundColor());
        Assert.assertEquals(UiStyleLength.px(14), panel.style().getBackdropBlurRadius());
        Assert.assertEquals(Float.valueOf(1.35F), panel.style().getBackdropSaturation());
    }

    /**
     * 验证 computed style 会使用元素默认 display 并继承文本颜色。
     */
    @Test
    public void shouldResolveDefaultsAndInheritedTextColor() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode span = document.span();
        root.append(span);

        root.style().setTextColor(0xFFD7E3FF);

        ComputedStyle rootStyle = UiStyleResolver.compute(root);
        ComputedStyle spanStyle = UiStyleResolver.compute(span);

        Assert.assertEquals(UiDisplay.BLOCK, rootStyle.getDisplay());
        Assert.assertEquals(UiDisplay.INLINE, spanStyle.getDisplay());
        Assert.assertEquals(0xFFD7E3FF, spanStyle.getTextColor());
        Assert.assertEquals(UiStyleLength.auto(), spanStyle.getWidth());
        Assert.assertEquals(UiPosition.STATIC, spanStyle.getPosition());
        Assert.assertEquals(UiStyleLength.auto(), spanStyle.getTop());
        Assert.assertEquals(UiStyleLength.auto(), spanStyle.getRight());
        Assert.assertEquals(UiStyleLength.auto(), spanStyle.getBottom());
        Assert.assertEquals(UiStyleLength.auto(), spanStyle.getLeft());
        Assert.assertNull(spanStyle.getZIndex());
        Assert.assertTrue(spanStyle.getTransitionProperties().isEmpty());
        Assert.assertEquals(0L, spanStyle.getTransitionDurationNanos());
        Assert.assertEquals(0L, spanStyle.getTransitionDelayNanos());
        Assert.assertEquals(DocumentAnimationTimingFunction.LINEAR, spanStyle.getTransitionTimingFunction());
        Assert.assertEquals(UiStyleInsets.zero(), spanStyle.getMargin());
        Assert.assertEquals(UiOverflow.VISIBLE, spanStyle.getOverflowX());
        Assert.assertEquals(UiOverflow.VISIBLE, spanStyle.getOverflowY());
        Assert.assertEquals(UiFlexDirection.ROW, spanStyle.getFlexDirection());
        Assert.assertEquals(UiAlignItems.STRETCH, spanStyle.getAlignItems());
        Assert.assertEquals(UiJustifyContent.START, spanStyle.getJustifyContent());
        Assert.assertEquals(UiStyleLength.px(0), spanStyle.getRowGap());
        Assert.assertEquals(UiStyleLength.px(0), spanStyle.getColumnGap());
        Assert.assertEquals(0.0F, spanStyle.getFlexGrow(), 0.0F);
        Assert.assertEquals(1.0F, spanStyle.getFlexShrink(), 0.0F);
        Assert.assertEquals(UiStyleLength.px(0), spanStyle.getBackdropBlurRadius());
        Assert.assertEquals(1.0F, spanStyle.getBackdropSaturation(), 0.0F);
    }

    /**
     * 验证 inline style 会覆盖初始值与继承值。
     */
    @Test
    public void shouldApplyInlineStyleOverrides() {
        UiDocument document = UiDocument.create();
        ElementNode panel = document.div();

        panel.style()
                .setDisplay(UiDisplay.FLEX)
                .setWidth(UiStyleLength.percent(1.0F))
                .setHeight(UiStyleLength.px(120))
                .setPosition(UiPosition.RELATIVE)
                .setTop(UiStyleLength.px(5))
                .setRight(UiStyleLength.px(7))
                .setBottom(UiStyleLength.px(9))
                .setLeft(UiStyleLength.px(11))
                .setZIndex(4)
                .setTransitionProperties(DocumentAnimationProperty.BACKGROUND_COLOR,
                        DocumentAnimationProperty.BORDER_COLOR)
                .setTransitionDurationMillis(300L)
                .setTransitionDelayMillis(40L)
                .setTransitionTimingFunction(DocumentAnimationTimingFunction.EASE_IN_OUT)
                .setMargin(UiStyleInsets.of(UiStyleLength.px(4), UiStyleLength.px(8), UiStyleLength.px(12),
                        UiStyleLength.px(16)))
                .setPadding(UiStyleLength.px(10))
                .setBorderWidth(UiStyleLength.px(1))
                .setBorderRadius(UiStyleLength.px(18))
                .setOverflowX(UiOverflow.HIDDEN)
                .setOverflowY(UiOverflow.AUTO)
                .setFlexDirection(UiFlexDirection.COLUMN)
                .setAlignItems(UiAlignItems.CENTER)
                .setJustifyContent(UiJustifyContent.SPACE_BETWEEN)
                .setRowGap(UiStyleLength.px(6))
                .setColumnGap(UiStyleLength.px(8))
                .setFlexGrow(2.0F)
                .setFlexShrink(0.5F)
                .setBorderColor(0xFF86A8F0)
                .setTextColor(0xFFF6D78E)
                .setBackdropBlurRadius(UiStyleLength.px(16))
                .setBackdropSaturation(1.4F);

        ComputedStyle computedStyle = UiStyleResolver.compute(panel);

        Assert.assertEquals(UiDisplay.FLEX, computedStyle.getDisplay());
        Assert.assertEquals(UiStyleLength.percent(1.0F), computedStyle.getWidth());
        Assert.assertEquals(UiStyleLength.px(120), computedStyle.getHeight());
        Assert.assertEquals(UiPosition.RELATIVE, computedStyle.getPosition());
        Assert.assertEquals(UiStyleLength.px(5), computedStyle.getTop());
        Assert.assertEquals(UiStyleLength.px(7), computedStyle.getRight());
        Assert.assertEquals(UiStyleLength.px(9), computedStyle.getBottom());
        Assert.assertEquals(UiStyleLength.px(11), computedStyle.getLeft());
        Assert.assertEquals(Integer.valueOf(4), computedStyle.getZIndex());
        Assert.assertEquals(2, computedStyle.getTransitionProperties().size());
        Assert.assertEquals(DocumentAnimationProperty.BACKGROUND_COLOR,
                computedStyle.getTransitionProperties().get(0));
        Assert.assertEquals(DocumentAnimationProperty.BORDER_COLOR, computedStyle.getTransitionProperties().get(1));
        Assert.assertEquals(300_000_000L, computedStyle.getTransitionDurationNanos());
        Assert.assertEquals(40_000_000L, computedStyle.getTransitionDelayNanos());
        Assert.assertEquals(DocumentAnimationTimingFunction.EASE_IN_OUT, computedStyle.getTransitionTimingFunction());
        Assert.assertEquals(UiStyleLength.px(4), computedStyle.getMargin().getTop());
        Assert.assertEquals(UiStyleLength.px(10), computedStyle.getPadding().getLeft());
        Assert.assertEquals(UiStyleLength.px(1), computedStyle.getBorderWidth());
        Assert.assertEquals(UiStyleLength.px(18), computedStyle.getBorderRadius());
        Assert.assertEquals(UiOverflow.HIDDEN, computedStyle.getOverflowX());
        Assert.assertEquals(UiOverflow.AUTO, computedStyle.getOverflowY());
        Assert.assertEquals(UiFlexDirection.COLUMN, computedStyle.getFlexDirection());
        Assert.assertEquals(UiAlignItems.CENTER, computedStyle.getAlignItems());
        Assert.assertEquals(UiJustifyContent.SPACE_BETWEEN, computedStyle.getJustifyContent());
        Assert.assertEquals(UiStyleLength.px(6), computedStyle.getRowGap());
        Assert.assertEquals(UiStyleLength.px(8), computedStyle.getColumnGap());
        Assert.assertEquals(2.0F, computedStyle.getFlexGrow(), 0.0F);
        Assert.assertEquals(0.5F, computedStyle.getFlexShrink(), 0.0F);
        Assert.assertEquals(0xFF86A8F0, computedStyle.getBorderColor());
        Assert.assertEquals(0xFFF6D78E, computedStyle.getTextColor());
        Assert.assertEquals(UiStyleLength.px(16), computedStyle.getBackdropBlurRadius());
        Assert.assertEquals(1.4F, computedStyle.getBackdropSaturation(), 0.0F);
    }

    /**
     * 验证长度值能按当前可用空间解析。
     */
    @Test
    public void shouldResolveStyleLengths() {
        Assert.assertEquals(32, UiStyleLength.auto().resolve(100, 32));
        Assert.assertEquals(64, UiStyleLength.percent(0.5F).resolve(128, 0));
        Assert.assertEquals(18, UiStyleLength.px(18).resolve(128, 0));
    }
}
