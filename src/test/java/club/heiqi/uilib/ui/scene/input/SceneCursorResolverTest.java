package club.heiqi.uilib.ui.scene.input;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import org.junit.Assert;
import org.junit.Test;

/**
 * SceneCursorResolver 纯算法单元测试（I4c cursor 投影）。
 *
 * <p>覆盖：祖先链级联解析（叶节点声明→返回自身 / 叶无父有→返回父 / 都无→DEFAULT / null→DEFAULT）。</p>
 */
public class SceneCursorResolverTest {

    // ==================== 辅助方法 ====================

    /** 构建三层树：root → mid → leaf */
    private SceneNode[] buildThreeLayerTree() {
        SceneNode root = new SceneNode();
        SceneNode mid = new SceneNode();
        SceneNode leaf = new SceneNode();
        root.appendChild(mid);
        mid.appendChild(leaf);
        return new SceneNode[]{root, mid, leaf};
    }

    // ==================== 祖先链解析 ====================

    @Test
    public void shouldReturnLeafCursorWhenLeafDeclares() {
        SceneNode[] tree = buildThreeLayerTree();
        SceneNode leaf = tree[2];
        leaf.setCursor(SceneCursor.POINTER);

        SceneCursor result = SceneCursorResolver.resolve(leaf);
        Assert.assertEquals("叶节点声明 POINTER 应返回 POINTER", SceneCursor.POINTER, result);
    }

    @Test
    public void shouldReturnParentCursorWhenLeafDoesNotDeclare() {
        SceneNode[] tree = buildThreeLayerTree();
        SceneNode mid = tree[1];
        SceneNode leaf = tree[2];
        mid.setCursor(SceneCursor.TEXT);

        SceneCursor result = SceneCursorResolver.resolve(leaf);
        Assert.assertEquals("叶无声明应从父 mid 继承 TEXT", SceneCursor.TEXT, result);
    }

    @Test
    public void shouldReturnRootCursorWhenNoLeafOrMidDeclares() {
        SceneNode[] tree = buildThreeLayerTree();
        SceneNode root = tree[0];
        SceneNode leaf = tree[2];
        root.setCursor(SceneCursor.CROSSHAIR);

        SceneCursor result = SceneCursorResolver.resolve(leaf);
        Assert.assertEquals("叶和 mid 都无声明应从 root 继承 CROSSHAIR", SceneCursor.CROSSHAIR, result);
    }

    @Test
    public void shouldReturnDefaultWhenNoAncestorDeclares() {
        SceneNode[] tree = buildThreeLayerTree();
        SceneNode leaf = tree[2];

        // 整条链都无声明
        SceneCursor result = SceneCursorResolver.resolve(leaf);
        Assert.assertEquals("整条链都无声明应返回 DEFAULT", SceneCursor.DEFAULT, result);
    }

    @Test
    public void shouldReturnDefaultWhenHoveredNodeIsNull() {
        SceneCursor result = SceneCursorResolver.resolve(null);
        Assert.assertEquals("hoveredNode==null 应返回 DEFAULT", SceneCursor.DEFAULT, result);
    }

    // ==================== 中间节点祖先链 ====================

    @Test
    public void shouldResolveFromMidNodeCorrectly() {
        SceneNode[] tree = buildThreeLayerTree();
        SceneNode root = tree[0];
        SceneNode mid = tree[1];
        root.setCursor(SceneCursor.MOVE);

        // 从 mid 开始解析——mid 无声明，应继承 root
        SceneCursor result = SceneCursorResolver.resolve(mid);
        Assert.assertEquals("mid 无声明应从 root 继承 MOVE", SceneCursor.MOVE, result);
    }

    // ==================== 根节点直接解析 ====================

    @Test
    public void shouldResolveFromRootDirectly() {
        SceneNode[] tree = buildThreeLayerTree();
        SceneNode root = tree[0];
        root.setCursor(SceneCursor.GRAB);

        SceneCursor result = SceneCursorResolver.resolve(root);
        Assert.assertEquals("根节点声明 GRAB 应返回 GRAB", SceneCursor.GRAB, result);
    }

    @Test
    public void shouldReturnDefaultForRootWithoutDeclaration() {
        SceneNode root = new SceneNode();
        // 根节点无声明
        SceneCursor result = SceneCursorResolver.resolve(root);
        Assert.assertEquals("根节点无声明应返回 DEFAULT", SceneCursor.DEFAULT, result);
    }

    // ==================== 各枚举值映射 ====================

    @Test
    public void shouldResolveAllCursorValues() {
        SceneNode node = new SceneNode();
        for (SceneCursor cursor : SceneCursor.values()) {
            node.setCursor(cursor);
            Assert.assertEquals(cursor, SceneCursorResolver.resolve(node));
        }
    }

    // ==================== 中间祖先 null 覆盖 ====================

    @Test
    public void shouldSkipNullDeclarationAndContinueUpward() {
        SceneNode root = new SceneNode();
        SceneNode mid = new SceneNode();
        SceneNode leaf = new SceneNode();
        root.appendChild(mid);
        mid.appendChild(leaf);

        // 仅 root 声明，mid 和 leaf 均 null
        root.setCursor(SceneCursor.HELP);

        SceneCursor result = SceneCursorResolver.resolve(leaf);
        Assert.assertEquals("跨过 null 声明的 mid 从 root 继承 HELP", SceneCursor.HELP, result);
    }
}
