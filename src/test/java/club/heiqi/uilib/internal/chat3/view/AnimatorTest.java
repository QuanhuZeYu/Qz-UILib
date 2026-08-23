package club.heiqi.uilib.internal.chat3.view;

import org.junit.Assert;
import org.junit.Test;

/**
 * Animator 契约测试:夹取与缓动端点/中点。
 */
public class AnimatorTest {

    @Test
    public void shouldClamp() {
        Assert.assertEquals(0.0F, Animator.clamp01(-1.0F), 0.0001F);
        Assert.assertEquals(1.0F, Animator.clamp01(2.0F), 0.0001F);
        Assert.assertEquals(0.5F, Animator.clamp01(0.5F), 0.0001F);
    }

    @Test
    public void shouldInterpolateLinear() {
        Assert.assertEquals(0.0F, Animator.linear(0.0F), 0.0001F);
        Assert.assertEquals(0.5F, Animator.linear(0.5F), 0.0001F);
        Assert.assertEquals(1.0F, Animator.linear(1.0F), 0.0001F);
        Assert.assertEquals(1.0F, Animator.linear(1.5F), 0.0001F);
    }

    @Test
    public void shouldInterpolateEaseOut() {
        Assert.assertEquals(0.0F, Animator.easeOut(0.0F), 0.0001F);
        Assert.assertEquals(0.75F, Animator.easeOut(0.5F), 0.0001F);
        Assert.assertEquals(1.0F, Animator.easeOut(1.0F), 0.0001F);
    }
}
