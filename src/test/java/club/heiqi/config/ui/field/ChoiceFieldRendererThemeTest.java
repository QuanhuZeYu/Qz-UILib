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
import club.heiqi.config.ui.UiSchemaFactory;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * {@link ChoiceFieldRenderer} 默认液态玻璃主题跟随证据（G15/Choice）。
 *
 * <p><b>实例口径</b>：字段卡片表面/语义色由 {@link FieldShellBinder}（G15/Support，
 * theme-aware {@code FormFieldShell.build} 默认路径）派生；≤4 项的 Segmented 底座/段与
 * &gt;4 项的 Select 触发器表面由已迁移控件本体（G09/Segmented、G08/Select，契约 §4.1：
 * 底座 TOOLBAR + 段 INDICATOR、触发器 INPUT）自持。Renderer 自身实扫<b>零外观写入点</b>，
 * 只挂载不加工、不复制控件样式、不叠加第二层表面（契约 §4 单写入者）。本测试类经
 * 「真实宿主装配路径」（{@code runtime.mount} + {@code SceneThemes.withTheme}，同
 * G15/Support 夹具口径）钉住三件事：
 * ① 默认路径各属性恰等于来源主题对应角色配方值（证「无叠加/无竞争写入」——任何 renderer
 * 侧补色都会偏离配方值）；② 切换主题后分段/触发器/卡片重派生（P-04：切换后更新 +
 * 两档 {@code assertNotEquals} 前提 + 真实装配路径）；③ dirty/error、选项值映射与
 * 草稿语义零改动。</p>
 *
 * <p>配套源码守卫钉「非视觉职责」与占位参现状（裁决②：Renderer 期间禁自行摘除 binder 的
 * {@code theme} 兼容占位形参）；控件弹层（OVERLAY）与显式配方覆盖归 G08/G09 控件本体证据。</p>
 */
public class ChoiceFieldRendererThemeTest {

    /** 选中段 tint：INDICATOR 配方 RGB 换主题 accent、alpha 0x59（SceneThemes.selectableSurface 口径）。 */
    private static final int SELECTED_TINT_ALPHA = 0x59;

    private SceneRuntime runtime;
    private ConfigSchema schema;
    private Authority authority;
    private DraftBuffer draft;
    private DraftSignalAdapter adapter;
    private ChoiceFieldRenderer renderer;

    @Before
    public void setUp() throws Exception {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        runtime = new SceneRuntime(measurer);
        schema = UiSchemaFactory.serverSchema();
        authority = Authority.load(new File("nonexistent-choice-theme.yaml"), schema);
        draft = DraftBuffer.from(authority);
        adapter = new DraftSignalAdapter(runtime, draft);
        renderer = new ChoiceFieldRenderer();
        ReactiveScheduler.get().flush();
    }

    @After
    public void tearDown() throws Exception {
        adapter.dispose();
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== Segmented 分支（≤4 项）：默认路径 = 真实装配下的配方恰等 ====================

    /**
     * 默认路径：卡片 = 来源主题 GROUP 配方、Segmented 底座 = TOOLBAR 配方、选中段 = INDICATOR
     * 配方 accent 档、段文字 = 主题 foreground——逐属性恰等，证明 renderer 零叠加、零复制样式。
     */
    @Test
    public void segmentedBranchFollowsSourceThemeThroughRealAssembly() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        FieldSpec spec = schema.field("server.mode");
        SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThroughRenderer(Signal.create(dark), spec, adapter, holder);
        runtime.flush();

        SceneNode card = holder[0];
        SceneNode segRoot = findSegmentedRoot(card);
        Assert.assertNotNull("应找到 Segmented 底座", segRoot);
        SceneNode seg0 = segRoot.__getChildren().get(0);
        SceneNode seg1 = segRoot.__getChildren().get(1);

        Assert.assertEquals("卡底色 = GROUP 配方 idle tint",
                dark.surface(SceneTheme.Role.GROUP).getIdle().getTint(), card.getBackgroundColor());
        Assert.assertEquals("静默态卡缘 = GROUP 配方 idle edge",
                dark.surface(SceneTheme.Role.GROUP).getIdle().getEdge(), card.getBorderColor());
        Assert.assertEquals("底座底色 = TOOLBAR 配方 idle tint",
                dark.surface(SceneTheme.Role.TOOLBAR).getIdle().getTint(), segRoot.getBackgroundColor());
        Assert.assertEquals("底座缘色 = TOOLBAR 配方 idle edge",
                dark.surface(SceneTheme.Role.TOOLBAR).getIdle().getEdge(), segRoot.getBorderColor());
        Assert.assertEquals("底座圆角 = TOOLBAR 配方圆角",
                dark.surface(SceneTheme.Role.TOOLBAR).getCornerRadius(), segRoot.getCornerRadius());
        Assert.assertEquals("底座缘宽 = TOOLBAR 配方缘宽",
                dark.surface(SceneTheme.Role.TOOLBAR).getBorderWidth(), segRoot.getBorderWidth());
        Assert.assertNotNull("底座语义表面装滤镜（配方自带，renderer 未加工）", segRoot.getBackdrop());

        // draft 初值 "online" → selectedIndex=0 → 首段选中（accent tint），次段未选中（INDICATOR idle）
        Assert.assertEquals("选中段 tint = accent 色系 + 0x59 强度",
                (SELECTED_TINT_ALPHA << 24) | (dark.accent() & 0x00FFFFFF), seg0.getBackgroundColor());
        Assert.assertEquals("未选中段 = INDICATOR 配方 idle tint",
                dark.surface(SceneTheme.Role.INDICATOR).getIdle().getTint(), seg1.getBackgroundColor());
        Assert.assertEquals("段文字前景 = 主题 foreground",
                dark.foreground(), segLabel(seg0).getTextColor());
        handle.dispose();
    }

