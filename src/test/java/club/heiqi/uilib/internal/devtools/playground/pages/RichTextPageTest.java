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

import club.heiqi.uilib.internal.devtools.playground.PlaygroundPageRegistry;
import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.ReactiveTestProbe;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.TextStyle;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * {@link RichTextPage} 默认消费主题的页面级测试（G16/RichTextPage）。
 *
 * <p><b>迁移口径</b>：富文本样本的容器/默认前景（标签未覆盖片段的颜色）由静态
 * {@code PlaygroundKit.TEXT} 改为来源主题 {@link SceneThemes#foreground} 派生——静态样本经公共构件
 * {@code PlaygroundKit.text(rt, ...)}，{@code SceneLabel} 走契约 §2.7 主题化默认路径
 * （builder 不调 {@code color(...)}）。主题切换只重派生颜色，不重建节点、不改字号、不改数据。</p>
 *
 * <p><b>反向钉住（渲染协议）</b>：字符串内 {@code <color=#...>}、{@code <mark=...>} 等标签显式色
 * 是 {@code TEXT_MODE_RICH_TAGS} 的输入数据（契约 §7.3「富文本」不迁移清单），主题切换前后逐字
 * 不变——样本色 {@code #4FC3F7} 与深浅两档正文前景均不同值，证明标签色未被容器取色路径改写；
 * 限行/换行等布局合同（maxLines/ellipsis/wrapWidth）保持。</p>
 */
public class RichTextPageTest {

    /** 宿主默认档（深色液态玻璃）。 */
    private static final SceneTheme DARK = SceneThemes.DEFAULT;
    /** 浅色档：正文前景与深色档不同，用于主题切换断言。 */
    private static final SceneTheme LIGHT = SceneTheme.liquidGlassLight();

    /** 布局画布：四张卡片纵向叠放。 */
    private static final int CANVAS_WIDTH = 900;
    private static final int CANVAS_HEIGHT = 2600;

    private static final String STYLE_TITLE = "样式标签（color / b / i / u / s）";
    private static final String SIZE_TITLE = "字号混排与自动换行（size / br / wrapWidth）";
    private static final String TOLERANCE_TITLE = "宽容解析（现代组件惯例：宽容失败）";
    private static final String LIVE_TITLE = "SceneLabel 组件（signal 驱动，按钮切换源文本）";

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

    /** 真实装配路径：页工厂 → mount（builder 在页作用域内执行）→ flush 物化派生 → 布局出命中盒。 */
    private SceneNode mountPage() {
        MountHandle handle = runtime.mount(mountPoint, new RichTextPage().build(runtime));
        runtime.flush();
        doLayout();
        Assert.assertNotNull("富文本页必须挂载成功", handle);
        return handle.getRoot();
    }

    private void doLayout() {
        layoutEngine.layout(mountPoint, new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /** 在指定节点中心合成 CLICK（DOWN+UP 一帧 route + flush + 重排），驱动真实演示动作。 */
    private void clickNode(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        int x = box.getX() + box.getWidth() / 2;
        int y = box.getY() + box.getHeight() / 2;
        InputFrameBuilder builder = new InputFrameBuilder(x, y);
        builder.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        builder.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1001L));
        runtime.route(mountPoint, builder.drainFrame(), 0, 0);
        runtime.flush();
        doLayout();
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

    /** 递归查找首个以指定前缀开头且有文本的节点；无则 null。 */
    private static SceneNode findTextStartsWith(SceneNode node, String prefix) {
        if (node.getText() != null && node.getText().startsWith(prefix)) {
            return node;
        }
        for (SceneNode child : node.__getChildren()) {
            SceneNode found = findTextStartsWith(child, prefix);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 样式卡首个富文本样本（任意 24 位颜色行）。 */
    private static SceneNode firstStyleSample(SceneNode root) {
        SceneNode sample = cardWithTitle(root, STYLE_TITLE).__getChildren().get(1);
        Assert.assertTrue("结构锚：样式卡第 1 个样本为 24 位颜色行",
                sample.getText() != null && sample.getText().contains("任意 24 位颜色"));
        return sample;
    }

    /** 字号卡的 SceneLabel 换行演示（末节点）。 */
    private static SceneNode wrapDemo(SceneNode root) {
        List<SceneNode> children = cardWithTitle(root, SIZE_TITLE).__getChildren();
        SceneNode demo = children.get(children.size() - 1);
        Assert.assertTrue("结构锚：字号卡末节点为 SceneLabel 换行演示",
                demo.getText() != null && demo.getText().contains("演示自动换行"));
        return demo;
    }

    /** 组件卡的 signal 驱动主演示（字号 15、RICH、wrap 320，紧随操作行；文本会被演示按钮改，只锚属性）。 */
    private static SceneNode liveLabel(SceneNode root) {
        SceneNode card = cardWithTitle(root, LIVE_TITLE);
        SceneNode label = card.__getChildren().get(2);
        Assert.assertEquals("结构锚：组件卡第 2 子为 signal 驱动演示（字号 15）", 15, label.getFontSize());
        Assert.assertEquals("结构锚：演示仍为 RICH 模式", TextStyle.TEXT_MODE_RICH_TAGS,
                label.getTextContentMode());
        Assert.assertEquals("结构锚：演示换行宽 320", 320, label.getMaxTextWidth());
        return label;
    }

    /** 组件卡链接回调读数（末节点，RAW 模式）。 */
    private static SceneNode feedbackLabel(SceneNode root) {
        List<SceneNode> children = cardWithTitle(root, LIVE_TITLE).__getChildren();
        SceneNode label = children.get(children.size() - 1);
        Assert.assertTrue("结构锚：组件卡末节点为链接回调读数",
                "（点击下方链接，回调写入这里）".equals(label.getText()));
        return label;
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

    // ==================== ① 容器/默认前景取主题正文 ====================

    /**
     * 默认工厂路径：富文本静态样本、SceneLabel 演示与回调读数的基础正文取来源主题正文前景；
     * 字号保持 15/14/13；内容模式仍为 RICH/RAW（迁移只改容器取色，不动渲染协议声明）。
     */
    @Test
    public void richTextBaseForegroundFollowsSourceTheme() {
        SceneNode root = mountPage();

        SceneNode sample = firstStyleSample(root);
        Assert.assertEquals("富文本样本默认前景取主题正文前景", DARK.foreground(), sample.getTextColor());
        Assert.assertEquals("富文本样本字号保持 15", 15, sample.getFontSize());
        Assert.assertEquals("富文本样本仍是 RICH 模式", TextStyle.TEXT_MODE_RICH_TAGS,
                sample.getTextContentMode());
        Assert.assertFalse("富文本样本不可命中", sample.isHitTestable());

        SceneNode tolerance = findTextStartsWith(cardWithTitle(root, TOLERANCE_TITLE), "未知标签原样保留");
        Assert.assertNotNull("宽容解析卡样本存在", tolerance);
        Assert.assertEquals("宽容解析样本默认前景取主题正文前景", DARK.foreground(), tolerance.getTextColor());

        SceneNode wrap = wrapDemo(root);
        Assert.assertEquals("SceneLabel 换行演示默认前景取主题正文前景", DARK.foreground(), wrap.getTextColor());
        Assert.assertEquals("SceneLabel 换行演示字号保持 14", 14, wrap.getFontSize());
        Assert.assertEquals("SceneLabel 换行宽度合同不变", 320, wrap.getMaxTextWidth());

        SceneNode live = liveLabel(root);
        Assert.assertEquals("signal 驱动演示默认前景取主题正文前景", DARK.foreground(), live.getTextColor());
        Assert.assertEquals("signal 驱动演示字号保持 15", 15, live.getFontSize());

        SceneNode feedback = feedbackLabel(root);
        Assert.assertEquals("链接回调读数取主题正文前景", DARK.foreground(), feedback.getTextColor());
        Assert.assertEquals("链接回调读数字号保持 13", 13, feedback.getFontSize());
        Assert.assertEquals("链接回调读数仍是 RAW 模式", TextStyle.TEXT_MODE_UILIB_RAW,
                feedback.getTextContentMode());
    }

    // ==================== ② 主题切换：颜色更新、身份不变、数据不变 ====================

    /**
     * 主题切换：所有容器/默认前景更新为浅色档正文前景，节点身份不变、字号不变、
     * 整页文本快照不变（富文本源串与标签色是数据，不参与主题）、订阅数不增长。
     */
    @Test
    public void themeSwitchUpdatesBaseForegroundWithoutRebuildOrDataLoss() {
        Assert.assertNotEquals("测试前提：深浅正文前景必须不同", DARK.foreground(), LIGHT.foreground());

        SceneNode root = mountPage();
        SceneNode sample = firstStyleSample(root);
        SceneNode wrap = wrapDemo(root);
        SceneNode live = liveLabel(root);
        SceneNode feedback = feedbackLabel(root);
        List<String> textsBefore = textSnapshot(root);
        int effectsBefore = ReactiveTestProbe.registeredEffectCount();

        theme.set(LIGHT);
        runtime.flush();

        Assert.assertSame("主题切换不重建富文本样本节点", sample, firstStyleSample(root));
        Assert.assertSame("主题切换不重建 SceneLabel 换行演示", wrap, wrapDemo(root));
        Assert.assertSame("主题切换不重建 signal 驱动演示", live, liveLabel(root));
        Assert.assertSame("主题切换不重建回调读数", feedback, feedbackLabel(root));
        Assert.assertEquals("样本默认前景随主题更新", LIGHT.foreground(), sample.getTextColor());
        Assert.assertEquals("SceneLabel 默认前景随主题更新", LIGHT.foreground(), wrap.getTextColor());
        Assert.assertEquals("signal 驱动演示默认前景随主题更新", LIGHT.foreground(), live.getTextColor());
        Assert.assertEquals("回调读数随主题更新", LIGHT.foreground(), feedback.getTextColor());
        Assert.assertEquals("主题切换不改样本文案（含标签源串）", textsBefore, textSnapshot(root));
        Assert.assertEquals("主题切换不新增订阅",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== ③ 保留的渲染协议样本（反向钉住） ====================

    /**
     * 反向钉住（契约 §7.3「富文本」）：标签显式色 {@code #4FC3F7}/{@code #FF5533}/{@code #80FF5533}
     * 与 {@code <mark>} 底色等是渲染协议输入数据——主题切换前后字符串逐字不变，且容器正文前景
     * （深/浅两档）与标签样本色均不同值，证明「容器取主题」没有把标签色刷成主题色；
     * 限行合同（maxLines=2 + ellipsis + wrap 200）与硬换行/容错字面输出样本同样原样保留。
     */
    @Test
    public void richTagExplicitColorsAreProtocolDataUntouchedByTheme() {
        Assert.assertNotEquals("样本前提：#4FC3F7 不是深色档正文前景",
                0xFF4FC3F7, DARK.foreground());
        Assert.assertNotEquals("样本前提：#4FC3F7 不是浅色档正文前景",
                0xFF4FC3F7, LIGHT.foreground());

        SceneNode root = mountPage();
        SceneNode sample = firstStyleSample(root);
        SceneNode markSample = findTextStartsWith(cardWithTitle(root, STYLE_TITLE), "行内高亮：");
        SceneNode argbSample = findTextStartsWith(cardWithTitle(root, STYLE_TITLE), "8 位 ARGB：");
        SceneNode maxLines = findTextStartsWith(cardWithTitle(root, LIVE_TITLE), "<color=#4FC3F7>这是一段足够长");
        String sampleBefore = sample.getText();
        String markBefore = markSample.getText();
        String argbBefore = argbSample.getText();
        String maxBefore = maxLines.getText();

        Assert.assertTrue("样本含标签显式色 #FF5533", sampleBefore.contains("<color=#FF5533>"));
        Assert.assertTrue("样本含命名色标签 gold", sampleBefore.contains("<color=gold>"));
        Assert.assertTrue("高亮样本含 <mark> 显式底色", markBefore.contains("<mark=#80FF5533>"));
        Assert.assertTrue("ARGB 样本含 8 位色", argbBefore.contains("#80FF5533"));
        Assert.assertEquals("限行演示换行合同保持（wrap 200）", 200, maxLines.getMaxTextWidth());
        Assert.assertEquals("限行演示 maxLines 合同保持", 2, maxLines.getMaxLines());
        Assert.assertTrue("限行演示 ellipsis 合同保持", maxLines.isEllipsis());

        theme.set(LIGHT);
        runtime.flush();

        Assert.assertEquals("主题切换不改写标签显式色源串 #FF5533", sampleBefore, sample.getText());
        Assert.assertEquals("主题切换不改写 <mark> 底色源串", markBefore, markSample.getText());
        Assert.assertEquals("主题切换不改写 8 位 ARGB 源串", argbBefore, argbSample.getText());
        Assert.assertEquals("主题切换不改写限行演示源串", maxBefore, maxLines.getText());
    }

    /** 容错字面输出样本（未知标签/坏属性/转义实体）不被主题或迁移改写。 */
    @Test
    public void toleranceSamplesRemainVerbatim() {
        SceneNode root = mountPage();
        SceneNode unknown = findTextStartsWith(cardWithTitle(root, TOLERANCE_TITLE), "未知标签原样保留");
        SceneNode badAttr = findTextStartsWith(cardWithTitle(root, TOLERANCE_TITLE), "坏属性忽略继承父样式");
        SceneNode escaped = findTextStartsWith(cardWithTitle(root, TOLERANCE_TITLE), "转义实体：");
        Assert.assertTrue("未知标签按字面保留在源串", unknown.getText().contains("<foo>尖括号</foo>"));
        Assert.assertTrue("坏属性样本原样", badAttr.getText().contains("<color=不是颜色>"));
        Assert.assertTrue("转义实体样本原样", escaped.getText().contains("&lt;color=red&gt;"));

        theme.set(LIGHT);
        runtime.flush();
        Assert.assertTrue("主题切换后未知标签源串不变", unknown.getText().contains("<foo>尖括号</foo>"));
        Assert.assertEquals("主题切换后默认前景回退链的输入不变（基础色=浅色档正文）",
                LIGHT.foreground(), unknown.getTextColor());
    }

    // ==================== ④ 演示行为与页面身份/结构不变 ====================

    /** SceneLabel signal 驱动合同保持：点「换行/容错/样式」按钮切换 liveLabel 源文本。 */
    @Test
    public void demoButtonsStillSwitchLiveLabelText() {
        SceneNode root = mountPage();
        SceneNode ops = cardWithTitle(root, LIVE_TITLE).__getChildren().get(1);
        SceneNode live = liveLabel(root);
        String initial = live.getText();

        clickNode(ops.__getChildren().get(1)); // 换行
        Assert.assertTrue("换行按钮切换源文本：" + live.getText(),
                live.getText().contains("<color=#FF5533>自动换行演示：</color>"));
        Assert.assertSame("切换源文本不重建演示节点", live, liveLabel(root));

        clickNode(ops.__getChildren().get(2)); // 容错
        Assert.assertTrue("容错按钮切换源文本：" + live.getText(),
                live.getText().contains("未知标签 <foo>x</foo>"));

        clickNode(ops.__getChildren().get(0)); // 样式
        Assert.assertEquals("样式按钮切回初始文本", initial, live.getText());
    }

    /** 页面 id/标题/说明、注册名与四卡结构、按钮清单不变（迁移不触碰页面装配契约）。 */
    @Test
    public void pageIdentityStructureAndRegistrationUnchanged() {
        RichTextPage page = new RichTextPage();
        Assert.assertEquals("页面 id 不变", "rich-text", page.id());
        Assert.assertEquals("页面标题不变", "富文本", page.title());
        Assert.assertEquals("页面说明不变",
                "SceneLabel 现代富文本标签：颜色/粗斜下删/字号混排/换行/宽容解析",
                page.description());
        Assert.assertTrue("注册表 rich-text 项仍是 RichTextPage 实例",
                PlaygroundPageRegistry.lookup("rich-text") instanceof RichTextPage);

        SceneNode root = mountPage();
        Assert.assertSame("页面根已挂入父节点", root, mountPoint.__getChildren().get(0));
        Assert.assertEquals("页面根仍为 4 张卡片", 4, root.__getChildren().size());
        Assert.assertEquals("样式卡结构不变（标题 + 6 样本 + 说明）", 8,
                cardWithTitle(root, STYLE_TITLE).__getChildren().size());
        Assert.assertEquals("字号卡结构不变（标题 + 2 样本 + 说明 + SceneLabel）", 5,
                cardWithTitle(root, SIZE_TITLE).__getChildren().size());
        Assert.assertEquals("容错卡结构不变（标题 + 4 样本）", 5,
                cardWithTitle(root, TOLERANCE_TITLE).__getChildren().size());
        Assert.assertEquals("组件卡结构不变（标题 + 操作行 + 4 演示 + 限行说明）", 7,
                cardWithTitle(root, LIVE_TITLE).__getChildren().size());

        SceneNode ops = cardWithTitle(root, LIVE_TITLE).__getChildren().get(1);
        String[] labels = { "样式", "换行", "容错" };
        Assert.assertEquals("演示按钮数量不变", labels.length, ops.__getChildren().size());
        for (int i = 0; i < labels.length; i++) {
            Assert.assertEquals("演示按钮标签不变 #" + i,
                    labels[i], firstText(ops.__getChildren().get(i)));
        }
    }

    /** 取节点子树首个非空文本（按钮根 → 文本叶）。 */
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

    // ==================== ⑤ 源码守卫 ====================

    /**
     * 源码守卫：本页不得残留静态取色写入者。本页无「刻意保留的节点级显式颜色样本」——
     * 保留项全部是富文本<b>字符串内</b>的标签色（渲染协议数据，不含 {@code 0xFF} 前缀，
     * 扫描不会命中），容器/默认前景已全部主题化，故守卫零豁免。
     */
    @Test
    public void pageSourceHasNoStaticPaletteWriters() throws Exception {
        Path path = Paths.get(
                "src/main/java/club/heiqi/uilib/internal/devtools/playground/pages/RichTextPage.java");
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

        Assert.assertTrue("富文本样本默认前景必须经公共构件主题信号重载", src.contains("PlaygroundKit.text(rt,"));
        Assert.assertTrue("默认前景必须取主题正文前景", src.contains("SceneThemes.foreground("));
        Assert.assertTrue("SceneLabel 必须走主题化 builder 路径", src.contains("SceneLabel.Props"));
        Assert.assertTrue("SceneLabel 必须经 Props.builder 构建", src.contains(".builder("));
        Assert.assertFalse("SceneLabel 不得再显式指定颜色（显式 = 不跟随主题）",
                src.contains(".color("));

        Assert.assertFalse("不得残留 PlaygroundKit 静态色常量", src.contains("PlaygroundKit.TEXT")
                || src.contains("PlaygroundKit.MUTED")
                || src.contains("PlaygroundKit.ACCENT")
                || src.contains("PlaygroundKit.DANGER")
                || src.contains("PlaygroundKit.PANEL_BG")
                || src.contains("PlaygroundKit.BORDER")
                || src.contains("PlaygroundKit.ROOT_BG"));
        Assert.assertFalse("不得残留静态边框/圆角/底色写入者", src.contains("setBorderWidth(")
                || src.contains("setBorderColor(")
                || src.contains("setCornerRadius(")
                || src.contains("setBackgroundColor(")
                || src.contains("setTextColor("));
        Assert.assertFalse("不得直接取 SceneChromeTokens", src.contains("SceneChromeTokens"));
        Assert.assertFalse("本页无刻意节点级色差样本，不得自带 0xFF 色值", src.contains("0xFF"));
        Assert.assertFalse("不得调用旧 chrome/状态色板接缝", src.contains("applyPanelChrome")
                || src.contains("SceneStateColors") || src.contains("SceneControlChrome"));
    }
}
