package club.heiqi.uilib.ui.scene.node;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.layout.AlignSelf;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.LayoutAssertions;
import club.heiqi.uilib.ui.scene.layout.LayoutAssertions.InvalidationLevel;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.image.SceneImageRect;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;

/**
 * I4 失效级别矩阵表测试 —— 对 {@link SceneNode} 全部属性 setter 逐个验证
 * 「调用后恰好点亮正确的失效位，不污染其它级别」。
 *
 * <p>对齐 {@code NORTH_STAR.md} I4 关键不变量：每个属性变化必须只触发其声明级别的
 * 失效（LAYOUT / PAINT / GEOMETRY / COMPOSITE 之一或明确的组合），不得越级污染。
 * 断言工具复用 {@link LayoutAssertions#assertOnlyInvalidation}（恰好单级别）与
 * {@link LayoutAssertions#assertClean}（全 8 位 clean）。</p>
 *
 * <h3>Clean 基线方案</h3>
 * <p>{@code new SceneNode()} 构造函数只初始化 {@code children}，全部 8 个 dirty
 * 字段（4 个 self + 4 个 descendant 路标）均为 Java 默认 {@code false}，故初始即全 clean。
 * 每个 @Test 独立 new 节点，无需 layout 引擎跑清脏、无需反射、无需 clearDirtyFlags。
 * 单节点无 parent，setter 内 mark 方法的向上冒泡 {@code current = parent == null} 立即终止，
 * descendant 路标保持 false，故 {@code assertOnlyInvalidation} 的 descendant 位检查同样通过。</p>
 *
 * <h3>分组</h3>
 * <ul>
 *   <li>LAYOUT 组（18 个）：只调 markSelfLayout</li>
 *   <li>PAINT 组（8 个）：只调 markSelfPaint</li>
 *   <li>GEOMETRY 组（2 个）：只调 markGeometryDirty</li>
 *   <li>COMPOSITE 组（2 个）：只调 markComposite</li>
 *   <li>多位组（2 个）：setText / setFontSize 同时打 LAYOUT + PAINT</li>
 *   <li>零标脏组（2 个）：setCursor / setHitTestable 有意不打任何脏</li>
 * </ul>
 *
 * <p>注：本测试只校验 self dirty 四位（layout/paint/geometry/composite），与
 * {@link LayoutAssertions#assertOnlyInvalidation} 语义一致。</p>
 */
public class InvalidationLevelMatrixTest {

