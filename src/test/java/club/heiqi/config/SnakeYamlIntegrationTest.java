package club.heiqi.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * SnakeYAML 集成测试，覆盖复杂嵌套 round-trip、子树序列化、
 * YAML 全特性（多行字符串、锚点/别名）、NUMBER 类型保真、空值处理。
 */
public class SnakeYamlIntegrationTest {

    /**
     * 4 层 Map + List-of-Maps 的复杂嵌套 round-trip：
     * writeToString 后 parse 回来结构等价。
     */
    @Test
    public void deepNestedRoundTripPreservesStructure() throws ConfigException {
        // 构造 a.b.c.d = value，并在 b 层嵌入 list-of-maps
        Map<String, Object> innerItem1 = new LinkedHashMap<String, Object>();
        innerItem1.put("id", 1);
        innerItem1.put("name", "first");
        Map<String, Object> innerItem2 = new LinkedHashMap<String, Object>();
        innerItem2.put("id", 2);
        innerItem2.put("name", "second");

        Map<String, Object> level3 = new LinkedHashMap<String, Object>();
        level3.put("d", "deep-value");
        level3.put("items", java.util.Arrays.asList(innerItem1, innerItem2));

        Map<String, Object> level2 = new LinkedHashMap<String, Object>();
        level2.put("c", level3);

        Map<String, Object> level1 = new LinkedHashMap<String, Object>();
        level1.put("b", level2);

        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("a", level1);
        root.put("top", true);

        MutableConfig config = Config.createMutable(ConfigFormat.YAML);
        config.set("a", root.get("a"));
        config.set("top", true);

        String yaml = ConfigSerializer.toString(config, ConfigFormat.YAML);
        assertNotNull(yaml);
        assertFalse("YAML 输出不应为空", yaml.trim().isEmpty());

        ConfigNode reparsed = Config.parse(yaml, ConfigFormat.YAML);
        assertEquals("deep-value", reparsed.get("a.b.c.d").asString());
        assertTrue(reparsed.get("top").asBoolean());

        ConfigNode items = reparsed.get("a.b.c.items");
        assertNotNull(items);
        assertEquals(ConfigNode.NodeType.LIST, items.getType());
        List<ConfigNode> list = items.asList();
        assertEquals(2, list.size());
        assertEquals(1, list.get(0).get("id").asInt());
        assertEquals("first", list.get(0).get("name").asString());
        assertEquals(2, list.get(1).get("id").asInt());
        assertEquals("second", list.get(1).get("name").asString());
    }

    /**
     * 子树序列化：取子树 writeToString，输出不应包含兄弟字段。
     */
    @Test
    public void subtreeSerializationExcludesSiblings() throws ConfigException {
        MutableConfig config = Config.createMutable(ConfigFormat.YAML);
        config.set("server.host", "localhost");
        config.set("server.port", 8080);
        config.set("debug", true);

        ConfigNode serverNode = config.get("server");
        String yaml = ConfigSerializer.toString(serverNode, ConfigFormat.YAML);

        assertNotNull(yaml);
        assertTrue("子树 YAML 应包含 host: " + yaml, yaml.contains("host"));
        assertTrue("子树 YAML 应包含 localhost: " + yaml, yaml.contains("localhost"));
        assertTrue("子树 YAML 应包含 port: " + yaml, yaml.contains("port"));
        assertTrue("子树 YAML 应包含 8080: " + yaml, yaml.contains("8080"));
        assertFalse("子树 YAML 不应包含兄弟字段 debug: " + yaml, yaml.contains("debug"));

        // 子树本身可被重新解析为独立 map
        ConfigNode reparsed = Config.parse(yaml, ConfigFormat.YAML);
        assertEquals("localhost", reparsed.get("host").asString());
        assertEquals(8080, reparsed.get("port").asInt());
    }

    /**
     * YAML 全特性：多行字符串（| 语法）。
     * SnakeYAML 解析后应得到带换行的字符串。
     */
    @Test
    public void multilineBlockScalarPreservesNewlines() throws ConfigException {
        String yaml = "description: |\n" +
                      "  line one\n" +
                      "  line two\n" +
                      "  line three\n";
        ConfigNode node = Config.parse(yaml, ConfigFormat.YAML);
        String value = node.get("description").asString();
        assertNotNull(value);
        // 块标量 | 保留换行，末尾保留一个换行
        assertTrue("多行字符串应包含 line one: " + value, value.contains("line one"));
        assertTrue("多行字符串应包含 line two: " + value, value.contains("line two"));
        assertTrue("多行字符串应包含 line three: " + value, value.contains("line three"));
        assertTrue("多行字符串应保留换行: " + value, value.contains("\n"));
    }