    // ==================== Segmented 分支：主题切换重派生（P-04 证据） ====================

    /**
     * 切换主题：卡片/底座/选中段/段文字全部重派生为新主题配方值；dirty 缘色语义跟随新主题
     * accent；节点身份不变、草稿与选中项值语义不丢、effect 数不增。
     */
    @Test
    public void themeSwitchRepaintsSegmentedBranchKeepingDraftDirtyAndIdentity() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        // 两档前提（P-04）：以下所有切换断言的取值档必须两档不同，否则切换不传播也恒绿
        Assert.assertNotEquals("前提：两档 foreground 不同", dark.foreground(), light.foreground());
        Assert.assertNotEquals("前提：两档 accent 不同", dark.accent(), light.accent());
        Assert.assertNotEquals("前提：两档 GROUP idle 配方不同",
                dark.surface(SceneTheme.Role.GROUP).getIdle(),
                light.surface(SceneTheme.Role.GROUP).getIdle());
        Assert.assertNotEquals("前提：两档 TOOLBAR idle 配方不同",
                dark.surface(SceneTheme.Role.TOOLBAR).getIdle(),
                light.surface(SceneTheme.Role.TOOLBAR).getIdle());

        FieldSpec spec = schema.field("server.mode");
        Signal<SceneTheme> pageTheme = Signal.create(dark);
        SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThroughRenderer(pageTheme, spec, adapter, holder);
        runtime.flush();

        // 编辑草稿 → dirty + 选中段迁移到 index 1（选项值语义先于切换成立）
        adapter.onFieldEdit("server.mode", "offline");
        runtime.flush();
        SceneNode card = holder[0];
        SceneNode segRoot = findSegmentedRoot(card);
        SceneNode seg0 = segRoot.__getChildren().get(0);
        SceneNode seg1 = segRoot.__getChildren().get(1);
        Assert.assertEquals("编辑后 dirty 缘色 = 深色档 accent", dark.accent(), card.getBorderColor());
        Assert.assertEquals("编辑后选中段 = 第 2 段（值→索引映射）",
                (SELECTED_TINT_ALPHA << 24) | (dark.accent() & 0x00FFFFFF), seg1.getBackgroundColor());
        int segBefore = segRoot.getBackgroundColor();
        Assert.assertEquals("切换前底座仍为深色档 TOOLBAR tint",
                dark.surface(SceneTheme.Role.TOOLBAR).getIdle().getTint(), segBefore);

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();

