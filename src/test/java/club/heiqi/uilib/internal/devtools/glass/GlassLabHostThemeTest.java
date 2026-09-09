package club.heiqi.uilib.internal.devtools.glass;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend;
import club.heiqi.uilib.ui.scene.paint.RecordingRenderBackend.RenderCall;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * {@link GlassLabHost} 的 G18 外壳主题化契约测试。
 *
 * <p>覆盖（任务单 §G18 + 契约 §9）：</p>
 * <ul>
 *   <li>默认路径：采样场面板 = PANEL 配方、探针卡/参数台/诊断卡 = GROUP 配方、
 *       标题/说明文字 = 主题前景/次要前景（六属性逐项引用配方值，不硬编码色号）；</li>
 *   <li>P-04「换主题→变」：themeSignal 切换后外壳表面与文字重派生、节点身份不变、
 *       订阅数不增长；显式局部主题（{@link SceneThemes#withTheme}）优先于 runtime 默认
 *       且不外泄到外壳；无滤镜档（{@link SceneTheme#withoutBackdrop()}）仍可读、不写 backdrop；</li>
 *   <li>P-04「换主题→不变」反向钉（§7.3 诊断材质样本保留）：SAMPLE_COLORS 色带逐值不变、
 *       材质/blur/饱和/圆角/液态强度受控源不变、三档对照 backdrop 矩形几何逐值不变、
 *       旧语义玻璃 tint 面不因主题改变；</li>
 *   <li>P-01 泄漏守卫：宿主销毁后外壳绑定全部回收；</li>
 *   <li>源码守卫：静态取色/旧接缝禁绝 token + 主题接缝正向钉计数。</li>
 * </ul>
 */
public class GlassLabHostThemeTest {

    private static final int CANVAS_WIDTH = 1280;
    private static final int CANVAS_HEIGHT = 800;
    /** 采样场色带显式样本（GlassLabHost.SAMPLE_COLORS 的测试镜像，§7.3 保留项）。 */
    private static final int[] SAMPLE_COLORS = {
            0xFFEC4899, 0xFF38BDF8, 0xFFFBBF24, 0xFF22C55E, 0xFFA855F7,
            0xFFF97316, 0xFF06B6D4, 0xFF84CC16, 0xFFEF4444, 0xFF6366F1,
    };
    /** 玻璃面 tint（GlassLabHost.GLASS_TINT 的测试镜像，旧语义对照样本）。 */
    private static final int GLASS_TINT = 0x26FFFFFF;
    /** drawSurface 的填充色参数下标。 */
    private static final int FILL_COLOR_ARG = 4;
    /** 浮点比较容差。 */
    private static final float EPSILON = 0.0001F;

    /** 库默认主题（宿主 runtime 默认档）：外壳外观断言的唯一来源。 */
    private static final SceneTheme DARK = SceneThemes.DEFAULT;
    /** 浅色档：主题切换断言用（与深色档各角色配方/语义色均不同）。 */
    private static final SceneTheme LIGHT = SceneTheme.liquidGlassLight();

    private GlassLabHost host;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        host = new GlassLabHost(new FixedTextMeasurer(), null);
    }

    @After
    public void tearDown() {
        host.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 辅助 ====================

    /** 角色配方取值：断言引用配方而不是硬编码色号，主题集中调参时本类自动跟随。 */
    private static SceneSurfaceStyle role(SceneTheme theme, SceneTheme.Role role) {
        return theme.surface(role);
    }

    /** 表面六属性 = 角色配方（idle 档）：底色/缘色/边框宽/圆角/实体高度/滤镜（同 G16/外壳口径）。 */
    private static void assertSurfaceRecipe(String where, SceneSurfaceStyle style, SceneNode node) {
        Assert.assertEquals(where + " 底色 = 角色 idle tint",
                style.getIdle().getTint(), node.getBackgroundColor());
        Assert.assertEquals(where + " 边框色 = 角色 idle edge",
                style.getIdle().getEdge(), node.getBorderColor());
        Assert.assertEquals(where + " 边框宽 = 角色配方", style.getBorderWidth(), node.getBorderWidth());
        Assert.assertEquals(where + " 圆角 = 角色配方", style.getCornerRadius(), node.getCornerRadius());
        Assert.assertEquals(where + " 实体高度 = 角色 idle elevation",
                style.getIdle().getElevation(), node.__getSurfaceElevation(), EPSILON);
        Assert.assertNotNull(where + " 配方必须带滤镜", style.getBackdrop());
        Assert.assertNotNull(where + " 滤镜必须已写入节点", node.getBackdrop());
        Assert.assertEquals(where + " 滤镜材质 = 角色配方",
                style.getBackdrop().getEffect().getMaterial(), node.getBackdrop().getEffect().getMaterial());
        Assert.assertEquals(where + " 滤镜模糊半径 = 角色配方",
                style.getBackdrop().getBlurRadius(), node.getBackdrop().getBlurRadius());
    }

    /** 渲染并 flush（同 GlassLabHostTest 口径）。 */
    private void renderAndFlush(RecordingRenderBackend target) {
        host.__getRuntime().flush();
        host.render(CANVAS_WIDTH, CANVAS_HEIGHT, target, 0, 0);
        host.__getRuntime().flush();
    }

    /** 期望的 20 条 chip 显式样本色（与宿主取色式同构：row*5+i 模 10）。 */
    private static int[] expectedChipColors() {
        int[] out = new int[20];
        for (int rowIndex = 0; rowIndex < 2; rowIndex++) {
            for (int i = 0; i < SAMPLE_COLORS.length; i++) {
                out[rowIndex * SAMPLE_COLORS.length + i] =
                        SAMPLE_COLORS[(rowIndex * 5 + i) % SAMPLE_COLORS.length];
            }
        }
        return out;
    }

    // ==================== ① 默认路径 = 主题配方 ====================

    /**
     * 默认工厂路径：stage=PANEL、probeCard/参数台/诊断卡=GROUP 六属性逐项等于配方值；
     * 场景根保留 §7.3 遮罩口径的显式不透明承托底（不参与主题）；文字取主题语义前景、字号不回归。
     */
    @Test
    public void shellSurfacesConsumeThemeRecipesByDefault() {
        host.__getRuntime().flush();

        assertSurfaceRecipe("采样场 stage", role(DARK, SceneTheme.Role.PANEL), host.__getStage());
        assertSurfaceRecipe("探针卡", role(DARK, SceneTheme.Role.GROUP), host.__getProbeCard());
        assertSurfaceRecipe("参数台卡", role(DARK, SceneTheme.Role.GROUP), host.__getControlsCard());
        assertSurfaceRecipe("诊断卡", role(DARK, SceneTheme.Role.GROUP), host.__getDiagnosticsCard());

        // 文字前景：主标题正文色、副标题/说明次要色（构建期不读值、flush 后落定）。
        SceneNode title = host.__getHeader().__getChildren().get(0);
        SceneNode subtitle = host.__getHeader().__getChildren().get(1);
        Assert.assertEquals("主标题取主题正文色", DARK.foreground(), title.getTextColor());
        Assert.assertEquals("主标题字号保持 22", 22, title.getFontSize());
        Assert.assertEquals("副标题取主题次要前景", DARK.mutedForeground(), subtitle.getTextColor());
        Assert.assertEquals("副标题字号保持 12", 12, subtitle.getFontSize());

        // 采样场标题 16px 正文前景 + 两条 hint 12px 次要前景（title/hint 语义保持）。
        SceneNode stageTitle = host.__getStage().__getChildren().get(0);
        Assert.assertEquals("采样场标题取主题正文色", DARK.foreground(), stageTitle.getTextColor());
        Assert.assertEquals("采样场标题字号保持 16", 16, stageTitle.getFontSize());
        SceneNode stageHint = host.__getStage().__getChildren().get(3);
        Assert.assertEquals("采样场说明取主题次要前景", DARK.mutedForeground(), stageHint.getTextColor());
        // 探针卡 strongHint=正文色 / hint=次要色语义保持。
        SceneNode probeStrong = host.__getProbeCard().__getChildren().get(0);
        Assert.assertEquals("探针 strongHint 取主题正文色", DARK.foreground(), probeStrong.getTextColor());
        // 诊断文本正文色（内容仍由 pathSignal 驱动，见 GlassLabHostTest 既有用例）。
        SceneNode pathText = host.__getDiagnosticsCard().__getChildren().get(0);
        Assert.assertEquals("诊断文本取主题正文色", DARK.foreground(), pathText.getTextColor());
    }

    // ==================== ② P-04「换主题→变」 ====================

    /**
     * 主题切换：外壳四块表面 + 全部主题前景文字随新主题重派生，节点身份不变、订阅不增长；
     * 切换前后各属性确实取值不同（两向可被变异打红的前提）。
     */
    @Test
    public void themeSwitchUpdatesShellAndTextsWithoutRebuild() {
        Assert.assertNotEquals("测试前提：深浅 PANEL 配方必须不同",
                role(DARK, SceneTheme.Role.PANEL), role(LIGHT, SceneTheme.Role.PANEL));
        Assert.assertNotEquals("测试前提：深浅 GROUP 配方必须不同",
                role(DARK, SceneTheme.Role.GROUP), role(LIGHT, SceneTheme.Role.GROUP));
        Assert.assertNotEquals("测试前提：深浅正文色必须不同", DARK.foreground(), LIGHT.foreground());

        host.__getRuntime().flush();
        SceneNode stage = host.__getStage();
        SceneNode probe = host.__getProbeCard();
        SceneNode controls = host.__getControlsCard();
        SceneNode diagnostics = host.__getDiagnosticsCard();
        SceneNode title = host.__getHeader().__getChildren().get(0);
        int darkTitleColor = title.getTextColor();
        int darkStageTint = stage.getBackgroundColor();
        int effectsBefore = ReactiveTestProbe.registeredEffectCount();

        host.__getThemeSignal().set(LIGHT);
        host.__getRuntime().flush();
        host.__getRuntime().__finishMotionForTest();

        Assert.assertSame("主题切换不重建 stage", stage, host.__getStage());
        Assert.assertSame("主题切换不重建探针卡", probe, host.__getProbeCard());
        Assert.assertSame("主题切换不重建参数台卡", controls, host.__getControlsCard());
        Assert.assertSame("主题切换不重建诊断卡", diagnostics, host.__getDiagnosticsCard());
        Assert.assertNotEquals("换主题→stage 底色必须变（P-04 正向）",
                darkStageTint, stage.getBackgroundColor());
        assertSurfaceRecipe("切换后 stage", role(LIGHT, SceneTheme.Role.PANEL), stage);
        assertSurfaceRecipe("切换后探针卡", role(LIGHT, SceneTheme.Role.GROUP), probe);
        assertSurfaceRecipe("切换后参数台卡", role(LIGHT, SceneTheme.Role.GROUP), controls);
        assertSurfaceRecipe("切换后诊断卡", role(LIGHT, SceneTheme.Role.GROUP), diagnostics);

        Assert.assertNotEquals("换主题→主标题前景必须变（P-04 正向）", darkTitleColor, title.getTextColor());
        Assert.assertEquals("切换后主标题取新主题正文色", LIGHT.foreground(), title.getTextColor());
        Assert.assertEquals("切换后副标题取新主题次要前景", LIGHT.mutedForeground(),
                host.__getHeader().__getChildren().get(1).getTextColor());
        Assert.assertEquals("切换后探针 strongHint 取新主题正文色", LIGHT.foreground(),
                probe.__getChildren().get(0).getTextColor());
        Assert.assertEquals("切换后诊断文本取新主题正文色", LIGHT.foreground(),
                diagnostics.__getChildren().get(0).getTextColor());

        Assert.assertEquals("主题切换不新增订阅", effectsBefore,
                ReactiveTestProbe.registeredEffectCount());
    }

    /**
     * 局部主题（显式覆盖不回归）：{@link SceneThemes#withTheme} 作用域内构建的表面取局部主题，
     * 不外泄到外壳；作用域卸载后回收。
     */
    @Test
    public void localWithThemeBeatsRuntimeDefaultWithoutLeakingToShell() {
        host.__getRuntime().flush();
        Signal<SceneTheme> localTheme = Signal.create(DARK);
        SceneNode container = new SceneNode();
        MountHandle probe = host.__getRuntime().mount(container, () -> {
            SceneNode box = SceneNode.column();
            SceneThemes.withTheme(localTheme, () -> {
                SceneSurfaceBinder.bind(host.__getRuntime(), box,
                        SceneThemes.surface(host.__getRuntime(), SceneTheme.Role.GROUP),
                        () -> Boolean.TRUE, hostRuntimeInteraction(box));
            });
            return box;
        });
        host.__getRuntime().flush();

        SceneNode box = probe.getRoot();
        assertSurfaceRecipe("局部主题作用域表面", role(DARK, SceneTheme.Role.GROUP), box);

        localTheme.set(LIGHT);
        host.__getRuntime().flush();
        host.__getRuntime().__finishMotionForTest();
        assertSurfaceRecipe("切换后局部主题表面", role(LIGHT, SceneTheme.Role.GROUP), box);
        Assert.assertEquals("局部主题不外泄到外壳（外壳仍取 runtime 默认 DARK）",
                role(DARK, SceneTheme.Role.PANEL).getIdle().getTint(),
                host.__getStage().getBackgroundColor());

        probe.dispose();
        host.__getRuntime().flush();
        Assert.assertTrue("局部主题探针卸载后无残留节点", container.__getChildren().isEmpty());
    }

    /** 局部主题探针的交互态（构建期声明关心，配方停在 idle 档）。 */
    private SceneInteractionState hostRuntimeInteraction(SceneNode node) {
        SceneInteractionState interaction = host.__getRuntime().interactionState(node);
        interaction.hovered();
        interaction.pressed();
        interaction.focused();
        return interaction;
    }

    /**
     * 无滤镜档（关闭滤镜对照，§2.5/契约 §10）：themeSignal 切 {@code withoutBackdrop()} 后
     * 外壳表面不再写 backdrop，底色取不透明替代 tint（可读），圆角/边框仍随配方，节点身份不变。
     */
    @Test
    public void withoutBackdropThemeKeepsShellReadableAndDropsBackdrop() {
        host.__getRuntime().flush();
        SceneTheme noFilter = SceneTheme.liquidGlassDark().withoutBackdrop();
        int effectsBefore = ReactiveTestProbe.registeredEffectCount();

        host.__getThemeSignal().set(noFilter);
        host.__getRuntime().flush();
        host.__getRuntime().__finishMotionForTest();

        SceneNode stage = host.__getStage();
        Assert.assertSame("无滤镜切换不重建 stage", stage, host.__getStage());
        Assert.assertNull("无滤镜档 stage 不写 backdrop", stage.getBackdrop());
        Assert.assertEquals("无滤镜档底色 = 不透明替代 tint",
                noFilter.surface(SceneTheme.Role.PANEL).getIdle().getTint(), stage.getBackgroundColor());
        Assert.assertEquals("替代 tint 必须全不透明可读", 0xFF,
                stage.getBackgroundColor() >>> 24);
        Assert.assertEquals("圆角仍随配方",
                noFilter.surface(SceneTheme.Role.PANEL).getCornerRadius(), stage.getCornerRadius());
        Assert.assertNull("无滤镜档探针卡不写 backdrop", host.__getProbeCard().getBackdrop());
        Assert.assertEquals("无滤镜切换不新增订阅", effectsBefore,
                ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== ③ P-04「换主题→不变」反向钉（§7.3 诊断样本） ====================

    /**
     * 主题更新不覆盖实验显式值：SAMPLE_COLORS 色带逐值不变（且始终 = 镜像显式样本）、
     * 材质/blur/饱和/圆角/液态强度受控源逐值不变、三档对照 backdrop 矩形逐值不变、
     * 旧语义玻璃 tint 面不因主题改变——实验能力与三档可比性不破坏。
     */
    @Test
    public void themeSwitchNeverTouchesDiagnosticSamples() {
        // 旧语义档：玻璃 tint 面可观测（材质档对照之一）。
        host.__getMaterialIndexSignal().set(Double.valueOf(0.0D));
        RecordingRenderBackend before = new RecordingRenderBackend();
        renderAndFlush(before);
        int tintCallsBefore = countOverlayTintFaces(before, host.__getBackdropRects());
        int[] rectsBefore = snapshotRects();
        int[] chipsBefore = host.__getSampleChipColors();
        Assert.assertArrayEquals("构建后色带样本 = 显式 SAMPLE_COLORS（逐值）",
                expectedChipColors(), chipsBefore);

        double materialBefore = host.__getMaterialIndexSignal().get().doubleValue();
        double blurBefore = host.__getBlurSignal().get().doubleValue();
        double lensBefore = host.__getLensStrengthSignal().get().doubleValue();

        host.__getThemeSignal().set(LIGHT);
        host.__getRuntime().flush();
        host.__getRuntime().__finishMotionForTest();

        // ① chip 显式样本逐值不变（含 chip 数量不变）。
        Assert.assertArrayEquals("换主题后色带样本仍 = 显式 SAMPLE_COLORS（P-04 反向钉）",
                expectedChipColors(), host.__getSampleChipColors());
        Assert.assertArrayEquals("换主题前后色带逐值恒定", chipsBefore, host.__getSampleChipColors());
        Assert.assertEquals("色带 chip 数保持 20", 20, host.__getSampleChipColors().length);

        // ② 实验受控源不被主题写动。
        Assert.assertEquals("材质档受控源不因主题变", materialBefore,
                host.__getMaterialIndexSignal().get().doubleValue(), EPSILON);
        Assert.assertEquals("模糊半径受控源不因主题变", blurBefore,
                host.__getBlurSignal().get().doubleValue(), EPSILON);
        Assert.assertEquals("液态强度受控源不因主题变", lensBefore,
                host.__getLensStrengthSignal().get().doubleValue(), EPSILON);

        // ③ 三档对照的样本几何不变：重渲染后滤镜矩形逐值相等。
        RecordingRenderBackend after = new RecordingRenderBackend();
        renderAndFlush(after);
        Assert.assertArrayEquals("backdrop 滤镜矩形不随主题漂移（三档对照基线可比）",
                rectsBefore, snapshotRects());
        // ④ 旧语义玻璃 tint 面仍由 GLASS_TINT 显式表达、逐矩形贴合 backdrop 请求，
        //    不被主题改写（按几何配对计数：主题配方 tint 可能与 GLASS_TINT 色值巧合，色值计数不可靠）。
        int overlayTintAfter = countOverlayTintFaces(after, host.__getBackdropRects());
        Assert.assertEquals("旧语义玻璃 tint 面次数不因主题改变",
                tintCallsBefore, overlayTintAfter);
        Assert.assertTrue("测试前提：切换前后帧均确有旧语义 tint 面（主面板+探针≥2）",
                tintCallsBefore >= 2 && overlayTintAfter >= 2);
    }

    /**
     * 统计与 backdrop 请求矩形逐坐标配对的 GLASS_TINT drawSurface 面数（几何口径）：
     * 主题配方 tint 可能恰好等于 GLASS_TINT 色值，纯色值计数会误纳主题面，几何配对才唯一标识
     * 宿主旧语义叠加层。
     */
    private static int countOverlayTintFaces(RecordingRenderBackend backend, List<int[]> rects) {
        int count = 0;
        for (int i = 0; i < backend.getCallCount(); i++) {
            RenderCall call = backend.getCall(i);
            if (!call.methodName().equals("drawSurface") || call.getInt(FILL_COLOR_ARG) != GLASS_TINT) {
                continue;
            }
            for (int[] rect : rects) {
                if (call.getInt(0) == rect[0] && call.getInt(1) == rect[1]
                        && call.getInt(2) == rect[2] && call.getInt(3) == rect[3]) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    /** 快照本帧 backdrop 滤镜矩形为扁平 int 数组。 */
    private int[] snapshotRects() {
        List<int[]> rects = host.__getBackdropRects();
        int[] flat = new int[rects.size() * 4];
        for (int i = 0; i < rects.size(); i++) {
            int[] rect = rects.get(i);
            flat[i * 4] = rect[0];
            flat[i * 4 + 1] = rect[1];
            flat[i * 4 + 2] = rect[2];
            flat[i * 4 + 3] = rect[3];
        }
        return flat;
    }

    /**
     * 实验参数仍精确驱动对应样本（能力保持，正向）：blur 受控源写入即时反映到
     * 本帧滤镜请求路径，旧语义 tint 面只在「材质=旧语义」时出现（材质档抑制）——
     * 本用例与 GlassLabHostTest 既有用例共同钉「切换实验参数仍改变对应样本」。
     */
    @Test
    public void blurSignalChangeStillDrivesFrameRequests() {
        host.__getMaterialIndexSignal().set(Double.valueOf(0.0D));
        renderAndFlush(new RecordingRenderBackend());
        int[] rectsBefore = snapshotRects();
        Assert.assertTrue("默认有滤镜请求", rectsBefore.length >= 8);

        host.__getBlurSignal().set(Double.valueOf(64.0D));
        renderAndFlush(new RecordingRenderBackend());
        Assert.assertArrayEquals("blur 滑杆不改几何矩形（几何由布局派生）",
                rectsBefore, snapshotRects());
        Assert.assertEquals("blur 受控源即时生效", 64.0D,
                host.__getBlurSignal().get().doubleValue(), EPSILON);
    }

    // ==================== ④ P-01 泄漏守卫 ====================

    /**
     * 卸载即回收：宿主（含 __runRoot 包裹的外壳全部主题绑定）销毁后 effect 数回到基线。
     * 若建树未包进 {@code __runRoot}，构造期创建的 Computed/Effect 不归属作用域，本用例变红。
     */
    @Test
    public void disposeReclaimsShellThemeBindings() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        GlassLabHost extra = new GlassLabHost(new FixedTextMeasurer(), null);
        try {
            extra.__getRuntime().flush();
            Assert.assertTrue("外壳主题绑定应注册响应式工作，baseline=" + baseline
                            + ", mounted=" + ReactiveTestProbe.registeredEffectCount(),
                    ReactiveTestProbe.registeredEffectCount() > baseline);
        } finally {
            extra.dispose();
        }
        Assert.assertEquals("卸载后外壳绑定全部回收（P-01 防泄漏）",
                baseline, ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== ⑤ 源码守卫 ====================

    /**
     * 源码守卫（标配）：
     * 禁绝 token = 竞争静态取色（PlaygroundKit 色常量、显式色文本构件、无 rt 构件回退路径）、
     * 旧接缝（包级 installRuntime 跨包触达尝试、SceneStateColors/applyPanelChrome）与
     * 迁移点残留静态边框/圆角写入；正向钉 = SceneSurfaceBinder/SceneThemes 消费点精确计数。
     */
    @Test
    public void sourceGuardShellConsumesThemeAndKeepsSamplesExplicit() throws Exception {
        Path path = Paths.get(
                "src/main/java/club/heiqi/uilib/internal/devtools/glass/GlassLabHost.java");
        String raw = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        StringBuilder code = new StringBuilder();
        for (String line : raw.split("\r?\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                continue;
            }
            code.append(trimmed).append('\n');
        }
        String src = code.toString();

        // ---- 正向钉：主题接缝消费点 ----
        Assert.assertEquals("表面绑定器唯一消费点（bindLabSurface 一处，禁止旁路直写）",
                1, count(src, "SceneSurfaceBinder.bind("));
        Assert.assertEquals("配方取角色 = SceneThemes.surface 一处（统一经 bindLabSurface）",
                1, count(src, "SceneThemes.surface("));
        Assert.assertEquals("runtime 默认主题安装一处", 1, count(src, "SceneThemes.install("));
        Assert.assertEquals("宿主构建必须包进 __runRoot（P-01 防泄漏）",
                1, count(src, "__runRoot("));
        Assert.assertEquals("正文前景构件调用点计数（迁移的标题/strongHint/诊断文本）",
                5, count(src, "SceneThemes.foreground(runtime"));
        Assert.assertEquals("次要前景构件调用点计数（迁移的副标题/hint）",
                6, count(src, "SceneThemes.mutedForeground(runtime"));
        Assert.assertEquals("主题前景文本一律走 PlaygroundKit.text(runtime,…) 公开重载",
                11, count(src, "PlaygroundKit.text(runtime"));

        // ---- 禁绝 token：竞争静态取色与旧接缝 ----
        Assert.assertFalse("不得残留 PlaygroundKit 静态色常量取色", src.contains("PlaygroundKit.TEXT")
                || src.contains("PlaygroundKit.MUTED")
                || src.contains("PlaygroundKit.PANEL_BG")
                || src.contains("PlaygroundKit.BORDER")
                || src.contains("PlaygroundKit.ACCENT")
                || src.contains("PlaygroundKit.DANGER"));
        Assert.assertFalse("不得调用无 rt 构件回退路径（跨包只会走静态色，主题必失联）",
                src.contains("PlaygroundKit.card(")
                        || src.contains("PlaygroundKit.title(")
                        || src.contains("PlaygroundKit.hint(")
                        || src.contains("PlaygroundKit.strongHint("));
        Assert.assertFalse("不得触碰 playground 包级旧接缝 installRuntime",
                src.contains("installRuntime("));
        Assert.assertFalse("不得残留旧状态色板/面板 chrome 接缝",
                src.contains("SceneStateColors") || src.contains("applyPanelChrome"));
        Assert.assertFalse("迁移表面不得静态写边框",
                src.contains("setBorderWidth(") || src.contains("setBorderColor("));
        Assert.assertFalse("颜色类 SceneChromeTokens 常量不得回流（布局常量另见正向钉）",
                src.contains("SceneChromeTokens.BG") || src.contains("SceneChromeTokens.BORDER")
                        || src.contains("SceneChromeTokens.TEXT"));

        // ---- 白名单保留项精确计数（防样本面扩大） ----
        Assert.assertEquals("setBackgroundColor 仅两处 = 遮罩承托底 1 + 样本 chip 1（§7.3 保留）",
                2, count(src, "setBackgroundColor("));
        Assert.assertEquals("setCornerRadius 仅一处 = 样本 chip 6px（§7.3 保留）",
                1, count(src, "setCornerRadius("));
        Assert.assertEquals("遮罩承托底 = PlaygroundKit.ROOT_BG 显式保留一处（G15/Shell 遮罩裁决）",
                1, count(src, "PlaygroundKit.ROOT_BG"));
        // 布局常量按契约 §4.2 继续使用（正向钉其存在，证明卡片复用 tokens 而非魔数）。
        Assert.assertTrue("卡片布局 padding 复用 SceneChromeTokens.PAD_LG",
                src.contains("SceneChromeTokens.PAD_LG"));
        Assert.assertTrue("卡片布局 gap 复用 SceneChromeTokens.GAP_MD",
                src.contains("SceneChromeTokens.GAP_MD"));
    }

    /** 统计子串出现次数（源码守卫用）。 */
    private static int count(String source, String token) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }
}
