package club.heiqi.config.ui.field;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.runtime.Authority;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.theme.ConfigTheme;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * G15/Number：{@link NumberFieldRenderer} 默认液态玻璃外观测试。
 *
 * <p>沿真实装配路径（{@code runtime.mount} + {@code SceneThemes.withTheme} +
 * {@code renderer.render}）验证：slider 读数前景跟随来源主题 {@code foreground} 信号、
 * 主题切换只重派生（草稿/节点身份/effect 数不动）；本实例的 ROW 包装不叠第二层表面；
 * input 路径的 INPUT 配方由已迁移控件 {@code SceneTextInput} 自持（只消费证据）；
 * dirty/error 桥经 binder 保持；布局常量按契约 §4 保留；源码守卫钉死外观写入边界。</p>
 */
public class NumberFieldRendererThemeTest {

    private SceneRuntime runtime;
    private ConfigSchema sliderSchema;
    private ConfigSchema inputSchema;
    private Authority authority;
    private DraftBuffer draft;
    private DraftSignalAdapter adapter;
    private NumberFieldRenderer renderer;

    @Before
    public void setUp() throws Exception {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        runtime = new SceneRuntime(measurer);
        sliderSchema = ConfigSchema.builder("t")
                .section("a")
                    .number("port").defaultValue(8080.0).range(0, 65535).slider().label("Port").build()
                .endSection()
                .build();
        inputSchema = ConfigSchema.builder("t")
                .section("a")
                    .number("count").defaultValue(50.0).label("Count").build()
                .endSection()
                .build();
        authority = Authority.load(new File("nonexistent-g15-number.yaml"), sliderSchema);
        draft = DraftBuffer.from(authority);
        adapter = new DraftSignalAdapter(runtime, draft);
        renderer = new NumberFieldRenderer();
        ReactiveScheduler.get().flush();
    }

    @After
    public void tearDown() throws Exception {
        adapter.dispose();
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    /** 默认路径：读数前景 = 来源主题 foreground；间距/字号按契约 §4 保留为布局常量。 */
    @Test
    public void readoutForegroundFollowsSourceTheme() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountRendered(Signal.create(dark), sliderSchema.field("a.port"), holder);
        runtime.flush();

        SceneNode row = findControlRow(holder[0]);
        SceneNode readout = lastChild(row);
        Assert.assertEquals("读数前景 = 主题 foreground", dark.foreground(), readout.getTextColor());
        // 契约 §4：padding/尺寸/布局属性归控件自身，主题不接管布局——这两个常量必须保持原值
        Assert.assertEquals("行间距保留 FIELD_GAP", ConfigTheme.FIELD_GAP, row.getGap());
        Assert.assertEquals("读数字号保留 FONT_READOUT", ConfigTheme.FONT_READOUT, readout.getFontSize());
        handle.dispose();
    }

    /**
     * P-04 切换断言：换主题 → 读数颜色变；草稿保留、节点身份不变、effect 不增长、卸载回收。
     *
     * <p>把绑定改回静态 {@code TEXT_COLOR} 或构造期 {@code .get()} 快照、或错绑别的语义色，
     * 本用例都会变红（变异可打红）。</p>
     */
    @Test
    public void themeSwitchRepaintsReadoutKeepingDraftAndIdentity() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Assert.assertNotEquals("前提：两档 foreground 不同（否则切换不传播）",
                dark.foreground(), light.foreground());

        Signal<SceneTheme> pageTheme = Signal.create(dark);
        SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountRendered(pageTheme, sliderSchema.field("a.port"), holder);
        runtime.flush();

        adapter.onFieldEdit("a.port", 4000.0);
        runtime.flush();
        SceneNode card = holder[0];
        SceneNode readout = lastChild(findControlRow(card));
        Assert.assertEquals("切换前读数 = 深色 foreground", dark.foreground(), readout.getTextColor());
        Assert.assertEquals("切换前读数文本已反映编辑", "4000", readout.getText());

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();
        Assert.assertEquals("切换主题后读数前景 = 浅色 foreground",
                light.foreground(), readout.getTextColor());

        pageTheme.set(dark);
        runtime.flush();
        Assert.assertEquals("切回深色后前景恢复", dark.foreground(), readout.getTextColor());

        Assert.assertSame("主题切换不重建卡片节点", card, holder[0]);
        Assert.assertSame("主题切换不重建读数节点", readout, lastChild(findControlRow(holder[0])));
        Assert.assertEquals("主题切换不丢草稿", 4000.0, adapter.draftSignal("a.port").get());
        Assert.assertEquals("读数文本不受主题切换影响", "4000", readout.getText());
        Assert.assertEquals("主题切换不新增 effect", effectsBefore,
                ReactiveTestProbe.registeredEffectCount());

