package club.heiqi.uilib.ui.scene.text;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.text.TextContentMode;
import club.heiqi.uilib.ui.scene.paint.TextStyle;

/**
 * SceneTextMode 编码契约编译期守卫。
 *
 * <p>scene 层 int 编码（paint.TextStyle TEXT_MODE_*、SceneNode 遗留 setter）与枚举
 * {@link SceneTextMode#getCode()}、渲染层 {@link TextContentMode} 序数值必须逐位对齐：
 * 任一漂移都是运行时语义错位（模式静默切换），本测试锁死三处锚点。</p>
 */
public class SceneTextModeTest {

    @Test
    public void paintTextStyleConstantsAlignWithEnumCodes() {
        Assert.assertEquals(TextStyle.TEXT_MODE_UILIB_RAW, SceneTextMode.UILIB_RAW.getCode());
        Assert.assertEquals(TextStyle.TEXT_MODE_MINECRAFT_FORMATTED, SceneTextMode.MINECRAFT_FORMATTED.getCode());
        Assert.assertEquals(TextStyle.TEXT_MODE_RICH_TAGS, SceneTextMode.RICH_TAGS.getCode());
    }

    @Test
    public void textContentModeOrdinalsAlignWithEnumCodes() {
        for (SceneTextMode mode : SceneTextMode.values()) {
            Assert.assertEquals("TextContentMode 序数值须与 code 对齐",
                    mode.getCode(), TextContentMode.values()[mode.getCode()].ordinal());
        }
    }

    @Test
    public void fromCodeResolvesKnownCodes() {
        Assert.assertEquals(SceneTextMode.UILIB_RAW, SceneTextMode.fromCode(0));
        Assert.assertEquals(SceneTextMode.MINECRAFT_FORMATTED, SceneTextMode.fromCode(1));
        Assert.assertEquals(SceneTextMode.RICH_TAGS, SceneTextMode.fromCode(2));
    }

    @Test
    public void fromCodeFallsBackToRawOnOutOfRange() {
        Assert.assertEquals(SceneTextMode.UILIB_RAW, SceneTextMode.fromCode(-1));
        Assert.assertEquals(SceneTextMode.UILIB_RAW, SceneTextMode.fromCode(3));
        Assert.assertEquals(SceneTextMode.UILIB_RAW, SceneTextMode.fromCode(Integer.MAX_VALUE));
    }

    @Test
    public void intConstructorNormalizesOutOfRangeCodes() {
        // paint.TextStyle 遗留 int 构造器须与 fromCode 归一语义一致（吸收原 normalizeTextMode）
        Assert.assertEquals(SceneTextMode.UILIB_RAW,
                new TextStyle(0xFFFFFFFF, 16, -5).getMode());
        Assert.assertEquals(SceneTextMode.UILIB_RAW,
                new TextStyle(0xFFFFFFFF, 16, 99).getMode());
        Assert.assertEquals(SceneTextMode.RICH_TAGS,
                new TextStyle(0xFFFFFFFF, 16, 2).getMode());
    }
}
