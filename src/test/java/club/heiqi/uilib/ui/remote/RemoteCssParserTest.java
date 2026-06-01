package club.heiqi.uilib.ui.remote;

import org.junit.Assert;
import org.junit.Test;

import net.minecraft.util.ResourceLocation;

import club.heiqi.uilib.ui.image.HostImageSource;
import club.heiqi.uilib.ui.style.UiStyleProperty;
import club.heiqi.uilib.ui.style.cascade.UiStyleDeclaration;
import club.heiqi.uilib.ui.style.props.UiDisplay;
import club.heiqi.uilib.ui.style.props.UiOverflow;
import club.heiqi.uilib.ui.style.values.UiStyleKeyword;
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

    @Test
    public void shouldParseBackgroundImageUrlAsTexture() {
        UiStyleDeclaration declaration = RemoteCssParser.parseDeclaration(
                "background-image:url(\"minecraft:textures/gui/options_background.png\")");

        Assert.assertNotNull(declaration.getBackgroundImage());
        HostImageSource source = declaration.getBackgroundImage().getSource();
        Assert.assertEquals(HostImageSource.Kind.TEXTURE, source.getKind());
        Assert.assertEquals(new ResourceLocation("minecraft", "textures/gui/options_background.png"),
                source.getTexture());
        Assert.assertEquals(1, source.getTextureWidth());
        Assert.assertEquals(1, source.getTextureHeight());
    }

    @Test
    public void shouldParseBackgroundImageNoneAsInitialKeyword() {
        UiStyleDeclaration declaration = RemoteCssParser.parseDeclaration(
                "background-image:url(qz_uilib:textures/test/card.png);background-image:none");

        Assert.assertNull(declaration.getBackgroundImage());
        Assert.assertEquals(UiStyleKeyword.INITIAL, declaration.getKeyword(UiStyleProperty.BACKGROUND_IMAGE));
    }

    @Test
    public void shouldIgnoreUnsupportedBackgroundImageUrl() {
        UiStyleDeclaration declaration = RemoteCssParser.parseDeclaration(
                "background-image:url(https://example.com/card.png)");

        Assert.assertNull(declaration.getBackgroundImage());
        Assert.assertNull(declaration.getKeyword(UiStyleProperty.BACKGROUND_IMAGE));
    }
}
