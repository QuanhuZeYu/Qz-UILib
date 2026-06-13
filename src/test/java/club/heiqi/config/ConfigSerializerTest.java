package club.heiqi.config;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * ConfigSerializer 测试，覆盖 JSON/YAML 序列化、子树序列化、null 与嵌套结构。
 */
public class ConfigSerializerTest {

    @Test
    public void emptyConfigSerializesToNullJson() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON);
        String json = ConfigSerializer.toString(config, ConfigFormat.JSON);
        assertEquals("null", json);
    }

    @Test
    public void emptyConfigSerializesToEmptyYaml() {
        MutableConfig config = Config.createMutable(ConfigFormat.YAML);
        String yaml = ConfigSerializer.toString(config, ConfigFormat.YAML);
        assertEquals("", yaml);
    }

    @Test
    public void nullNodeSerializesAsJsonNull() {
        String json = ConfigSerializer.toString(null, ConfigFormat.JSON);
        assertEquals("null", json);
    }

    @Test
    public void nullNodeSerializesAsEmptyYaml() {
        String yaml = ConfigSerializer.toString(null, ConfigFormat.YAML);
        assertEquals("", yaml);
    }

    @Test
    public void simpleJsonSerializationContainsPrimitiveFields() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON);
        config.set("name", "Qz");
        config.set("count", 42);
        config.set("enabled", true);

        String json = ConfigSerializer.toString(config, ConfigFormat.JSON);

        assertNotNull(json);
        assertTrue("JSON 应包含 name 字段: " + json, json.contains("\"name\""));
        assertTrue(json.contains("\"Qz\""));
        assertTrue("JSON 应包含 count 字段: " + json, json.contains("\"count\""));
        assertTrue(json.contains("42"));
        assertTrue(json.contains("\"enabled\""));
        assertTrue(json.contains("true"));
    }

    @Test
    public void simpleYamlSerializationContainsPrimitiveFields() {
        MutableConfig config = Config.createMutable(ConfigFormat.YAML);
        config.set("name", "Qz");
        config.set("count", 42);
        config.set("enabled", true);

        String yaml = ConfigSerializer.toString(config, ConfigFormat.YAML);

        assertNotNull(yaml);
        assertTrue("YAML 应包含 name 键: " + yaml, yaml.contains("name:"));
        assertTrue(yaml.contains("Qz"));
        assertTrue(yaml.contains("count:"));
        assertTrue(yaml.contains("42"));
        assertTrue(yaml.contains("enabled:"));
        assertTrue(yaml.contains("true"));
    }

    @Test
    public void jsonRoundTripPreservesStructure() throws ConfigException {
        MutableConfig original = Config.createMutable(ConfigFormat.JSON);
        original.set("server.host", "localhost");
        original.set("server.port", 8080);
        original.set("debug", true);

        String json = ConfigSerializer.toString(original, ConfigFormat.JSON);
        ConfigNode reparsed = Config.parse(json, ConfigFormat.JSON);

        assertEquals("localhost", reparsed.get("server.host").asString());
        assertEquals(8080, reparsed.get("server.port").asInt());
        assertTrue(reparsed.get("debug").asBoolean());
    }

    @Test
    public void yamlRoundTripPreservesStructure() throws ConfigException {
        MutableConfig original = Config.createMutable(ConfigFormat.YAML);
        original.set("server.host", "localhost");
        original.set("server.port", 8080);

        String yaml = ConfigSerializer.toString(original, ConfigFormat.YAML);
        ConfigNode reparsed = Config.parse(yaml, ConfigFormat.YAML);

        assertEquals("localhost", reparsed.get("server.host").asString());
        assertEquals(8080, reparsed.get("server.port").asInt());
    }

    @Test
    public void subtreeSerializationProducesSubtreeText() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON);
        config.set("server.host", "localhost");
        config.set("server.port", 8080);
        config.set("debug", true);

        ConfigNode serverNode = config.get("server");
        String json = ConfigSerializer.toString(serverNode, ConfigFormat.JSON);

        assertNotNull(json);
        assertTrue("子树 JSON 应包含 host 字段: " + json, json.contains("\"host\""));
        assertTrue(json.contains("\"localhost\""));
        assertTrue(json.contains("\"port\""));
        assertTrue(json.contains("8080"));
        assertFalse("子树 JSON 不应包含 debug 字段: " + json, json.contains("\"debug\""));
    }

    @Test
    public void nestedListSerializesToJsonArray() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON);
        config.set("tags", Arrays.asList("alpha", "beta", "gamma"));

        String json = ConfigSerializer.toString(config, ConfigFormat.JSON);

        assertTrue(json.contains("\"tags\""));
        assertTrue(json.contains("\"alpha\""));
        assertTrue(json.contains("\"beta\""));
        assertTrue(json.contains("\"gamma\""));
    }

    @Test
    public void nestedMapSerializesInBothFormats() {
        Map<String, Object> inner = new LinkedHashMap<String, Object>();
        inner.put("host", "db.local");
        inner.put("port", 5432);

        MutableConfig config = Config.createMutable(ConfigFormat.JSON);
        config.set("database", inner);

        String json = ConfigSerializer.toString(config, ConfigFormat.JSON);
        String yaml = ConfigSerializer.toString(config, ConfigFormat.YAML);

        assertTrue(json.contains("\"database\""));
        assertTrue(json.contains("\"db.local\""));
        assertTrue(json.contains("5432"));
        assertTrue(yaml.contains("database:"));
        assertTrue(yaml.contains("db.local"));
        assertTrue(yaml.contains("5432"));
    }

    @Test
    public void listOfMapsSerializesCorrectly() throws ConfigException {
        Map<String, Object> first = new LinkedHashMap<String, Object>();
        first.put("name", "primary");
        first.put("value", 1);
        Map<String, Object> second = new LinkedHashMap<String, Object>();
        second.put("name", "replica");
        second.put("value", 2);

        MutableConfig config = Config.createMutable(ConfigFormat.JSON);
        config.set("servers", Arrays.asList(first, second));

        String json = ConfigSerializer.toString(config, ConfigFormat.JSON);

        assertTrue(json.contains("\"primary\""));
        assertTrue(json.contains("\"replica\""));
        // 重新解析校验结构稳定
        ConfigNode reparsed = Config.parse(json, ConfigFormat.JSON);
        List<ConfigNode> servers = reparsed.get("servers").asList();
        assertNotNull(servers);
        assertEquals(2, servers.size());
        assertEquals("primary", servers.get(0).get("name").asString());
        assertEquals(2, servers.get(1).get("value").asInt());
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullFormatThrows() {
        MutableConfig config = Config.createMutable(ConfigFormat.JSON);
        ConfigSerializer.toString(config, null);
    }

    @Test
    public void jsonFormatFromYamlConfigCrossFormatWorks() throws ConfigException {
        MutableConfig config = Config.createMutable(ConfigFormat.YAML);
        config.set("name", "Qz");
        config.set("value", 10);

        // 用 YAML 源数据序列化为 JSON 文本
        String json = ConfigSerializer.toString(config, ConfigFormat.JSON);
        ConfigNode reparsed = Config.parse(json, ConfigFormat.JSON);
        assertEquals("Qz", reparsed.get("name").asString());
        assertEquals(10, reparsed.get("value").asInt());
    }
}
