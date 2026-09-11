package club.heiqi.config.ui.field;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.VisualAdapter;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.ScenePickerPanel;
import club.heiqi.uilib.ui.scene.control.search.PickerDensityPreference;

/**
 * 密度偏好装配点源码守卫（防「新增面板构建点忘记接线」与「注入面被静默摘除」）。
 *
 * <p>运行期效力由 {@code PickerDensityPanelWiringTest} 负责（真挂载 + 真列数对拍）；
 * 本类只补两条静态钉子：</p>
 * <ol>
 *   <li><b>构建点全接线</b>：{@link SearchPickerFieldSupport} 内每一处
 *       {@code ScenePickerPanel.Props.builder(} 都必须配一处
 *       {@code .densityPreference(PickerDensityPreferenceSource.installed())}，
 *       且构建点数 &gt;= 2（正锚：否则本守卫在 ∅ 上打转）；</li>
 *   <li><b>缺省仍是 AUTO</b>：面板 Props 未设置偏好时为 {@code null}（P5 语义 = AUTO），
 *       显式 {@code densityPreference(null)} 亦然 —— 这就是「未接线与现状逐值一致」的 API 级钉子；
 *       同时面板必须保留信号型注入面与派生处的消费点。</li>
 * </ol>
 */
public class SearchPickerDensityWiringGuardTest {

    private static final Path SUPPORT = Paths.get(
            "src/main/java/club/heiqi/config/ui/field/SearchPickerFieldSupport.java");
    private static final Path PANEL = Paths.get(
            "src/main/java/club/heiqi/uilib/ui/scene/control/ScenePickerPanel.java");

    private static final String BUILD_SITE = "ScenePickerPanel.Props.builder(";
    private static final String WIRING = ".densityPreference(PickerDensityPreferenceSource.installed())";

    /** 构建点与接线必须一一对应（新增构建点即红，须显式接线并说明理由）。 */
    @Test
    public void everyPanelBuildSiteWiresThePreferenceSource() throws Exception {
        String code = codeWithoutComments(read(SUPPORT));
        int buildSites = count(code, BUILD_SITE);
        int wirings = count(code, WIRING);
        Assert.assertTrue("构建点数骤降说明提取式子失效（实测 2：SINGLE_VALUE + LIST_MEMBERS）：" + buildSites,
                buildSites >= 2);
        Assert.assertEquals("面板构建点必须全部接线（未接线 = 面板恒 AUTO，用户拿不到三档密度）",
                buildSites, wirings);
    }

    /** 缺省面：未设置 / 显式 null 的偏好都必须读回 null（面板按 AUTO）。 */
    @Test
    public void unsetPreferenceReadsBackAsNullMeaningAuto() {
        ScenePickerPanel.Props unset = ScenePickerPanel.Props.builder(
                Signal.<String>create(""),
                (ReadableSignal<SearchPickerData.SearchResult>) SearchPickerData.SearchResult::empty,
                Signal.create(Boolean.TRUE), query -> { }, selection -> { }, visualAdapter()).build();
        Assert.assertNull("未设置偏好 = null = 面板按 AUTO（与接线前逐值一致）", unset.densityPreference());

        ScenePickerPanel.Props explicitNull = ScenePickerPanel.Props.builder(
                Signal.<String>create(""),
                (ReadableSignal<SearchPickerData.SearchResult>) SearchPickerData.SearchResult::empty,
                Signal.create(Boolean.TRUE), query -> { }, selection -> { }, visualAdapter())
                .densityPreference(null).build();
        Assert.assertNull("显式 null 同样 = AUTO", explicitNull.densityPreference());
    }

    /** 注入面与消费点必须都在（信号型属性，不是构造期常量）。 */
    @Test
    public void panelKeepsSignalTypedInjectionSurfaceAndConsumption() throws Exception {
        String panel = codeWithoutComments(read(PANEL));
        Assert.assertTrue("Props 必须保留密度偏好只读访问器",
                panel.contains("ReadableSignal<PickerDensityPreference> densityPreference()"));
        Assert.assertTrue("Builder 必须保留密度偏好信号注入面",
                panel.contains("Builder densityPreference(ReadableSignal<PickerDensityPreference> value)"));
        Assert.assertTrue("面板派生必须消费该偏好",
                panel.contains("props.densityPreference()"));
        Assert.assertTrue("无偏好时必须回落 AUTO（P5 语义）",
                panel.contains("PickerDensityPreference.AUTO"));
    }

    // ==================== 助手 ====================

    private static VisualAdapter visualAdapter() {
        return new VisualAdapter() {
            @Override
            public String candidateLabel(SearchPickerData.Candidate candidate) {
                return candidate.label();
            }

            @Override
            public String variantLabel(SearchPickerData.Variant variant) {
                return variant.label();
            }
        };
    }

    private static int count(String source, String token) {
        int hits = 0;
        int index = source.indexOf(token);
        while (index >= 0) {
            hits++;
            index = source.indexOf(token, index + token.length());
        }
        return hits;
    }

    private static String read(Path path) throws IOException {
        Assert.assertTrue("扫描输入不存在，守卫会空转：" + path, Files.isRegularFile(path));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    /** 剥离注释（守卫只对真实代码生效）。 */
    private static String codeWithoutComments(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int index = 0;
        int length = source.length();
        while (index < length) {
            char current = source.charAt(index);
            if (current == '/' && index + 1 < length && source.charAt(index + 1) == '/') {
                while (index < length && source.charAt(index) != '\n') {
                    index++;
                }
            } else if (current == '/' && index + 1 < length && source.charAt(index + 1) == '*') {
                index += 2;
                while (index + 1 < length && !(source.charAt(index) == '*' && source.charAt(index + 1) == '/')) {
                    index++;
                }
                index += 2;
            } else if (current == '"') {
                out.append(current);
                index++;
                while (index < length && source.charAt(index) != '"') {
                    if (source.charAt(index) == '\\') {
                        out.append(source.charAt(index));
                        index++;
                    }
                    if (index < length) {
                        out.append(source.charAt(index));
                        index++;
                    }
                }
                if (index < length) {
                    out.append(source.charAt(index));
                    index++;
                }
            } else {
                out.append(current);
                index++;
            }
        }
        return out.toString();
    }
}
