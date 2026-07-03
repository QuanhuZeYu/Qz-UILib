package club.heiqi.config.ui.theme;

import club.heiqi.uilib.ui.scene.form.FormTheme;
import org.junit.Assert;
import org.junit.Test;

/**
 * 守护 {@link FormTheme#defaultDark()} 与 {@link ConfigTheme} 仍物理重复的 9 个共享字段对齐。
 *
 * <p>U1 后卡片相关 6 个 CARD_* 常量已从 ConfigTheme 删除（下沉为 FormTheme 独有），
 * 剩余 9 个字段（fieldGap/textColor/mutedColor/errorColor/dirtyColor/
 * fontLabel/fontHelper/fontError/inputHeight）在两侧仍是各自定义的常量/字面量，
 * 属于双真相源，本测试断言其值一致，防止后续单边修改导致漂移。</p>
 */
public class ConfigThemeAlignmentTest {

    @Test
    public void formThemeDefaultDarkAlignsWithConfigThemeSharedFields() {
        FormTheme t = ConfigTheme.asFormTheme();

        Assert.assertEquals("fieldGap 应与 ConfigTheme.FIELD_GAP 对齐",
                ConfigTheme.FIELD_GAP, t.fieldGap());
        Assert.assertEquals("textColor 应与 ConfigTheme.TEXT_COLOR 对齐",
                ConfigTheme.TEXT_COLOR, t.textColor());
        Assert.assertEquals("mutedColor 应与 ConfigTheme.MUTED_COLOR 对齐",
                ConfigTheme.MUTED_COLOR, t.mutedColor());
        Assert.assertEquals("errorColor 应与 ConfigTheme.ERROR_COLOR 对齐",
                ConfigTheme.ERROR_COLOR, t.errorColor());
        Assert.assertEquals("dirtyColor 应与 ConfigTheme.DIRTY_COLOR 对齐",
                ConfigTheme.DIRTY_COLOR, t.dirtyColor());
        Assert.assertEquals("fontLabel 应与 ConfigTheme.FONT_LABEL 对齐",
                ConfigTheme.FONT_LABEL, t.fontLabel());
        Assert.assertEquals("fontHelper 应与 ConfigTheme.FONT_HELPER 对齐",
                ConfigTheme.FONT_HELPER, t.fontHelper());
        Assert.assertEquals("fontError 应与 ConfigTheme.FONT_ERROR 对齐",
                ConfigTheme.FONT_ERROR, t.fontError());
        Assert.assertEquals("inputHeight 应与 ConfigTheme.INPUT_HEIGHT 对齐",
                ConfigTheme.INPUT_HEIGHT, t.inputHeight());
    }
}