    /** 图片源身份变化只影响绘制。 */
    @Test
    public void setImageSource_marksOnlyPaint() {
        SceneNode node = new SceneNode();
        node.setImageSource(new SceneImageSource() { });
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.PAINT);
    }

    /** 图片目标矩形变化只影响绘制。 */
    @Test
    public void setImageRect_marksOnlyPaint() {
        SceneNode node = new SceneNode();
        node.setImageRect(new SceneImageRect(1, 2, 3, 4));
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.PAINT);
    }

    // ==================== LAYOUT 组（18 个，仅 markSelfLayout） ====================

    /** setFillParentHeight：fill 意图变化影响自身布局，期望恰好 LAYOUT 级失效。 */
    @Test
    public void setFillParentHeight_marksOnlyLayout() {
        SceneNode node = new SceneNode();
        node.setFillParentHeight(true);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.LAYOUT);
    }

    /** setFillParentWidth：fill 意图变化影响自身布局，期望恰好 LAYOUT 级失效。 */
    @Test
    public void setFillParentWidth_marksOnlyLayout() {
        SceneNode node = new SceneNode();
        node.setFillParentWidth(true);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.LAYOUT);
    }

    /** setPreferredHeight：尺寸变化影响自身布局，期望恰好 LAYOUT 级失效。 */
    @Test
    public void setPreferredHeight_marksOnlyLayout() {
        SceneNode node = new SceneNode();
        node.setPreferredHeight(100);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.LAYOUT);
    }

    /** setPreferredWidth：尺寸变化影响自身布局，期望恰好 LAYOUT 级失效。 */
    @Test
    public void setPreferredWidth_marksOnlyLayout() {
        SceneNode node = new SceneNode();
        node.setPreferredWidth(100);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.LAYOUT);
    }

    /** setMaxHeight：尺寸上界变化影响自身布局，期望恰好 LAYOUT 级失效。 */
    @Test
    public void setMaxHeight_marksOnlyLayout() {
        SceneNode node = new SceneNode();
        node.setMaxHeight(200);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.LAYOUT);
    }

    /** setMaxWidth：尺寸上界变化影响自身布局，期望恰好 LAYOUT 级失效。 */
    @Test
    public void setMaxWidth_marksOnlyLayout() {
        SceneNode node = new SceneNode();
        node.setMaxWidth(200);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.LAYOUT);
    }

    /** setPercentHeight：尺寸百分比变化影响自身布局，期望恰好 LAYOUT 级失效。 */
    @Test
    public void setPercentHeight_marksOnlyLayout() {
        SceneNode node = new SceneNode();
        node.setPercentHeight(50);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.LAYOUT);
    }

    /** setPercentWidth：尺寸百分比变化影响自身布局，期望恰好 LAYOUT 级失效。 */
    @Test
    public void setPercentWidth_marksOnlyLayout() {
        SceneNode node = new SceneNode();
        node.setPercentWidth(50);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.LAYOUT);
    }

    /** setWidthSizing：容器宽度策略变化影响自身布局，期望恰好 LAYOUT 级失效。 */
    @Test
    public void setWidthSizing_marksOnlyLayout() {
        SceneNode node = new SceneNode();
        node.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.LAYOUT);
    }

    /** setFlexDirection：主轴方向改变子节点排布，期望恰好 LAYOUT 级失效。 */
    @Test
    public void setFlexDirection_marksOnlyLayout() {
        SceneNode node = new SceneNode();
        node.setFlexDirection(FlexDirection.ROW);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.LAYOUT);
    }

    /** setFlexGrow：grow 权重变化影响剩余空间分配，期望恰好 LAYOUT 级失效。 */
    @Test
    public void setFlexGrow_marksOnlyLayout() {
        SceneNode node = new SceneNode();
        node.setFlexGrow(2);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.LAYOUT);
    }

    /** setPadding(int)：内边距改变盒模型可用空间，期望恰好 LAYOUT 级失效。 */
    @Test
    public void setPadding_marksOnlyLayout() {
        SceneNode node = new SceneNode();
        node.setPadding(5);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.LAYOUT);
    }

    /** setMargin(int)：外边距改变子在父容器内的占用空间，期望恰好 LAYOUT 级失效。 */
    @Test
    public void setMargin_marksOnlyLayout() {
        SceneNode node = new SceneNode();
        node.setMargin(5);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.LAYOUT);
    }

    /** setGap：主轴间距改变子节点排布，期望恰好 LAYOUT 级失效。 */
    @Test
    public void setGap_marksOnlyLayout() {
        SceneNode node = new SceneNode();
        node.setGap(8);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.LAYOUT);
    }

    /** setMainAxisAlign：主轴对齐改变子节点分布，期望恰好 LAYOUT 级失效。 */
    @Test
    public void setMainAxisAlign_marksOnlyLayout() {
        SceneNode node = new SceneNode();
        node.setMainAxisAlign(MainAxisAlign.CENTER);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.LAYOUT);
    }

    /** setCrossAxisAlign：交叉轴对齐改变子节点尺寸/位置，期望恰好 LAYOUT 级失效。 */
    @Test
    public void setCrossAxisAlign_marksOnlyLayout() {
        SceneNode node = new SceneNode();
        node.setCrossAxisAlign(CrossAxisAlign.START);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.LAYOUT);
    }

    /** setAlignSelf：子级交叉轴覆盖改变自身在交叉轴尺寸/位置，期望恰好 LAYOUT 级失效。 */
    @Test
    public void setAlignSelf_marksOnlyLayout() {
        SceneNode node = new SceneNode();
        node.setAlignSelf(AlignSelf.CENTER);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.LAYOUT);
    }

    /** setScrollable：viewport 高度计算语义变化属布局输入，期望恰好 LAYOUT 级失效。 */
    @Test
    public void setScrollable_marksOnlyLayout() {
        SceneNode node = new SceneNode();
        node.setScrollable(true);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.LAYOUT);
    }

    // ==================== PAINT 组（8 个，仅 markSelfPaint） ====================

    /** setBackgroundColor：背景颜色变化只改绘制输出，期望恰好 PAINT 级失效。 */
    @Test
    public void setBackgroundColor_marksOnlyPaint() {
        SceneNode node = new SceneNode();
        node.setBackgroundColor(0xFF0000FF);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.PAINT);
    }

    /** setBorderColor：边框颜色变化只改绘制输出，期望恰好 PAINT 级失效。 */
    @Test
    public void setBorderColor_marksOnlyPaint() {
        SceneNode node = new SceneNode();
        node.setBorderColor(0xFFFFFFFF);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.PAINT);
    }

    /** setBorderWidth：边框宽度变化只改绘制输出，期望恰好 PAINT 级失效。 */
    @Test
    public void setBorderWidth_marksOnlyPaint() {
        SceneNode node = new SceneNode();
        node.setBorderWidth(2);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.PAINT);
    }

    /** setCornerRadius：圆角变化只改绘制输出，期望恰好 PAINT 级失效。 */
    @Test
    public void setCornerRadius_marksOnlyPaint() {
        SceneNode node = new SceneNode();
        node.setCornerRadius(4);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.PAINT);
    }

    /** setClipChildren：裁剪只改绘制输出，期望恰好 PAINT 级失效。 */
    @Test
    public void setClipChildren_marksOnlyPaint() {
        SceneNode node = new SceneNode();
        node.setClipChildren(true);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.PAINT);
    }

    /**
     * setTextColor：文本颜色只改绘制输出不改文字尺寸，期望恰好 PAINT 级失效。
     * <p>注：默认值 0xFFFFFFFF，此处用 0xFF000000（黑色）确保值变化触发失效。</p>
     */
    @Test
    public void setTextColor_marksOnlyPaint() {
        SceneNode node = new SceneNode();
        node.setTextColor(0xFF000000);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.PAINT);
    }

    /**
     * setTextVerticalAlign：文本垂直对齐只影响绘制偏移，期望恰好 PAINT 级失效。
     * <p>注：默认值 CENTER，此处用 TOP 确保值变化触发失效。</p>
     */
    @Test
    public void setTextVerticalAlign_marksOnlyPaint() {
        SceneNode node = new SceneNode();
        node.setTextVerticalAlign(TextVerticalAlign.TOP);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.PAINT);
    }

    /** setTextHorizontalAlign：文本水平对齐只影响绘制偏移，期望恰好 PAINT 级失效。 */
    @Test
    public void setTextHorizontalAlign_marksOnlyPaint() {
        SceneNode node = new SceneNode();
        node.setTextHorizontalAlign(TextHorizontalAlign.CENTER);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.PAINT);
    }

    // ==================== GEOMETRY 组（2 个，仅 markGeometryDirty） ====================

    /** setScrollOffsetY：滚动偏移只平移显示位置不重排不重绘，期望恰好 GEOMETRY 级失效。 */
    @Test
    public void setScrollOffsetY_marksOnlyGeometry() {
        SceneNode node = new SceneNode();
        node.setScrollOffsetY(10);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.GEOMETRY);
    }

    /** internal presentation offset 只重定位 PaintPlan，不清布局或 fragment 缓存。 */
    @Test
    public void setPresentationOffsetY_marksOnlyGeometryAndPreservesCaches() {
        SceneNode parent = new SceneNode();
        SceneNode node = new SceneNode();
        parent.appendChild(node);
        parent.clearDirtyFlags();
        parent.clearGeometryDirty();
        node.clearDirtyFlags();
        node.clearGeometryDirty();
        Object layout = new Object();
        Object paint = new Object();
        node.setCachedLayout(layout);
        node.setCachedPaint(paint);

        assertEquals(0, node.__getPresentationOffsetY());
        node.__setPresentationOffsetY(-12);

        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.GEOMETRY);
        assertTrue("presentation offset 应向祖先冒泡 geometry 路标",
                parent.__isDescendantGeometryDirty());
        assertSame("presentation offset 不清 LayoutBox", layout, node.getCachedLayout());
        assertSame("presentation offset 不清 PaintFragment", paint, node.getCachedPaint());

        parent.clearGeometryDirty();
        node.clearGeometryDirty();
        node.__setPresentationOffsetY(-12);
        LayoutAssertions.assertClean(node);
        assertFalse("同值写入不得重复冒泡", parent.__isDescendantGeometryDirty());
    }

    // ==================== COMPOSITE 组（2 个，仅 markComposite） ====================

    /** setOpacity：不透明度变化只调合成层 group opacity，期望恰好 COMPOSITE 级失效。 */
    @Test
    public void setOpacity_marksOnlyComposite() {
        SceneNode node = new SceneNode();
        node.setOpacity(0.5f);
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.COMPOSITE);
    }

    /** setTransform：变换矩阵只调合成层 transform offset，期望恰好 COMPOSITE 级失效。 */
    @Test
    public void setTransform_marksOnlyComposite() {
        SceneNode node = new SceneNode();
        node.setTransform(new Transform(5f, 5f));
        LayoutAssertions.assertOnlyInvalidation(node, InvalidationLevel.COMPOSITE);
    }

    // ==================== 多位组（2 个，LAYOUT + PAINT 同时） ====================

    /**
     * setText：文本变化同时影响布局盒尺寸/行高（LAYOUT）与绘制输出字符串（PAINT），
     * 期望 LAYOUT + PAINT 双位失效，GEOMETRY + COMPOSITE 保持 false。
     */
    @Test
    public void setText_marksLayoutAndPaint() {
        SceneNode node = new SceneNode();
        node.setText("hello");
        assertTrue("setText 应 selfLayoutDirty", node.__isSelfLayoutDirty());
        assertTrue("setText 应 selfPaintDirty", node.__isSelfPaintDirty());
        assertFalse("setText 不应 selfGeometryDirty", node.__isSelfGeometryDirty());
        assertFalse("setText 不应 compositeDirty", node.__isCompositeDirty());
    }

    /**
     * setFontSize：字号变化既改几何尺寸又改绘制输出，期望 LAYOUT + PAINT 双位失效，
     * GEOMETRY + COMPOSITE 保持 false。
     */
    @Test
    public void setFontSize_marksLayoutAndPaint() {
        SceneNode node = new SceneNode();
        node.setFontSize(20);
        assertTrue("setFontSize 应 selfLayoutDirty", node.__isSelfLayoutDirty());
        assertTrue("setFontSize 应 selfPaintDirty", node.__isSelfPaintDirty());
        assertFalse("setFontSize 不应 selfGeometryDirty", node.__isSelfGeometryDirty());
        assertFalse("setFontSize 不应 compositeDirty", node.__isCompositeDirty());
    }

    // ==================== 零标脏组（2 个，有意不打脏） ====================

    /**
     * setCursor：纯交互投影（D6-A 设计例外），不影响 layout/paint/composite 任何阶段，
     * 期望调用后节点仍全 clean。
     */
    @Test
    public void setCursor_marksNothing() {
        SceneNode node = new SceneNode();
        node.setCursor(SceneCursor.POINTER);
        LayoutAssertions.assertClean(node);
    }

    /**
     * setHitTestable：纯输入路由投影（与 setCursor 同例外），不影响 layout/paint/composite，
     * 期望调用后节点仍全 clean。
     */
    @Test
    public void setHitTestable_marksNothing() {
        SceneNode node = new SceneNode();
        node.setHitTestable(false);
        LayoutAssertions.assertClean(node);
    }
}
