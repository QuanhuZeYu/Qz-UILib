package club.heiqi.uilib.config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.net.api.NetBody;
import club.heiqi.uilib.net.api.NetService;
import club.heiqi.uilib.net.core.NetEnvelope;
import club.heiqi.uilib.net.core.NetPayloadLimits;
import club.heiqi.uilib.net.transport.FrameHandler;
import club.heiqi.uilib.net.transport.ITransport;
import club.heiqi.uilib.net.transport.NetReceiveOrigin;
import club.heiqi.uilib.net.transport.NetSide;
import club.heiqi.uilib.ui.remote.RemoteDocumentPages;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * 远程配置页与配置同步 session 绑定生命周期测试。
 */
public class RemoteConfigDocumentPagesLifecycleTest {

    private ConfigTemplateSyncManager manager;
    private RecordingTransport transport;

    @Before
    public void setUp() {
        resetNetService();
        resetRemoteDocumentPages();
        RemoteConfigDocumentPages.resetForTests();
        manager = ConfigTemplateSyncManager.getInstance();
        manager.resetForTests();
        transport = new RecordingTransport();
        NetService.getInstance().bootstrap(transport);
        manager.register();
        manager.registerTarget(sampleTarget());
        RemoteDocumentPages.register();
    }

    @After
    public void tearDown() {
        RemoteConfigDocumentPages.resetForTests();
        resetRemoteDocumentPages();
        manager.resetForTests();
        resetNetService();
    }

    @Test
    public void shouldCloseConfigSessionWhenRemotePageCloses() {
        FakePlayer player = new FakePlayer("close", 1);
        String remotePageSessionId = RemoteConfigDocumentPages.open(player, "test-config");
        Assert.assertEquals(1, manager.getServerSessionCountForTests());

        transport.deliverToServer(buildCloseFrame(remotePageSessionId), player);
        NetService.getInstance().drainServerMainThreadTasks();

        Assert.assertEquals(0, manager.getServerSessionCountForTests());
        Assert.assertEquals("", manager.getPublishedServerStateForTests(player).sessionId);
    }

    @Test
    public void shouldCloseConfigSessionWhenRemotePageTtlExpires() {
        final AtomicLong nowMillis = new AtomicLong(5_000L);
        setRemotePageClock(new LongSupplier() {
            @Override
            public long getAsLong() {
                return nowMillis.get();
            }
        });
        FakePlayer player = new FakePlayer("ttl", 2);
        RemoteConfigDocumentPages.open(player, "test-config");
        Assert.assertEquals(1, manager.getServerSessionCountForTests());

        nowMillis.addAndGet(remotePageTtlMillis() + 1L);
        tickRemotePageLeaseCleanup();

        Assert.assertEquals(0, manager.getServerSessionCountForTests());
        Assert.assertEquals("", manager.getPublishedServerStateForTests(player).sessionId);
    }

    private static byte[] buildCloseFrame(String remotePageSessionId) {
        String json = "{\"protocolVersion\":1,\"messageType\":\"CLOSE_SURFACE\","
                + "\"feature\":\"remote-ui-lease-v1\",\"sessionId\":\"" + remotePageSessionId + "\","
                + "\"surfaceType\":\"PAGE\",\"surfaceId\":\"primary\",\"contentRevision\":1,"
                + "\"closeScope\":\"SESSION\",\"pageId\":\"\",\"reason\":\"test-close\"}";
        return NetEnvelope.of(NetEnvelope.Kind.CHANNEL, NetSide.SERVER, MyMod.MODID + ":remote_page_close",
                0L, 0, Collections.<String, String>emptyMap(), NetBody.json(json)).encode();
    }

    private static ConfigSyncTarget sampleTarget() {
        Configuration configuration = new Configuration();
        Property mode = configuration.get("general", "mode", "normal", "");
        mode.setValidValues(new String[] { "normal", "safe", "debug" });
        return ConfigSyncTarget.builder("test-config", configuration)
                .categories(Collections.singletonList(new ConfigSyncCategorySpec("general", "General", "测试分类")))
                .build();
    }

    private static void setRemotePageClock(LongSupplier clock) {
        try {
            Method method = RemoteDocumentPages.class.getDeclaredMethod("setSessionClockForTests", LongSupplier.class);
            method.setAccessible(true);
            method.invoke(null, clock);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("无法设置远程页面测试时钟", exception);
        }
    }

    private static long remotePageTtlMillis() {
        try {
            Class<?> gatewayClass = Class.forName("club.heiqi.uilib.ui.remote.RemoteHtmlSessionGateway");
            Field field = gatewayClass.getDeclaredField("DEFAULT_SESSION_TTL_MILLIS");
            field.setAccessible(true);
            return field.getLong(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("无法读取远程页面 TTL", exception);
        }
    }

    private static void resetRemoteDocumentPages() {
        try {
            Method method = RemoteDocumentPages.class.getDeclaredMethod("resetForTests");
            method.setAccessible(true);
            method.invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("无法重置远程页面", exception);
        }
    }

    private static void tickRemotePageLeaseCleanup() {
        try {
            Method method = RemoteDocumentPages.class.getDeclaredMethod("tickLeaseCleanup");
            method.setAccessible(true);
            method.invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("无法推进远程页面 TTL 清扫", exception);
        }
    }

    private static void resetNetService() {
        try {
            Method method = NetService.class.getDeclaredMethod("resetForTests");
            method.setAccessible(true);
            method.invoke(NetService.getInstance());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("无法重置 NetService", exception);
        }
    }

    private static final class RecordingTransport implements ITransport {

        FrameHandler frameHandler;

        @Override
        public String getName() {
            return "recording";
        }

        @Override
        public void bootstrap(FrameHandler frameHandler) {
            this.frameHandler = frameHandler;
        }

        @Override
        public void shutdown() {
        }

        @Override
        public void sendToServer(String channelName, byte[] payload) {
        }

        @Override
        public void sendToPlayer(Object player, String channelName, byte[] payload) {
        }

        @Override
        public void sendToAll(String channelName, byte[] payload) {
        }

        @Override
        public void sendToDimension(int dimensionId, String channelName, byte[] payload) {
        }

        @Override
        public Iterable<?> getConnectedPlayers() {
            return new ArrayList<Object>();
        }

        @Override
        public Integer getPlayerDimensionId(Object player) {
            return Integer.valueOf(player instanceof FakePlayer ? ((FakePlayer) player).dimensionId : 0);
        }

        @Override
        public int getPhysicalFrameLimit(NetSide targetSide) {
            return NetPayloadLimits.GTNH_DEFAULT_PHYSICAL_LIMIT;
        }

        void deliverToServer(byte[] payload, FakePlayer sender) {
            frameHandler.handleFrame(NetService.PHYSICAL_CHANNEL, payload, NetReceiveOrigin.server(sender));
        }
    }

    private static final class FakePlayer {

        final String name;
        final int dimensionId;

        FakePlayer(String name, int dimensionId) {
            this.name = name;
            this.dimensionId = dimensionId;
        }
    }
}
