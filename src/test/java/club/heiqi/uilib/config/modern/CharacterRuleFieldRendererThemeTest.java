package club.heiqi.uilib.config.modern;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

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
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * {@link CharacterRuleFieldRenderer} 的 G15/CharRule 主题跟随证据与源码守卫。
 *
 * <p>本实例的装配边界：字段壳经 {@code FieldShellBinder}（G15/Support）→ {@code FormFieldShell}
 * theme-aware 默认路径消费来源主题（GROUP 配方，surface 归表面绑定器）；行内 parse 错误文本是
 * 动态复用行的轻量内容（契约 §4.1 G13 裁决口径：零行滤镜、不装角色表面），语义色由
 * {@code SceneThemes.errorText} 公共派生入口独占重派生（G19/P-02 收编，构造期捕获来源信号）；
 * error 字号与视口高度是纯 int 排版/布局常量（契约 §4.2、Support 要点 3）；「添加/删除」保持
 * 既有手工最小文本按钮（主代理裁决：无底色/无边框/零滤镜结构不重构为 SceneButton）。本测试沿
 * <b>真实渲染器路径</b>（{@code renderer.render → FieldShellBinder.build → FormFieldShell.build
 * → forEach buildRow}）验证「默认路径 = 来源主题派生」与「换主题 → 行错误色/卡配方变」（P-04
 * 口径：切换后更新 + 深/浅两档 {@code assertNotEquals} 前提 + {@code mount + withTheme} 页作用域
 * 与镜像 {@code ConfigScreen} 的 {@code SceneThemes.install} runtime 路径），并断言行/error 节点
 * 身份、草稿与几何常量跨切换保持。</p>
 *
 * <p>默认外观说明：深色档 {@code errorText}（{@code 0xFFFFB4AB}）与迁移前
 * {@code FormTheme.defaultDark().errorColor()} 快照值同源同值，默认外观不变；换主题才显出差异。</p>
 */
public class CharacterRuleFieldRendererThemeTest {

    /** 与主文件同款排版常量（契约 §4.2 主题不接管排版，钉住防被主题信号误接管）。 */
    private static final int EXPECTED_ERROR_FONT_SIZE = 13;
    /** 与主文件同款视口高度常量（Support 要点 3 纯 int 布局入参）。 */
    private static final int EXPECTED_LIST_VIEWPORT_HEIGHT = 220;

    private SceneRuntime runtime;
    private ConfigSchema schema;
    private DraftBuffer draft;
    private DraftSignalAdapter adapter;
    private CharacterRuleFieldRenderer renderer;
    private FieldSpec spec;

    @Before
    public void setUp() throws Exception {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        runtime = new SceneRuntime(measurer);
        schema = ConfigSchema.builder("t")
                .section("fontSystem")
                    .simpleList("characterFontRules").label("字符字体规则").helper("每行一条").build()
                .endSection()
                .build();
        spec = schema.field("fontSystem.characterFontRules");
        File file = File.createTempFile("char-rule-theme-", ".yaml");
        write(file, "fontSystem:\n  characterFontRules:\n    - a=FontA\n    - noSeparator\n");
        draft = DraftBuffer.from(Authority.load(file, schema));
        adapter = new DraftSignalAdapter(runtime, draft);
        renderer = new CharacterRuleFieldRenderer();
        ReactiveScheduler.get().flush();
    }

