package club.heiqi.uilib.internal.devtools.playground.pages;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.font.config.FontConfig;
import club.heiqi.uilib.font.layout.TextSegment;
import club.heiqi.uilib.font.layout.TextStyle;
import club.heiqi.uilib.font.layout.markdown.MarkdownDocument;
import club.heiqi.uilib.font.layout.markdown.MarkdownLayoutLine;
import club.heiqi.uilib.font.layout.markdown.MarkdownStyleTable;
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
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * {@link MarkdownPage}（含页面私有 L3 {@link MarkdownPageContent}）默认液态玻璃验收测试
 * （G16/MarkdownPage）。
 *
 * <p><b>迁移口径（两类写入分开钉）</b>：</p>
 * <ul>
 *   <li><b>页面 chrome 文字</b>（10 卡标题 / 逐卡说明）复用公共构件 {@code PlaygroundKit.title/hint}，
 * 在真实宿主装配路径（{@code TestPlaygroundHost} 已 {@code installRuntime} + runtime 默认主题）内
 * 取来源主题正文/次要前景；主题切换后前景更新、节点不重建——本页无直接 {@code setTextColor} 的
 * chrome 写入点，故本测试反向防「回退静态」。</li>
 *   <li><b>markdown 渲染协议样本</b>（契约 §7.3「markdown/LaTeX 样本」不迁移清单）全部保留显式并被
 * 反向钉住不被主题接管：① {@code base.setColor(PlaygroundKit.TEXT)} 两处是 <b>markdown 正文渲染
 * 默认色</b>——base 经 {@code toLayoutLines}/{@code toLayoutContent} 进 L1 内联解析、成为未加样式段的
 * 渲染像素（测试以「正文段落段流色 = TEXT」证明其语义归属，与 LatexPage 公式正文同理）；② 引用竖条 /
 * 真横线的 {@code line.getAccentArgb()}、围栏块 {@code head.getBackgroundArgb()} 与表格
 * {@code PaintCommand} 底色（表头/边框）是 {@code MarkdownStyleTable} 登记项解析出的<b>数据驱动色</b>
 * ——期望值在本测试独立重解析 L1 得出（与 {@code MarkdownStyleTable} 出货口径常量互证），既不随
 * 主题切换改变，也不等于深浅两档任何主题 token（{@code assertNotEquals} 前提防同值假绿）。</li>
 * </ul>
 *
 * <p>主题切换经 {@code SceneThemes.withTheme} 局部探针页验证（配方与 runtime 默认档确凿不同）：
 * chrome 更新、卡片底座底色更新（切换可观察）、其余全部节点的底色/圆角/文字色与节点身份逐项不变、
 * 整页文本（chrome 文案 + 全部段流文本）逐字不变、订阅数不增长、卸载回到基线。页面结构与表格宿主宽
 * 算法不变另有用例（与 {@code MarkdownTableHostWidthTest} 同口径公式，不替代该类）。</p>
 */
public class MarkdownPageTest {

    /** 画布宽：9 页导航段横排需 ≥833px（与 OverlayPageTest/LatexPageTest 同口径）。 */
    private static final int CANVAS_WIDTH = 1000;
    private static final int CANVAS_HEIGHT = 2600;

    private static final SceneTheme DARK = SceneThemes.DEFAULT;
    private static final SceneTheme LIGHT = SceneTheme.liquidGlassLight();

    /** 10 卡标题 = 9 个 markdown 样本 + 表格卡（注册顺序），结构钉住用。 */
    private static final String[] CARD_TITLES = {
            "标题（ATX 1..6 级）",
            "围栏代码块（字面不解析）",
            "嵌套引用",
            "列表与续行",
            "列表项名下全部块（M10d）",
            "硬换行与软换行",
            "行内公式",
            "链接与行内样式",
            "分隔线与刻意不支持面",
            "表格：对齐与自动换行",
    };

    private static final String HEAD_CARD = CARD_TITLES[0];
    private static final String CODE_CARD = CARD_TITLES[1];
    private static final String QUOTE_CARD = CARD_TITLES[2];
    private static final String BREAK_CARD = CARD_TITLES[8];

    /** 正文基准段落（SAMPLES[0] 末行）：验证 base 色进入渲染段（L124/L273 归类依据的行为证据）。 */
    private static final String BODY_PARAGRAPH = "正文段落回到基准字号。";
    /** 嵌套引用首行正文（标记符已被 L1 剥离后的行文本）。 */
    private static final String QUOTE_LINE = "一层引用";

    /**
     * 竖条几何谓词：镜像 {@code MarkdownPage} 私有常量（{@code QUOTE_BAR_WIDTH_PX}=2、
     * {@code QUOTE_BAR_RADIUS_PX}=1）与 {@code fillParentHeight}（M10a 连续竖条），契约 §4.2
     * 布局常量主题不接管，允许在测试侧作为结构谓词镜像。
     */
    private static final int BAR_WIDTH_PX = 2;
    private static final int BAR_RADIUS_PX = 1;
    /** 镜像 {@code MarkdownPage.WRAP_WIDTH_PX}：分隔线（无引用缩进）铺满换行宽 600px。 */
    private static final int WRAP_WIDTH_PX = 600;

