package club.heiqi.uilib.net.codec;

import org.junit.Assert;
import org.junit.Test;

/**
 * `SchemaRegistry` 类型字典测试。
 */
public class SchemaRegistryTest {

    @Test
    public void shouldRegisterPrimitiveLikeMessageTypesWithoutFieldLayout() {
        SchemaRegistry registry = new SchemaRegistry();

        int stringId = registry.register(String.class);
        int bytesId = registry.register(byte[].class);
        int enumId = registry.register(Mode.class);

        Assert.assertEquals(String.class, registry.findById(stringId).getType());
        Assert.assertEquals(byte[].class, registry.findById(bytesId).getType());
        Assert.assertEquals(Mode.class, registry.findById(enumId).getType());
        Assert.assertNotEquals(0, registry.findById(stringId).getSchemaHash());
    }

    private enum Mode {
        ACTIVE
    }
}
