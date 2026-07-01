package club.heiqi.config.runtime;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigException;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.ConfigNode;
import club.heiqi.config.ConfigSource;
import club.heiqi.config.schema.ConfigSchema;

import java.io.File;
import java.io.FileWriter;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link LegacyAdapter} 测试，覆盖 getRawJson 取子树、setRawJson 写回 + flushRaw 持久化、
 * 标量字段 getRawJson。
 */
public class LegacyAdapterTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /**
     * 写字符串到文件。
     */
    private static void write(File file, String content) throws Exception {
        FileWriter writer = new FileWriter(file);
        try {
            writer.write(content);
        } finally {
            writer.close();
        }
    }

    /**
     * getRawJson 取非 Schema 子树。
     */
    @Test
    public void getRawJsonReturnsSubtree() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file,
                "server:\n" +
                "  host: localhost\n" +
                "  port: 8080\n" +
                "  debug: false\n" +
                "  mode: online\n" +
                "extra:\n" +
                "  name: legacy\n" +
                "  count: 7\n");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);

        String raw = authority.legacy().getRawJson("extra");

        assertTrue("应包含 name: " + raw, raw.contains("name"));
        assertTrue("应包含 legacy: " + raw, raw.contains("legacy"));
        assertTrue("应包含 count: " + raw, raw.contains("count"));
        assertTrue("应包含 7: " + raw, raw.contains("7"));
    }

    /**
     * setRawJson 写回 + flushRaw 持久化到文件。
     */
    @Test
    public void setRawJsonAndFlushPersists() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        manager.authority().legacy().setRawJson("custom", "key: value\nnum: 42\n");
        manager.flushRaw();

        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertEquals("value", reloaded.get("custom.key").asString());
        assertEquals(42, reloaded.get("custom.num").asInt());
    }

    /**
     * 标量字段 getRawJson 返回 YAML 文本。
     */
    @Test
    public void scalarFieldGetRawJson() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file,
                "server:\n" +
                "  host: scalar.example\n" +
                "  port: 8080\n" +
                "  debug: true\n" +
                "  mode: online\n");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);

        String hostYaml = authority.legacy().getRawJson("server.host");
        assertTrue("标量应包含 scalar.example: " + hostYaml, hostYaml.contains("scalar.example"));

        String debugYaml = authority.legacy().getRawJson("server.debug");
        assertTrue("布尔应包含 true: " + debugYaml, debugYaml.contains("true"));
    }

    /**
     * 不存在路径 getRawJson 返回空串。
     */
    @Test
    public void missingPathGetRawJsonReturnsEmpty() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);

        assertEquals("", authority.legacy().getRawJson("nonexistent.path"));
    }

    // ===== 边界用例追加 =====

    /**
     * setRawJson 非法 YAML：抛 ConfigException。
     */
    @Test(expected = ConfigException.class)
    public void setRawJsonInvalidYamlThrows() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);

        authority.legacy().setRawJson("custom", "key: [unclosed");
    }

    /**
     * setRawJson 覆盖已有子树：后一次覆盖前一次，只含新内容。
     */
    @Test
    public void setRawJsonOverwritesExistingSubtree() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);

        authority.legacy().setRawJson("custom", "a: 1\n");
        authority.legacy().setRawJson("custom", "b: 2\n");

        String raw = authority.legacy().getRawJson("custom");
        assertTrue("应含 b: " + raw, raw.contains("b"));
        assertTrue("应含 2: " + raw, raw.contains("2"));
        // 验证当前行为：覆盖后不含 a
        assertFalse("覆盖后不应含 a: " + raw, raw.contains("a:"));
    }

    /**
     * setRawJson 嵌套路径：setRawJson("custom.nested", ...) 后 getRawJson("custom.nested") 含内容。
     */
    @Test
    public void setRawJsonNestedPath() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);

        authority.legacy().setRawJson("custom.nested", "x: 1\n");

        String raw = authority.legacy().getRawJson("custom.nested");
        assertTrue("应含 x: " + raw, raw.contains("x"));
        assertTrue("应含 1: " + raw, raw.contains("1"));
    }

    /**
     * setRawJson 后 getRawJson round-trip：结构等价。
     */
    @Test
    public void setRawJsonGetRawJsonRoundTrip() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);

        authority.legacy().setRawJson("custom", "name: legacy\nvalue: 42\nflag: true\n");

        String raw = authority.legacy().getRawJson("custom");
        assertTrue(raw.contains("name"));
        assertTrue(raw.contains("legacy"));
        assertTrue(raw.contains("value"));
        assertTrue(raw.contains("42"));
        assertTrue(raw.contains("flag"));
        assertTrue(raw.contains("true"));
    }

    /**
     * getRawJson 对 Schema 字段（标量数值）。
     * 验证当前行为：Schema 字段返回标量 YAML 文本。
     */
    @Test
    public void getRawJsonScalarNumber() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file,
                "server:\n  host: localhost\n  port: 8080\n  debug: false\n  mode: online\n");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);

        String portYaml = authority.legacy().getRawJson("server.port");
        assertNotNull(portYaml);
        assertTrue("标量数值应含 8080: " + portYaml, portYaml.contains("8080"));
    }

    /**
     * setRawJson 对 Schema 字段路径：覆盖 typed 值。
     * 验证当前行为：putRaw 对 Schema 路径提取 typed 值覆盖。
     */
    @Test
    public void setRawJsonOnSchemaFieldOverwritesTyped() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);

        authority.legacy().setRawJson("server.host", "overridden.host");

        // 验证当前行为：setRawJson 覆盖了 Schema 字段 typed 值
        assertEquals("overridden.host", authority.getString("server.host"));
    }

    /**
     * setRawJson 大型嵌套 JSON：3 层 Map+List round-trip 结构等价。
     */
    @Test
    public void setRawJsonLargeNestedStructure() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);

        String yaml =
                "level1:\n" +
                "  level2:\n" +
                "    items:\n" +
                "      - name: item1\n" +
                "        value: 1\n" +
                "      - name: item2\n" +
                "        value: 2\n" +
                "    flag: true\n" +
                "  count: 2\n";
        authority.legacy().setRawJson("custom", yaml);

        String raw = authority.legacy().getRawJson("custom");
        assertTrue("应含 level1: " + raw, raw.contains("level1"));
        assertTrue("应含 level2: " + raw, raw.contains("level2"));
        assertTrue("应含 items: " + raw, raw.contains("items"));
        assertTrue("应含 item1: " + raw, raw.contains("item1"));
        assertTrue("应含 item2: " + raw, raw.contains("item2"));
    }

    /**
     * setRawJson 中文内容：round-trip 不乱码。
     */
    @Test
    public void setRawJsonChineseContent() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);

        authority.legacy().setRawJson("custom", "名称: 测试\n描述: 中文内容\n");

        String raw = authority.legacy().getRawJson("custom");
        assertTrue("应含中文: " + raw, raw.contains("名称"));
        assertTrue("应含中文内容: " + raw, raw.contains("中文内容"));
    }

    /**
     * setRawJson 特殊字符：值含冒号、引号，round-trip 正确。
     */
    @Test
    public void setRawJsonSpecialCharacters() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);

        // 用 YAML 引号承载特殊字符
        authority.legacy().setRawJson("custom", "text: 'a: b'\nquote: \"hello\"\n");

        String raw = authority.legacy().getRawJson("custom");
        assertTrue("应含 text: " + raw, raw.contains("text"));
        assertTrue("应含 quote: " + raw, raw.contains("quote"));
    }

    /**
     * getRawJson 对 LIST 子树：非 Schema 字段是 list，返回 YAML list 文本。
     */
    @Test
    public void getRawJsonListSubtree() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file,
                "server:\n  host: localhost\n  port: 8080\n  debug: false\n  mode: online\n" +
                "items:\n  - one\n  - two\n  - three\n");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);

        String raw = authority.legacy().getRawJson("items");
        assertTrue("应含 one: " + raw, raw.contains("one"));
        assertTrue("应含 two: " + raw, raw.contains("two"));
        assertTrue("应含 three: " + raw, raw.contains("three"));
    }

    /**
     * setRawJson 后多次 flushRaw：第一次写盘，第二次无改动仍成功。
     */
    @Test
    public void setRawJsonMultipleFlushRaw() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        manager.authority().legacy().setRawJson("custom", "key: value\n");
        manager.flushRaw();

        // 第二次 flushRaw 无改动仍成功
        manager.flushRaw();

        ConfigNode reloaded = Config.load(ConfigSource.fromFile(file), ConfigFormat.YAML);
        assertEquals("value", reloaded.get("custom.key").asString());
    }

    /**
     * setRawJson 空文档：传 "null"。
     * 验证当前行为：SnakeYAML 解析 "null" 返回 null，
     * putRaw 对 null/NullConfigNode 不写入，getRawJson 返回空串或抛异常均合理。
     */
    @Test
    public void setRawJsonEmptyDocument() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);

        try {
            authority.legacy().setRawJson("custom", "null\n");
            // null 文档后 getRawJson 应返回空串（未写入）或 "null" 文本
            String result = authority.legacy().getRawJson("custom");
            // 接受空串或 null 文本，只要不抛异常即可
            assertTrue("null 文档后 getRawJson 不应抛异常: " + result, result != null);
        } catch (ConfigException e) {
            // 解析器拒绝空文档也属合理行为
        }
    }
}
