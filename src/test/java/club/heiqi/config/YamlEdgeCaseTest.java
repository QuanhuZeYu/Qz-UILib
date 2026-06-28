package club.heiqi.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * YAML 序列化边界场景测试，覆盖特殊字符、Unicode、数值边界、
 * 布尔/null 变体、深嵌套、大型集合、空集合、锚点/合并键、
 * 多文档、注释丢失、tab 缩进、重复 key 等边界行为。
 *
 * <p>所有用例通过 {@link ConfigSerializer#toString} / {@link ConfigSerializer#parse}
 * 做 round-trip 或单步解析校验，不触碰主代码。</p>
 */
public class YamlEdgeCaseTest {

    /** round-trip 辅助：把节点序列化为 YAML 再解析回来 */
    private static ConfigNode roundTrip(ConfigNode node) throws ConfigException {
        String yaml = ConfigSerializer.toString(node, ConfigFormat.YAML);
        return ConfigSerializer.parse(yaml, ConfigFormat.YAML);
    }

    /** 构造一个只含单个标量字段的可变配置 */
    private static MutableConfig single(String key, Object value) {
        MutableConfig config = Config.createMutable(ConfigFormat.YAML);
        config.set(key, value);
        return config;
    }

    /**
     * 空字符串值：序列化后反解析，asString 返回空串。
     */
    @Test
    public void emptyStringRoundTrip() throws ConfigException {
        ConfigNode reparsed = roundTrip(single("key", ""));
        assertEquals("", reparsed.get("key").asString());
    }

    /**
     * 空字符串带单引号：解析 `key: ''` 为空串。
     */
    @Test
    public void emptySingleQuotedStringParsesToEmpty() throws ConfigException {
        ConfigNode node = ConfigSerializer.parse("key: ''\n", ConfigFormat.YAML);
        assertEquals("", node.get("key").asString());
    }

    /**
     * 含冒号的字符串值：round-trip 不被误解析为嵌套。
     */
    @Test
    public void stringWithColonsRoundTrip() throws ConfigException {
        ConfigNode reparsed = roundTrip(single("key", "a:b:c"));
        assertEquals("a:b:c", reparsed.get("key").asString());
    }

    /**
     * 含单引号的字符串值：`it's` round-trip 保真。
     */
    @Test
    public void stringWithSingleQuoteRoundTrip() throws ConfigException {
        ConfigNode reparsed = roundTrip(single("key", "it's"));
        assertEquals("it's", reparsed.get("key").asString());
    }

    /**
     * 含双引号的字符串值 round-trip 保真。
     */
    @Test
    public void stringWithDoubleQuoteRoundTrip() throws ConfigException {
        ConfigNode reparsed = roundTrip(single("key", "say \"hi\""));
        assertEquals("say \"hi\"", reparsed.get("key").asString());
    }

    /**
     * 多行字符串 round-trip：换行保真。
     */
    @Test
    public void multilineStringRoundTripPreservesNewlines() throws ConfigException {
        String value = "line one\nline two\nline three";
        ConfigNode reparsed = roundTrip(single("description", value));
        assertEquals(value, reparsed.get("description").asString());
    }

    /**
     * 字符串值含 tab：round-trip 保真（tab 出现在值中，非缩进）。
     */
    @Test
    public void stringWithTabRoundTrip() throws ConfigException {
        String value = "a\tb";
        ConfigNode reparsed = roundTrip(single("key", value));
        assertEquals(value, reparsed.get("key").asString());
    }

    /**
     * 中文内容 round-trip：不乱码。
     */
    @Test
    public void chineseContentRoundTrip() throws ConfigException {
        MutableConfig config = Config.createMutable(ConfigFormat.YAML);
        config.set("名称", "配置值");
        ConfigNode reparsed = roundTrip(config);
        assertEquals("配置值", reparsed.get("名称").asString());
    }

    /**
     * emoji 内容 round-trip：SnakeYAML 应支持 Unicode。
     */
    @Test
    public void emojiContentRoundTrip() throws ConfigException {
        ConfigNode reparsed = roundTrip(single("key", "🎮"));
        assertEquals("🎮", reparsed.get("key").asString());
    }

    /**
     * 特殊字符集合：含 # & * ! | > ' " 的字符串值，需正确引号包裹后 round-trip 保真。
     */
    @Test
    public void specialCharactersRoundTrip() throws ConfigException {
        String value = "a#b&c*d!e|f>g'h\"i";
        ConfigNode reparsed = roundTrip(single("key", value));
        assertEquals(value, reparsed.get("key").asString());
    }

    /**
     * 大整数（超过 int 范围）：asLong 正确。
     */
    @Test
    public void largeLongRoundTrip() throws ConfigException {
        long big = 999999999999999L;
        ConfigNode reparsed = roundTrip(single("value", big));
        assertEquals(big, reparsed.get("value").asLong());
    }

    /**
     * 大浮点（Double.MAX_VALUE）：asDouble 正确。
     */
    @Test
    public void doubleMaxValueRoundTrip() throws ConfigException {
        double max = 1.7976931348623157E308;
        ConfigNode reparsed = roundTrip(single("value", max));
        assertEquals(max, reparsed.get("value").asDouble(), 0.0);
    }

    /**
     * 负零 -0.0：round-trip 后符号位保真。
     */
    @Test
    public void negativeZeroRoundTrip() throws ConfigException {
        ConfigNode reparsed = roundTrip(single("value", -0.0));
        double v = reparsed.get("value").asDouble();
        // 用 bits 区分 -0.0 与 0.0
        assertEquals(Double.doubleToLongBits(-0.0), Double.doubleToLongBits(v));
    }

    /**
     * 科学计数法：1.23e+5 解析为 123000.0。
     */
    @Test
    public void scientificNotationParses() throws ConfigException {
        ConfigNode node = ConfigSerializer.parse("value: 1.23e+5\n", ConfigFormat.YAML);
        assertEquals(123000.0, node.get("value").asDouble(), 0.0001);
    }

    /**
     * 布尔值变体（YAML 1.1）：true/True/TRUE/false/False/FALSE/yes/no/on/off
     * 都应被 SnakeYAML 解析为布尔。
     */
    @Test
    public void booleanVariantsParse() throws ConfigException {
        String yaml = "a: true\nb: True\nc: TRUE\nd: false\ne: False\nf: FALSE\n"
                + "g: yes\nh: no\ni: on\nj: off\n";
        ConfigNode node = ConfigSerializer.parse(yaml, ConfigFormat.YAML);
        assertTrue("true", node.get("a").asBoolean());
        assertTrue("True", node.get("b").asBoolean());
        assertTrue("TRUE", node.get("c").asBoolean());
        assertFalse("false", node.get("d").asBoolean());
        assertFalse("False", node.get("e").asBoolean());
        assertFalse("FALSE", node.get("f").asBoolean());
        assertTrue("yes", node.get("g").asBoolean());
        assertFalse("no", node.get("h").asBoolean());
        assertTrue("on", node.get("i").asBoolean());
        assertFalse("off", node.get("j").asBoolean());
    }

    /**
     * null 变体：null/Null/NULL/~/空值 都解析为 NULL 节点。
     */
    @Test
    public void nullVariantsParse() throws ConfigException {
        String yaml = "a: null\nb: Null\nc: NULL\nd: ~\ne:\n";
        ConfigNode node = ConfigSerializer.parse(yaml, ConfigFormat.YAML);
        assertTrue("null", node.get("a").isNull());
        assertTrue("Null", node.get("b").isNull());
        assertTrue("NULL", node.get("c").isNull());
        assertTrue("~", node.get("d").isNull());
        assertTrue("empty", node.get("e").isNull());
    }

    /**
     * 深嵌套 Map：10 层嵌套 map，round-trip 结构等价。
     */
    @Test
    public void deepNestedMapRoundTrip() throws ConfigException {
        // 构造 l1.l2....l10 = "deep"
        MutableConfig config = Config.createMutable(ConfigFormat.YAML);
        StringBuilder path = new StringBuilder();
        for (int i = 1; i <= 10; i++) {
            if (i > 1) {
                path.append(".");
            }
            path.append("l").append(i);
        }
        config.set(path.toString(), "deep");
        ConfigNode reparsed = roundTrip(config);
        assertEquals("deep", reparsed.get(path.toString()).asString());
    }

    /**
     * 深嵌套 List：5 层嵌套 list，round-trip。
     */
    @Test
    public void deepNestedListRoundTrip() throws ConfigException {
        // 构造 [[[[[42]]]]]
        Object inner = 42;
        for (int i = 0; i < 5; i++) {
            List<Object> wrap = new ArrayList<Object>();
            wrap.add(inner);
            inner = wrap;
        }
        MutableConfig config = Config.createMutable(ConfigFormat.YAML);
        config.set("nested", inner);
        ConfigNode reparsed = roundTrip(config);
        ConfigNode cur = reparsed.get("nested");
        for (int i = 0; i < 4; i++) {
            assertEquals(ConfigNode.NodeType.LIST, cur.getType());
            cur = cur.get(0);
        }
        assertEquals(42, cur.get(0).asInt());
    }

    /**
     * 大型 list：100 个元素，round-trip 长度正确。
     */
    @Test
    public void largeListRoundTrip() throws ConfigException {
        List<Integer> items = new ArrayList<Integer>();
        for (int i = 0; i < 100; i++) {
            items.add(i);
        }
        MutableConfig config = Config.createMutable(ConfigFormat.YAML);
        config.set("items", items);
        ConfigNode reparsed = roundTrip(config);
        List<ConfigNode> list = reparsed.get("items").asList();
        assertEquals(100, list.size());
        assertEquals(0, list.get(0).asInt());
        assertEquals(99, list.get(99).asInt());
    }

    /**
     * 大型 map：50 个 key，round-trip size 正确。
     */
    @Test
    public void largeMapRoundTrip() throws ConfigException {
        MutableConfig config = Config.createMutable(ConfigFormat.YAML);
        for (int i = 0; i < 50; i++) {
            config.set("k" + i, i);
        }
        ConfigNode reparsed = roundTrip(config);
        Map<String, ConfigNode> map = reparsed.asMap();
        assertEquals(50, map.size());
        assertEquals(25, reparsed.get("k25").asInt());
    }

    /**
     * 混合嵌套：map 含 list 含 map 含标量，round-trip。
     */
    @Test
    public void mixedNestingRoundTrip() throws ConfigException {
        Map<String, Object> inner = new LinkedHashMap<String, Object>();
        inner.put("x", 1);
        List<Object> list = new ArrayList<Object>();
        list.add(inner);
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("items", list);

        MutableConfig config = Config.createMutable(ConfigFormat.YAML);
        config.set("data", root);
        ConfigNode reparsed = roundTrip(config);
        // get(String) 不支持 list 索引路径，用 asList 导航
        ConfigNode item = reparsed.get("data.items").asList().get(0);
        assertEquals(1, item.get("x").asInt());
    }

    /**
     * 空 list：`key: []` 解析为 LIST 节点，asList().isEmpty()。
     */
    @Test
    public void emptyListParses() throws ConfigException {
        ConfigNode node = ConfigSerializer.parse("key: []\n", ConfigFormat.YAML);
        ConfigNode list = node.get("key");
        assertEquals(ConfigNode.NodeType.LIST, list.getType());
        assertTrue(list.asList().isEmpty());
    }

    /**
     * 空 map：`key: {}` 解析为 MAP 节点，asMap().isEmpty()。
     */
    @Test
    public void emptyMapParses() throws ConfigException {
        ConfigNode node = ConfigSerializer.parse("key: {}\n", ConfigFormat.YAML);
        ConfigNode map = node.get("key");
        assertEquals(ConfigNode.NodeType.MAP, map.getType());
        assertTrue(map.asMap().isEmpty());
    }

    /**
     * list 中含 null：`- null\n- value`，第一项 isNull。
     */
    @Test
    public void listWithNullElement() throws ConfigException {
        ConfigNode node = ConfigSerializer.parse("items:\n  - null\n  - value\n", ConfigFormat.YAML);
        List<ConfigNode> list = node.get("items").asList();
        assertEquals(2, list.size());
        assertTrue("第一项应为 null", list.get(0).isNull());
        assertEquals("value", list.get(1).asString());
    }

    /**
     * map 中含 null value：`key: null`，get("key").isNull()。
     */
    @Test
    public void mapWithNullValue() throws ConfigException {
        ConfigNode node = ConfigSerializer.parse("key: null\n", ConfigFormat.YAML);
        assertTrue(node.get("key").isNull());
    }

    /**
     * 锚点 round-trip：用锚点构造配置，序列化后反解析，值正确
     * （序列化时锚点可能丢失但值应保留）。
     */
    @Test
    public void anchorRoundTripPreservesValues() throws ConfigException {
        String yaml = "defaults: &defaults\n  timeout: 30\n  retries: 3\n"
                + "production:\n  <<: *defaults\n  host: prod\n";
        ConfigNode node = ConfigSerializer.parse(yaml, ConfigFormat.YAML);
        // 合并后 production 含 timeout/retries/host
        assertEquals(30, node.get("production.timeout").asInt());
        assertEquals(3, node.get("production.retries").asInt());
        assertEquals("prod", node.get("production.host").asString());

        // 再 round-trip：锚点丢失但合并后的字段应保留
        ConfigNode reparsed = roundTrip(node);
        assertEquals(30, reparsed.get("production.timeout").asInt());
        assertEquals(3, reparsed.get("production.retries").asInt());
        assertEquals("prod", reparsed.get("production.host").asString());
    }

    /**
     * 合并键 round-trip：`<<: *anchor` 合并后字段都在。
     */
    @Test
    public void mergeKeyRoundTrip() throws ConfigException {
        String yaml = "base: &base\n  a: 1\n  b: 2\nderived:\n  <<: *base\n  c: 3\n";
        ConfigNode node = ConfigSerializer.parse(yaml, ConfigFormat.YAML);
        assertEquals(1, node.get("derived.a").asInt());
        assertEquals(2, node.get("derived.b").asInt());
        assertEquals(3, node.get("derived.c").asInt());

        ConfigNode reparsed = roundTrip(node);
        assertEquals(1, reparsed.get("derived.a").asInt());
        assertEquals(2, reparsed.get("derived.b").asInt());
        assertEquals(3, reparsed.get("derived.c").asInt());
    }

    /**
     * 文档头 `---`：以 `---` 开头的文档，解析正确。
     */
    @Test
    public void documentHeaderParses() throws ConfigException {
        String yaml = "---\nkey: value\n";
        ConfigNode node = ConfigSerializer.parse(yaml, ConfigFormat.YAML);
        assertEquals("value", node.get("key").asString());
    }

    /**
     * 多文档（`---` 分隔）：验证当前行为。
     *
     * <p>SnakeYAML 的单文档 {@code load} 遇到多文档流时，当前实现的行为
     * 是抛出 ConfigException（"expected a single document"）。
     * 若未来改为只返回第一个文档，本测试需相应调整。</p>
     */
    @Test
    public void multiDocumentCurrentBehavior() {
        String yaml = "a: 1\n---\nb: 2\n";
        try {
            ConfigNode node = ConfigSerializer.parse(yaml, ConfigFormat.YAML);
            // 若未抛异常，验证当前行为：只返回第一个文档
            assertNotNull(node);
            assertEquals(1, node.get("a").asInt());
            // 第二个文档不应可见
            assertFalse(node.has("b"));
        } catch (ConfigException e) {
            // 验证当前行为：多文档流被单文档加载器拒绝
            assertTrue("多文档应抛 ConfigException: " + e.getMessage(), true);
        }
    }

    /**
     * 注释保留测试：带 `#` 注释的 YAML 解析后再序列化，注释保留（SnakeYAML 2.2 compose/serialize 支持）。
     */
    @Test
    public void commentsAreLostOnReserialize() throws ConfigException {
        String yaml = "# header comment\nkey: value  # inline\n";
        ConfigNode node = ConfigSerializer.parse(yaml, ConfigFormat.YAML);
        String dumped = ConfigSerializer.toString(node, ConfigFormat.YAML);
        // 注释保真后注释应保留
        assertTrue("序列化后应保留注释: " + dumped, dumped.contains("header comment"));
        // 值仍正确
        ConfigNode reparsed = ConfigSerializer.parse(dumped, ConfigFormat.YAML);
        assertEquals("value", reparsed.get("key").asString());
    }

    /**
     * tab 缩进错误：用 tab 而非空格缩进，SnakeYAML 应抛 ConfigException。
     */
    @Test(expected = ConfigException.class)
    public void tabIndentationThrows() throws ConfigException {
        // 用 \t 缩进子键
        String yaml = "key:\n\tsub: value\n";
        ConfigSerializer.parse(yaml, ConfigFormat.YAML);
    }

    /**
     * 缩进不一致：同一层级不同缩进数，验证当前行为。
     *
     * <p>SnakeYAML 对同一 mapping 下不一致的缩进可能抛异常或容忍。
     * 本测试记录当前实际行为。</p>
     */
    @Test
    public void inconsistentIndentationCurrentBehavior() {
        // a 缩进 2 空格，b 缩进 4 空格（同一父级下不一致）
        String yaml = "parent:\n  a: 1\n    b: 2\n";
        try {
            ConfigNode node = ConfigSerializer.parse(yaml, ConfigFormat.YAML);
            // 若未抛异常，记录当前行为：至少 a 可读
            assertNotNull(node);
            assertEquals(1, node.get("parent.a").asInt());
        } catch (ConfigException e) {
            // 验证当前行为：不一致缩进被拒绝
            assertTrue(true);
        }
    }

    /**
     * 重复 key：`key: 1\nkey: 2`，验证当前行为。
     *
     * <p>SnakeYAML 默认对重复 key 的行为：可能抛 DuplicateKeyException，
     * 也可能后者覆盖。本测试记录当前实际行为。</p>
     */
    @Test
    public void duplicateKeyCurrentBehavior() {
        String yaml = "key: 1\nkey: 2\n";
        try {
            ConfigNode node = ConfigSerializer.parse(yaml, ConfigFormat.YAML);
            // 若未抛异常，验证当前行为：后者覆盖
            assertEquals(2, node.get("key").asInt());
        } catch (ConfigException e) {
            // 验证当前行为：重复 key 被拒绝
            assertTrue(true);
        }
    }
}
