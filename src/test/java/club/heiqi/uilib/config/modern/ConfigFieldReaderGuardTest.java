package club.heiqi.uilib.config.modern;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Test;

/**
 * 「配置面上有键 &lt;=&gt; 主源码里有人读」守卫（R1 负向锁，2026-09-04 内聚轮）。
 *
 * <p>立项依据是两个真实假开关：customInvCountFont 自 05584e8f 引入起全历史零读取者
 * （从写下那天就没实现过）；drawStageUploadBatchSize 的读取者在 b7888236 被删、键与滑块
 * 留下（改算法时漏收配置面）。两者都「能拖、会落盘、会回灌、什么都不做」，而当时
 * <b>没有任何一条断言</b>拦得住 —— 本测试就是那道闸：下一次「加了开关没接线」或
 * 「拆了消费点忘了收键」会直接变红。</p>
 *
 * <p>三条判据分别覆盖三种腐坏方向：回灌了但 schema 没声明这个键（半截接线）、
 * 有键有回灌但零读取者（假开关）、白名单里的项已经有读取者（僵尸白名单，逼着删条目）。</p>
 *
 * <p>「读取者」两条来源：类外的 {@code ClassName.field} 引用，或类内的<b>真读取</b>
 * （喂派生态、被 getter 返回）。只看前者会把 {@code characterFontRules} 这类字段误判成假开关，
 * 故 {@link #hasInternalLiveRead} 与 {@link #countReaders} 必须同时为 0 才成立。</p>
 */
public class ConfigFieldReaderGuardTest {

    private static final Path MAIN_ROOT = Paths.get("src/main/java");

    private static final Path SCHEMA = MAIN_ROOT
            .resolve(Paths.get("club/heiqi/uilib/config/modern/QzUiLibModernSchema.java"));

    private static final Path BRIDGE = MAIN_ROOT
            .resolve(Paths.get("club/heiqi/uilib/config/modern/ConfigValueBridge.java"));

    /** 受检落点类：{源文件相对路径, 类名}。配置回灌只有这两个目标。 */
    private static final String[][] TARGETS = {
            {"club/heiqi/uilib/font/config/FontConfig.java", "FontConfig"},
            {"club/heiqi/uilib/Config.java", "Config"},
    };

    /**
     * 已知假开关白名单。<b>只能在撤键时同步删除；新增一条等于承认又造了个假开关。</b>
     * 撤键清单见 docs/开发者文档/架构审查/2026-09-04-内聚项增量扫描.md 的 T1-1
     * （用户已裁两条都撤，走 D-4 那趟 breaking 车）。
     */
    private static final Set<String> ACCEPTED_DEAD_SWITCHES = new HashSet<String>(Arrays.asList(
            "FontConfig.customInvCountFont",
            "FontConfig.drawStageUploadBatchSize"));

    @Test
    public void everyBridgedConfigFieldIsReadOutsideItsOwnClass() throws IOException {
        Set<String> declaredKeys = declaredSchemaKeys();
        String bridgeSource = read(BRIDGE);
        List<String> deadSwitches = new ArrayList<String>();
        List<String> misaligned = new ArrayList<String>();

        for (String[] target : TARGETS) {
            Path ownerFile = MAIN_ROOT.resolve(Paths.get(target[0]));
            String className = target[1];
            for (String field : publicStaticNonFinalFields(read(ownerFile))) {
                String id = className + "." + field;
                if (!writesField(bridgeSource, className, field)) {
                    // 不在本闸范围：没有配置面的静态字段（代码级旋钮、只读缓存位）不判
                    continue;
                }
                if (!bridgedFromKey(bridgeSource, className, field)) {
                    // 派生写入不判：例如 fontSortConfigured 是「fontSort 是否非空」的结果，
                    // 由 Bridge 写但没有自己的 schema 键，属正常设计（见 ConfigValueBridge:149-155 注释）
                    continue;
                }
                if (!declaredKeys.contains(field)) {
                    misaligned.add(id + "（Bridge 回灌了，schema 却没声明这个键）");
                } else if (countReaders(className, field, ownerFile) == 0
                        && !hasInternalLiveRead(read(ownerFile), field)) {
                    if (!ACCEPTED_DEAD_SWITCHES.contains(id)) {
                        deadSwitches.add(id);
                    }
                } else if (ACCEPTED_DEAD_SWITCHES.contains(id)) {
                    misaligned.add(id + "（白名单称它是假开关，但它已有读取者：请从白名单删）");
                }
            }
        }
        Assert.assertTrue("有键、有回灌、主源码零读取者 = 用户可拖可存却什么都不做的假开关："
                + deadSwitches + "（要新增豁免必须先经用户裁定；撤键后请同步删白名单条目）",
                deadSwitches.isEmpty());
        Assert.assertTrue("回灌与键声明不对齐：" + misaligned, misaligned.isEmpty());
    }