    /**
     * YAML 全特性：锚点/别名（&/* 语法）。
     * SnakeYAML 解析时会把别名解析为同一对象引用。
     */
    @Test
    public void anchorAndAliasResolvesToSameValue() throws ConfigException {
        String yaml = "defaults: &defaults\n" +
                      "  timeout: 30\n" +
                      "  retries: 3\n" +
                      "production:\n" +
                      "  <<: *defaults\n" +
                      "  host: prod.example.com\n";
        ConfigNode node = Config.parse(yaml, ConfigFormat.YAML);

        // 合并键（<<）应把 defaults 的字段合并进 production
        assertEquals(30, node.get("production.timeout").asInt());
        assertEquals(3, node.get("production.retries").asInt());
        assertEquals("prod.example.com", node.get("production.host").asString());

        // 原始锚点节点仍可访问
        assertEquals(30, node.get("defaults.timeout").asInt());
    }

    /**
     * NUMBER 类型保真：整数不变成小数，浮点不丢精度。
     */
    @Test
    public void numberTypePreservedOnRoundTrip() throws ConfigException {
        String yaml = "intVal: 42\n" +
                      "longVal: 9000000000\n" +
                      "doubleVal: 3.14159\n" +
                      "negativeInt: -7\n" +
                      "zeroFloat: 0.0\n";
        ConfigNode node = Config.parse(yaml, ConfigFormat.YAML);

        assertEquals(42, node.get("intVal").asInt());
        assertEquals(9000000000L, node.get("longVal").asLong());
        assertEquals(3.14159, node.get("doubleVal").asDouble(), 0.000001);
        assertEquals(-7, node.get("negativeInt").asInt());
        assertEquals(0.0, node.get("zeroFloat").asDouble(), 0.000001);

        // round-trip 后再解析，数值仍保真
        String dumped = ConfigSerializer.toString(node, ConfigFormat.YAML);
        ConfigNode reparsed = Config.parse(dumped, ConfigFormat.YAML);
        assertEquals(42, reparsed.get("intVal").asInt());
        assertEquals(9000000000L, reparsed.get("longVal").asLong());
        assertEquals(3.14159, reparsed.get("doubleVal").asDouble(), 0.000001);
        assertEquals(-7, reparsed.get("negativeInt").asInt());
    }

    /**
     * 空值/null 处理：显式 null 与空字段都应解析为 NULL 节点。
     */
    @Test
    public void explicitNullAndEmptyFieldResolveToNullNode() throws ConfigException {
        String yaml = "explicit: null\n" +
                      "tilde: ~\n" +
                      "empty:\n";
        ConfigNode node = Config.parse(yaml, ConfigFormat.YAML);

        assertTrue("explicit null 应为 NULL 节点", node.get("explicit").isNull());
        assertTrue("tilde ~ 应为 NULL 节点", node.get("tilde").isNull());
        // 空字段在 YAML 中也解析为 null
        assertTrue("空字段应为 NULL 节点", node.get("empty").isNull());
    }

    /**
     * 空文档解析为可用的空 map 节点，不抛异常。
     */
    @Test
    public void emptyDocumentParsesToEmptyMap() throws ConfigException {
        ConfigNode node = Config.parse("", ConfigFormat.YAML);
        assertNotNull(node);
        // 空文档约定返回 MAP（与早期实现一致），便于上层按路径取值
        assertEquals(ConfigNode.NodeType.MAP, node.getType());
        assertNotNull(node.asMap());
        assertTrue(node.asMap().isEmpty());
    }

    /**
     * 纯注释文档解析为空 map。
     */
    @Test
    public void commentOnlyDocumentParsesToEmptyMap() throws ConfigException {
        String yaml = "# just a comment\n# another line\n";
        ConfigNode node = Config.parse(yaml, ConfigFormat.YAML);
        assertNotNull(node);
        assertEquals(ConfigNode.NodeType.MAP, node.getType());
        assertTrue(node.asMap().isEmpty());
    }

    /**
     * 内联 map/array 语法（flow style）应被正确解析。
     */
    @Test
    public void flowStyleMapAndListParsedCorrectly() throws ConfigException {
        String yaml = "config: {host: localhost, port: 8080}\n" +
                      "tags: [alpha, beta, gamma]\n";
        ConfigNode node = Config.parse(yaml, ConfigFormat.YAML);

        assertEquals("localhost", node.get("config.host").asString());
        assertEquals(8080, node.get("config.port").asInt());

        ConfigNode tags = node.get("tags");
        assertEquals(ConfigNode.NodeType.LIST, tags.getType());
        assertEquals(3, tags.asList().size());
        assertEquals("alpha", tags.get(0).asString());
        assertEquals("beta", tags.get(1).asString());
        assertEquals("gamma", tags.get(2).asString());
    }
}
