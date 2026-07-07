package club.heiqi.config.runtime;

import java.io.File;
import java.io.FileWriter;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import club.heiqi.config.schema.ConfigSchema;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * SIMPLE_LIST 字段的 {@link Authority} 持久化往返测试（D6，最隐蔽 bug 防护）。
 *
 * <p>原 extractTyped 的 default 分支 {@code node.asString()} 会把 LIST 节点压成字符串
 * （如 {@code "[a, b]"}），导致读回后类型与默认值类型不一致、往返丢数据。
 * 修复后 {@code case SIMPLE_LIST} 从 {@code node.asList()} 提取 {@code List<String>}。</p>
 */
public class SimpleListAuthorityTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /**
     * 文件含 list 值时，load 读回得 {@code List<String>}（而非压成字符串）。
     */
    @Test
    public void loadReturnsListNotFlattenedString() throws Exception {
        File file = tempFolder.newFile("simplelist.yaml");
        write(file,
                "font:\n" +
                "  sort:\n" +
                "    - alpha\n" +
                "    - beta\n" +
                "    - gamma\n");
        ConfigSchema schema = ConfigSchema.builder("t")
                .section("font")
                    .simpleList("sort").label("Sort").build()
                .endSection()
                .build();

        Authority authority = Authority.load(file, schema);
        Object value = authority.<Object>get("font.sort");

        assertNotNull("font.sort 非 null", value);
        assertTrue("load 读回应是 List，而非被压成 String（实际: "
                + (value == null ? "null" : value.getClass().getName()) + ")",
                value instanceof List);
        @SuppressWarnings("unchecked")
        List<String> list = (List<String>) value;
        assertEquals("list 含 3 项", 3, list.size());
        assertEquals("list 内容保序", java.util.Arrays.asList("alpha", "beta", "gamma"), list);
    }

    /**
     * 空文件 + SIMPLE_LIST schema：load 补默认空 list（非 null、非字符串）。
     */
    @Test
    public void emptyFileFillsEmptyListDefault() throws Exception {
        File file = tempFolder.newFile("simplelist-empty.yaml");
        write(file, "");
        ConfigSchema schema = ConfigSchema.builder("t")
                .section("font")
                    .simpleList("sort").label("Sort").build()
                .endSection()
                .build();

        Authority authority = Authority.load(file, schema);
        Object value = authority.<Object>get("font.sort");

        assertNotNull("font.sort 非 null", value);
        assertTrue("补默认应是 List（实际: "
                + (value == null ? "null" : value.getClass().getName()) + ")",
                value instanceof List);
        assertEquals("补默认空 list size=0", 0, ((List<?>) value).size());
    }

    private static void write(File file, String content) throws Exception {
        FileWriter w = new FileWriter(file);
        try {
            w.write(content);
        } finally {
            w.close();
        }
    }
}
