package club.heiqi.config.ui.field;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.config.runtime.Authority;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.schema.Values;
import club.heiqi.config.ui.DraftSignalAdapter;
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
 * {@link StructuredListFieldRenderer} 的 G15/StructuredList 主题跟随证据与源码守卫。
 *
 * <p>本实例的装配边界：字段壳经 {@code FormFieldShell.buildBorderless} theme-aware 无边框重载
 * 消费来源主题（无边框保持无边框）；行卡为动态复用行，按契约 §4.1（G13 裁决）轻量口径只做
 * 主题缘色派生（零行滤镜）；member 标签行经 {@code FormLabeledControl.vertical(rt, ...)}
 * （G11）；SimpleList/Checkbox/Button 等已迁移控件外观自持。本测试沿<b>真实渲染器路径</b>
 * （{@code renderer.render → FormFieldShell.buildBorderless → forEach buildRow/buildMember}）
 * 验证「默认路径 = 来源主题派生」与「换主题 → 颜色变」（P-04 口径：切换后更新 + 深/浅两档
 * {@code assertNotEquals} 前提 + {@code mount + withTheme} 页作用域与镜像 {@code ConfigScreen}
 * 的 {@code SceneThemes.install} runtime 路径），并断言草稿/身份跨切换保持。</p>
 */
public class StructuredListFieldRendererThemeTest {

    private SceneRuntime runtime;
    private ConfigSchema schema;
    private DraftSignalAdapter adapter;
    private StructuredListFieldRenderer renderer;

    @Before
    public void setUp() throws Exception {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        runtime = new SceneRuntime(measurer);
        schema = ConfigSchema.builder("t")
                .section("general")
                    .structuredList("rules", Values.objectWithIdentity("id",
                            Values.member("id", Values.string()),
                            Values.member("members", Values.list(Values.string()))))
                    .build()
                .endSection()
                .build();
        File file = File.createTempFile("structured-list-theme-", ".yaml");
        write(file, "general:\n  rules:\n    - id: first\n      members:\n        - alpha\n");
        DraftBuffer draft = DraftBuffer.from(Authority.load(file, schema));
        adapter = new DraftSignalAdapter(runtime, draft);
        renderer = new StructuredListFieldRenderer();
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

    /** 默认路径（mount + withTheme 真实装配）：壳保持无边框，行缘色/成员前景/语义色全随来源主题。 */
    @Test
    public void defaultPathFollowsSourceThemeThroughRealRendererAssembly() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        FieldSpec spec = schema.field("general.rules");
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThroughRenderer(Signal.create(dark), spec, holder);
        runtime.flush();
        runtime.flush();

        SceneNode card = holder[0];
        Assert.assertEquals("无边框壳不装底色（契约 §4.1：不因主题加出卡片）", 0, card.getBackgroundColor());
        Assert.assertEquals("无边框壳边框宽保持 0", 0, card.getBorderWidth());
        Assert.assertEquals("无边框壳圆角保持 0", 0, card.getCornerRadius());
        Assert.assertNull("无边框壳不装行滤镜", card.getBackdrop());

        SceneNode title = card.__getChildren().get(0).__getChildren().get(1);
        Assert.assertEquals("字段标题前景 = 来源主题 foreground", dark.foreground(), title.getTextColor());

        SceneNode row = firstRow(card);
        Assert.assertEquals("行缘色 = 来源主题 borderDefault（深色档与旧 BORDER_DEFAULT 同值 0xFF938F99）",
                dark.borderDefault(), row.getBorderColor());
        Assert.assertEquals("边框宽是纯 int 几何，保留（契约 §4.2 主题不接管布局）", 1, row.getBorderWidth());
        Assert.assertEquals("圆角 4 是纯 int 几何，保留（契约 §4.2）", 4, row.getCornerRadius());
        Assert.assertNull("轻量行零滤镜（契约 §4.1 G13 裁决）", row.getBackdrop());
        Assert.assertEquals("轻量行不装角色底色，保持透明", 0, row.getBackgroundColor());

        SceneNode[] memberForm = memberFormRow(card, "id");
        Assert.assertEquals("member 标签前景 = vertical(rt,...) 主题正文前景",
                dark.foreground(), memberForm[0].getTextColor());
        Assert.assertEquals("member error 语义色 = 来源主题 errorText（深色档与旧 ERROR_COLOR 同值）",
                dark.errorText(), memberForm[1].getTextColor());

        SceneNode addButton = addButton(card);
        Assert.assertEquals("操作按钮表面归 SceneButton（G03）自持 = BUTTON_STANDARD idle tint，本类零复制",
                dark.surface(SceneTheme.Role.BUTTON_STANDARD).getIdle().getTint(), addButton.getBackgroundColor());
        handle.dispose();
    }

