package club.heiqi.uilib.ui.scene.text.font;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 数学文字模式的真实正文族；aliases 只选择候选，整形与光栅继续由 FontTextService 完成。 每个名称以一个中性字族候选为首；fallback
 * 只来自公开工厂明确传入的配置列表。 固定来源、字节 SHA256、完整 OFL 与上游元数据见同目录资源 manifest.json。
 */
public final class MathTextFontSources {
    public static final String ROMAN = "math-text-roman";
    public static final String SANS = "math-text-sans";
    public static final String MONO = "math-text-mono";
    private static final String BASE = "assets/qz_uilib/fonts/math-text/";

    private MathTextFontSources() {}

    /**
     * 创建独立资源代际。资源重载者用新 revision 创建 sources，再通过服务 register 逐名称发布； register 的正式失效通知负责重新布局。旧布局始终持有原
     * source 的不可变字节/轴事实。
     */
    public static Map<String, List<FontSource>> namedSources(long revision) {
        Map<String, List<FontSource>> result = new LinkedHashMap<String, List<FontSource>>();
        result.put(
                ROMAN, Collections.singletonList(staticFamily(ROMAN, "roman/NotoSerif", revision)));
        result.put(SANS, Collections.singletonList(staticFamily(SANS, "sans/NotoSans", revision)));
        result.put(
                MONO, Collections.singletonList(staticFamily(MONO, "mono/IBMPlexMono", revision)));
        return Collections.unmodifiableMap(result);
    }

    /**
     * 公开完整候选链：三族分别在首位，配置默认列表原顺序作为显式 tail。 这是发布时的不可变链，不在每次调用时追加 fallback；register 可完整覆盖它。
     * 默认列表换代时，来源提供者以新 revision 重建并 register 发布所依赖的具名链。
     */
    public static Map<String, List<FontSource>> namedSources(
            long revision, List<FontSource> defaults) {
        Objects.requireNonNull(defaults, "defaults");
        Map<String, List<FontSource>> result = new LinkedHashMap<String, List<FontSource>>();
        for (Map.Entry<String, List<FontSource>> entry : namedSources(revision).entrySet()) {
            List<FontSource> chain = new ArrayList<FontSource>(entry.getValue());
            for (FontSource source : defaults)
                chain.add(Objects.requireNonNull(source, "candidate"));
            result.put(entry.getKey(), Collections.unmodifiableList(chain));
        }
        return Collections.unmodifiableMap(result);
    }

    private static FontSource staticFamily(String alias, String path, long revision) {
        return FontSource.family(
                alias,
                Arrays.asList(
                        resource(alias + "-regular", revision, path + "-Regular.ttf"),
                        resource(alias + "-bold", revision, path + "-Bold.ttf"),
                        resource(alias + "-italic", revision, path + "-Italic.ttf"),
                        resource(alias + "-bold-italic", revision, path + "-BoldItalic.ttf")));
    }

    private static FontSource resource(String id, long revision, String path) {
        // Latin 以及 Noto 的 Greek/Cyrillic 已由 MathTextFontScenario 真实 FT/HB 核验。
        // 复杂脚本能力不从 cmap 覆盖推断；其候选仅来自显式配置 tail。
        return path.startsWith("mono/")
                ? FontSource.fromResource(id, revision, BASE + path, false, "Latn")
                : FontSource.fromResource(id, revision, BASE + path, false, "Latn", "Grek", "Cyrl");
    }
}
