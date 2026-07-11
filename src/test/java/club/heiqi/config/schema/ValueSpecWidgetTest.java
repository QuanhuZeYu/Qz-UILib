package club.heiqi.config.schema;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigFormat;
import org.junit.Test;

import static org.junit.Assert.*;

/** ValueSpec widget 元数据兼容与不可变性测试。 */
public class ValueSpecWidgetTest {
    /** widget 复制不改变原 spec，并在后续复制式操作中保留。 */
    @Test
    public void widgetIsImmutableMetadata() {
        ValueSpec original = Values.string();
        SearchPickerSpec widget = Values.searchPicker("qzuilib:font", 32);
        ValueSpec decorated = original.withWidget(widget);

        assertNull(original.widget());
        assertSame(widget, decorated.widget());
        assertSame(widget, decorated.withDefault("default").widget());
        assertEquals("default", decorated.withDefault("default").defaultValue());
    }

    /** widget 不参与默认值、校验、YAML 读取或 schema path。 */
    @Test
    public void widgetDoesNotChangeValueSemantics() throws Exception {
        ValueSpec plain = Values.string().withDefault("fallback");
        ValueSpec decorated = plain.withWidget(new SearchPickerSpec("qzuilib:item", 64));

        assertEquals(plain.defaultValue(), decorated.defaultValue());
        assertEquals(plain.validate(Integer.valueOf(1), "value").errors(),
                decorated.validate(Integer.valueOf(1), "value").errors());
        assertEquals(plain.acceptsPath(""), decorated.acceptsPath(""));
        club.heiqi.config.ConfigNode node = Config.parse("\"disk\"", ConfigFormat.JSON);
        assertEquals(plain.readNode(node, "value", true), decorated.readNode(node, "value", true));
    }

    /** picker id 与预算在 schema 构建期 fail-fast。 */
    @Test
    public void searchPickerSpecValidatesIdAndBudget() {
        assertEquals("qzuilib:item", new SearchPickerSpec("qzuilib:item", 1).editorId());
        expectIllegalArgument(new Runnable() { public void run() { new SearchPickerSpec("item", 1); } });
        expectIllegalArgument(new Runnable() { public void run() { new SearchPickerSpec("QZ:item", 1); } });
        expectIllegalArgument(new Runnable() { public void run() { new SearchPickerSpec("qz:item", 0); } });
        expectIllegalArgument(new Runnable() { public void run() { new SearchPickerSpec("qz:item", 65); } });
    }

    private static void expectIllegalArgument(Runnable action) {
        try {
            action.run();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // expected
        }
    }
}
