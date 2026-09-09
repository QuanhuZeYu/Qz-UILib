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
 * {@link StringFieldRenderer} 默认液态玻璃路径测试（G15/String）。
 *
 * <p>本实例实扫 {@code StringFieldRenderer} 零外观写入：字段卡片表面由
 * {@link FieldShellBinder} → {@code FormFieldShell} theme-aware 默认路径（GROUP 角色）承担，
 * 输入框表面与语义前景由已迁移控件 {@code SceneTextInput}（G04/TextInput，INPUT 角色）本体承担，
 * 渲染器自身只做值信号 / maxLength / onChange 的非视觉适配。故本类不新增外观写入，而是把
 * 「非视觉职责」钉成守卫，并从真实装配路径（{@code renderer.render} 经 binder 下沉）验证：</p>
 *
 * <ol>
 *   <li>默认路径角色配方装配：卡片 = GROUP 配方、输入框根 = INPUT 配方（契约 §4.1 输入族），
 *       正文/标题/占位前景取主题 foreground / mutedForeground；</li>
 *   <li>P-04 主题切换：换来源主题 → 卡片底色与输入框底色、正文前景「颜色变」（两档前提断言
 *       {@code assertNotEquals}），且草稿保留、节点身份不变、effect 数不增；</li>
 *   <li>dirty/error 桥不动：经渲染器路径，静默=配方缘色、dirty=accent、error=errorText 可辨；</li>
 *   <li>卸载回收：dispose 后外观绑定 effect 回基线，主题更新不再写入旧节点；</li>
 *   <li>源码守卫：渲染器零外观写入点、零旧接缝、零主题快照，保留
 *       {@code FieldShellBinder.build} 与 {@code ConfigTheme.asFormTheme()} 兼容占位形参
 *       （G15/Support 衔接要点：Renderer 实例期间禁自行摘参，由主代理统一收口）。</li>
 * </ol>
 */
public class StringFieldRendererLiquidGlassTest {

    /** B2 五节点结构：prefix/caret/highlight/caretAfter/suffix（与既有渲染器测试同判据）。 */
    private static final int TEXT_INPUT_CHILD_COUNT = 5;

    private SceneRuntime runtime;
    private ConfigSchema schema;
    private DraftSignalAdapter adapter;
    private StringFieldRenderer renderer;

    @Before
    public void setUp() throws Exception {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        runtime = new SceneRuntime(measurer);
        schema = ConfigSchema.builder("t")
                .section("server")
                    .string("host").label("Host").helper("server host")
                    .defaultValue("localhost").required().maxLength(100).build()
                .endSection()
                .build();
        Authority authority = Authority.load(new File("nonexistent-string-glass.yaml"), schema);
        DraftBuffer draft = DraftBuffer.from(authority);
        adapter = new DraftSignalAdapter(runtime, draft);
        renderer = new StringFieldRenderer();
        ReactiveScheduler.get().flush();
    }