    // ==================== P-04：换主题 → 颜色变（切换后更新 + 两档前提） ====================

    /** 页面局部主题切换：行缘色、member 标签/error、标题、按钮表面全重派生，身份与草稿保持。 */
    @Test
    public void themeSwitchUpdatesRowBorderMemberLabelErrorTitleAndButton() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        // 两档前提：所断言分量在深/浅档互不相同，否则切换不传播、断言无意义（Computed 值记忆化）。
        Assert.assertNotEquals("前提：borderDefault 两档不同", dark.borderDefault(), light.borderDefault());
        Assert.assertNotEquals("前提：errorText 两档不同", dark.errorText(), light.errorText());
        Assert.assertNotEquals("前提：foreground 两档不同", dark.foreground(), light.foreground());
        Assert.assertNotEquals("前提：mutedForeground 两档不同",
                dark.mutedForeground(), light.mutedForeground());
        Assert.assertNotEquals("前提：BUTTON_STANDARD idle tint 两档不同",
                dark.surface(SceneTheme.Role.BUTTON_STANDARD).getIdle().getTint(),
                light.surface(SceneTheme.Role.BUTTON_STANDARD).getIdle().getTint());

        FieldSpec spec = schema.field("general.rules");
        Signal<SceneTheme> pageTheme = Signal.create(dark);
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThroughRenderer(pageTheme, spec, holder);
        runtime.flush();
        runtime.flush();

        SceneNode card = holder[0];
        SceneNode title = card.__getChildren().get(0).__getChildren().get(1);
        SceneNode row = firstRow(card);
        SceneNode[] memberForm = memberFormRow(card, "id");
        SceneNode labelNode = memberForm[0];
        SceneNode errorNode = memberForm[1];
        SceneNode addButton = addButton(card);
        Assert.assertEquals("前置：行缘色 = 深色 borderDefault", dark.borderDefault(), row.getBorderColor());
        Assert.assertEquals("前置：member 标签 = 深色 foreground", dark.foreground(), labelNode.getTextColor());
        Assert.assertEquals("前置：error 语义色 = 深色 errorText", dark.errorText(), errorNode.getTextColor());
        Assert.assertEquals("前置：标题 = 深色 foreground", dark.foreground(), title.getTextColor());
        int rowBorderBefore = row.getBorderColor();
        int labelBefore = labelNode.getTextColor();
        int errorBefore = errorNode.getTextColor();
        int titleBefore = title.getTextColor();
        int buttonBefore = addButton.getBackgroundColor();

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();

        Assert.assertNotEquals("切换后行缘色变化", rowBorderBefore, row.getBorderColor());
        Assert.assertEquals("切换后行缘色 = 浅色 borderDefault", light.borderDefault(), row.getBorderColor());
        Assert.assertNotEquals("切换后 member 标签变化", labelBefore, labelNode.getTextColor());
        Assert.assertEquals("切换后 member 标签 = 浅色 foreground", light.foreground(), labelNode.getTextColor());
        Assert.assertNotEquals("切换后 error 语义色变化", errorBefore, errorNode.getTextColor());
        Assert.assertEquals("切换后 error 语义色 = 浅色 errorText", light.errorText(), errorNode.getTextColor());
        Assert.assertNotEquals("切换后字段标题变化", titleBefore, title.getTextColor());
        Assert.assertEquals("切换后字段标题 = 浅色 foreground", light.foreground(), title.getTextColor());
        Assert.assertNotEquals("切换后按钮表面变化（归控件自持，随主题重派生）",
                buttonBefore, addButton.getBackgroundColor());
        Assert.assertEquals("切换后按钮表面 = 浅色 BUTTON_STANDARD idle tint",
                light.surface(SceneTheme.Role.BUTTON_STANDARD).getIdle().getTint(),
                addButton.getBackgroundColor());
        Assert.assertEquals("几何常量不受主题影响：边框宽仍 1", 1, row.getBorderWidth());
        Assert.assertEquals("几何常量不受主题影响：圆角仍 4", 4, row.getCornerRadius());
        Assert.assertNull("换主题不给轻量行装滤镜", row.getBackdrop());
        Assert.assertNull("换主题不给无边框壳装滤镜", card.getBackdrop());

