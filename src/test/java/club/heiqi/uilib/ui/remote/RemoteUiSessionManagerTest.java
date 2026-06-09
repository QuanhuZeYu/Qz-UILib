package club.heiqi.uilib.ui.remote;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.junit.Assert;
import org.junit.Test;

/**
 * 远程 UI session manager 状态机与 close scope 测试。
 */
public class RemoteUiSessionManagerTest {

    @Test
    public void shouldCreateSessionWithRevisionLeaseAndAsset() {
        RemoteUiSessionManager<String> manager = new RemoteUiSessionManager<String>("test");
        RemoteUiSessionManager.RemoteUiSession<String> session = manager.createSession("player",
                RemoteUiProtocol.SurfaceType.PAGE, RemoteUiProtocol.PAGE_PRIMARY_SURFACE_ID, "payload", "<p>ok</p>");

        Assert.assertEquals(RemoteUiProtocol.SurfaceType.PAGE, session.getSurfaceType());
        Assert.assertEquals(RemoteUiProtocol.PAGE_PRIMARY_SURFACE_ID, session.getSurfaceId());
        Assert.assertEquals(1L, session.getContentRevision());
        Assert.assertEquals(RemoteUiProtocol.LeasePolicy.FIXED, session.getLeasePolicy());
        Assert.assertEquals(RemoteUiProtocol.SessionState.OFFER_SENT, session.getState());
        Assert.assertNotNull(session.getAssetId());
        Assert.assertEquals("payload", session.getPayload());
    }

    @Test
    public void shouldRejectStaleStreamByRevisionAssetAndSurface() {
        RemoteUiSessionManager<String> manager = new RemoteUiSessionManager<String>("test");
        RemoteUiSessionManager.RemoteUiSession<String> session = manager.createSession("player",
                RemoteUiProtocol.SurfaceType.HUD, "overlay", "payload", "<p>ok</p>");

        RemoteUiProtocol.FetchHtmlRequest staleRevision = fetch(session);
        staleRevision.contentRevision = 2L;
        Assert.assertFalse(manager.validateFetch(staleRevision, "player", null).isOk());

        RemoteUiProtocol.FetchHtmlRequest staleAsset = fetch(session);
        staleAsset.assetId = "other";
        Assert.assertFalse(manager.validateFetch(staleAsset, "player", null).isOk());

        RemoteUiProtocol.FetchHtmlRequest staleSurface = fetch(session);
        staleSurface.surfaceId = "other";
        Assert.assertFalse(manager.validateFetch(staleSurface, "player", null).isOk());
    }

    @Test
    public void shouldRejectStaleSubmitByRevisionAndSurface() {
        RemoteUiSessionManager<String> manager = new RemoteUiSessionManager<String>("test");
        RemoteUiSessionManager.RemoteUiSession<String> session = manager.createSession("player",
                RemoteUiProtocol.SurfaceType.PAGE, RemoteUiProtocol.PAGE_PRIMARY_SURFACE_ID, "payload", "<p>ok</p>");

        RemoteUiProtocol.SubmitPayload staleRevision = submit(session);
        staleRevision.contentRevision = 2L;
        Assert.assertFalse(manager.validateSubmit(staleRevision, "player", null).isOk());

        RemoteUiProtocol.SubmitPayload staleSurface = submit(session);
        staleSurface.surfaceId = "other";
        Assert.assertFalse(manager.validateSubmit(staleSurface, "player", null).isOk());
    }

    @Test
    public void shouldNotifyAndRemoveExpiredSession() {
        final AtomicLong now = new AtomicLong(10L);
        RemoteUiSessionManager<String> manager = new RemoteUiSessionManager<String>("test");
        manager.setClockForTests(new LongSupplier() {
            @Override
            public long getAsLong() {
                return now.get();
            }
        });
        final List<String> removed = new ArrayList<String>();
        RemoteUiSessionManager.RemoteUiSession<String> session = manager.createSession("player",
                RemoteUiProtocol.SurfaceType.PAGE, RemoteUiProtocol.PAGE_PRIMARY_SURFACE_ID, "payload", "<p>ok</p>");

        now.addAndGet(RemoteUiSessionManager.DEFAULT_LEASE_MILLIS + 1L);
        manager.cleanupExpiredSessions(new RemoteUiSessionManager.SessionRemovalListener<String>() {
            @Override
            public void onSessionRemoved(RemoteUiSessionManager.RemoteUiSession<String> session) {
                removed.add(session.getSessionId());
            }
        });

        Assert.assertEquals(1, removed.size());
        Assert.assertEquals(session.getSessionId(), removed.get(0));
        Assert.assertNull(manager.getSession(session.getSessionId()));
    }

    @Test
    public void shouldCloseOnlyMatchingSessionScope() {
        RemoteUiSessionManager<String> manager = new RemoteUiSessionManager<String>("test");
        RemoteUiSessionManager.RemoteUiSession<String> first = manager.createSession("player",
                RemoteUiProtocol.SurfaceType.HUD, "overlay", "first", "one");
        RemoteUiSessionManager.RemoteUiSession<String> second = manager.createSession("player",
                RemoteUiProtocol.SurfaceType.HUD, "overlay", "second", "two");

        Assert.assertFalse(manager.closeSession("player", RemoteUiProtocol.SurfaceType.HUD, "overlay",
                first.getSessionId(), first.getContentRevision()).isClosed());
        Assert.assertNotNull(manager.getSession(second.getSessionId()));

        Assert.assertTrue(manager.closeSession("player", RemoteUiProtocol.SurfaceType.HUD, "overlay",
                second.getSessionId(), second.getContentRevision()).isClosed());
        Assert.assertNull(manager.getSession(second.getSessionId()));
    }

    @Test
    public void shouldCloseCurrentSurfaceScope() {
        RemoteUiSessionManager<String> manager = new RemoteUiSessionManager<String>("test");
        RemoteUiSessionManager.RemoteUiSession<String> session = manager.createSession("player",
                RemoteUiProtocol.SurfaceType.HUD, "overlay", "payload", "html");

        RemoteUiSessionManager.CloseResult<String> result = manager.closeSurface("player",
                RemoteUiProtocol.SurfaceType.HUD, "overlay");

        Assert.assertTrue(result.isClosed());
        Assert.assertEquals(session.getSessionId(), result.getSession().getSessionId());
        Assert.assertNull(manager.getSession(session.getSessionId()));
    }

    private static RemoteUiProtocol.FetchHtmlRequest fetch(RemoteUiSessionManager.RemoteUiSession<?> session) {
        RemoteUiProtocol.FetchHtmlRequest request = new RemoteUiProtocol.FetchHtmlRequest();
        request.sessionId = session.getSessionId();
        request.surfaceType = session.getSurfaceType().name();
        request.surfaceId = session.getSurfaceId();
        request.contentRevision = session.getContentRevision();
        request.assetId = session.getAssetId();
        return request;
    }

    private static RemoteUiProtocol.SubmitPayload submit(RemoteUiSessionManager.RemoteUiSession<?> session) {
        RemoteUiProtocol.SubmitPayload payload = new RemoteUiProtocol.SubmitPayload();
        payload.sessionId = session.getSessionId();
        payload.surfaceType = session.getSurfaceType().name();
        payload.surfaceId = session.getSurfaceId();
        payload.contentRevision = session.getContentRevision();
        return payload;
    }
}
