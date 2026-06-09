package club.heiqi.uilib.ui.remote;

import org.junit.Assert;
import org.junit.Test;

/**
 * 远程 UI 内部协议 DTO 测试。
 */
public class RemoteUiProtocolTest {

    @Test
    public void shouldRoundTripOpenSurfacePayloadWithLeaseFields() {
        RemoteUiProtocol.OpenSurfacePayload payload = new RemoteUiProtocol.OpenSurfacePayload();
        payload.sessionId = "session";
        payload.surfaceType = RemoteUiProtocol.SurfaceType.HUD.name();
        payload.surfaceId = "overlay";
        payload.contentRevision = 7L;
        payload.assetId = "asset";
        payload.sha256 = "sha";
        payload.htmlBytes = 12;
        payload.leaseExpiresAtMillis = 1234L;

        RemoteUiProtocol.OpenSurfacePayload decoded = RemoteUiProtocol.fromJson(RemoteUiProtocol.toJson(payload),
                RemoteUiProtocol.OpenSurfacePayload.class);

        RemoteUiProtocol.validateOpenSurface(decoded);
        Assert.assertEquals(RemoteUiProtocol.PROTOCOL_VERSION, decoded.protocolVersion);
        Assert.assertEquals(RemoteUiProtocol.MessageType.OPEN_SURFACE.name(), decoded.messageType);
        Assert.assertEquals(RemoteUiProtocol.FEATURE_LEASE_V1, decoded.feature);
        Assert.assertEquals("overlay", decoded.surfaceId);
        Assert.assertEquals(7L, decoded.contentRevision);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectSubmitWithoutContentRevision() {
        RemoteUiProtocol.SubmitPayload payload = new RemoteUiProtocol.SubmitPayload();
        payload.sessionId = "session";
        payload.surfaceType = RemoteUiProtocol.SurfaceType.PAGE.name();
        payload.surfaceId = RemoteUiProtocol.PAGE_PRIMARY_SURFACE_ID;

        RemoteUiProtocol.validateSubmit(payload);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectSessionCloseWithoutExplicitCloseScope() {
        RemoteUiProtocol.ClosePayload payload = new RemoteUiProtocol.ClosePayload();
        payload.sessionId = "session";
        payload.surfaceType = RemoteUiProtocol.SurfaceType.HUD.name();
        payload.surfaceId = "overlay";
        payload.contentRevision = 1L;

        RemoteUiProtocol.validateClose(payload);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectPageSubmitJsonMissingExplicitSurfaceId() {
        RemoteDocumentPages.decodeSubmitPayload("{\"protocolVersion\":1,\"messageType\":\"SUBMIT\","
                + "\"feature\":\"remote-ui-lease-v1\",\"sessionId\":\"session\","
                + "\"surfaceType\":\"PAGE\",\"contentRevision\":1,\"pageId\":\"page\"}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectHudSessionDismissJsonMissingExplicitContentRevision() {
        RemoteHudOverlays.decodeDismissPayload("{\"protocolVersion\":1,\"messageType\":\"CLOSE_SURFACE\","
                + "\"feature\":\"remote-ui-lease-v1\",\"sessionId\":\"session\","
                + "\"surfaceType\":\"HUD\",\"surfaceId\":\"overlay\","
                + "\"closeScope\":\"SESSION\",\"overlayId\":\"overlay\"}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectPageExpiredJsonMissingExplicitSurfaceId() {
        RemoteDocumentPages.decodeExpiredPayload("{\"protocolVersion\":1,\"messageType\":\"SESSION_EXPIRED\","
                + "\"feature\":\"remote-ui-lease-v1\",\"sessionId\":\"session\","
                + "\"surfaceType\":\"PAGE\",\"contentRevision\":1,\"closeScope\":\"SESSION\","
                + "\"pageId\":\"page\"}");
    }

    @Test
    public void shouldAllowSurfaceScopedCloseWithoutSessionId() {
        RemoteUiProtocol.ClosePayload payload = new RemoteUiProtocol.ClosePayload();
        payload.surfaceType = RemoteUiProtocol.SurfaceType.HUD.name();
        payload.surfaceId = "overlay";
        payload.closeScope = RemoteUiProtocol.CloseScope.SURFACE.name();

        RemoteUiProtocol.validateClose(payload);
        Assert.assertEquals(RemoteUiProtocol.CloseScope.SURFACE,
                RemoteUiProtocol.parseCloseScope(payload.closeScope));
    }
}
