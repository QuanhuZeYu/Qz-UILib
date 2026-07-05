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
                    club.heiqi.uilib.ui.scene.runtime.SceneRuntime rt,
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

    // ============ P1：path 覆盖分发基建 ============

    /**
     * P1：pathOverrides 命中时优先于 type 注册返回。
     *
     * <p>构造一个 STRING 类型 spec（path "foo.bar"），同时注册 STRING→rendererA
     * 与 path "foo.bar"→rendererB，resolve 应返回 rendererB。</p>
     */
    @Test
    public void pathOverrideTakesPrecedenceOverType() {
        FieldRendererRegistry registry = FieldRendererRegistry.defaultRegistry();
        ConfigSchema schema = UiSchemaFactory.serverSchema();
        FieldSpec spec = schema.field("server.host"); // STRING, path "server.host"

        FieldRenderer override = new FieldRenderer() {
            @Override
            public club.heiqi.uilib.ui.scene.node.SceneNode render(
                    club.heiqi.uilib.ui.scene.runtime.SceneRuntime rt,
                    club.heiqi.config.schema.FieldSpec s,
                    DraftSignalAdapter adapter) {
                return null;
            }
        };
        registry.registerPath("server.host", override);

        Assert.assertSame("path 覆盖应优先于 type 注册",
                override, registry.resolve(spec));
    }

    /**
     * P1：pathOverrides 未命中时回落到 type 注册。
     *
     * <p>对未注册 path 的 STRING spec，resolve 应返回 type 注册的 StringFieldRenderer。</p>
     */
    @Test
    public void unregisteredPathFallsBackToType() {
        FieldRendererRegistry registry = FieldRendererRegistry.defaultRegistry();
        ConfigSchema schema = UiSchemaFactory.serverSchema();
        FieldSpec spec = schema.field("server.host"); // STRING, path "server.host"

        // 不为 "server.host" 注册 path 覆盖，应回落 type 注册
        FieldRenderer r = registry.resolve(spec);
        Assert.assertTrue("未注册 path 应回落到 type 注册的 StringFieldRenderer",
                r instanceof StringFieldRenderer);
    }

    /**
     * P1：defaultRegistry() 后所有 5 种 type 仍能正常 resolve（回归）。
     *
     * <p>serverSchema 提供 STRING / NUMBER / BOOLEAN / CHOICE 4 种；
     * SIMPLE_LIST 字段在该 schema 中没有，故直接构造一个 SIMPLE_LIST FieldSpec 验证。</p>
     */
    @Test
    public void defaultRegistryStillWorks() {
        FieldRendererRegistry registry = FieldRendererRegistry.defaultRegistry();
        ConfigSchema schema = UiSchemaFactory.serverSchema();
        // server.host: STRING / server.port: NUMBER / server.debug: BOOLEAN / server.mode: CHOICE
        Assert.assertTrue("STRING", registry.resolve(schema.field("server.host")) instanceof StringFieldRenderer);
        Assert.assertTrue("NUMBER", registry.resolve(schema.field("server.port")) instanceof NumberFieldRenderer);
        Assert.assertTrue("BOOLEAN", registry.resolve(schema.field("server.debug")) instanceof BooleanFieldRenderer);
        Assert.assertTrue("CHOICE", registry.resolve(schema.field("server.mode")) instanceof ChoiceFieldRenderer);
        // SIMPLE_LIST：直接构造 FieldSpec 验证（serverSchema 无 SIMPLE_LIST 字段）
        FieldSpec simpleListSpec = new FieldSpec(
                "server.list", FieldType.SIMPLE_LIST, new java.util.ArrayList<String>(),
                club.heiqi.config.schema.FieldConstraints.none(), "List", null, null);
        FieldRenderer r = registry.resolve(simpleListSpec);
        Assert.assertTrue("SIMPLE_LIST", r instanceof club.heiqi.config.ui.field.SimpleListFieldRenderer);
    }

    /**
     * P1：registerPath 对 path / renderer 做 null 校验，抛 IllegalArgumentException。
     */
    @Test
    public void registerPathNullChecks() {
        FieldRendererRegistry registry = FieldRendererRegistry.defaultRegistry();
        FieldRenderer renderer = new FieldRenderer() {
            @Override
            public club.heiqi.uilib.ui.scene.node.SceneNode render(
                    club.heiqi.uilib.ui.scene.runtime.SceneRuntime rt,
                    club.heiqi.config.schema.FieldSpec s,
                    DraftSignalAdapter adapter) {
                return null;
            }
        };
        try {
            registry.registerPath(null, renderer);
            Assert.fail("registerPath(null, renderer) 应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        try {
            registry.registerPath("server.host", null);
            Assert.fail("registerPath(path, null) 应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }
}
