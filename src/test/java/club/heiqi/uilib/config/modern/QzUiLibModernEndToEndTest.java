package club.heiqi.uilib.config.modern;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.ConfigNode;
import club.heiqi.config.ConfigSource;
import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.runtime.SaveOutcome;
import club.heiqi.config.schema.ConfigSchema;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * uilib 自身配置接入新架构的端到端集成测试。
 *
 * <p>验证完整链路：声明 Schema（{@link QzUiLibModernSchema}）→ bootstrap → openDraft
 * → 编辑字段 → save → 重新 bootstrap 加载，确认值持久。覆盖三个 section
 * （general / fontSystem / fontSizeSetting）的标量字段、校验回滚、恢复默认、
 * 多次保存一致性等场景。</p>
 *
 * <p>本测试只覆盖 {@link ConfigManager} 链路（纯 JVM 可跑），不涉及 MC GuiScreen
 * 打开部分（{@link ModernConfigEntry#open} 依赖 Minecraft 类，留给真机验证）。</p>
 *
 * <h3>合规边界</h3>
 * <p>测试位于 {@code uilib.config.modern} 测试包，import {@code config.schema/runtime}
 * 核心层与 {@code config} 序列化层，合法使用新架构 API。</p>
 */
public class QzUiLibModernEndToEndTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /** 读文件全文（UTF-8） */
    private static String readText(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /**
     * 空文件 bootstrap 后全部字段为 schema 默认值。
     *
     * <p>验证三个 section 的代表性字段：general.useDebug=false、general.netTransport=vanilla、
     * fontSystem.lerpMode=3、fontSizeSetting.charSize=9.0。</p>
     */
    @Test
    public void emptyBootstrapReturnsAllDefaults() throws Exception {
        File file = tempFolder.newFile("qzuilib-modern.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        // general
        assertFalse(manager.authority().getBool("general.useDebug"));
        assertFalse(manager.authority().getBool("general.uiDebug"));
        assertFalse(manager.authority().getBool("general.fontRuntimeDebug"));
        assertEquals("vanilla", manager.authority().getString("general.netTransport"));
        // fontSystem
        assertEquals(3.0, manager.authority().getNumber("fontSystem.lerpMode"), 0.0);
        assertEquals(2.0, manager.authority().getNumber("fontSystem.aaMode"), 0.0);
        assertEquals(2.0, manager.authority().getNumber("fontSystem.brightnessGain"), 0.0);
        assertFalse(manager.authority().getBool("fontSystem.replaceOrigin"));
        // fontSizeSetting
        assertEquals(64.0, manager.authority().getNumber("fontSizeSetting.awtCharSize"), 0.0);
        assertEquals(9.0, manager.authority().getNumber("fontSizeSetting.charSize"), 0.0);
    }

    /**
     * 完整保存流程：bootstrap 空文件 → openDraft → 改三个 section 各一个字段 → save
     * → 读回文件验证 → 重新 bootstrap → authority 返回新值。
     */
    @Test
    public void fullSaveFlowPersistsAcrossThreeSections() throws Exception {
        File file = tempFolder.newFile("qzuilib-modern.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("general.useDebug", Boolean.TRUE);
        draft.setDraft("general.netTransport", "forge");
        draft.setDraft("fontSystem.lerpMode", Double.valueOf(1.0));
        draft.setDraft("fontSizeSetting.charSize", Double.valueOf(12.0));

        SaveOutcome outcome = manager.save(draft);
        assertTrue("保存应成功: " + outcome.status(), outcome.isSuccess());

        // 读回文件验证
        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertTrue(reloaded.get("general.useDebug").asBoolean());
        assertEquals("forge", reloaded.get("general.netTransport").asString());
        assertEquals(1, reloaded.get("fontSystem.lerpMode").asInt());
        assertEquals(12.0, reloaded.get("fontSizeSetting.charSize").asDouble(), 0.0);

        // 重新 bootstrap，authority 返回新值
        ConfigManager manager2 = ConfigManager.bootstrap(file, schema);
        assertTrue(manager2.authority().getBool("general.useDebug"));
        assertEquals("forge", manager2.authority().getString("general.netTransport"));
        assertEquals(1.0, manager2.authority().getNumber("fontSystem.lerpMode"), 0.0);
        assertEquals(12.0, manager2.authority().getNumber("fontSizeSetting.charSize"), 0.0);
    }

    /**
     * 部分修改：只改 1 个字段，其他字段保持默认。
     */
    @Test
    public void partialSaveKeepsOtherDefaults() throws Exception {
        File file = tempFolder.newFile("qzuilib-modern.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("general.useDebug", Boolean.TRUE);
        assertTrue(manager.save(draft).isSuccess());

        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertTrue(reloaded.get("general.useDebug").asBoolean());
        // 其他字段保持默认
        assertFalse(reloaded.get("general.uiDebug").asBoolean());
        assertEquals("vanilla", reloaded.get("general.netTransport").asString());
        assertEquals(3, reloaded.get("fontSystem.lerpMode").asInt());
    }

    /**
     * 校验失败回滚：lerpMode 超范围（>3）→ save → INVALID → 文件不变 → authority 不变。
     */
    @Test
    public void saveRollsBackOnValidationFailure() throws Exception {
        File file = tempFolder.newFile("qzuilib-modern.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("general.useDebug", Boolean.TRUE);
        draft.setDraft("fontSystem.lerpMode", Double.valueOf(99.0)); // 非法，超范围 [0,3]

        SaveOutcome outcome = manager.save(draft);
        assertEquals(SaveOutcome.Status.INVALID, outcome.status());

        // 文件应保持空（未写入）
        assertTrue("文件应保持空: " + file.length(), file.length() == 0);
        // authority 不变：仍为默认
        assertFalse(manager.authority().getBool("general.useDebug"));
        assertEquals(3.0, manager.authority().getNumber("fontSystem.lerpMode"), 0.0);
    }

    /**
     * CHOICE 字段校验：netTransport 设为非 options 值 → save → INVALID。
     */
    @Test
    public void choiceValidationRejectsUnknownValue() throws Exception {
        File file = tempFolder.newFile("qzuilib-modern.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("general.netTransport", "unknown_transport");

        SaveOutcome outcome = manager.save(draft);
        assertEquals(SaveOutcome.Status.INVALID, outcome.status());
        assertEquals("vanilla", manager.authority().getString("general.netTransport"));
    }

    /**
     * 恢复默认：bootstrap 带初始值 → resetFieldToDefault → save → 文件中该字段变为默认。
     */
    @Test
    public void resetFieldToDefaultThenSave() throws Exception {
        File file = tempFolder.newFile("qzuilib-modern.yaml");
        // 预置非默认值
        java.io.FileWriter w = new java.io.FileWriter(file);
        try {
            w.write("general:\n  useDebug: true\n  netTransport: forge\n"
                    + "fontSystem:\n  lerpMode: 1\n"
                    + "fontSizeSetting:\n  charSize: 20.0\n");
        } finally {
            w.close();
        }

        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        // 确认加载了非默认值
        assertTrue(manager.authority().getBool("general.useDebug"));
        assertEquals(20.0, manager.authority().getNumber("fontSizeSetting.charSize"), 0.0);

        DraftBuffer draft = manager.openDraft();
        draft.resetFieldToDefault("general.useDebug");
        draft.resetFieldToDefault("fontSizeSetting.charSize");
        assertTrue(manager.save(draft).isSuccess());

        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        // 回到 schema 默认
        assertFalse(reloaded.get("general.useDebug").asBoolean());
        assertEquals(9.0, reloaded.get("fontSizeSetting.charSize").asDouble(), 0.0);
        // 未重置的字段保持预置值
        assertEquals("forge", reloaded.get("general.netTransport").asString());
        assertEquals(1, reloaded.get("fontSystem.lerpMode").asInt());
    }

    /**
     * 保存后再次打开编辑：save → openDraft → current=保存后的值 → 改 → save
     * → 文件反映第二次改动。
     */
    @Test
    public void reopenDraftAfterSaveReflectsCommittedValues() throws Exception {
        File file = tempFolder.newFile("qzuilib-modern.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        // 第一次保存 useDebug
        DraftBuffer draft1 = manager.openDraft();
        draft1.setDraft("general.useDebug", Boolean.TRUE);
        assertTrue(manager.save(draft1).isSuccess());

        // 再次打开，current 应为保存后的值
        DraftBuffer draft2 = manager.openDraft();
        assertEquals(Boolean.TRUE, draft2.getCurrent("general.useDebug"));
        // 改另一个字段
        draft2.setDraft("fontSystem.aaMode", Double.valueOf(1.0));
        assertTrue(manager.save(draft2).isSuccess());

        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertTrue(reloaded.get("general.useDebug").asBoolean());
        assertEquals(1, reloaded.get("fontSystem.aaMode").asInt());
    }

    /**
     * 取消后重新编辑：改字段 → resetToCurrent → 改另一字段 → save → 只有第二个改动生效。
     */
    @Test
    public void resetToCurrentThenEditOnlySecondChange() throws Exception {
        File file = tempFolder.newFile("qzuilib-modern.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("general.useDebug", Boolean.TRUE);
        // 取消，回到当前（默认）
        draft.resetToCurrent();
        // 改另一个字段
        draft.setDraft("fontSystem.lerpMode", Double.valueOf(2.0));

        assertTrue(manager.save(draft).isSuccess());

        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        // useDebug 保持默认 false，lerpMode 为新值 2
        assertFalse(reloaded.get("general.useDebug").asBoolean());
        assertEquals(2, reloaded.get("fontSystem.lerpMode").asInt());
    }

    /**
     * 多次 bootstrap 同一文件一致性。
     */
    @Test
    public void multipleBootstrapConsistent() throws Exception {
        File file = tempFolder.newFile("qzuilib-modern.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager1 = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager1.openDraft();
        draft.setDraft("general.netTransport", "forge");
        draft.setDraft("fontSizeSetting.charSize", Double.valueOf(11.0));
        assertTrue(manager1.save(draft).isSuccess());

        String transport1 = manager1.authority().getString("general.netTransport");
        double charSize1 = manager1.authority().getNumber("fontSizeSetting.charSize");

        ConfigManager manager2 = ConfigManager.bootstrap(file, schema);
        assertEquals(transport1, manager2.authority().getString("general.netTransport"));
        assertEquals(charSize1, manager2.authority().getNumber("fontSizeSetting.charSize"), 0.0);
    }

    /**
     * YAML 格式输出验证：save 后读回文件内容，确认是 YAML 格式（含冒号缩进，不是 JSON）。
     */
    @Test
    public void saveProducesYamlNotJson() throws Exception {
        File file = tempFolder.newFile("qzuilib-modern.yaml");
        ConfigSchema schema = QzUiLibModernSchema.create();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        DraftBuffer draft = manager.openDraft();
        draft.setDraft("general.useDebug", Boolean.TRUE);
        assertTrue(manager.save(draft).isSuccess());

        String text = readText(file);
        assertTrue("YAML 应含 general 键: " + text, text.contains("general:"));
        assertTrue("YAML 应含 useDebug 键: " + text, text.contains("useDebug:"));
        assertTrue("YAML 应含 fontSystem 键: " + text, text.contains("fontSystem:"));
        // 不应是 JSON 大括号格式
        assertFalse("不应含 JSON 大括号: " + text, text.contains("{"));
        assertFalse("不应含 JSON 大括号: " + text, text.contains("}"));
    }

    /**
     * Schema 结构完整性：三个 section 全部存在，字段数符合预期
     * （general 4 + fontSystem 16 + fontSizeSetting 2 = 22）。
     */
    @Test
    public void schemaHasExpectedSectionsAndFieldCount() {
        ConfigSchema schema = QzUiLibModernSchema.create();
        assertEquals("qzuilib", schema.modId());
        assertEquals(3, schema.sections().size());
        assertEquals("general", schema.sections().get(0).name());
        assertEquals("fontSystem", schema.sections().get(1).name());
        assertEquals("fontSizeSetting", schema.sections().get(2).name());
        // 总字段数：general 4 + fontSystem 16 + fontSizeSetting 2 = 22
        assertEquals(22, schema.allFields().size());
    }
}