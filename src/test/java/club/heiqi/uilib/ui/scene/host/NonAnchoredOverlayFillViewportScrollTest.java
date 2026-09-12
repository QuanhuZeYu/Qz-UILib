package club.heiqi.uilib.ui.scene.host;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import club.heiqi.uilib.ui.reactive.ReactiveScheduler;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.FixedTextMeasurer;
import club.heiqi.uilib.ui.scene.control.SceneScrollContainer;
import club.heiqi.uilib.ui.scene.layout.Constraints;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * U2 探针：「非锚定 portal 的编辑面占满视口 + 视图内单层滚动」是否只靠<b>既有 public 能力</b>就能达成。
 *
 * <h3>待证事实（设计侧悬而未决项）</h3>
 * <p>非锚定 {@code rt.portal(...)} 的 overlay root <b>不自动撑满</b>视口：宿主布局阶段给 overlay root 的
 * 约束确实是宿主逻辑盒（{@link #layoutOverlayLikeProduction} 复刻&#32;{@code SceneFramePipeline#layoutOverlays}
 * 的非锚定分支 = {@code new Constraints(w, h)}），但「收到约束」不等于「撑满约束」——高度轴默认是
 * content-driven（{@code SizingCalculator.computeHeight}：只有 {@code fillParentHeight}/{@code flexGrow}/
 * {@code percentHeight} 才取 {@code max(内容高, 约束高)}）。</p>
 *
 * <h3>本测试的定位</h3>
 * <p>只使用 UILib <b>src/main 的 public 面</b>（Miner 侧可用的同一面：{@code rt.portal} /
 * {@code SceneNode.setFillParentWidth/setFillParentHeight} / {@code SceneScrollContainer} /
 * {@code SceneGeometry.maxScrollY} / {@code Constraints}），因此结论可直接搬到下游编辑视图：
 * <b>零 UILib 改动</b>即可得到「占满视口 + 单层内滚」。</p>
 *
 * <ul>
 *   <li>{@link #bareOverlayRootDoesNotFillViewportOnHeightAxis()}：负对照——不声明 fill 时 overlay root
 *       高度 = 内容高（不撑满）；</li>
 *   <li>{@link #explicitFillParentOnBothAxesPinsOverlayRootToViewport()}：正解——两轴显式 fill ⇒
 *       root 与宿主逻辑盒逐值同尺寸，且不被超高内容撑大；</li>
 *   <li>{@link #scrollContainerInsideFillRootIsTheOnlyScrollLayer()}：结构——顶栏 + 滚动容器 ⇒
 *       视图内只有一层滚动（容器 viewport），滚程 = 内容高 − viewport 高，页面自身不长高。</li>
 * </ul>
 */
public class NonAnchoredOverlayFillViewportScrollTest {

    /** 宿主逻辑盒（生产上是宿主逻辑宽高；非锚定 overlay 的布局约束即此盒）。 */
    private static final int W = 640;
    private static final int H = 360;
    /** 编辑面顶栏高（真实实现按生效字号派生，这里取常量只为体积可算）。 */
    private static final int TOP_BAR = 40;
    /** 内滚内容：40 行 × 50px = 2000px，远高于视口。 */
    private static final int ROWS = 40;
    private static final int ROW_HEIGHT = 50;

    private SceneRuntime rt;
    private SceneLayoutEngine layoutEngine;

    @Before
    public void setUp() {
        ReactiveScheduler.get().reset();
        FixedTextMeasurer measurer = new FixedTextMeasurer(8, 16);
        rt = new SceneRuntime(measurer);
        layoutEngine = new SceneLayoutEngine(measurer);
    }

    @After
    public void tearDown() {
        rt.dispose();
        ReactiveScheduler.get().reset();
    }

    /**
     * 复刻生产 overlay 布局：{@code SceneFramePipeline.layoutOverlays} 非锚定分支给 overlay root 的约束
     * 就是宿主逻辑盒（{@code new Constraints(w, h)}，相对倍率 s=1 时逐位相等）；随后桥接布局纪元并按
     * 宿主逐帧节奏再布局一趟收敛。
     */
    private void layoutOverlayLikeProduction(SceneNode overlayRoot) {
        layoutEngine.layout(overlayRoot, new Constraints(W, H));
        rt.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        rt.flush();
        layoutEngine.layout(overlayRoot, new Constraints(W, H));
        rt.__bridgeLayoutEpoch(layoutEngine.layoutEpoch());
        rt.flush();
    }

    /** 建一个非锚定 portal，并返回它挂载后的 overlay root（内容根即 overlay root）。 */
    private SceneNode mountOverlay(SceneNode contentRoot) {
        Signal<Boolean> visible = Signal.create(Boolean.TRUE);
        SceneNode[] mounted = new SceneNode[1];
        rt.portal(visible, () -> {
            mounted[0] = contentRoot;
            return contentRoot;
        });
        rt.flush();
        Assert.assertNotNull("portal 内容应已挂载", mounted[0]);
        Assert.assertEquals("非锚定 portal 内容根即 overlay root", 1, rt.getOverlayHost().size());
        return mounted[0];
    }

    /** 递归统计子树内可滚动节点数（「视图内单层滚动」的判定口径）。 */
    private static int countScrollable(SceneNode node) {
        int count = node.isScrollable() ? 1 : 0;
        for (SceneNode child : node.__getChildren()) {
            count += countScrollable(child);
        }
        return count;
    }

    /** 负对照：不声明 fill 的 overlay root 高度由内容决定，不自动撑满视口。 */
    @Test
    public void bareOverlayRootDoesNotFillViewportOnHeightAxis() {
        SceneNode root = SceneNode.column();
        root.appendChild(new SceneNode().setPreferredHeight(TOP_BAR));
        SceneNode overlayRoot = mountOverlay(root);
        layoutOverlayLikeProduction(overlayRoot);

        LayoutBox box = (LayoutBox) overlayRoot.getCachedLayout();
        Assert.assertNotNull(box);
        Assert.assertEquals("默认宽度语义 = 可用宽（容器 fill），宽轴本来就不缩", W, box.getWidth());
        Assert.assertEquals("高度轴默认 content-driven：40px 内容 ⇒ root 高 40，不自动撑满视口",
                TOP_BAR, box.getHeight());
        Assert.assertNotEquals("反向钉住：不声明 fill 就不是视口高", H, box.getHeight());
    }

    /** 正解：两轴显式 fill ⇒ overlay root 与宿主逻辑盒逐值同尺寸（含超高内滚内容时也不被撑大）。 */
    @Test
    public void explicitFillParentOnBothAxesPinsOverlayRootToViewport() {
        SceneNode root = SceneNode.column();
        root.setFillParentWidth(true);
        root.setFillParentHeight(true);
        root.appendChild(new SceneNode().setPreferredHeight(TOP_BAR));
        SceneNode[] viewport = new SceneNode[1];
        SceneScrollContainer.attach(rt, root, content -> {
            for (int row = 0; row < ROWS; row++) {
                content.appendChild(new SceneNode().setPreferredHeight(ROW_HEIGHT));
            }
        });
        viewport[0] = root.__getChildren().get(1).__getChildren().get(0);
        SceneNode overlayRoot = mountOverlay(root);
        layoutOverlayLikeProduction(overlayRoot);

        LayoutBox box = (LayoutBox) overlayRoot.getCachedLayout();
        Assert.assertNotNull(box);
        Assert.assertEquals("两轴 fillParent ⇒ 宽 = 视口宽", W, box.getWidth());
        Assert.assertEquals("两轴 fillParent ⇒ 高 = 视口高（2000px 内滚内容不撑大页面）",
                H, box.getHeight());

        LayoutBox viewportBox = (LayoutBox) viewport[0].getCachedLayout();
        Assert.assertNotNull(viewportBox);
        Assert.assertEquals("滚动容器得到确定高（顶栏之外的全部视口高）",
                H - TOP_BAR, viewportBox.getHeight());
        Assert.assertEquals("滚程 = 内容高 − viewport 高（唯一内滚层的实际可滚量）",
                ROWS * ROW_HEIGHT - (H - TOP_BAR), SceneGeometry.maxScrollY(viewport[0]));
    }

    /** 结构：fill root 内恰一层滚动（容器 viewport），页面自身不可滚、不长高。 */
    @Test
    public void scrollContainerInsideFillRootIsTheOnlyScrollLayer() {
        SceneNode root = SceneNode.column();
        root.setFillParentWidth(true);
        root.setFillParentHeight(true);
        root.appendChild(new SceneNode().setPreferredHeight(TOP_BAR));
        SceneScrollContainer.attach(rt, root, content -> {
            for (int row = 0; row < ROWS; row++) {
                content.appendChild(new SceneNode().setPreferredHeight(ROW_HEIGHT));
            }
        });
        SceneNode overlayRoot = mountOverlay(root);
        layoutOverlayLikeProduction(overlayRoot);

        Assert.assertFalse("页面自身不可滚（滚动只发生在视图内的单一容器里）", overlayRoot.isScrollable());
        Assert.assertEquals("视图内恰一层滚动层", 1, countScrollable(overlayRoot));
        LayoutBox box = (LayoutBox) overlayRoot.getCachedLayout();
        Assert.assertEquals("外层不参与滚动 ⇒ 根盒高恒等于视口高", H, box.getHeight());
    }

    /** 探针：同一结构不重复挂载/卸载时的 overlay 数（供上游判断是否需要自定义宿主）。 */
    @Test
    public void overlayEntryCountIsOneWhileVisible() {
        SceneNode root = SceneNode.column();
        root.setFillParentWidth(true);
        root.setFillParentHeight(true);
        mountOverlay(root);
        layoutOverlayLikeProduction(root);
        AtomicInteger entries = new AtomicInteger(rt.getOverlayHost().size());
        Assert.assertEquals("非锚定编辑面 = 单个 overlay entry", 1, entries.get());
    }
}
