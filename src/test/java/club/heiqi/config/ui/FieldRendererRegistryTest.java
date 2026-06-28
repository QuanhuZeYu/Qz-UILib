package club.heiqi.config.ui;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.schema.FieldType;
import club.heiqi.config.ui.field.BooleanFieldRenderer;
import club.heiqi.config.ui.field.ChoiceFieldRenderer;
import club.heiqi.config.ui.field.FieldRenderer;
import club.heiqi.config.ui.field.FieldRendererRegistry;
import club.heiqi.config.ui.field.NumberFieldRenderer;
import club.heiqi.config.ui.field.StringFieldRenderer;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;

/**
 * {@link FieldRendererRegistry} 单元测试。
 */
public class FieldRendererRegistryTest {

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    /** defaultRegistry 注册 4 种类型 */
    @Test
    public void defaultRegistryRegistersFourTypes() {
        FieldRendererRegistry registry = FieldRendererRegistry.defaultRegistry();
        ConfigSchema schema = UiSchemaFactory.serverSchema();
        for (FieldSpec field : schema.allFields()) {
            Assert.assertNotNull("类型 " + field.type() + " 应有 renderer",
                    registry.resolve(field));
        }
    }

    @Test
    public void resolveStringReturnsStringFieldRenderer() {
        FieldRendererRegistry registry = FieldRendererRegistry.defaultRegistry();
        ConfigSchema schema = UiSchemaFactory.serverSchema();
        FieldRenderer r = registry.resolve(schema.field("server.host"));
        Assert.assertTrue("STRING → StringFieldRenderer", r instanceof StringFieldRenderer);
    }

    @Test
    public void resolveNumberReturnsNumberFieldRenderer() {
        FieldRendererRegistry registry = FieldRendererRegistry.defaultRegistry();
        ConfigSchema schema = UiSchemaFactory.serverSchema();
        FieldRenderer r = registry.resolve(schema.field("server.port"));
        Assert.assertTrue("NUMBER → NumberFieldRenderer", r instanceof NumberFieldRenderer);
    }

    @Test
    public void resolveBooleanReturnsBooleanFieldRenderer() {
        FieldRendererRegistry registry = FieldRendererRegistry.defaultRegistry();
        ConfigSchema schema = UiSchemaFactory.serverSchema();
        FieldRenderer r = registry.resolve(schema.field("server.debug"));
        Assert.assertTrue("BOOLEAN → BooleanFieldRenderer", r instanceof BooleanFieldRenderer);
    }

    @Test
    public void resolveChoiceReturnsChoiceFieldRenderer() {
        FieldRendererRegistry registry = FieldRendererRegistry.defaultRegistry();
        ConfigSchema schema = UiSchemaFactory.serverSchema();
        FieldRenderer r = registry.resolve(schema.field("server.mode"));
        Assert.assertTrue("CHOICE → ChoiceFieldRenderer", r instanceof ChoiceFieldRenderer);
    }

    /** register 自定义 renderer 替换默认 */
    @Test
    public void registerReplacesDefault() {
        FieldRendererRegistry registry = FieldRendererRegistry.defaultRegistry();
        FieldRenderer custom = new FieldRenderer() {
            @Override
            public club.heiqi.uilib.ui.scene.node.SceneNode render(
                    club.heiqi.uilib.ui.scene.component.SceneRuntime rt,
                    club.heiqi.config.schema.FieldSpec spec,
                    DraftSignalAdapter adapter) {
                return null;
            }
        };
        registry.register(FieldType.STRING, custom);
        ConfigSchema schema = UiSchemaFactory.serverSchema();
        Assert.assertSame("自定义 renderer 替换默认", custom, registry.resolve(schema.field("server.host")));
    }

    /** resolve 未知类型返回 null */
    @Test
    public void resolveUnregisteredTypeReturnsNull() {
        FieldRendererRegistry registry = new FieldRendererRegistry();
        ConfigSchema schema = UiSchemaFactory.serverSchema();
        Assert.assertNull("未注册类型返回 null", registry.resolve(schema.field("server.host")));
    }

    /** resolve null spec 返回 null */
    @Test
    public void resolveNullSpecReturnsNull() {
        FieldRendererRegistry registry = FieldRendererRegistry.defaultRegistry();
        Assert.assertNull("null spec 返回 null", registry.resolve(null));
    }
}