    /**
     * 表头衬底数据色期望值：{@code MarkdownStyleTable} 包内登记项（chat3 出货口径
     * {@code tableHeaderArgb}）的独立互证常量——L2 把它解析进 {@code PaintCommand}，页面 L3 只搬运。
     */
    private static final int EXPECTED_TABLE_HEADER_ARGB = 0x18FFFFFF;

    private ProbeHost host;
    private int savedWidthMissBudget;

    @Before
    public void setUp() {
        savedWidthMissBudget = FontConfig.widthCacheMissBudgetPerWindow;
        FontConfig.widthCacheMissBudgetPerWindow = 0;
        ReactiveScheduler.get().reset();
        host = new ProbeHost();
        layoutHost();
        // 真实导航路径进入本页（点击导航段），不直接调页工厂。
        clickNode(navSegment(markdownPageIndex()));
        settleHost(CANVAS_WIDTH);
        host.runtime().flush();
        Assert.assertNotNull("导航后本页已挂载", cardWithTitle(pageRoot(), CARD_TITLES[0]));
    }

    @After
    public void tearDown() {
        host.dispose();
        ReactiveScheduler.get().reset();
        FontConfig.widthCacheMissBudgetPerWindow = savedWidthMissBudget;
    }

    // ==================== ① 真实宿主装配：chrome 取主题、样本取数据色 ====================

    /**
     * 真实宿主路径（runtime 默认主题 = 库深色档）：10 卡标题取主题正文前景（16px）、逐卡说明取
     * 主题次要前景（12px）——公共构件主题路径生效；markdown 样本色逐项等于「独立重解析 L1/L2」
     * 得出的数据驱动期望值：正文渲染默认色（{@code PlaygroundKit.TEXT} 入段流）、引用正文降色、
     * 引用竖条/真横线装饰色、围栏块衬底、表格表头/边框（PaintCommand 底色），字号保持既有口径。
     */
    @Test
    public void realHostChromeConsumeThemeWhileMarkdownColorsAreL2Data() {
        SceneNode page = pageRoot();
        Assert.assertEquals("页面仍为 9 样本卡 + 表格卡", 10, page.__getChildren().size());

        for (int i = 0; i < CARD_TITLES.length; i++) {
            SceneNode card = page.__getChildren().get(i);
            SceneNode title = card.__getChildren().get(0);
            SceneNode hint = card.__getChildren().get(card.__getChildren().size() - 1);
            Assert.assertEquals("第 " + i + " 卡标题不变", CARD_TITLES[i], title.getText());
            Assert.assertEquals("第 " + i + " 卡标题取主题正文前景", DARK.foreground(), title.getTextColor());
            Assert.assertEquals("第 " + i + " 卡标题字号保持 16", 16, title.getFontSize());
            Assert.assertEquals("第 " + i + " 卡说明取主题次要前景", DARK.mutedForeground(), hint.getTextColor());
            Assert.assertEquals("第 " + i + " 卡说明字号保持 12", 12, hint.getFontSize());
        }

        // base 色语义证据：正文段落段的渲染颜色就是显式 PlaygroundKit.TEXT（chrome 之外的渲染像素）。
        SceneNode body = findSegmentsOf(cardWithTitle(page, HEAD_CARD), BODY_PARAGRAPH);
        for (TextSegment segment : body.getSegments()) {
            Assert.assertEquals("正文渲染默认色 = 显式样本 base（L1 把 base 铺进段样式）",
                    PlaygroundKit.TEXT, segment.getStyle().getColor());
        }
        // 引用正文降色：MarkdownStyleTable.getQuoteTextColor() 登记项，非主题。
        int quoteText = MarkdownStyleTable.defaults().getQuoteTextColor();
        SceneNode quote = findSegmentsOf(cardWithTitle(page, QUOTE_CARD), QUOTE_LINE);
        for (TextSegment segment : quote.getSegments()) {
            Assert.assertEquals("引用正文色保持样式表登记项", quoteText, segment.getStyle().getColor());
        }

        // 数据驱动装饰/底色：期望值由本测试独立重解析 L1 得出。
        int accent = expectedQuoteAccent();
        int codeBg = expectedCodeBlockBackground();
        List<SceneNode> bars = quoteBars(page);
        Assert.assertEquals("M10a 组级连续竖条总数不变（嵌套引用卡 3 + 项内引用 1）", 4, bars.size());
        Assert.assertEquals("嵌套引用卡：外层/二层/三层各一根连续条（引用块间空行系 quoteLevel=1 续行，"
                        + "不另断条——M10a 现行为，迁移不得触碰）", 3,
                quoteBars(cardWithTitle(page, QUOTE_CARD)).size());
        Assert.assertEquals("项内引用（M10d）恰一根竖条", 1,
                quoteBars(cardWithTitle(page, CARD_TITLES[4])).size());
        for (SceneNode bar : bars) {
            Assert.assertEquals("引用竖条底色 = L2 数据装饰色", accent, bar.getBackgroundColor());
        }
        List<SceneNode> codeBlocks = codeBlockContainers(page);
        Assert.assertEquals("围栏块 = 带底色容器（SAMPLES[1] 两块）", 2, codeBlocks.size());
        for (SceneNode block : codeBlocks) {
            Assert.assertEquals("围栏衬底 = L2 数据块底色", codeBg, block.getBackgroundColor());
        }
        List<SceneNode> rules = ruleLines(page);
        Assert.assertEquals("真横线恰 1 条（分隔线样本）", 1, rules.size());
        Assert.assertEquals("真横线色 = L2 数据装饰色", accent, rules.get(0).getBackgroundColor());

        List<Integer> tableColors = distinctTableBackgrounds(page);
        Assert.assertTrue("表格表头衬底为 PaintCommand 数据色", tableColors.contains(EXPECTED_TABLE_HEADER_ARGB));
        Assert.assertTrue("表格边框色与引用装饰同一登记值送达", tableColors.contains(accent));
        assertNoColorEqualsThemeTokens("表格底色", tableColors);
    }

