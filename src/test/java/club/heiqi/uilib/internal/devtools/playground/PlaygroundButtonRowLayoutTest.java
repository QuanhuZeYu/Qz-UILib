package club.heiqi.uilib.internal.devtools.playground;

import java.util.ArrayList;
import java.util.List;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.scene.input.InputFrameBuilder;
import club.heiqi.uilib.ui.scene.input.RawInputEvent;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.SceneNode.WidthSizing;

/**
 * 测试场地演示页「按钮行不越界/不重叠」回归测试（对应真机反馈：按钮跑到容器最右侧、
 * 只露出一点圆角边缘）。
 *
 * <p>不变量（对每个演示页的整棵子树成立）：</p>
 * <ul>
 *   <li>每个 ROW 容器（除 scrollableX 视口之外）的每个直接子节点局部坐标
 *       {@code 0 <= x && x + width <= 行宽} —— 子不得溢出行容器右缘；</li>
 *   <li>行容器 children 无重复引用 —— 同一控件不得被挂载两次
 *       （PlaygroundKit.button 已内部 mount 挂载，调用方不得再 append）；</li>
 *   <li>同父兄弟沿主轴不重叠 —— 后一个子的 x 不得早于前一个子的右缘；</li>
 *   <li>带 chrome（圆角/内边距）的控件子（按钮/徽标）宽度应严格小于行宽 ——
 *       即必须按内容宽 SHRINK 排布，而非 FILL 拉满整行。
 *       该条直接捕获「按钮整行填满、后续按钮溢出到行外」的布局回归。</li>
 * </ul>
 *
 * <p>headless 构造（input=null）+ 真实布局引擎驱动，与 {@link TestPlaygroundHostTest}
 * 同口径。</p>
 */
public class PlaygroundButtonRowLayoutTest {

    private static final int CANVAS_WIDTH = 720;
    private static final int CANVAS_HEIGHT = 520;

