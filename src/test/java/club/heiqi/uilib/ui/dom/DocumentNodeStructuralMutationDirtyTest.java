package club.heiqi.uilib.ui.dom;

import org.junit.Assert;
import org.junit.Test;

/**
 * `DocumentNode` 结构变更标脏粒度契约测试（NORTH_STAR I7：干净子树三阶段跳过）。
 *
 * <p>方案 X 将容器结构变更（append/insert/remove/move）的标脏从"无条件递归整棵子树"
 * 降级为"只标容器自身 self+subtree 版本 + 向上冒泡刷祖先 subtree 版本"。被移动/插入的
 * 节点采用保守版：强制标其自身（正确性安全），但不递归其后代。本测试在 DOM 层直接建树，
 * 用 {@code __getLayoutMutationVersion()}（self）与 {@code __getSubtreeLayoutMutationVersion()}
 * （subtree）断言稳定兄弟与未动子树的版本不被株连。</p>
 */
public class DocumentNodeStructuralMutationDirtyTest {

    /**
     * INSERT 稳定兄弟零株连：容器含 c1/c2/c3，在 c2 前插入新节点后，
     * c1/c3 的 self 且 subtree 版本应不变，容器 self 版本自增。
     */
    @Test
    public void insertDoesNotDirtyStableSiblings() {
        UiDocument document = UiDocument.create();
        ElementNode container = document.div();
        ElementNode c1 = document.div();
        ElementNode c2 = document.div();
        ElementNode c3 = document.div();
        container.appendChild(c1);
        container.appendChild(c2);
        container.appendChild(c3);

        // 记录基线版本
        int c1SelfBefore = c1.__getLayoutMutationVersion();
        int c1SubtreeBefore = c1.__getSubtreeLayoutMutationVersion();
        int c3SelfBefore = c3.__getLayoutMutationVersion();
        int c3SubtreeBefore = c3.__getSubtreeLayoutMutationVersion();
        int containerSelfBefore = container.__getLayoutMutationVersion();

        ElementNode inserted = document.div();
        container.insertBefore(inserted, c2);

        Assert.assertEquals("c1 self 版本应不变", c1SelfBefore, c1.__getLayoutMutationVersion());
        Assert.assertEquals("c1 subtree 版本应不变", c1SubtreeBefore, c1.__getSubtreeLayoutMutationVersion());
        Assert.assertEquals("c3 self 版本应不变", c3SelfBefore, c3.__getLayoutMutationVersion());
        Assert.assertEquals("c3 subtree 版本应不变", c3SubtreeBefore, c3.__getSubtreeLayoutMutationVersion());
        Assert.assertNotEquals("容器 self 版本应自增", containerSelfBefore,
                container.__getLayoutMutationVersion());
    }

    /**
     * REMOVE 稳定兄弟零株连（风险 A removeChild 单次株连回归锚点）：
     * 移除 c2 后，c1/c3 版本应不变，容器 self 版本应变。
     */
    @Test
    public void removeDoesNotDirtyStableSiblings() {
        UiDocument document = UiDocument.create();
        ElementNode container = document.div();
        ElementNode c1 = document.div();
        ElementNode c2 = document.div();
        ElementNode c3 = document.div();
        container.appendChild(c1);
        container.appendChild(c2);
        container.appendChild(c3);

        int c1SelfBefore = c1.__getLayoutMutationVersion();
        int c1SubtreeBefore = c1.__getSubtreeLayoutMutationVersion();
        int c3SelfBefore = c3.__getLayoutMutationVersion();
        int c3SubtreeBefore = c3.__getSubtreeLayoutMutationVersion();
        int containerSelfBefore = container.__getLayoutMutationVersion();

        container.removeChild(c2);

        Assert.assertEquals("c1 self 版本应不变", c1SelfBefore, c1.__getLayoutMutationVersion());
        Assert.assertEquals("c1 subtree 版本应不变", c1SubtreeBefore, c1.__getSubtreeLayoutMutationVersion());
        Assert.assertEquals("c3 self 版本应不变", c3SelfBefore, c3.__getLayoutMutationVersion());
        Assert.assertEquals("c3 subtree 版本应不变", c3SubtreeBefore, c3.__getSubtreeLayoutMutationVersion());
        Assert.assertNotEquals("容器 self 版本应自增", containerSelfBefore,
                container.__getLayoutMutationVersion());
    }

