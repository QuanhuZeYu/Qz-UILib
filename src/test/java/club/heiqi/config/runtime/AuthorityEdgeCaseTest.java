package club.heiqi.config.runtime;

import club.heiqi.config.Config;
import club.heiqi.config.ConfigException;
import club.heiqi.config.ConfigFormat;
import club.heiqi.config.ConfigNode;
import club.heiqi.config.MutableConfig;
import club.heiqi.config.schema.ConfigSchema;

import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * {@link Authority} 边界用例测试，覆盖损坏 YAML、类型不匹配、非 Schema 子树、
 * applyAll 空Map、getRaw/putRaw 对 Schema 字段等异常与边界场景。
 */
public class AuthorityEdgeCaseTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private static void write(File file, String content) throws Exception {
        FileWriter w = new FileWriter(file);
        try {
            w.write(content);
        } finally {
            w.close();
        }
    }

    /**
     * 损坏 YAML 时 load 抛 ConfigException。
     */
    @Test(expected = ConfigException.class)
    public void corruptedYamlThrowsConfigException() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file, "server: [unclosed");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority.load(file, schema);
    }

    /**
     * 部分字段缺失补默认：文件只有 server.host，server.port 返回默认值 8080。
     */
    @Test
    public void partialMissingFieldFillsDefault() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file, "server:\n  host: only.host\n");
        ConfigSchema schema = SchemaTestFactory.serverSchema();

        Authority authority = Authority.load(file, schema);

        assertEquals("only.host", authority.getString("server.host"));
        assertEquals(8080.0, authority.getNumber("server.port"), 0.0);
        assertFalse(authority.getBool("server.debug"));
        assertEquals("online", authority.getString("server.mode"));
    }

    /**
     * 字段类型不匹配：server.port 写成字符串 "abc"。
     * 验证当前行为：extractTyped 用 asDouble(0.0)，解析失败返回 0.0。
     */
    @Test
    public void typeMismatchNumberFallsBackToZero() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file, "server:\n  host: localhost\n  port: abc\n  debug: false\n  mode: online\n");
        ConfigSchema schema = SchemaTestFactory.serverSchema();

        Authority authority = Authority.load(file, schema);

        // 验证当前行为：非数值字符串解析失败回退到 0.0
        assertEquals(0.0, authority.getNumber("server.port"), 0.0);
    }

    /**
     * 嵌套非 Schema 子树保留：文件含 servers 列表，legacy().getRawJson 取到完整子树。
     */
    @Test
    public void nestedNonSchemaListSubtreePreserved() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file,
                "server:\n  host: localhost\n  port: 8080\n  debug: false\n  mode: online\n" +
                "servers:\n  - name: a\n  - name: b\n");
        ConfigSchema schema = SchemaTestFactory.serverSchema();

        Authority authority = Authority.load(file, schema);
        String raw = authority.legacy().getRawJson("servers");

        assertNotNull(raw);
        assertTrue("应含 name: " + raw, raw.contains("name"));
        assertTrue("应含 a: " + raw, raw.contains("a"));
        assertTrue("应含 b: " + raw, raw.contains("b"));
    }

    /**
     * 多层非 Schema 子树：database.pool.size 不在 schema，getRawJson("database") 取到子树。
     */
    @Test
    public void deepNonSchemaSubtreePreserved() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file,
                "server:\n  host: localhost\n  port: 8080\n  debug: false\n  mode: online\n" +
                "database:\n  pool:\n    size: 10\n    timeout: 30\n");
        ConfigSchema schema = SchemaTestFactory.serverSchema();

        Authority authority = Authority.load(file, schema);
        String raw = authority.legacy().getRawJson("database");

        assertNotNull(raw);
        assertTrue("应含 pool: " + raw, raw.contains("pool"));
        assertTrue("应含 size: " + raw, raw.contains("size"));
        assertTrue("应含 10: " + raw, raw.contains("10"));

        // 嵌套路径取标量
        String size = authority.legacy().getRawJson("database.pool.size");
        assertNotNull(size);
        assertTrue("嵌套标量应含 10: " + size, size.contains("10"));
    }

    /**
     * getString 对非 STRING 字段（NUMBER）。
     * 验证当前行为：getString 用 String.valueOf，Double 8080.0 → "8080.0"。
     */
    @Test
    public void getStringOnNumberField() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);

        // 验证当前行为：NUMBER 字段经 getString 返回 String.valueOf(Double)
        String port = authority.getString("server.port");
        assertNotNull(port);
        assertEquals(String.valueOf(8080.0), port);
    }

    /**
     * getNumber 对 BOOLEAN 字段。
     * 验证当前行为：Boolean 非 Number，getNumber 返回 0.0。
     */
    @Test
    public void getNumberOnBooleanField() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);

        // 验证当前行为：BOOLEAN 字段经 getNumber 返回 0.0
        assertEquals(0.0, authority.getNumber("server.debug"), 0.0);
    }

    /**
     * applyAll 后 snapshotTyped 反映新值。
     */
    @Test
    public void applyAllReflectedInSnapshotTyped() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);

        Map<String, Object> newValues = new HashMap<String, Object>();
        newValues.put("server.host", "new.host");
        newValues.put("server.port", 1234.0);
        authority.applyAll(newValues);

        Map<String, Object> snapshot = authority.snapshotTyped();
        assertEquals("new.host", snapshot.get("server.host"));
        assertEquals(1234.0, snapshot.get("server.port"));
    }

    /**
     * applyAll 空 Map：清空所有 typed 值。
     * 验证当前行为：applyAll(null) → typedValues=new HashMap，所有 get 返回默认回退值。
     */
    @Test
    public void applyAllNullClearsTypedValues() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);

        authority.applyAll(null);

        // 验证当前行为：清空后 getString 返回 null，getNumber 返回 0.0，getBool 返回 false
        assertNull(authority.getString("server.host"));
        assertEquals(0.0, authority.getNumber("server.port"), 0.0);
        assertFalse(authority.getBool("server.debug"));
    }

    /**
     * snapshotTyped 修改不影响 Authority（含非 Schema 子树 key）。
     */
    @Test
    public void snapshotTypedModificationDoesNotAffectAuthority() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file,
                "server:\n  host: localhost\n  port: 8080\n  debug: false\n  mode: online\n" +
                "extra:\n  key: value\n");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);

        Map<String, Object> snapshot = authority.snapshotTyped();
        snapshot.remove("server.host");
        snapshot.remove("extra");

        // authority 仍可取到
        assertEquals("localhost", authority.getString("server.host"));
        String extra = authority.legacy().getRawJson("extra");
        assertTrue("extra 仍应存在: " + extra, extra.contains("value"));
    }

    /**
     * load 后 getRaw 对 Schema 字段。
     * 验证当前行为：getRaw 对 Schema 字段返回 scalarToNode(typedValue)，非 null。
     */
    @Test
    public void getRawOnSchemaFieldReturnsScalarNode() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);

        // getRaw 包级私有，同包可访问
        ConfigNode node = authority.getRaw("server.host");
        assertNotNull(node);
        assertFalse(node.isNull());
    }

    /**
     * load 后 putRaw 对 Schema 字段路径：覆盖 typed 值。
     * 验证当前行为：putRaw 对 Schema 路径提取 typed 值覆盖原值。
     */
    @Test
    public void putRawOnSchemaFieldOverwritesTyped() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = SchemaTestFactory.serverSchema();
        Authority authority = Authority.load(file, schema);

        // 构造标量 ConfigNode
        MutableConfig mc = Config.createMutable(ConfigFormat.YAML);
        mc.set("_", "overridden");
        ConfigNode node = mc.get("_");

        authority.putRaw("server.host", node);

        // 验证当前行为：putRaw 覆盖了 typed 值
        assertEquals("overridden", authority.getString("server.host"));
    }
}