    @After
    public void tearDown() {
        adapter.dispose();
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 默认路径：真实装配的角色配方消费 ====================

    /** 经渲染器真实装配：卡片吃来源主题 GROUP 配方，输入框根吃 INPUT 配方，前景取主题语义色。 */
    @Test
    public void renderThroughThemePathUsesGroupAndInputRecipes() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        FieldSpec spec = schema.field("server.host");
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThroughRenderer(Signal.create(dark), spec, holder);
        runtime.flush();

        SceneNode card = holder[0];
        SceneSurfaceStyle group = dark.surface(SceneTheme.Role.GROUP);
        Assert.assertEquals("卡底色 = GROUP 配方 idle tint", group.getIdle().getTint(), card.getBackgroundColor());
        Assert.assertEquals("卡缘色 = GROUP 配方 idle edge", group.getIdle().getEdge(), card.getBorderColor());
        Assert.assertEquals("卡圆角 = GROUP 配方圆角", group.getCornerRadius(), card.getCornerRadius());
        Assert.assertEquals("卡边框宽 = GROUP 配方边框宽", group.getBorderWidth(), card.getBorderWidth());
        Assert.assertNotNull("卡片语义表面装有滤镜", card.getBackdrop());

        SceneNode input = findTextInputRoot(card);
        Assert.assertNotNull("应找到 TextInput 控件根", input);
        SceneSurfaceStyle surface = dark.surface(SceneTheme.Role.INPUT);
        Assert.assertEquals("输入框底色 = INPUT 配方 idle tint", surface.getIdle().getTint(),
                input.getBackgroundColor());
        Assert.assertEquals("输入框缘色 = INPUT 配方 idle edge", surface.getIdle().getEdge(),
                input.getBorderColor());
        Assert.assertEquals("输入框圆角 = INPUT 配方圆角", surface.getCornerRadius(), input.getCornerRadius());
        Assert.assertNotNull("输入框语义表面装有滤镜", input.getBackdrop());

        SceneNode title = card.__getChildren().get(0).__getChildren().get(1);
        Assert.assertEquals("标题文本桥不动", "Host", title.getText());
        Assert.assertEquals("标题前景 = 主题 foreground", dark.foreground(), title.getTextColor());
        SceneNode helperNode = card.__getChildren().get(1);
        Assert.assertEquals("helper 文本桥不动", "server host", helperNode.getText());
        Assert.assertEquals("helper 前景 = 主题 mutedForeground",
                dark.mutedForeground(), helperNode.getTextColor());

        // 正文前景归控件本体（G04）：value 非占位态 → foreground；渲染器不写占位显式色
        Assert.assertEquals("prefix 正文前景 = 主题 foreground", dark.foreground(),
                input.__getChildren().get(0).getTextColor());
        Assert.assertEquals("suffix 正文前景 = 主题 foreground", dark.foreground(),
                input.__getChildren().get(4).getTextColor());
        handle.dispose();
    }

    // ==================== P-04：换主题 → 颜色变（真实装配路径） ====================

    /** 主题切换：卡片与输入框表面、正文前景重派生为浅色档值；草稿、节点身份、effect 数不动。 */
    @Test
    public void themeSwitchRepaintsCardAndInputKeepingDraftAndIdentity() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Assert.assertNotEquals("前提：两档 GROUP idle 配方不同（否则切换不传播）",
                dark.surface(SceneTheme.Role.GROUP).getIdle(),
                light.surface(SceneTheme.Role.GROUP).getIdle());
        Assert.assertNotEquals("前提：两档 INPUT idle 配方不同",
                dark.surface(SceneTheme.Role.INPUT).getIdle(),
                light.surface(SceneTheme.Role.INPUT).getIdle());
        Assert.assertNotEquals("前提：两档正文前景不同", dark.foreground(), light.foreground());

        FieldSpec spec = schema.field("server.host");
        Signal<SceneTheme> pageTheme = Signal.create(dark);
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThroughRenderer(pageTheme, spec, holder);
        runtime.flush();

        adapter.onFieldEdit("server.host", "typed-draft");
        runtime.flush();
        SceneNode card = holder[0];
        SceneNode input = findTextInputRoot(card);
        SceneNode prefix = input.__getChildren().get(0);
        SceneNode suffix = input.__getChildren().get(4);
        Assert.assertEquals("前置：输入框底色 = 深色档 INPUT tint",
                dark.surface(SceneTheme.Role.INPUT).getIdle().getTint(), input.getBackgroundColor());

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();

