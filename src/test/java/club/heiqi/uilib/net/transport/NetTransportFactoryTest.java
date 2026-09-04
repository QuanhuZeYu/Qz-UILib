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
    /**
     * 未知适配器名走回落而不是抛异常（#71 同族审计 A2）。
     *
     * <p>create 由 CommonProxy.preInit 直调，两侧启动都挂在它上面；抛裸 IllegalArgumentException
     * 等于"配置里一个字母打错就带走整个进程"。回落仍要打 WARN，并且不得改写用户看到的原值，
     * 否则坏值连线索都不剩。</p>
     */
    @Test
    public void unknownTransportNameFallsBackToDefault() {
        Assert.assertTrue("不认识的名字必须回落默认适配器而不是抛异常",
                NetTransportFactory.create("qz") instanceof VanillaMixinTransport);
        Assert.assertTrue("纯空白也按未配置处理",
                NetTransportFactory.create("   ") instanceof VanillaMixinTransport);
        Assert.assertEquals("resolveName 不得静默修正原值：坏值要能在日志里被认出来",
                "qz", NetTransportFactory.resolveName("qz"));
    }

    /**
     * 覆盖优先级不变：system property 仍然最高，但它写了坏名字同样只回落。
     */
    @Test
    public void unknownOverrideFallsBackWithoutBreakingConfiguration() {
        System.setProperty(NetTransportFactory.SYSTEM_PROPERTY, "nope");
        Assert.assertEquals("nope", NetTransportFactory.resolveName("forge"));
        Assert.assertTrue(NetTransportFactory.create("forge") instanceof VanillaMixinTransport);
    }
}
