package club.heiqi.uilib.internal.devtools.playground.pages;

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
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * {@link TextAreaPage} 默认消费主题的页面级测试（G16/TextAreaPage）。
 *
 * <p><b>迁移口径</b>：行数/码点统计文本由静态 {@code PlaygroundKit.MUTED} 改为来源主题
 * {@link SceneThemes#mutedForeground(SceneRuntime)} 派生信号；主题切换只重派生颜色，不重建
 * 节点、不改字号，也不改页面数据（{@code body} signal 与统计结果）。页面构建器签名、页面
 * id/标题、按钮与演示行为全部不动。</p>
 *
 * <p>测试自建 runtime 并 {@link SceneThemes#install} 默认主题，在 {@code rt.mount} 的 builder
 * 内执行真实页工厂（与宿主 {@code TestPlaygroundHost} 同一装配路径；本类位于 {@code pages}
 * 子包，不能访问宿主的包级探针，故直接以 runtime + 页工厂断言）。</p>
 */
public class TextAreaPageTest {

    /** 宿主默认档（深色液态玻璃）。 */
    private static final SceneTheme DARK = SceneThemes.DEFAULT;
    /** 浅色档：次要前景与深色档不同，用于主题切换断言。 */
    private static final SceneTheme LIGHT = SceneTheme.liquidGlassLight();

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
        MountHandle handle = runtime.mount(mountPoint, new TextAreaPage().build(runtime));
        runtime.flush();
        Assert.assertNotNull("多行文本页必须挂载成功", handle);
        return handle.getRoot();
    }

    /** 多行输入卡（root 第 1 张卡）；结构锚先钉，防卡片增删导致取样漂移。 */
    private static SceneNode areaCard(SceneNode root) {
        Assert.assertEquals("多行文本页应为 2 张卡片（多行输入 / 快捷操作）",
                2, root.__getChildren().size());
        SceneNode card = root.__getChildren().get(0);
        Assert.assertEquals("结构锚：第 1 卡首节点为多行输入标题",
                "多行输入（soft wrap + 跨行选区）", card.__getChildren().get(0).getText());
        return card;
    }

    /** 统计文本节点（多行输入卡的末节点）。 */
    private static SceneNode statsNode(SceneNode root) {
        SceneNode card = areaCard(root);
        List<SceneNode> children = card.__getChildren();
        SceneNode stats = children.get(children.size() - 1);
        Assert.assertTrue("结构锚：多行输入卡末节点为统计文本，实测「"
                        + (stats.getText() == null ? "null" : stats.getText()) + "」",
                stats.getText() != null && stats.getText().startsWith("逻辑行："));
        return stats;
    }

    /** 快捷操作卡（root 第 2 张卡）。 */
    private static SceneNode opsCard(SceneNode root) {
        SceneNode card = root.__getChildren().get(1);
        Assert.assertEquals("结构锚：第 2 卡首节点为快捷操作标题",
                "快捷操作", card.__getChildren().get(0).getText());
        return card;
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

    // ==================== ① 目标文字取主题次要前景 ====================

    /**
     * 默认工厂路径：统计文本取来源主题次要前景，字号 12、不可命中；统计内容仍由页面
     * {@code body} signal 派生（样本 5 逻辑行），迁移只改取色路径。
     */
    @Test
    public void statsTextFollowsSourceThemeMutedForeground() {
        SceneNode root = mountPage();
        SceneNode stats = statsNode(root);

        Assert.assertEquals("统计文本取主题次要前景",
                DARK.mutedForeground(), stats.getTextColor());
        Assert.assertEquals("统计文本字号保持 12", 12, stats.getFontSize());
        Assert.assertFalse("统计文本不可命中", stats.isHitTestable());
        Assert.assertTrue("统计文本仍反映样本数据（5 逻辑行）：" + stats.getText(),
                stats.getText().startsWith("逻辑行：5 行"));
        Assert.assertTrue("统计文本仍带码点数说明：" + stats.getText(),
                stats.getText().contains("码点：") && stats.getText().contains("视觉行数由 soft wrap 决定"));
    }

    // ==================== ② 主题切换：颜色更新、身份不变、数据不变 ====================

    /**
     * 主题切换：{@code theme.set(LIGHT)} + flush 后统计文本颜色更新为浅色档次要前景，
     * 节点身份不变、订阅数不增长、整页文本快照不变（主题不参与页面数据）。
     */
    @Test
    public void themeSwitchUpdatesStatsColorWithoutRebuildOrDataLoss() {
        Assert.assertNotEquals("测试前提：深浅次要前景必须不同",
                DARK.mutedForeground(), LIGHT.mutedForeground());

        SceneNode root = mountPage();
        SceneNode stats = statsNode(root);
        String statsBefore = stats.getText();
        List<String> textsBefore = textSnapshot(root);
        int effectsBefore = ReactiveTestProbe.registeredEffectCount();

        theme.set(LIGHT);
        runtime.flush();

        Assert.assertSame("主题切换不重建统计文本节点", stats, statsNode(root));
        Assert.assertEquals("主题切换后统计文本取浅色档次要前景",
                LIGHT.mutedForeground(), stats.getTextColor());
        Assert.assertEquals("主题切换不改统计文本字号", 12, stats.getFontSize());
        Assert.assertEquals("主题切换不改统计结果（页面数据）", statsBefore, stats.getText());
        Assert.assertEquals("主题切换不改整页文本数据", textsBefore, textSnapshot(root));
        Assert.assertEquals("主题切换不新增订阅",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== ③ 页面身份与结构不变 ====================

    /** 页面 id/标题/说明、注册名、根结构与快捷按钮标签不变（迁移不触碰页面装配契约）。 */
    @Test
    public void pageIdentityStructureAndRegistrationUnchanged() {
        TextAreaPage page = new TextAreaPage();
        Assert.assertEquals("页面 id 不变", "text-area", page.id());
        Assert.assertEquals("页面标题不变", "多行文本", page.title());
        Assert.assertEquals("页面说明不变",
                "SceneTextArea：soft wrap 视觉行、跨行选区、Undo/Redo、纵向跟随",
                page.description());
        Assert.assertTrue("注册表 text-area 项仍是 TextAreaPage 实例",
                PlaygroundPageRegistry.lookup("text-area") instanceof TextAreaPage);

        SceneNode root = mountPage();
        Assert.assertSame("页面根已挂入父节点", root, mountPoint.__getChildren().get(0));
        Assert.assertEquals("页面根仍为 2 张卡片", 2, root.__getChildren().size());

        SceneNode card = areaCard(root);
        Assert.assertEquals("多行输入卡结构不变（标题 / 输入框 / 说明 / 统计）",
                4, card.__getChildren().size());
        Assert.assertFalse("多行输入卡含 TextArea 输入根", card.__getChildren().get(1).__getChildren().isEmpty());

        SceneNode ops = opsCard(root);
        SceneNode opsRow = ops.__getChildren().get(1);
        String[] expectedLabels = { "填充示例", "清空", "换行计数" };
        Assert.assertEquals("快捷按钮数量不变", expectedLabels.length, opsRow.__getChildren().size());
        for (int i = 0; i < expectedLabels.length; i++) {
            Assert.assertEquals("快捷按钮标签不变 #" + i,
                    expectedLabels[i], firstText(opsRow.__getChildren().get(i)));
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
}
