package club.heiqi.config.runtime;

import club.heiqi.config.ConfigException;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.ConfigNode;
import club.heiqi.config.Config;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.SectionSpec;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link Persistence} 层直接测试，覆盖 read 空文件/不存在文件、
 * writeAll round-trip、空 Map、嵌套非 Schema 子树、格式一致性、
 * 覆盖写、多次写、大型配置、NUMBER/BOOLEAN 保真、损坏 YAML 解析报错。
 *
 * <p>使用 {@link SchemaTestFactory#serverSchema()} 与 {@link TemporaryFolder}，
 * 不触碰主代码。</p>
 */
public class PersistenceTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /** 写字符串到文件 */
    private static void write(File file, String content) throws Exception {
        FileWriter writer = new FileWriter(file);
        try {
            writer.write(content);
        } finally {
            writer.close();
        }
    }

    /** 读文件全文（UTF-8） */
    private static String readText(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /** 构造 5 section × 10 string 字段的大型 schema */
    private static ConfigSchema bigSchema() {
        ConfigSchema.Builder b = ConfigSchema.builder("big");
        for (int s = 0; s < 5; s++) {
            SectionSpec.Builder sb = b.section("sec" + s);
            for (int f = 0; f < 10; f++) {
                sb = sb.string("f" + f).defaultValue("v" + f).build();
            }
            sb.endSection();
        }
        return b.build();
    }

    /**
     * read 空文件：返回空 MAP 节点。
     */
    @Test
    public void readEmptyFileReturnsEmptyMap() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        // 确保文件存在但为空
        assertTrue(file.length() == 0);

        Persistence persistence = new Persistence(file, ConfigFormat.YAML);
        ConfigNode node = persistence.read();
        assertEquals(ConfigNode.NodeType.MAP, node.getType());
        assertTrue(node.asMap().isEmpty());
    }

    /**
     * read 不存在文件：返回空 MAP 节点（不抛异常）。
     */
    @Test
    public void readNonExistentFileReturnsEmptyMap() throws Exception {
        File file = new File(tempFolder.getRoot(), "no-such-file.yaml");
        assertFalse(file.exists());

        Persistence persistence = new Persistence(file, ConfigFormat.YAML);
        ConfigNode node = persistence.read();
        assertEquals(ConfigNode.NodeType.MAP, node.getType());
        assertTrue(node.asMap().isEmpty());
    }

    /**
     * writeAll 后 read round-trip：写入 4 个字段 → read → 验证 4 个值。
     */
    @Test
    public void writeAllRoundTripFourFields() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        Persistence persistence = new Persistence(file, ConfigFormat.YAML);
        ConfigSchema schema = SchemaTestFactory.serverSchema();

        Map<String, Object> typed = new HashMap<String, Object>();
        typed.put("server.host", "rt.host");
        typed.put("server.port", 3000.0);
        typed.put("server.debug", true);
        typed.put("server.mode", "test");

        persistence.writeAll(typed, schema);

        ConfigNode reloaded = persistence.read();
        assertEquals("rt.host", reloaded.get("server.host").asString());
        assertEquals(3000, reloaded.get("server.port").asInt());
        assertTrue(reloaded.get("server.debug").asBoolean());
        assertEquals("test", reloaded.get("server.mode").asString());
    }

    /**
     * writeAll 空 Map：写入空 Map → read → 空节点。
     */
    @Test
    public void writeAllEmptyMapProducesEmptyFile() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        Persistence persistence = new Persistence(file, ConfigFormat.YAML);
        ConfigSchema schema = SchemaTestFactory.emptySchema();

        persistence.writeAll(new HashMap<String, Object>(), schema);

        ConfigNode reloaded = persistence.read();
        assertEquals(ConfigNode.NodeType.MAP, reloaded.getType());
        assertTrue(reloaded.asMap().isEmpty());
    }

    /**
     * writeAll 嵌套非 Schema 子树：写入含非 Schema 子树的 Map → read → 子树保留。
     */
    @Test
    public void writeAllNestedNonSchemaSubtreePreserved() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        Persistence persistence = new Persistence(file, ConfigFormat.YAML);
        ConfigSchema schema = SchemaTestFactory.serverSchema();

        // 构造非 Schema 子树
        ConfigNode extra = Config.parse("nested:\n  value: keep\n  deep:\n    leaf: 42\n",
                ConfigFormat.YAML);

        Map<String, Object> typed = new HashMap<String, Object>();
        typed.put("server.host", "x.host");
        typed.put("server.port", 8080.0);
        typed.put("server.debug", false);
        typed.put("server.mode", "online");
        typed.put("extra", extra);

        persistence.writeAll(typed, schema);

        ConfigNode reloaded = persistence.read();
        assertEquals("x.host", reloaded.get("server.host").asString());
        assertEquals("keep", reloaded.get("extra.nested.value").asString());
        assertEquals(42, reloaded.get("extra.nested.deep.leaf").asInt());
    }

    /**
     * writeAll 格式一致性：写入后文件内容是 YAML 格式（含冒号，不含 JSON 大括号）。
     */
    @Test
    public void writeAllProducesYamlFormat() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        Persistence persistence = new Persistence(file, ConfigFormat.YAML);
        ConfigSchema schema = SchemaTestFactory.serverSchema();

        Map<String, Object> typed = new HashMap<String, Object>();
        typed.put("server.host", "fmt.host");
        typed.put("server.port", 8080.0);
        persistence.writeAll(typed, schema);

        String text = readText(file);
        assertTrue("YAML 应含 server: " + text, text.contains("server:"));
        assertTrue("YAML 应含 host: " + text, text.contains("host:"));
        assertFalse("不应含 JSON 大括号: " + text, text.contains("{"));
        assertFalse("不应含 JSON 大括号: " + text, text.contains("}"));
    }

    /**
     * writeAll 覆盖已有文件：文件有旧内容 → writeAll → 旧内容被完全替换。
     */
    @Test
    public void writeAllOverwritesExistingFile() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file, "legacy:\n  old: data\n  stale: true\n");
        Persistence persistence = new Persistence(file, ConfigFormat.YAML);
        ConfigSchema schema = SchemaTestFactory.serverSchema();

        Map<String, Object> typed = new HashMap<String, Object>();
        typed.put("server.host", "new.host");
        typed.put("server.port", 8080.0);
        typed.put("server.debug", false);
        typed.put("server.mode", "online");
        persistence.writeAll(typed, schema);

        ConfigNode reloaded = persistence.read();
        assertEquals("new.host", reloaded.get("server.host").asString());
        // 旧内容应被完全替换
        assertFalse("旧 legacy 子树应被覆盖: " + readText(file),
                reloaded.has("legacy"));
    }

    /**
     * writeAll 多次写同一文件：第一次写 → 第二次写不同值 → read 反映第二次值。
     */
    @Test
    public void writeAllMultipleWritesLastWins() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        Persistence persistence = new Persistence(file, ConfigFormat.YAML);
        ConfigSchema schema = SchemaTestFactory.serverSchema();

        Map<String, Object> first = new HashMap<String, Object>();
        first.put("server.host", "first.host");
        first.put("server.port", 1000.0);
        first.put("server.debug", false);
        first.put("server.mode", "online");
        persistence.writeAll(first, schema);

        Map<String, Object> second = new HashMap<String, Object>();
        second.put("server.host", "second.host");
        second.put("server.port", 2000.0);
        second.put("server.debug", true);
        second.put("server.mode", "test");
        persistence.writeAll(second, schema);

        ConfigNode reloaded = persistence.read();
        assertEquals("second.host", reloaded.get("server.host").asString());
        assertEquals(2000, reloaded.get("server.port").asInt());
        assertTrue(reloaded.get("server.debug").asBoolean());
        assertEquals("test", reloaded.get("server.mode").asString());
    }

    /**
     * writeAll 大型配置：50 个字段 → writeAll → read → 50 个值正确。
     */
    @Test
    public void writeAllLargeConfigRoundTrip() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        Persistence persistence = new Persistence(file, ConfigFormat.YAML);
        ConfigSchema schema = bigSchema();

        Map<String, Object> typed = new HashMap<String, Object>();
        for (int s = 0; s < 5; s++) {
            for (int f = 0; f < 10; f++) {
                typed.put("sec" + s + ".f" + f, "val" + s + "_" + f);
            }
        }
        persistence.writeAll(typed, schema);

        ConfigNode reloaded = persistence.read();
        for (int s = 0; s < 5; s++) {
            for (int f = 0; f < 10; f++) {
                assertEquals("val" + s + "_" + f,
                        reloaded.get("sec" + s + ".f" + f).asString());
            }
        }
    }

    /**
     * writeAll NUMBER 保真：整数不变成小数。
     *
     * <p>直接向 writeAll 传 Integer 值（绕过 Authority 的 Double 规范化），
     * 验证 Persistence 层不会把整数序列化为 "8080.0"。</p>
     */
    @Test
    public void writeAllNumberIntegerPreserved() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        Persistence persistence = new Persistence(file, ConfigFormat.YAML);
        ConfigSchema schema = SchemaTestFactory.serverSchema();

        Map<String, Object> typed = new HashMap<String, Object>();
        typed.put("server.host", "localhost");
        typed.put("server.port", Integer.valueOf(8080)); // Integer 而非 Double
        typed.put("server.debug", false);
        typed.put("server.mode", "online");
        persistence.writeAll(typed, schema);

        String text = readText(file);
        assertTrue("应含 8080: " + text, text.contains("8080"));
        assertFalse("不应含 8080.0（整数不应变浮点）: " + text, text.contains("8080.0"));

        ConfigNode reloaded = persistence.read();
        assertEquals(8080, reloaded.get("server.port").asInt());
    }

    /**
     * writeAll BOOLEAN 保真：true/false 不被序列化成 "true" 字符串（带引号）。
     */
    @Test
    public void writeAllBooleanPreserved() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        Persistence persistence = new Persistence(file, ConfigFormat.YAML);
        ConfigSchema schema = SchemaTestFactory.serverSchema();

        Map<String, Object> typed = new HashMap<String, Object>();
        typed.put("server.host", "localhost");
        typed.put("server.port", 8080.0);
        typed.put("server.debug", Boolean.TRUE);
        typed.put("server.mode", "online");
        persistence.writeAll(typed, schema);

        String text = readText(file);
        // YAML 中布尔 true 不应被引号包裹
        assertTrue("应含 debug: true: " + text, text.contains("debug: true"));
        assertFalse("布尔不应被引号包裹: " + text, text.contains("\"true\""));
        assertFalse("布尔不应被引号包裹: " + text, text.contains("'true'"));

        ConfigNode reloaded = persistence.read();
        assertTrue(reloaded.get("server.debug").asBoolean());
    }

    /**
     * read 损坏 YAML：文件含语法错误 → read 抛 ConfigException。
     */
    @Test
    public void readCorruptedYamlThrowsConfigException() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        // 未闭合的 flow sequence
        write(file, "key: [unclosed\n");
        Persistence persistence = new Persistence(file, ConfigFormat.YAML);

        try {
            persistence.read();
            fail("损坏 YAML 应抛 ConfigException");
        } catch (ConfigException e) {
            // 预期：解析失败被包装为 ConfigException
            assertTrue(e.getMessage() != null);
        }
    }

    /** writeAll 的公开参数错误继续抛 IllegalArgumentException，不映射为 IO_FAILED。 */
    @Test
    public void writeAllKeepsIllegalArgumentContract() throws Exception {
        File file = tempFolder.newFile("arguments.yaml");
        Persistence persistence = new Persistence(file, ConfigFormat.YAML);
        try {
            persistence.writeAll(null, SchemaTestFactory.serverSchema());
            fail("null typedValues should fail");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("typedValues"));
        }
        try {
            persistence.writeAll(new HashMap<String, Object>(), null);
            fail("null schema should fail");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("schema"));
        }
    }
}
