package club.heiqi.uilib.internal.devtools.playground;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

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
 * {@link PlaygroundKit} 公共文本构件默认消费主题（G16/接缝实例）测试。
 *
 * <p><b>契约</b>：{@link PlaygroundKit#text(String)}/{@link PlaygroundKit#title(String)}/
 * {@link PlaygroundKit#hint(String)}/{@link PlaygroundKit#strongHint(String)} 在宿主上下文内
 * （{@link PlaygroundKit#installRuntime} 已把 runtime 挂上 Owner 链）默认跟随<b>来源主题</b>前景：
 * 正文/标题/强调取 {@link SceneThemes#foreground}，次级说明取 {@link SceneThemes#mutedForeground}；
 * 主题切换后自动更新且节点身份不变。无宿主上下文（无当前 Owner，或该 Owner 链未登记 runtime）时
 * 回落迁移前的静态 {@link PlaygroundKit#TEXT}/{@link PlaygroundKit#MUTED}，不抛异常、不新增绑定。</p>
 *
 * <p>显式色重载 {@link PlaygroundKit#text(String, int, int)} 语义恒定：诊断样本/刻意色差仍走它，
 * 不随主题变化。</p>
 */
public class PlaygroundKitTest {

    /** 宿主 runtime 默认档（与外壳同源）。 */
    private static final SceneTheme DARK = SceneThemes.DEFAULT;
    /** 浅色档：主题切换断言用（正文/次要前景与深色档均不同）。 */
    private static final SceneTheme LIGHT = SceneTheme.liquidGlassLight();

    /** 被测默认文本构件在挂载结果中的固定下标。 */
    private static final int IDX_TEXT = 0;
    private static final int IDX_TITLE = 1;
    private static final int IDX_HINT = 2;
    private static final int IDX_STRONG_HINT = 3;

    private TestPlaygroundHost host;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        host = new TestPlaygroundHost(null);
    }

    @After
    public void tearDown() {
        host.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 辅助方法 ====================

    /**
     * 在宿主 runtime 内挂载一组默认文本构件（text/title/hint/strongHint），
     * 返回它们在 {@code out} 中的引用与挂载句柄。
     */
    private MountHandle mountDefaultTexts(SceneNode parent, SceneNode[] out) {
        return host.__getRuntime().mount(parent, () -> {
            SceneNode box = new SceneNode();
            out[IDX_TEXT] = PlaygroundKit.text("value");
            out[IDX_TITLE] = PlaygroundKit.title("标题");
            out[IDX_HINT] = PlaygroundKit.hint("说明");
            out[IDX_STRONG_HINT] = PlaygroundKit.strongHint("强调");
            for (SceneNode node : out) {
                box.appendChild(node);
            }
            return box;
        });
    }

    /** 四个默认构件的前景 = 主题语义色。 */
    private static void assertThemeColors(String where, SceneTheme theme, SceneNode[] nodes) {
        Assert.assertEquals(where + " text(value) 取主题正文前景",
                theme.foreground(), nodes[IDX_TEXT].getTextColor());
        Assert.assertEquals(where + " title 取主题正文前景",
                theme.foreground(), nodes[IDX_TITLE].getTextColor());
        Assert.assertEquals(where + " hint 取主题次要前景",
                theme.mutedForeground(), nodes[IDX_HINT].getTextColor());
        Assert.assertEquals(where + " strongHint 取主题正文前景",
                theme.foreground(), nodes[IDX_STRONG_HINT].getTextColor());
    }

    /** 静态回落：显式色 + 字号 + 不可命中。 */
    private static void assertStaticFallback(String where, SceneNode node, int color, int fontSize) {
        Assert.assertEquals(where + " 回落静态色", color, node.getTextColor());
        Assert.assertEquals(where + " 字号不变", fontSize, node.getFontSize());
        Assert.assertFalse(where + " 默认文本不可命中", node.isHitTestable());
    }

    // ==================== ① 宿主内取主题前景 ====================

    /**
     * 默认工厂路径（真实宿主上下文）：text/title/strongHint 取主题正文前景，
     * hint 取主题次要前景；字号与不可命中语义保持。
     */
    @Test
    public void defaultTextConstructsConsumeSourceThemeForeground() {
        SceneNode[] nodes = new SceneNode[4];
        MountHandle handle = mountDefaultTexts(new SceneNode(), nodes);
        host.__getRuntime().flush();

        assertThemeColors("默认主题下", DARK, nodes);
        Assert.assertEquals("text(value) 字号 16", 16, nodes[IDX_TEXT].getFontSize());
        Assert.assertEquals("title 字号 16", 16, nodes[IDX_TITLE].getFontSize());
        Assert.assertEquals("hint 字号 12", 12, nodes[IDX_HINT].getFontSize());
        Assert.assertEquals("strongHint 字号 12", 12, nodes[IDX_STRONG_HINT].getFontSize());
        for (SceneNode node : nodes) {
            Assert.assertFalse("默认文本构件不可命中", node.isHitTestable());
        }

        handle.dispose();
        host.__getRuntime().flush();
    }

    /**
     * 主题切换：{@link TestPlaygroundHost#__getThemeSignal()} 写入浅色档 + flush 后，
     * 四个默认构件颜色更新、节点身份不变、订阅数不增长。
     */
    @Test
    public void themeSwitchUpdatesDefaultTextColorsWithoutRebuild() {
        Assert.assertNotEquals("测试前提：深浅正文前景必须不同", DARK.foreground(), LIGHT.foreground());
        Assert.assertNotEquals("测试前提：深浅次要前景必须不同",
                DARK.mutedForeground(), LIGHT.mutedForeground());

        SceneNode[] nodes = new SceneNode[4];
        SceneNode mountPoint = new SceneNode();
        mountDefaultTexts(mountPoint, nodes);
        host.__getRuntime().flush();
        assertThemeColors("切换前", DARK, nodes);

        SceneNode[] before = nodes.clone();
        int effectsBeforeSwitch = ReactiveTestProbe.registeredEffectCount();

        host.__getThemeSignal().set(LIGHT);
        host.__getRuntime().flush();

        for (int i = 0; i < nodes.length; i++) {
            Assert.assertSame("主题切换不重建默认文本节点 #" + i, before[i], nodes[i]);
        }
        assertThemeColors("切换后", LIGHT, nodes);
        Assert.assertEquals("主题切换不新增订阅",
                effectsBeforeSwitch, ReactiveTestProbe.registeredEffectCount());
    }

    /**
     * 来源主题优先于 runtime 默认：{@link SceneThemes#withTheme} 作用域内构建的默认文本构件
     * 跟随局部主题更新，且不外泄到宿主 runtime 默认档。
     */
    @Test
    public void defaultTextFollowsLocalThemeScopeOverRuntimeDefault() {
        final SceneNode[] nodes = new SceneNode[4];
        final Signal<SceneTheme> localTheme = Signal.create(DARK);
        SceneNode mountPoint = new SceneNode();
        host.__getRuntime().mount(mountPoint, () -> {
            SceneNode box = new SceneNode();
            SceneThemes.withTheme(localTheme, () -> {
                nodes[IDX_TEXT] = PlaygroundKit.text("value");
                nodes[IDX_TITLE] = PlaygroundKit.title("标题");
                nodes[IDX_HINT] = PlaygroundKit.hint("说明");
                nodes[IDX_STRONG_HINT] = PlaygroundKit.strongHint("强调");
                for (SceneNode node : nodes) {
                    box.appendChild(node);
                }
            });
            return box;
        });
        host.__getRuntime().flush();
        assertThemeColors("局部主题（深色）", DARK, nodes);

        localTheme.set(LIGHT);
        host.__getRuntime().flush();

        assertThemeColors("局部主题切换后", LIGHT, nodes);
        Assert.assertEquals("局部主题不外泄到宿主 runtime 默认档",
                DARK.foreground(), host.__getThemeSignal().get().foreground());
    }

    // ==================== ② 显式色重载语义恒定 ====================

    /**
     * 显式 3 参重载：宿主内也保持显式色，主题切换后颜色与节点身份均不变。
     */
    @Test
    public void explicitColorOverloadIgnoresTheme() {
        final int explicit = 0xFF3366CC;
        final SceneNode[] node = new SceneNode[1];
        host.__getRuntime().mount(new SceneNode(), () -> {
            SceneNode box = new SceneNode();
            node[0] = PlaygroundKit.text("诊断样本", explicit, 14);
            box.appendChild(node[0]);
            return box;
        });
        host.__getRuntime().flush();

        Assert.assertEquals("显式色重载 = 传入色", explicit, node[0].getTextColor());
        Assert.assertEquals("显式色重载字号 = 传入字号", 14, node[0].getFontSize());

        SceneNode explicitNode = node[0];
        host.__getThemeSignal().set(LIGHT);
        host.__getRuntime().flush();

        Assert.assertSame("显式色重载节点不重建", explicitNode, node[0]);
        Assert.assertEquals("显式色重载不随主题变化", explicit, node[0].getTextColor());
    }

    // ==================== ③ 无宿主上下文静态回落 ====================

    /**
     * 无宿主上下文（无当前 Owner，或 Owner 链未登记 runtime）：四个默认构件回落静态
     * {@link PlaygroundKit#TEXT}/{@link PlaygroundKit#MUTED}，不抛异常、不新增响应式绑定。
     */
    @Test
    public void withoutHostContextFallsBackToStaticColors() {
        SceneRuntime bare = new SceneRuntime(new FixedTextMeasurer());
        try {
            int effectsBefore = ReactiveTestProbe.registeredEffectCount();

            // 路径 A：完全无当前 Owner（独立像素夹具的典型调用形态）。
            assertStaticFallback("无 Owner title", PlaygroundKit.title("t"), PlaygroundKit.TEXT, 16);
            assertStaticFallback("无 Owner hint", PlaygroundKit.hint("h"), PlaygroundKit.MUTED, 12);
            assertStaticFallback("无 Owner strongHint",
                    PlaygroundKit.strongHint("s"), PlaygroundKit.TEXT, 12);
            assertStaticFallback("无 Owner text(value)",
                    PlaygroundKit.text("v"), PlaygroundKit.TEXT, 16);

            // 路径 B：有 Owner 但该 runtime 未 installRuntime（未迁移宿主/独立夹具）。
            final SceneNode[] nodes = new SceneNode[4];
            bare.mount(new SceneNode(), () -> {
                SceneNode box = new SceneNode();
                nodes[IDX_TEXT] = PlaygroundKit.text("value");
                nodes[IDX_TITLE] = PlaygroundKit.title("标题");
                nodes[IDX_HINT] = PlaygroundKit.hint("说明");
                nodes[IDX_STRONG_HINT] = PlaygroundKit.strongHint("强调");
                for (SceneNode node : nodes) {
                    box.appendChild(node);
                }
                return box;
            });
            bare.flush();

            assertStaticFallback("未登记宿主 text(value)",
                    nodes[IDX_TEXT], PlaygroundKit.TEXT, 16);
            assertStaticFallback("未登记宿主 title", nodes[IDX_TITLE], PlaygroundKit.TEXT, 16);
            assertStaticFallback("未登记宿主 hint", nodes[IDX_HINT], PlaygroundKit.MUTED, 12);
            assertStaticFallback("未登记宿主 strongHint",
                    nodes[IDX_STRONG_HINT], PlaygroundKit.TEXT, 12);

            Assert.assertEquals("回落路径不得新增响应式绑定",
                    effectsBefore, ReactiveTestProbe.registeredEffectCount());
        } finally {
            bare.dispose();
        }
    }

    // ==================== ④ 真实装配路径 ====================

    /**
     * 真实页面装配路径（HomePage 工厂 → {@code PlaygroundKit.card()} 内的 title/hint）：
     * 宿主内默认跟随主题、切换后更新且不重建；同页显式 ACCENT 样本保持原色。
     */
    @Test
    public void homePageTextFollowsThemeOnRealAssemblyPath() {
        host.__getRuntime().flush();

        SceneNode intro = host.__getDisplayedPageRoot().__getChildren().get(0);
        SceneNode introTitle = intro.__getChildren().get(0);
        SceneNode introHint = intro.__getChildren().get(1);
        Assert.assertEquals("结构锚：首页首卡首个文本为欢迎标题",
                "欢迎使用 Qz UILib 测试场地", introTitle.getText());

        SceneNode pagesCard = host.__getDisplayedPageRoot().__getChildren().get(1);
        SceneNode explicitRow = pagesCard.__getChildren().get(1);
        SceneNode explicitSample = explicitRow.__getChildren().get(0);
        Assert.assertTrue("结构锚：演示页清单首行为显式 ACCENT 样本",
                explicitSample.getText() != null && explicitSample.getText().startsWith("· "));

        Assert.assertEquals("真实页面 title 取主题正文前景",
                DARK.foreground(), introTitle.getTextColor());
        Assert.assertEquals("真实页面 hint 取主题次要前景",
                DARK.mutedForeground(), introHint.getTextColor());
        Assert.assertEquals("真实页面显式样本色不被主题接管",
                PlaygroundKit.ACCENT, explicitSample.getTextColor());

        host.__getThemeSignal().set(LIGHT);
        host.__getRuntime().flush();

        Assert.assertSame("主题切换不重建真实页面 title 节点", introTitle, intro.__getChildren().get(0));
        Assert.assertSame("主题切换不重建真实页面 hint 节点", introHint, intro.__getChildren().get(1));
        Assert.assertEquals("真实页面 title 随主题更新",
                LIGHT.foreground(), introTitle.getTextColor());
        Assert.assertEquals("真实页面 hint 随主题更新",
                LIGHT.mutedForeground(), introHint.getTextColor());
        Assert.assertEquals("真实页面显式样本色仍不随主题变化",
                PlaygroundKit.ACCENT, explicitSample.getTextColor());
    }
}
