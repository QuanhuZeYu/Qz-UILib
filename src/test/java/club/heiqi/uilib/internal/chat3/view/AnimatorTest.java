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

    @Test
    public void shouldInterpolateEaseInQuad() {
        Assert.assertEquals("慢启动端点", 0.0F, Animator.easeInQuad(0.0F), 0.0001F);
        Assert.assertEquals("慢启动中点 p²", 0.25F, Animator.easeInQuad(0.5F), 0.0001F);
        Assert.assertEquals("收尾端点", 1.0F, Animator.easeInQuad(1.0F), 0.0001F);
        Assert.assertEquals("越界夹取", 1.0F, Animator.easeInQuad(1.5F), 0.0001F);
    }

    @Test
    public void shouldInterpolateEaseOutCubic() {
        Assert.assertEquals("端点", 0.0F, Animator.easeOutCubic(0.0F), 0.0001F);
        Assert.assertEquals("中点 1-(1-0.5)³", 0.875F, Animator.easeOutCubic(0.5F), 0.0001F);
        Assert.assertEquals("端点半程 1-(0.25)³ 前段快", 0.984375F, Animator.easeOutCubic(0.75F), 0.0001F);
        Assert.assertEquals("收尾端点", 1.0F, Animator.easeOutCubic(1.0F), 0.0001F);
        Assert.assertEquals("越界夹取", 1.0F, Animator.easeOutCubic(1.5F), 0.0001F);
        Assert.assertEquals("负向夹取", 0.0F, Animator.easeOutCubic(-1.0F), 0.0001F);
    }

    @Test
    public void shouldInterpolateEaseOutBack() {
        // 端点:p=0 → 0、p=1 → 1(公式 1+(c+1)(p-1)³+c(p-1)² 两端精确)
        Assert.assertEquals("起点", 0.0F, Animator.easeOutBack(0.0F), 0.0001F);
        Assert.assertEquals("终点", 1.0F, Animator.easeOutBack(1.0F), 0.0001F);
        Assert.assertEquals("越界夹取", 1.0F, Animator.easeOutBack(1.5F), 0.0001F);
        Assert.assertEquals("负向夹取", 0.0F, Animator.easeOutBack(-1.0F), 0.0001F);
        // 中点 c=1.04:1+2.04×(−0.125)+1.04×0.25 = 1−0.255+0.26 = 1.005(已 overshoot,>1)
        Assert.assertEquals("中点 overshoot 1.005", 1.005F, Animator.easeOutBack(0.5F), 0.0001F);
        Assert.assertEquals("默认 c=1.04 与显式一致",
                Animator.easeOutBack(0.5F), Animator.easeOutBack(0.5F, 1.04F), 0.0001F);
        // 峰值:解析解 p*=1−2c/(3(c+1)), f_max=1+4c³/(27(c+1)²);c=1.04 → ≈1.0406(≈4.1% overshoot,
        // 编排裁决 2026-08-24:原稿标注 c=1.4 与「约 4%」矛盾,c=1.4 真实峰值 ≈7.1%,取 1.04 贴合克制意图)
        float pPeak = 1.0F - 2.0F * 1.04F / (3.0F * (1.04F + 1.0F));
        Assert.assertEquals("overshoot 峰值 1.0406", 1.0406F, Animator.easeOutBack(pPeak, 1.04F), 0.001F);
        Assert.assertTrue("overshoot 峰值 >1", Animator.easeOutBack(pPeak, 1.04F) > 1.0F);
    }
}
