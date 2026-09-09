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
import club.heiqi.uilib.ui.scene.form.FormTheme;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * {@link FieldShellBinder} 单元测试：薄 helper 装配 FormFieldShell 外壳的契约。
 *
 * <p>验证 binder 正确把 spec/adapter 拆解为 FormFieldShell 需要的参数：
 * 标题回退（label → path）、helper 透传、error/dirty signal 桥接、controlFn 注入、控件高度入参。</p>
 */
public class FieldShellBinderTest {

    private SceneRuntime runtime;
    private ConfigSchema schema;
    private Authority authority;
    private DraftBuffer draft;
    private DraftSignalAdapter adapter;
    private FormTheme theme;

    @Before
    public void setUp() throws Exception {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        runtime = new SceneRuntime(measurer);
        schema = ConfigSchema.builder("t")
                .section("server")
                    .string("host").label("Host").helper("server host").build()
                .endSection()
                .build();
        authority = Authority.load(new File("nonexistent-binder.yaml"), schema);
        draft = DraftBuffer.from(authority);
        adapter = new DraftSignalAdapter(runtime, draft);
        // G15/收口：夹具的 theme 字段只作 controlHeight 纯 int 布局源（FormTheme.defaultDark()
        // 同源常量，与旧 ConfigTheme.asFormTheme() 缓存值逐值相等）——binder 已无 theme 形参，
        // 本测试不再经兼容入口持整主题对象（兼容入口覆盖归 ConfigThemeTest）。
        theme = FormTheme.defaultDark();
        ReactiveScheduler.get().flush();
    }

