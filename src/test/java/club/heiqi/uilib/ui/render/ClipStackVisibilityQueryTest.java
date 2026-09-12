package club.heiqi.uilib.ui.render;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.base.cascade.UiBorderRadiusResolver;

/**
 * W2 可见性短路的判定基元：{@link ClipStack#intersectsCurrentClip(int, int, int, int)}。
 *
 * <p>这条判定决定玻璃表面是否整链早退，误判"不可见"会直接吃掉像素（可见表面被跳过），
 * 故逐一钉住栈空、内外、边缘相接、嵌套交集、退化裁剪盒与圆角情形。判定的两个硬性约束：
 * 只读（不改 GL 状态）、零分配（不得退回 {@code copySnapshot()} / {@code peekClipRectForTest()}）。</p>
 */
public class ClipStackVisibilityQueryTest {

    private static ClipStack newStack() {
        ClipStack stack = new ClipStack();
        stack.glOperationsEnabled = false;
        return stack;
    }

    private static void push(ClipStack stack, int left, int top, int right, int bottom) {
        stack.push(left, top, right, bottom, 320, 240, UiBorderRadiusResolver.ResolvedCornerRadii.uniform(0));
    }

    /** 栈空 = 无 UI 裁剪盒 ⇒ 一律按可见处理（宿主基线是外部事实，本判定不消费）。 */
    @Test
    public void emptyStackTreatsEverySurfaceAsVisible() {
        ClipStack stack = newStack();

        Assert.assertTrue(stack.intersectsCurrentClip(0, 0, 10, 10));
        Assert.assertTrue("栈空时不得短路任何表面", stack.intersectsCurrentClip(1000, 1000, 1100, 1100));
        Assert.assertTrue(stack.intersectsCurrentClip(-50, -50, 500, 500));
    }

    /** 栈顶裁剪盒与表面的相交关系：内/交/含为真，外/相接为假。 */
    @Test
    public void pushedClipRectIntersectsOnlyOverlappingSurfaces() {
        ClipStack stack = newStack();
        push(stack, 100, 100, 200, 200);

        Assert.assertTrue("完全在内", stack.intersectsCurrentClip(120, 120, 180, 180));
        Assert.assertTrue("与裁剪盒同框", stack.intersectsCurrentClip(100, 100, 200, 200));
        Assert.assertTrue("部分重叠", stack.intersectsCurrentClip(50, 50, 150, 150));
        Assert.assertTrue("包含裁剪盒", stack.intersectsCurrentClip(0, 0, 320, 240));

        Assert.assertFalse("完全在右外", stack.intersectsCurrentClip(200, 100, 300, 200));
        Assert.assertFalse("完全在下外", stack.intersectsCurrentClip(100, 201, 200, 300));
        Assert.assertFalse("右边缘相接 = 零宽交集", stack.intersectsCurrentClip(200, 100, 260, 200));
        Assert.assertFalse("左边缘相接 = 零宽交集", stack.intersectsCurrentClip(40, 100, 100, 200));
        Assert.assertFalse("上边缘相接 = 零高交集", stack.intersectsCurrentClip(100, 40, 200, 100));
    }

    /** 嵌套压栈后，判定用的是与父层求交后的合成裁剪盒。 */
    @Test
    public void nestedPushIntersectsWithTheCombinedBox() {
        ClipStack stack = newStack();
        push(stack, 0, 0, 200, 200);
        push(stack, 100, 0, 150, 200);

        Assert.assertFalse("父层内但在子层外", stack.intersectsCurrentClip(20, 20, 80, 80));
        Assert.assertTrue(stack.intersectsCurrentClip(110, 20, 140, 80));
    }

    /** 退化裁剪盒（零宽/零高）不含任何像素 ⇒ 全裁。 */
    @Test
    public void degenerateClipBoxHidesEverything() {
        ClipStack stack = newStack();
        push(stack, 150, 150, 150, 150);

        Assert.assertFalse("空裁剪盒", stack.intersectsCurrentClip(0, 0, 320, 240));
    }

    /** 圆角裁剪不参与矩形快判：矩形有交集即不短路，逐像素行为仍由既有圆角 mask 保证。 */
    @Test
    public void roundedClipKeepsRectOverlappingSurfacesVisible() {
        ClipStack stack = newStack();
        stack.push(100, 100, 200, 200, 320, 240,
                UiBorderRadiusResolver.ResolvedCornerRadii.uniform(16));

        Assert.assertTrue(stack.intersectsCurrentClip(100, 100, 200, 200));
        Assert.assertTrue("圆角内的可见表面不得被矩形快判误杀", stack.intersectsCurrentClip(140, 140, 160, 160));
    }

    /** pop 后回到父层盒（W2 判定必须与 push/pop 同步，不能读到已弹出层）。 */
    @Test
    public void popRestoresTheOuterClipBox() {
        ClipStack stack = newStack();
        push(stack, 0, 0, 200, 200);
        push(stack, 100, 0, 150, 200);
        stack.pop();

        Assert.assertTrue("弹出内层后，父层盒内的表面重新可见", stack.intersectsCurrentClip(20, 20, 80, 80));
    }
}
