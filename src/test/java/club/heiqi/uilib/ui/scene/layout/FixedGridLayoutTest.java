package club.heiqi.uilib.ui.scene.layout;

import static org.junit.Assert.assertSame;

import org.junit.Test;

import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 固定列 Grid 的 L2 纯数学布局测试。 */
public class FixedGridLayoutTest {

    /**
     * 两列三项覆盖 padding、单一 gap、margin、非整除列宽、行高 max 与不满末行。
     */
    @Test
    public void twoColumns_positionsRowMajorAndAggregatesNaturalHeight() {
        SceneLayoutEngine engine = new SceneLayoutEngine(new FixedTextMeasurer(8, 16));
        SceneNode grid = SceneNode.grid(2)
                .setPadding(2, 4, 3, 3)
                .setGap(3)
                .setMainAxisAlign(MainAxisAlign.END)
                .setCrossAxisAlign(CrossAxisAlign.END);
        SceneNode a = new SceneNode().setPreferredHeight(10).setMargin(1, 2, 3, 4);
        SceneNode b = new SceneNode().setPreferredWidth(30).setPreferredHeight(18)
                .setMargin(2, 1, 1, 1).setAlignSelf(AlignSelf.CENTER);
        SceneNode c = new SceneNode().setPreferredWidth(25).setPreferredHeight(12)
                .setMargin(4, 0, 2, 2);
        grid.appendChild(a);
        grid.appendChild(b);
        grid.appendChild(c);

        engine.layout(grid, new Constraints(107));

        // inner=100，usable=97，轨道宽 49/48，第二列起点 52。
        LayoutAssertions.assertLocalBox(a, 7, 3, 43, 10);
        LayoutAssertions.assertLocalBox(b, 56, 4, 30, 18);
        // 第一行占位高 max(14,21)=21；第二行从 2+21+3=26 起。
        LayoutAssertions.assertLocalBox(c, 5, 30, 25, 12);
        LayoutAssertions.assertLocalBox(grid, 0, 0, 107, 47);
    }

    /** 显式 preferred 尺寸不应被 Grid 自动 stretch 或 clamp 到轨道。 */
    @Test
    public void explicitChildSize_isNotRewrittenByGridTracks() {
        SceneLayoutEngine engine = new SceneLayoutEngine(new FixedTextMeasurer(8, 16));
        SceneNode grid = SceneNode.grid(2);
        SceneNode wide = new SceneNode().setPreferredWidth(70).setPreferredHeight(11);
        SceneNode narrow = new SceneNode().setPreferredWidth(9).setPreferredHeight(7);
        grid.appendChild(wide);
        grid.appendChild(narrow);

        engine.layout(grid, new Constraints(100));

        LayoutAssertions.assertLocalBox(wide, 0, 0, 70, 11);
        LayoutAssertions.assertLocalBox(narrow, 50, 0, 9, 7);
        LayoutAssertions.assertHeight(grid, 11);
    }

    /** gridColumns=0 必须保留既有 COLUMN Flex 逐项堆叠。 */
    @Test
    public void zeroColumns_fallsBackToFlex() {
        SceneLayoutEngine engine = new SceneLayoutEngine(new FixedTextMeasurer(8, 16));
        SceneNode flex = new SceneNode();
        SceneNode a = new SceneNode().setPreferredWidth(20).setPreferredHeight(10);
        SceneNode b = new SceneNode().setPreferredWidth(30).setPreferredHeight(12);
        flex.appendChild(a);
        flex.appendChild(b);

        engine.layout(flex, new Constraints(100));

        LayoutAssertions.assertLocalBox(a, 0, 0, 20, 10);
        LayoutAssertions.assertLocalBox(b, 0, 10, 30, 12);
        LayoutAssertions.assertHeight(flex, 22);
    }

    /** 列数变化只重算必要父节点；值未变的首项盒引用稳定，随后干净帧零重算。 */
    @Test
    public void columnChange_repositionsPreciselyAndCleanFrameSkipsAll() {
        SceneLayoutEngine engine = new SceneLayoutEngine(new FixedTextMeasurer(8, 16));
        SceneNode grid = SceneNode.grid(2);
        SceneNode a = fixedCard();
        SceneNode b = fixedCard();
        SceneNode c = fixedCard();
        grid.appendChild(a);
        grid.appendChild(b);
        grid.appendChild(c);
        Constraints constraints = new Constraints(100);
        engine.layout(grid, constraints);
        Object firstBox = a.getCachedLayout();

        grid.setGridColumns(3);
        LayoutResult changed = engine.layout(grid, constraints);

        LayoutAssertions.assertRelayoutSet(changed, grid);
        assertSame("几何值不变的首项应复用 LayoutBox 引用", firstBox, a.getCachedLayout());
        LayoutAssertions.assertLocalBox(a, 0, 0, 10, 10);
        LayoutAssertions.assertLocalBox(b, 34, 0, 10, 10);
        LayoutAssertions.assertLocalBox(c, 67, 0, 10, 10);
        LayoutAssertions.assertHeight(grid, 10);

        LayoutAssertions.assertNoRelayout(engine.layout(grid, constraints));
    }

    private static SceneNode fixedCard() {
        return new SceneNode().setPreferredWidth(10).setPreferredHeight(10);
    }
}
