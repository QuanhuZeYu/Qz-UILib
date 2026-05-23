package club.heiqi.uilib.net.transport.vanilla;

import java.lang.reflect.Field;
import java.util.Queue;

import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.client.C17PacketCustomPayload;

/**
 * `VanillaPacketBuilders` 客户端发送路径测试。
 */
public class VanillaPacketBuildersTest {

    @After
    public void tearDown() {
        VanillaPacketBuilders.clearClientNetworkManager();
    }

    @Test
    public void shouldSendThroughRememberedEarlyNetworkManager() throws Exception {
        NetworkManager networkManager = new NetworkManager(true);
        byte[] payload = new byte[] { 1, 2, 3 };
        VanillaPacketBuilders.rememberClientNetworkManager(networkManager);

        VanillaPacketBuilders.sendToServer("qz:0", payload);

        Queue<?> queue = outboundQueueOf(networkManager);
        Assert.assertEquals(1, queue.size());
        Object packet = packetOf(queue.peek());
        Assert.assertTrue(packet instanceof C17PacketCustomPayload);
        C17PacketCustomPayload customPayload = (C17PacketCustomPayload) packet;
        Assert.assertEquals("qz:0", customPayload.func_149559_c());
        Assert.assertArrayEquals(payload, customPayload.func_149558_e());
    }

    private static Queue<?> outboundQueueOf(NetworkManager networkManager) throws ReflectiveOperationException {
        Field field = NetworkManager.class.getDeclaredField("outboundPacketsQueue");
        field.setAccessible(true);
        return (Queue<?>) field.get(networkManager);
    }

    private static Object packetOf(Object tuple) throws ReflectiveOperationException {
        Field field = tuple.getClass().getDeclaredField("field_150774_a");
        field.setAccessible(true);
        return field.get(tuple);
    }
}
