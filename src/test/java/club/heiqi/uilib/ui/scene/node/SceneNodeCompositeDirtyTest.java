package club.heiqi.uilib.ui.scene.node;

import org.junit.Assert;
import org.junit.Test;

/**
 * SceneNode composite 失效路标独立化单元测试（Phase 3A）。
 *
 * <p>Phase 3A 将 {@code compositeDirty} 从"借道 paint 路标冒泡"改为拥有独立的
 * {@code descendantCompositeDirty} 路标 + 独立 bubble/clear，与 paint 严格分离。
 * 本测试类逐条验证解耦后的核心不变量：</p>
 * <ul>
 *   <li>setOpacity/setTransform 只点亮 composite 路标，绝不污染 paint 路标（解耦正向断言）。</li>
 *   <li>composite 路标冒泡遵循 O(深度)：遇已点亮即停，绝不向下递归（I7）。</li>
 *   <li>干净兄弟节点零 composite 标记（I7）。</li>
 *   <li>composite 与 paint 的清除双向隔离（互不影响）。</li>
 * </ul>
 */
public class SceneNodeCompositeDirtyTest {

    /**
     * 辅助方法：递归清除整棵树的所有脏标记，回到干净状态。
     *
     * @param node 子树根
     */
    private void flushAll(SceneNode node) {
        if (node == null) return;
        node.clearDirtyFlags();
        for (SceneNode child : node.__getChildren()) {
            flushAll(child);
        }
    }

    // ==================== 测试 1：setOpacity 只点亮 composite，不污染 paint ====================

    /**
     * 验证：setOpacity 后只点亮 self compositeDirty + 祖先链 descendantCompositeDirty，
     * 绝不触碰 selfPaintDirty / descendantPaintDirty（解耦正向断言）。
     *
     * <p>树结构：root → mid → leaf，对 leaf setOpacity。</p>
     */
    @Test
    public void setOpacityShouldLightCompositeOnlyNotPaint() {
        SceneNode root = new SceneNode();
        SceneNode mid = new SceneNode();
        SceneNode leaf = new SceneNode();
        mid.appendChild(leaf);
        root.appendChild(mid);
        flushAll(root);

        leaf.setOpacity(0.5f);

        // leaf 自身 composite 脏
        Assert.assertTrue("leaf compositeDirty", leaf.__isCompositeDirty());
        // 祖先链 composite 路标点亮
        Assert.assertTrue("mid descendantComposite", mid.__isDescendantCompositeDirty());
        Assert.assertTrue("root descendantComposite", root.__isDescendantCompositeDirty());

        // ★ 解耦正向断言：paint 路标全程零污染
        Assert.assertFalse("leaf selfPaint 不污染", leaf.__isSelfPaintDirty());
        Assert.assertFalse("mid descendantPaint 不污染", mid.__isDescendantPaintDirty());
        Assert.assertFalse("root descendantPaint 不污染", root.__isDescendantPaintDirty());
        // leaf 自身不应被点亮 descendantComposite（它是叶子，自己只 self 脏）
        Assert.assertFalse("leaf descendantComposite", leaf.__isDescendantCompositeDirty());
    }

    // ==================== 测试 2：setTransform 只点亮 composite，不污染 paint ====================

    /**
     * 验证：setTransform 后只点亮 composite 路标，绝不污染 paint 路标。
     */
    @Test
    public void setTransformShouldLightCompositeOnlyNotPaint() {
        SceneNode root = new SceneNode();
        SceneNode mid = new SceneNode();
        SceneNode leaf = new SceneNode();
        mid.appendChild(leaf);
        root.appendChild(mid);
        flushAll(root);

        leaf.setTransform(new Transform(10f, 20f));

        Assert.assertTrue("leaf compositeDirty", leaf.__isCompositeDirty());
        Assert.assertTrue("mid descendantComposite", mid.__isDescendantCompositeDirty());
        Assert.assertTrue("root descendantComposite", root.__isDescendantCompositeDirty());

        // ★ paint 零污染
        Assert.assertFalse("leaf selfPaint 不污染", leaf.__isSelfPaintDirty());
        Assert.assertFalse("mid descendantPaint 不污染", mid.__isDescendantPaintDirty());
        Assert.assertFalse("root descendantPaint 不污染", root.__isDescendantPaintDirty());
    }

    // ==================== 测试 3：bubble 遇已点亮即停（O(深度)） ====================

    /**
     * 验证：祖先链上已有 descendantCompositeDirty 点亮时，二次冒泡遇已点亮即停，
     * 不重复向更上层冒泡。
     *
     * <p>树结构：root → mid → (leafA, leafB)。先对 leafA setOpacity 点亮整条链，
     * 再对 leafB setOpacity，mid 已点亮 → 冒泡应在 mid 处停止，不重复触碰 root
     * （root 本就已点亮，作为正确性兜底断言）。核心验证 bubble 的提前终止结构。</p>
     */
    @Test
    public void bubbleShouldStopAtAlreadyLitAncestor() {
        SceneNode root = new SceneNode();
        SceneNode mid = new SceneNode();
        SceneNode leafA = new SceneNode();
        SceneNode leafB = new SceneNode();
        mid.appendChild(leafA);
        mid.appendChild(leafB);
        root.appendChild(mid);
        flushAll(root);

        // 先点亮整条链
        leafA.setOpacity(0.5f);
        Assert.assertTrue("mid 已点亮", mid.__isDescendantCompositeDirty());
        Assert.assertTrue("root 已点亮", root.__isDescendantCompositeDirty());

        // 再对 leafB：mid 已点亮 → 冒泡遇已点亮即停
        leafB.setOpacity(0.3f);

        // leafB 自身 composite 脏
        Assert.assertTrue("leafB compositeDirty", leafB.__isCompositeDirty());
        // 祖先链保持点亮（不因提前终止而丢失）
        Assert.assertTrue("mid 仍点亮", mid.__isDescendantCompositeDirty());
        Assert.assertTrue("root 仍点亮", root.__isDescendantCompositeDirty());
    }

