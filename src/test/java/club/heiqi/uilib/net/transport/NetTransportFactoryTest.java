package club.heiqi.uilib.net.transport;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.net.transport.forge.ForgeTransport;
import club.heiqi.uilib.net.transport.vanilla.VanillaMixinTransport;

/**
 * `NetTransportFactory` 选择逻辑测试。
 */
public class NetTransportFactoryTest {

    private String previousProperty;

    @Before
    public void setUp() {
        previousProperty = System.getProperty(NetTransportFactory.SYSTEM_PROPERTY);
        System.clearProperty(NetTransportFactory.SYSTEM_PROPERTY);
    }

    @After
    public void tearDown() {
        if (previousProperty == null) {
            System.clearProperty(NetTransportFactory.SYSTEM_PROPERTY);
        } else {
            System.setProperty(NetTransportFactory.SYSTEM_PROPERTY, previousProperty);
        }
    }

    @Test
    public void shouldUseVanillaByDefault() {
        Assert.assertTrue(NetTransportFactory.create(null) instanceof VanillaMixinTransport);
    }

    @Test
    public void shouldUseConfiguredForgeTransport() {
        Assert.assertTrue(NetTransportFactory.create("forge") instanceof ForgeTransport);
    }

    @Test
    public void shouldLetSystemPropertyOverrideConfig() {
        System.setProperty(NetTransportFactory.SYSTEM_PROPERTY, "forge");

        Assert.assertEquals("forge", NetTransportFactory.resolveName("vanilla"));
        Assert.assertTrue(NetTransportFactory.create("vanilla") instanceof ForgeTransport);
    }
}
