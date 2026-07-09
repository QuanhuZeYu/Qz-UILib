package club.heiqi.config.ui;

import java.io.File;
import java.io.FileWriter;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.ui.field.FieldRendererRegistry;
import club.heiqi.config.ui.field.SimpleListFieldRenderer;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;

/**
 * {@link ConfigUI} 单元测试。
 *
 * <p>P3 重点覆盖新增的 3 参
 * {@link ConfigUI#buildScreen(ConfigManager, club.heiqi.uilib.ui.scene.input.PlatformInputSource,
 * java.util.function.Consumer) buildScreen(manager, input, registryCustomizer)} 重载：
 * customizer 在 defaultRegistry 之后、ConfigScreen 装配前被调用，且能注入 path 覆盖。</p>
 *
 * <p>原 2 参 {@link ConfigUI#buildScreen(ConfigManager,
 * club.heiqi.uilib.ui.scene.input.PlatformInputSource)} 行为不变（向后兼容），
 * 由本类 {@link #twoArgBuildScreenStillWorks} 间接验证。</p>
 */
public class ConfigUITest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private ConfigManager manager;

    @Before
    public void setUp() throws Exception {
        ReactiveScheduler.get().reset();
        File file = tempFolder.newFile("config-ui.yaml");
        write(file, "");
        ConfigSchema schema = UiSchemaFactory.serverSchema();
        manager = ConfigManager.bootstrap(file, schema);
    }

    @After
    public void tearDown() {
        ReactiveScheduler.get().reset();
    }

    /**
     * P3：3 参 buildScreen 在 ConfigScreen 装配前调用 customizer，
     * customizer 内的 registerPath 注入能影响后续 registry（capture flag 验证被调用）。
     */
    @Test
    public void customizerInvokedBeforeScreenAssembly() {
        final boolean[] called = {false};
        ConfigScreen screen = ConfigUI.buildScreen(manager, null, registry -> {
            called[0] = true;
        });
        try {
            Assert.assertTrue("customizer 应在 buildScreen 流程中被调用", called[0]);
        } finally {
            screen.dispose();
        }
    }

    /**
     * P3：customizer 注入的 path 覆盖在装配出的 registry 中生效——
     * 通过 capture customizer 注入的实例验证 registerPath 调用确实落到了 buildScreen 内部创建的 registry。
     *
     * <p>模拟 uilib 接入层（ModernConfigEntry）的真实用法：
     * {@code registry.registerPath("fontSystem.fontSort", new SimpleListFieldRenderer(true))}。</p>
     */
    @Test
    public void customizerRegistryMutationTakesEffect() {
        final SimpleListFieldRenderer fontSortRenderer = new SimpleListFieldRenderer(true);
        final FieldRendererRegistry[] captured = {null};
        ConfigScreen screen = ConfigUI.buildScreen(manager, null, registry -> {
            captured[0] = registry;
            registry.registerPath("fontSystem.fontSort", fontSortRenderer);
        });
        try {
            Assert.assertNotNull("customizer 应收到 registry 实例", captured[0]);
            Assert.assertSame(
                    "customizer 持有的 registry 应是 buildScreen 内部创建并传入 ConfigScreen 的同一实例",
                    fontSortRenderer,
                    captured[0].resolve(fontSortSpec()));
        } finally {
            screen.dispose();
        }
    }

    /**
     * P3：customizer 传入 null 抛 IllegalArgumentException（防止误用）。
     */
    @Test
    public void nullCustomizerThrows() {
        try {
            ConfigUI.buildScreen(manager, null, null);
            Assert.fail("buildScreen(manager, input, null) 应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    /**
     * P3：2 参 buildScreen 仍可正常装配（向后兼容）。
     *
     * <p>2 参重载内部委托 3 参版传空 no-op customizer，行为与改造前一致。</p>
     */
    @Test
    public void twoArgBuildScreenStillWorks() {
        ConfigScreen screen = ConfigUI.buildScreen(manager, null);
        try {
            Assert.assertNotNull("2 参 buildScreen 应返回 ConfigScreen", screen);
        } finally {
            screen.dispose();
        }
    }

    /**
     * 4 参 buildScreen 应把恢复默认策略注入 ConfigScreen。
     */
    @Test
    public void restorePolicyCustomizerTakesEffect() {
        ConfigScreen screen = ConfigUI.buildScreen(manager, null,
                registry -> { },
                policy -> policy.custom("server.host",
                        adapter -> adapter.onFieldEdit("server.host", "policy.host")));
        try {
            screen.__restoreDefaults();
            screen.__getRuntime().flush();
            Assert.assertEquals("恢复默认应走注入的 policy custom action",
                    "policy.host", screen.__getAdapter().draftSignal("server.host").get());
        } finally {
            screen.dispose();
        }
    }

    /**
     * 3 参 buildScreen 默认注入空恢复策略，恢复默认行为保持逐字段 resetFieldToDefault。
     */
    @Test
    public void threeArgBuildScreenKeepsDefaultRestoreBehavior() {
        ConfigScreen screen = ConfigUI.buildScreen(manager, null, registry -> { });
        try {
            screen.__getAdapter().onFieldEdit("server.host", "current.host");
            screen.__getRuntime().flush();
            screen.__saveChanges();
            screen.__getRuntime().flush();

            screen.__restoreDefaults();
            screen.__getRuntime().flush();
            Assert.assertEquals("默认无特殊 policy 时应恢复 schema 默认值",
                    "localhost", screen.__getAdapter().draftSignal("server.host").get());
        } finally {
            screen.dispose();
        }
    }

    /**
     * P3：manager 为 null 抛 IllegalArgumentException（保持原有契约）。
     */
    @Test
    public void nullManagerThrows() {
        try {
            ConfigUI.buildScreen(null, null, registry -> { });
            Assert.fail("buildScreen(null, ...) 应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    /** 4 参 buildScreen 的 restorePolicyCustomizer 不可为 null。 */
    @Test
    public void nullRestorePolicyCustomizerThrows() {
        try {
            ConfigUI.buildScreen(manager, null, registry -> { }, null);
            Assert.fail("restorePolicyCustomizer 为 null 应抛 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    /** 构造 fontSystem.fontSort FieldSpec（SIMPLE_LIST 类型，path "fontSystem.fontSort"）。 */
    private static club.heiqi.config.schema.FieldSpec fontSortSpec() {
        return new club.heiqi.config.schema.FieldSpec(
                "fontSystem.fontSort",
                club.heiqi.config.schema.FieldType.SIMPLE_LIST,
                new java.util.ArrayList<String>(),
                club.heiqi.config.schema.FieldConstraints.none(),
                "Font Sort", null, null);
    }

    private static void write(File file, String content) throws Exception {
        FileWriter w = new FileWriter(file);
        try {
            w.write(content);
        } finally {
            w.close();
        }
    }
}