    /**
     * 反空跑地板：扫描输入与两条提取式子都必须真的在工作，否则上面的断言是在 ∅ 上打转。
     * （本轮 T2-1 刚因同类问题挨掉一条规则，范式沿用。）
     */
    @Test
    public void scanInputsAndExtractorsMustNotRunOnAir() throws IOException {
        for (Path path : new Path[] {SCHEMA, BRIDGE}) {
            Assert.assertTrue("扫描输入不存在，本守卫会空转：" + path, Files.isRegularFile(path));
        }
        for (String[] target : TARGETS) {
            Path ownerFile = MAIN_ROOT.resolve(Paths.get(target[0]));
            Assert.assertTrue("目标类源码不存在：" + ownerFile, Files.isRegularFile(ownerFile));
            Assert.assertTrue(target[1] + " 的可回灌字段集为空说明提取式子失效",
                    publicStaticNonFinalFields(read(ownerFile)).size() >= 2);
        }
        Assert.assertTrue("schema 键数骤降说明提取式子失效（实测 24）：" + declaredSchemaKeys().size(),
                declaredSchemaKeys().size() >= 20);
        Assert.assertTrue("Bridge 写点数骤降说明提取式子失效（实测 21）：" + countBridgeWrites(),
                countBridgeWrites() >= 15);
    }

    /**
     * 声明类内部是否存在<b>真读取</b>：字段被喂进派生态构建或被 getter 返回时，外部虽然没有
     * {@code ClassName.field} 形式的引用，它依然是活的。
     *
     * <p>这条判据是踩过才知道的：{@code characterFontRules} 全仓只有一处 {@code FontConfig.}
     * 限定引用（Bridge 的写入），看起来像假开关，实际在 {@code FontConfig.refreshDerivedRuleSet}
     * 里被 {@code FontCharacterRuleSet.parse(...)} 消化、并有 getter 对外供给。同一形状的误判
     * 我在扫描阶段对 {@code spaceWidth} / {@code characterSpacing} 也犯过一次。
     * 排除项即"自我复制型出现"：声明行、last* 镜像（含比较与重抄）、摘要拼接行（带 {@code ="}）。</p>
     */
    private static boolean hasInternalLiveRead(String ownerSource, String field) {
        Pattern declaration = Pattern.compile("public\\s+static\\s+(?!final)[^;]*\\b" + Pattern.quote(field) + "\\b\\s*[=;]");
        String mirror = "last" + Character.toUpperCase(field.charAt(0)) + field.substring(1);
        for (String line : ownerSource.split("\r?\n")) {
            if (!line.contains(field)) {
                continue;
            }
            if (line.contains(mirror) || line.contains("=\"") || declaration.matcher(line).find()) {
                continue;
            }
            return true;
        }
        return false;
    }

    /** schema 声明的字段名：builder 链上每个方法调用的首参字符串。 */
    private static Set<String> declaredSchemaKeys() throws IOException {
        Set<String> keys = new HashSet<String>();
        Matcher matcher = Pattern.compile("\\.\\w+\\(\\s*\"([A-Za-z0-9_]+)\"").matcher(read(SCHEMA));
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    /** 声明类里全部 public static 非 final 字段名。 */
    private static Set<String> publicStaticNonFinalFields(String ownerSource) {
        Set<String> fields = new HashSet<String>();
        Matcher matcher = Pattern
                .compile("public\\s+static\\s+(?!final)[A-Za-z0-9_\\[\\]<>]+\\s+([A-Za-z0-9_]+)\\s*[=;]")
                .matcher(ownerSource);
        while (matcher.find()) {
            fields.add(matcher.group(1));
        }
        return fields;
    }

    /**
     * 该字段的回灌是否<b>由配置键驱动</b>：赋值语句右侧出现形如 "section.field" 的键路径字面量。
     *
     * <p>没有它就不算配置面的一部分（派生标志位如 {@code fontSortConfigured} 由别的键推导而来），
     * 拿它去要求 schema 有同名键会产生误报。</p>
     */
    private static boolean bridgedFromKey(String bridgeSource, String className, String field) {
        return Pattern.compile(Pattern.quote(className + "." + field) + "\\s*=[^;\"]*\"[A-Za-z0-9_.]*\\."
                + Pattern.quote(field) + "\"").matcher(bridgeSource).find();
    }

    private static boolean writesField(String bridgeSource, String className, String field) {
        return Pattern.compile(Pattern.quote(className + "." + field) + "\\s*=").matcher(bridgeSource).find();
    }

    private static int countBridgeWrites() throws IOException {
        Matcher matcher = Pattern.compile("\\b(?:FontConfig|Config)\\.[A-Za-z0-9_]+\\s*=").matcher(read(BRIDGE));
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    /**
     * 主源码里读取该字段的文件数（不含声明类自身，也不含 Bridge 这个唯一写入方）。
     *
     * <p>Java 访问他类静态字段必须带类名限定，故 "ClassName.field" 子串扫描不漏读；
     * 赋值目标不算读取，用 lookingAt 就地判定。</p>
     */
    private static int countReaders(String className, String field, Path ownerFile) throws IOException {
        final String access = className + "." + field;
        final Pattern assignmentHere = Pattern.compile(Pattern.quote(access) + "\\s*=[^=]");
        final List<Path> candidates = new ArrayList<Path>();
        try (Stream<Path> walked = Files.walk(MAIN_ROOT)) {
            walked.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.equals(ownerFile))
                    .filter(path -> !path.equals(BRIDGE))
                    .forEach(candidates::add);
        }
        int readers = 0;
        for (Path file : candidates) {
            String source = read(file);
            int index = -1;
            boolean reads = false;
            while ((index = source.indexOf(access, index + 1)) >= 0 && !reads) {
                reads = !assignmentHere.matcher(source.substring(index)).lookingAt();
            }
            if (reads) {
                readers++;
            }
        }
        return readers;
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