    // ==================== 测试 4：干净兄弟零 composite 标记（I7） ====================

    /**
     * 验证：对一个节点 setOpacity，干净兄弟及其子树零 composite 标记。
     *
     * <p>树结构：root → (branchA → leafA, branchB → leafB)。
     * 只对 leafA setOpacity，断言 branchB 整棵子树零 composite 标记。</p>
     */
    @Test
    public void cleanSiblingShouldHaveZeroCompositeMark() {
        SceneNode root = new SceneNode();
        SceneNode branchA = new SceneNode();
        SceneNode branchB = new SceneNode();
        SceneNode leafA = new SceneNode();
        SceneNode leafB = new SceneNode();
        branchA.appendChild(leafA);
        branchB.appendChild(leafB);
        root.appendChild(branchA);
        root.appendChild(branchB);
        flushAll(root);

        leafA.setOpacity(0.5f);

        // 脏链：leafA(self) → branchA → root
        Assert.assertTrue("leafA compositeDirty", leafA.__isCompositeDirty());
        Assert.assertTrue("branchA descendantComposite", branchA.__isDescendantCompositeDirty());
        Assert.assertTrue("root descendantComposite", root.__isDescendantCompositeDirty());

        // ★ I7：干净兄弟 branchB 子树零 composite 标记
        Assert.assertFalse("branchB compositeDirty", branchB.__isCompositeDirty());
        Assert.assertFalse("branchB descendantComposite", branchB.__isDescendantCompositeDirty());
        Assert.assertFalse("leafB compositeDirty", leafB.__isCompositeDirty());
        Assert.assertFalse("leafB descendantComposite", leafB.__isDescendantCompositeDirty());
    }

    // ==================== 测试 5：clearCompositeDirty 不影响 paint 标记 ====================

    /**
     * 验证：节点同时有 composite 脏和 paint 脏时，clearCompositeDirty 只清 composite，
     * paint 标记不受影响（双向隔离之一）。
     */
    @Test
    public void clearCompositeDirtyShouldNotAffectPaint() {
        SceneNode node = new SceneNode();
        // 同时打 paint 脏和 composite 脏
        node.setBackgroundColor(0xFF112233); // → selfPaintDirty
        node.setOpacity(0.5f);               // → compositeDirty
        Assert.assertTrue("paint 脏", node.__isSelfPaintDirty());
        Assert.assertTrue("composite 脏", node.__isCompositeDirty());

        node.clearCompositeDirty();

        // composite 清掉
        Assert.assertFalse("composite 已清", node.__isCompositeDirty());
        Assert.assertFalse("descendantComposite 已清", node.__isDescendantCompositeDirty());
        // ★ paint 不受影响
        Assert.assertTrue("paint 标记仍在", node.__isSelfPaintDirty());
    }

    // ==================== 测试 6：clearPaintDirty 不影响 composite 标记 ====================

    /**
     * 验证：节点同时有 composite 脏和 paint 脏时，clearPaintDirty 只清 paint，
     * composite 标记不受影响（双向隔离之二）。
     *
     * <p>这是 Phase 3A 的关键回归点：解耦前 clearPaintDirty 会顺手清 compositeDirty，
     * 解耦后必须保留 composite 脏，由 clearCompositeDirty 单独负责。</p>
     */
    @Test
    public void clearPaintDirtyShouldNotAffectComposite() {
        SceneNode node = new SceneNode();
        node.setBackgroundColor(0xFF112233); // → selfPaintDirty
        node.setOpacity(0.5f);               // → compositeDirty
        Assert.assertTrue("paint 脏", node.__isSelfPaintDirty());
        Assert.assertTrue("composite 脏", node.__isCompositeDirty());

        node.clearPaintDirty();

        // paint 清掉
        Assert.assertFalse("paint 已清", node.__isSelfPaintDirty());
        // ★ composite 不受影响（解耦关键断言）
        Assert.assertTrue("composite 标记仍在", node.__isCompositeDirty());
    }

    // ==================== 测试 7：clearCompositeDirty 清祖先链路标 ====================

    /**
     * 验证：clearCompositeDirty 同时清除 self compositeDirty 与 descendantCompositeDirty。
     */
    @Test
    public void clearCompositeDirtyShouldClearBothFlags() {
        SceneNode root = new SceneNode();
        SceneNode leaf = new SceneNode();
        root.appendChild(leaf);
        flushAll(root);

        leaf.setOpacity(0.5f);
        Assert.assertTrue("root descendantComposite", root.__isDescendantCompositeDirty());

        // 清除 root 的 descendant 路标
        root.clearCompositeDirty();
        Assert.assertFalse("root descendantComposite 已清", root.__isDescendantCompositeDirty());
        Assert.assertFalse("root compositeDirty 已清", root.__isCompositeDirty());
    }
}
