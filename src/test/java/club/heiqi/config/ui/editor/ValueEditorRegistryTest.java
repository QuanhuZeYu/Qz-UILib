package club.heiqi.config.ui.editor;

import org.junit.Test;

import static org.junit.Assert.*;

/** ValueEditorRegistry 生命周期与冲突测试。 */
public class ValueEditorRegistryTest {
    /** 缺失查询为空，冻结后仍可查询但不可注册。 */
    @Test
    public void freezePreventsFurtherRegistration() {
        Registry registry = new Registry();
        ValueEditorProvider provider = provider("qzuilib:item");
        assertNull(registry.find("missing:id"));
        registry.register(provider);
        registry.freeze();
        registry.freeze();

        assertTrue(registry.isFrozen());
        assertSame(provider, registry.find("qzuilib:item"));
        expectFailure(new Runnable() { public void run() { registry.register(provider("qzuilib:other")); } });
    }

    /** 重复与空 id 在注册点 fail-fast。 */
    @Test
    public void duplicateAndEmptyIdsFailFast() {
        final Registry registry = new Registry();
        registry.register(provider("qzuilib:item"));
        expectFailure(new Runnable() { public void run() { registry.register(provider("qzuilib:item")); } });
        expectFailure(new Runnable() { public void run() { registry.register(provider("")); } });
    }

    private static ValueEditorProvider provider(final String id) {
        return new ValueEditorProvider() {
            public String id() { return id; }
            public Codec codec() {
                return new Codec() {
                    public SearchPickerData.Selection decode(Object value) { return (SearchPickerData.Selection) value; }
                    public Object encode(SearchPickerData.Selection selection) { return selection; }
                };
            }
            public VisualAdapter visualAdapter() {
                return new VisualAdapter() {
                    public String candidateLabel(SearchPickerData.Candidate candidate) { return candidate.label(); }
                    public String variantLabel(SearchPickerData.Variant variant) { return variant.label(); }
                };
            }
        };
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            fail("expected registration failure");
        } catch (IllegalArgumentException expected) {
            // expected
        } catch (IllegalStateException expected) {
            // expected
        }
    }
}
