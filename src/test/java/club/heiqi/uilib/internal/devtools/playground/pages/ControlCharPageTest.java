package club.heiqi.uilib.internal.devtools.playground.pages;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.internal.devtools.playground.PlaygroundPageRegistry;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.TextStyle;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * {@link ControlCharPage} 默认消费主题的页面级测试（G16/ControlCharPage）。
 *
 * <p><b>迁移口径</b>：控制字符样本的基础正文（容器/默认前景）由静态 {@code PlaygroundKit.TEXT}
 * 改为来源主题 {@link SceneThemes#foreground}——RAW 静态样本经公共构件 {@code text(rt, ...)}，
 * RICH/MINECRAFT {@code SceneLabel} 走契约 §2.7 主题化默认路径（builder 不调 {@code color(...)}）。
 * 主题切换只重派生未着色片段的前景，不重建节点、不改字号。</p>
 *
 * <p><b>反向钉住（渲染协议数据）</b>：字符串中的控制/格式字符（NEL/LS/PS/VT/FF/CRLF、tab、
 * 空白家族、ZWSP/软连字符、变体选择符/组合标记、BOM/剥离类、泰阿堆叠）与 {@code §} 原版
 * 格式化色（{@code TEXT_MODE_MINECRAFT_FORMATTED}）、{@code <color>} 标签色是<b>被测内容本体</b>
 * （契约 §7.3 富文本/字体不迁移清单 + 任务单 G16「不得为匹配主题改写渲染样本」）——主题切换
 * 前后逐码点不变；样本节点前景（深/浅两档）与 {@code §a} 原版绿、{@code #4FC3F7} 标签蓝均
 * 不同值，证明容器取色路径没有触碰协议色。</p>
 */
public class ControlCharPageTest {

    /** 宿主默认档（深色液态玻璃）。 */
    private static final SceneTheme DARK = SceneThemes.DEFAULT;
    /** 浅色档：正文前景与深色档不同，用于主题切换断言。 */
    private static final SceneTheme LIGHT = SceneTheme.liquidGlassLight();

    private static final int CANVAS_WIDTH = 900;
    private static final int CANVAS_HEIGHT = 3200;

    private static final String NEWLINE_TITLE = "换行类（统一折叠为换行，CRLF 只算一个）";
    private static final String TAB_TITLE = "制表符（CSS 默认 8 空格列宽）";
    private static final String SPACE_TITLE = "空白家族（统一断词 / 行尾折叠 / 行首丢弃）";
    private static final String SOFT_TITLE = "软断行（ZWSP 断行零宽 / 软连字符断行补 '-'）";
    private static final String CLUSTER_TITLE = "粘合（变体选择符 / 组合标记附着前字，不落行首）";
    private static final String CONTROL_TITLE = "Cc 控制字符（可见 glyph：Control Pictures 映射）";
    private static final String STRIP_TITLE = "剥离类（Default_Ignorable：静默不可见、零宽）";
    private static final String WATER_TITLE = "网页灌水文本（贴吧水帖圣经，对照网页渲染效果）";
    private static final String MODE_TITLE = "三种内容模式同口径（RAW / MINECRAFT / RICH）";

    /** 注册表顺序下的九卡标题（结构钉住用）。 */
    private static final String[] CARD_TITLES = {
        NEWLINE_TITLE, TAB_TITLE, SPACE_TITLE, SOFT_TITLE, CLUSTER_TITLE,
        CONTROL_TITLE, STRIP_TITLE, WATER_TITLE, MODE_TITLE,
    };

    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private Signal<SceneTheme> theme;
    private SceneNode mountPoint;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        runtime = new SceneRuntime(new FixedTextMeasurer());
        layoutEngine = new SceneLayoutEngine(new FixedTextMeasurer());
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

    /** 真实装配路径：页工厂 → mount（builder 在页作用域内执行）→ flush 物化派生 → 布局。 */
    private SceneNode mountPage() {
        MountHandle handle = runtime.mount(mountPoint, new ControlCharPage().build(runtime));
        runtime.flush();
        layoutEngine.layout(mountPoint, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
        Assert.assertNotNull("控制字符页必须挂载成功", handle);
        return handle.getRoot();
    }

    /** 按卡片首节点标题定位卡片；结构锚先钉，防卡片增删导致取样漂移。 */
    private static SceneNode cardWithTitle(SceneNode root, String title) {
        for (SceneNode card : root.__getChildren()) {
            List<SceneNode> children = card.__getChildren();
            if (!children.isEmpty() && title.equals(children.get(0).getText())) {
                return card;
            }
        }
        Assert.fail("页面缺少标题为「" + title + "」的卡片");
        return null;
    }

    /** 卡片内首个文本以 prefix 开头的直接子节点（页面无动态文本，按前缀锚定稳定）。 */
    private static SceneNode childStartingWith(SceneNode card, String prefix) {
        for (SceneNode child : card.__getChildren()) {
            if (child.getText() != null && child.getText().startsWith(prefix)) {
                return child;
            }
        }
        Assert.fail("卡片「" + card.__getChildren().get(0).getText() + "」缺少以「" + prefix + "」开头的样本");
        return null;
    }

    /** 递归收集子树全部文本（顺序稳定），用于「主题切换不改样本数据」快照。 */
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

    // ==================== ① 样本基础正文取主题前景 ====================

    /**
     * 默认工厂路径：RAW 静态样本、RICH/MINECRAFT SceneLabel 的基础正文取来源主题正文前景；
     * 说明文字取次要前景；字号保持 14/32；内容模式声明（RAW/RICH/MINECRAFT）不变。
     */
    @Test
    public void sampleBaseForegroundsFollowSourceTheme() {
        SceneNode root = mountPage();

        SceneNode nel = childStartingWith(cardWithTitle(root, NEWLINE_TITLE), "NEL(U+0085)");
        Assert.assertEquals("NEL 样本基础正文取主题正文前景", DARK.foreground(), nel.getTextColor());
        Assert.assertEquals("NEL 样本字号保持 14", 14, nel.getFontSize());
        Assert.assertEquals("NEL 样本仍是 RAW 模式", TextStyle.TEXT_MODE_UILIB_RAW, nel.getTextContentMode());

        SceneNode spaceLabel = cardWithTitle(root, SPACE_TITLE).__getChildren().get(1);
        Assert.assertEquals("空白家族 SceneLabel 基础正文取主题正文前景",
                DARK.foreground(), spaceLabel.getTextColor());
        Assert.assertEquals("空白家族 SceneLabel 字号保持 14", 14, spaceLabel.getFontSize());
        Assert.assertEquals("空白家族 SceneLabel 换行合同保持（320）", 320, spaceLabel.getMaxTextWidth());
        Assert.assertEquals("空白家族 SceneLabel 仍是 RICH 模式",
                TextStyle.TEXT_MODE_RICH_TAGS, spaceLabel.getTextContentMode());

        SceneNode pyramid = childStartingWith(cardWithTitle(root, CLUSTER_TITLE), "多层堆叠");
        Assert.assertEquals("32px 堆叠样本取主题正文前景", DARK.foreground(), pyramid.getTextColor());
        Assert.assertEquals("32px 堆叠样本字号保持 32", 32, pyramid.getFontSize());

        SceneNode minecraft = childStartingWith(cardWithTitle(root, MODE_TITLE), "MINECRAFT：");
        Assert.assertEquals("MINECRAFT 样本基础正文取主题正文前景",
                DARK.foreground(), minecraft.getTextColor());
        Assert.assertEquals("MINECRAFT 样本内容模式不变",
                TextStyle.TEXT_MODE_MINECRAFT_FORMATTED, minecraft.getTextContentMode());

        SceneNode hint = childStartingWith(cardWithTitle(root, TAB_TITLE), "\\t 按 8 个空格宽度推进");
        Assert.assertEquals("说明文字取主题次要前景", DARK.mutedForeground(), hint.getTextColor());
        Assert.assertEquals("说明文字字号保持 12", 12, hint.getFontSize());
    }

    // ==================== ② 主题切换：颜色更新、身份不变、数据不变 ====================

    /**
     * 主题切换：全部样本节点基础正文更新为浅色档前景，节点身份不变、字号不变、
     * 整页文本快照逐字不变（控制字符是样本数据）、订阅数不增长。
     */
    @Test
    public void themeSwitchUpdatesForegroundsWithoutRebuildOrDataLoss() {
        Assert.assertNotEquals("测试前提：深浅正文前景必须不同", DARK.foreground(), LIGHT.foreground());

        SceneNode root = mountPage();
        Map<String, SceneNode> watch = new LinkedHashMap<String, SceneNode>();
        watch.put(NEWLINE_TITLE, childStartingWith(cardWithTitle(root, NEWLINE_TITLE), "CRLF："));
        watch.put(TAB_TITLE, childStartingWith(cardWithTitle(root, TAB_TITLE), "tab："));
        watch.put(SOFT_TITLE, childStartingWith(cardWithTitle(root, SOFT_TITLE), "软连字符："));
        watch.put(STRIP_TITLE, childStartingWith(cardWithTitle(root, STRIP_TITLE), "BOM"));
        watch.put(WATER_TITLE, cardWithTitle(root, WATER_TITLE).__getChildren().get(1));
        List<String> textsBefore = textSnapshot(root);
        int effectsBefore = ReactiveTestProbe.registeredEffectCount();

        theme.set(LIGHT);
        runtime.flush();

        for (Map.Entry<String, SceneNode> entry : watch.entrySet()) {
            SceneNode node = entry.getValue();
            Assert.assertEquals("切换后基础正文取浅色档前景（" + entry.getKey() + "）",
                    LIGHT.foreground(), node.getTextColor());
        }
        Assert.assertSame("主题切换不重建 CRLF 样本", watch.get(NEWLINE_TITLE),
                childStartingWith(cardWithTitle(root, NEWLINE_TITLE), "CRLF："));
        Assert.assertSame("主题切换不重建 tab 样本", watch.get(TAB_TITLE),
                childStartingWith(cardWithTitle(root, TAB_TITLE), "tab："));
        Assert.assertSame("主题切换不重建软连字符样本", watch.get(SOFT_TITLE),
                childStartingWith(cardWithTitle(root, SOFT_TITLE), "软连字符："));
        Assert.assertSame("主题切换不重建 BOM 样本", watch.get(STRIP_TITLE),
                childStartingWith(cardWithTitle(root, STRIP_TITLE), "BOM"));
        Assert.assertSame("主题切换不重建灌水文本", watch.get(WATER_TITLE),
                cardWithTitle(root, WATER_TITLE).__getChildren().get(1));
        Assert.assertEquals("主题切换不改样本数据（含全部控制/格式码点）",
                textsBefore, textSnapshot(root));
        Assert.assertEquals("主题切换不新增订阅",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== ③ 渲染协议样本逐码点保留（反向钉住） ====================

    /**
     * 反向钉住：主题切换前后，各卡样本字符串逐码点相等——NEL/LS/PS/VT/FF/CRLF、tab、
     * NBSP/全角/窄空格、ZWSP/软连字符、变体选择符/四层组合标记、BOM/bidi/WORD JOINER/CGJ/
     * 非字符、泰阿堆叠序列、{@code §} 格式码与 {@code <color>} 标签蓝全部原样；
     * 且容器前景（两档）与 {@code §a} 原版绿（{@code #55FF55}）、标签蓝（{@code #4FC3F7}）
     * 均不同值，证明容器取色路径未触碰协议色。
     */
    @Test
    public void controlCharacterSamplesRemainVerbatimUnderThemeSwitch() {
        Assert.assertNotEquals("前提：§a 原版绿不是深色档正文", 0xFF55FF55, DARK.foreground());
        Assert.assertNotEquals("前提：§a 原版绿不是浅色档正文", 0xFF55FF55, LIGHT.foreground());
        Assert.assertNotEquals("前提：#4FC3F7 标签蓝不是深色档正文", 0xFF4FC3F7, DARK.foreground());
        Assert.assertNotEquals("前提：#4FC3F7 标签蓝不是浅色档正文", 0xFF4FC3F7, LIGHT.foreground());

        SceneNode root = mountPage();
        Map<String, String> samples = new LinkedHashMap<String, String>();
        SceneNode newlineCard = cardWithTitle(root, NEWLINE_TITLE);
        samples.put("nel", childStartingWith(newlineCard, "NEL(U+0085)").getText());
        samples.put("ls", childStartingWith(newlineCard, "LS(U+2028)").getText());
        samples.put("ps", childStartingWith(newlineCard, "PS(U+2029)").getText());
        samples.put("vtff", childStartingWith(newlineCard, "VT(U+000B)").getText());
        samples.put("crlf", childStartingWith(newlineCard, "CRLF：").getText());
        samples.put("tab", childStartingWith(cardWithTitle(root, TAB_TITLE), "tab：").getText());
        samples.put("mixed", childStartingWith(cardWithTitle(root, TAB_TITLE), "混合：").getText());
        samples.put("spaceFamily", cardWithTitle(root, SPACE_TITLE).__getChildren().get(1).getText());
        samples.put("zwsp", childStartingWith(cardWithTitle(root, SOFT_TITLE), "ZWSP：").getText());
        samples.put("softHyphen", childStartingWith(cardWithTitle(root, SOFT_TITLE), "软连字符：").getText());
        samples.put("vs", childStartingWith(cardWithTitle(root, CLUSTER_TITLE), "变体选择符：").getText());
        samples.put("combine", childStartingWith(cardWithTitle(root, CLUSTER_TITLE), "组合标记：").getText());
        samples.put("pyramid", childStartingWith(cardWithTitle(root, CLUSTER_TITLE), "多层堆叠").getText());
        samples.put("cc", childStartingWith(cardWithTitle(root, CONTROL_TITLE), "C0 控制：").getText());
        samples.put("bom", childStartingWith(cardWithTitle(root, STRIP_TITLE), "BOM").getText());
        samples.put("pureStrip", childStartingWith(cardWithTitle(root, STRIP_TITLE), "纯剥离：").getText());
        samples.put("water", cardWithTitle(root, WATER_TITLE).__getChildren().get(1).getText());
        SceneNode modeCard = cardWithTitle(root, MODE_TITLE);
        String minecraft = childStartingWith(modeCard, "MINECRAFT：").getText();
        String richMode = childStartingWith(modeCard, "RICH：").getText();

        // 逐类协议码点在场证明（迁移未「为匹配主题」洗掉任何被测字符）。
        Assert.assertTrue("NEL U+0085 在场", samples.get("nel").indexOf('\u0085') > 0);
        Assert.assertTrue("LS U+2028 在场", samples.get("ls").indexOf('\u2028') > 0);
        Assert.assertTrue("PS U+2029 在场", samples.get("ps").indexOf('\u2029') > 0);
        Assert.assertTrue("VT U+000B / FF U+000C 在场",
                samples.get("vtff").indexOf('\u000B') > 0 && samples.get("vtff").indexOf('\u000C') > 0);
        Assert.assertTrue("CRLF 在场", samples.get("crlf").contains("\r\n"));
        Assert.assertTrue("tab 样本含制表符", samples.get("tab").contains("a\tb"));
        Assert.assertTrue("NBSP/全角/窄空格家族在场",
                samples.get("spaceFamily").indexOf('\u00A0') > 0
                        && samples.get("spaceFamily").indexOf('\u2003') > 0
                        && samples.get("spaceFamily").indexOf('\u202F') > 0
                        && samples.get("spaceFamily").indexOf('\u205F') > 0
                        && samples.get("spaceFamily").indexOf('\u2007') > 0);
        Assert.assertTrue("ZWSP U+200B 在场", samples.get("zwsp").indexOf('\u200B') > 0);
        Assert.assertTrue("软连字符 U+00AD 在场", samples.get("softHyphen").indexOf('\u00AD') > 0);
        Assert.assertTrue("变体选择符 U+FE0F 在场", samples.get("vs").indexOf('\uFE0F') > 0);
        Assert.assertTrue("四层组合标记在场", samples.get("pyramid").contains("a\u0301\u0300\u0308\u0303"));
        Assert.assertTrue("Cc 可见样本 U+0007/U+0001 在场",
                samples.get("cc").indexOf('\u0007') > 0 && samples.get("cc").indexOf('\u0001') > 0);
        Assert.assertTrue("BOM/WORD JOINER/bidi 控制在场",
                samples.get("bom").indexOf('\uFEFF') > 0 && samples.get("bom").indexOf('\u2060') > 0
                        && samples.get("bom").indexOf('\u202E') > 0 && samples.get("bom").indexOf('\u202C') > 0);
        Assert.assertTrue("纯剥离行全零宽字符在场",
                samples.get("pureStrip").contains("\u2060\u2061\u2062\u2063\u2064\uFEFF"));
        Assert.assertTrue("泰语/阿拉伯组合堆叠在场",
                samples.get("water").contains("\u0E34\u06D6") && samples.get("water").contains("\u06E3"));
        Assert.assertTrue("§ 格式码与续传样本在场",
                minecraft.contains("§a甲\u2028§a乙") && minecraft.contains("§aa\t§ab"));
        Assert.assertTrue("RICH 标签蓝与 LS 共存样本在场",
                richMode.contains("<color=#4FC3F7>甲\u2028乙</color>"));

        theme.set(LIGHT);
        runtime.flush();

        Assert.assertEquals("NEL 样本逐字不变", samples.get("nel"),
                childStartingWith(cardWithTitle(root, NEWLINE_TITLE), "NEL(U+0085)").getText());
        Assert.assertEquals("tab 样本逐字不变", samples.get("tab"),
                childStartingWith(cardWithTitle(root, TAB_TITLE), "tab：").getText());
        Assert.assertEquals("空白家族样本逐字不变", samples.get("spaceFamily"),
                cardWithTitle(root, SPACE_TITLE).__getChildren().get(1).getText());
        Assert.assertEquals("ZWSP 样本逐字不变", samples.get("zwsp"),
                childStartingWith(cardWithTitle(root, SOFT_TITLE), "ZWSP：").getText());
        Assert.assertEquals("组合堆叠样本逐字不变", samples.get("pyramid"),
                childStartingWith(cardWithTitle(root, CLUSTER_TITLE), "多层堆叠").getText());
        Assert.assertEquals("BOM 样本逐字不变", samples.get("bom"),
                childStartingWith(cardWithTitle(root, STRIP_TITLE), "BOM").getText());
        Assert.assertEquals("灌水文本样本逐字不变", samples.get("water"),
                cardWithTitle(root, WATER_TITLE).__getChildren().get(1).getText());
        Assert.assertEquals("MINECRAFT § 样本逐字不变（原版格式化色不被主题改写）",
                minecraft, childStartingWith(cardWithTitle(root, MODE_TITLE), "MINECRAFT：").getText());
        Assert.assertEquals("RICH 标签色样本逐字不变", richMode,
                childStartingWith(cardWithTitle(root, MODE_TITLE), "RICH：").getText());
    }

    // ==================== ④ 页面身份与结构不变 ====================

    /** 页面 id/标题/说明、注册名与九卡结构不变（迁移不触碰页面装配契约）。 */
    @Test
    public void pageIdentityStructureAndRegistrationUnchanged() {
        ControlCharPage page = new ControlCharPage();
        Assert.assertEquals("页面 id 不变", "control-chars", page.id());
        Assert.assertEquals("页面标题不变", "控制字符", page.title());
        Assert.assertEquals("页面说明不变",
                "Unicode 控制字符实际解析：换行类/tab列宽/空白家族/ZWSP软断行/软连字符/粘合/剥离类",
                page.description());
        Assert.assertTrue("注册表 control-chars 项仍是 ControlCharPage 实例",
                PlaygroundPageRegistry.lookup("control-chars") instanceof ControlCharPage);

        SceneNode root = mountPage();
        Assert.assertSame("页面根已挂入父节点", root, mountPoint.__getChildren().get(0));
        Assert.assertEquals("页面根仍为 9 张卡片", 9, root.__getChildren().size());
        for (int i = 0; i < CARD_TITLES.length; i++) {
            Assert.assertEquals("第 " + i + " 卡标题不变", CARD_TITLES[i],
                    root.__getChildren().get(i).__getChildren().get(0).getText());
        }
        int[] childCounts = { 8, 5, 3, 4, 6, 4, 5, 3, 5 };
        for (int i = 0; i < childCounts.length; i++) {
            Assert.assertEquals("第 " + i + " 卡子节点数不变", childCounts[i],
                    root.__getChildren().get(i).__getChildren().size());
        }
    }

    // ==================== ⑤ 源码守卫 ====================

    /**
     * 源码守卫：本页不得残留静态取色写入者。本页<b>无节点级刻意保留颜色样本</b>——刻意保留项
     * 全部在字符串数据内（控制字符、{@code §} 码、标签色，均非 {@code 0xFF} 前缀字面量，扫描
     * 不命中），容器/默认前景已全部主题化，故守卫零豁免。
     */
    @Test
    public void pageSourceHasNoStaticPaletteWriters() throws Exception {
        Path path = Paths.get(
                "src/main/java/club/heiqi/uilib/internal/devtools/playground/pages/ControlCharPage.java");
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

        Assert.assertTrue("RAW 样本必须经公共构件主题信号重载", src.contains("PlaygroundKit.text(rt,"));
        Assert.assertTrue("基础正文必须取主题正文前景", src.contains("SceneThemes.foreground("));
        Assert.assertTrue("SceneLabel 必须走主题化 builder 路径", src.contains(".builder("));
        Assert.assertFalse("SceneLabel 不得再显式指定颜色（显式 = 不跟随主题）",
                src.contains(".color("));

        Assert.assertFalse("不得残留 PlaygroundKit 静态色常量", src.contains("PlaygroundKit.TEXT")
                || src.contains("PlaygroundKit.MUTED")
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
        Assert.assertFalse("不得直接取 SceneChromeTokens", src.contains("SceneChromeTokens"));
        Assert.assertFalse("本页无节点级色差样本，不得自带 0xFF 色值", src.contains("0xFF"));
        Assert.assertFalse("不得调用旧 chrome/状态色板接缝", src.contains("applyPanelChrome")
                || src.contains("SceneStateColors") || src.contains("SceneControlChrome"));
    }
}
