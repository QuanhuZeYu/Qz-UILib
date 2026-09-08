package club.heiqi.uilib.internal.chat3.view;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.api.chat.ChatActionService;
import club.heiqi.uilib.internal.chat3.input.ChatToolbar;
import club.heiqi.uilib.ui.hud.api.HudToolbarService;
import club.heiqi.uilib.ui.hud.api.HudToolbarSide;
import club.heiqi.uilib.ui.hud.api.HudToolbarSpec;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;

/**
 * 聊天 HUD 外接工具栏注册契约（真机「再打开聊天后工具栏消失」回归）。
 *
 * <p>{@link HudToolbarService#clear()} 只清注册表 map、不关闭已发出的句柄；若
 * {@code ensureToolbarRegistered} 只看静态句柄的 {@code isClosed()}，clear 之后就会永久跳过
 * 重装，与"外部 clear 后仍能装回"的注释承诺矛盾。本类以注册表实际存在性为准做红绿回归。</p>
 */
public class ChatHudWindowToolbarTest {

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        ChatActionService.getInstance().clear();
        HudToolbarService.getInstance().clear();
        ChatHudWindow.close();
        ChatHudWindow.setToolbarSide(HudToolbarSide.DEFAULT);
    }

    @After
    public void tearDown() {
        ChatActionService.getInstance().clear();
        HudToolbarService.getInstance().clear();
        ChatHudWindow.close();
        ChatHudWindow.setToolbarSide(HudToolbarSide.DEFAULT);
        ReactiveScheduler.get().reset();
    }

    @Test
    public void reRegistersAfterExternalRegistryClear() {
        ChatHudWindow.attachToolbarHost(ChatToolbar.inertHost());
        ChatHudWindow.ensureToolbarRegistered();
        Assert.assertTrue("首次必须注册", HudToolbarService.getInstance().hasToolbar(ChatHudWindow.HUD_ID));

        HudToolbarService.getInstance().clear();
        Assert.assertFalse("clear 后注册表为空", HudToolbarService.getInstance().hasToolbar(ChatHudWindow.HUD_ID));

        ChatHudWindow.ensureToolbarRegistered();
        Assert.assertTrue("clear 之后必须按注册表实际内容重装（陈旧句柄未关闭不得短路）",
                HudToolbarService.getInstance().hasToolbar(ChatHudWindow.HUD_ID));

        // 幂等：注册表已有该项时重复调用不得重复注册（重复 id 会抛异常）
        ChatHudWindow.ensureToolbarRegistered();
        Assert.assertTrue(HudToolbarService.getInstance().hasToolbar(ChatHudWindow.HUD_ID));
    }

    @Test
    public void specThicknessFitsSideOrientation() {
        ChatHudWindow.setToolbarSide(HudToolbarSide.BOTTOM);
        Assert.assertEquals("水平边用默认行高",
                HudToolbarSpec.DEFAULT_THICKNESS_PX, ChatHudWindow.chatToolbarSpec().getThickness());

        ChatHudWindow.setToolbarSide(HudToolbarSide.RIGHT);
        Assert.assertEquals("竖直边条宽必须容得下文本标签",
                ChatToolbar.VERTICAL_THICKNESS_PX, ChatHudWindow.chatToolbarSpec().getThickness());
        Assert.assertEquals(HudToolbarSide.RIGHT, ChatHudWindow.chatToolbarSpec().getSide());
    }
}
