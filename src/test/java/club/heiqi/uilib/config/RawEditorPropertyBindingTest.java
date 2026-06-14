package club.heiqi.uilib.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.ConfigNode;
import club.heiqi.config.MutableConfig;
import club.heiqi.uilib.ui.control.DocumentCodeEditorControl;
import club.heiqi.uilib.ui.control.DocumentCodeEditorSyntaxSupport;
import club.heiqi.uilib.ui.dom.UiDocument;

/**
 * 源码编辑器字段绑定测试。
 *
 * <p>覆盖：合法 JSON 写回 draft、非法 JSON 触发 setErrorLines 但不污染 draft、
 * 语言切换重新序列化、restoreCurrentValue 重置草稿、YAML 格式 round-trip。</p>
 */
public class RawEditorPropertyBindingTest {

    @Test
    public void lawfulJsonWritesBackToDraft() throws Exception {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("server.host", "localhost")
                .set("server.port", 8080)
                .set("debug", Boolean.TRUE);
        ModernConfigTemplateScreen.FieldSpec fieldSpec = new ModernConfigTemplateScreen.FieldSpec("server")
                .setTemplateHint("json");
        RawEditorPropertyBinding binding = newBinding(config, "server", fieldSpec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        // 初次渲染：未编辑，不脏
        assertFalse(binding.isDirty());
        assertNull(binding.validateDraft());

        // 用户改成新的合法 JSON
        String newJson = "{\"host\":\"remote.example\",\"port\":9999,\"debug\":false}";
        binding.processUserText(newJson);

        assertTrue(binding.isDirty());
        assertNull(binding.validateDraft());
        assertNotNull(binding.getLastValidNode());
        assertEquals("remote.example", binding.getLastValidNode().get("host").asString());

        binding.applyDraft();
        assertEquals("remote.example", config.get("server.host").asString());
        assertEquals(9999, config.get("server.port").asInt());
        assertFalse(config.get("server.debug").asBoolean());
    }

    @Test
    public void unlawfulJsonSetsErrorLinesButDoesNotCorruptDraft() throws Exception {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("server.host", "localhost");
        ModernConfigTemplateScreen.FieldSpec fieldSpec = new ModernConfigTemplateScreen.FieldSpec("server")
                .setTemplateHint("json");
        RawEditorPropertyBinding binding = newBinding(config, "server", fieldSpec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        DocumentCodeEditorControl editor = binding.getCodeEditor();
        ConfigNode lastValidBeforeBreakage = binding.getLastValidNode();
        assertNotNull(lastValidBeforeBreakage);

        // 用户编辑成不合法 JSON
        binding.processUserText("{\"broken\": ");
        assertTrue("错误行集合应包含第 0 行", editor.getErrorLines().contains(Integer.valueOf(0)));
        assertNotNull(binding.validateDraft());
        assertFalse(binding.getLastErrorMessage().isEmpty());
        // draft 不被污染：lastValidNode 保持上一次合法值
        assertEquals(lastValidBeforeBreakage, binding.getLastValidNode());

        // applyDraft 应写回最后一次合法值，不写语法错误的当前文本
        binding.applyDraft();
        assertEquals("localhost", config.get("server.host").asString());
    }

    @Test
    public void setLanguageYamlWhenHintIsYaml() {
        MutableConfig config = Config.createMutable(ConfigFormat.YAML)
                .set("theme.base", "dark")
                .set("theme.accent", "blue");
        ModernConfigTemplateScreen.FieldSpec fieldSpec = new ModernConfigTemplateScreen.FieldSpec("theme")
                .setTemplateHint("yaml");
        RawEditorPropertyBinding binding = newBinding(config, "theme", fieldSpec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        DocumentCodeEditorControl editor = binding.getCodeEditor();

        assertEquals(DocumentCodeEditorSyntaxSupport.Language.YAML, editor.getLanguage());
        assertEquals(ConfigFormat.YAML, binding.getRawFormat());
        // 序列化后的初始文本应包含 YAML 风格的键值分隔
        String initialText = editor.getText();
        assertTrue("YAML 文本应包含 base: " + initialText, initialText.contains("base:"));
        assertTrue("YAML 文本应包含 dark" + initialText, initialText.contains("dark"));
    }

    @Test
    public void restoreCurrentValueResetsDraft() throws Exception {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("server.host", "localhost");
        ModernConfigTemplateScreen.FieldSpec fieldSpec = new ModernConfigTemplateScreen.FieldSpec("server")
                .setTemplateHint("json");
        RawEditorPropertyBinding binding = newBinding(config, "server", fieldSpec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        DocumentCodeEditorControl editor = binding.getCodeEditor();
        String initialText = editor.getText();

        // 用户改成新值
        binding.processUserText("{\"host\":\"remote.example\"}");
        assertTrue(binding.isDirty());

        // 恢复当前值：应把 draft 重置回 config 当前值
        binding.restoreCurrentValue();
        assertFalse(binding.isDirty());
        assertNull(binding.validateDraft());
        assertEquals(initialText, editor.getText());
        // 错误行集合应被清空
        assertTrue(editor.getErrorLines().isEmpty());
    }

    @Test
    public void restoringAfterBreakageBringsBackLawfulState() throws Exception {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("server.host", "localhost");
        ModernConfigTemplateScreen.FieldSpec fieldSpec = new ModernConfigTemplateScreen.FieldSpec("server")
                .setTemplateHint("json");
        RawEditorPropertyBinding binding = newBinding(config, "server", fieldSpec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        DocumentCodeEditorControl editor = binding.getCodeEditor();

        // 输入非法
        binding.processUserText("not a json {");
        assertNotNull(binding.validateDraft());
        assertFalse(editor.getErrorLines().isEmpty());

        // 恢复：错误应清空，draft 应回到当前 config 值
        binding.restoreCurrentValue();
        assertNull(binding.validateDraft());
        assertTrue(editor.getErrorLines().isEmpty());
        assertFalse(binding.isDirty());
    }

    @Test
    public void genericRawHintFallsBackToJsonFormat() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("blob", "payload");
        ModernConfigTemplateScreen.FieldSpec fieldSpec = new ModernConfigTemplateScreen.FieldSpec("blob")
                .setTemplateHint("raw");
        RawEditorPropertyBinding binding = newBinding(config, "blob", fieldSpec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        // raw hint 走 fallback；测试以默认 createMutable(JSON) 创建，因此 rawFormat 解析为 JSON
        assertEquals(ConfigFormat.JSON, binding.getRawFormat());
    }

    @Test
    public void mapSubtreeSerializesAllChildren() throws Exception {
        Map<String, Object> inner = new LinkedHashMap<String, Object>();
        inner.put("name", "primary");
        inner.put("value", Integer.valueOf(1));
        MutableConfig config = Config.createMutable(ConfigFormat.JSON)
                .set("cluster", inner);
        ModernConfigTemplateScreen.FieldSpec fieldSpec = new ModernConfigTemplateScreen.FieldSpec("cluster")
                .setTemplateHint("json");
        RawEditorPropertyBinding binding = newBinding(config, "cluster", fieldSpec);

        binding.createEditorElement(UiDocument.create(), ForgeConfigTemplateScreen.Theme.defaultTheme());
        DocumentCodeEditorControl editor = binding.getCodeEditor();
        String initialText = editor.getText();
        assertTrue(initialText.contains("\"name\""));
        assertTrue(initialText.contains("\"primary\""));

        // 编辑后写回
        binding.processUserText("{\"name\":\"secondary\",\"value\":2}");
        binding.applyDraft();
        assertEquals("secondary", config.get("cluster.name").asString());
        assertEquals(2, config.get("cluster.value").asInt());
    }

    private static RawEditorPropertyBinding newBinding(MutableConfig config, String path,
            ModernConfigTemplateScreen.FieldSpec fieldSpec) {
        ModernConfigTypeInference.Result inference = ModernConfigTypeInference.infer(path, config.get(path),
                fieldSpec);
        return new RawEditorPropertyBinding(config, path, config.get(path), fieldSpec, inference, null);
    }
}
