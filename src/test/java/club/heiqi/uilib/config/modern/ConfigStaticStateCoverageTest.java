package club.heiqi.uilib.config.modern;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.Config;
import club.heiqi.uilib.font.config.FontConfig;

/**
 * 配置静态面夹具覆盖率守卫（R1 / T2-5，2026-09-04）。
 *
 * <p>三个配置测试各手写一份「保存-恢复」清单，逐行同构。手写清单的病灶本轮实测到了：
 * 三份**一致漏掉同 4 个**可变静态字段（{@code widthCacheMissBudgetPerWindow}、
 * {@code glyphInkPadding}、{@code atlasTextureScale}、{@code missingFontSort}），
 * 而这 4 个正被别的测试内联改写（靠各自的 {@code finally} 自觉兜底）——
 * 漏盖 + 内联改写就是跨类状态泄漏的真实敞口。teardown 也已经漂了：
 * {@code ConfigValueBridgeTest} 少一句 {@code FontConfig.onConfigReload()}，
 * 而 {@code ModernConfigBootstrapTest} 的注释明言它是"防 last* 跨测试漂移"所必需。</p>
 *
 * <p>本守卫不替手写清单负责，只负责让它<b>无法再漏</b>：把"三份恢复清单的并集"与
 * "反射枚举出的 public static 非 final 全集"对齐。新增字段而忘了登记 = 直接红。</p>
 */
public class ConfigStaticStateCoverageTest {

    private static final Path FIXTURES = Paths
            .get("src/test/java/club/heiqi/uilib/config/modern");

    /** 受检的三份手写夹具。 */
    private static final String[] FIXTURE_FILES = {
            "ConfigValueBridgeTest.java",
            "ModernConfigBootstrapTest.java",
            "ConfigSaveListenerTest.java",
    };

    /** 每个夹具的 teardown 都必须以这两个刷新调用收尾（顺序也要求：先派生态后 last*）。 */
    private static final String[] REQUIRED_TEARDOWNS = {
            "FontConfig.refreshDerivedRuleSet();",
            "FontConfig.onConfigReload();",
    };

    @Test
    public void fixtureUnionMustCoverEveryMutableStaticField() throws IOException {
        Set<String> mutable = new TreeSet<String>();
        addMutableStatics(mutable, FontConfig.class);
        addMutableStatics(mutable, Config.class);
        Assert.assertTrue("反射没枚举到可变静态字段，说明判据本身失效：" + mutable.size(), mutable.size() >= 25);

        Set<String> covered = coveredByFixtures();
        Assert.assertTrue("恢复清单并集过小，说明提取式子失效：" + covered.size(), covered.size() >= 25);

        Set<String> missing = new TreeSet<String>(mutable);
        missing.removeAll(covered);
        Assert.assertTrue("以下 public static 非 final 字段没有任何夹具在保存/恢复它，"
                + "被测试内联改写后就是跨类状态泄漏（要么登记进三份夹具，要么改成 final）：" + missing,
                missing.isEmpty());
    }

    @Test
    public void everyFixtureMustRefreshBothDerivedStateAndLastSnapshot() throws IOException {
        for (String file : FIXTURE_FILES) {
            String source = new String(Files.readAllBytes(FIXTURES.resolve(file)), StandardCharsets.UTF_8);
            for (String call : REQUIRED_TEARDOWNS) {
                Assert.assertTrue(file + " 的 teardown 缺少 " + call
                        + "（只恢复 public 而不同步派生态/last* 快照，会让下一批测试看到陈旧比较基线）",
                        source.contains(call));
            }
        }
    }

    private static void addMutableStatics(Set<String> sink, Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            int mod = field.getModifiers();
            if (Modifier.isPublic(mod) && Modifier.isStatic(mod) && !Modifier.isFinal(mod)
                    && !field.isSynthetic()) {
                sink.add(type.getSimpleName() + "." + field.getName());
            }
        }
    }

    /** 从三份夹具的恢复段里抽出 "ClassName.field = saveXxx;" 形式的字段名。 */
    private static Set<String> coveredByFixtures() throws IOException {
        Set<String> covered = new HashSet<String>();
        Pattern pattern = Pattern.compile("\\b(FontConfig|Config)\\.([A-Za-z0-9_]+)\\s*=\\s*save[A-Za-z0-9_]+;");
        for (String file : FIXTURE_FILES) {
            String source = new String(Files.readAllBytes(FIXTURES.resolve(file)), StandardCharsets.UTF_8);
            Matcher matcher = pattern.matcher(source);
            while (matcher.find()) {
                covered.add(matcher.group(1) + "." + matcher.group(2));
            }
        }
        return covered;
    }
}
