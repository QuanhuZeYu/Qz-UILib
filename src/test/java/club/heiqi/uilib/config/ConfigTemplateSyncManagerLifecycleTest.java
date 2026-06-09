package club.heiqi.uilib.config;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.net.api.NetService;
import club.heiqi.uilib.net.core.NetPayloadLimits;
import club.heiqi.uilib.net.transport.FrameHandler;
import club.heiqi.uilib.net.transport.ITransport;
import club.heiqi.uilib.net.transport.NetSide;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * 配置同步服务端生命周期清理测试。
 */
public class ConfigTemplateSyncManagerLifecycleTest {

    private ConfigTemplateSyncManager manager;
    private RecordingTransport transport;

    @Before
    public void setUp() {
        resetNetService();
        manager = ConfigTemplateSyncManager.getInstance();
        manager.resetForTests();
        transport = new RecordingTransport();
        NetService.getInstance().bootstrap(transport);
        manager.register();
        manager.registerTarget(sampleTarget());
    }

    @After
    public void tearDown() {
        manager.resetForTests();
        resetNetService();
    }

    @Test
    public void shouldClearServerSessionsAndPlayerStoreWhenPlayerLeaves() {
        FakePlayer player = new FakePlayer("leave", 1);
        ConfigTemplateRemoteSession session = manager.openServerSession("test-config", player);

        manager.onServerPlayerLeft(player);

        Assert.assertEquals(0, manager.getServerSessionCountForTests());
        Assert.assertEquals("", manager.getPublishedServerStateForTests(player).sessionId);
        try {
            manager.saveServerSession(session.getSessionId(), player, new ConfigSyncModels.ConfigDraftSnapshot());
            Assert.fail("离线清理后不应继续保存旧 session");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("不存在"));
        }
    }

    @Test
    public void shouldResetPlayerStoreWhenServerSessionCloses() {
        FakePlayer player = new FakePlayer("close", 2);
        ConfigTemplateRemoteSession session = manager.openServerSession("test-config", player);
        Assert.assertEquals(session.getSessionId(), manager.getPublishedServerStateForTests(player).sessionId);

        Assert.assertTrue(manager.closeServerSession(session.getSessionId(), player));

        Assert.assertEquals(0, manager.getServerSessionCountForTests());
        Assert.assertEquals("", manager.getPublishedServerStateForTests(player).sessionId);
        Assert.assertEquals(2, transport.playerPayloads.size());
    }

    private static ConfigSyncTarget sampleTarget() {
        Configuration configuration = new Configuration();
        Property mode = configuration.get("general", "mode", "normal", "运行模式");
        mode.setValidValues(new String[] { "normal", "safe", "debug" });
        return ConfigSyncTarget.builder("test-config", configuration)
                .categories(Collections.singletonList(new ConfigSyncCategorySpec("general", "General", "测试分类")))
                .build();
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

        final java.util.List<PlayerPayload> playerPayloads = new ArrayList<PlayerPayload>();

        @Override
        public String getName() {
            return "recording";
        }

        @Override
        public void bootstrap(FrameHandler frameHandler) {
        }

        @Override
        public void shutdown() {
        }

        @Override
        public void sendToServer(String channelName, byte[] payload) {
        }

        @Override
        public void sendToPlayer(Object player, String channelName, byte[] payload) {
            playerPayloads.add(new PlayerPayload(player, payload));
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
    }

    private static final class PlayerPayload {

        final Object player;
        final byte[] payload;

        PlayerPayload(Object player, byte[] payload) {
            this.player = player;
            this.payload = payload;
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
