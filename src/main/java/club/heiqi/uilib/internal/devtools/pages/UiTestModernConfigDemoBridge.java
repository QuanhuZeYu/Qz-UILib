package club.heiqi.uilib.internal.devtools.pages;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.config.ModernConfigTemplateScreen;
import club.heiqi.uilib.config.ModernConfigTemplateScreen.FieldSpec;
import club.heiqi.uilib.config.ModernConfigTemplateScreen.SaveHandler;
import club.heiqi.uilib.config.ModernConfigTemplateScreen.Spec;
import club.heiqi.uilib.ui.screen.UiScreenManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

/**
 * 现代配置模板 demo 的实际屏幕创建桥接。
 *
 * <p>本类允许直接引用 {@code club.heiqi.config} 与 {@link ModernConfigTemplateScreen}，
 * 但只能在 {@link UiTestModernConfigDemoLauncher#isModernConfigModuleAvailable()} 检测通过后加载。</p>
 */
final class UiTestModernConfigDemoBridge {

    private UiTestModernConfigDemoBridge() {}

    /**
     * 打开现代配置模板 demo 屏幕。
     *
     * <p>当前 test 页（{@code Minecraft.currentScreen}）作为 demo 屏幕的 parentScreen，
     * ESC / 返回按钮会经 {@code ModernConfigTemplateScreen.requestClose} 切回 test 页。</p>
     */
    static void openDemo() {
        final Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null) {
            return;
        }
        final GuiScreen parentScreen = minecraft.currentScreen;
        final ModernConfigTemplateScreen demoScreen = createDemoScreen(parentScreen);
        UiScreenManager.getInstance().enqueue(new Runnable() {
            @Override
            public void run() {
                minecraft.displayGuiScreen(demoScreen);
            }
        });
    }

    /**
     * 创建现代配置模板 demo 屏幕。
     *
     * @param parentScreen 父界面（test 页）
     * @return demo 屏幕
     */
    private static ModernConfigTemplateScreen createDemoScreen(GuiScreen parentScreen) {
        MutableConfig config = createDemoConfig();
        Spec spec = buildDemoSpec(config);
        return new ModernConfigTemplateScreen(parentScreen, spec);
    }

    /**
     * 创建覆盖 12 个模板入口的 demo 内存配置。
     *
     * @return 已填充且标记为干净状态的 demo 配置
     */
    static MutableConfig createDemoConfig() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON);
        populateDemoConfig(config);
        return config;
    }

    /**
     * 构造覆盖 12 个模板入口的 demo 规格。
     *
     * <p>包级可见，供 JVM 测试验证 FieldSpec 的 path、label、hint 等声明，
     * 不触发 {@code ModernConfigTemplateScreen} 构造与 Minecraft 静态初始化。</p>
     *
     * @param config 已填充 demo 数据的可变配置
     * @return demo 规格
     */
    static Spec buildDemoSpec(MutableConfig config) {
        Spec spec = new Spec("qzuilib-test-demo", "现代配置模板 demo", config)
                .setSubtitle("覆盖 12 个模板入口的完整示例")
                .setDescription("由 /qzuilib test MODCFG 组打开，演示 ModernConfigTemplateScreen 全部能力。")
                .setConfigPath("demo（内存配置，不落盘）")
                .setSaveHandler(new DemoSaveHandler());
        addStringField(spec);
        addNumberField(spec);
        addBooleanField(spec);
        addChoiceField(spec);
        addLongTextField(spec);
        addSimpleListField(spec);
        addTableField(spec);
        addObjectField(spec);
        addKeyValueMapField(spec);
        addPresetSelectorField(spec);
        addRawEditorField(spec);
        addEnhancedPickerFields(spec);
        return spec;
    }

    /**
     * 填充覆盖 12 个模板入口的 demo 配置值。
     *
     * @param config 空的可变配置
     */
    private static void populateDemoConfig(MutableConfig config) {
        // STRING
        config.set("player.name", "Steve");
        // NUMBER
        config.set("server.maxPlayers", Integer.valueOf(20));
        // BOOLEAN
        config.set("feature.enableHud", Boolean.TRUE);
        // CHOICE
        config.set("render.mode", "fast");
        // LONG_TEXT（含换行触发长文本）
        config.set("motd", "欢迎进入 demo\n本公告演示 LONG_TEXT");
        // SIMPLE_LIST
        config.set("ports", Arrays.asList(
                Integer.valueOf(25565), Integer.valueOf(25566), Integer.valueOf(25567)));
        // TABLE（同构对象列表）
        config.set("servers", Arrays.asList(
                makeRow("host", "a.local", "port", Integer.valueOf(25565)),
                makeRow("host", "b.local", "port", Integer.valueOf(25566))));
        // OBJECT
        config.set("database.credentials.username", "admin");
        config.set("database.credentials.password", "secret");
        // KEY_VALUE_MAP
        config.set("labels.alpha", "A");
        config.set("labels.beta", "B");
        // PRESET_SELECTOR（含 _presets 子键）
        config.set("profile.mode", "fast");
        config.set("profile.threads", Integer.valueOf(4));
        config.set("profile._presets.fast", makeRow("mode", "fast", "threads", Integer.valueOf(4)));
        config.set("profile._presets.safe", makeRow("mode", "safe", "threads", Integer.valueOf(1)));
        // RAW_EDITOR（JSON 字符串）
        config.set("payload", "{\"host\":\"localhost\",\"port\":8080}");
        // ENHANCED_PICKER（color / resource / sound）
        config.set("theme.primary", "#FF8800");
        config.set("texture.block", "minecraft:block/stone");
        config.set("audio.click", "minecraft:block.stone.click");
        config.markClean();
    }

    /**
     * 构造两键值键值对。
     *
     * @param k1 第一个键
     * @param v1 第一个值
     * @param k2 第二个键
     * @param v2 第二个值
     * @return 有序映射
     */
    private static Map<String, Object> makeRow(String k1, Object v1, String k2, Object v2) {
        LinkedHashMap<String, Object> map = new LinkedHashMap<String, Object>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    /**
     * 追加 STRING 文本字段。
     *
     * @param spec demo 规格
     */
    private static void addStringField(Spec spec) {
        spec.addField(new FieldSpec("player.name")
                .setLabel("玩家名称")
                .setDescription("显示在 HUD 上的玩家名。")
                .setPlaceholder("输入名称")
                .setMaxLength(32));
    }

    /**
     * 追加 NUMBER 数值字段。
     *
     * @param spec demo 规格
     */
    private static void addNumberField(Spec spec) {
        spec.addField(new FieldSpec("server.maxPlayers")
                .setLabel("最大玩家数")
                .setRange(1, 100)
                .setStep(1)
                .setDefaultValue(20));
    }

    /**
     * 追加 BOOLEAN 开关字段。
     *
     * @param spec demo 规格
     */
    private static void addBooleanField(Spec spec) {
        spec.addField(new FieldSpec("feature.enableHud")
                .setLabel("启用 HUD")
                .setDefaultValue(Boolean.TRUE));
    }

    /**
     * 追加 CHOICE 离散选项字段。
     *
     * @param spec demo 规格
     */
    private static void addChoiceField(Spec spec) {
        spec.addField(new FieldSpec("render.mode")
                .setLabel("渲染模式")
                .setValidValues("fast", "balanced", "safe")
                .setDefaultValue("balanced"));
    }

    /**
     * 追加 LONG_TEXT 长文本字段。
     *
     * @param spec demo 规格
     */
    private static void addLongTextField(Spec spec) {
        spec.addField(new FieldSpec("motd")
                .setLabel("服务器公告")
                .setTemplateHint("textarea")
                .setMaxLength(4096));
    }

    /**
     * 追加 SIMPLE_LIST primitive 列表字段。
     *
     * @param spec demo 规格
     */
    private static void addSimpleListField(Spec spec) {
        spec.addField(new FieldSpec("ports")
                .setLabel("监听端口列表")
                .setDefaultValue(Arrays.asList(Integer.valueOf(25565))));
    }

    /**
     * 追加 TABLE 同构对象列表字段。
     *
     * @param spec demo 规格
     */
    private static void addTableField(Spec spec) {
        spec.addField(new FieldSpec("servers")
                .setLabel("服务器列表"));
    }

    /**
     * 追加 OBJECT 嵌套对象字段。
     *
     * @param spec demo 规格
     */
    private static void addObjectField(Spec spec) {
        spec.addField(new FieldSpec("database.credentials")
                .setLabel("数据库凭证"));
    }

    /**
     * 追加 KEY_VALUE_MAP 动态映射字段。
     *
     * @param spec demo 规格
     */
    private static void addKeyValueMapField(Spec spec) {
        spec.addField(new FieldSpec("labels")
                .setLabel("自定义标签")
                .setTemplateHint("dynamic-map"));
    }

    /**
     * 追加 PRESET_SELECTOR 预设选择字段。
     *
     * @param spec demo 规格
     */
    private static void addPresetSelectorField(Spec spec) {
        spec.addField(new FieldSpec("profile")
                .setLabel("运行档位"));
    }

    /**
     * 追加 RAW_EDITOR 源码编辑字段。
     *
     * @param spec demo 规格
     */
    private static void addRawEditorField(Spec spec) {
        spec.addField(new FieldSpec("payload")
                .setLabel("JSON 负载")
                .setTemplateHint("json"));
    }

    /**
     * 追加 ENHANCED_PICKER 增强选择器字段。
     *
     * @param spec demo 规格
     */
    private static void addEnhancedPickerFields(Spec spec) {
        spec.addField(new FieldSpec("theme.primary")
                .setLabel("主题色")
                .setTemplateHint("color"));
        spec.addField(new FieldSpec("texture.block")
                .setLabel("方块纹理")
                .setTemplateHint("resource"));
        spec.addField(new FieldSpec("audio.click")
                .setLabel("点击音效")
                .setTemplateHint("sound"));
    }

    /**
     * demo 保存回调：demo 配置无文件源，仅清理脏标记，不写文件。
     */
    private static final class DemoSaveHandler implements SaveHandler {
        @Override
        public void onSave(MutableConfig config) {
            if (config != null) {
                config.markClean();
            }
        }
    }
}