    // ==================== ② 反向钉住：样本色不被主题接管（含防同值假绿前提） ====================

    /**
     * 反向钉住：主题从深色档切到浅色档（局部 {@code withTheme} 探针页），markdown 渲染样本——
     * 正文默认色段、引用竖条、围栏衬底、真横线、表格 PaintCommand 底色——逐节点底色/圆角/文字色
     * 不变；而 chrome（标题/说明）与卡片底座确凿更新，证明切换真实传播进本页子树（防「没切所以
     * 没变」假绿）。前提用 {@code assertNotEquals} 钉死：样本值 ≠ 两档正文/次要/选区前景，两档
     * 前景互不相等——否则「不变」结论无判别力。
     */
    @Test
    public void markdownSampleColorsSurviveThemeSwitchWithChromeUpdating() {
        assertSwitchHasDiscriminatingPower();
        int accent = expectedQuoteAccent();
        int codeBg = expectedCodeBlockBackground();
        assertNoColorEqualsThemeTokens("引用装饰色", singleList(accent));
        assertNoColorEqualsThemeTokens("围栏衬底色", singleList(codeBg));
        assertNoColorEqualsThemeTokens("表头衬底色", singleList(EXPECTED_TABLE_HEADER_ARGB));

        Probe probe = mountProbePage();
        List<SceneNode> sampleNodes = sampleNodes(probe.page);
        Map<SceneNode, int[]> recordsBefore = recordPaintProps(sampleNodes);
        int barsBefore = quoteBars(probe.page).size();
        List<Integer> tableBefore = distinctTableBackgrounds(probe.page);
        Assert.assertTrue("样本节点集合非空（断言不空转）", !sampleNodes.isEmpty() && barsBefore > 0);
        for (SceneNode bar : quoteBars(probe.page)) {
            Assert.assertEquals("切换前竖条为数据色", accent, bar.getBackgroundColor());
        }
        for (SceneNode block : codeBlockContainers(probe.page)) {
            Assert.assertEquals("切换前围栏衬底为数据色", codeBg, block.getBackgroundColor());
        }
        SceneNode firstCard = probe.page.__getChildren().get(0);
        Assert.assertEquals("切换前卡片底座取深色档 GROUP 配方 tint（TestPlaygroundHostTest 同口径）",
                DARK.surface(SceneTheme.Role.GROUP).getIdle().getTint(), firstCard.getBackgroundColor());

        probe.theme.set(LIGHT);
        host.runtime().flush();
        // 颜色经 Motion 过渡：推进到配方目标值再断言（与 TestPlaygroundHostTest 同法）。
        host.runtime().__finishMotionForTest();

        List<SceneNode> sampleNodesAfter = sampleNodes(probe.page);
        Map<SceneNode, int[]> recordsAfter = recordPaintProps(sampleNodesAfter);
        Assert.assertEquals("主题切换不增删样本节点", sampleNodes.size(), sampleNodesAfter.size());
        for (int i = 0; i < sampleNodes.size(); i++) {
            Assert.assertSame("主题切换不重建样本节点 #" + i, sampleNodes.get(i), sampleNodesAfter.get(i));
            Assert.assertArrayEquals("样本节点底色/圆角/文字色逐字段不变（渲染协议样本保留显式）",
                    recordsBefore.get(sampleNodes.get(i)), recordsAfter.get(sampleNodesAfter.get(i)));
        }
        Assert.assertEquals("切换后竖条仍为数据色（未被主题接管）",
                accent, quoteBars(probe.page).get(0).getBackgroundColor());
        Assert.assertEquals("切换后围栏衬底仍为数据色（未被主题接管）",
                codeBg, codeBlockContainers(probe.page).get(0).getBackgroundColor());
        Assert.assertEquals("切换后表格 PaintCommand 底色不变", tableBefore, distinctTableBackgrounds(probe.page));

        // 判别力双保险：同一棵探针页里 chrome 与卡片底座确凿随主题更新（防「没切所以没变」假绿）。
        SceneNode titleAfter = probe.page.__getChildren().get(0).__getChildren().get(0);
        Assert.assertEquals("同页 chrome 标题切到浅色档正文（主题确实传播进本页）",
                LIGHT.foreground(), titleAfter.getTextColor());
        Assert.assertEquals("同页卡片底座切到浅色档 GROUP 配方 tint",
                LIGHT.surface(SceneTheme.Role.GROUP).getIdle().getTint(),
                probe.page.__getChildren().get(0).getBackgroundColor());

        probe.dispose();
        host.runtime().flush();
    }

