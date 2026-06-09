package club.heiqi.uilib.net.transport.forge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.net.api.NetService;
import club.heiqi.uilib.net.core.NetEnvelope;
import club.heiqi.uilib.net.core.NetPayloadLimits;
import club.heiqi.uilib.net.transport.FrameHandler;
import club.heiqi.uilib.net.transport.ITransport;
import club.heiqi.uilib.net.transport.NetSide;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.NetworkManager;
import sun.misc.Unsafe;

/**
 * Forge/FML 回退传输连接生命周期握手测试。
 */
public class ForgeConnectionLifecycleTest {

    private RecordingTransport transport;

    @Before
    public void setUp() throws Exception {
        resetNetService();
        ForgeConnectionLifecycle.getInstance().resetForTests();
        transport = new RecordingTransport();
        NetService.getInstance().bootstrap(transport);
    }

    @After
    public void tearDown() throws Exception {
        NetService.getInstance().drainClientMainThreadTasks();
        NetService.getInstance().drainServerMainThreadTasks();
        ForgeConnectionLifecycle.getInstance().resetForTests();
        resetNetService();
    }

    @Test
    public void shouldSendClientCapabilityHandshakeAfterFmlConnectionEstablishedOnlyOnce() {
        NetworkManager networkManager = new NetworkManager(true);

        ForgeConnectionLifecycle.getInstance().onClientConnectionEstablished(networkManager);
        ForgeConnectionLifecycle.getInstance().onClientConnectionEstablished(networkManager);

        Assert.assertEquals(1, transport.clientToServerPayloads.size());
        NetEnvelope envelope = NetEnvelope.decode(transport.clientToServerPayloads.get(0));
        Assert.assertEquals(NetEnvelope.Kind.META, envelope.getKind());
        Assert.assertEquals(NetSide.SERVER, envelope.getTargetSide());
    }

    @Test
    public void shouldQueueServerCapabilityHandshakeAfterFmlConnectionEstablishedOnlyOnce() {
        NetworkManager networkManager = new NetworkManager(false);
        Object player = new Object();

        ForgeConnectionLifecycle.getInstance().onServerConnectionEstablished(networkManager, player);
        ForgeConnectionLifecycle.getInstance().onServerConnectionEstablished(networkManager, player);
        Assert.assertEquals(0, transport.playerPayloads.size());

        NetService.getInstance().drainServerMainThreadTasks();

        Assert.assertEquals(1, transport.playerPayloads.size());
        Assert.assertSame(player, transport.playerPayloads.get(0).player);
        NetEnvelope envelope = NetEnvelope.decode(transport.playerPayloads.get(0).payload);
        Assert.assertEquals(NetEnvelope.Kind.META, envelope.getKind());
        Assert.assertEquals(NetSide.CLIENT, envelope.getTargetSide());
    }

    @Test
    public void shouldAllowNewHandshakeAfterDisconnectCleanup() {
        NetworkManager firstManager = new NetworkManager(true);
        NetworkManager secondManager = new NetworkManager(true);

        ForgeConnectionLifecycle.getInstance().onClientConnectionEstablished(firstManager);
        ForgeConnectionLifecycle.getInstance().onClientDisconnected();
        ForgeConnectionLifecycle.getInstance().onClientConnectionEstablished(secondManager);

        Assert.assertEquals(2, transport.clientToServerPayloads.size());
    }

    @Test
    public void shouldResolveServerPlayerFromPlayHandler() throws Exception {
        NetHandlerPlayServer handler = allocate(NetHandlerPlayServer.class);
        EntityPlayerMP player = allocate(EntityPlayerMP.class);
        handler.playerEntity = player;

        Assert.assertSame(player, ForgeConnectionLifecycle.resolveServerPlayer(handler));
    }

    private static void resetNetService() throws Exception {
        Method method = NetService.class.getDeclaredMethod("resetForTests");
        method.setAccessible(true);
        method.invoke(NetService.getInstance());
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static final class RecordingTransport implements ITransport {

        final List<byte[]> clientToServerPayloads = new ArrayList<byte[]>();
        final List<PlayerPayload> playerPayloads = new ArrayList<PlayerPayload>();

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
            clientToServerPayloads.add(payload);
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
            return null;
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
}
