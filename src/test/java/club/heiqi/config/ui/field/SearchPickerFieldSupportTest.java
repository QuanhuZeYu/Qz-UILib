package club.heiqi.config.ui.field;

import org.junit.Test;

import club.heiqi.config.schema.SearchPickerSpec;
import club.heiqi.config.schema.ValueSpec;
import club.heiqi.config.ui.editor.Registry;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** SearchPickerFieldSupport 的注册表边界测试。 */
public class SearchPickerFieldSupportTest {
    /** 搜索 widget 引用缺失 provider 时必须在装配点 fail-fast。 */
    @Test
    public void missingProviderFailsFast() {
        Registry registry = new Registry();
        registry.freeze();
        ValueSpec spec = ValueSpec.string().withWidget(new SearchPickerSpec("test:missing", 8));

        try {
            SearchPickerFieldSupport.createIfPresent(null, spec, "", registry, value -> { });
            fail("expected missing provider failure");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("test:missing"));
        }
    }
}
