package club.heiqi.uilib.ui.scene.layout;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 内容折叠声明与「子树挂摘」两种形态的等价性守卫（U-P5-17 收尾的等价性证明）。
 *
 * <p>同构装配（树结构、属性、约束逐字段相同），只有显隐机制不同：</p>
 * <ul>
 *   <li>{@link Form#DETACH}：第五轮形态 —— 隐藏时把 label/按钮 {@code removeChild} 摘出树；</li>
 *   <li>{@link Form#COLLAPSE}：本轮形态 —— 子控件常驻，隐藏时 {@link SceneNode#setCollapsed(boolean)}
 *       声明内容折叠。</li>
 * </ul>
 *
 * <p>断言「两种形态的布局结果逐值一致」：对整棵可观测树（<b>不进入折叠子树</b>这一与布局域
 * 完全同口径的遍历规则）按结构路径收集 {@code (x, y, w, h)}，逐键逐值相等；并比较
 * {@link SceneGeometry#maxScrollY} 与视口/面板盒。隐藏态与可见态都断言，且在
 * 隐↔显 多次切换后重复断言（覆盖折叠/解折叠的失效链：解折叠若未整体重算，可见态比较当场红）。</p>
 */
public class CollapsedLayoutEquivalenceTest {

    /** 成员带/面板尺寸（与 picker 夹具同量级）。 */
    private static final int PANEL_WIDTH = 400;
    private static final int PANEL_HEIGHT = 200;
    /** 撤销条行高（可见态，来自字号的派生值）。 */
    private static final int ROW_VISIBLE_HEIGHT = 16;

    /** 显隐机制形态。 */
    private enum Form {
        /** 第五轮：按可见性挂摘子内容。 */
        DETACH,
        /** 本轮：子控件常驻 + 内容折叠声明。 */
        COLLAPSE
    }

    /**
     * 同构装配：{@code root(COLUMN, preferredHeight=PANEL_HEIGHT)} → panel(COLUMN, preferredHeight)
     * → [header, viewport(scrollable, fillParentHeight, flexGrow=1) → content → cell, row(撤销条)]。
     *
     * <p>面板高可先验 + 同级 grow 视口 + 末位常驻零高行 = U-P5-17 陷阱的完整现场。</p>
     */
    private static final class Assembly {

        final Form form;
        final SceneLayoutEngine engine;
        final SceneNode root;
        final SceneNode panel;
        final SceneNode header;
        final SceneNode viewport;
        final SceneNode content;
        final SceneNode cell;
        final SceneNode row;
        final SceneNode label;
        final SceneNode button;

        Assembly(Form form, FixedTextMeasurer measurer) {
            this.form = form;
            this.engine = new SceneLayoutEngine(measurer);

            root = SceneNode.column();
            root.setPreferredHeight(PANEL_HEIGHT);

            panel = SceneNode.column();
            panel.setPreferredHeight(PANEL_HEIGHT);
            panel.setClipChildren(true);
            root.appendChild(panel);

            header = SceneNode.row();
            header.setPreferredHeight(24);
            panel.appendChild(header);

            viewport = SceneNode.column();
            viewport.setScrollable(true);
            viewport.setFillParentHeight(true);
            viewport.setFlexGrow(1);
            viewport.setClipChildren(true);
            content = SceneNode.column();
            cell = new SceneNode();
            cell.setPreferredHeight(300);
            content.appendChild(cell);
            viewport.appendChild(content);
            panel.appendChild(viewport);

            row = SceneNode.row();
            row.setGap(4);
            row.setPadding(0, 6, 0, 6);
            row.setClipChildren(true);
            row.setHitTestable(false);
            label = new SceneNode();
            label.setText("撤销删除");
            label.setFlexGrow(1);
            button = new SceneNode();
            button.setPreferredHeight(20);
            row.appendChild(label);
            row.appendChild(button);
            panel.appendChild(row);
        }

        /** 切到指定可见性（两种形态各自的机制；行高派生信号两种形态完全一致）。 */
        void setVisible(boolean visible) {
            row.setPreferredHeight(visible ? ROW_VISIBLE_HEIGHT : 0);
            if (form == Form.DETACH) {
                if (visible) {
                    if (label.__getParent() == null) {
                        row.appendChild(label);
                        row.appendChild(button);
                    }
                } else if (label.__getParent() != null) {
                    row.removeChild(label);
                    row.removeChild(button);
                }
            } else {
                row.setCollapsed(!visible);
            }
        }

        void layout() {
            engine.layout(root, new Constraints(PANEL_WIDTH, PANEL_HEIGHT));
        }

        /** 可观测布局快照：按结构路径收集盒值；折叠子树整体不进入（= 布局域口径）。 */
        Map<String, String> observableBoxes() {
            Map<String, String> boxes = new LinkedHashMap<String, String>();
            collect(root, "root", boxes);
            return boxes;
        }

        private static void collect(SceneNode node, String path, Map<String, String> out) {
            Object cached = node.getCachedLayout();
            out.put(path, cached instanceof LayoutBox
                    ? ((LayoutBox) cached).toString() : "none");
            if (node.isCollapsed()) {
                return;   // 折叠子树不属布局域，两种形态下都不进快照
            }
            java.util.List<SceneNode> children = node.__getChildren();
            for (int i = 0; i < children.size(); i++) {
                collect(children.get(i), path + "/" + i, out);
            }
        }

        int maxScroll() {
            return SceneGeometry.maxScrollY(viewport);
        }
    }

    private static void assertSameObservableLayout(Assembly detach, Assembly collapse, String state) {
        Map<String, String> a = detach.observableBoxes();
        Map<String, String> b = collapse.observableBoxes();
        Assert.assertEquals("[" + state + "] 可观测结构路径集合应逐键一致", a.keySet(), b.keySet());
        for (Map.Entry<String, String> entry : a.entrySet()) {
            Assert.assertEquals("[" + state + "] 路径 " + entry.getKey() + " 的布局盒应逐值一致",
                    entry.getValue(), b.get(entry.getKey()));
        }
        Assert.assertEquals("[" + state + "] maxScrollY 应逐值一致",
                detach.maxScroll(), collapse.maxScroll());
    }

    /** 隐藏态：两种形态布局结果逐值一致（grow 分配同样正常）。 */
    @Test
    public void collapseDeclarationMatchesDetachFormValueByValue() {
        Assembly detach = new Assembly(Form.DETACH, new FixedTextMeasurer(8, 16));
        Assembly collapse = new Assembly(Form.COLLAPSE, new FixedTextMeasurer(8, 16));

        detach.setVisible(false);
        collapse.setVisible(false);
        detach.layout();
        collapse.layout();
        detach.layout();
        collapse.layout();

        assertSameObservableLayout(detach, collapse, "隐藏态");

        // 陷阱不复发（两形态共同的关键几何）：视口高 = 面板高 - header（grow 分配值），带内可滚动
        Assert.assertEquals("隐藏态视口高应有界（= 面板高 - header 24）",
                PANEL_HEIGHT - 24, ((LayoutBox) collapse.viewport.getCachedLayout()).getHeight());
        Assert.assertEquals("隐藏态 maxScrollY = 内容高 - 视口高",
                300 - (PANEL_HEIGHT - 24), collapse.maxScroll());
    }

    /** 可见态：子控件重新参与（挂摘 / 解折叠），布局结果仍逐值一致。 */
    @Test
    public void visibleStateMatchesDetachFormValueByValue() {
        Assembly detach = new Assembly(Form.DETACH, new FixedTextMeasurer(8, 16));
        Assembly collapse = new Assembly(Form.COLLAPSE, new FixedTextMeasurer(8, 16));

        detach.setVisible(true);
        collapse.setVisible(true);
        detach.layout();
        collapse.layout();
        detach.layout();
        collapse.layout();

        assertSameObservableLayout(detach, collapse, "可见态");

        // 交叉轴 STRETCH 拉满行内高（20），主轴 shrink-to-fit 到文本宽 32，起点 = 行左 padding 6
        Assert.assertEquals("可见态 label 盒应参与布局（6,0,32,20）",
                new LayoutBox(6, 0, 32, 20).toString(),
                collapse.label.getCachedLayout().toString());
        Assert.assertEquals("可见态行高 = max(内容 20, 派生 16) = 20",
                20, ((LayoutBox) collapse.row.getCachedLayout()).getHeight());
    }

    /** 多次隐↔显切换后逐态逐值一致（失效链完整：解折叠整体重算，无陈旧盒子）。 */
    @Test
    public void alternatingVisibilityStaysEquivalent() {
        Assembly detach = new Assembly(Form.DETACH, new FixedTextMeasurer(8, 16));
        Assembly collapse = new Assembly(Form.COLLAPSE, new FixedTextMeasurer(8, 16));

        for (int round = 0; round < 3; round++) {
            boolean visible = (round % 2) == 0;
            detach.setVisible(visible);
            collapse.setVisible(visible);
            detach.layout();
            collapse.layout();
            detach.layout();
            collapse.layout();
            assertSameObservableLayout(detach, collapse, "第 " + (round + 1) + " 轮 visible=" + visible);
        }
    }

    /** 折叠期外部约束变化：解折叠后两种形态仍逐值一致（折叠期子树保持脏态 ⇒ 必须整体重算）。 */
    @Test
    public void constraintChangeWhileCollapsedStaysEquivalent() {
        Assembly detach = new Assembly(Form.DETACH, new FixedTextMeasurer(8, 16));
        Assembly collapse = new Assembly(Form.COLLAPSE, new FixedTextMeasurer(8, 16));

        detach.setVisible(false);
        collapse.setVisible(false);
        detach.layout();
        collapse.layout();

        // 隐藏期间面板宽度变化（两种形态都经历同一输入变化）
        detach.root.setPreferredHeight(240);
        collapse.root.setPreferredHeight(240);
        detach.layout();
        collapse.layout();
        assertSameObservableLayout(detach, collapse, "折叠期约束变化后（仍隐藏）");

        // 再显形：折叠期未布局的子树必须整体重算到新约束
        detach.setVisible(true);
        collapse.setVisible(true);
        detach.layout();
        collapse.layout();
        detach.layout();
        collapse.layout();
        assertSameObservableLayout(detach, collapse, "约束变化后显形");
    }
}
