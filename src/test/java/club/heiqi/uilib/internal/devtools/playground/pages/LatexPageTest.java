package club.heiqi.uilib.internal.devtools.playground.pages;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.font.latex.LatexShowcaseFormulas;
import club.heiqi.uilib.internal.devtools.playground.PlaygroundKit;
import club.heiqi.uilib.internal.devtools.playground.PlaygroundPage;
import club.heiqi.uilib.internal.devtools.playground.PlaygroundPageRegistry;
import club.heiqi.uilib.internal.devtools.playground.TestPlaygroundHost;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * {@link LatexPage} 默认液态玻璃验收测试（G16/LatexPage）。
 *
 * <p><b>迁移口径</b>：本页唯一的基础前景取色（{@code formulaCard} 行文本）是<b>公式正文默认色</b>
 * ——LaTeX 渲染像素继承外层前景（卡片 8「颜色继承」演示的正是该链路），按契约 §7.3
 * 「markdown/LaTeX 样本」不迁移清单刻意保留显式 {@code PlaygroundKit.TEXT}，本测试反向钉住：
 * 主题切到浅色档（正文近黑 0xFF1C1B1F）后公式行颜色不变、{@code <latex>} 源串逐字不变
 * ——改它即改公式渲染像素、摧毁 headless 目检基线（任务单 G16「不得为匹配主题改 LaTeX 渲染」禁止项）。</p>
 *
 * <p>公式外的节标题/说明复用公共文本构件（G16/公共构件实例交付的 {@code PlaygroundKit.title/
 * hint} 默认路径），在真实宿主上下文（{@code TestPlaygroundHost} 已登记 runtime 作用域）内跟随
 * 来源主题：本测试以 {@code SceneThemes.withTheme} 探针页验证「切换后外观更新、节点身份不变、
 * 公式源数据不变、订阅不增长」。证据面另含真实导航进入本页（点导航段）与页面卸载回收。</p>
 */
public class LatexPageTest {

    /** 画布宽：9 页导航段横排需 ≥833px（与 OverlayPageTest 同口径）。 */
    private static final int CANVAS_WIDTH = 1000;
    private static final int CANVAS_HEIGHT = 2600;

    private static final SceneTheme DARK = SceneThemes.DEFAULT;
    private static final SceneTheme LIGHT = SceneTheme.liquidGlassLight();

    private static final String HERO_TITLE = "公式速览（32px）";
    private static final String STRESS_TITLE = "嵌套与边界（压力公式 24px）";
    private static final String MIXED_TITLE = "混排行内基准（文本与公式基线）";

    /** 12 卡标题（注册顺序），结构钉住用。 */
    private static final String[] CARD_TITLES = {
        HERO_TITLE,
        "分数与根号",
        "上下标与函数名",
        "希腊字母与运算符",
        "矩阵与分段函数",
        "伸缩括号与组合数",
        "重音与上下划线",
        "公式内文本与样式继承",
        STRESS_TITLE,
        "全量公式目检 A（20px，1-11 条）",
        "全量公式目检 B（20px，12-22 条）",
        MIXED_TITLE,
    };

