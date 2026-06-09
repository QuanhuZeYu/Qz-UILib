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
