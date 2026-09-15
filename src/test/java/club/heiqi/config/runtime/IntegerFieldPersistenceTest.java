package club.heiqi.config.runtime;

import club.heiqi.config.ConfigException;
import club.heiqi.config.schema.ConfigSchema;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * INTEGER 字段的持久化与旧数据迁移回归。
 *
 * <p>本类钉住四件真实回归风险：</p>
 * <ol>
 *   <li><b>落盘形态</b>：INTEGER 字段写十进制整数字面量（{@code 16777216}），
 *       既有 NUMBER 字段写浮点形态（{@code 1.6777216E7}）——同一批值、两种形态，互不影响。</li>
 *   <li><b>旧值读回（跨仓迁移成败点）</b>：某键此前按 NUMBER 声明、磁盘上是科学计数法
 *       {@code 1.6777216E7} 或 {@code 1.0} 或 {@code 16777216}，改成 INTEGER 声明后
 *       必须读成对应整数值，不触发类型错误、不回落默认值、不改写磁盘。</li>
 *   <li><b>小数 / 越界语义</b>：{@code 1.5} 在磁盘与草稿两条路径都 fail-closed，不静默截断；
 *       越界按声明的 range 拒绝。</li>
 *   <li><b>既有 NUMBER 键零变化</b>：同一个值走 NUMBER 声明时读值仍是 Double、落盘仍是浮点形态。</li>
 * </ol>
 *
 * <p>走真实装配路径（{@link ConfigManager#bootstrap} → 临时文件 → {@code save}）而不是直接调
 * 编解码：落盘形态与读回形态都由这条链路共同决定，单元级断言防不住它。</p>
 */
public class IntegerFieldPersistenceTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    /** 两个 INTEGER 键 + 一个既有 NUMBER 键（后者是红线 1 的对照组）。 */
    private static ConfigSchema schema() {
        return ConfigSchema.builder("qzuilib")
                .section("server")
                    .title("Server")
                    .integer("maxQzbpBytes").defaultValue(16777216L).range(0, 2147483647)
                        .label("maxQzbpBytes").build()
                    .integer("maxPayload").defaultValue(1024L)
                        .label("maxPayload").build()
                    .number("legacyRatio").defaultValue(1.6777216E7)
                        .label("legacyRatio").build()
                .endSection()
                .build();
    }

    private static void write(File file, String content) throws Exception {
        FileWriter writer = new FileWriter(file);
        try {
            writer.write(content);
        } finally {
            writer.close();
        }
    }

    private static String readText(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    /** 取某顶层 key 所在行的 trim 形态；不存在返回 null。 */
    private static String lineOf(String text, String key) {
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith(key + ":")) {
                return trimmed;
            }
        }
        return null;
    }

    // ==================== 1. 落盘形态 ====================

    /**
     * INTEGER 默认值落盘为十进制整数字面量；同批 NUMBER 字段落盘仍是浮点形态（红线 1）。
     */
    @Test
    public void integerFieldWritesDecimalLiteralWhileNumberFieldKeepsFloatForm() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = schema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        SaveOutcome outcome = manager.save(manager.openDraft());
        assertTrue("空文件 bootstrap 后原样保存必须成功", outcome.isSuccess());

        String text = readText(file);
        assertEquals("INTEGER 字段必须是十进制整数字面量", "maxQzbpBytes: 16777216", lineOf(text, "maxQzbpBytes"));
        assertEquals("INTEGER 字段无小数点", "maxPayload: 1024", lineOf(text, "maxPayload"));
        assertEquals("既有 NUMBER 字段落盘形态逐字不变", "legacyRatio: 1.6777216E7", lineOf(text, "legacyRatio"));
    }

    // ==================== 2. 旧值读回 ====================

    /**
     * 迁移成败点：改声明前落的是科学计数法 {@code 1.6777216E7}，改声明后必须读成整数值 16777216，
     * 且再次保存即落成整数形态。
     */
    @Test
    public void scientificNotationLegacyValueReadsBackAsInteger() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file, "server:\n  maxQzbpBytes: 1.6777216E7\n");
        ConfigSchema schema = schema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        Object typed = manager.authority().get("server.maxQzbpBytes");
        assertTrue("INTEGER 字段的内存形态必须是 Long（决定落盘走 Tag.INT）", typed instanceof Long);
        assertEquals("旧科学计数法值必须读成对应整数值，不得回落默认值",
                Long.valueOf(16777216L), typed);
        assertEquals(16777216.0, manager.authority().getNumber("server.maxQzbpBytes"), 0.0);

        SaveOutcome outcome = manager.save(manager.openDraft());
        assertTrue("读回成功后保存必须成功", outcome.isSuccess());
        String text = readText(file);
        assertEquals("迁移后落盘即整数形态", "maxQzbpBytes: 16777216", lineOf(text, "maxQzbpBytes"));
        assertEquals("其它键形态不受影响", "legacyRatio: 1.6777216E7", lineOf(text, "legacyRatio"));

        ConfigManager again = ConfigManager.bootstrap(file, schema);
        assertEquals("整数形态可再读回", Long.valueOf(16777216L), again.authority().get("server.maxQzbpBytes"));
    }

    /**
     * {@code 1.0} 与纯整数 {@code 16777216} 两种旧形态都读成同一整数值。
     */
    @Test
    public void floatAndPlainIntegerLegacyValuesReadBackAsInteger() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file, "server:\n  maxQzbpBytes: 1.0\n  maxPayload: 1024\n");
        ConfigManager manager = ConfigManager.bootstrap(file, schema());

        assertEquals("1.0 是整数值 1", Long.valueOf(1L), manager.authority().get("server.maxQzbpBytes"));
        assertEquals("纯整数原样读回", Long.valueOf(1024L), manager.authority().get("server.maxPayload"));
    }

    /**
     * 超出 double 精确域（{@code 2^53}）的整数字面量按字面量精确读，不被 double 舍入。
     */
    @Test
    public void integerLiteralBeyondDoublePrecisionIsNotRounded() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file, "server:\n  maxPayload: 9007199254740993\n");
        ConfigManager manager = ConfigManager.bootstrap(file, schema());

        assertEquals("2^53+1 必须原样读回，不得被 double 舍成 9007199254740992",
                Long.valueOf(9007199254740993L), manager.authority().get("server.maxPayload"));
        assertEquals("64 位上界同样精确", Long.valueOf(9223372036854775807L),
                readBack(file, "server:\n  maxPayload: 9223372036854775807\n"));
    }

    // ==================== 3. 小数 / 越界语义 ====================

    /**
     * 磁盘上的小数值 fail-closed 抛 VALIDATION，且<b>不改写磁盘</b>（无坏文件恢复链）。
     */
    @Test
    public void fractionalValueIsRejectedOnLoadWithoutRewritingTheFile() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        String original = "server:\n  maxQzbpBytes: 1.5\n";
        write(file, original);

        try {
            ConfigManager.bootstrap(file, schema());
            Assert.fail("1.5 不是整数值，disk 严格路径必须抛 VALIDATION");
        } catch (ConfigException e) {
            assertEquals(ConfigException.Category.VALIDATION, e.category());
        }
        assertEquals("加载失败不得改写磁盘", original, readText(file));
    }

    /**
     * 超出 long 字面量范围（{@code 2^63}）在加载层退化为字符串节点 ⇒ 严格 NodeType 拒绝，
     * 不静默夹到 {@code Long.MAX_VALUE}。
     */
    @Test
    public void outOfRangeLiteralIsRejectedInsteadOfSaturated() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file, "server:\n  maxPayload: 9223372036854775808\n");
        try {
            ConfigManager.bootstrap(file, schema());
            Assert.fail("2^63 越界，必须拒绝而不是夹取");
        } catch (ConfigException e) {
            assertEquals(ConfigException.Category.VALIDATION, e.category());
        }
    }

    /**
     * 草稿路径的整数语义：{@code 1.5}（数值与 UI 原文两种形态）报错不截断，
     * 越界按 range 拒绝，非法值不推进 Authority；改回合法值即可正常保存。
     */
    @Test
    public void fractionalAndOutOfRangeDraftValuesAreRejected() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        ConfigSchema schema = schema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);
        DraftBuffer draft = manager.openDraft();

        draft.setDraft("server.maxQzbpBytes", Double.valueOf(1.5));
        String numericError = draft.error("server.maxQzbpBytes");
        assertTrue("1.5 必须报「不是整数值」，实际: " + numericError,
                numericError != null && numericError.contains("整数值"));

        draft.setDraft("server.maxQzbpBytes", "1.5");
        String textError = draft.error("server.maxQzbpBytes");
        assertTrue("UI 原文 1.5 必须报「不是有效整数」，实际: " + textError,
                textError != null && textError.contains("有效整数"));

        SaveOutcome rejected = manager.save(draft);
        assertFalse("非法草稿不得保存成功", rejected.isSuccess());
        assertEquals("拒绝的保存不得推进 Authority（不得静默截断成 1）",
                Long.valueOf(16777216L), manager.authority().get("server.maxQzbpBytes"));

        draft.setDraft("server.maxQzbpBytes", Long.valueOf(2147483648L));
        String rangeError = draft.error("server.maxQzbpBytes");
        assertTrue("越界必须报下限/上限，实际: " + rangeError,
                rangeError != null && rangeError.contains("上限"));

        draft.setDraft("server.maxQzbpBytes", Long.valueOf(2048L));
        assertFalse("合法整数不应有错误", draft.hasError());
        assertTrue("改回合法值后可正常保存", manager.save(draft).isSuccess());
        assertEquals("maxQzbpBytes: 2048", lineOf(readText(file), "maxQzbpBytes"));
    }

    // ==================== 4. 既有 NUMBER 键零变化 ====================

    /**
     * 同一个磁盘整数形态进入 NUMBER 键时仍是 Double、落盘仍是浮点形态——
     * INTEGER 的加入没有改变既有 NUMBER 通道。
     */
    @Test
    public void numberFieldKeepsDoubleSemanticsForIntegerLiteralOnDisk() throws Exception {
        File file = tempFolder.newFile("config.yaml");
        write(file, "server:\n  legacyRatio: 16777216\n");
        ConfigSchema schema = schema();
        ConfigManager manager = ConfigManager.bootstrap(file, schema);

        Object typed = manager.authority().get("server.legacyRatio");
        assertTrue("既有 NUMBER 键读值仍是 Double", typed instanceof Double);
        assertEquals(Double.valueOf(1.6777216E7), typed);

        assertTrue(manager.save(manager.openDraft()).isSuccess());
        assertEquals("既有 NUMBER 键落盘仍是浮点形态", "legacyRatio: 1.6777216E7",
                lineOf(readText(file), "legacyRatio"));
    }

    /** 每次用新临时文件 bootstrap 一次，读指定键的 typed 值。 */
    private Object readBack(File file, String content) throws Exception {
        write(file, content);
        return ConfigManager.bootstrap(file, schema()).authority().get("server.maxPayload");
    }
}