    @After
    public void tearDown() {
        if (adapter != null) {
            adapter.dispose();
        }
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 默认路径：真实渲染器装配 = 来源主题派生 + 轻量行零滤镜 ====================

    /** 默认路径（mount + withTheme 真实装配）：卡表面/标题/helper/行错误色全随来源主题，轻量行零滤镜。 */
    @Test
    public void defaultPathRowErrorAndCardFollowSourceTheme() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThroughRenderer(Signal.create(dark), holder);
        settle();

        SceneNode card = holder[0];
        SceneSurfaceStyle group = dark.surface(SceneTheme.Role.GROUP);
        Assert.assertEquals("卡底色 = 来源主题 GROUP idle tint（归表面绑定器，契约 §4）",
                group.getIdle().getTint(), card.getBackgroundColor());
        Assert.assertEquals("卡缘色 = GROUP idle edge", group.getIdle().getEdge(), card.getBorderColor());
        Assert.assertNotNull("卡表面装有滤镜（GROUP 语义表面）", card.getBackdrop());

        SceneNode title = headerOf(card).__getChildren().get(1);
        SceneNode helper = card.__getChildren().get(1);
        Assert.assertEquals("标题前景 = 主题 foreground", dark.foreground(), title.getTextColor());
        Assert.assertEquals("helper 前景 = 主题 mutedForeground", dark.mutedForeground(), helper.getTextColor());

        SceneNode controlRoot = controlRootOf(card);
        Assert.assertEquals("视口高度是纯 int 布局入参，保留 220（契约 §4.2 主题不接管布局）",
                EXPECTED_LIST_VIEWPORT_HEIGHT, controlRoot.getPreferredHeight());

        // 行错误色 = 来源主题 errorText（深色档与旧快照值同源同值 → 默认外观不变）
        SceneNode err = errorNodeOf(rowAt(viewportOf(card), 1));
        Assert.assertEquals("行错误色 = 来源主题 errorText", dark.errorText(), err.getTextColor());
        // G19/P-02 收编钉：控件消费的是公共入口现值（同 runtime 同主题逐位相等）。
        Assert.assertEquals("行错误色 = SceneThemes.errorText 公共入口现值",
                SceneThemes.errorText(runtime).get(), Integer.valueOf(err.getTextColor()));
        Assert.assertEquals("error 字号是排版常量，保留 13（契约 §4.2）",
                EXPECTED_ERROR_FONT_SIZE, err.getFontSize());
        Assert.assertFalse("error 节点不可命中（R6 保持）", err.isHitTestable());

        // 轻量行口径（契约 §4.1 G13 裁决）：零行滤镜、不装角色表面
        SceneNode rowRoot = rowAt(viewportOf(card), 1);
        SceneNode line = rowRoot.__getChildren().get(0);
        Assert.assertNull("轻量行根零滤镜", rowRoot.getBackdrop());
        Assert.assertEquals("轻量行根不装底色", 0, rowRoot.getBackgroundColor());
        Assert.assertNull("行输入排零滤镜", line.getBackdrop());
        Assert.assertEquals("行输入排不装底色", 0, line.getBackgroundColor());
        Assert.assertNull("error 节点零滤镜", err.getBackdrop());

        // 输入控件复用已主题化 SceneTextInput（G04）：INPUT 配方自持，本类零加工
        SceneNode selectorInput = line.__getChildren().get(1);
        Assert.assertNotNull("selector 输入表面归 SceneTextInput 自持（INPUT 滤镜）",
                selectorInput.getBackdrop());
        Assert.assertEquals("selector 底色 = 来源主题 INPUT idle tint",
                dark.surface(SceneTheme.Role.INPUT).getIdle().getTint(), selectorInput.getBackgroundColor());
        handle.dispose();
    }

    /** 手工最小按钮销账用例（主代理裁决）：添加/删除按钮保持零底色、零边框、零滤镜 + 几何保留。 */
    @Test
    public void textButtonsRemainSurfaceFreeMinimalNodes() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        Signal<SceneTheme> pageTheme = Signal.create(dark);
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThroughRenderer(pageTheme, holder);
        settle();

        SceneNode controlRoot = controlRootOf(holder[0]);
        SceneNode addButton = addButtonOf(controlRoot);
        assertMinimalTextButton(addButton, "+ 添加规则");

        SceneNode deleteButton = rowAt(viewportOf(holder[0]), 0).__getChildren().get(0)
                .__getChildren().get(3);
        assertMinimalTextButton(deleteButton, "×");

