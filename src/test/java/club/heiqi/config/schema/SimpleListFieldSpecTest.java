package club.heiqi.config.schema;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * SIMPLE_LIST 字段的 {@link FieldSpec.Builder} / {@link SectionSpec.Builder#simpleList} 测试。
 *
 * <p>覆盖：simpleList 工厂返回 SIMPLE_LIST 类型、defaultValue 类型校验、
 * 未设默认值时 resolveDefault 返回空 list（非 null，防 default throw 崩）、
 * validateType 对非 List 默认值抛 IllegalArgumentException。</p>
 */
public class SimpleListFieldSpecTest {

    /** simpleList 工厂 + 显式默认值：type=SIMPLE_LIST，defaultValue 是含 a/b 的 List。 */
    @Test
    public void simpleListFactorySetsTypeAndDefault() {
        ConfigSchema s = ConfigSchema.builder("t")
                .section("a")
                    .simpleList("k").defaultValue(new ArrayList<String>(Arrays.asList("a", "b")))
                        .label("K").build()
                .endSection()
                .build();
        FieldSpec f = s.field("a.k");
        assertEquals("type=SIMPLE_LIST", FieldType.SIMPLE_LIST, f.type());
        assertNotNull("defaultValue 非 null", f.defaultValue());
        assertTrue("defaultValue 是 List", f.defaultValue() instanceof List);
        @SuppressWarnings("unchecked")
        List<String> vals = (List<String>) f.defaultValue();
        assertEquals("defaultValue 含 a/b", Arrays.asList("a", "b"), vals);
    }

    /** 不设默认值 build：resolveDefault 返回空 list（非 null，防 default throw 崩）。 */
    @Test
    public void resolveDefaultReturnsEmptyListWhenUnset() {
        ConfigSchema s = ConfigSchema.builder("t")
                .section("a")
                    .simpleList("k").label("K").build()
                .endSection()
                .build();
        FieldSpec f = s.field("a.k");
        assertEquals("type=SIMPLE_LIST", FieldType.SIMPLE_LIST, f.type());
        assertNotNull("未设默认值时 resolveDefault 返回非 null 空列表", f.defaultValue());
        assertTrue("默认值是 List", f.defaultValue() instanceof List);
        assertEquals("默认空列表 size=0", 0, ((List<?>) f.defaultValue()).size());
    }

    /**
     * validateType 对非 List 默认值抛 IllegalArgumentException。
     *
     * <p>用 raw type 绕过编译期泛型检查，模拟运行期双校验兜底。</p>
     */
    @Test
    public void validateTypeRejectsNonListDefault() {
        ConfigSchema.Builder sb = ConfigSchema.builder("t");
        SectionSpec.Builder sec = sb.section("a");
        @SuppressWarnings({"rawtypes", "unchecked"})
        FieldSpec.Builder raw = sec.simpleList("k");
        raw.defaultValue("not a list");
        try {
            raw.label("K").build();
            fail("默认值非 List 应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue("异常文案含字段路径与类型提示: " + expected.getMessage(),
                    expected.getMessage().contains("SIMPLE_LIST")
                            && expected.getMessage().contains("a.k")
                            && expected.getMessage().contains("List"));
        }
    }
}
