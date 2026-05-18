package club.heiqi.uilib.ui.style;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.DomTokenList;
import club.heiqi.uilib.ui.dom.ElementNode;
import club.heiqi.uilib.ui.dom.UiDocument;

/**
 * 样式表系统（选择器、级联、className）的契约测试。
 */
public class UiStyleSheetTest {

    // ========== DomTokenList 测试 ==========

    @Test
    public void classListAddAndContains() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();

        div.getClassList().add("header", "active");

        Assert.assertTrue(div.getClassList().contains("header"));
        Assert.assertTrue(div.getClassList().contains("active"));
        Assert.assertFalse(div.getClassList().contains("footer"));
        Assert.assertEquals(2, div.getClassList().length());
    }

    @Test
    public void classListRemove() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();

        div.getClassList().add("a", "b", "c");
        div.getClassList().remove("b");

        Assert.assertTrue(div.getClassList().contains("a"));
        Assert.assertFalse(div.getClassList().contains("b"));
        Assert.assertTrue(div.getClassList().contains("c"));
        Assert.assertEquals(2, div.getClassList().length());
    }

    @Test
    public void classListToggle() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();

        Assert.assertTrue(div.getClassList().toggle("active"));
        Assert.assertTrue(div.getClassList().contains("active"));

        Assert.assertFalse(div.getClassList().toggle("active"));
        Assert.assertFalse(div.getClassList().contains("active"));
    }

    @Test
    public void classListToggleForce() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();

        div.getClassList().toggle("visible", true);
        Assert.assertTrue(div.getClassList().contains("visible"));

        div.getClassList().toggle("visible", true);
        Assert.assertTrue(div.getClassList().contains("visible"));

        div.getClassList().toggle("visible", false);
        Assert.assertFalse(div.getClassList().contains("visible"));
    }

    @Test
    public void classListReplace() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();

        div.getClassList().add("old-class");
        Assert.assertTrue(div.getClassList().replace("old-class", "new-class"));
        Assert.assertFalse(div.getClassList().contains("old-class"));
        Assert.assertTrue(div.getClassList().contains("new-class"));

        Assert.assertFalse(div.getClassList().replace("nonexistent", "other"));
    }

    @Test
    public void classNameGetterAndSetter() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();

        div.setClassName("header active primary");

        Assert.assertEquals("header active primary", div.getClassName());
        Assert.assertTrue(div.getClassList().contains("header"));
        Assert.assertTrue(div.getClassList().contains("active"));
        Assert.assertTrue(div.getClassList().contains("primary"));
        Assert.assertEquals(3, div.getClassList().length());
    }

    @Test
    public void classNameSetClearsExisting() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();

        div.setClassName("old");
        div.setClassName("new");

        Assert.assertFalse(div.getClassList().contains("old"));
        Assert.assertTrue(div.getClassList().contains("new"));
    }

    @Test
    public void classListMutationTriggersDocumentVersion() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();
        document.getRootElement().append(div);

        int versionBefore = document.getMutationVersion();
        div.getClassList().add("highlight");
        Assert.assertTrue(document.getMutationVersion() > versionBefore);
    }

    // ========== UiSelector 测试 ==========

    @Test
    public void selectorMatchesTag() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();
        ElementNode span = document.span();

        UiSelector selector = UiSelector.tag("div");
        Assert.assertTrue(selector.matches(div));
        Assert.assertFalse(selector.matches(span));
    }

    @Test
    public void selectorMatchesClass() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();
        div.getClassList().add("active");

        UiSelector selector = UiSelector.className("active");
        Assert.assertTrue(selector.matches(div));

        UiSelector otherSelector = UiSelector.className("inactive");
        Assert.assertFalse(otherSelector.matches(div));
    }

    @Test
    public void selectorMatchesId() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();
        div.setId("main-panel");

        UiSelector selector = UiSelector.id("main-panel");
        Assert.assertTrue(selector.matches(div));

        UiSelector otherSelector = UiSelector.id("other");
        Assert.assertFalse(otherSelector.matches(div));
    }

    @Test
    public void selectorMatchesUniversal() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();
        ElementNode span = document.span();

        UiSelector selector = UiSelector.universal();
        Assert.assertTrue(selector.matches(div));
        Assert.assertTrue(selector.matches(span));
    }

    @Test
    public void selectorParseCompound() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();
        div.setClassName("panel active");
        div.setId("main");

        // div.panel.active#main 应该匹配
        UiSelector selector = UiSelector.parse("div.panel.active#main");
        Assert.assertTrue(selector.matches(div));

        // 缺少一个 class 不匹配
        ElementNode div2 = document.div();
        div2.setClassName("panel");
        div2.setId("main");
        Assert.assertFalse(selector.matches(div2));
    }

    @Test
    public void selectorParseClassOnly() {
        UiSelector selector = UiSelector.parse(".highlight");
        Assert.assertNull(selector.getTagName());
        Assert.assertEquals(1, selector.getClassNames().size());
        Assert.assertEquals("highlight", selector.getClassNames().get(0));
    }

    @Test
    public void selectorParseIdOnly() {
        UiSelector selector = UiSelector.parse("#title");
        Assert.assertNull(selector.getTagName());
        Assert.assertEquals("title", selector.getId());
    }

    @Test
    public void selectorSpecificityOrder() {
        UiSelector tagSelector = UiSelector.tag("div");
        UiSelector classSelector = UiSelector.className("active");
        UiSelector idSelector = UiSelector.id("main");

        // id > class > tag
        Assert.assertTrue(idSelector.compareSpecificity(classSelector) > 0);
        Assert.assertTrue(classSelector.compareSpecificity(tagSelector) > 0);
        Assert.assertTrue(idSelector.compareSpecificity(tagSelector) > 0);
    }

    // ========== UiStyleSheet 级联测试 ==========

    @Test
    public void styleSheetRuleAppliedToElement() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();
        div.setClassName("panel");
        document.getRootElement().append(div);

        UiStyleSheet sheet = UiStyleSheet.create()
                .addRule(".panel", new UiStyleDeclaration()
                        .setBackgroundColor(0xFF333333)
                        .setPadding(UiStyleLength.px(8)));
        document.addStyleSheet(sheet);

        ComputedStyle computed = UiStyleResolver.compute(div);
        Assert.assertEquals(0xFF333333, computed.getBackgroundColor());
        Assert.assertEquals(UiStyleInsets.all(UiStyleLength.px(8)), computed.getPadding());
    }

    @Test
    public void inlineStyleOverridesStyleSheet() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();
        div.setClassName("panel");
        div.style().setBackgroundColor(0xFFFF0000);
        document.getRootElement().append(div);

        UiStyleSheet sheet = UiStyleSheet.create()
                .addRule(".panel", new UiStyleDeclaration()
                        .setBackgroundColor(0xFF333333));
        document.addStyleSheet(sheet);

        ComputedStyle computed = UiStyleResolver.compute(div);
        // inline style 优先级最高
        Assert.assertEquals(0xFFFF0000, computed.getBackgroundColor());
    }

    @Test
    public void idSelectorOverridesClassSelector() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();
        div.setClassName("panel");
        div.setId("main");
        document.getRootElement().append(div);

        UiStyleSheet sheet = UiStyleSheet.create()
                .addRule(".panel", new UiStyleDeclaration()
                        .setBackgroundColor(0xFF111111))
                .addRule("#main", new UiStyleDeclaration()
                        .setBackgroundColor(0xFF222222));
        document.addStyleSheet(sheet);

        ComputedStyle computed = UiStyleResolver.compute(div);
        // id 选择器特异性高于 class 选择器
        Assert.assertEquals(0xFF222222, computed.getBackgroundColor());
    }

    @Test
    public void laterRuleWinsAtSameSpecificity() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();
        div.setClassName("a b");
        document.getRootElement().append(div);

        UiStyleSheet sheet = UiStyleSheet.create()
                .addRule(".a", new UiStyleDeclaration()
                        .setBackgroundColor(0xFF111111))
                .addRule(".b", new UiStyleDeclaration()
                        .setBackgroundColor(0xFF222222));
        document.addStyleSheet(sheet);

        ComputedStyle computed = UiStyleResolver.compute(div);
        // 同特异性时后声明的规则优先
        Assert.assertEquals(0xFF222222, computed.getBackgroundColor());
    }

    @Test
    public void multiplePropertiesFromDifferentRules() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();
        div.setClassName("panel");
        div.setId("main");
        document.getRootElement().append(div);

        UiStyleSheet sheet = UiStyleSheet.create()
                .addRule(".panel", new UiStyleDeclaration()
                        .setBackgroundColor(0xFF333333)
                        .setPadding(UiStyleLength.px(8)))
                .addRule("#main", new UiStyleDeclaration()
                        .setBackgroundColor(0xFF444444));
        document.addStyleSheet(sheet);

        ComputedStyle computed = UiStyleResolver.compute(div);
        // backgroundColor 被 id 规则覆盖
        Assert.assertEquals(0xFF444444, computed.getBackgroundColor());
        // padding 只在 class 规则中声明，保留
        Assert.assertEquals(UiStyleInsets.all(UiStyleLength.px(8)), computed.getPadding());
    }

    @Test
    public void tagSelectorApplies() {
        UiDocument document = UiDocument.create();
        ElementNode button = document.button();
        document.getRootElement().append(button);

        UiStyleSheet sheet = UiStyleSheet.create()
                .addRule("button", new UiStyleDeclaration()
                        .setBorderRadius(UiStyleLength.px(4))
                        .setBackgroundColor(0xFF5566AA));
        document.addStyleSheet(sheet);

        ComputedStyle computed = UiStyleResolver.compute(button);
        Assert.assertEquals(UiStyleLength.px(4), computed.getBorderRadius());
        Assert.assertEquals(0xFF5566AA, computed.getBackgroundColor());
    }

    @Test
    public void noStyleSheetFallsBackToDefaults() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();
        document.getRootElement().append(div);

        // 无样式表、无 inline style
        ComputedStyle computed = UiStyleResolver.compute(div);
        Assert.assertEquals(UiDisplay.BLOCK, computed.getDisplay());
        Assert.assertEquals(0x00000000, computed.getBackgroundColor());
        Assert.assertEquals(UiStyleLength.auto(), computed.getWidth());
    }

    @Test
    public void removeStyleSheetStopsApplying() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();
        div.setClassName("panel");
        document.getRootElement().append(div);

        UiStyleSheet sheet = UiStyleSheet.create()
                .addRule(".panel", new UiStyleDeclaration()
                        .setBackgroundColor(0xFF333333));
        document.addStyleSheet(sheet);

        Assert.assertEquals(0xFF333333, UiStyleResolver.compute(div).getBackgroundColor());

        document.removeStyleSheet(sheet);
        Assert.assertEquals(0x00000000, UiStyleResolver.compute(div).getBackgroundColor());
    }

    @Test
    public void inheritablePropertyFromStyleSheet() {
        UiDocument document = UiDocument.create();
        ElementNode root = document.getRootElement();
        ElementNode child = document.div();
        root.append(child);

        UiStyleSheet sheet = UiStyleSheet.create()
                .addRule("document", new UiStyleDeclaration()
                        .setTextColor(0xFFAABBCC));
        document.addStyleSheet(sheet);

        ComputedStyle childStyle = UiStyleResolver.compute(child);
        // textColor 是可继承属性，子元素应继承父元素的样式表值
        Assert.assertEquals(0xFFAABBCC, childStyle.getTextColor());
    }

    // ========== ElementNode id 便捷方法测试 ==========

    @Test
    public void elementIdGetterAndSetter() {
        UiDocument document = UiDocument.create();
        ElementNode div = document.div();

        Assert.assertNull(div.getId());

        div.setId("my-panel");
        Assert.assertEquals("my-panel", div.getId());
        Assert.assertEquals("my-panel", div.getAttribute("id"));

        div.setId(null);
        Assert.assertNull(div.getId());
    }
}
