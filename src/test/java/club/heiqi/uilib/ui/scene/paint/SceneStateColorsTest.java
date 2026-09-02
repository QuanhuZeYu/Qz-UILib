package club.heiqi.uilib.ui.scene.paint;

import org.junit.Assert;
import org.junit.Test;

/**
 * SceneStateColors 状态到 chrome token 查表测试。
 */
public class SceneStateColorsTest {

    @Test
    public void standardBackgroundShouldResolveAllBranches() {
        Assert.assertEquals("禁用态背景", SceneChromeTokens.BG_DISABLED,
                SceneStateColors.standardBackground(false, true, true));
        Assert.assertEquals("按下态背景", SceneChromeTokens.BG_PRESSED,
                SceneStateColors.standardBackground(true, true, true));
        Assert.assertEquals("悬停态背景", SceneChromeTokens.BG_HOVER,
                SceneStateColors.standardBackground(true, true, false));
        Assert.assertEquals("默认态背景", SceneChromeTokens.BG_DEFAULT,
                SceneStateColors.standardBackground(true, false, false));
    }

    @Test
    public void selectedBackgroundShouldResolveAllBranches() {
        Assert.assertEquals("禁用态选中背景", SceneChromeTokens.BG_DISABLED,
                SceneStateColors.selectedBackground(false, true, true));
        Assert.assertEquals("按下态选中背景", SceneChromeTokens.ACCENT_PRESSED,
                SceneStateColors.selectedBackground(true, true, true));
        Assert.assertEquals("悬停态选中背景", SceneChromeTokens.ACCENT_HOVER,
                SceneStateColors.selectedBackground(true, true, false));
        Assert.assertEquals("默认态选中背景", SceneChromeTokens.ACCENT,
                SceneStateColors.selectedBackground(true, false, false));
    }

    /**
     * 危险通道四态。禁用态刻意走 {@code DANGER_BG_DISABLED}（Red 通道自己的禁用色），
     * 而不是灰通道 {@code BG_DISABLED} —— 该 token 早已存在却长期零消费者，
     * 因为危险按钮此前根本没有 disabled 派生路径。
     */
    @Test
    public void dangerBackgroundShouldResolveAllBranches() {
        Assert.assertEquals("禁用态危险背景", SceneChromeTokens.DANGER_BG_DISABLED,
                SceneStateColors.dangerBackground(false, true, true));
        Assert.assertEquals("按下态危险背景", SceneChromeTokens.DANGER_BG_PRESSED,
                SceneStateColors.dangerBackground(true, true, true));
        Assert.assertEquals("悬停态危险背景", SceneChromeTokens.DANGER_BG_HOVER,
                SceneStateColors.dangerBackground(true, true, false));
        Assert.assertEquals("默认态危险背景", SceneChromeTokens.DANGER_BG,
                SceneStateColors.dangerBackground(true, false, false));
    }

    @Test
    public void standardBorderShouldResolveAllBranches() {
        Assert.assertEquals("禁用态边框", SceneChromeTokens.BORDER_DISABLED,
                SceneStateColors.standardBorder(false, true));
        Assert.assertEquals("聚焦态边框", SceneChromeTokens.BORDER_FOCUS,
                SceneStateColors.standardBorder(true, true));
        Assert.assertEquals("默认态边框", SceneChromeTokens.BORDER_DEFAULT,
                SceneStateColors.standardBorder(true, false));
    }

    @Test
    public void standardTextShouldResolveAllBranches() {
        Assert.assertEquals("禁用态文本", SceneChromeTokens.TEXT_DISABLED,
                SceneStateColors.standardText(false, true));
        Assert.assertEquals("选中态文本", SceneChromeTokens.TEXT_ON_ACCENT,
                SceneStateColors.standardText(true, true));
        Assert.assertEquals("默认态文本", SceneChromeTokens.TEXT_PRIMARY,
                SceneStateColors.standardText(true, false));
    }

    @Test
    public void secondaryTextShouldResolveAllBranches() {
        Assert.assertEquals("禁用态次要文本", SceneChromeTokens.TEXT_DISABLED,
                SceneStateColors.secondaryText(false));
        Assert.assertEquals("默认态次要文本", SceneChromeTokens.TEXT_SECONDARY,
                SceneStateColors.secondaryText(true));
    }

    @Test
    public void inputBackgroundShouldResolveAllBranches() {
        Assert.assertEquals("禁用态输入区背景", SceneChromeTokens.BG_DISABLED,
                SceneStateColors.inputBackground(false));
        Assert.assertEquals("默认态输入区背景", SceneChromeTokens.BG_PRESSED,
                SceneStateColors.inputBackground(true));
    }

    @Test
    public void listItemBackgroundShouldKeepSelectedOnlyTransparentAndInteractiveFeedbackVisible() {
        Assert.assertEquals("禁用态 item 背景", SceneChromeTokens.BG_DISABLED,
                SceneStateColors.listItemBackground(false, true, true, true));
        Assert.assertEquals("选中+hover 走选中悬停色", SceneChromeTokens.ACCENT_HOVER,
                SceneStateColors.listItemBackground(true, true, true, true));
        Assert.assertEquals("选中+highlight 走选中高亮色", SceneChromeTokens.ACCENT_PRESSED,
                SceneStateColors.listItemBackground(true, true, true, false));
        Assert.assertEquals("纯选中背景透明", 0x00000000,
                SceneStateColors.listItemBackground(true, true, false, false));
        Assert.assertEquals("未选中高亮背景", SceneChromeTokens.BG_DEFAULT,
                SceneStateColors.listItemBackground(true, false, true, false));
        Assert.assertEquals("未选中 hover 背景", SceneChromeTokens.BG_HOVER,
                SceneStateColors.listItemBackground(true, false, false, true));
        Assert.assertEquals("默认 item 透明", 0x00000000,
                SceneStateColors.listItemBackground(true, false, false, false));
    }

    @Test
    public void listItemTextShouldUseOnAccentOnlyWhenSelectedInteractive() {
        Assert.assertEquals("禁用态 item 文本", SceneChromeTokens.TEXT_DISABLED,
                SceneStateColors.listItemText(false, true, true, true));
        Assert.assertEquals("selected-only 透明背景用普通文本色", SceneChromeTokens.TEXT_PRIMARY,
                SceneStateColors.listItemText(true, true, false, false));
        Assert.assertEquals("选中+highlight accent 背景用白字", SceneChromeTokens.TEXT_ON_ACCENT,
                SceneStateColors.listItemText(true, true, true, false));
        Assert.assertEquals("选中+hover accent 背景用白字", SceneChromeTokens.TEXT_ON_ACCENT,
                SceneStateColors.listItemText(true, true, false, true));
        Assert.assertEquals("未选中高亮仍用普通文本色", SceneChromeTokens.TEXT_PRIMARY,
                SceneStateColors.listItemText(true, false, true, false));
    }

    @Test
    public void thumbBackgroundShouldResolveAllBranches() {
        Assert.assertEquals("禁用态 thumb", SceneChromeTokens.TEXT_DISABLED,
                SceneStateColors.thumbBackground(false, true, true));
        Assert.assertEquals("按下态 thumb", SceneChromeTokens.THUMB_PRESSED,
                SceneStateColors.thumbBackground(true, true, true));
        Assert.assertEquals("悬停态 thumb", SceneChromeTokens.THUMB_HOVER,
                SceneStateColors.thumbBackground(true, true, false));
        Assert.assertEquals("默认态 thumb", SceneChromeTokens.THUMB_DEFAULT,
                SceneStateColors.thumbBackground(true, false, false));
    }
}
