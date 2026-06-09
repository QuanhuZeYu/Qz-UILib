package club.heiqi.uilib.ui.remote;

import org.junit.Assert;
import org.junit.Test;

/**
 * 远程 UI 客户端 runtime 异步落地 guard 测试。
 */
public class RemoteUiClientRuntimeTest {

    @Test
    public void shouldRejectStaleMountByLocalToken() {
        RemoteUiClientRuntime runtime = new RemoteUiClientRuntime();
        RemoteUiClientRuntime.PendingMount first = runtime.beginOpen(RemoteUiProtocol.SurfaceType.PAGE,
                RemoteUiProtocol.PAGE_PRIMARY_SURFACE_ID, "S1", 1L);
        RemoteUiClientRuntime.PendingMount second = runtime.beginOpen(RemoteUiProtocol.SurfaceType.PAGE,
                RemoteUiProtocol.PAGE_PRIMARY_SURFACE_ID, "S2", 1L);

        Assert.assertFalse(runtime.completePending(RemoteUiProtocol.SurfaceType.PAGE,
                RemoteUiProtocol.PAGE_PRIMARY_SURFACE_ID, "S1", 1L, first.getLocalMountToken()));
        Assert.assertTrue(runtime.completePending(RemoteUiProtocol.SurfaceType.PAGE,
                RemoteUiProtocol.PAGE_PRIMARY_SURFACE_ID, "S2", 1L, second.getLocalMountToken()));
        Assert.assertEquals(0, runtime.pendingSizeForTests());
    }

    @Test
    public void shouldRemoveReplacedPendingMountWhenNewOfferBecomesCurrent() {
        RemoteUiClientRuntime runtime = new RemoteUiClientRuntime();
        RemoteUiClientRuntime.PendingMount first = runtime.beginOpen(RemoteUiProtocol.SurfaceType.PAGE,
                RemoteUiProtocol.PAGE_PRIMARY_SURFACE_ID, "S1", 1L);

        runtime.beginOpen(RemoteUiProtocol.SurfaceType.PAGE, RemoteUiProtocol.PAGE_PRIMARY_SURFACE_ID, "S2", 1L);

        Assert.assertFalse(runtime.discard(RemoteUiProtocol.SurfaceType.PAGE,
                RemoteUiProtocol.PAGE_PRIMARY_SURFACE_ID, "S1", 1L, first.getLocalMountToken()));
        Assert.assertEquals("新 offer 替换当前 surface 时应立即移除旧 pending", 1,
                runtime.pendingSizeForTests());
    }

    @Test
    public void shouldTerminalizeFailedCurrentMount() {
        RemoteUiClientRuntime runtime = new RemoteUiClientRuntime();
        RemoteUiClientRuntime.PendingMount mount = runtime.beginOpen(RemoteUiProtocol.SurfaceType.PAGE,
                RemoteUiProtocol.PAGE_PRIMARY_SURFACE_ID, "S1", 1L);

        Assert.assertTrue(runtime.terminalizeError(RemoteUiProtocol.SurfaceType.PAGE,
                RemoteUiProtocol.PAGE_PRIMARY_SURFACE_ID, "S1", 1L, mount.getLocalMountToken()));

        Assert.assertEquals(0, runtime.pendingSizeForTests());
        Assert.assertEquals(0, runtime.currentSizeForTests());
        Assert.assertFalse(runtime.isCurrent(RemoteUiProtocol.SurfaceType.PAGE,
                RemoteUiProtocol.PAGE_PRIMARY_SURFACE_ID, "S1", 1L, mount.getLocalMountToken()));
    }

    @Test
    public void shouldCloseOnlyMatchingSessionRevisionAndToken() {
        RemoteUiClientRuntime runtime = new RemoteUiClientRuntime();
        RemoteUiClientRuntime.PendingMount mount = runtime.beginOpen(RemoteUiProtocol.SurfaceType.HUD,
                "overlay", "S1", 3L);
        Assert.assertTrue(runtime.completePending(RemoteUiProtocol.SurfaceType.HUD, "overlay", "S1", 3L,
                mount.getLocalMountToken()));

        Assert.assertFalse(runtime.closeSession(RemoteUiProtocol.SurfaceType.HUD, "overlay", "S1", 2L,
                mount.getLocalMountToken()));
        Assert.assertFalse(runtime.closeSession(RemoteUiProtocol.SurfaceType.HUD, "overlay", "S1", 3L,
                mount.getLocalMountToken() + 1L));
        Assert.assertTrue(runtime.closeSession(RemoteUiProtocol.SurfaceType.HUD, "overlay", "S1", 3L,
                mount.getLocalMountToken()));
    }

    @Test
    public void shouldCloseSurfaceWithoutEmptySessionSentinel() {
        RemoteUiClientRuntime runtime = new RemoteUiClientRuntime();
        RemoteUiClientRuntime.PendingMount mount = runtime.beginOpen(RemoteUiProtocol.SurfaceType.HUD,
                "overlay", "S1", 1L);
        Assert.assertTrue(runtime.completePending(RemoteUiProtocol.SurfaceType.HUD, "overlay", "S1", 1L,
                mount.getLocalMountToken()));

        Assert.assertTrue(runtime.closeSurface(RemoteUiProtocol.SurfaceType.HUD, "overlay"));
        Assert.assertFalse(runtime.isCurrent(RemoteUiProtocol.SurfaceType.HUD, "overlay", "S1", 1L,
                mount.getLocalMountToken()));
    }
}