        Assert.assertSame("换肤不重建：卡片身份不变", card, holder[0]);
        Assert.assertSame("换肤不重建：keyed 行身份不变", row, firstRow(holder[0]));
        Assert.assertSame("换肤不重建：member 标签身份不变", labelNode, memberFormRow(holder[0], "id")[0]);
        Assert.assertSame("换肤不重建：error 节点身份不变", errorNode, memberFormRow(holder[0], "id")[1]);
        Assert.assertEquals("主题切换不新增 effect",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());
        Assert.assertEquals("主题切换不丢草稿", "first", rowIds().get(0));
        handle.dispose();
    }

    /** 镜像 ConfigScreen 真实机制：{@code SceneThemes.install} runtime 档切换，dirty 状态点与草稿跨切换保持。 */
    @Test
    public void runtimeInstalledThemeSwitchKeepsDraftAndReDerivesDirtyDot() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        Assert.assertNotEquals("前提：accent 两档不同（dirty 状态点跟随）", dark.accent(), light.accent());

        Signal<SceneTheme> runtimeTheme = Signal.create(dark);
        SceneThemes.install(runtime, runtimeTheme);
        FieldSpec spec = schema.field("general.rules");
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = runtime.mount(new SceneNode(), () -> {
            holder[0] = renderer.render(runtime, spec, adapter);
            return holder[0];
        });
        runtime.flush();
        runtime.flush();

        // 草稿事务：保持 identity 不变、改 members 值 → dirty（状态点 = dirtyColor = accent）；
        // 外观切换不得触碰草稿，也不得重建 keyed 行。
        List<Map<String, Object>> edited = Arrays.asList(rule("first", "beta"));
        adapter.onFieldEdit("general.rules", edited);
        runtime.flush();
        SceneNode card = holder[0];
        SceneNode dot = card.__getChildren().get(0).__getChildren().get(0);
        Assert.assertEquals("状态点文本", "●", dot.getText());
        Assert.assertEquals("dirty 状态点 = 深色 accent", dark.accent(), dot.getTextColor());
        SceneNode row = firstRow(card);
        Assert.assertEquals("前置：行缘色 = 深色 borderDefault", dark.borderDefault(), row.getBorderColor());

        runtimeTheme.set(light);
        runtime.flush();

        Assert.assertEquals("切换后 dirty 状态点重派生为浅色 accent", light.accent(), dot.getTextColor());
        Assert.assertEquals("切换后行缘色 = 浅色 borderDefault", light.borderDefault(), row.getBorderColor());
        Assert.assertEquals("切换后 member error = 浅色 errorText",
                light.errorText(), memberFormRow(holder[0], "id")[1].getTextColor());
        Assert.assertEquals("主题切换不改草稿（draftSignal）", "first", rowIds().get(0));
        Assert.assertEquals("主题切换不改草稿（成员值）", Arrays.asList("beta"), rowMembers());
        Assert.assertSame("主题切换不重建 keyed 行", row, firstRow(holder[0]));
        handle.dispose();
    }

    // ==================== 生命周期：卸载回收主题派生订阅 ====================

    /** 卸载后主题派生 effect 全部回收（施工手册 §3.4：绑定归属当前 Owner）。 */
    @Test
    public void unmountReturnsEffectCountToBaseline() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        FieldSpec spec = schema.field("general.rules");
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThroughRenderer(Signal.create(SceneTheme.liquidGlassDark()), spec, holder);
        runtime.flush();
        runtime.flush();
        Assert.assertTrue("挂载后确有外观派生 effect",
                ReactiveTestProbe.registeredEffectCount() > baseline);
        handle.dispose();
        runtime.flush();
        Assert.assertEquals("卸载后 effect 数回到基线", baseline, ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== 源码守卫：销账显式快照与静态色写 ====================

    /**
     * G15/StructuredList 销账守卫：本渲染器不得再出现显式旧主题快照、旧 chrome/静态色接缝，
     * 颜色属性只经 {@code SceneThemes.resolve} 派生绑定写入（唯一写入者），几何/布局保持纯 int；
     * 控件外观只归已迁移控件自持（只挂载不复制样式、不给轻量行装表面）。
     */
    @Test
    public void sourceGuardRendererWritesColorsOnlyViaThemeBindings() throws Exception {
        String code = FieldShellBinderTest.codeWithoutComments(
                new String(Files.readAllBytes(Paths.get(
                        "src/main/java/club/heiqi/config/ui/field/StructuredListFieldRenderer.java")),
                        StandardCharsets.UTF_8));
        String[] banned = {
                // 显式旧主题 / 快照喂壳（契约 §3、G15/Theme 书面警示、Support 衔接要点 4）
                "ConfigTheme", "asFormTheme", "FormTheme",
                // 旧 chrome / 静态色板接缝（契约 §4.2）
                "SceneChromeTokens", "SceneControlChrome", "SceneStateColors",
                // 表面绑定归壳与控件；轻量行禁第二套表面（契约 §4、§4.1）
                "SceneSurfaceBinder", "SceneSurfaceStyle",
                // 静态颜色写入（.dot 调用形态；方法引用绑定 root::setXxx 不在此列）
                ".setTextColor(", ".setBorderColor(", ".setBackgroundColor", "setBackdrop", "setForeground(",
                "__bindAnimatedColor", "__bindAnimatedFloat",
        };
        for (String token : banned) {
            Assert.assertFalse("守卫：StructuredListFieldRenderer 代码不得出现 " + token, code.contains(token));
        }
        String[] required = {
                "FormFieldShell.buildBorderless(",           // 字段壳只走 theme-aware 无边框重载
                "SceneThemes.resolve(",                      // 颜色派生必经来源主题
                "rt.bindComputed(",                          // 缘色/语义色只经派生绑定写入
                "FormLabeledControl.vertical(rt,",           // member 标签行必经 theme-aware 重载（G11）
                "SceneButton.create(",                       // 操作按钮复用已主题化控件（G03）
                "SceneSimpleList.create(",                   // raw 列表复用已主题化控件（G12）
                "SceneCheckbox.create(",                     // choice 列表复用已主题化控件（G05）
        };
        for (String token : required) {
            Assert.assertTrue("守卫：应出现 " + token, code.contains(token));
        }
    }

    // ==================== 夹具 ====================

    /** 真实渲染器装配夹具：{@code runtime.mount} + {@code SceneThemes.withTheme} 页作用域内调 {@code renderer.render}。 */
    private MountHandle mountThroughRenderer(Signal<SceneTheme> pageTheme, FieldSpec spec, SceneNode[] holder) {
        return runtime.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(pageTheme, () -> holder[0] = renderer.render(runtime, spec, adapter));
            return holder[0];
        });
    }

    /** 列表视口（本渲染器唯一 scrollable 节点）。 */
    private static SceneNode viewport(SceneNode card) {
        SceneNode found = findScrollable(card);
        Assert.assertNotNull("应存在结构化列表视口", found);
        return found;
    }

    /** 第一条 keyed 行。 */
    private SceneNode firstRow(SceneNode card) {
        List<SceneNode> rows = viewport(card).__getChildren();
        Assert.assertFalse("应有 keyed 行", rows.isEmpty());
        return rows.get(0);
    }

    /**
     * member 表单的两类文字节点：{@code [0]} = 标签文字节点（labelSlot 内），
     * {@code [1]} = wrapper 末子 error 文本节点。
     */
    private static SceneNode[] memberFormRow(SceneNode card, String member) {
        SceneNode wrapper = findMemberWrapper(card, member);
        Assert.assertNotNull("未找到 member: " + member, wrapper);
        SceneNode form = findDirectMemberForm(wrapper, member);
        Assert.assertNotNull("member 应经 FormLabeledControl 标签行装配", form);
        SceneNode labelNode = form.__getChildren().get(0).__getChildren().get(0);
        Assert.assertEquals(member, labelNode.getText());
        return new SceneNode[] {labelNode, wrapper.__getChildren().get(wrapper.__getChildren().size() - 1)};
    }

    /** 添加按钮（控件根 = [视口, 添加按钮] 的兄弟槽）。 */
    private static SceneNode addButton(SceneNode card) {
        SceneNode viewport = viewport(card);
        SceneNode control = viewport.__getParent();
        for (SceneNode child : control.__getChildren()) {
            if (child != viewport && !child.__getChildren().isEmpty()
                    && "添加".equals(child.__getChildren().get(0).getText())) {
                return child;
            }
        }
        throw new AssertionError("未找到结构化列表添加按钮");
    }

    private static SceneNode findMemberWrapper(SceneNode node, String member) {
        for (SceneNode child : node.__getChildren()) {
            if (findDirectMemberForm(child, member) != null) {
                return child;
            }
            SceneNode nested = findMemberWrapper(child, member);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static SceneNode findDirectMemberForm(SceneNode wrapper, String member) {
        for (SceneNode candidate : wrapper.__getChildren()) {
            if (candidate.__getChildren().isEmpty()) {
                continue;
            }
            SceneNode labelSlot = candidate.__getChildren().get(0);
            if (!labelSlot.__getChildren().isEmpty()
                    && member.equals(labelSlot.__getChildren().get(0).getText())) {
                return candidate;
            }
        }
        return null;
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

    @SuppressWarnings("unchecked")
    private List<String> rowIds() {
        List<Map<String, Object>> rows = draftRows();
        return Arrays.asList(String.valueOf(rows.get(0).get("id")));
    }

    @SuppressWarnings("unchecked")
    private List<Object> rowMembers() {
        return (List<Object>) draftRows().get(0).get("members");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> draftRows() {
        return (List<Map<String, Object>>) adapter.draftSignal("general.rules").get();
    }

    private static Map<String, Object> rule(String id, String member) {
        LinkedHashMap<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("id", id);
        row.put("members", Arrays.<Object>asList(member));
        return row;
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
