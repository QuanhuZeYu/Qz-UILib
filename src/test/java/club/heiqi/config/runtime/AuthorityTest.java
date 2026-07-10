package club.heiqi.config.runtime;

import club.heiqi.config.ConfigException;
import club.heiqi.config.ConfigNode;
import club.heiqi.config.schema.ConfigSchema;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/**
 * {@link Authority} 测试，覆盖启动加载补默认、typed 读取、非 Schema 透传、
 * applyAll 替换、snapshotTyped 深拷贝。
 */
public class AuthorityTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /**
     * 空文件 + schema，load 后各字段返回默认值。
     */
    @Test
    public void emptyFileFillsDefaults() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();

        Authority authority = Authority.load(file, schema);

        assertEquals("localhost", authority.getString("server.host"));
        assertEquals(8080.0, authority.getNumber("server.port"), 0.0);
        assertFalse(authority.getBool("server.debug"));
        assertEquals("online", authority.getString("server.mode"));
    }

    /**
     * 文件不存在时同样补默认。
     */
    @Test
    public void missingFileFillsDefaults() throws Exception {
        File file = new File(tempFolder.getRoot(), "nonexistent.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();

        Authority authority = Authority.load(file, schema);

        assertEquals("localhost", authority.getString("server.host"));
        assertEquals(8080.0, authority.getNumber("server.port"), 0.0);
    }

    /**
     * 文件含值时 typed get 各类型正确。
     */
    @Test
    public void typedGetReturnsFileValues() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file,
                "server:\n" +
                "  host: 1.2.3.4\n" +
                "  port: 1234\n" +
                "  debug: true\n" +
                "  mode: offline\n");
        ConfigSchema schema = SchemaTestFactory.serverSchema();

        Authority authority = Authority.load(file, schema);

        assertEquals("1.2.3.4", authority.getString("server.host"));
        assertEquals(1234.0, authority.getNumber("server.port"), 0.0);
        assertTrue(authority.getBool("server.debug"));
        assertEquals("offline", authority.getString("server.mode"));
    }

    /**
     * 非 Schema 顶层 key 原样保留，legacy().getRawJson 能取到子树。
     */
    @Test
    public void nonSchemaKeyPreservedAsRawSubtree() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file,
                "server:\n" +
                "  host: localhost\n" +
                "  port: 8080\n" +
                "  debug: false\n" +
                "  mode: online\n" +
                "extra:\n" +
                "  nested:\n" +
                "    value: hello\n" +
                "    count: 5\n");
        ConfigSchema schema = SchemaTestFactory.serverSchema();

        Authority authority = Authority.load(file, schema);
        String raw = authority.legacy().getRawJson("extra");

        assertNotNull(raw);
        assertTrue("应包含 nested: " + raw, raw.contains("nested"));
        assertTrue("应包含 hello: " + raw, raw.contains("hello"));

        // 嵌套路径取标量
        String nested = authority.legacy().getRawJson("extra.nested.value");
        assertTrue("嵌套标量应包含 hello: " + nested, nested.contains("hello"));
    }

    /**
     * applyAll 替换后 get 返回新值。
     */
    @Test
    public void applyAllReplacesTypedValues() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);

        Map<String, Object> newValues = new HashMap<String, Object>();
        newValues.put("server.host", "0.0.0.0");
        newValues.put("server.port", 9999.0);
        newValues.put("server.debug", true);
        newValues.put("server.mode", "test");
        authority.applyAll(newValues);

        assertEquals("0.0.0.0", authority.getString("server.host"));
        assertEquals(9999.0, authority.getNumber("server.port"), 0.0);
        assertTrue(authority.getBool("server.debug"));
        assertEquals("test", authority.getString("server.mode"));
    }

    /**
     * snapshotTyped 返回深拷贝，修改 snapshot 不影响 authority。
     */
    @Test
    public void snapshotTypedIsIndependentCopy() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);

        Map<String, Object> snapshot = authority.snapshotTyped();
        snapshot.put("server.host", "modified");
        snapshot.remove("server.port");

        assertEquals("localhost", authority.getString("server.host"));
        assertEquals(8080.0, authority.getNumber("server.port"), 0.0);
    }

    /**
     * 标量字段 getRawJson 返回 YAML 文本。
     */
    @Test
    public void scalarFieldGetRawJsonReturnsYaml() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file,
                "server:\n" +
                "  host: example.com\n" +
                "  port: 8080\n" +
                "  debug: false\n" +
                "  mode: online\n");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);

        String hostYaml = authority.legacy().getRawJson("server.host");
        assertNotNull(hostYaml);
        assertTrue("标量应包含 example.com: " + hostYaml, hostYaml.contains("example.com"));
    }

    /** get 对容器与 ConfigNode 均返回防御副本，调用方不能持有内部别名。 */
    @Test
    @SuppressWarnings("unchecked")
    public void getDefensivelyCopiesContainersAndConfigNodes() throws Exception {
        File file = tempFolder.newFile("aliases.yaml");
        write(file,
                "server:\n  tags:\n    - a\n    - b\n  host: localhost\n" +
                "extra:\n  nested:\n    value: stable\n");
        Authority authority = Authority.load(file, SchemaTestFactory.listSchema());

        List<String> first = authority.get("server.tags");
        first.add("injected");
        assertEquals(Arrays.asList("a", "b"), authority.get("server.tags"));

        ConfigNode firstNode = authority.get("extra");
        ConfigNode secondNode = authority.get("extra");
        assertNotNull(firstNode);
        assertNotNull(secondNode);
        assertNotSame(firstNode, secondNode);
        assertEquals("stable", secondNode.get("nested.value").asString());

        Map<String, Object> replacement = new HashMap<String, Object>();
        List<String> source = new ArrayList<String>(Arrays.asList("x"));
        replacement.put("server.tags", source);
        authority.applyAll(replacement);
        source.add("source-mutation");
        assertEquals(Arrays.asList("x"), authority.get("server.tags"));
    }

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
}
