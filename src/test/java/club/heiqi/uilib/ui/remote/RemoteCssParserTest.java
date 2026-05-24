package club.heiqi.uilib.ui.remote;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.style.UiStyleProperty;
import club.heiqi.uilib.ui.style.cascade.UiStyleDeclaration;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleLength;

/**
 * 远程页面 CSS 白名单解析测试。
 */
public class RemoteCssParserTest {

    @Test
    public void shouldParseWhitelistedPropertiesAndImportantFlag() {
        UiStyleDeclaration declaration = RemoteCssParser.parseDeclaration(
                "display:flex; width:calc(100% - 16px); overflow:auto; color:#123456 !important; nope:x");

        Assert.assertEquals(UiDisplay.FLEX, declaration.getDisplay());
        Assert.assertEquals(UiStyleLength.calc(1.0F, -16.0F), declaration.getWidth());
        Assert.assertEquals(UiOverflow.AUTO, declaration.getOverflowX());
        Assert.assertEquals(UiOverflow.AUTO, declaration.getOverflowY());
        Assert.assertEquals(0xFF123456, declaration.getTextColor().intValue());
        Assert.assertTrue(declaration.isImportant(UiStyleProperty.TEXT_COLOR));
    }

    @Test
    public void shouldParseSupportedColorForms() {
        Assert.assertEquals(0xFFAABBCC, RemoteCssParser.parseColor("#abc"));
        Assert.assertEquals(0xFFAABBCC, RemoteCssParser.parseColor("#aabbcc"));
        Assert.assertEquals(0x80AABBCC, RemoteCssParser.parseColor("#80aabbcc"));
        Assert.assertEquals(0x804080BF, RemoteCssParser.parseColor("rgba(64, 128, 191, 0.5)"));
        Assert.assertEquals(0xFFFFA500, RemoteCssParser.parseColor("orange"));
    }
}
