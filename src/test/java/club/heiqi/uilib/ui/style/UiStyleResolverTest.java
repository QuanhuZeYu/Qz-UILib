package club.heiqi.uilib.ui.style;

import org.junit.Assert;
import org.junit.Test;

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
                .setPadding(UiStyleLength.px(12))
                .setBackgroundColor(0xAA101820);

        Assert.assertTrue(document.getMutationVersion() > initialVersion);
        Assert.assertSame(panel.style(), panel.getInlineStyle());
        Assert.assertEquals(UiDisplay.FLEX, panel.style().getDisplay());
        Assert.assertEquals(UiStyleLength.percent(0.5F), panel.style().getWidth());
        Assert.assertEquals(UiStyleInsets.all(UiStyleLength.px(12)), panel.style().getPadding());
        Assert.assertEquals(Integer.valueOf(0xAA101820), panel.style().getBackgroundColor());
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
                .setTextColor(0xFFF6D78E);

        ComputedStyle computedStyle = UiStyleResolver.compute(panel);

        Assert.assertEquals(UiDisplay.FLEX, computedStyle.getDisplay());
        Assert.assertEquals(UiStyleLength.percent(1.0F), computedStyle.getWidth());
        Assert.assertEquals(UiStyleLength.px(120), computedStyle.getHeight());
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
