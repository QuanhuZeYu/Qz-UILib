package club.heiqi.uilib.internal.chat3.input;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.api.chat.ChatAction;
import club.heiqi.uilib.api.chat.ChatActionService;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;

/**
 * 「切换聊天框形态」动作的注册与切换时序。
 *
 * <p>headless：关屏与聊天栏提示都是可替换缝（照 {@code ChatAccess} 的可测缝先例），
 * 生产实现在真机路径上执行。关屏缝在这里模拟「关屏完成回调」的两种时机（立即执行 /
 * 不执行），用来钉住「先关屏、后回灌运行态」的时序契约。</p>
 */
public class ChatFrameIntentTest {

    private final AtomicBoolean screenClosed = new AtomicBoolean();
    private final List<String> notices = new ArrayList<String>();

    @Before
    public void setUp() {
        ChatActionService.getInstance().clear();
        ChatMarkdownSettings.setEnabled(true);
        screenClosed.set(false);
        notices.clear();
        // 生产语义：请求关屏 → 关屏完成后执行回调。
        ChatFrameIntent.__setScreenCloserForTest(onClosed -> {
            screenClosed.set(true);
            onClosed.run();
        });
        ChatFrameIntent.__setNotifierForTest(notices::add);
    }

    @After
    public void tearDown() {
        ChatActionService.getInstance().clear();
        ChatMarkdownSettings.setEnabled(true);
        ChatFrameIntent.__setScreenCloserForTest(null);
        ChatFrameIntent.__setNotifierForTest(null);
    }

    /** ① 端口漏接线是显式失败：不给「点了没反应的按钮」。 */
    @Test
    public void installRejectsMissingPorts() {
        try {
            ChatFrameIntent.install(null, () -> { });
            Assert.fail("install(null, ...) 必须被拒绝（漏接线不得静默留一个点不动的按钮）");
        } catch (IllegalArgumentException expected) {
            // 预期
        }
        try {
            ChatFrameIntent.install(() -> true, null);
            Assert.fail("install(..., null) 必须被拒绝（没有回灌端口就切不了形态）");
        } catch (IllegalArgumentException expected) {
            // 预期
        }
    }

    /** ② 幂等注册：重复 install 不产生重复动作。 */
    @Test
    public void installRegistersExactlyOneActionIdempotently() {
        ChatFrameIntent.install(() -> true, () -> { });
        ChatFrameIntent.install(() -> true, () -> { });
        int count = 0;
        for (ChatAction action : ChatActionService.getInstance().actions()) {
            if (ChatFrameIntent.ACTION_ID.equals(action.getId())) {
                count++;
                Assert.assertEquals("切换聊天框形态", action.getLabel());
                Assert.assertEquals(Boolean.TRUE, action.getVisible().get());
                Assert.assertEquals(Boolean.TRUE, action.getEnabled().get());
            }
        }
        Assert.assertEquals("重复安装不得重复注册", 1, count);
        Assert.assertTrue(ChatFrameIntent.__isInstalledForTest());
    }

    /** ③ 写盘失败：运行态不变、不请求关屏，并给玩家一条提示。 */
    @Test
    public void failedPersistKeepsRuntimeAndNotifiesPlayer() {
        ChatMarkdownSettings.setEnabled(true);
        ChatFrameIntent.install(() -> false, () -> ChatMarkdownSettings.setEnabled(false));
        runAction();
        Assert.assertTrue("写盘失败不得回灌运行态", ChatMarkdownSettings.isEnabled());
        Assert.assertFalse("写盘失败不得请求关屏", screenClosed.get());
        Assert.assertEquals("必须给出一条失败提示", 1, notices.size());
    }

    /** ④ 关屏未完成前不得回灌运行态（否则安装器会在关闭动画期间注销聊天 HUD）。 */
    @Test
    public void runtimeStaysCustomUntilScreenCloseCompletes() {
        ChatFrameIntent.__setScreenCloserForTest(onClosed -> screenClosed.set(true)); // 不执行回调 = 动画未结束
        ChatFrameIntent.install(() -> true, () -> ChatMarkdownSettings.setEnabled(false));
        runAction();
        Assert.assertTrue("必须已请求关屏", screenClosed.get());
        Assert.assertTrue("关屏动画未结束时运行态必须仍是自定义形态", ChatMarkdownSettings.isEnabled());
    }

    /** ⑤ 写盘成功 + 关屏完成：回灌运行态，且不打印失败提示。 */
    @Test
    public void successfulPersistAppliesAfterScreenClose() {
        ChatFrameIntent.install(() -> true, () -> ChatMarkdownSettings.setEnabled(false));
        runAction();
        Assert.assertTrue("必须已请求关屏", screenClosed.get());
        Assert.assertFalse("关屏完成后运行态 = 原版聊天框", ChatMarkdownSettings.isEnabled());
        Assert.assertTrue("成功路径不得提示失败", notices.isEmpty());
    }

    private static void runAction() {
        for (ChatAction action : ChatActionService.getInstance().actions()) {
            if (ChatFrameIntent.ACTION_ID.equals(action.getId())) {
                action.run();
                return;
            }
        }
        Assert.fail("动作未注册：" + ChatFrameIntent.ACTION_ID);
    }
}
