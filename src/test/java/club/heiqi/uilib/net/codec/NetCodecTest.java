package club.heiqi.uilib.net.codec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

/**
 * `NetCodec` 的反射编解码测试。
 */
public class NetCodecTest {

    @Test
    public void shouldRoundTripPojoWithCollectionsAndNestedObject() {
        ProbeMessage message = ProbeMessage.sample();

        byte[] encoded = NetCodec.of(ProbeMessage.class).encode(message);
        ProbeMessage decoded = NetCodec.of(ProbeMessage.class).decode(encoded);

        Assert.assertEquals(message.id, decoded.id);
        Assert.assertEquals(message.title, decoded.title);
        Assert.assertEquals(message.mode, decoded.mode);
        Assert.assertEquals(message.tags, decoded.tags);
        Assert.assertEquals(message.values, decoded.values);
        Assert.assertNotNull(decoded.child);
        Assert.assertEquals(42, decoded.child.value);
        Assert.assertNull(decoded.localOnly);
    }

    @Test
    public void shouldBuildStableFieldLayoutByProtocolName() {
        FieldLayout layout = FieldLayout.forClass(ProbeMessage.class);

        Assert.assertEquals("child", layout.getFields().get(0).getProtocolName());
        Assert.assertEquals("id", layout.getFields().get(1).getProtocolName());
        Assert.assertEquals("mode", layout.getFields().get(2).getProtocolName());
        Assert.assertEquals("renamedTitle", layout.getFields().get(3).getProtocolName());
    }

    public static final class ProbeMessage {

        public int id;
        @NetField(name = "renamedTitle")
        public String title;
        public Mode mode;
        public List<String> tags = new ArrayList<String>();
        public Map<String, Integer> values = new LinkedHashMap<String, Integer>();
        public ProbeChild child;
        @NetTransient
        public String localOnly;

        static ProbeMessage sample() {
            ProbeMessage message = new ProbeMessage();
            message.id = 7;
            message.title = "hello";
            message.mode = Mode.ACTIVE;
            message.tags.add("alpha");
            message.tags.add("beta");
            message.values.put("answer", Integer.valueOf(42));
            message.child = new ProbeChild();
            message.child.value = 42;
            message.localOnly = "skip";
            return message;
        }
    }

    public static final class ProbeChild {

        public int value;
    }

    public enum Mode {
        ACTIVE,
        IDLE
    }
}
