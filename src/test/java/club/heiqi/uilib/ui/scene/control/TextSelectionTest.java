package club.heiqi.uilib.ui.scene.control;

import org.junit.Assert;
import org.junit.Test;

/**
 * TextSelection 选区模型单元测试。
 *
 * <p>覆盖：折叠/激活判定、归一化区间、焦点移动（拖选语义）、非负校验。
 * 索引语义均为 Unicode 码点。</p>
 */
public class TextSelectionTest {

    @Test
    public void collapsed_isInactiveWithSameAnchorFocus() {
        TextSelection sel = TextSelection.collapsed(3);
        Assert.assertFalse("折叠选区不应激活", sel.isActive());
        Assert.assertEquals(3, sel.anchorCp());
        Assert.assertEquals(3, sel.focusCp());
        Assert.assertEquals(3, sel.startCp());
        Assert.assertEquals(3, sel.endCp());
    }

    @Test
    public void active_selectionHasNonEqualAnchorFocus() {
        TextSelection sel = TextSelection.of(2, 7);
        Assert.assertTrue("非重合选区应激活", sel.isActive());
        Assert.assertEquals(2, sel.startCp());
        Assert.assertEquals(7, sel.endCp());
    }

    @Test
    public void normalized_handlesReverseSelection() {
        // 向后拖选（focus < anchor）归一化区间不变
        TextSelection sel = TextSelection.of(9, 4);
        Assert.assertEquals(4, sel.startCp());
        Assert.assertEquals(9, sel.endCp());
    }

    @Test
    public void withFocus_movesFocusOnly() {
        TextSelection sel = TextSelection.of(2, 5).withFocus(8);
        Assert.assertEquals(2, sel.anchorCp());
        Assert.assertEquals(8, sel.focusCp());
        Assert.assertTrue(sel.isActive());
    }

    @Test
    public void withFocus_canCollapseBackToAnchor() {
        TextSelection sel = TextSelection.of(2, 5).withFocus(2);
        Assert.assertFalse("focus 回到 anchor 应折叠", sel.isActive());
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeAnchor_rejected() {
        TextSelection.of(-1, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeFocus_rejected() {
        TextSelection.of(0, -1);
    }
}
