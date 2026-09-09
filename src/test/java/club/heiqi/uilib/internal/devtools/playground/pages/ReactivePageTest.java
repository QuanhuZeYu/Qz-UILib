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

import club.heiqi.uilib.internal.devtools.playground.PlaygroundKit;
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
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * {@link ReactivePage} 默认消费主题的页面级测试（G16/ReactivePage）。
 *
 * <p><b>迁移口径</b>：计数读数、派生读数、标签项文本、列表统计四处普通文字由静态
 * {@code PlaygroundKit.TEXT/MUTED} 改为来源主题 {@link SceneThemes#foreground}/{@link
 * SceneThemes#mutedForeground} 派生信号（经公共构件 {@code text(rt, ...)} 绑定）；主题切换只
 * 重派生颜色，不重建节点、不改字号、不改页面数据；{@code rt.show}/{@code rt.forEach} 的
 * 演示行为（徽标按计数切换、标签按 key 增删）经真实按钮点击验证保持不变。</p>
 *
 * <p><b>反向钉住</b>：三枚状态徽标属契约 §7.3「状态徽标」不迁移清单——正/负/零语义色由
 * 参数显式传入（{@code ACCENT/DANGER/MUTED}），徽标文字保持显式 {@code TEXT}，胶囊
 * {@code 999} 圆角是几何而非材质；主题切换（含浅色档，其正文色为近黑、accent/danger 均与
 * 样本色不同值）不得改写这些显式样本，否则浅色档近黑正文落在高饱和深底上不可读。</p>
 *
 * <p>测试自建 runtime 并 {@link SceneThemes#install} 默认主题，在 {@code rt.mount} 的 builder
 * 内执行真实页工厂（与宿主 {@code TestPlaygroundHost} 同一装配路径；本类位于 {@code pages}
 * 子包，不能访问宿主的包级探针，故直接以 runtime + 页工厂 + 真实布局/事件管线断言）。</p>
 */
public class ReactivePageTest {

    /** 宿主默认档（深色液态玻璃）。 */
    private static final SceneTheme DARK = SceneThemes.DEFAULT;
    /** 浅色档：正文/次要前景与深色档不同（正文为近黑），用于主题切换与反向钉住断言。 */
    private static final SceneTheme LIGHT = SceneTheme.liquidGlassLight();

    /** 布局画布：三张卡片纵向叠放，需足够高避免可用高挤压命中盒。 */
    private static final int CANVAS_WIDTH = 900;
    private static final int CANVAS_HEIGHT = 2600;

    private SceneRuntime runtime;
    private SceneLayoutEngine layoutEngine;
    private Signal<SceneTheme> theme;
    private SceneNode mountPoint;
    private MountHandle pageHandle;

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
        pageHandle = runtime.mount(mountPoint, new ReactivePage().build(runtime));
        runtime.flush();
        doLayout();
        Assert.assertNotNull("响应式页必须挂载成功", pageHandle);
        return pageHandle.getRoot();
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

    /** 递归查找首个指定文本的节点（徽标/标签项经 show/forEach 动态挂载，按文本锚定）；无则 null。 */
    private static SceneNode findText(SceneNode node, String text) {
        if (text.equals(node.getText())) {
            return node;
        }
        for (SceneNode child : node.__getChildren()) {
            SceneNode found = findText(child, text);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 当前挂载中的徽标节点（含指定文字的文本叶的父节点）；三枚 show 互斥，至多一枚存在。 */
    private static SceneNode badgeNode(SceneNode root, String label) {
        SceneNode text = findText(root, label);
        Assert.assertNotNull("徽标「" + label + "」应处于挂载状态", text);
        return text.__getParent();
    }

    /** 计数器卡（第 1 张）。 */
    private static SceneNode counterCard(SceneNode root) {
        return cardWithTitle(root, "Signal → Computed → bind（派生同步）");
    }

    /** show 卡（第 2 张）。 */
    private static SceneNode showCard(SceneNode root) {
        return cardWithTitle(root, "rt.show 条件渲染（按信号轻挂载/卸载内容）");
    }

    /** forEach 卡（第 3 张）。 */
    private static SceneNode listCard(SceneNode root) {
        return cardWithTitle(root, "rt.forEach keyed 列表（增删按 key 协调）");
    }

    private static SceneNode countReadout(SceneNode root) {
        SceneNode node = counterCard(root).__getChildren().get(2);
        Assert.assertTrue("结构锚：计数读数文本以「计数：」开头", node.getText() != null
                && node.getText().startsWith("计数："));
        return node;
    }

    private static SceneNode derivedReadout(SceneNode root) {
        SceneNode node = counterCard(root).__getChildren().get(3);
        Assert.assertTrue("结构锚：派生读数文本以「派生：」开头", node.getText() != null
                && node.getText().startsWith("派生："));
        return node;
    }

    private static SceneNode listStats(SceneNode root) {
        SceneNode node = listCard(root).__getChildren().get(3);
        Assert.assertTrue("结构锚：统计文本以「标签数：」开头", node.getText() != null
                && node.getText().startsWith("标签数："));
        return node;
    }

    /** 计数器卡操作行（−1 / +1 / 重置 三按钮）。 */
    private static SceneNode counterOps(SceneNode root) {
        return counterCard(root).__getChildren().get(1);
    }

    /** forEach 列表容器（标签行挂这里）。 */
    private static SceneNode listContainer(SceneNode root) {
        return listCard(root).__getChildren().get(2);
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

    // ==================== ① 目标文字取主题前景/次要前景 ====================

    /**
     * 默认工厂路径：计数读数与标签项文本取来源主题正文前景、派生读数与列表统计取次要前景；
     * 字号保持 14/12/13/12；读数内容仍由页面 signal 派生（迁移只改取色路径）。
     */
    @Test
    public void readoutTextsFollowSourceThemeForegroundAndMuted() {
        SceneNode root = mountPage();

        SceneNode count = countReadout(root);
        Assert.assertEquals("计数读数取主题正文前景", DARK.foreground(), count.getTextColor());
        Assert.assertEquals("计数读数初始文案不变", "计数：0", count.getText());
        Assert.assertEquals("计数读数字号保持 14", 14, count.getFontSize());
        Assert.assertFalse("计数读数不可命中", count.isHitTestable());

        SceneNode derived = derivedReadout(root);
        Assert.assertEquals("派生读数取主题次要前景", DARK.mutedForeground(), derived.getTextColor());
        Assert.assertEquals("派生读数初始文案不变", "派生：平方 = 0　奇偶 = 偶", derived.getText());
        Assert.assertEquals("派生读数字号保持 12", 12, derived.getFontSize());

        // 演示动作：新增两个标签（forEach 按 key 挂载行），标签项文本随主题。
        clickNode(findText(root, "新增标签").__getParent());
        clickNode(findText(root, "新增标签").__getParent());
        SceneNode label1 = findText(root, "标签#1");
        SceneNode label2 = findText(root, "标签#2");
        Assert.assertNotNull("forEach 演示：标签#1 已挂载", label1);
        Assert.assertNotNull("forEach 演示：标签#2 已挂载", label2);
        Assert.assertEquals("标签项文本取主题正文前景 #1", DARK.foreground(), label1.getTextColor());
        Assert.assertEquals("标签项文本取主题正文前景 #2", DARK.foreground(), label2.getTextColor());
        Assert.assertEquals("标签项字号保持 13", 13, label1.getFontSize());
        Assert.assertEquals("key 说明仍走公共 hint（主题次要前景）", DARK.mutedForeground(),
                findText(root, "（key=t-1）").getTextColor());

        SceneNode stats = listStats(root);
        Assert.assertEquals("列表统计取主题次要前景", DARK.mutedForeground(), stats.getTextColor());
        Assert.assertTrue("列表统计仍反映标签数：" + stats.getText(), stats.getText().startsWith("标签数：2"));
    }

    // ==================== ② 主题切换：颜色更新、身份不变、数据不变 ====================

    /**
     * 主题切换：正文/次要前景读数更新为浅色档，节点身份不变、字号不变、整页文本快照不变
     * （计数/派生/标签/徽标文案均不参与主题）、订阅数不增长。
     */
    @Test
    public void themeSwitchUpdatesReadoutsWithoutRebuildOrDataLoss() {
        Assert.assertNotEquals("测试前提：深浅正文前景必须不同", DARK.foreground(), LIGHT.foreground());
        Assert.assertNotEquals("测试前提：深浅次要前景必须不同",
                DARK.mutedForeground(), LIGHT.mutedForeground());

        SceneNode root = mountPage();
        // 演示动作先行：正计数徽标 + 一个标签行，作为「数据不变」快照的一部分。
        clickNode(findText(root, "+1").__getParent());
        clickNode(findText(root, "新增标签").__getParent());
        SceneNode count = countReadout(root);
        SceneNode derived = derivedReadout(root);
        SceneNode stats = listStats(root);
        SceneNode label = findText(root, "标签#1");
        SceneNode badgeText = findText(root, "当前为正计数");
        Assert.assertNotNull("切换前正计数徽标应已挂载", badgeText);
        List<String> textsBefore = textSnapshot(root);
        int effectsBefore = ReactiveTestProbe.registeredEffectCount();

        theme.set(LIGHT);
        runtime.flush();

        Assert.assertSame("主题切换不重建计数读数节点", count, countReadout(root));
        Assert.assertSame("主题切换不重建派生读数节点", derived, derivedReadout(root));
        Assert.assertSame("主题切换不重建统计节点", stats, listStats(root));
        Assert.assertSame("主题切换不重建 forEach 标签行节点", label, findText(root, "标签#1"));
        Assert.assertSame("主题切换不重建徽标文本节点", badgeText, findText(root, "当前为正计数"));
        Assert.assertEquals("切换后计数读数取浅色档正文前景", LIGHT.foreground(), count.getTextColor());
        Assert.assertEquals("切换后派生读数取浅色档次要前景", LIGHT.mutedForeground(), derived.getTextColor());
        Assert.assertEquals("切换后标签项取浅色档正文前景", LIGHT.foreground(), label.getTextColor());
        Assert.assertEquals("切换后统计取浅色档次要前景", LIGHT.mutedForeground(), stats.getTextColor());
        Assert.assertEquals("主题切换不改字号（计数）", 14, count.getFontSize());
        Assert.assertEquals("主题切换不改字号（标签项）", 13, label.getFontSize());
        Assert.assertEquals("主题切换不改整页文本数据", textsBefore, textSnapshot(root));
        Assert.assertEquals("主题切换不新增订阅",
                effectsBefore, ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== ③ 保留的显式样本：状态徽标不被主题接管（反向钉住） ====================

    /**
     * 反向钉住（契约 §7.3「状态徽标」）：正/负/零三枚徽标的底色是<b>参数传入的状态语义色</b>
     * （{@code ACCENT/DANGER/MUTED}），文字是显式 {@code TEXT}——主题切换（尤其浅色档：正文近黑、
     * accent={@code 0xFF6750A4}、danger={@code 0xFFB3261E} 与深色档 danger={@code 0xFF7F1D1D}
     * 各成一格）后全部保持显式样本色不变、胶囊 999 圆角与节点身份不变；徽标色同时与主题
     * danger/accent/次要前景断言不等，证明它们不是「恰好同值」而是未被主题接管。
     */
    @Test
    public void stateBadgesStayExplicitUnderThemeSwitch() {
        Assert.assertEquals("样本前提：浅色档正文为近黑（若被主题接管，徽标文字在高饱和深底上不可读）",
                0xFF1C1B1F, LIGHT.foreground());

        SceneNode root = mountPage();
        // 初始 count=0 → 「计数为零」徽标（MUTED 中性底）挂载。深色档 MUTED 与主题次要前景
        // 恰好同值（0xFFCAC4D0），反向钉住必须在浅色档做（0xFF49454F ≠ 样本）。
        SceneNode zeroBadge = badgeNode(root, "计数为零");
        Assert.assertEquals("零计数徽标底色 = 显式 MUTED 语义色", PlaygroundKit.MUTED,
                zeroBadge.getBackgroundColor());

        theme.set(LIGHT);
        runtime.flush();

        Assert.assertSame("主题切换不重建零计数徽标", zeroBadge, badgeNode(root, "计数为零"));
        Assert.assertEquals("浅色档下零计数徽标底色仍为显式 MUTED（不被主题接管）",
                PlaygroundKit.MUTED, zeroBadge.getBackgroundColor());
        Assert.assertNotEquals("浅色档次要与样本不同值（防「恰好同值」假绿）",
                LIGHT.mutedForeground(), zeroBadge.getBackgroundColor());
        Assert.assertEquals("徽标文字保持显式 TEXT（浅色档近黑正文不可读场景反向钉住）",
                PlaygroundKit.TEXT, badgeNode(root, "计数为零").__getChildren().get(0).getTextColor());
        Assert.assertNotEquals("徽标文字 ≠ 浅色档正文前景", LIGHT.foreground(),
                PlaygroundKit.TEXT);
        Assert.assertEquals("胶囊 999 圆角是几何（非材质半径），主题切换不改", 999,
                zeroBadge.getCornerRadius());

        // 演示动作跨三档：重置 → +1（正计数 ACCENT）→ −1 ×2（负计数 DANGER）。
        theme.set(DARK);
        runtime.flush();
        clickNode(findText(root, "+1").__getParent());
        SceneNode posBadge = badgeNode(root, "当前为正计数");
        Assert.assertNull("show 协调：切换为正计数后零徽标卸载", findText(root, "计数为零"));
        Assert.assertEquals("正计数徽标底色 = 显式 ACCENT 语义色", PlaygroundKit.ACCENT,
                posBadge.getBackgroundColor());

        theme.set(LIGHT);
        runtime.flush();
        Assert.assertTrue("浅色档 accent 配方与样本不同值（前提，防「恰好同值」假绿)",
                LIGHT.accent() != PlaygroundKit.ACCENT && LIGHT.accent() == 0xFF6750A4);
        Assert.assertEquals("浅色档下正计数徽标仍为显式 ACCENT（不随 accent(rt) 走）",
                PlaygroundKit.ACCENT, posBadge.getBackgroundColor());

        clickNode(findText(root, "−1").__getParent());
        clickNode(findText(root, "−1").__getParent());
        SceneNode negBadge = badgeNode(root, "当前为负计数");
        Assert.assertEquals("负计数徽标底色 = 显式 DANGER 警示红", PlaygroundKit.DANGER,
                negBadge.getBackgroundColor());
        Assert.assertNull("show 协调：负计数时正徽标卸载", findText(root, "当前为正计数"));

        // DANGER 档钉回深色档：浅色档 danger 与样本恰好同值（0xFFB3261E），深色档 danger
        // （0xFF7F1D1D）才构成「未被主题 danger(rt) 接管」的强证据。
        theme.set(DARK);
        runtime.flush();
        Assert.assertEquals("深色档下负计数徽标仍为显式 DANGER", PlaygroundKit.DANGER,
                negBadge.getBackgroundColor());
        Assert.assertNotEquals("负计数徽标 ≠ 深色档 danger 语义（0xFF7F1D1D，改取主题会静默换色）",
                DARK.danger(), negBadge.getBackgroundColor());
    }

    // ==================== ④ 演示行为与页面身份/结构不变 ====================

    /** forEach 增删协调不变：新增按 key 追加、移除末尾只动受影响项，稳定项节点身份保持。 */
    @Test
    public void forEachAddRemoveKeepsStableItemIdentity() {
        SceneNode root = mountPage();
        SceneNode add = findText(root, "新增标签").__getParent();
        SceneNode remove = findText(root, "移除末尾").__getParent();

        clickNode(add);
        clickNode(add);
        SceneNode row1 = listContainer(root).__getChildren().isEmpty() ? null
                : findText(root, "标签#1").__getParent();
        Assert.assertNotNull("两标签追加后按 key 各挂载一行", row1);
        Assert.assertEquals("列表统计跟随增删", "标签数：2", listStats(root).getText().split("　")[0]);

        clickNode(remove);
        Assert.assertNull("移除末尾只动末项", findText(root, "标签#2"));
        Assert.assertSame("稳定项零重建", row1, findText(root, "标签#1").__getParent());
        Assert.assertEquals("统计同步减一", "标签数：1", listStats(root).getText().split("　")[0]);
    }

    /** 页面 id/标题/说明、注册名与三卡结构、按钮清单不变（迁移不触碰页面装配契约）。 */
    @Test
    public void pageIdentityStructureAndRegistrationUnchanged() {
        ReactivePage page = new ReactivePage();
        Assert.assertEquals("页面 id 不变", "reactive", page.id());
        Assert.assertEquals("页面标题不变", "响应式", page.title());
        Assert.assertEquals("页面说明不变",
                "Signal/Computed/rt.show/rt.forEach：数据驱动 UI 的底层原语演示",
                page.description());
        Assert.assertTrue("注册表 reactive 项仍是 ReactivePage 实例",
                PlaygroundPageRegistry.lookup("reactive") instanceof ReactivePage);

        SceneNode root = mountPage();
        Assert.assertSame("页面根已挂入父节点", root, mountPoint.__getChildren().get(0));
        Assert.assertEquals("页面根仍为 3 张卡片", 3, root.__getChildren().size());
        Assert.assertEquals("三卡标题不变", "Signal → Computed → bind（派生同步）",
                counterCard(root).__getChildren().get(0).getText());
        Assert.assertEquals("三卡标题不变", "rt.show 条件渲染（按信号轻挂载/卸载内容）",
                showCard(root).__getChildren().get(0).getText());
        Assert.assertEquals("三卡标题不变", "rt.forEach keyed 列表（增删按 key 协调）",
                listCard(root).__getChildren().get(0).getText());

        String[] counterLabels = { "−1", "+1", "重置" };
        List<SceneNode> ops = counterOps(root).__getChildren();
        Assert.assertEquals("计数按钮数量不变", counterLabels.length, ops.size());
        for (int i = 0; i < counterLabels.length; i++) {
            Assert.assertEquals("计数按钮标签不变 #" + i,
                    counterLabels[i], firstText(ops.get(i)));
        }
        List<SceneNode> listOps = listCard(root).__getChildren().get(1).__getChildren();
        Assert.assertEquals("列表按钮数量不变", 2, listOps.size());
        Assert.assertEquals("列表按钮标签不变 #0", "新增标签", firstText(listOps.get(0)));
        Assert.assertEquals("列表按钮标签不变 #1", "移除末尾", firstText(listOps.get(1)));
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

    // ==================== ⑤ 卸载回收 ====================

    /** 页面卸载后前景/读数/徽标/列表绑定全部回收，effect 数回到挂载前基线。 */
    @Test
    public void unmountReclaimsPageBindings() {
        int baseline = ReactiveTestProbe.registeredEffectCount();
        mountPage();
        Assert.assertTrue("挂载后应注册响应式工作",
                ReactiveTestProbe.registeredEffectCount() > baseline);
        pageHandle.dispose();
        runtime.flush();
        Assert.assertEquals("卸载后本页绑定全部回收",
                baseline, ReactiveTestProbe.registeredEffectCount());
    }

    // ==================== ⑤ 源码守卫（含徽标豁免条款） ====================

    /**
     * 源码守卫：本页不得自带静态外观写入者；<b>豁免清单 = 状态徽标刻意保留的显式样本</b>
     * （契约 §7.3，逐处说明见 {@code ReactivePage} 内注释）：
     * {@code PlaygroundKit.TEXT} 恰 1 处（徽标文字）、{@code ACCENT/DANGER/MUTED} 各恰 1 处
     * （徽标三档语义色实参）、{@code setBackgroundColor(/setCornerRadius(} 各恰 1 处
     * （仅 {@code badge()} 内部：语义色底 + 999 胶囊几何）。超出「恰 1 处」即红，防止样本面扩大。
     */
    @Test
    public void pageSourceHasNoStaticPaletteWriters() throws Exception {
        Path path = Paths.get(
                "src/main/java/club/heiqi/uilib/internal/devtools/playground/pages/ReactivePage.java");
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

        Assert.assertTrue("读数文字必须经公共构件的主题信号重载", src.contains("PlaygroundKit.text(rt,"));
        Assert.assertTrue("正文读数必须取主题正文前景", src.contains("SceneThemes.foreground("));
        Assert.assertTrue("次要读数必须取主题次要前景", src.contains("SceneThemes.mutedForeground("));

        Assert.assertEquals("PlaygroundKit.TEXT 仅允许徽标文字 1 处（豁免见 badge()）",
                1, count(src, "PlaygroundKit.TEXT"));
        Assert.assertEquals("PlaygroundKit.ACCENT 仅允许正计数徽标 1 处",
                1, count(src, "PlaygroundKit.ACCENT"));
        Assert.assertEquals("PlaygroundKit.DANGER 仅允许负计数徽标 1 处",
                1, count(src, "PlaygroundKit.DANGER"));
        Assert.assertEquals("PlaygroundKit.MUTED 仅允许零计数徽标 1 处",
                1, count(src, "PlaygroundKit.MUTED"));
        Assert.assertEquals("setBackgroundColor 仅允许 badge() 语义色底 1 处",
                1, count(src, "setBackgroundColor("));
        Assert.assertEquals("setCornerRadius 仅允许 badge() 999 胶囊几何 1 处",
                1, count(src, "setCornerRadius("));
        Assert.assertEquals("徽标几何钉死为胶囊 999", 1, count(src, "setCornerRadius(999)"));

        Assert.assertFalse("不得残留静态边框写入者", src.contains("setBorderWidth(")
                || src.contains("setBorderColor("));
        Assert.assertFalse("不得直接取 SceneChromeTokens", src.contains("SceneChromeTokens"));
        Assert.assertFalse("不得自带 0xFF 色值样本", src.contains("0xFF"));
        Assert.assertFalse("不得调用旧 chrome/状态色板接缝", src.contains("applyPanelChrome")
                || src.contains("SceneStateColors") || src.contains("SceneControlChrome")
                || src.contains("PlaygroundKit.PANEL_BG") || src.contains("PlaygroundKit.BORDER")
                || src.contains("PlaygroundKit.ROOT_BG"));
    }

    private static int count(String haystack, String needle) {
        int n = 0;
        for (int i = haystack.indexOf(needle); i >= 0; i = haystack.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }
}