    private ProbeHost host;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        host = new ProbeHost();
        layoutHost();
        // 真实导航路径进入本页（点击导航段），不直接调页工厂。
        clickNode(navSegment(latexPageIndex()));
        layoutHost();
        host.runtime().flush();
        Assert.assertNotNull("导航后本页已挂载", cardWithTitle(pageRoot(), HERO_TITLE));
    }

    @After
    public void tearDown() {
        host.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== ① 公式外文字跟随主题（默认档） ====================

    /**
     * 真实宿主装配路径：12 卡标题取来源主题正文前景、逐卡说明取次要前景；
     * 公式行基础前景 = 刻意保留的显式样本色；全部字号保持既有口径（32/14/24/20）。
     */
    @Test
    public void formulaCardTitlesAndHintsConsumeThemeWhileFormulaKeepsExplicitBase() {
        SceneNode page = pageRoot();
        Assert.assertEquals("页面仍为 12 张卡", 12, page.__getChildren().size());

        SceneNode hero = cardWithTitle(page, HERO_TITLE);
        Assert.assertEquals("卡 1 标题取主题正文前景", DARK.foreground(),
                hero.__getChildren().get(0).getTextColor());
        Assert.assertEquals("卡 1 标题字号保持 16", 16, hero.__getChildren().get(0).getFontSize());
        Assert.assertEquals("卡 1 说明取主题次要前景", DARK.mutedForeground(), hintOf(hero).getTextColor());
        Assert.assertEquals("卡 1 说明字号保持 12", 12, hintOf(hero).getFontSize());

        SceneNode stress = cardWithTitle(page, STRESS_TITLE);
        Assert.assertEquals("压力卡标题取主题正文前景", DARK.foreground(),
                stress.__getChildren().get(0).getTextColor());
        Assert.assertEquals("压力卡说明取主题次要前景", DARK.mutedForeground(), hintOf(stress).getTextColor());

        // 公式行：全部保留显式样本色（同时与主题浅色档正文不同值，见反向钉住用例）。
        List<SceneNode> formulas = formulaLines(page);
        Assert.assertEquals("公式行总数 = 3(速览) + 18(卡 2-8) + 8(压力) + 22(目检 A/B) + 3(混排)",
                3 + 18 + 8 + 22 + 3, formulas.size());
        for (SceneNode formula : formulas) {
            Assert.assertEquals("公式行基础前景保持显式样本色（公式像素继承该色）",
                    PlaygroundKit.TEXT, formula.getTextColor());
        }
    }

    // ==================== ② 主题切换：卡文字更新、公式样本不接管 ====================

    /**
     * 主题切换（{@code withTheme} 局部档，配方与 runtime 默认档确凿不同）：标题/说明前景更新为
     * 浅色档，公式行保留显式样本色（反向钉住），节点身份不变、字号不变、整页文本快照逐字不变
     * （LaTeX 源是数据）、订阅不增长。
     */
    @Test
    public void themeSwitchUpdatesCardTextsButNeverFormulaBaseColor() {
        Assert.assertNotEquals("测试前提：深浅正文前景必须不同", DARK.foreground(), LIGHT.foreground());
        Assert.assertNotEquals("测试前提：深浅次要前景必须不同", DARK.mutedForeground(), LIGHT.mutedForeground());
        Assert.assertNotEquals("测试前提：显式样本色 ≠ 浅色档正文（反向钉住有效性前提）",
                PlaygroundKit.TEXT, LIGHT.foreground());

        Signal<SceneTheme> pageTheme = Signal.create(DARK);
        SceneNode container = new SceneNode();
        MountHandle probe = host.runtime().mount(container, () -> {
            SceneNode box = SceneNode.column();
            SceneThemes.withTheme(pageTheme, () -> box.appendChild(new LatexPage().build(host.runtime()).get()));
            return box;
        });
        host.runtime().flush();
        host.getLayoutEngine().layout(probe.getRoot(), new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));

        SceneNode page = probe.getRoot().__getChildren().get(0);
        SceneNode hero = cardWithTitle(page, HERO_TITLE);
        SceneNode heroTitle = hero.__getChildren().get(0);
        SceneNode heroHint = hintOf(hero);
        List<SceneNode> formulas = formulaLines(page);
        SceneNode firstFormula = formulas.get(0);
        String firstFormulaText = firstFormula.getText();
        List<String> textsBefore = textSnapshot(page);
        int effectsBefore = ReactiveTestProbe.registeredEffectCount();

        Assert.assertEquals("切换前标题取深色档正文", DARK.foreground(), heroTitle.getTextColor());
        Assert.assertEquals("切换前说明取深色档次要", DARK.mutedForeground(), heroHint.getTextColor());

        pageTheme.set(LIGHT);
        host.runtime().flush();

        Assert.assertSame("主题切换不重建标题节点", heroTitle, cardWithTitle(page, HERO_TITLE).__getChildren().get(0));
        Assert.assertSame("主题切换不重建说明节点", heroHint, hintOf(cardWithTitle(page, HERO_TITLE)));
        Assert.assertEquals("切换后标题随主题更新为浅色档正文", LIGHT.foreground(), heroTitle.getTextColor());
        Assert.assertEquals("切换后说明随主题更新为浅色档次要", LIGHT.mutedForeground(), heroHint.getTextColor());
        Assert.assertEquals("标题字号不随主题变化", 16, heroTitle.getFontSize());
        Assert.assertEquals("说明字号不随主题变化", 12, heroHint.getFontSize());

        // 反向钉住：公式正文默认色（含公式渲染像素的继承源）不被主题接管。
        Assert.assertSame("主题切换不重建公式行", firstFormula, formulaLines(page).get(0));
        for (SceneNode formula : formulas) {
            Assert.assertEquals("浅色档下公式行仍为显式样本色（契约 §7.3 LaTeX 样本不迁移）",
                    PlaygroundKit.TEXT, formula.getTextColor());
        }
        Assert.assertEquals("主题切换不改公式源文本（LaTeX 源码逐字不变）",
                firstFormulaText, firstFormula.getText());
        Assert.assertEquals("主题切换不改整页文本数据", textsBefore, textSnapshot(page));
        Assert.assertEquals("主题切换不新增订阅",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());

        probe.dispose();
        host.runtime().flush();
    }

    // ==================== ③ 页面结构与注册不变 ====================

    /** 页面 id/标题/说明、注册名、12 卡标题顺序与逐卡行数不变；LaTeX 数据源仍来自共享夹具。 */
    @Test
    public void pageIdentityStructureAndRegistrationUnchanged() {
        LatexPage page = new LatexPage();
        Assert.assertEquals("页面 id 不变", "latex", page.id());
        Assert.assertEquals("页面标题不变", "LaTeX 公式", page.title());
        Assert.assertTrue("注册表 latex 项仍是 LatexPage 实例",
                PlaygroundPageRegistry.lookup("latex") instanceof LatexPage);

        SceneNode root = pageRoot();
        for (int i = 0; i < CARD_TITLES.length; i++) {
            Assert.assertEquals("第 " + i + " 卡标题不变", CARD_TITLES[i],
                    root.__getChildren().get(i).__getChildren().get(0).getText());
        }
        int[] lineCounts = {
                LatexShowcaseFormulas.HERO.length, 3, 3, 3, 2, 2, 2, 3, 8,
                LatexShowcaseFormulas.all().length / 2,
                LatexShowcaseFormulas.all().length - LatexShowcaseFormulas.all().length / 2,
                3,
        };
        for (int i = 0; i < lineCounts.length; i++) {
            Assert.assertEquals("第 " + i + " 卡结构不变（标题 + 公式行 + 说明）",
                    lineCounts[i] + 2, root.__getChildren().get(i).__getChildren().size());
        }
    }

    // ==================== ④ 卸载回收 ====================

    /** 探针页卸载后本页全部前景/表面绑定回收，effect 数回到基线。 */
    @Test
    public void unmountReclaimsProbePageBindings() {
        host.runtime().flush();
        int baseline = ReactiveTestProbe.registeredEffectCount();
        Signal<SceneTheme> pageTheme = Signal.create(DARK);
        SceneNode container = new SceneNode();
        MountHandle probe = host.runtime().mount(container, () -> {
            SceneNode box = SceneNode.column();
            SceneThemes.withTheme(pageTheme, () -> box.appendChild(new LatexPage().build(host.runtime()).get()));
            return box;
        });
        host.runtime().flush();
        Assert.assertTrue("挂载后应注册响应式工作",
                ReactiveTestProbe.registeredEffectCount() > baseline);
        probe.dispose();
        host.runtime().flush();
        Assert.assertTrue("探针挂载点无残留节点", container.__getChildren().isEmpty());
        Assert.assertEquals("卸载后本页绑定全部回收",
                baseline, ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== ⑤ 源码守卫（含保留样本豁免条款） ====================

    /**
     * 源码守卫：本页不得自带静态<b>外观材质</b>写入者。两处经契约背书的<b>豁免</b>逐处计数钉死，
     * 超出即红（防样本面扩大）：
     * <ul>
     *   <li>{@code PlaygroundKit.TEXT} 恰 1 处 = {@code formulaCard} 公式正文默认色样本
     *       （契约 §7.3「markdown/LaTeX 样本」保留显式，页面注释写明原因）；</li>
     *   <li>{@code SceneChromeTokens} 恰 1 处且必为 {@code PAD_LG} 间距常量
     *       （契约 §4.2 尺寸/间距常量继续使用；任何颜色 token 引用都算违规）。</li>
     * </ul>
     */
    @Test
    public void pageSourceHasNoStaticPaletteWriters() throws Exception {
        Path path = Paths.get(
                "src/main/java/club/heiqi/uilib/internal/devtools/playground/pages/LatexPage.java");
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

        Assert.assertTrue("卡标题必须复用公共主题文本构件", src.contains("PlaygroundKit.title("));
        Assert.assertTrue("卡说明必须复用公共主题文本构件", src.contains("PlaygroundKit.hint("));
        Assert.assertTrue("公式行必须显式钉住样本色（豁免条款本体，见方法注释）",
                src.contains("PlaygroundKit.TEXT"));

        Assert.assertEquals("PlaygroundKit.TEXT 仅允许公式正文默认色样本 1 处（§7.3 豁免）",
                1, count(src, "PlaygroundKit.TEXT"));
        // import 行以「SceneChromeTokens;」结尾，不计入「SceneChromeTokens.」前缀命中。
        Assert.assertEquals("SceneChromeTokens 成员引用仅允许 1 处",
                1, count(src, "SceneChromeTokens."));
        Assert.assertEquals("该唯一成员引用必须是 PAD_LG 间距常量（§4.2 豁免，非颜色）",
                1, count(src, "SceneChromeTokens.PAD_LG"));

        Assert.assertFalse("不得残留其余静态色常量", src.contains("PlaygroundKit.MUTED")
                || src.contains("PlaygroundKit.ACCENT")
                || src.contains("PlaygroundKit.DANGER")
                || src.contains("PlaygroundKit.PANEL_BG")
                || src.contains("PlaygroundKit.BORDER")
                || src.contains("PlaygroundKit.ROOT_BG"));
        Assert.assertFalse("不得残留静态边框/圆角/底色/文字色写入者", src.contains("setBorderWidth(")
                || src.contains("setBorderColor(")
                || src.contains("setCornerRadius(")
                || src.contains("setBackgroundColor(")
                || src.contains("setTextColor("));
        Assert.assertFalse("本页不自带 0xFF 色值（样本色必须复用公共常量以便守卫计数）",
                src.contains("0xFF"));
        Assert.assertFalse("不得调用旧 chrome/状态色板接缝", src.contains("applyPanelChrome")
                || src.contains("SceneStateColors") || src.contains("SceneControlChrome"));
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }

    // ==================== 辅助：宿主/导航 ====================

    /**
     * 测试探针宿主：暴露基类 protected 的 runtime 与根节点（与 OverlayPageTest 同口径）。
     */
    private static final class ProbeHost extends TestPlaygroundHost {

        ProbeHost() {
            super(null);
        }

        SceneRuntime runtime() {
            return runtime;
        }

        SceneNode root() {
            return getRoot();
        }
    }

    private static int latexPageIndex() {
        List<PlaygroundPage> pages = PlaygroundPageRegistry.defaultPages();
        for (int i = 0; i < pages.size(); i++) {
            if ("latex".equals(pages.get(i).id())) {
                return i;
            }
        }
        throw new AssertionError("注册表缺少 id=latex 的演示页");
    }

    private void layoutHost() {
        host.getLayoutEngine().layout(host.root(), new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /** 导航段节点：navBar → segmented root → 第 index 段（与宿主结构同口径）。 */
    private SceneNode navSegment(int index) {
        SceneNode navBar = host.root().__getChildren().get(1);
        SceneNode segmented = navBar.__getChildren().get(0);
        return segmented.__getChildren().get(index);
    }

    /** 当前 live 页根：root → scrollContainer → viewport → content → pageRoot。 */
    private SceneNode pageRoot() {
        SceneNode scrollContainer = host.root().__getChildren().get(2);
        SceneNode viewport = scrollContainer.__getChildren().get(0);
        SceneNode content = viewport.__getChildren().get(0);
        Assert.assertEquals("单槽：content 仅一个 live 页", 1, content.__getChildren().size());
        return content.__getChildren().get(0);
    }

    private void clickNode(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        int x = box.getX() + box.getWidth() / 2;
        int y = box.getY() + box.getHeight() / 2;
        InputFrameBuilder builder = new InputFrameBuilder(x, y);
        builder.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        builder.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1001L));
        host.runtime().route(host.root(), builder.drainFrame(), 0, 0);
        host.runtime().flush();
        layoutHost();
    }

    // ==================== 辅助：节点定位 ====================

    private static SceneNode cardWithTitle(SceneNode pageRoot, String title) {
        for (SceneNode card : pageRoot.__getChildren()) {
            List<SceneNode> children = card.__getChildren();
            if (!children.isEmpty() && title.equals(children.get(0).getText())) {
                return card;
            }
        }
        Assert.fail("页面缺少标题为「" + title + "」的卡片");
        return null;
    }

    /** 卡片末节点 = 说明文字（公共构件主题路径）。 */
    private static SceneNode hintOf(SceneNode card) {
        List<SceneNode> children = card.__getChildren();
        return children.get(children.size() - 1);
    }

    /** 收集整页公式行（含 {@code <latex>} 的行文本节点）。 */
    private static List<SceneNode> formulaLines(SceneNode page) {
        List<SceneNode> out = new ArrayList<SceneNode>();
        collectFormulaLines(page, out);
        return out;
    }

    private static void collectFormulaLines(SceneNode node, List<SceneNode> out) {
        if (node.getText() != null && node.getText().contains("<latex>")) {
            out.add(node);
        }
        for (SceneNode child : node.__getChildren()) {
            collectFormulaLines(child, out);
        }
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
}