    /**
     * 嵌套子树不被株连（递归全标债最直接铁证）：c1 含孙子 g1，
     * 对容器删除 c3 后，g1 的 self+subtree 版本应不变。
     */
    @Test
    public void nestedDescendantNotDirtiedBySiblingMutation() {
        UiDocument document = UiDocument.create();
        ElementNode container = document.div();
        ElementNode c1 = document.div();
        ElementNode g1 = document.div();
        ElementNode c2 = document.div();
        ElementNode c3 = document.div();
        container.appendChild(c1);
        c1.appendChild(g1);
        container.appendChild(c2);
        container.appendChild(c3);

        int g1SelfBefore = g1.__getLayoutMutationVersion();
        int g1SubtreeBefore = g1.__getSubtreeLayoutMutationVersion();

        container.removeChild(c3);

        Assert.assertEquals("孙子 g1 self 版本应不变", g1SelfBefore, g1.__getLayoutMutationVersion());
        Assert.assertEquals("孙子 g1 subtree 版本应不变", g1SubtreeBefore,
                g1.__getSubtreeLayoutMutationVersion());
    }

    /**
     * MOVE 项子树保护（保守版）：c1 含孙子 g1，把 c1 移到 c3 之后，
     * c1 自身 self 版本应变（被强制重算），但 g1 版本应不变。
     */
    @Test
    public void moveOnlyDirtiesMovedNodeSelfNotItsDescendants() {
        UiDocument document = UiDocument.create();
        ElementNode container = document.div();
        ElementNode c1 = document.div();
        ElementNode g1 = document.div();
        ElementNode c2 = document.div();
        ElementNode c3 = document.div();
        container.appendChild(c1);
        c1.appendChild(g1);
        container.appendChild(c2);
        container.appendChild(c3);

        int g1SelfBefore = g1.__getLayoutMutationVersion();
        int g1SubtreeBefore = g1.__getSubtreeLayoutMutationVersion();
        int c1SelfBefore = c1.__getLayoutMutationVersion();

        // 把 c1 移到末尾（c3 之后），构成一次 MOVE
        container.appendChild(c1);

        Assert.assertNotEquals("被移动节点 c1 self 版本应自增", c1SelfBefore,
                c1.__getLayoutMutationVersion());
        Assert.assertEquals("孙子 g1 self 版本应不变", g1SelfBefore, g1.__getLayoutMutationVersion());
        Assert.assertEquals("孙子 g1 subtree 版本应不变", g1SubtreeBefore,
                g1.__getSubtreeLayoutMutationVersion());
    }

    /**
     * 旧父株连隔离（跨容器移动）：A 含 a1/a2，B 含 b1，把 a2 移入 B 后，
     * a1（旧父稳定兄弟）、b1（新父稳定兄弟）版本应不变，A、B 容器 self 版本应均变。
     */
    @Test
    public void crossContainerMoveIsolatesStableSiblingsInBothParents() {
        UiDocument document = UiDocument.create();
        ElementNode a = document.div();
        ElementNode a1 = document.div();
        ElementNode a2 = document.div();
        ElementNode b = document.div();
        ElementNode b1 = document.div();
        a.appendChild(a1);
        a.appendChild(a2);
        b.appendChild(b1);

        int a1SelfBefore = a1.__getLayoutMutationVersion();
        int b1SelfBefore = b1.__getLayoutMutationVersion();
        int aSelfBefore = a.__getLayoutMutationVersion();
        int bSelfBefore = b.__getLayoutMutationVersion();

        // 把 a2 从 A 移入 B
        b.appendChild(a2);

        Assert.assertEquals("旧父稳定兄弟 a1 self 版本应不变", a1SelfBefore,
                a1.__getLayoutMutationVersion());
        Assert.assertEquals("新父稳定兄弟 b1 self 版本应不变", b1SelfBefore,
                b1.__getLayoutMutationVersion());
        Assert.assertNotEquals("旧父容器 A self 版本应变", aSelfBefore, a.__getLayoutMutationVersion());
        Assert.assertNotEquals("新父容器 B self 版本应变", bSelfBefore, b.__getLayoutMutationVersion());
    }
}