    // ==================== ③ 主题切换：chrome 更新、身份/内容/订阅不漂移 ====================

    /**
     * 主题切换：chrome（标题/说明）更新为浅色档且节点身份不变、字号不变；整页内容快照——全部
     * {@code getText()}（chrome 文案）+ 全部段流文本（markdown 渲染数据，含表格单元）——逐字不变；
     * 订阅数不增长；卸载后回到基线。
     */
    @Test
    public void themeSwitchUpdatesChromeWithoutRebuildOrMarkdownDataLoss() {
        assertSwitchHasDiscriminatingPower();

        Probe probe = mountProbePage();
        SceneNode page = probe.page;
        SceneNode title = page.__getChildren().get(0).__getChildren().get(0);
        SceneNode hint = lastOf(cardWithTitle(page, QUOTE_CARD));
        List<SceneNode> tableRows = new ArrayList<SceneNode>(
                cardWithTitle(page, CARD_TITLES[9]).__getChildren().get(1).__getChildren());
        Assert.assertFalse("表格内容节点已装配", tableRows.isEmpty());
        List<String> textsBefore = textSnapshot(page);
        List<String> segmentsBefore = segmentTexts(page);
        int effectsBefore = ReactiveTestProbe.registeredEffectCount();

        Assert.assertEquals("切换前标题取深色档正文", DARK.foreground(), title.getTextColor());
        Assert.assertEquals("切换前说明取深色档次要", DARK.mutedForeground(), hint.getTextColor());

        probe.theme.set(LIGHT);
        host.runtime().flush();

        Assert.assertSame("主题切换不重建标题节点", title,
                page.__getChildren().get(0).__getChildren().get(0));
        Assert.assertSame("主题切换不重建说明节点", hint, lastOf(cardWithTitle(page, QUOTE_CARD)));
        Assert.assertEquals("切换后标题随主题更新为浅色档正文", LIGHT.foreground(), title.getTextColor());
        Assert.assertEquals("切换后说明随主题更新为浅色档次要", LIGHT.mutedForeground(), hint.getTextColor());
        Assert.assertEquals("标题字号不随主题变化", 16, title.getFontSize());
        Assert.assertEquals("说明字号不随主题变化", 12, hint.getFontSize());

        List<SceneNode> tableRowsAfter = new ArrayList<SceneNode>(
                cardWithTitle(page, CARD_TITLES[9]).__getChildren().get(1).__getChildren());
        Assert.assertEquals("主题切换不重建表格内容节点（计划缓存未被外观扰动击穿）",
                tableRows.size(), tableRowsAfter.size());
        for (int i = 0; i < tableRows.size(); i++) {
            Assert.assertSame(tableRows.get(i), tableRowsAfter.get(i));
        }
        Assert.assertEquals("主题切换不改 chrome 文案", textsBefore, textSnapshot(page));
        Assert.assertEquals("主题切换不改 markdown 渲染数据（段流文本逐字不变）",
                segmentsBefore, segmentTexts(page));
        Assert.assertEquals("主题切换不新增订阅",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());

        probe.dispose();
        host.runtime().flush();
    }

    // ==================== ④ 页面结构 / 注册 / 表格宽度不变 ====================

    /** 页面 id/标题/说明、注册表实例与 10 卡结构不变；说明文案保持（G16 不改页面装配契约）。 */
    @Test
    public void pageIdentityStructureAndRegistrationUnchanged() {
        MarkdownPage page = new MarkdownPage();
        Assert.assertEquals("页面 id 不变", "markdown", page.id());
        Assert.assertEquals("页面标题不变", "Markdown 渲染", page.title());
        Assert.assertEquals("页面说明不变", "标题、代码、引用、列表、公式与表格；支持对齐和自动换行",
                page.description());
        Assert.assertTrue("注册表 markdown 项仍是 MarkdownPage 实例",
                PlaygroundPageRegistry.lookup("markdown") instanceof MarkdownPage);
        Assert.assertTrue("build 签名仍为 Supplier<SceneNode> build(SceneRuntime)",
                page.build(host.runtime()) != null);

        SceneNode root = pageRoot();
        Assert.assertEquals("10 卡结构不变", 10, root.__getChildren().size());
        for (int i = 0; i < CARD_TITLES.length; i++) {
            SceneNode card = root.__getChildren().get(i);
            Assert.assertEquals("第 " + i + " 卡标题不变", CARD_TITLES[i],
                    card.__getChildren().get(0).getText());
            Assert.assertTrue("每卡 = 标题 + 渲染行（≥0）+ 说明", card.__getChildren().size() >= 2);
            Assert.assertNull("说明节点是纯文本（无段流）", lastOf(card).getSegments());
        }
    }

