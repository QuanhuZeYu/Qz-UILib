package club.heiqi.config;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * YAML 注释保真测试，覆盖块注释、内联注释、多行注释、嵌套注释、
 * list 元素注释、混合注释、无注释文件、中文注释、空行保留等 round-trip 场景。
 *
 * <p>所有用例走 {@link ConfigSerializer#parse(String, ConfigFormat)} +
 * {@link ConfigSerializer#toString(ConfigNode, ConfigFormat)} 的 compose/serialize 路径，
 * 验证带 {@code #} 注释的 yml 文件 round-trip 后注释不丢。</p>
 */
public class YamlCommentPreservationTest {

    /** round-trip：parse → toString → 再 parse，返回二次解析后的 ConfigNode */
    private static ConfigNode roundTrip(String yaml) throws ConfigException {
        ConfigNode first = ConfigSerializer.parse(yaml, ConfigFormat.YAML);
        String dumped = ConfigSerializer.toString(first, ConfigFormat.YAML);
        return ConfigSerializer.parse(dumped, ConfigFormat.YAML);
    }

    /** round-trip 后的文本（用于断言注释文本存在） */
    private static String roundTripText(String yaml) throws ConfigException {
        ConfigNode first = ConfigSerializer.parse(yaml, ConfigFormat.YAML);
        return ConfigSerializer.toString(first, ConfigFormat.YAML);
    }

    /**
     * 用例 1：块注释保真。
     * 节点上方的独立行注释 round-trip 后保留。
     */
    @Test
    public void blockCommentPreserved() throws ConfigException {
        String yaml = "# server config\nserver:\n  host: localhost\n";
        String out = roundTripText(yaml);
        assertTrue("块注释应保留: " + out, out.contains("server config"));

        ConfigNode reparsed = roundTrip(yaml);
        ConfigNode server = reparsed.get("server");
        assertNotNull(server);
        CommentMeta block = server.getBlockComment();
        assertNotNull("server 节点应携带块注释", block);
        assertTrue("块注释 value 应包含 server config: " + block.getValue(),
                block.getValue().contains("server config"));
    }

    /**
     * 用例 2：内联注释保真。
     * 与节点同行的 {@code #} 注释 round-trip 后保留。
     */
    @Test
    public void inlineCommentPreserved() throws ConfigException {
        String yaml = "host: localhost # server host\n";
        String out = roundTripText(yaml);
        assertTrue("内联注释应保留: " + out, out.contains("server host"));

        ConfigNode reparsed = roundTrip(yaml);
        ConfigNode host = reparsed.get("host");
        assertNotNull(host);
        CommentMeta inline = host.getInlineComment();
        assertNotNull("host 节点应携带内联注释", inline);
        assertTrue("内联注释 value 应包含 server host: " + inline.getValue(),
                inline.getValue().contains("server host"));
    }

    /**
     * 用例 3：多行块注释保真。
     * 连续多行 {@code #} 注释 round-trip 后全部保留。
     */
    @Test
    public void multilineBlockCommentPreserved() throws ConfigException {
        String yaml = "# line1\n# line2\nserver:\n  host: localhost\n";
        String out = roundTripText(yaml);
        assertTrue("多行注释 line1 应保留: " + out, out.contains("line1"));
        assertTrue("多行注释 line2 应保留: " + out, out.contains("line2"));

        ConfigNode reparsed = roundTrip(yaml);
        ConfigNode server = reparsed.get("server");
        CommentMeta block = server.getBlockComment();
        assertNotNull("server 节点应携带多行块注释", block);
        assertTrue("块注释应包含 line1: " + block.getValue(), block.getValue().contains("line1"));
        assertTrue("块注释应包含 line2: " + block.getValue(), block.getValue().contains("line2"));
    }

    /**
     * 用例 4：嵌套注释保真。
     * 子节点上方的块注释 round-trip 后保留。
     */
    @Test
    public void nestedCommentPreserved() throws ConfigException {
        String yaml = "server:\n  # database config\n  database:\n    host: db.local\n";
        String out = roundTripText(yaml);
        assertTrue("嵌套注释应保留: " + out, out.contains("database config"));

        ConfigNode reparsed = roundTrip(yaml);
        ConfigNode database = reparsed.get("server.database");
        assertNotNull(database);
        CommentMeta block = database.getBlockComment();
        assertNotNull("database 子节点应携带块注释", block);
        assertTrue("嵌套块注释应包含 database config: " + block.getValue(),
                block.getValue().contains("database config"));
    }

    /**
     * 用例 5：list 元素注释保真。
     * list 各元素上方的块注释 round-trip 后保留。
     */
    @Test
    public void listItemCommentPreserved() throws ConfigException {
        String yaml = "servers:\n  # first server\n  - name: primary\n  # second server\n  - name: replica\n";
        String out = roundTripText(yaml);
        assertTrue("list 元素注释 first server 应保留: " + out, out.contains("first server"));
        assertTrue("list 元素注释 second server 应保留: " + out, out.contains("second server"));

        ConfigNode reparsed = roundTrip(yaml);
        ConfigNode servers = reparsed.get("servers");
        assertNotNull(servers);
        assertEquals(ConfigNode.NodeType.LIST, servers.getType());
        List<ConfigNode> list = servers.asList();
        assertEquals(2, list.size());

        CommentMeta first = list.get(0).getBlockComment();
        assertNotNull("第一个 list 元素应携带块注释", first);
        assertTrue("第一个元素注释应包含 first server: " + first.getValue(),
                first.getValue().contains("first server"));

        CommentMeta second = list.get(1).getBlockComment();
        assertNotNull("第二个 list 元素应携带块注释", second);
        assertTrue("第二个元素注释应包含 second server: " + second.getValue(),
                second.getValue().contains("second server"));

        // 值也保真
        assertEquals("primary", list.get(0).get("name").asString());
        assertEquals("replica", list.get(1).get("name").asString());
    }

    /**
     * 用例 6：混合注释保真。
     * 块注释 + 内联注释 + 嵌套注释同时存在，round-trip 后全部保留。
     */
    @Test
    public void mixedCommentsPreserved() throws ConfigException {
        String yaml =
                "# top block comment\n" +
                "server:\n" +
                "  # database block comment\n" +
                "  database:\n" +
                "    host: db.local # db host inline\n" +
                "    port: 3306\n";
        String out = roundTripText(yaml);
        assertTrue("顶层块注释应保留: " + out, out.contains("top block comment"));
        assertTrue("嵌套块注释应保留: " + out, out.contains("database block comment"));
        assertTrue("内联注释应保留: " + out, out.contains("db host inline"));

        ConfigNode reparsed = roundTrip(yaml);
        assertNotNull(reparsed.get("server").getBlockComment());
        assertNotNull(reparsed.get("server.database").getBlockComment());
        assertNotNull(reparsed.get("server.database.host").getInlineComment());
    }

    /**
     * 用例 7：无注释文件不受影响。
     * 无注释的 YAML round-trip 后内容正确，不产生多余注释。
     */
    @Test
    public void noCommentFileUnaffected() throws ConfigException {
        String yaml = "server:\n  host: localhost\n  port: 8080\n";
        ConfigNode reparsed = roundTrip(yaml);

        assertEquals("localhost", reparsed.get("server.host").asString());
        assertEquals(8080, reparsed.get("server.port").asInt());

        // 无注释节点不应携带 CommentMeta
        assertNull("无注释节点不应有块注释", reparsed.get("server").getBlockComment());
        assertNull("无注释节点不应有内联注释", reparsed.get("server.host").getInlineComment());

        String out = roundTripText(yaml);
        assertFalse("无注释文件不应产生 # 注释: " + out, out.contains("#"));
    }

    /**
     * 用例 8：注释 + 值保真。
     * 读入带注释的配置，round-trip 写回后注释保留且所有值更新/保真。
     *
     * <p>注：本用例验证 round-trip 后注释与值同时保留。涉及 MutableConfig.set 修改值
     * 并保留注释的场景，因 DefaultMutableConfig 内部用 Map&lt;String,Object&gt; 存储
     * （注释元数据无法在 Map 中携带），注释会丢失——该路径的注释保留为已知遗留 TODO。</p>
     */
    @Test
    public void commentAndValuePreservedAfterRoundTrip() throws ConfigException {
        String yaml =
                "# server config\n" +
                "server:\n" +
                "  host: localhost # server host\n" +
                "  port: 8080\n";
        ConfigNode reparsed = roundTrip(yaml);

        // 注释保留
        assertNotNull("server 块注释应保留", reparsed.get("server").getBlockComment());
        assertNotNull("host 内联注释应保留", reparsed.get("server.host").getInlineComment());

        // 值保真
        assertEquals("localhost", reparsed.get("server.host").asString());
        assertEquals(8080, reparsed.get("server.port").asInt());
    }

    /**
     * 用例 9：中文注释保真。
     * 中文注释和中文值 round-trip 后保留。
     */
    @Test
    public void chineseCommentPreserved() throws ConfigException {
        String yaml = "# 服务器配置\nserver:\n  host: 本地\n";
        String out = roundTripText(yaml);
        assertTrue("中文注释应保留: " + out, out.contains("服务器配置"));
        assertTrue("中文值应保留: " + out, out.contains("本地"));

        ConfigNode reparsed = roundTrip(yaml);
        ConfigNode server = reparsed.get("server");
        CommentMeta block = server.getBlockComment();
        assertNotNull("server 节点应携带中文块注释", block);
        assertTrue("中文块注释应包含 服务器配置: " + block.getValue(),
                block.getValue().contains("服务器配置"));
        assertEquals("本地", reparsed.get("server.host").asString());
    }

    /**
     * 用例 10：空行保留。
     * 注释间有空行，round-trip 后空行保留（以 BLANK_LINE 形式）。
     */
    @Test
    public void blankLinePreserved() throws ConfigException {
        String yaml = "# line1\n\n# line2\nserver:\n  host: localhost\n";
        ConfigNode first = ConfigSerializer.parse(yaml, ConfigFormat.YAML);
        ConfigNode server = first.get("server");
        CommentMeta block = server.getBlockComment();
        assertNotNull("server 节点应携带块注释", block);
        String value = block.getValue();
        assertNotNull("块注释 value 不应为 null", value);
        assertTrue("块注释应包含 line1: " + value, value.contains("line1"));
        assertTrue("块注释应包含 line2: " + value, value.contains("line2"));
        // 空行在 value 中表现为中间的空字符串行，即 "\n\n" 出现
        assertTrue("块注释应保留空行（含 \\n\\n）: " + value, value.contains("\n\n"));

        // round-trip 文本应仍含空行（两个连续换行）
        String out = roundTripText(yaml);
        assertTrue("round-trip 文本应保留空行: " + out, out.contains("\n\n"));
    }
}