    @After
    public void tearDown() {
        adapter.dispose();
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    /** 默认重载（inputHeight）：返回非 null card，标题显示 label。 */
    @Test
    public void defaultOverloadReturnsCardWithLabel() throws Exception {
        FieldSpec spec = schema.field("server.host");
        SceneNode card = FieldShellBinder.build(runtime, spec, adapter, SceneNode::new);
        runtime.flush();
        Assert.assertNotNull("返回非 null card", card);
        SceneNode header = card.__getChildren().get(0);
        SceneNode title = header.__getChildren().get(1);
        Assert.assertEquals("title 显示 label 'Host'", "Host", title.getText());
    }

    /** 显式 controlHeight 重载：行为与默认一致，仅控件根 preferredHeight 不同。 */
    @Test
    public void explicitControlHeightOverloadReturnsCard() throws Exception {
        FieldSpec spec = schema.field("server.host");
        SceneNode card = FieldShellBinder.build(runtime, spec, adapter,
                SceneNode::new, theme.listHeight());
        runtime.flush();
        Assert.assertNotNull("返回非 null card", card);
        Assert.assertEquals("title 仍显示 label", "Host",
                card.__getChildren().get(0).__getChildren().get(1).getText());
    }

    /** 空输入时显示：label=null 时回退 path，helper=空时不崩。 */
    @Test
    public void labelNullFallsBackToPath() throws Exception {
        ConfigSchema s = ConfigSchema.builder("t")
                .section("a")
                    .string("k").build()
                .endSection()
                .build();
        Authority auth = Authority.load(new File("nonexistent-binder2.yaml"), s);
        DraftBuffer d = DraftBuffer.from(auth);
        DraftSignalAdapter a = new DraftSignalAdapter(runtime, d);
        ReactiveScheduler.get().flush();
        FieldSpec spec = s.field("a.k");
        SceneNode card = FieldShellBinder.build(runtime, spec, a, SceneNode::new);
        runtime.flush();
        Assert.assertNotNull("空 schema 渲染不崩", card);
        Assert.assertEquals("label null 回退 path", "a.k",
                card.__getChildren().get(0).__getChildren().get(1).getText());
        a.dispose();
    }

    /** error signal 桥接：触发 required 违反后 card 边框色变化。 */
    @Test
    public void errorSignalBridgedToBorderColor() throws Exception {
        ConfigSchema s = ConfigSchema.builder("t")
                .section("server")
                    .string("host").defaultValue("v").required().maxLength(2).build()
                .endSection()
                .build();
        Authority auth = Authority.load(new File("nonexistent-binder3.yaml"), s);
        DraftBuffer d = DraftBuffer.from(auth);
        DraftSignalAdapter a = new DraftSignalAdapter(runtime, d);
        ReactiveScheduler.get().flush();
        FieldSpec spec = s.field("server.host");
        SceneNode card = FieldShellBinder.build(runtime, spec, a, SceneNode::new);
        runtime.flush();
        int borderBefore = card.getBorderColor();
        a.onFieldEdit("server.host", "abc"); // maxLength 违反
        runtime.flush();
        int borderAfter = card.getBorderColor();
        Assert.assertNotEquals("error 时边框色变化", borderBefore, borderAfter);
        a.dispose();
    }

    // ==================== G15/Support：默认路径 = GROUP 角色配方（真实装配） ====================

    /** 真实装配（mount + withTheme）下字段卡片 = 来源主题 GROUP 配方 + 主题语义文字色。 */
    @Test
    public void defaultPathUsesGroupRecipeThroughRealAssembly() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        FieldSpec spec = schema.field("server.host");
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThroughBinder(Signal.create(dark), spec, adapter, holder);
        runtime.flush();

        SceneNode card = holder[0];
        SceneSurfaceStyle group = dark.surface(SceneTheme.Role.GROUP);
        Assert.assertEquals("卡底色 = GROUP 配方 idle tint", group.getIdle().getTint(), card.getBackgroundColor());
        Assert.assertEquals("卡缘色 = GROUP 配方 idle edge", group.getIdle().getEdge(), card.getBorderColor());
        Assert.assertEquals("卡圆角 = GROUP 配方圆角", group.getCornerRadius(), card.getCornerRadius());
        Assert.assertEquals("卡边框宽 = GROUP 配方边框宽", group.getBorderWidth(), card.getBorderWidth());
        Assert.assertNotNull("GROUP 配方表面装有滤镜（语义表面采样背景）", card.getBackdrop());
        SceneNode title = card.__getChildren().get(0).__getChildren().get(1);
        Assert.assertEquals("标题前景 = 主题 foreground", dark.foreground(), title.getTextColor());
        SceneNode helperNode = card.__getChildren().get(1);
        Assert.assertEquals("helper 前景 = 主题 mutedForeground",
                dark.mutedForeground(), helperNode.getTextColor());
        handle.dispose();
    }

    /** 主题切换：卡片表面与文字语义重派生，草稿不丢、节点身份不变、effect 数不增。 */
    @Test
    public void themeSwitchRepaintsCardKeepingDraftAndIdentity() {
        SceneTheme dark = SceneTheme.liquidGlassDark();
        SceneTheme light = SceneTheme.liquidGlassLight();
        FieldSpec spec = schema.field("server.host");
        Signal<SceneTheme> pageTheme = Signal.create(dark);
        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThroughBinder(pageTheme, spec, adapter, holder);
        runtime.flush();

        adapter.onFieldEdit("server.host", "typed-draft");
        runtime.flush();
        SceneNode card = holder[0];
        SceneNode titleNode = card.__getChildren().get(0).__getChildren().get(1);
        Assert.assertEquals("前置：dirty 缘色 = 深色档 accent", dark.accent(), card.getBorderColor());
        Assert.assertNotEquals("前置：两档 GROUP idle 配方不同（否则切换不传播）",
                dark.surface(SceneTheme.Role.GROUP).getIdle(),
                light.surface(SceneTheme.Role.GROUP).getIdle());

        int effectsBefore = ReactiveTestProbe.registeredEffectCount();
        pageTheme.set(light);
        runtime.flush();

        Assert.assertEquals("切换后底色 = 浅色 GROUP idle tint",
                light.surface(SceneTheme.Role.GROUP).getIdle().getTint(), card.getBackgroundColor());
        Assert.assertEquals("切换后 dirty 缘色仍取新主题 accent", light.accent(), card.getBorderColor());
        Assert.assertEquals("切换后标题前景 = 新主题 foreground", light.foreground(), titleNode.getTextColor());
        Assert.assertSame("卡片节点身份不变", card, holder[0]);
        Assert.assertSame("标题节点身份不变", titleNode,
                holder[0].__getChildren().get(0).__getChildren().get(1));
        Assert.assertEquals("主题切换不丢草稿", "typed-draft", adapter.draftSignal("server.host").get());
        Assert.assertEquals("主题切换不新增 effect",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());
        handle.dispose();
    }

