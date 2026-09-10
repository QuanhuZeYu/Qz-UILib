package club.heiqi.uilib.internal.chat3.input;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.api.chat.ChatAction;
import club.heiqi.uilib.api.chat.ChatActionRegistration;
import club.heiqi.uilib.api.chat.ChatActionService;
import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.internal.chat3.view.ChatHudWindow;
import club.heiqi.uilib.ui.hud.api.HudToolbarLayer;
import club.heiqi.uilib.ui.hud.api.HudToolbarSide;
import club.heiqi.uilib.ui.hud.api.HudToolbarSpec;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.render.UiBackdrop;
import club.heiqi.uilib.ui.render.UiGlassMaterial;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.SceneGlassButtonStyle;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * G17/Toolbar 主题接入契约（真实宿主装配路径：{@link ChatHudWindow#chatToolbarSpec()} +
 * {@link HudToolbarLayer} + {@link ChatToolbar} 工厂）。
 *
 * <p>证据口径（契约 §9 + 任务单 P-04）：</p>
 * <ul>
 *   <li>默认路径 = 主题档基线：动作按钮底色/缘色/圆角/边框宽跟随已安装主题的按钮角色配方；
 *       聊天玻璃设置按第一优先级只管「滤镜」字段（开 = 设置模糊/强度的 DARK_THIN 系玻璃，
 *       关 = null 显式关闭），未管辖属性双向跟随主题。</li>
 *   <li>显式旧路径保持静态：公共缩放按钮（{@code spec.getPublicButtonStyle()}）不订阅主题，
 *       主题两档下属性纹丝不动——与动作按钮「换主题→颜色变」形成同框对照。</li>
 *   <li>主题切换断言用 install light → 切 dark 实测重派生；两档期望值先 assertNotEquals
 *       钉住前提（防深档数值与旧默认同值造成的静态断言盲区）。</li>
 *   <li>位图图标白色协议色按 §7.3 保留显式：浅色主题下图标 textColor 仍 0xFFFFFFFF。</li>
 * </ul>
 */
public class ChatToolbarThemeTest {

    private boolean savedGlass;
    private int savedBlur;
    private float savedLens;
    private ChatToolbar.Host attachedHost;
    private SceneRuntime rt;

    @Before
    public void setUp() {
        savedGlass = ChatMarkdownSettings.isGlassEnabled();
        savedBlur = ChatMarkdownSettings.getGlassBlurRadiusPx();
        savedLens = ChatMarkdownSettings.getGlassLensStrength();
        ChatToolbarTest.primeIconCache();
        ChatActionService.getInstance().clear();
        ChatHudWindow.setToolbarSide(HudToolbarSide.DEFAULT);
        ReactiveScheduler.get().reset();
    }

    @After
    public void tearDown() {
        if (rt != null) {
            rt.dispose();
            rt = null;
        }
        ChatMarkdownSettings.setGlassEnabled(savedGlass);
        ChatMarkdownSettings.setGlassBlurRadiusPx(savedBlur);
        ChatMarkdownSettings.setGlassLensStrength(savedLens);
        if (attachedHost != null) {
            ChatHudWindow.detachToolbarHost(attachedHost);
            attachedHost = null;
        }
        ChatHudWindow.setToolbarSide(HudToolbarSide.DEFAULT);
        ChatActionService.getInstance().clear();
        ReactiveScheduler.get().reset();
    }

    /** 只驱动编辑态信号的宿主（重置项恒禁用，覆盖禁用档不抛错）。 */
    private static ChatToolbar.Host host(Signal<Boolean> editing) {
        return new ChatToolbar.Host() {
            @Override public ReadableSignal<Boolean> editing() { return editing; }
            @Override public ReadableSignal<Boolean> canResetCurrent() { return Signal.create(Boolean.FALSE); }
            @Override public ReadableSignal<Boolean> canResetAll() { return Signal.create(Boolean.FALSE); }
            @Override public void finishEdit() { }
            @Override public void cancelEdit() { }
            @Override public void resetCurrent() { }
            @Override public void resetAll() { }
        };
    }

    /**
     * 真实宿主装配：注册一个可见动作 → attach 聊天工具栏宿主 → 用
     * {@link ChatHudWindow#chatToolbarSpec()}（公共按钮 = 显式旧路径）经
     * {@link HudToolbarLayer#mount} 挂 {@link ChatToolbar}（动作按钮 = 默认主题路径）。
     */
    private HudToolbarLayer.Result mountViaHudLayer(Signal<SceneTheme> theme, Signal<Boolean> editing) {
        attachedHost = host(editing);
        ChatHudWindow.attachToolbarHost(attachedHost);
        rt = new SceneRuntime(new FixedTextMeasurer(8, 16));
        if (theme != null) {
            SceneThemes.install(rt, theme);
        }
        return HudToolbarLayer.mount(rt, ChatHudWindow.chatToolbarSpec(),
                SceneNode.column().setPreferredWidth(200).setPreferredHeight(100),
                runtime -> ChatToolbar.mount(runtime, attachedHost));
    }

    private static SceneNode actionButtonAt(HudToolbarLayer.Result layer, int index) {
        SceneNode actions = layer.toolbar().__getChildren().get(0);
        return actions.__getChildren().get(index);
    }

    /** 公共缩放按钮（缩小 / 1:1 / 放大）= 规格显式旧路径的三个消费者。 */
    private static SceneNode publicButtonAt(HudToolbarLayer.Result layer, int index) {
        return layer.toolbar().__getChildren().get(1 + index);
    }

    private static SceneNode iconOf(SceneNode button) {
        return button.__getChildren().get(0).__getChildren().get(0);
    }

    /** ① 默认路径 = 主题基线：安装浅色主题后动作按钮逐属性取 light 档，聊天设置只管滤镜。 */
    @Test
    public void actionButtonsFollowInstalledThemeAndGlassSettingsWinFilterField() {
        ChatMarkdownSettings.setGlassEnabled(true);
        ChatMarkdownSettings.setGlassBlurRadiusPx(8);
        ChatMarkdownSettings.setGlassLensStrength(0.5F);
        HudToolbarLayer.Result layer = mountViaHudLayer(
                Signal.create(SceneTheme.liquidGlassLight()), Signal.create(Boolean.FALSE));
        ChatActionRegistration registration = ChatActionService.getInstance()
                .register(ChatAction.builder("test:theme").label("主题动作").order(1)
                        .visible(Signal.create(Boolean.TRUE)).enabled(Signal.create(Boolean.TRUE))
                        .action(new Runnable() {
                            @Override public void run() { }
                        }).build());
        try {
            rt.flush();
            SceneNode button = actionButtonAt(layer, 0);
            SceneSurfaceStyle light = SceneTheme.liquidGlassLight().surface(SceneTheme.Role.BUTTON_STANDARD);
            SceneSurfaceStyle dark = SceneTheme.liquidGlassDark().surface(SceneTheme.Role.BUTTON_STANDARD);
            // P-04 前提：两档期望值必须本就不同，否则「换主题→颜色变」不可分辨。
            Assert.assertNotEquals(light.getIdle().getTint(), dark.getIdle().getTint());
            Assert.assertNotEquals(light.getIdle().getEdge(), dark.getIdle().getEdge());

            Assert.assertEquals("底色 = 已安装主题 light 按钮档 idle tint",
                    light.getIdle().getTint(), button.getBackgroundColor());
            Assert.assertEquals("缘色 = 主题 light 按钮档 idle edge",
                    light.getIdle().getEdge(), button.getBorderColor());
            Assert.assertEquals("圆角 = 主题按钮档", light.getCornerRadius(), button.getCornerRadius());
            Assert.assertEquals("边框宽 = 主题按钮档", light.getBorderWidth(), button.getBorderWidth());

            // 聊天玻璃设置第一优先级：滤镜字段压过 light 主题的 THIN 档基线。
            Assert.assertNotNull(button.getBackdrop());
            Assert.assertSame("滤镜材质 = 聊天设置档（非主题 light 档）",
                    UiGlassMaterial.DARK_THIN, button.getBackdrop().getEffect().getMaterial());
            Assert.assertNotSame(light.getBackdrop().getEffect().getMaterial(),
                    button.getBackdrop().getEffect().getMaterial());
            Assert.assertEquals(6, button.getBackdrop().getBlurRadius());
            Assert.assertEquals(0.5F * light.getIdle().getLensFactor(),
                    button.getBackdrop().getEffect().getLensStrength(), 1.0E-4F);

            // 显式旧路径对照：公共缩放按钮不订阅主题——light 下仍是旧按钮配方值。
            SceneGlassDefaults legacy = SceneGlassDefaults.of();
            for (int i = 0; i < 3; i++) {
                SceneNode publicButton = publicButtonAt(layer, i);
                Assert.assertEquals("公共按钮保持显式旧配方（底色）",
                        legacy.idleTint, publicButton.getBackgroundColor());
                Assert.assertNotEquals("公共按钮不随主题变（与动作按钮同框对照）",
                        light.getIdle().getTint(), publicButton.getBackgroundColor());
                Assert.assertEquals(legacy.cornerRadius, publicButton.getCornerRadius());
            }

            // §7.3 位图图标白色协议色保留显式：主题前景不染色图标文本回退通道。
            Assert.assertEquals(0xFFFFFFFF, iconOf(button).getTextColor());
            Assert.assertNotEquals("图标协议白不被主题前景接管",
                    Integer.valueOf(SceneTheme.liquidGlassLight().foreground()),
                    Integer.valueOf(iconOf(button).getTextColor()));
        } finally {
            registration.close();
        }
    }

    /**
     * ② 主题切换实测重派生：install light → set dark，动作按钮换色且节点身份不变、
     * effect 数不增长；公共按钮两档纹丝不动（显式路径不订阅）；滤镜字段恒为设置档。
     */
    @Test
    public void themeSwitchReDerivesActionButtonsAndKeepsPublicPathStatic() {
        ChatMarkdownSettings.setGlassEnabled(true);
        ChatMarkdownSettings.setGlassBlurRadiusPx(8);
        ChatMarkdownSettings.setGlassLensStrength(0.5F);
        Signal<SceneTheme> theme = Signal.create(SceneTheme.liquidGlassLight());
        HudToolbarLayer.Result layer = mountViaHudLayer(theme, Signal.create(Boolean.FALSE));
        ChatActionRegistration registration = ChatActionService.getInstance()
                .register(ChatAction.builder("test:switch").label("切换动作").order(1)
                        .visible(Signal.create(Boolean.TRUE)).enabled(Signal.create(Boolean.TRUE))
                        .action(new Runnable() {
                            @Override public void run() { }
                        }).build());
        try {
            rt.flush();
            SceneNode button = actionButtonAt(layer, 0);
            SceneNode plus = publicButtonAt(layer, 2);
            SceneSurfaceStyle light = SceneTheme.liquidGlassLight().surface(SceneTheme.Role.BUTTON_STANDARD);
            SceneSurfaceStyle dark = SceneTheme.liquidGlassDark().surface(SceneTheme.Role.BUTTON_STANDARD);
            Assert.assertNotEquals(light.getIdle().getTint(), dark.getIdle().getTint());
            Assert.assertEquals(light.getIdle().getTint(), button.getBackgroundColor());
            int publicTintBefore = plus.getBackgroundColor();
            int effectsBefore = ReactiveTestProbe.registeredEffectCount();

            theme.set(SceneTheme.liquidGlassDark());
            rt.flush();

            Assert.assertEquals("换主题→动作按钮底色变（dark 档）",
                    dark.getIdle().getTint(), button.getBackgroundColor());
            Assert.assertEquals("换主题→动作按钮缘色变",
                    dark.getIdle().getEdge(), button.getBorderColor());
            Assert.assertNotEquals("切换后不再是 light 档值",
                    light.getIdle().getTint(), button.getBackgroundColor());
            Assert.assertSame("主题切换不重建动作按钮", button, actionButtonAt(layer, 0));
            Assert.assertSame("主题切换不重建公共按钮", plus, publicButtonAt(layer, 2));
            Assert.assertEquals("公共按钮不订阅主题：两档同值", publicTintBefore, plus.getBackgroundColor());
            Assert.assertEquals("主题切换不新增订阅", effectsBefore,
                    ReactiveTestProbe.registeredEffectCount());
            Assert.assertEquals("滤镜字段恒由聊天设置管辖",
                    UiGlassMaterial.DARK_THIN, button.getBackdrop().getEffect().getMaterial());
            Assert.assertEquals(6, button.getBackdrop().getBlurRadius());
        } finally {
            registration.close();
        }
    }

    /**
     * ③ 与主题全面冲突的聊天设置逐项覆盖 + 未管辖属性跟随主题 + 关滤镜显式生效；
     * 设置变更与主题变更一样只重派生（节点身份、effect 数不变）。
     */
    @Test
    public void conflictingGlassSettingsWinAndUnmanagedFieldsFollowTheme() {
        ChatMarkdownSettings.setGlassEnabled(true);
        ChatMarkdownSettings.setGlassBlurRadiusPx(3);
        ChatMarkdownSettings.setGlassLensStrength(0.2F);
        SceneSurfaceStyle conflicting = SceneSurfaceStyle.builder()
                .backdrop(UiBackdrop.liquidGlass(UiGlassMaterial.THIN, 12, 0.5F))
                .cornerRadius(14)
                .idle(new SceneSurfaceStyle.StateStyle(0xFF112233, 0xFF445566, 0.5F, 0.85F))
                .build();
        Signal<SceneTheme> theme = Signal.create(SceneTheme.builder()
                .surface(SceneTheme.Role.BUTTON_STANDARD, conflicting).build());
        HudToolbarLayer.Result layer = mountViaHudLayer(theme, Signal.create(Boolean.FALSE));
        ChatActionRegistration registration = ChatActionService.getInstance()
                .register(ChatAction.builder("test:conflict").label("冲突动作").order(1)
                        .visible(Signal.create(Boolean.TRUE)).enabled(Signal.create(Boolean.TRUE))
                        .action(new Runnable() {
                            @Override public void run() { }
                        }).build());
        try {
            rt.flush();
            SceneNode button = actionButtonAt(layer, 0);
            // 主题管辖字段直通冲突档：底色/缘色/圆角都不是聊天设置管辖项。
            Assert.assertEquals(0xFF112233, button.getBackgroundColor());
            Assert.assertEquals(0xFF445566, button.getBorderColor());
            Assert.assertEquals(14, button.getCornerRadius());
            // 聊天设置管辖字段压过主题：模糊 3 ≠ 主题 12，材质 DARK_THIN ≠ 主题 THIN。
            Assert.assertEquals(3, button.getBackdrop().getBlurRadius());
            Assert.assertSame(UiGlassMaterial.DARK_THIN, button.getBackdrop().getEffect().getMaterial());
            int effectsBefore = ReactiveTestProbe.registeredEffectCount();

            // 关玻璃 = 显式局部关滤镜（契约 §2.2），压过带滤镜主题；底色仍随主题。
            ChatMarkdownSettings.setGlassEnabled(false);
            rt.__tickFrame(1L);
            rt.flush();
            Assert.assertNull("设置关 = 显式关闭滤镜，主题滤镜不得回填", button.getBackdrop());
            Assert.assertEquals(0xFF112233, button.getBackgroundColor());
            Assert.assertEquals("设置变更不新增订阅", effectsBefore,
                    ReactiveTestProbe.registeredEffectCount());
            Assert.assertSame(button, actionButtonAt(layer, 0));

            // 再开玻璃：滤镜按设置回填，仍不重建。
            ChatMarkdownSettings.setGlassEnabled(true);
            rt.__tickFrame(2L);
            rt.flush();
            Assert.assertNotNull(button.getBackdrop());
            Assert.assertEquals(3, button.getBackdrop().getBlurRadius());
            Assert.assertSame(button, actionButtonAt(layer, 0));
            Assert.assertEquals(effectsBefore, ReactiveTestProbe.registeredEffectCount());
        } finally {
            registration.close();
        }
    }

    /**
     * ④ keyed 列表复用与角色映射：编辑态按钮按变体取对应主题角色档（finish=PRIMARY 档
     * ≠ STANDARD 档），来回切换 effect 数回基线（配方派生随项卸载回收）。
     */
    @Test
    public void editRowButtonsMapVariantsToThemeRolesAndReclaimOnReuse() {
        ChatMarkdownSettings.setGlassEnabled(true);
        Signal<Boolean> editing = Signal.create(Boolean.FALSE);
        HudToolbarLayer.Result layer = mountViaHudLayer(
                Signal.create(SceneTheme.liquidGlassLight()), editing);
        ChatActionRegistration registration = ChatActionService.getInstance()
                .register(ChatAction.builder("test:role").label("角色动作").order(1)
                        .visible(Signal.create(Boolean.TRUE)).enabled(Signal.create(Boolean.TRUE))
                        .action(new Runnable() {
                            @Override public void run() { }
                        }).build());
        try {
            rt.flush();
            int baseline = ReactiveTestProbe.registeredEffectCount();

            editing.set(Boolean.TRUE);
            rt.flush();
            SceneSurfaceStyle lightStandard =
                    SceneTheme.liquidGlassLight().surface(SceneTheme.Role.BUTTON_STANDARD);
            SceneSurfaceStyle lightPrimary =
                    SceneTheme.liquidGlassLight().surface(SceneTheme.Role.BUTTON_PRIMARY);
            Assert.assertNotEquals("两角色档本就不同（P-04 前提）",
                    lightPrimary.getIdle().getTint(), lightStandard.getIdle().getTint());
            SceneNode finish = actionButtonAt(layer, 0);
            SceneNode cancel = actionButtonAt(layer, 1);
            Assert.assertEquals("finish 按 PRIMARY 变体取主题按钮强调档",
                    lightPrimary.getIdle().getTint(), finish.getBackgroundColor());
            Assert.assertEquals("cancel 按 STANDARD 变体取主题标准档",
                    lightStandard.getIdle().getTint(), cancel.getBackgroundColor());
            // 禁用项（两个 reset）仍显示：走配方 disabled 档，不因主题路径改变语义。
            SceneNode disabled = actionButtonAt(layer, 2);
            Assert.assertEquals("禁用档仍按状态优先级供给",
                    lightStandard.getDisabled().getTint(), disabled.getBackgroundColor());

            editing.set(Boolean.FALSE);
            rt.flush();
            Assert.assertEquals("编辑态来回切换后 effect 数回基线（配方派生随项回收）",
                    baseline, ReactiveTestProbe.registeredEffectCount());
        } finally {
            registration.close();
        }
    }

    /**
     * ⑤ 源码守卫（唯一写入者与互斥双路径）：动作按钮通道不再经按钮专用显式样式
     * （{@code SceneLiquidGlassStyle.bindButton}）；主题基线只在 {@code recipe} 默认路径消费，
     * 显式旧路径（{@code STYLE}）不得订阅主题；消费方向 chat3 → theme 单向。
     */
    @Test
    public void dualPathsRemainExclusiveAndDependencyDirectionStaysOneWay() throws IOException {
        String toolbarSource = new String(Files.readAllBytes(Paths.get(
                "src/main/java/club/heiqi/uilib/internal/chat3/input/ChatToolbar.java")),
                StandardCharsets.UTF_8);
        Assert.assertFalse("动作按钮不得再走按钮专用显式样式通道（防双通道竞争）",
                toolbarSource.contains("SceneLiquidGlassStyle"));
        Assert.assertFalse("动作按钮不得再消费 SceneGlassButtonStyle",
                toolbarSource.contains("SceneGlassButtonStyle"));
        Assert.assertTrue("动作表面唯一写入者 = 通用绑定器",
                toolbarSource.contains("SceneSurfaceBinder.bind("));
        Assert.assertTrue("光标保持既有 chrome 入口",
                toolbarSource.contains("SceneControlChrome.bindCursor"));

        String appearanceSource = new String(Files.readAllBytes(Paths.get(
                "src/main/java/club/heiqi/uilib/internal/chat3/input/ChatToolbarAppearance.java")),
                StandardCharsets.UTF_8);
        Assert.assertTrue("默认路径基线来自通用主题接缝（字段级派生入口）",
                appearanceSource.contains("SceneThemes.derivedSurface("));
        int styleStart = appearanceSource.indexOf("STYLE =");
        int ctorStart = appearanceSource.indexOf("private ChatToolbarAppearance()");
        int recipeStart = appearanceSource.indexOf("static ReadableSignal<SceneSurfaceStyle> recipe");
        Assert.assertTrue("显式旧路径定义与私有构造先于默认路径（结构守卫）",
                styleStart >= 0 && ctorStart > styleStart && recipeStart > ctorStart);
        Assert.assertFalse("显式旧路径（STYLE 区段）不得订阅主题",
                appearanceSource.substring(styleStart, ctorStart).contains("SceneThemes"));

        try (Stream<Path> walk = Files.walk(Paths.get("src/main/java/club/heiqi/uilib/ui/scene/theme"))) {
            for (Path file : (Iterable<Path>) walk.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String themeSource = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                Assert.assertFalse("通用主题不得 import internal/chat3: " + file,
                        themeSource.matches("(?s).*import\\s+club\\.heiqi\\.uilib\\.internal\\..*"));
            }
        }
    }

    /** 显式旧路径（按钮专用配方）默认值参考：公共缩放按钮两档不变的断言基准。 */
    private static final class SceneGlassDefaults {
        private final int idleTint;
        private final int cornerRadius;

        private static SceneGlassDefaults of() {
            SceneGlassButtonStyle legacy =
                    HudToolbarSpec.builder().build().getPublicButtonStyle().get();
            return new SceneGlassDefaults(legacy.getIdle().getTint(), legacy.getCornerRadius());
        }

        private SceneGlassDefaults(int idleTint, int cornerRadius) {
            this.idleTint = idleTint;
            this.cornerRadius = cornerRadius;
        }
    }
}