        Assert.assertEquals("换主题后卡片底色变（= 浅色档 GROUP idle tint）",
                light.surface(SceneTheme.Role.GROUP).getIdle().getTint(), card.getBackgroundColor());
        Assert.assertEquals("换主题后输入框底色变（= 浅色档 INPUT idle tint）",
                light.surface(SceneTheme.Role.INPUT).getIdle().getTint(), input.getBackgroundColor());
        Assert.assertEquals("换主题后正文前景变（prefix = 浅色档 foreground）",
                light.foreground(), prefix.getTextColor());
        Assert.assertEquals("换主题后正文前景变（suffix = 浅色档 foreground）",
                light.foreground(), suffix.getTextColor());
        Assert.assertSame("换主题不重建卡片节点", card, holder[0]);
        Assert.assertSame("换主题不重建输入控件节点", input, findTextInputRoot(holder[0]));
        Assert.assertEquals("主题切换不丢草稿", "typed-draft", adapter.draftSignal("server.host").get());
        Assert.assertTrue("草稿文本仍在控件上", concatenatedText(input).contains("typed-draft"));
        Assert.assertEquals("主题切换不新增 effect",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());
        handle.dispose();
    }

    // ==================== dirty / error 桥不动（经渲染器路径） ====================

    /** 经渲染器装配，dirty/error 语义在默认路径可辨：clean=配方缘色、dirty=accent、error=errorText。 */
    @Test
    public void dirtyAndErrorBridgeUnchangedThroughRenderer() throws Exception {
        ConfigSchema strict = ConfigSchema.builder("t")
                .section("server")
                    .string("host").defaultValue("v").required().maxLength(2).build()
                .endSection()
                .build();
        Authority auth = Authority.load(new File("nonexistent-string-glass2.yaml"), strict);
        DraftBuffer d = DraftBuffer.from(auth);
        DraftSignalAdapter a = new DraftSignalAdapter(runtime, d);
        ReactiveScheduler.get().flush();
        try {
            SceneTheme dark = SceneTheme.liquidGlassDark();
            Assert.assertNotEquals("前提：accent 与 errorText 可辨", dark.accent(), dark.errorText());
            final SceneNode[] holder = new SceneNode[1];
            MountHandle handle = runtime.mount(new SceneNode(), () -> {
                SceneThemes.withTheme(Signal.create(dark),
                        () -> holder[0] = renderer.render(runtime, strict.field("server.host"), a));
                return holder[0];
            });
            runtime.flush();
            SceneNode card = holder[0];
            Assert.assertEquals("静默态缘色 = GROUP 配方原缘色",
                    dark.surface(SceneTheme.Role.GROUP).getIdle().getEdge(), card.getBorderColor());

            a.onFieldEdit("server.host", "ok");
            runtime.flush();
            Assert.assertEquals("dirty 缘色 = 主题 accent", dark.accent(), card.getBorderColor());

            a.onFieldEdit("server.host", "toolong");
            runtime.flush();
            Assert.assertEquals("error 压过 dirty = 主题 errorText", dark.errorText(), card.getBorderColor());

            a.onFieldEdit("server.host", "ok");
            runtime.flush();
            Assert.assertEquals("错误清除后回落 dirty 强调色", dark.accent(), card.getBorderColor());
            handle.dispose();
        } finally {
            a.dispose();
        }
    }

    // ==================== 卸载回收 ====================

    /** dispose 后外观绑定 effect 回基线，主题再切换不再写入旧节点。 */
    @Test
    public void unmountReleasesThemeBindingsOfRenderedField() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        Signal<SceneTheme> pageTheme = Signal.create(SceneTheme.liquidGlassDark());
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThroughRenderer(pageTheme, schema.field("server.host"), holder);
        runtime.flush();
        Assert.assertTrue("默认路径应注册响应式外观绑定",
                ReactiveTestProbe.registeredEffectCount() > baseline);

        SceneNode card = holder[0];
        SceneNode input = findTextInputRoot(card);
        int cardBgBefore = card.getBackgroundColor();
        int inputBgBefore = input.getBackgroundColor();

        handle.dispose();
        runtime.flush();
        Assert.assertEquals("卸载后外观绑定 effect 应回收",
                baseline, ReactiveTestProbe.registeredEffectCount());

        pageTheme.set(SceneTheme.liquidGlassLight());
        runtime.flush();
        Assert.assertEquals("卸载后主题更新不再写入旧卡片", cardBgBefore, card.getBackgroundColor());
        Assert.assertEquals("卸载后主题更新不再写入旧输入框", inputBgBefore, input.getBackgroundColor());
    }

    // ==================== G15/String 源码守卫（非视觉职责钉） ====================

    /**
     * 渲染器零外观写入：本类迁移口径 = 「主文件零改动」，守卫把非视觉职责钉进回归。
     *
     * <p>禁则涵盖契约 §4 全部外观写入面（set 色/边框/圆角/backdrop、旧 chrome 接缝、
     * 主题快照解引用、显式占位色），同时正向钉住：装配只经 {@link FieldShellBinder.build}、
     * 控件只经 {@code SceneTextInput.create} 旧工厂（已主题化本体），且
     * {@code ConfigTheme.asFormTheme()} 兼容占位实参在 Renderer 实例期间保留（G15/Support
     * 裁决：7 个 Renderer 全并入后由主代理统一收口摘除，期间禁自行摘参）。</p>
     */
    @Test
    public void sourceGuardRendererStaysNonVisual() throws Exception {
        String code = FieldShellBinderTest.codeWithoutComments(
                new String(Files.readAllBytes(
                        Paths.get("src/main/java/club/heiqi/config/ui/field/StringFieldRenderer.java")),
                        StandardCharsets.UTF_8));
        String[] banned = {
                "setBackgroundColor", "setBorderColor", "setBorderWidth", "setCornerRadius",
                "setBackdrop", "setTextColor", "applyPanelChrome", "applyOuterShell",
                "SceneControlChrome", "SceneStateColors", "SceneChromeTokens",
                "SceneSurfaceBinder", "SceneThemes.", "FormThemes.", "defaultDark",
                ".get(", "placeholderColor", "bindStandardBorder", "bindSelectableBackground",
        };
        for (String token : banned) {
            Assert.assertFalse("守卫：StringFieldRenderer 代码不得出现 " + token, code.contains(token));
        }
        Assert.assertTrue("守卫：外壳装配应只经 FieldShellBinder.build",
                code.contains("FieldShellBinder.build("));
        Assert.assertTrue("守卫：控件应只经已主题化的 SceneTextInput.create 旧工厂",
                code.contains("SceneTextInput.create("));
        Assert.assertTrue("守卫：信号收敛应复用 FieldRenderSupport.toStringSignal",
                code.contains("FieldRenderSupport.toStringSignal("));
        Assert.assertTrue("守卫：binder 兼容占位实参 ConfigTheme.asFormTheme() 必须原样保留（禁自行摘参）",
                code.contains("ConfigTheme.asFormTheme()"));
        String withoutBridgeArg = code.replace("ConfigTheme.asFormTheme()", "")
                .replaceAll("(?m)^import\\s[^;]+;$", "");
        Assert.assertFalse("守卫：ConfigTheme 只允许作为 binder 兼容占位实参出现一次（禁取静态色/快照）",
                withoutBridgeArg.contains("ConfigTheme"));
    }

    // ==================== 夹具 ====================

    /** 真实装配夹具：{@code runtime.mount} + {@code SceneThemes.withTheme} 内调 {@code renderer.render}。 */
    private MountHandle mountThroughRenderer(Signal<SceneTheme> pageTheme, FieldSpec spec,
                                             SceneNode[] holder) {
        return runtime.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(pageTheme,
                    () -> holder[0] = renderer.render(runtime, spec, adapter));
            return holder[0];
        });
    }

    /**
     * 在字段卡片中找 TextInput 控件根：跳过 header，按 B2 五子节点结构匹配
     * （与 {@code StringFieldRendererTest} 同判据，不依赖尾部固定位置——error 行是条件挂载）。
     */
    private static SceneNode findTextInputRoot(SceneNode card) {
        for (int i = 1; i < card.__getChildren().size(); i++) {
            SceneNode c = card.__getChildren().get(i);
            if (c.__getChildren().size() == TEXT_INPUT_CHILD_COUNT) {
                return c;
            }
        }
        return null;
    }

    /** 拼接控件各文本子节点内容（含选区高亮片段），用于草稿可见性断言。 */
    private static String concatenatedText(SceneNode input) {
        StringBuilder sb = new StringBuilder();
        for (SceneNode c : input.__getChildren()) {
            if (c.getText() != null) {
                sb.append(c.getText());
            }
        }
        return sb.toString();
    }
}
