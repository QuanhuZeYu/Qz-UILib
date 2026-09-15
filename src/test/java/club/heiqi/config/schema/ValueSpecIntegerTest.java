package club.heiqi.config.schema;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigException;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.ConfigNode;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 递归值层的 INTEGER 语义（{@link Values#integer()} / {@link ValueKind#INTEGER}）。
 *
 * <p>结构化列表的元素成员走的是 {@link ValueSpec#readNode} / {@link ValueSpec#normalize} 而不是
 * 标量字段的 Authority 通道，故整数语义在两条通道上各要有一份判据：读旧浮点形态得 Long、
 * 归一化把整数值收敛为 Long（落盘才是整数形态）、小数在严格读与校验两处都被拒绝。</p>
 */
public class ValueSpecIntegerTest {

    private static ValueSpec itemSpec() {
        return Values.object(Values.member("bytes", Values.integer()));
    }

    private static Object bytesOf(Object value) {
        return ((Map<?, ?>) value).get("bytes");
    }

    /** 旧浮点形态的成员值在严格读下也读成整数值。 */
    @Test
    public void integerMemberReadsLegacyFloatFormAsLong() throws Exception {
        ConfigNode node = Config.parse("bytes: 1.6777216E7\n", ConfigFormat.YAML);
        assertEquals("科学计数法旧值必须读成整数值", Long.valueOf(16777216L),
                bytesOf(itemSpec().readNode(node, "item", true)));

        ConfigNode plain = Config.parse("bytes: 16777216\n", ConfigFormat.YAML);
        assertEquals("纯整数原样读回", Long.valueOf(16777216L),
                bytesOf(itemSpec().readNode(plain, "item", true)));
    }

    /** 小数在严格读下 fail-closed。 */
    @Test
    public void fractionalMemberIsRejectedInStrictRead() throws Exception {
        ConfigNode node = Config.parse("bytes: 1.5\n", ConfigFormat.YAML);
        try {
            itemSpec().readNode(node, "item", true);
            fail("1.5 不是整数值，严格读必须抛 VALIDATION");
        } catch (ConfigException e) {
            assertEquals(ConfigException.Category.VALIDATION, e.category());
        }
    }

    /** 归一化把整数值收敛为 Long（落盘整数形态），小数原样保留给校验拒绝。 */
    @Test
    public void normalizeCoercesIntegralValueAndKeepsFractional() {
        ValueSpec spec = itemSpec();

        Map<String, Object> integral = new LinkedHashMap<String, Object>();
        integral.put("bytes", Double.valueOf(7.0));
        Object normalized = spec.normalize(integral);
        assertEquals("整数值归一化为 Long", Long.valueOf(7L), bytesOf(normalized));
        assertFalse("整数值不应有校验错误", spec.validate(normalized, "item").hasErrors());

        Map<String, Object> fractional = new LinkedHashMap<String, Object>();
        fractional.put("bytes", Double.valueOf(1.5));
        Object kept = spec.normalize(fractional);
        assertEquals("数值原样保留，不截断", Double.valueOf(1.5), bytesOf(kept));
        assertTrue("小数必须被校验拒绝", spec.validate(kept, "item").hasErrors());
    }
}
