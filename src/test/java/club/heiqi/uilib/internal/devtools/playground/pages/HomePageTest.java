package club.heiqi.uilib.internal.devtools.playground.pages;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.internal.devtools.playground.PlaygroundKit;
import club.heiqi.uilib.internal.devtools.playground.PlaygroundPage;
import club.heiqi.uilib.internal.devtools.playground.PlaygroundPageRegistry;
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
 * {@link HomePage} 默认消费主题的页面级测试（G16/HomePage）。
 *
 * <p><b>迁移口径</b>：快捷键速查卡的「按键列」是普通正文，取来源主题
 * {@link SceneThemes#foreground(SceneRuntime)}；主题切换只重派生颜色，不重建节点、不改字号、
 * 不动页面数据。页面清单的「{@code · 页名}」强调标记是<b>保留的显式 ACCENT 样本</b>
 * （见 {@code HomePage} 内注释与 G16 公共构件实例的 {@code PlaygroundKitTest} 锚点），
 * 本测试反向钉住它不随主题变化。</p>
 *
 * <p>测试自建 runtime 并 {@link SceneThemes#install} 默认主题，在 {@code rt.mount} 的 builder
 * 内执行真实页工厂（与宿主 {@code TestPlaygroundHost} 同一装配路径；本类位于 {@code pages}
 * 子包，不能访问宿主的包级探针，故直接以 runtime + 页工厂断言）。</p>
 */
public class HomePageTest {

    /** 宿主默认档（深色液态玻璃）。 */
    private static final SceneTheme DARK = SceneThemes.DEFAULT;
    /** 浅色档：正文前景与深色档不同，用于主题切换断言。 */
    private static final SceneTheme LIGHT = SceneTheme.liquidGlassLight();

    /** 快捷键速查卡第 0 行起的按键列文本（页面数据契约）。 */
    private static final String[] SHORTCUT_KEYS = {
        "Ctrl+C / Ctrl+X / Ctrl+V",
        "Ctrl+Z / Ctrl+Shift+Z / Ctrl+Y",
        "Shift+方向键",
        "Ctrl+←/→",
        "右键文本输入框",
        "ESC",
    };

    private SceneRuntime runtime;
    private Signal<SceneTheme> theme;
    private SceneNode mountPoint;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime(new FixedTextMeasurer());
        theme = Signal.create(DARK);
        SceneThemes.install(runtime, theme);
        mountPoint = new SceneNode();
    }

    @After
    public void tearDown() {
        runtime.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 辅助方法 ====================

    /** 真实装配路径：页工厂 → mount（builder 在页作用域内执行）→ flush 物化派生。 */
    private SceneNode mountPage() {
        MountHandle handle = runtime.mount(mountPoint, new HomePage().build(runtime));
        runtime.flush();
        Assert.assertNotNull("首页必须挂载成功", handle);
        return handle.getRoot();
    }

    /** 快捷键速查卡（root 第 3 张卡）；结构锚先钉，防卡片增删导致取样漂移。 */
    private static SceneNode shortcutsCard(SceneNode root) {
        Assert.assertEquals("首页应为 3 张卡片（总览 / 演示页 / 快捷键速查）",
                3, root.__getChildren().size());
        SceneNode card = root.__getChildren().get(2);
        Assert.assertEquals("结构锚：第 3 卡首节点为快捷键速查标题",
                "文本能力快捷键速查", card.__getChildren().get(0).getText());
        return card;
    }

    /** 快捷键卡第 index 条快捷键行的按键列节点（行直接子 0）。 */
    private static SceneNode shortcutKeys(SceneNode card, int index) {
        SceneNode row = card.__getChildren().get(index + 1);
        return row.__getChildren().get(0);
    }

    /** 演示页清单卡（root 第 2 张卡）首行的显式 ACCENT 样本节点。 */
    private static SceneNode explicitAccentSample(SceneNode root) {
        SceneNode card = root.__getChildren().get(1);
        Assert.assertEquals("结构锚：第 2 卡首节点为演示页标题",
                "演示页", card.__getChildren().get(0).getText());
        SceneNode firstRow = card.__getChildren().get(1);
        SceneNode sample = firstRow.__getChildren().get(0);
        Assert.assertTrue("结构锚：演示页清单首行为「· 页名」强调标记",
                sample.getText() != null && sample.getText().startsWith("· "));
        return sample;
    }

    /** 递归收集子树全部文本（顺序稳定），用于「主题切换不改页面数据」快照。 */
    private static List<String> collectTexts(SceneNode node, List<String> out) {
        if (node.getText() != null) {
            out.add(node.getText());
        }
        for (SceneNode child : node.__getChildren()) {
            collectTexts(child, out);
        }
        return out;
    }

    private static List<String> textSnapshot(SceneNode root) {
        return collectTexts(root, new ArrayList<String>());
    }

    // ==================== ① 目标文字取主题前景 ====================

    /**
     * 默认工厂路径：快捷键按键列取来源主题正文前景，字号 13、不可命中；文本内容与页面数据契约
     * 一致（迁移只改取色路径，不改文本）。
     */
    @Test
    public void shortcutKeyTextsFollowSourceThemeForeground() {
        SceneNode root = mountPage();
        SceneNode card = shortcutsCard(root);

        for (int i = 0; i < SHORTCUT_KEYS.length; i++) {
            SceneNode keys = shortcutKeys(card, i);
            Assert.assertEquals("快捷键按键文本不变 #" + i, SHORTCUT_KEYS[i], keys.getText());
            Assert.assertEquals("快捷键按键列取主题正文前景 #" + i,
                    DARK.foreground(), keys.getTextColor());
            Assert.assertEquals("快捷键按键列字号保持 13 #" + i, 13, keys.getFontSize());
            Assert.assertFalse("快捷键按键列不可命中 #" + i, keys.isHitTestable());
        }
        // 说明列仍走已主题化的 hint（同页结构不因本实例改动而变）。
        Assert.assertNotNull("快捷键行说明列存在",
                card.__getChildren().get(1).__getChildren().get(1).getText());
    }

    // ==================== ② 主题切换：颜色更新、身份不变、数据不变 ====================

    /**
     * 主题切换：{@code theme.set(LIGHT)} + flush 后按键列颜色更新为浅色档正文前景，
     * 节点身份不变、订阅数不增长、整页文本快照不变（主题不参与页面数据）。
     */
    @Test
    public void themeSwitchUpdatesShortcutColorsWithoutRebuildOrDataLoss() {
        Assert.assertNotEquals("测试前提：深浅正文前景必须不同",
                DARK.foreground(), LIGHT.foreground());

        SceneNode root = mountPage();
        SceneNode card = shortcutsCard(root);
        SceneNode[] before = new SceneNode[SHORTCUT_KEYS.length];
        for (int i = 0; i < SHORTCUT_KEYS.length; i++) {
            before[i] = shortcutKeys(card, i);
            Assert.assertEquals("切换前按键列取深色档正文前景 #" + i,
                    DARK.foreground(), before[i].getTextColor());
        }
        List<String> textsBefore = textSnapshot(root);
        int effectsBefore = ReactiveTestProbe.registeredEffectCount();

        theme.set(LIGHT);
        runtime.flush();

        for (int i = 0; i < SHORTCUT_KEYS.length; i++) {
            Assert.assertSame("主题切换不重建按键列节点 #" + i, before[i], shortcutKeys(card, i));
            Assert.assertEquals("主题切换后按键列取浅色档正文前景 #" + i,
                    LIGHT.foreground(), before[i].getTextColor());
            Assert.assertEquals("主题切换不改按键列字号 #" + i, 13, before[i].getFontSize());
        }
        Assert.assertEquals("主题切换不改页面文本数据", textsBefore, textSnapshot(root));
        Assert.assertEquals("主题切换不新增订阅",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== ③ 保留的显式样本 ====================

    /**
     * 页面清单的「· 页名」强调标记是刻意保留的显式 ACCENT 样本：不随主题变化
     * （G16 公共构件实例的 PlaygroundKitTest 以本节点为「显式样本不被主题接管」锚点）。
     */
    @Test
    public void explicitAccentPageMarkerStaysExplicit() {
        SceneNode root = mountPage();
        SceneNode sample = explicitAccentSample(root);
        Assert.assertEquals("演示页清单强调标记 = 显式 ACCENT",
                PlaygroundKit.ACCENT, sample.getTextColor());
        Assert.assertEquals("显式样本字号保持 14", 14, sample.getFontSize());

        theme.set(LIGHT);
        runtime.flush();

        Assert.assertSame("主题切换不重建显式样本节点", sample, explicitAccentSample(root));
        Assert.assertEquals("显式 ACCENT 样本不随主题变化",
                PlaygroundKit.ACCENT, sample.getTextColor());
    }

    // ==================== ④ 页面身份与结构不变 ====================

    /** 页面 id/标题/说明、注册名与根结构不变（迁移不触碰页面身份与装配契约）。 */
    @Test
    public void pageIdentityStructureAndRegistrationUnchanged() {
        HomePage page = new HomePage();
        Assert.assertEquals("页面 id 不变", "home", page.id());
        Assert.assertEquals("页面标题不变", "总览", page.title());
        Assert.assertEquals("页面说明不变",
                "测试场地入口引导：页面清单与文本能力快捷键速查", page.description());

        PlaygroundPage registered = PlaygroundPageRegistry.lookup("home");
        Assert.assertTrue("注册表 home 项仍是 HomePage 实例", registered instanceof HomePage);
        Assert.assertEquals("home 仍是默认首页", "home",
                PlaygroundPageRegistry.defaultPages().get(0).id());
        Assert.assertSame("注册表复用同一实例语义", registered, PlaygroundPageRegistry.lookup("home"));

        SceneNode root = mountPage();
        Assert.assertSame("页面根已挂入父节点", root, mountPoint.__getChildren().get(0));
        Assert.assertEquals("页面根仍为 3 张卡片", 3, root.__getChildren().size());
        Assert.assertEquals("总览卡首节点为欢迎标题",
                "欢迎使用 Qz UILib 测试场地",
                root.__getChildren().get(0).__getChildren().get(0).getText());

        SceneNode pagesCard = root.__getChildren().get(1);
        int expectedRows = PlaygroundPageRegistry.defaultPages().size() - 1;
        Assert.assertEquals("演示页清单行数 = 注册页数 - 首页自身",
                expectedRows, pagesCard.__getChildren().size() - 3);
        Assert.assertEquals("快捷键速查条数不变", SHORTCUT_KEYS.length,
                shortcutsCard(root).__getChildren().size() - 2);
    }
}
