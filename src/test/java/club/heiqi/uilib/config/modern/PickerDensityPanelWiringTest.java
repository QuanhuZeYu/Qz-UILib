package club.heiqi.uilib.config.modern;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.schema.SearchPickerSpec;
import club.heiqi.config.schema.ValueSpec;
import club.heiqi.config.ui.editor.Codec;
import club.heiqi.config.ui.editor.CurrentValuePresenter;
import club.heiqi.config.ui.editor.Registry;
import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.ValueEditorProvider;
import club.heiqi.config.ui.editor.VisualAdapter;
import club.heiqi.config.ui.field.PickerDensityPreferenceSource;
import club.heiqi.config.ui.field.SearchPickerFieldSupport;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.search.PickerDensityPreference;
import club.heiqi.uilib.ui.scene.control.search.PickerMetrics;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 密度偏好全链路守卫（配置项 → 进程级信号 → 接线缝 → 面板装配点 → 面板生效档位）。
 *
 * <p>被测装配点是生产路径本身：{@link SearchPickerFieldSupport#createControlledIfPresent}
 * （字段侧唯一的面板构建点），偏好经 {@link PickerDensityPreferenceSource} 注入
 * {@code ScenePickerPanel.Props.Builder.densityPreference(...)}。观测面是面板左导航的
 * <b>生效档位文案</b>（{@code presentation.densityLabel() + " " + metrics.density().name()} ——
 * P5 U-P5-1 的既有只读出口，直接说明面板采用了哪一档），期望值统一取 P5 派生内核
 * {@link PickerMetrics#derive} 的 oracle（同一逻辑盒 / 字号倍率 / 成员带）。</p>
 *
 * <p>覆盖交付要求 ⑤①-④：①未接线 = AUTO 且与 AUTO 派生逐值一致（P5 现状）；
 * ②三档均被面板采用；③改配置无需重开面板即生效；④非法值回落 AUTO 且面板不崩。
 * 另加两条反空跑判据：三档在参考视口必须可区分（否则②③是空断言）、重开面板仍读同一进程信号。</p>
 */
public class PickerDensityPanelWiringTest {

    private static final int W = 1920;
    private static final int H = 1080;
    private static final String EDITOR_ID = "test:density";

    private SceneRuntime rt;
    private SceneLayoutEngine layoutEngine;
    private SceneNode sceneRoot;
    private MountHandle mountHandle;
    private SceneNode fieldRoot;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        rt = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
        sceneRoot = new SceneNode();
        rt.__setViewportLogicalBox(W, H);
        // 每个用例都从"未接线 + AUTO"基线开始（全局信号/接线缝双向清理）
        PickerDensityPreferenceSource.release();
        PickerDensityPreferences.resetForTest();
        rt.flush();
    }

    @After
    public void tearDown() {
        if (mountHandle != null) {
            mountHandle.dispose();
        }
        rt.dispose();
        PickerDensityPreferenceSource.release();
        PickerDensityPreferences.resetForTest();
        ReactiveScheduler.get().reset();
    }

    // ==================== ① 未接线 = AUTO = P5 现状 ====================

    /** 未接线时面板按 AUTO 处理：生效档位必须与 AUTO 派生的 P5 现状逐值一致。 */
    @Test
    public void unwiredSourceKeepsAutoAndMatchesP5Derivation() {
        SceneNode panel = mountAndOpenPanel();
        PickerMetrics expected = oracle(PickerDensityPreference.AUTO);
        Assert.assertEquals("未接线必须落 AUTO 解析出的档位（1920x1080 → 标准档）",
                densityStatus(expected), densityStatusText(panel));
        Assert.assertEquals("未接线列数必须与 AUTO 派生逐值一致",
                expected.grid().columns(), mountedColumns(panel));
    }

    /** 显式接线的 AUTO 与未接线不可区分（同一 AUTO 派生口径）。 */
    @Test
    public void wiredAutoTierIsIndistinguishableFromUnwired() {
        installProductionSource();
        PickerDensityPreferences.applyConfigured("auto");
        SceneNode panel = mountAndOpenPanel();
        Assert.assertEquals(densityStatus(oracle(PickerDensityPreference.AUTO)), densityStatusText(panel));
    }

    // ==================== ② 三档生效 ====================

    /** 配置为 compact / standard / roomy 时，同一面板随即采用对应档。 */
    @Test
    public void configuredTiersAreAdoptedByPanel() {
        installProductionSource();
        SceneNode panel = mountAndOpenPanel();
        assertTierAdopted(panel, PickerDensityPreference.COMPACT, "compact");
        assertTierAdopted(panel, PickerDensityPreference.STANDARD, "standard");
        assertTierAdopted(panel, PickerDensityPreference.ROOMY, "roomy");
    }

    /** 反空跑：参考视口下三档派生的几何必须互不相同（否则上面的"采用对应档"是空断言）。 */
    @Test
    public void tiersAreDistinguishableAtReferenceViewport() {
        int compact = oracle(PickerDensityPreference.COMPACT).grid().columns();
        int standard = oracle(PickerDensityPreference.STANDARD).grid().columns();
        int roomy = oracle(PickerDensityPreference.ROOMY).grid().columns();
        Assert.assertTrue("紧凑档列数必须多于标准档（compact=" + compact + ", standard=" + standard + "）",
                compact > standard);
        Assert.assertTrue("宽松档列数必须少于标准档（standard=" + standard + ", roomy=" + roomy + "）",
                standard > roomy);
    }

    // ==================== ③ 改配置无需重开面板 ====================

    /** 配置变更后（保存/热更路径的同一入口）密度即时跟随：不重开面板、不重建卡片。 */
    @Test
    public void tierChangeAppliesWithoutReopeningPanel() {
        installProductionSource();
        PickerDensityPreferences.applyConfigured("standard");
        SceneNode panel = mountAndOpenPanel();
        SceneOverlayHost.Entry entry = rt.getOverlayHost().bottomFirst().get(0);

        assertTierAdopted(panel, PickerDensityPreference.ROOMY, "roomy");
        Assert.assertSame("改档位不得重开面板（overlay 条目同一实例）",
                entry, rt.getOverlayHost().bottomFirst().get(0));
        Assert.assertSame("改档位不得重建面板卡片", panel, panelCard());
        Assert.assertEquals("改档位期间面板必须保持打开", 1, rt.getOverlayHost().size());
    }

    /** 关掉面板再打开：仍读同一条进程级信号（偏好不随面板生命周期重建）。 */
    @Test
    public void reopenedPanelStillReadsTheSameProcessSignal() {
        installProductionSource();
        SceneNode panel = mountAndOpenPanel();
        assertTierAdopted(panel, PickerDensityPreference.ROOMY, "roomy");
        pressKey(SceneKey.ESCAPE);
        Assert.assertTrue("ESC 应关闭面板", rt.getOverlayHost().isEmpty());

        pressKey(SceneKey.ENTER);
        layoutAll();
        layoutAll();
        Assert.assertEquals("重开面板应沿用已生效档位", 1, rt.getOverlayHost().size());
        assertTierAdopted(panelCard(), PickerDensityPreference.ROOMY, "roomy");
    }

    // ==================== ④ 非法值回落 ====================

    /** 配置里的非法档位回落 AUTO，面板保持可用（不崩、不空档）。 */
    @Test
    public void invalidConfiguredNameFallsBackToAutoWithoutBreakingPanel() {
        installProductionSource();
        PickerDensityPreferences.applyConfigured("compact");
        SceneNode panel = mountAndOpenPanel();
        assertTierAdopted(panel, PickerDensityPreference.COMPACT, "compact");

        applyConfigured("bogus");
        PickerMetrics expected = oracle(PickerDensityPreference.AUTO);
        Assert.assertEquals("非法档位必须回落 AUTO 解析出的档位",
                densityStatus(expected), densityStatusText(panel));
        Assert.assertEquals("非法档位必须回落 AUTO 的几何", expected.grid().columns(), mountedColumns(panel));
    }

    // ==================== 回归钉子：结果网格必须吃 P5 派生度量 ====================

    /**
     * 结果网格的列数必须等于 P5 派生度量的列数（三档逐值对拍）。
     *
     * <p>这条钉子的由来：面板内容是在 portal 打开的那一次 flush 内构建的，下游
     * {@code SearchResultList.create} 紧接着<b>同步</b>读一次度量投影；若投影是
     * {@code Computed.create(Supplier)}（首帧 flush 前 {@code get()} 恒为 null），网格会永久落到
     * 回退分支 —— 列数按 {@code GridProps.cellWidth} 推算、图位边长 0、此后不再订阅度量通道。
     * 现象极隐蔽：面板盒 / 顶栏 / 信息条 / 行预算都按密度派生正确，唯独网格不吃密度。</p>
     */
    @Test
    public void resultGridColumnsComeFromDerivedMetrics() {
        installProductionSource();
        SceneNode panel = mountAndOpenPanel();
        PickerDensityPreference[] tiers = {PickerDensityPreference.COMPACT,
                PickerDensityPreference.STANDARD, PickerDensityPreference.ROOMY};
        for (PickerDensityPreference tier : tiers) {
            applyConfigured(tier.name().toLowerCase(java.util.Locale.ROOT));
            PickerMetrics expected = oracle(tier);
            Assert.assertEquals("网格列数必须等于派生度量列数（tier=" + tier + "）",
                    expected.grid().columns(), mountedColumns(panel));
        }
    }

    // ==================== 面板装配与观测助手 ====================

    /** 生产接线：与 {@code ModernConfigBootstrap.bootstrapAndApply} 内安装的是同一条信号。 */
    private static void installProductionSource() {
        PickerDensityPreferenceSource.install(PickerDensityPreferences.signal());
    }

    /** 走配置面写入口 + 帧末 flush（宿主帧语义），随后重布局。 */
    private void applyConfigured(String configuredName) {
        PickerDensityPreferences.applyConfigured(configuredName);
        rt.flush();
        layoutAll();
    }

    /** 写配置 + 帧末生效，然后断言面板采用的档位与实测列数（同一条装配链，不重开面板）。 */
    private void assertTierAdopted(SceneNode panel, PickerDensityPreference preference, String configuredName) {
        applyConfigured(configuredName);
        PickerMetrics expected = oracle(preference);
        Assert.assertEquals("配置 " + configuredName + " → 面板生效档位",
                densityStatus(expected), densityStatusText(panel));
        Assert.assertEquals("配置 " + configuredName + " → 面板实测列数",
                expected.grid().columns(), mountedColumns(panel));
    }

    /** P5 派生内核 oracle（同一逻辑盒 / 字号倍率 / 无成员带），读数是面板侧唯一真值来源。 */
    private PickerMetrics oracle(PickerDensityPreference preference) {
        return PickerMetrics.derive(rt, W, H, rt.getFontScalePercent(), preference, -1);
    }

    /** 生效档位文案（面板左导航状态行）。 */
    private static String densityStatus(PickerMetrics metrics) {
        return "Density " + metrics.density().name();
    }

    /**
     * 挂载后实测：结果网格首行单元数 = 面板实际采用的列数。
     *
     * <p>这是几何证据：档位必须真的走到结果网格，而不只是被 Props 收下。度量通道的回归见
     * {@link #resultGridColumnsComeFromDerivedMetrics()}。</p>
     */
    private static int mountedColumns(SceneNode panel) {
        SceneNode rowsContainer = rowsContainer(panel);
        Assert.assertFalse("首行必须有单元（候选数不足会让本断言假绿）",
                rowsContainer.__getChildren().isEmpty());
        return rowsContainer.__getChildren().get(0).__getChildren().size();
    }

    /** 结构定位（同 {@code SearchPickerPanelWiringTest} 的既有口径）：
     *  中栏 children = [错误行, 空态占位, stackHost, 信息条]；stackHost[0] = viewport；
     *  viewport[0] = content；content = [topSpacer, rowsContainer, bottomSpacer]。 */
    private static SceneNode rowsContainer(SceneNode panel) {
        SceneNode viewport = panel.__getChildren().get(1).__getChildren().get(1)
                .__getChildren().get(2).__getChildren().get(0);
        return viewport.__getChildren().get(0).__getChildren().get(1);
    }

    /** 从面板子树里读回生效档位文案。 */
    private static String densityStatusText(SceneNode panel) {
        List<String> texts = new ArrayList<String>();
        collectTexts(panel, texts);
        for (String text : texts) {
            if (text != null && text.startsWith("Density ")) {
                return text;
            }
        }
        Assert.fail("面板内未找到密度状态行；实际文本=" + texts);
        return null;
    }

    private static void collectTexts(SceneNode node, List<String> sink) {
        if (node.getText() != null && !node.getText().isEmpty()) {
            sink.add(node.getText());
        }
        for (SceneNode child : node.__getChildren()) {
            collectTexts(child, sink);
        }
    }

    /** 挂载字段壳并打开面板，返回面板卡片节点。 */
    private SceneNode mountAndOpenPanel() {
        Signal<Object> value = Signal.<Object>create("before");
        Registry registry = pickerRegistry();
        mountHandle = rt.mount(sceneRoot, () -> {
            fieldRoot = SearchPickerFieldSupport.createControlledIfPresent(rt,
                    ValueSpec.string().withWidget(new SearchPickerSpec(EDITOR_ID, 64)),
                    value, registry, value::set);
            return fieldRoot;
        });
        rt.flush();
        rt.requestFocus(fieldRoot.__getChildren().get(0));
        pressKey(SceneKey.ENTER);
        layoutAll();
        layoutAll();
        Assert.assertEquals("行触发器 Enter 应打开面板", 1, rt.getOverlayHost().size());
        return panelCard();
    }

    /** 面板卡片节点：overlay root（透明 scrim）的 children[0]。 */
    private SceneNode panelCard() {
        return rt.getOverlayHost().bottomFirst().get(0).getRoot().__getChildren().get(0);
    }

    private void layoutAll() {
        layoutEngine.layout(sceneRoot, new Constraints(W, H));
        for (SceneOverlayHost.Entry entry : rt.getOverlayHost().bottomFirst()) {
            layoutEngine.layout(entry.getRoot(), new Constraints(W, H));
        }
        rt.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        rt.flush();
    }

    private void pressKey(SceneKey key) {
        InputFrameBuilder builder = new InputFrameBuilder(0, 0);
        builder.push(RawInputEvent.ofKey(key, SceneKeyAction.PRESSED, false, false, false, false, 0, 0, 1000L));
        rt.route(sceneRoot, builder.drainFrame(), 0, 0);
        rt.flush();
    }

    // ==================== 夹具 ====================

    /** 300 个候选：确保结果网格首行单元数足以反映真实列数（不因候选不足而截断）。 */
    private static Registry pickerRegistry() {
        Registry registry = new Registry();
        registry.register(new ValueEditorProvider() {
            @Override
            public String id() {
                return EDITOR_ID;
            }

            @Override
            public Codec codec() {
                return new Codec() {
                    @Override
                    public SearchPickerData.Selection decode(Object raw) {
                        return new SearchPickerData.Selection(String.valueOf(raw),
                                SearchPickerData.SelectionMode.ALL, Collections.<String>emptyList());
                    }

                    @Override
                    public Object encode(Object current, SearchPickerData.Selection selected) {
                        return selected.candidateKey();
                    }
                };
            }

            @Override
            public VisualAdapter visualAdapter() {
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

            @Override
            public SearchFunction searchFunction() {
                return (query, max) -> new SearchPickerData.SearchResult(candidates());
            }

            @Override
            public CurrentValuePresenter currentValuePresenter() {
                return value -> new CurrentValuePresenter.Presentation(String.valueOf(value), "summary", null);
            }
        });
        registry.freeze();
        return registry;
    }

    private static List<SearchPickerData.Candidate> candidates() {
        List<SearchPickerData.Candidate> out = new ArrayList<SearchPickerData.Candidate>(300);
        for (int i = 0; i < 300; i++) {
            out.add(new SearchPickerData.Candidate("key-" + i, "Candidate " + i,
                    Collections.<SearchPickerData.Variant>emptyList()));
        }
        return out;
    }
}