        handle.dispose();
        runtime.flush();
        Assert.assertTrue("卸载后本卡片 effect 被回收",
                ReactiveTestProbe.registeredEffectCount() < effectsBefore);
    }

    /** ROW 包装只是布局容器：不写底色、不装滤镜、不带表面浮雕（不叠第二层玻璃）。 */
    @Test
    public void sliderRowCarriesNoSecondSurface() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountRendered(Signal.create(dark), sliderSchema.field("a.port"), holder);
        runtime.flush();

        SceneNode row = findControlRow(holder[0]);
        SceneNode fresh = new SceneNode();
        Assert.assertEquals("ROW 底色保持节点默认（未写第二表面）",
                fresh.getBackgroundColor(), row.getBackgroundColor());
        Assert.assertNull("ROW 不装 backdrop", row.getBackdrop());
        Assert.assertEquals("ROW 未绑定表面浮雕（契约 §4：未绑定节点保持 -1）",
                fresh.__getSurfaceElevation(), row.__getSurfaceElevation(), 0.0001f);
        handle.dispose();
    }

    /**
     * input 路径证据：卡片走 GROUP 主题配方、输入框走控件自持的 INPUT 配方——
     * 本 Renderer 只消费 {@code SceneTextInput}（G04），不复制样式、不竞争写入。
     */
    @Test
    public void inputPathConsumesMigratedControlsThroughRealAssembly() throws Exception {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        Authority auth = Authority.load(new File("nonexistent-g15-number-input.yaml"), inputSchema);
        DraftBuffer d = DraftBuffer.from(auth);
        DraftSignalAdapter a = new DraftSignalAdapter(runtime, d);
        ReactiveScheduler.get().flush();
        SceneNode[] holder = new SceneNode[1];
        MountHandle handle = runtime.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(Signal.create(dark),
                    () -> holder[0] = renderer.render(runtime, inputSchema.field("a.count"), a));
            return holder[0];
        });
        runtime.flush();

        SceneNode card = holder[0];
        SceneSurfaceStyle group = dark.surface(SceneTheme.Role.GROUP);
        Assert.assertEquals("卡片底色 = GROUP 配方 idle tint（binder 主题路径，本类不复制）",
                group.getIdle().getTint(), card.getBackgroundColor());

        SceneNode inputRoot = findFiveChildRoot(card);
        Assert.assertNotNull("input 路径应挂出 SceneTextInput 根", inputRoot);
        SceneSurfaceStyle input = dark.surface(SceneTheme.Role.INPUT);
        Assert.assertEquals("输入框底色 = INPUT 配方 idle tint（控件自持，本类未竞争）",
                input.getIdle().getTint(), inputRoot.getBackgroundColor());
        Assert.assertNotNull("输入框滤镜由控件本体装配", inputRoot.getBackdrop());
        handle.dispose();
        a.dispose();
    }

    /** dirty/error 语义桥经 binder 在真实 Renderer 装配路径保持可辨（契约 §4.1 GROUP 行）。 */
    @Test
    public void dirtyAndErrorBridgesStayDistinct() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        Assert.assertNotEquals("前提：accent 与 errorText 可辨", dark.accent(), dark.errorText());
        SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountRendered(Signal.create(dark), sliderSchema.field("a.port"), holder);
        runtime.flush();
        SceneNode card = holder[0];
        Assert.assertEquals("静默态缘色 = GROUP 配方 idle edge",
                dark.surface(SceneTheme.Role.GROUP).getIdle().getEdge(), card.getBorderColor());

        adapter.onFieldEdit("a.port", 4000.0);
        runtime.flush();
        Assert.assertEquals("dirty 缘色 = 主题 accent", dark.accent(), card.getBorderColor());

        adapter.onFieldEdit("a.port", 99999.0); // 超出 range max → error
        runtime.flush();
        Assert.assertEquals("error 压过 dirty = 主题 errorText", dark.errorText(), card.getBorderColor());
        handle.dispose();
    }

    // ==================== G15/Number 源码守卫 ====================

    /**
     * 主文件外观写入边界：语义色零静态直写、零旧接缝、零竞争表面；布局/排版纯 int 常量保留。
     *
     * <p><b>G15/收口同步义务（已执行）</b>：binder theme 兼容占位形参已从签名删除，本类两处
     * 占位实参随之摘除——原「asFormTheme() 兼容占位形参保留（禁止提前摘参）」正向钉翻转为
     * 「ConfigTheme.asFormTheme 零出现」反向钉（防快照回潮）；{@code FIELD_GAP}/{@code FONT_READOUT}
     * 纯 int 常量正向钉保持。</p>
     */
    @Test
    public void sourceGuardNoCompetingAppearanceWrites() throws Exception {
        String code = codeWithoutComments(
                new String(Files.readAllBytes(Paths.get(
                        "src/main/java/club/heiqi/config/ui/field/NumberFieldRenderer.java")),
                        StandardCharsets.UTF_8));
        String[] banned = {
                "setTextColor(ConfigTheme", "ConfigTheme.TEXT_COLOR", "ConfigTheme.TITLE_COLOR",
                "ConfigTheme.MUTED_COLOR", "ConfigTheme.ERROR_COLOR", "ConfigTheme.OK_COLOR",
                "ConfigTheme.DIRTY_COLOR", "ConfigTheme.READOUT_BG", "ConfigTheme.SURFACE_CONTAINER",
                "ConfigTheme.VIEWPORT_BG", "ConfigTheme.ROOT_BG",
                "setBackgroundColor", "setBorderColor", "setBorderWidth", "setCornerRadius",
                "setBackdrop", "__setSurfaceElevation",
                "SceneControlChrome", "SceneStateColors", "SceneChromeTokens", "SceneSurfaceBinder",
                "SceneThemes.surface", "applyPanelChrome", "FormThemes.",
                // G15/收口：theme 兼容占位实参已摘，显式旧主题快照封死回潮
                "asFormTheme", "FormTheme",
        };
        for (String token : banned) {
            Assert.assertFalse("守卫：NumberFieldRenderer 代码不得出现 " + token, code.contains(token));
        }
        // 读数前景唯一来源 = 主题 foreground 信号；卡片表面唯一路径 = FieldShellBinder 收口
        Assert.assertTrue("守卫：读数前景必须经 SceneThemes.foreground(rt) 信号",
                code.contains("SceneThemes.foreground("));
        Assert.assertTrue("守卫：外壳装配必须收口在 FieldShellBinder.build",
                code.contains("FieldShellBinder.build("));
        // 契约 §4 布局/排版纯 int 常量保留（G15/收口后本类 ConfigTheme 消费只剩这两处）
        Assert.assertTrue("守卫：FIELD_GAP 布局常量保留", code.contains("ConfigTheme.FIELD_GAP"));
        Assert.assertTrue("守卫：FONT_READOUT 排版常量保留", code.contains("ConfigTheme.FONT_READOUT"));
        Assert.assertEquals("G15/收口：ConfigTheme 引用计数 = import + FIELD_GAP + FONT_READOUT 恰 3",
                3, countOccurrences(code, "ConfigTheme"));
    }

    // ==================== 夹具 ====================

    /** 真实装配夹具：{@code runtime.mount} + {@code SceneThemes.withTheme} 内调 {@code renderer.render}。 */
    private MountHandle mountRendered(Signal<SceneTheme> pageTheme, FieldSpec spec, SceneNode[] holder) {
        return runtime.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(pageTheme,
                    () -> holder[0] = renderer.render(runtime, spec, adapter));
            return holder[0];
        });
    }

    /** 控件 ROW：card 子节点（跳过 index 0 header）中恰含 2 子（sliderRoot + readout）者。 */
    private SceneNode findControlRow(SceneNode card) {
        for (int i = 1; i < card.__getChildren().size(); i++) {
            SceneNode c = card.__getChildren().get(i);
            if (c.__getChildren().size() == 2) {
                return c;
            }
        }
        Assert.fail("未找到 slider 控件 ROW");
        return null;
    }

    /** TextInput 根：B2 五节点结构（prefix/caret/highlight/caretAfter/suffix）。 */
    private SceneNode findFiveChildRoot(SceneNode card) {
        for (int i = 1; i < card.__getChildren().size(); i++) {
            SceneNode c = card.__getChildren().get(i);
            if (c.__getChildren().size() == 5) {
                return c;
            }
        }
        return null;
    }

    private SceneNode lastChild(SceneNode parent) {
        return parent.__getChildren().get(parent.__getChildren().size() - 1);
    }

    /** 复用 G15/Support 的包内共享去注释夹具，守卫只审查代码、不误伤 Javadoc 措辞。 */
    private static String codeWithoutComments(String raw) {
        return FieldShellBinderTest.codeWithoutComments(raw);
    }

    /** 子串计数（G15/收口守卫用；与 ChoiceFieldRendererThemeTest 同款实现，包内私有不可跨类复用）。 */
    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int idx = haystack.indexOf(needle, from);
            if (idx < 0) {
                return count;
            }
            count++;
            from = idx + needle.length();
        }
    }
}