    private TestPlaygroundHost host;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        host = new TestPlaygroundHost(null);
        doLayout();
    }

    @After
    public void tearDown() {
        host.dispose();
        ReactiveScheduler.get().reset();
    }

    // ==================== 辅助方法 ====================

    /** 用宿主布局引擎对齐主树（与真机 render 前的主树 layout 同口径）。 */
    private void doLayout() {
        SceneLayoutEngine engine = host.getLayoutEngine();
        engine.layout(host.__getRoot(), new Constraints(CANVAS_WIDTH, CANVAS_HEIGHT));
    }

    /** 取第 index 个导航段节点（segmented root 的第 index 个子节点）。 */
    private SceneNode navSegment(int index) {
        SceneNode segmentedRoot = host.__getNavBar().__getChildren().get(0);
        return segmentedRoot.__getChildren().get(index);
    }

    /** 在指定节点中心合成 CLICK（DOWN+UP 两帧 route + flush）。 */
    private void clickNode(SceneNode node) {
        AnchorRect box = SceneGeometry.absoluteBox(node, 0, 0);
        int x = box.getX() + box.getWidth() / 2;
        int y = box.getY() + box.getHeight() / 2;
        InputFrameBuilder builder = new InputFrameBuilder(x, y);
        builder.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_DOWN, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1000L));
        builder.push(RawInputEvent.ofPointer(ScenePointerAction.BUTTON_UP, x, y, SceneMouseButton.LEFT,
                0, 0, 0, false, false, false, false, 1001L));
        host.__getRuntime().route(host.__getRoot(), builder.drainFrame(), 0, 0);
        host.__getRuntime().flush();
    }

    /** 切到指定页并重排。 */
    private void switchToPage(int index) {
        clickNode(navSegment(index));
        doLayout();
    }

    /** ROW 容器与其直接子节点的一对（供断言）。 */
    private static final class RowChild {
        private final SceneNode row;
        private final SceneNode child;

        RowChild(SceneNode row, SceneNode child) {
            this.row = row;
            this.child = child;
        }
    }

    /** 收集以 root 为根的子树内全部「ROW 容器 → 直接子」对。 */
    private static void collectRowChildren(SceneNode node, List<RowChild> out) {
        if (node.getFlexDirection() == FlexDirection.ROW && !node.isScrollableX()) {
            for (SceneNode child : node.__getChildren()) {
                out.add(new RowChild(node, child));
            }
        }
        for (SceneNode child : node.__getChildren()) {
            collectRowChildren(child, out);
        }
    }

    private static LayoutBox boxOf(SceneNode node, String where) {
        Object cached = node.getCachedLayout();
        Assert.assertNotNull(where + " 节点尚未布局（cachedLayout=null）", cached);
        Assert.assertTrue(where + " cachedLayout 不是 LayoutBox: " + cached.getClass(),
                cached instanceof LayoutBox);
        return (LayoutBox) cached;
    }

    /** 是否带控件 chrome（圆角或内边距）——按钮/徽标等按内容宽排布的控件形态。 */
    private static boolean isChromeChild(SceneNode node) {
        return node.getCornerRadius() > 0
                || node.getPaddingLeft() + node.getPaddingRight() > 0
                || node.getPaddingTop() + node.getPaddingBottom() > 0;
    }

    // ==================== 回归不变量 ====================

    @Test
    public void buttonRowsStayInsideRowContainersOnEveryPage() {
        List<PlaygroundPage> pages = PlaygroundPageRegistry.defaultPages();
        for (int i = 0; i < pages.size(); i++) {
            switchToPage(i);
            SceneNode pageRoot = host.__getDisplayedPageRoot();
            Assert.assertNotNull("页根不应为 null: " + pages.get(i).id(), pageRoot);

            List<RowChild> pairs = new ArrayList<RowChild>();
            collectRowChildren(pageRoot, pairs);
            Assert.assertFalse("页面「" + pages.get(i).id() + "」应存在至少一个 ROW 行", pairs.isEmpty());

            // 不变量 0：行 children 无重复引用（防止把已 mount 挂载的控件再次 append 造成双份）。
            java.util.List<SceneNode> flatKids = new ArrayList<SceneNode>();
            java.util.Set<SceneNode> seen = new java.util.HashSet<SceneNode>();
            for (RowChild rc : pairs) {
                if (flatKids.isEmpty() || flatKids.get(flatKids.size() - 1) != rc.row) {
                    seen.clear();
                }
                flatKids.add(rc.child);
                Assert.assertTrue("页面「" + pages.get(i).id() + "」同一行内出现重复子节点引用（控件被挂载两次）",
                        seen.add(rc.child));
            }

            for (RowChild rc : pairs) {
                LayoutBox rowBox = boxOf(rc.row, "行容器");
                LayoutBox childBox = boxOf(rc.child, "行子节点");
                String where = "页面「" + pages.get(i).id() + "」行 @x=" + rowBox.getX() + ",w="
                        + rowBox.getWidth() + " 的子";

                // 不变量 1：子局部坐标不得溢出所在行的内容区（含行右缘）。
                Assert.assertTrue(where + " 左缘越界（x=" + childBox.getX() + " < 0）",
                        childBox.getX() >= 0);
                Assert.assertTrue(where + " 右缘越出行容器（x+w="
                                + (childBox.getX() + childBox.getWidth()) + " > 行宽 " + rowBox.getWidth() + "）",
                        childBox.getX() + childBox.getWidth() <= rowBox.getWidth());

                // 不变量 2：带控件 chrome 的子（按钮/徽标）必须按内容宽 SHRINK 排布，
                // 不得 FILL 拉满整行——这会把同行的后续按钮挤出容器右缘（真机症状）。
                if (isChromeChild(rc.child)) {
                    Assert.assertTrue(where + " 控件子(marker="
                                    + rc.child.getWidthSizing() + ",w=" + childBox.getWidth()
                                    + ")宽度应严格小于行宽 " + rowBox.getWidth()
                                    + "（须 WidthSizing.SHRINK 而非 FILL）",
                            childBox.getWidth() < rowBox.getWidth());
                }
            }
        }
    }

    @Test
    public void rowSiblingsDoNotOverlapOnEveryPage() {
        List<PlaygroundPage> pages = PlaygroundPageRegistry.defaultPages();
        for (int i = 0; i < pages.size(); i++) {
            switchToPage(i);
            SceneNode pageRoot = host.__getDisplayedPageRoot();
            List<RowChild> pairs = new ArrayList<RowChild>();
            collectRowChildren(pageRoot, pairs);

            // 按行分组：同一 row 的所有子按出现顺序（即布局顺序）断言相邻不重叠。
            // 收集行容器 → 其全部子（保持顺序）。
            List<Object[]> perRow = new ArrayList<Object[]>();
            SceneNode lastRow = null;
            List<SceneNode> lastKids = null;
            for (RowChild rc : pairs) {
                if (rc.row != lastRow) {
                    lastRow = rc.row;
                    lastKids = new ArrayList<SceneNode>();
                    perRow.add(new Object[] {lastRow, lastKids});
                }
                lastKids.add(rc.child);
            }
            for (Object[] entry : perRow) {
                SceneNode row = (SceneNode) entry[0];
                @SuppressWarnings("unchecked")
                List<SceneNode> kids = (List<SceneNode>) entry[1];
                LayoutBox rowBox = boxOf(row, "行容器");
                SceneNode prev = null;
                LayoutBox prevBox = null;
                for (SceneNode kid : kids) {
                    LayoutBox kidBox = boxOf(kid, "行子节点");
                    if (prev != null) {
                        String where = "页面「" + pages.get(i).id() + "」行 @w=" + rowBox.getWidth()
                                + " 相邻子";
                        Assert.assertTrue(where + " 重叠：后子 x=" + kidBox.getX()
                                        + " < 前子右缘 x+w=" + (prevBox.getX() + prevBox.getWidth()),
                                kidBox.getX() >= prevBox.getX() + prevBox.getWidth());
                    }
                    prev = kid;
                    prevBox = kidBox;
                }
            }
        }
    }

    @Test
    public void shrinkButtonsHaveContentDrivenWidth() {
        // 直接对齐引擎语义：显式断言按钮（带圆角的行子）布局后按内容宽而非整行宽。
        // 与 SizingCalculator.computeWidth 的 SHRINK 分支语义挂钩，作为
        // WidthSizing 默认 FILL 的对照锚点（防止今后误改默认值后本测试静默变绿）。
        switchToPage(1); // 单行文本页：opsRow 按钮行
        SceneNode pageRoot = host.__getDisplayedPageRoot();
        List<RowChild> pairs = new ArrayList<RowChild>();
        collectRowChildren(pageRoot, pairs);
        int chromeCount = 0;
        for (RowChild rc : pairs) {
            if (!isChromeChild(rc.child)) {
                continue;
            }
            chromeCount++;
            Assert.assertEquals("带 chrome 的控件子应为 SHRINK 宽度策略",
                    WidthSizing.SHRINK, rc.child.getWidthSizing());
        }
        Assert.assertTrue("单行文本页应存在按钮（带 chrome 的 ROW 子）", chromeCount >= 3);
    }
}
