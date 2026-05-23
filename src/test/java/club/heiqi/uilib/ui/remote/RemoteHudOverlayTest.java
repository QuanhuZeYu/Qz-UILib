package club.heiqi.uilib.ui.remote;

import org.junit.Assert;
import org.junit.Test;

/**
 * 远程 HUD 浮层模型测试。
 */
public class RemoteHudOverlayTest {

    @Test
    public void shouldApplyDialogToastAndDanmakuDefaults() {
        RemoteDocumentPage page = RemoteDocumentPage.of("page", "Title", "<p>ok</p>");

        RemoteHudOverlay dialog = RemoteHudOverlay.dialog("dialog", page).build();
        RemoteHudOverlay toast = RemoteHudOverlay.toast("toast", page).build();
        RemoteHudOverlay danmaku = RemoteHudOverlay.danmaku("danmaku", page).build();

        Assert.assertEquals(RemoteHudOverlayMode.DIALOG, dialog.getMode());
        Assert.assertEquals(RemoteHudOverlay.STICKY_DURATION_MILLIS, dialog.getDurationMillis());
        Assert.assertTrue(dialog.isDefaultCloseButtonVisible());
        Assert.assertEquals(RemoteHudOverlay.DEFAULT_TOAST_DURATION_MILLIS, toast.getDurationMillis());
        Assert.assertFalse(toast.isDefaultCloseButtonVisible());
        Assert.assertEquals(RemoteHudOverlay.DEFAULT_DANMAKU_DURATION_MILLIS, danmaku.getDurationMillis());
        Assert.assertFalse(danmaku.isDefaultCloseButtonVisible());
    }

    @Test
    public void shouldClampDurationAndPreserveMetadata() {
        RemoteDocumentPage page = RemoteDocumentPage.of("page", "Title", "<p>ok</p>");

        RemoteHudOverlay overlay = RemoteHudOverlay.toast("toast", page)
                .durationMillis(RemoteHudOverlay.MAX_DURATION_MILLIS + 1L)
                .metadata("placement", "top-left")
                .build();

        Assert.assertEquals(RemoteHudOverlay.MAX_DURATION_MILLIS, overlay.getDurationMillis());
        Assert.assertEquals("top-left", overlay.getMetadata().get("placement"));
    }
}
