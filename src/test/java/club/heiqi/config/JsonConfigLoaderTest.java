package club.heiqi.config;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * JSON 配置加载器测试
 */
public class JsonConfigLoaderTest {

    @Test
    public void testLoadSimpleJson() throws ConfigException {
        String json = "{\"name\": \"test\", \"value\": 42, \"enabled\": true}";
        ConfigNode node = Config.parse(json, ConfigFormat.JSON);

        assertNotNull(node);
        assertEquals(ConfigNode.NodeType.MAP, node.getType());
        assertEquals("test", node.get("name").asString());
        assertEquals(42, node.get("value").asInt());
        assertTrue(node.get("enabled").asBoolean());
    }

    @Test
    public void testLoadNestedJson() throws ConfigException {
        String json = "{\"server\": {\"host\": \"localhost\", \"port\": 8080}}";
        ConfigNode node = Config.parse(json, ConfigFormat.JSON);

        assertEquals("localhost", node.get("server.host").asString());
        assertEquals(8080, node.get("server.port").asInt());
    }

    @Test
    public void testLoadJsonArray() throws ConfigException {
        String json = "{\"items\": [1, 2, 3, 4, 5]}";
        ConfigNode node = Config.parse(json, ConfigFormat.JSON);

        ConfigNode items = node.get("items");
        assertEquals(ConfigNode.NodeType.LIST, items.getType());
        assertEquals(5, items.asList().size());
        assertEquals(1, items.get(0).asInt());
        assertEquals(5, items.get(4).asInt());
    }

    @Test
    public void testLoadComplexJson() throws ConfigException {
        String json = "{\n" +
                "  \"database\": {\n" +
                "    \"host\": \"localhost\",\n" +
                "    \"port\": 3306,\n" +
                "    \"credentials\": {\n" +
                "      \"username\": \"admin\",\n" +
                "      \"password\": \"secret\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"features\": [\"auth\", \"logging\", \"cache\"],\n" +
                "  \"debug\": false\n" +
                "}";

        ConfigNode node = Config.parse(json, ConfigFormat.JSON);

        assertEquals("localhost", node.get("database.host").asString());
        assertEquals(3306, node.get("database.port").asInt());
        assertEquals("admin", node.get("database.credentials.username").asString());
        assertEquals("secret", node.get("database.credentials.password").asString());
        
        ConfigNode features = node.get("features");
        assertEquals(3, features.asList().size());
        assertEquals("auth", features.get(0).asString());
        
        assertFalse(node.get("debug").asBoolean());
    }

    @Test
    public void testMissingKey() throws ConfigException {
        String json = "{\"name\": \"test\"}";
        ConfigNode node = Config.parse(json, ConfigFormat.JSON);

        ConfigNode missing = node.get("nonexistent");
        assertTrue(missing.isNull());
        assertEquals("default", missing.asString("default"));
        assertEquals(100, missing.asInt(100));
    }

    @Test
    public void testHasMethod() throws ConfigException {
        String json = "{\"name\": \"test\", \"nested\": {\"value\": 42}}";
        ConfigNode node = Config.parse(json, ConfigFormat.JSON);

        assertTrue(node.has("name"));
        assertTrue(node.has("nested.value"));
        assertFalse(node.has("nonexistent"));
        assertFalse(node.has("nested.missing"));
    }

    @Test
    public void testDefaultValues() throws ConfigException {
        String json = "{\"text\": \"hello\"}";
        ConfigNode node = Config.parse(json, ConfigFormat.JSON);

        assertEquals("hello", node.get("text").asString("default"));
        assertEquals("default", node.get("missing").asString("default"));
        assertEquals(42, node.get("missing").asInt(42));
        assertEquals(3.14, node.get("missing").asDouble(3.14), 0.001);
        assertTrue(node.get("missing").asBoolean(true));
    }

    @Test
    public void testNumberConversions() throws ConfigException {
        String json = "{\"int\": 42, \"double\": 3.14, \"string\": \"100\"}";
        ConfigNode node = Config.parse(json, ConfigFormat.JSON);

        assertEquals(42, node.get("int").asInt());
        assertEquals(42L, node.get("int").asLong());
        assertEquals(42.0, node.get("int").asDouble(), 0.001);

        assertEquals(3, node.get("double").asInt());
        assertEquals(3.14, node.get("double").asDouble(), 0.001);

        assertEquals(100, node.get("string").asInt());
        assertEquals(100L, node.get("string").asLong());
    }

    @Test(expected = ConfigException.class)
    public void testInvalidNumberConversion() throws ConfigException {
        String json = "{\"text\": \"not_a_number\"}";
        ConfigNode node = Config.parse(json, ConfigFormat.JSON);
        node.get("text").asInt();
    }
}