        // 换主题也不给手工按钮加表面（零底色/零边框/零滤镜在浅色档同样成立）
        pageTheme.set(SceneTheme.liquidGlassLight());
        runtime.flush();
        assertMinimalTextButton(addButtonOf(controlRootOf(holder[0])), "+ 添加规则");
        assertMinimalTextButton(deleteButton, "×");
        handle.dispose();
    }

    // ==================== P-04：换主题 → 行错误色/卡配方变（切换后更新 + 两档前提） ====================

    /** 页面局部主题切换：行错误色、卡表面、标题、helper、输入底色全重派生，身份/草稿/几何保持。 */
    @Test
    public void themeSwitchReDerivesRowErrorColorAndCardSurface() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        // 两档前提：所断言分量在深/浅档互不相同，否则切换不传播、断言无意义（Computed 值记忆化）。
        Assert.assertNotEquals("前提：errorText 两档不同", dark.errorText(), light.errorText());
        Assert.assertNotEquals("前提：GROUP idle tint 两档不同",
                dark.surface(SceneTheme.Role.GROUP).getIdle().getTint(),
                light.surface(SceneTheme.Role.GROUP).getIdle().getTint());
        Assert.assertNotEquals("前提：GROUP idle edge 两档不同",
                dark.surface(SceneTheme.Role.GROUP).getIdle().getEdge(),
                light.surface(SceneTheme.Role.GROUP).getIdle().getEdge());
        Assert.assertNotEquals("前提：foreground 两档不同", dark.foreground(), light.foreground());
        Assert.assertNotEquals("前提：mutedForeground 两档不同",
                dark.mutedForeground(), light.mutedForeground());
        Assert.assertNotEquals("前提：INPUT idle tint 两档不同",
                dark.surface(SceneTheme.Role.INPUT).getIdle().getTint(),
                light.surface(SceneTheme.Role.INPUT).getIdle().getTint());

        Signal<SceneTheme> pageTheme = Signal.create(dark);
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThroughRenderer(pageTheme, holder);
        settle();

        SceneNode card = holder[0];
        SceneNode title = headerOf(card).__getChildren().get(1);
        SceneNode helper = card.__getChildren().get(1);
        SceneNode rowRoot = rowAt(viewportOf(card), 1);
        SceneNode err = errorNodeOf(rowRoot);
        SceneNode selectorInput = rowRoot.__getChildren().get(0).__getChildren().get(1);
        int errBefore = err.getTextColor();
        int cardBgBefore = card.getBackgroundColor();
        int cardEdgeBefore = card.getBorderColor();
        int titleBefore = title.getTextColor();
        int inputBefore = selectorInput.getBackgroundColor();
        Assert.assertEquals("前置：行错误色 = 深色 errorText", dark.errorText(), errBefore);
        Assert.assertEquals("前置：卡底色 = 深色 GROUP idle tint",
                dark.surface(SceneTheme.Role.GROUP).getIdle().getTint(), cardBgBefore);

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();

        Assert.assertNotEquals("切换后行错误色变化", errBefore, err.getTextColor());
        Assert.assertEquals("切换后行错误色 = 浅色 errorText", light.errorText(), err.getTextColor());
        Assert.assertNotEquals("切换后卡底色变化", cardBgBefore, card.getBackgroundColor());
        Assert.assertEquals("切换后卡底色 = 浅色 GROUP idle tint",
                light.surface(SceneTheme.Role.GROUP).getIdle().getTint(), card.getBackgroundColor());
        Assert.assertNotEquals("切换后卡缘色变化", cardEdgeBefore, card.getBorderColor());
        Assert.assertEquals("切换后卡缘色 = 浅色 GROUP idle edge",
                light.surface(SceneTheme.Role.GROUP).getIdle().getEdge(), card.getBorderColor());
        Assert.assertNotEquals("切换后标题前景变化", titleBefore, title.getTextColor());
        Assert.assertEquals("切换后标题 = 浅色 foreground", light.foreground(), title.getTextColor());
        Assert.assertEquals("切换后 helper = 浅色 mutedForeground",
                light.mutedForeground(), helper.getTextColor());
        Assert.assertNotEquals("切换后输入底色变化（控件自持随主题重派生）",
                inputBefore, selectorInput.getBackgroundColor());
        Assert.assertEquals("切换后输入底色 = 浅色 INPUT idle tint",
                light.surface(SceneTheme.Role.INPUT).getIdle().getTint(),
                selectorInput.getBackgroundColor());

        Assert.assertEquals("几何常量不受主题影响：error 字号仍 13",
                EXPECTED_ERROR_FONT_SIZE, err.getFontSize());
        Assert.assertEquals("几何常量不受主题影响：视口高度仍 220",
                EXPECTED_LIST_VIEWPORT_HEIGHT, controlRootOf(holder[0]).getPreferredHeight());
        Assert.assertNull("换主题不给轻量行装滤镜", rowRoot.getBackdrop());

        Assert.assertSame("换肤不重建：卡片身份不变", card, holder[0]);
        Assert.assertSame("换肤不重建：keyed 行身份不变", rowRoot, rowAt(viewportOf(holder[0]), 1));
        Assert.assertSame("换肤不重建：error 节点身份不变", err, errorNodeOf(rowRoot));
        Assert.assertEquals("主题切换不新增 effect",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());
        Assert.assertEquals("主题切换不丢草稿",
                Arrays.asList("a=FontA", "noSeparator"), draftValue());
        handle.dispose();
    }

    /** 镜像 ConfigScreen 真实机制：{@code SceneThemes.install} runtime 档切换，dirty 状态点重派生、草稿保持。 */
    @Test
    public void runtimeInstalledThemeSwitchKeepsDraftAndRederivesErrorAndDirtyDot() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Assert.assertNotEquals("前提：accent 两档不同（dirty 状态点跟随）", dark.accent(), light.accent());
        Assert.assertNotEquals("前提：errorText 两档不同", dark.errorText(), light.errorText());

        Signal<SceneTheme> runtimeTheme = Signal.create(dark);
        SceneThemes.install(runtime, runtimeTheme);
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = runtime.mount(new SceneNode(), () -> {
            holder[0] = renderer.render(runtime, spec, adapter);
            return holder[0];
        });
        settle();

        // 草稿事务：编辑列表（第 2 条仍无效 → 行错误保留；字段 dirty）
        adapter.onFieldEdit("fontSystem.characterFontRules", Arrays.asList("a=FontA", "XY=Font"));
        settle();
        SceneNode card = holder[0];
        SceneNode dot = headerOf(card).__getChildren().get(0);
        Assert.assertEquals("状态点文本", "●", dot.getText());
        Assert.assertEquals("dirty 状态点 = 深色 accent", dark.accent(), dot.getTextColor());
        SceneNode rowRoot = rowAt(viewportOf(card), 1);
        SceneNode err = errorNodeOf(rowRoot);
        Assert.assertEquals("前置：行错误色 = 深色 errorText", dark.errorText(), err.getTextColor());
        Assert.assertEquals("前置：卡底色 = 深色 GROUP idle tint",
                dark.surface(SceneTheme.Role.GROUP).getIdle().getTint(), card.getBackgroundColor());

        runtimeTheme.set(light);
        runtime.flush();

        Assert.assertEquals("切换后 dirty 状态点重派生为浅色 accent", light.accent(), dot.getTextColor());
        Assert.assertNotEquals("切换后行错误色变化", dark.errorText(), err.getTextColor());
        Assert.assertEquals("切换后行错误色 = 浅色 errorText", light.errorText(), err.getTextColor());
        Assert.assertEquals("切换后卡底色 = 浅色 GROUP idle tint",
                light.surface(SceneTheme.Role.GROUP).getIdle().getTint(), card.getBackgroundColor());
        Assert.assertSame("主题切换不重建 keyed 行", rowRoot, rowAt(viewportOf(holder[0]), 1));
        Assert.assertEquals("主题切换不改草稿（draftSignal）",
                Arrays.asList("a=FontA", "XY=Font"), draftValue());
        Assert.assertEquals("主题切换不改草稿（DraftBuffer）",
                Arrays.asList("a=FontA", "XY=Font"), draft.getDraft("fontSystem.characterFontRules"));
        handle.dispose();
    }

    // ==================== 生命周期：卸载回收主题派生订阅 ====================

    /** 卸载后主题派生 effect 全部回收（施工手册 §3.4：绑定归属当前 Owner）。 */
    @Test
    public void unmountReturnsEffectCountToBaseline() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThroughRenderer(Signal.create(SceneTheme.liquidGlassDark()), holder);
        settle();
        Assert.assertTrue("挂载后确有外观派生 effect",
                ReactiveTestProbe.registeredEffectCount() > baseline);
        handle.dispose();
        runtime.flush();
        Assert.assertEquals("卸载后 effect 数回到基线", baseline, ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== 源码守卫：销账显式快照与静态色写 ====================

    /**
     * G15/CharRule 销账守卫：本渲染器不得再出现显式旧主题快照、{@code ConfigTheme.ERROR_COLOR}
     * 直引、旧 chrome/静态色接缝或第二套表面绑定；行错误色只经 {@code SceneThemes.errorText}
     * 公共派生入口绑定写入（唯一写入者，G19/P-02 收编，语义较旧 {@code SceneThemes.resolve}
     * 口径更强），装配必经 {@code FieldShellBinder}（G15/收口后无 theme
     * 入参），排版/布局常量必经 {@code FormTheme.defaultDark()} 同源取值（恰 2 处，
     * 防止恢复整主题对象快照）。已主题化控件只挂载不复制样式；手工最小按钮零表面。
     *
     * <p><b>收口同步义务（G15/收口实例，已执行）</b>：{@code FieldShellBinder.build} 的
     * theme 形参已删除，本文件调用点的 {@code null} 占位实参同步摘除——原「{@code , null,}
     * 恰 1 处」计数钉按义务降为恰 0 处（保留断言本体作回潮哨兵）。</p>
     */
    @Test
    public void sourceGuardWritesColorsOnlyViaThemeBindings() throws Exception {
        String code = codeWithoutComments(
                new String(Files.readAllBytes(Paths.get(
                        "src/main/java/club/heiqi/uilib/config/modern/CharacterRuleFieldRenderer.java")),
                        StandardCharsets.UTF_8));
        String[] banned = {
                // 显式旧主题 / 快照喂装配（G15/Theme 书面警示 + 契约 §3 + Support 衔接要点 4）
                "ConfigTheme", "asFormTheme", "ERROR_COLOR",
                "theme.errorColor()", "theme.fontError()", "theme.listHeight()",
                // 旧 chrome / 静态色板接缝（契约 §4.2）
                "SceneChromeTokens", "SceneControlChrome", "SceneStateColors",
                // 表面绑定归壳与控件；轻量行禁第二套表面（契约 §4、§4.1）
                "SceneSurfaceBinder", "SceneSurfaceStyle", "FormFieldShell",
                // 静态颜色/表面写入（.dot 调用形态；方法引用绑定 errNode::setTextColor 不在此列）
                ".setTextColor(", ".setBorderColor", "setBackgroundColor", "setBorderWidth(",
                "setBackdrop", "__bindAnimatedColor", "__bindAnimatedFloat",
        };
        for (String token : banned) {
            Assert.assertFalse("守卫：CharacterRuleFieldRenderer 代码不得出现 " + token, code.contains(token));
        }
        String[] required = {
                "SceneThemes.errorText(",                      // 行错误色必经公共语义派生入口（G19/P-02 收编）
                "rt.bind(errorTextColor,",                     // 语义色只经该派生信号绑定写入（唯一写入者）
                "FieldShellBinder.build(",                     // 字段壳装配必经 Support helper
                "SceneCheckbox.create(",                       // 已主题化控件只挂载（G05）
                "SceneTextInput.create(",                      // G04
                "SceneAutocomplete.create(",                   // G04 成品 autocomplete
                "SceneScrollbar.createDefault(",               // G07 滚动条只挂载
                "rt.forEach(",                                 // keyed 行树（I5）
                "CharacterRuleItem::getId",                    // 行复用合同不动
        };
        for (String token : required) {
                Assert.assertTrue("守卫：应出现 " + token, code.contains(token));
        }
        // 计数在空白归一后的源码上进行（防换行/缩进排版影响子串匹配）。
        String flat = code.replaceAll("\\s+", " ");
        Assert.assertEquals("守卫：G15/收口后 theme 占位 null 实参恰 0 处（回潮即红）",
                0, countOccurrences(flat, ", null,"));
        Assert.assertEquals("守卫：FormTheme.defaultDark() 仅排版/布局同源常量取值恰 2 处"
                        + "（禁恢复整主题快照消费）",
                2, countOccurrences(flat, "FormTheme.defaultDark()"));
    }

    // ==================== 夹具 ====================

    /** 真实渲染器装配夹具：{@code runtime.mount} + {@code SceneThemes.withTheme} 页作用域内调 {@code renderer.render}。 */
    private MountHandle mountThroughRenderer(Signal<SceneTheme> pageTheme, SceneNode[] holder) {
        return runtime.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(pageTheme, () -> holder[0] = renderer.render(runtime, spec, adapter));
            return holder[0];
        });
    }

    /** 简单多次 flush 让响应式收敛。 */
    private void settle() {
        runtime.flush();
        runtime.flush();
    }

    /** 草稿值。 */
    private Object draftValue() {
        return adapter.draftSignal("fontSystem.characterFontRules").get();
    }

    /** header（dot + title）。 */
    private static SceneNode headerOf(SceneNode card) {
        return card.__getChildren().get(0);
    }

    /** 列表视口（本渲染器唯一 scrollable 节点）。 */
    private static SceneNode viewportOf(SceneNode card) {
        SceneNode found = findScrollable(card);
        Assert.assertNotNull("应存在滚动视口", found);
        return found;
    }

    /** 控件根 = 视口 → stackHost → 控件根 column。 */
    private static SceneNode controlRootOf(SceneNode card) {
        SceneNode viewport = viewportOf(card);
        SceneNode stackHost = viewport.__getParent();
        return stackHost.__getParent();
    }

    private static SceneNode rowAt(SceneNode viewport, int index) {
        return viewport.__getChildren().get(index);
    }

    /** 行错误节点：rowRoot 第 2 子（结构 [line, error]）；缺子说明 rt.show 未渲染。 */
    private static SceneNode errorNodeOf(SceneNode rowRoot) {
        List<SceneNode> children = rowRoot.__getChildren();
        Assert.assertTrue("该行应已渲染错误节点（rt.show 条件挂载）", children.size() >= 2);
        return children.get(1);
    }

    /** 添加按钮：控件根直接子中文本为「+ 添加规则」者。 */
    private static SceneNode addButtonOf(SceneNode controlRoot) {
        for (SceneNode child : controlRoot.__getChildren()) {
            if (!child.__getChildren().isEmpty()
                    && "+ 添加规则".equals(child.__getChildren().get(0).getText())) {
                return child;
            }
        }
        throw new AssertionError("未找到添加按钮");
    }

    /** 手工最小按钮钉：零底色/零边框/零滤镜 + 几何（padding 6、radius 4、POINTER 光标）保留。 */
    private static void assertMinimalTextButton(SceneNode button, String label) {
        Assert.assertEquals("最小按钮零底色", 0, button.getBackgroundColor());
        Assert.assertEquals("最小按钮零边框宽", 0, button.getBorderWidth());
        Assert.assertNull("最小按钮零滤镜（不随主题装表面）", button.getBackdrop());
        Assert.assertEquals("padding 几何保留（契约 §4 控件自身）", 6, button.getPaddingTop());
        Assert.assertEquals("cornerRadius 几何保留", 4, button.getCornerRadius());
        Assert.assertEquals("POINTER 光标保留", SceneCursor.POINTER, button.getCursor());
        Assert.assertEquals("按钮文案", label, button.__getChildren().get(0).getText());
    }

    private static SceneNode findScrollable(SceneNode node) {
        if (node.isScrollable()) {
            return node;
        }
        for (SceneNode child : node.__getChildren()) {
            SceneNode found = findScrollable(child);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 统计非重叠出现次数。 */
    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = haystack.indexOf(needle);
        while (idx >= 0) {
            count++;
            idx = haystack.indexOf(needle, idx + needle.length());
        }
        return count;
    }

    /**
     * 去掉 {@code //} 行注释与块注释后的源码（字符串/字符字面量原样保留）。
     *
     * <p>与 {@code FieldShellBinderTest.codeWithoutComments} 同款实现（包内私有不可跨包复用，
     * 守卫只审查代码、不误伤文档措辞）。</p>
     */
    static String codeWithoutComments(String raw) {
        String source = raw.replace("\r\n", "\n");
        StringBuilder out = new StringBuilder(source.length());
        int i = 0;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < source.length() && source.charAt(j) != c) {
                    if (source.charAt(j) == '\\') {
                        j++;
                    }
                    j++;
                }
                out.append(source, i, Math.min(j + 1, source.length()));
                i = j + 1;
            } else if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '/') {
                int j = source.indexOf('\n', i);
                i = j < 0 ? source.length() : j;
            } else if (c == '/' && i + 1 < source.length() && source.charAt(i + 1) == '*') {
                int j = source.indexOf("*/", i + 2);
                i = j < 0 ? source.length() : j + 2;
            } else {
                out.append(c);
                i++;
            }
        }
        return out.toString();
    }

    private static void write(File file, String text) throws Exception {
        FileWriter writer = new FileWriter(file);
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
    }
}