        Assert.assertNotEquals("切换后底色变（P-04：换主题→颜色变）", segBefore, segRoot.getBackgroundColor());
        Assert.assertEquals("切换后底座底色 = 浅色档 TOOLBAR idle tint",
                light.surface(SceneTheme.Role.TOOLBAR).getIdle().getTint(), segRoot.getBackgroundColor());
        Assert.assertEquals("切换后卡底色 = 浅色档 GROUP idle tint",
                light.surface(SceneTheme.Role.GROUP).getIdle().getTint(), card.getBackgroundColor());
        Assert.assertEquals("切换后 dirty 缘色跟随新主题 accent", light.accent(), card.getBorderColor());
        Assert.assertEquals("切换后选中段 tint = 浅色档 accent 映射",
                (SELECTED_TINT_ALPHA << 24) | (light.accent() & 0x00FFFFFF), seg1.getBackgroundColor());
        Assert.assertEquals("切换后段文字前景 = 新主题 foreground",
                light.foreground(), segLabel(seg1).getTextColor());
        Assert.assertSame("切换不重建卡片节点", card, holder[0]);
        Assert.assertSame("切换不重建底座节点", segRoot, findSegmentedRoot(holder[0]));
        Assert.assertEquals("切换不丢草稿值", "offline", draft.getDraft("server.mode"));
        Assert.assertEquals("切换不新增 effect", effectsBefore, ReactiveTestProbe.registeredEffectCount());
        handle.dispose();
    }

    // ==================== Select 分支（>4 项）：触发器 INPUT 配方只读复用 + 切换 ====================

    /**
     * Select 分支：触发器表面 = 来源主题 INPUT 配方（G08 已迁移控件自持，renderer 不复制样式、
     * 不叠加表面）；选中值文字 = foreground、箭头 = mutedForeground；主题切换后全部重派生，
     * 选项值与标签文本语义不受切换影响；弹层 OVERLAY 归控件本体证据。
     */
    @Test
    public void selectBranchTriggerFollowsSourceThemeAndSwitches() throws Exception {
        ConfigSchema many = UiSchemaFactory.manyChoiceSchema();
        Authority auth = Authority.load(new File("nonexistent-choice-theme2.yaml"), many);
        DraftBuffer d = DraftBuffer.from(auth);
        DraftSignalAdapter a = new DraftSignalAdapter(runtime, d);
        ReactiveScheduler.get().flush();

        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Assert.assertNotEquals("前提：两档 INPUT idle 配方不同",
                dark.surface(SceneTheme.Role.INPUT).getIdle(),
                light.surface(SceneTheme.Role.INPUT).getIdle());
        Assert.assertNotEquals("前提：两档 mutedForeground 不同",
                dark.mutedForeground(), light.mutedForeground());

        FieldSpec spec = many.field("opts.color");
        Signal<SceneTheme> pageTheme = Signal.create(dark);
        SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThroughRenderer(pageTheme, spec, a, holder);
        runtime.flush();

        SceneNode trigger = findSelectRoot(holder[0]);
        Assert.assertNotNull("应找到 Select 触发器", trigger);
        SceneNode label = findChildWithText(trigger, "red");
        SceneNode arrow = trigger.__getChildren().get(0) == label
                ? trigger.__getChildren().get(1) : trigger.__getChildren().get(0);
        Assert.assertNotNull("触发器含选中值标签", label);
        Assert.assertNotSame("触发器除标签外另有箭头指示节点", label, arrow);

        Assert.assertEquals("触发器底色 = INPUT 配方 idle tint",
                dark.surface(SceneTheme.Role.INPUT).getIdle().getTint(), trigger.getBackgroundColor());
        Assert.assertEquals("触发器缘色 = INPUT 配方 idle edge",
                dark.surface(SceneTheme.Role.INPUT).getIdle().getEdge(), trigger.getBorderColor());
        Assert.assertEquals("触发器圆角 = INPUT 配方圆角",
                dark.surface(SceneTheme.Role.INPUT).getCornerRadius(), trigger.getCornerRadius());
        Assert.assertNotNull("触发器语义表面装滤镜（INPUT 配方自带）", trigger.getBackdrop());
        Assert.assertEquals("选中值文字 = 主题 foreground", dark.foreground(), label.getTextColor());
        Assert.assertEquals("箭头指示 = 主题 mutedForeground", dark.mutedForeground(), arrow.getTextColor());

        int bgBefore = trigger.getBackgroundColor();
        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();

        Assert.assertNotEquals("切换后触发器底色变（P-04：换主题→颜色变）", bgBefore, trigger.getBackgroundColor());
        Assert.assertEquals("切换后触发器底色 = 浅色档 INPUT idle tint",
                light.surface(SceneTheme.Role.INPUT).getIdle().getTint(), trigger.getBackgroundColor());
        Assert.assertEquals("切换后缘色 = 浅色档 INPUT idle edge",
                light.surface(SceneTheme.Role.INPUT).getIdle().getEdge(), trigger.getBorderColor());
        a.onFieldEdit("opts.color", "cyan");
        runtime.flush();
        SceneNode relabeled = findChildWithText(trigger, "cyan");
        Assert.assertNotNull("切换后选项值→标签文本映射保持（值语义零改动）", relabeled);
        Assert.assertEquals("切换后编辑的选项文字 = 新主题 foreground",
                light.foreground(), relabeled.getTextColor());
        Assert.assertEquals("切换后 dirty 缘色 = 新主题 accent", light.accent(), holder[0].getBorderColor());
        Assert.assertSame("切换不重建触发器节点", trigger, findSelectRoot(holder[0]));
        Assert.assertEquals("主题切换不丢草稿", "cyan", d.getDraft("opts.color"));
        Assert.assertEquals("切换不新增 effect", effectsBefore, ReactiveTestProbe.registeredEffectCount());
        handle.dispose();
        a.dispose();
    }

    // ==================== 源码守卫：非视觉职责 + 占位参现状（裁决②） ====================

    /**
     * 守卫：{@code ChoiceFieldRenderer} 零外观写入、零取色、零旧接缝——所有表面/前景属性写入
     * API 与静态色字面量禁出现；已迁移控件只经 {@code create(rt, props)} 只读复用；对
     * {@code ConfigTheme.asFormTheme()} 的引用仅允许为 {@link FieldShellBinder#build} 的兼容
     * 占位实参（G15/Support 裁决②：默认路径不消费、Renderer 期间禁自行摘参）。
     *
     * <p><b>G15/收口实例同步义务</b>：摘除 binder 占位形参时，须同批把本守卫的占位计数断言与
     * 两处调用点一并更新，不得只改一侧。</p>
     */
    @Test
    public void sourceGuardRendererCarriesNoVisualWritesOnlyPlaceholderArgs() throws Exception {
        String code = FieldShellBinderTest.codeWithoutComments(
                new String(Files.readAllBytes(Paths.get(
                        "src/main/java/club/heiqi/config/ui/field/ChoiceFieldRenderer.java")),
                        StandardCharsets.UTF_8));
        String[] banned = {
                "setBackgroundColor", "setBorderColor", "setBorderWidth", "setCornerRadius",
                "setBackdrop", "setTextColor", "setForeground", "SurfaceElevation",
                "bindStandardBorder", "bindSelectableBackground",
                "SceneSurfaceBinder", "SceneControlChrome", "SceneStateColors", "SceneChromeTokens",
                "SceneThemes", "ui.scene.theme", "FormFieldShell", "0x",
        };
        for (String token : banned) {
            Assert.assertFalse("守卫：ChoiceFieldRenderer 代码不得出现 " + token, code.contains(token));
        }
        // 已迁移控件只读复用：各恰一处 create，无第二份样式构造
        Assert.assertEquals("Segmented 只经工厂挂载恰 1 处",
                1, countOccurrences(code, "SceneSegmented.create(rt, props)"));
        Assert.assertEquals("Select 只经工厂挂载恰 1 处",
                1, countOccurrences(code, "SceneSelect.create(rt, props)"));
        // 装配只走 Support binder；theme 实参仅允许占位形态恰 2 处（Segmented/Select 分支各一）
        Assert.assertEquals("外壳装配只经 FieldShellBinder 恰 2 处",
                2, countOccurrences(code, "FieldShellBinder.build(rt, spec, adapter,"));
        Assert.assertEquals("显式主题只允许 binder 占位实参形态恰 2 处",
                2, countOccurrences(code, "ConfigTheme.asFormTheme())"));
        Assert.assertEquals("ConfigTheme 引用总数 = import + 2 占位实参",
                3, countOccurrences(code, "ConfigTheme"));
        // 表面/语义色的写入点在 Support 与控件本体，renderer 不得截胡
        Assert.assertFalse("守卫：不得直接组装 FormFieldShell（装配唯一经 FieldShellBinder）",
                code.contains("FormPageShell") || code.contains("buildBorderless"));
    }

    // ==================== 夹具 ====================

    /** 真实装配夹具：{@code runtime.mount} + {@code SceneThemes.withTheme} 内调 renderer.render。 */
    private MountHandle mountThroughRenderer(Signal<SceneTheme> pageTheme, FieldSpec spec,
                                             DraftSignalAdapter a, SceneNode[] holder) {
        return runtime.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(pageTheme, () -> holder[0] = renderer.render(runtime, spec, a));
            return holder[0];
        });
    }

    /** Segmented 底座根：card 子节点（跳过 header）中首个有子节点者（同 ChoiceFieldRendererTest 口径）。 */
    private static SceneNode findSegmentedRoot(SceneNode card) {
        for (int i = 1; i < card.__getChildren().size(); i++) {
            SceneNode c = card.__getChildren().get(i);
            if (c.__getChildren().size() > 0) {
                return c;
            }
        }
        return null;
    }

    /** Select 触发器根：含恰 2 子（label + arrow）的控件根（同 ChoiceFieldRendererTest 口径）。 */
    private static SceneNode findSelectRoot(SceneNode card) {
        for (int i = 1; i < card.__getChildren().size(); i++) {
            SceneNode c = card.__getChildren().get(i);
            if (c.__getChildren().size() == 2) {
                return c;
            }
        }
        return null;
    }

    /** 段文字节点：segment 的唯一子节点（primitive 不挂 label，由控件 appendChild 恰 1 子）。 */
    private static SceneNode segLabel(SceneNode segment) {
        return segment.__getChildren().get(0);
    }

    /** 按文本内容查找直接子节点（bindText 派生在 flush 后可见）。 */
    private static SceneNode findChildWithText(SceneNode parent, String text) {
        for (SceneNode c : parent.__getChildren()) {
            if (text.equals(c.getText())) {
                return c;
            }
        }
        return null;
    }

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
