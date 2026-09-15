package club.heiqi.uilib.config.modern;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.schema.FieldType;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;

/**
 * 聊天框形态配置项（{@code general.chatFrame}）的回归锁：schema 声明、值解析、
 * 「按钮写盘 + 回灌」通道与失败语义。
 *
 * <p>本类会写运行态开关（进程级静态量），每个用例后必须复位（照
 * {@code BackdropQualityConfigTest} 的双向清理口径）。</p>
 */
public class ChatFrameConfigTest {

    @Rule
    public final TemporaryFolder tempFolder = new TemporaryFolder();

    @After
    public void restoreRuntime() {
        ChatMarkdownSettings.setEnabled(true);
    }

    /** ① schema 声明：CHOICE + 两档 + 默认 custom（= 改动后的聊天框）。 */
    @Test
    public void schemaDeclaresChatFrameChoiceWithCustomDefault() {
        ConfigSchema schema = QzUiLibModernSchema.create();
        FieldSpec field = schema.field(ChatFrameConfig.CONFIG_PATH);
        Assert.assertNotNull("schema 必须声明 " + ChatFrameConfig.CONFIG_PATH, field);
        Assert.assertEquals("形态是枚举型字段", FieldType.CHOICE, field.type());
        Assert.assertEquals("默认必须是 custom（改动后的聊天框）",
                ChatFrameConfig.MODE_CUSTOM, field.defaultValue());
        Assert.assertEquals("选项恰为两档（顺序即 UI 呈现顺序）",
                Arrays.asList(ChatFrameConfig.MODE_CUSTOM, ChatFrameConfig.MODE_VANILLA),
                field.constraints().choices());
    }

    /** ② 不得新增 Config 静态镜像字段（运行态权威是接管开关，镜像会被假开关守卫判红）。 */
    @Test
    public void frameModeHasNoStaticMirrorOnConfig() {
        try {
            club.heiqi.uilib.Config.class.getField("chatFrame");
            Assert.fail("Config 不得新增 chatFrame 静态镜像：运行态权威是 ChatMarkdownSettings 接管开关");
        } catch (NoSuchFieldException expected) {
            // 预期：字段不存在
        }
    }

    /** ③ 值解析：大小写/空白容错；null / 空白 / 未知值回落 custom。 */
    @Test
    public void resolveFallsBackToCustomForMissingOrUnknownValue() {
        Assert.assertEquals(ChatFrameConfig.MODE_VANILLA, ChatFrameConfig.resolve("vanilla"));
        Assert.assertEquals(ChatFrameConfig.MODE_VANILLA, ChatFrameConfig.resolve("  Vanilla "));
        Assert.assertEquals(ChatFrameConfig.MODE_CUSTOM, ChatFrameConfig.resolve("custom"));
        Assert.assertEquals(ChatFrameConfig.MODE_CUSTOM, ChatFrameConfig.resolve(null));
        Assert.assertEquals(ChatFrameConfig.MODE_CUSTOM, ChatFrameConfig.resolve("   "));
        Assert.assertEquals(ChatFrameConfig.MODE_CUSTOM, ChatFrameConfig.resolve("bogus"));
    }

    /**
     * ④ 写盘与回灌分离：persist 只落盘（不改运行态），applyVanilla 才切原版；
     * applyConfigured(custom) 切回自定义。
     *
     * <p>「写盘不动运行态」是按钮时序的前提：运行态要等自定义输入屏按 CLOSING 动画收回去、
     * 真正关屏之后才切，否则渲染帧安装器会在动画期间就注销聊天 HUD。</p>
     */
    @Test
    public void persistWritesYamlWithoutTouchingRuntime() throws Exception {
        File file = tempFolder.newFile("qzuilib-modern.yaml");
        Assert.assertTrue("首次写盘必须成功", ChatFrameConfig.persist(file, ChatFrameConfig.MODE_VANILLA));
        Assert.assertEquals("权威源必须读到 vanilla", ChatFrameConfig.MODE_VANILLA,
                ConfigManager.bootstrap(file, QzUiLibModernSchema.create())
                        .authority().getString(ChatFrameConfig.CONFIG_PATH));
        Assert.assertTrue("写盘自身不得改运行态（按钮侧要等输入屏关屏动画结束）",
                ChatMarkdownSettings.isEnabled());

        ChatFrameConfig.applyVanilla();
        Assert.assertFalse("回灌后运行态 = 原版聊天框", ChatMarkdownSettings.isEnabled());

        Assert.assertTrue("回切写盘必须成功", ChatFrameConfig.persist(file, ChatFrameConfig.MODE_CUSTOM));
        Assert.assertEquals(ChatFrameConfig.MODE_CUSTOM,
                ConfigManager.bootstrap(file, QzUiLibModernSchema.create())
                        .authority().getString(ChatFrameConfig.CONFIG_PATH));
        ChatFrameConfig.applyConfigured(ChatFrameConfig.MODE_CUSTOM);
        Assert.assertTrue("回灌后运行态 = 自定义聊天框", ChatMarkdownSettings.isEnabled());
    }

    /** ⑤ 按钮只改本键：文件里其它配置值必须原样保留。 */
    @Test
    public void persistKeepsOtherConfiguredValues() throws Exception {
        File file = tempFolder.newFile("existing.yaml");
        writeText(file, "general:\n  chatFrame: custom\n  pickerDensity: roomy\n");
        Assert.assertTrue(ChatFrameConfig.persist(file, ChatFrameConfig.MODE_VANILLA));
        ConfigManager persisted = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
        Assert.assertEquals(ChatFrameConfig.MODE_VANILLA,
                persisted.authority().getString(ChatFrameConfig.CONFIG_PATH));
        Assert.assertEquals("其它配置值必须原样保留", "roomy",
                persisted.authority().getString("general.pickerDensity"));
    }

    /** ⑥ 配置读不出来时不写盘、不改运行态（看到的形态 = 配置里的形态）。 */
    @Test
    public void persistLeavesRuntimeUntouchedWhenConfigUnreadable() throws Exception {
        File unreadable = tempFolder.newFolder("qzuilib-modern.yaml");
        Assert.assertFalse("加载失败必须返回 false，不得抛",
                ChatFrameConfig.persist(unreadable, ChatFrameConfig.MODE_VANILLA));
        Assert.assertTrue("失败时运行态必须保持自定义聊天框", ChatMarkdownSettings.isEnabled());
    }

    /** ⑦ 两档取值能手改 YAML 裸词往返（不得是 YAML 1.1 布尔字面量，如 off/on/yes/no）。 */
    @Test
    public void everyOptionSurvivesUnquotedHandWrittenYaml() throws Exception {
        for (String option : Arrays.asList(ChatFrameConfig.MODE_CUSTOM, ChatFrameConfig.MODE_VANILLA)) {
            File file = tempFolder.newFile("frame-" + option + ".yaml");
            writeText(file, String.join("\n", "general:", "  chatFrame: " + option, ""));
            ConfigManager manager = ConfigManager.bootstrap(file, QzUiLibModernSchema.create());
            Assert.assertEquals("形态取值 " + option + " 必须能手改 YAML 裸词读写",
                    option, manager.authority().getString(ChatFrameConfig.CONFIG_PATH));
        }
    }

    private static void writeText(File file, String text) throws Exception {
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
    }
}