    /** dirty / error 语义在默认路径可辨：clean=配方缘色、dirty=accent、error=errorText 优先。 */
    @Test
    public void dirtyAndErrorRemainDistinctInDefaultPath() throws Exception {
        ConfigSchema s = ConfigSchema.builder("t")
                .section("server")
                    .string("host").defaultValue("v").required().maxLength(2).build()
                .endSection()
                .build();
        Authority auth = Authority.load(new File("nonexistent-binder4.yaml"), s);
        DraftBuffer d = DraftBuffer.from(auth);
        final DraftSignalAdapter a = new DraftSignalAdapter(runtime, d);
        ReactiveScheduler.get().flush();
        FieldSpec spec = s.field("server.host");
        SceneTheme dark = SceneTheme.liquidGlassDark();
        Assert.assertNotEquals("前置：accent 与 errorText 可辨", dark.accent(), dark.errorText());

        final SceneNode[] holder = new SceneNode[1];
        MountHandle handle = mountThroughBinder(Signal.create(dark), spec, a, holder);
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
        a.dispose();
    }

    // ==================== G15/Support 源码守卫 ====================

    /**
     * binder 零主题快照、零旧接缝：只把 spec/adapter 拆解后转发 theme-aware
     * {@code FormFieldShell.build}。
     *
     * <p><b>G15/收口强化</b>：theme 兼容占位形参已从签名删除，守卫同步钉「FormTheme」
     * 标识符整体禁出现——类型级封死「回喂整主题对象」的复潮路径（多行高度只允许
     * controlHeight 纯 int 入参，调用点自带同源常量）。</p>
     */
    @Test
    public void sourceGuardBinderNeverConsumesExplicitThemeSnapshot() throws Exception {
        String code = codeWithoutComments(
                new String(Files.readAllBytes(
                        Paths.get("src/main/java/club/heiqi/config/ui/field/FieldShellBinder.java")),
                        StandardCharsets.UTF_8));
        String[] banned = {
                "ConfigTheme", "asFormTheme", ".get(", "theme.inputHeight",
                "FormTheme",
                "SceneChromeTokens", "SceneControlChrome", "SceneStateColors",
        };
        for (String token : banned) {
            Assert.assertFalse("守卫：FieldShellBinder 代码不得出现 " + token, code.contains(token));
        }
        Assert.assertTrue("守卫：应下调 FormFieldShell.build（theme-aware 重载）",
                code.contains("FormFieldShell.build("));
    }

    // ==================== 夹具 ====================

    /** 真实装配夹具：{@code runtime.mount} + {@code SceneThemes.withTheme} 内调 binder。 */
    private MountHandle mountThroughBinder(Signal<SceneTheme> pageTheme, FieldSpec spec,
                                           DraftSignalAdapter a, SceneNode[] holder) {
        return runtime.mount(new SceneNode(), () -> {
            SceneThemes.withTheme(pageTheme, () -> holder[0] = FieldShellBinder.build(
                    runtime, spec, a, SceneNode::new));
            return holder[0];
        });
    }

    /**
     * 去掉 {@code //} 行注释与 {@code /*}{@code *} 块注释后的源码（字符串/字符字面量原样保留）。
     *
     * <p>包内共享：G15/Support 四个源码守卫测试共用，守卫只审查代码、不误伤文档措辞。</p>
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
}