    /**
     * 表格宽度算法不变：内容宽恒等卡片可用宽（与 {@code MarkdownTableHostWidthTest} 同口径公式），
     * 换宿主宽再换回，稳态无摆动（本例是回归哨兵，不替代该类既有锁）。
     */
    @Test
    public void tableHostWidthAlgorithmUnchanged() {
        for (int width : new int[] {720, CANVAS_WIDTH}) {
            settleHost(width);
            SceneNode card = lastOf(pageRoot());
            SceneNode body = card.__getChildren().get(1);
            int available = box(card).getWidth() - card.getPaddingLeft() - card.getPaddingRight();
            Assert.assertEquals("宿主宽 " + width + "：实际卡片内容宽下传表格",
                    available, box(body).getWidth());
            settleHost(width);
            Assert.assertEquals("稳态不因表格固有宽回流摆动", available, box(body).getWidth());
        }
    }

    // ==================== ⑤ 卸载回收 ====================

    /** 探针页卸载后本页全部前景/表面/表格订阅回收，effect 数回到基线。 */
    @Test
    public void unmountReclaimsProbePageBindings() {
        host.runtime().flush();
        int baseline = ReactiveTestProbe.registeredEffectCount();
        Probe probe = mountProbePage();
        Assert.assertTrue("挂载后应注册响应式工作",
                ReactiveTestProbe.registeredEffectCount() > baseline);
        probe.dispose();
        host.runtime().flush();
        Assert.assertTrue("探针挂载点无残留节点", probe.container.__getChildren().isEmpty());
        Assert.assertEquals("卸载后本页绑定全部回收",
                baseline, ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== ⑥ 源码守卫（页 + 页面私有 L3，含保留样本豁免计数） ====================

    /**
     * 源码守卫：两个白名单主文件不得自带静态<b>外观材质</b>写入者。豁免逐处计数钉死（防样本面扩大）：
     * <ul>
     *   <li>{@code MarkdownPage}：{@code PlaygroundKit.TEXT} 恰 <b>2</b> 处 = {@code tableCard}/
     *       {@code sampleCard} 的 markdown 正文渲染默认色样本（契约 §7.3 保留显式，页面注释写明原因）；
     *       {@code setBackgroundColor} 恰 3 处且实参<b>必须</b>是数据驱动表达式
     *       （{@code getBackgroundArgb()}/{@code accentArgb}/{@code getAccentArgb()}——数据驱动色不算
     *       静态取色，也不允许被替换成主题或常量）；{@code setCornerRadius} 恰 1 处且实参为
     *       {@code QUOTE_BAR_RADIUS_PX} 几何常量（§4.2 允许）。</li>
     *   <li>{@code MarkdownPageContent}：{@code setBackgroundColor}/{@code setCornerRadius} 各恰
     *       1 处，实参恒为 {@code PaintCommand} 搬运（{@code command.getColor()}/
     *       {@code command.getCornerRadius()}），且本类不得出现任何主题/色板符号。</li>
     * </ul>
     */
    @Test
    public void pageAndContentSourceHaveNoStaticPaletteWriters() throws Exception {
        String src = readCodeOnly(
                "src/main/java/club/heiqi/uilib/internal/devtools/playground/pages/MarkdownPage.java");

        Assert.assertTrue("卡标题必须复用公共主题文本构件", src.contains("PlaygroundKit.title("));
        Assert.assertTrue("卡说明必须复用公共主题文本构件", src.contains("PlaygroundKit.hint("));
        Assert.assertTrue("卡片底座必须复用公共主题构件", src.contains("PlaygroundKit.card()"));
        Assert.assertTrue("markdown 正文默认色必须显式钉住样本色（豁免条款本体，见类注释）",
                src.contains("PlaygroundKit.TEXT"));
        Assert.assertEquals("PlaygroundKit.TEXT 仅允许 tableCard/sampleCard 两处正文默认色样本（§7.3 豁免）",
                2, count(src, "PlaygroundKit.TEXT"));

        // 底色写入者：恰 3 处，且逐处实参必须是数据驱动表达式（渲染协议样本搬运）。
        Assert.assertEquals("setBackgroundColor 恰 3 处（围栏衬底/引用竖条/真横线，全部数据驱动）",
                3, count(src, "setBackgroundColor("));
        for (String line : src.split("\n")) {
            if (!line.contains("setBackgroundColor(")) {
                continue;
            }
            Assert.assertTrue("底色写入点实参必须是 L2 数据表达式：" + line,
                    line.contains("getBackgroundArgb()") || line.contains("getAccentArgb()")
                            || line.contains("accentArgb"));
        }
        // 圆角：仅引用竖条几何常量一处（契约 §4.2 主题不接管布局）。
        Assert.assertEquals("setCornerRadius 恰 1 处", 1, count(src, "setCornerRadius("));
        Assert.assertEquals("该处实参必须是 QUOTE_BAR_RADIUS_PX 几何常量（§4.2 豁免）",
                1, count(src, "setCornerRadius(QUOTE_BAR_RADIUS_PX)"));

        Assert.assertFalse("chrome 文字不得回退公共构件以外的文本构件/静态色", src.contains("PlaygroundKit.text(")
                || src.contains("PlaygroundKit.MUTED") || src.contains("PlaygroundKit.ACCENT")
                || src.contains("PlaygroundKit.DANGER") || src.contains("PlaygroundKit.PANEL_BG")
                || src.contains("PlaygroundKit.BORDER") || src.contains("PlaygroundKit.ROOT_BG"));
        Assert.assertFalse("不得直接写节点文字色（chrome 全部经公共构件主题路径）", src.contains("setTextColor("));
        Assert.assertFalse("本页不得自带静态色值/色板/旧 chrome 接缝", src.contains("0xFF")
                || src.contains("SceneChromeTokens") || src.contains("SceneStateColors")
                || src.contains("SceneControlChrome") || src.contains("applyPanelChrome"));

        String l3 = readCodeOnly("src/main/java/club/heiqi/uilib/internal/devtools/playground/pages/"
                + "MarkdownPageContent.java");
        // L3 只搬运 PaintCommand 数据：底色/圆角各恰 1 处且实参恒为命令字段。
        Assert.assertEquals("L3 setBackgroundColor 恰 1 处（PaintCommand 搬运）",
                1, count(l3, "setBackgroundColor("));
        Assert.assertEquals("L3 setCornerRadius 恰 1 处（PaintCommand 搬运）",
                1, count(l3, "setCornerRadius("));
        Assert.assertTrue("底色实参恒为命令数据色（§7.3 保留显式，禁改主题接管）："
                        + "l3.contains(\"setBackgroundColor(command.getColor())\")",
                l3.contains("setBackgroundColor(command.getColor())"));
        Assert.assertTrue("圆角恒为命令几何（搬运协议）", l3.contains("setCornerRadius(command.getCornerRadius())"));
        Assert.assertFalse("L3 不得出现任何主题/色板/静态色符号", l3.contains("PlaygroundKit")
                || l3.contains("SceneThemes") || l3.contains("SceneChromeTokens")
                || l3.contains("SceneStateColors") || l3.contains("SceneControlChrome")
                || l3.contains("applyPanelChrome") || l3.contains("setTextColor(") || l3.contains("0xFF"));
    }

    // ==================== 反向钉住前提 ====================

    /** 切换判别力前提：两档正文/次要前景互异，且 ≠ 正文样本显式色（防「同值假绿」）。 */
    private static void assertSwitchHasDiscriminatingPower() {
        Assert.assertNotEquals("测试前提：深浅正文前景必须不同", DARK.foreground(), LIGHT.foreground());
        Assert.assertNotEquals("测试前提：深浅次要前景必须不同", DARK.mutedForeground(), LIGHT.mutedForeground());
        Assert.assertNotEquals("测试前提：正文样本默认色 ≠ 浅色档正文（反向钉住有效性前提）",
                PlaygroundKit.TEXT, LIGHT.foreground());
        Assert.assertNotEquals("测试前提：深浅 GROUP 配方 tint 必须不同（切换在页内可观察）",
                DARK.surface(SceneTheme.Role.GROUP).getIdle().getTint(),
                LIGHT.surface(SceneTheme.Role.GROUP).getIdle().getTint());
    }

    /** 样本数据色与两档主题 token 全量两两不同值（防「样本色恰好=主题色」假绿）。 */
    private static void assertNoColorEqualsThemeTokens(String what, List<Integer> colors) {
        int[] tokens = {
                DARK.foreground(), DARK.mutedForeground(), DARK.disabledForeground(),
                DARK.selectionBackground(), DARK.accent(),
                LIGHT.foreground(), LIGHT.mutedForeground(), LIGHT.disabledForeground(),
                LIGHT.selectionBackground(), LIGHT.accent(),
        };
        for (Integer color : colors) {
            for (int token : tokens) {
                Assert.assertNotEquals(what + " " + Integer.toHexString(color)
                        + " 不得与任一主题 token 同值（反向钉住前提）", token, color.intValue());
            }
        }
    }

    // ==================== 数据驱动期望值：独立重解析 L1，不读页面常量 ====================

    /** 引用块装饰色期望：{@code MarkdownStyleTable.defaults()} 登记项经 L1 送达行的 accentArgb。 */
    private static int expectedQuoteAccent() {
        MarkdownLayoutLine line = MarkdownDocument.parse("> 引用")
                .toLayoutLines(MarkdownStyleTable.defaults(), new TextStyle()).get(0);
        Assert.assertNotEquals("L1 必须为引用行解析出装饰色", 0, line.getAccentArgb());
        return line.getAccentArgb();
    }

    /** 围栏块衬底色期望：同表登记项经 L1 送达 CODE 行的 backgroundArgb。 */
    private static int expectedCodeBlockBackground() {
        for (MarkdownLayoutLine line : MarkdownDocument.parse("```\ncode\n```")
                .toLayoutLines(MarkdownStyleTable.defaults(), new TextStyle())) {
            if (line.getKind() == MarkdownLayoutLine.Kind.CODE && line.getBackgroundArgb() != 0) {
                return line.getBackgroundArgb();
            }
        }
        throw new AssertionError("L1 未为围栏 CODE 行解析出块底色");
    }

    // ==================== 探针页（局部主题切换路径） ====================

    /** 一次探针装配：theme 信号 + 挂载句柄 + 页根 + 挂载点。 */
    private static final class Probe {
        final Signal<SceneTheme> theme;
        final SceneNode container;
        final MountHandle handle;
        final SceneNode page;

        Probe(Signal<SceneTheme> theme, SceneNode container, MountHandle handle, SceneNode page) {
            this.theme = theme;
            this.container = container;
            this.handle = handle;
            this.page = page;
        }

        void dispose() {
            handle.dispose();
        }
    }

    private Probe mountProbePage() {
        Signal<SceneTheme> probeTheme = Signal.create(DARK);
        SceneNode container = new SceneNode();
        MountHandle handle = host.runtime().mount(container, () -> {
            SceneNode box = SceneNode.column();
            SceneThemes.withTheme(probeTheme,
                    () -> box.appendChild(new MarkdownPage().build(host.runtime()).get()));
            return box;
        });
        host.runtime().flush();
        settleNode(handle.getRoot(), CANVAS_WIDTH);
        SceneNode page = handle.getRoot().__getChildren().get(0);
        Assert.assertEquals("探针页 10 卡", 10, page.__getChildren().size());
        return new Probe(probeTheme, container, handle, page);
    }

    // ==================== 节点谓词与快照 ====================

    /** 页 + 10 卡根 + 各卡首(标题)末(说明)节点之外的一切节点 = markdown 渲染产物/数据容器集合。 */
    private static List<SceneNode> sampleNodes(SceneNode page) {
        List<SceneNode> out = new ArrayList<SceneNode>();
        for (SceneNode node : allNodes(page)) {
            if (node == page || isCardOrChrome(page, node)) {
                continue;
            }
            out.add(node);
        }
        return out;
    }

    private static boolean isCardOrChrome(SceneNode page, SceneNode node) {
        for (SceneNode card : page.__getChildren()) {
            if (card == node) {
                return true;
            }
            List<SceneNode> kids = card.__getChildren();
            if (!kids.isEmpty() && (kids.get(0) == node || kids.get(kids.size() - 1) == node)) {
                return true;
            }
        }
        return false;
    }

    private static Map<SceneNode, int[]> recordPaintProps(List<SceneNode> nodes) {
        Map<SceneNode, int[]> out = new IdentityHashMap<SceneNode, int[]>();
        for (SceneNode node : nodes) {
            out.put(node, new int[] {node.getBackgroundColor(), node.getCornerRadius(), node.getTextColor()});
        }
        return out;
    }

    /** M10a 连续竖条：宽 2 + 圆角 1 + fillParentHeight 的结构谓词（几何常量镜像，§4.2）。 */
    private static List<SceneNode> quoteBars(SceneNode scope) {
        List<SceneNode> out = new ArrayList<SceneNode>();
        for (SceneNode node : allNodes(scope)) {
            if (node.getPreferredWidth() == BAR_WIDTH_PX && node.getCornerRadius() == BAR_RADIUS_PX
                    && node.isFillParentHeight()) {
                out.add(node);
            }
        }
        return out;
    }

    /** 围栏块容器：CODE 卡直接子节点中带底色者（底色值单独与 L1 期望互证）。 */
    private static List<SceneNode> codeBlockContainers(SceneNode page) {
        List<SceneNode> out = new ArrayList<SceneNode>();
        for (SceneNode card : page.__getChildren()) {
            if (!CODE_CARD.equals(firstText(card))) {
                continue;
            }
            for (SceneNode child : card.__getChildren()) {
                if (child.getBackgroundColor() != 0 && child.getSegments() == null) {
                    out.add(child);
                }
            }
        }
        return out;
    }

    /** 真横线：分隔线卡直接子节点中 1px 高、铺满换行宽（600 - 行左偏移）者。 */
    private static List<SceneNode> ruleLines(SceneNode page) {
        List<SceneNode> out = new ArrayList<SceneNode>();
        for (SceneNode card : page.__getChildren()) {
            if (!BREAK_CARD.equals(firstText(card))) {
                continue;
            }
            for (SceneNode child : card.__getChildren()) {
                if (child.getPreferredHeight() == 1 && child.getPreferredWidth() == WRAP_WIDTH_PX) {
                    out.add(child);
                }
            }
        }
        return out;
    }

    /** 表格卡内容列（MarkdownPageContent 的 L3 列）全部子节点的互异非零底色（PaintCommand 搬运值）。 */
    private static List<Integer> distinctTableBackgrounds(SceneNode page) {
        SceneNode body = cardWithTitle(page, CARD_TITLES[9]).__getChildren().get(1);
        List<Integer> out = new ArrayList<Integer>();
        for (SceneNode node : allNodes(body)) {
            int color = node.getBackgroundColor();
            if (color != 0 && !out.contains(Integer.valueOf(color))) {
                out.add(Integer.valueOf(color));
            }
        }
        return out;
    }

    private static List<Integer> singleList(int value) {
        List<Integer> out = new ArrayList<Integer>();
        out.add(Integer.valueOf(value));
        return out;
    }

    private static void allNodes(SceneNode node, List<SceneNode> out) {
        out.add(node);
        for (SceneNode child : node.__getChildren()) {
            allNodes(child, out);
        }
    }

    private static List<SceneNode> allNodes(SceneNode root) {
        List<SceneNode> out = new ArrayList<SceneNode>();
        allNodes(root, out);
        return out;
    }

    /** 定位段流节点：其全部段文本按序拼接恰等于 {@code joinedText}。 */
    private static SceneNode findSegmentsOf(SceneNode scope, String joinedText) {
        for (SceneNode node : allNodes(scope)) {
            List<TextSegment> segments = node.getSegments();
            if (segments == null || segments.isEmpty()) {
                continue;
            }
            StringBuilder joined = new StringBuilder();
            for (TextSegment segment : segments) {
                joined.append(segment.getText());
            }
            if (joinedText.contentEquals(joined)) {
                return node;
            }
        }
        Assert.fail("缺少段流节点：「" + joinedText + "」");
        return null;
    }

    /** 子树全部段流文本（树序），markdown 渲染数据的逐字快照维度之一。 */
    private static List<String> segmentTexts(SceneNode root) {
        List<String> out = new ArrayList<String>();
        for (SceneNode node : allNodes(root)) {
            List<TextSegment> segments = node.getSegments();
            if (segments == null) {
                continue;
            }
            for (TextSegment segment : segments) {
                out.add(segment.getText());
            }
        }
        return out;
    }

    /** 递归收集子树全部 {@code getText()}（chrome 文案快照），顺序稳定。 */
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

    private static String firstText(SceneNode node) {
        if (node.getText() != null) {
            return node.getText();
        }
        for (SceneNode child : node.__getChildren()) {
            String text = firstText(child);
            if (text != null) {
                return text;
            }
        }
        return null;
    }

    private static SceneNode lastOf(SceneNode card) {
        List<SceneNode> kids = card.__getChildren();
        return kids.get(kids.size() - 1);
    }

    private static SceneNode cardWithTitle(SceneNode pageRoot, String title) {
        for (SceneNode card : pageRoot.__getChildren()) {
            if (title.equals(firstText(card))) {
                return card;
            }
        }
        Assert.fail("页面缺少标题为「" + title + "」的卡片");
        return null;
    }

    // ==================== 宿主/布局辅助（与 LatexPageTest 同口径） ====================

    /** 测试探针宿主：暴露基类 protected 的 runtime 与根节点（与 OverlayPageTest 同口径）。 */
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

    private static int markdownPageIndex() {
        List<PlaygroundPage> pages = PlaygroundPageRegistry.defaultPages();
        for (int i = 0; i < pages.size(); i++) {
            if ("markdown".equals(pages.get(i).id())) {
                return i;
            }
        }
        throw new AssertionError("注册表缺少 id=markdown 的演示页");
    }

    private void layoutHost() {
        host.getLayoutEngine().layout(host.root(), new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /** 两趟收敛（表格 layoutDone 桥接需要，与 MarkdownTableHostWidthTest.settle 同口径）。 */
    private void settleHost(int width) {
        settleNodeOnHost(host.root(), width, CANVAS_HEIGHT);
    }

    private void settleNode(SceneNode node, int width) {
        settleNodeOnHost(node, width, CANVAS_HEIGHT);
    }

    private void settleNodeOnHost(SceneNode node, int width, int height) {
        for (int i = 0; i < 4; i++) {
            host.runtime().flush();
            host.getLayoutEngine().layout(node, new Constraints(width, height));
            host.runtime().__setLayoutDoneEpoch(host.getLayoutEngine().layoutEpoch());
        }
        host.runtime().flush();
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
        AnchorRect anchor = SceneGeometry.absoluteBox(node, 0, 0);
        int x = anchor.getX() + anchor.getWidth() / 2;
        int y = anchor.getY() + anchor.getHeight() / 2;
        InputFrameBuilder builder = new InputFrameBuilder(x, y);
        builder.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        builder.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1001L));
        host.runtime().route(host.root(), builder.drainFrame(), 0, 0);
        host.runtime().flush();
        layoutHost();
    }

    private static LayoutBox box(SceneNode node) {
        return (LayoutBox) node.getCachedLayout();
    }

    // ==================== 源码扫描辅助 ====================

    /** 读源码并剔除整行注释（{@code //}、{@code *}、{@code /*}），只保留代码行文本。 */
    private static String readCodeOnly(String relativePath) throws Exception {
        Path path = Paths.get(relativePath);
        String raw = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        StringBuilder code = new StringBuilder();
        for (String line : raw.split("\r?\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                continue;
            }
            code.append(trimmed).append('\n');
        }
        return code.toString();
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }
}
