package club.heiqi.uilib.internal.chat3.view;

import org.junit.Assert;
import org.junit.Test;

/**
 * ChatScrollbar 单元测试 —— {@link ChatScrollbar#computeOpacity} 纯函数全分支覆盖
 * （T3 自动隐藏状态机核心：拖拽不隐藏 / 活跃帧满显 / 淡入 / 保显期 / 淡出 / 边界）。
 *
 * <p>固定时长常量（FADE_IN=150ms、hide=1200ms、FADE_OUT=300ms）驱动；数值依据：
 * {@code easeOut(0.5)=1-(0.5)²=0.75}、{@code easeInQuad(0.5)=0.5²=0.25 →
 * 1-0.25=0.75}（float 精确值，无累计误差）。</p>
 */
public class ChatScrollbarTest {

    private static final long FADE_IN_MS = ChatScrollbar.FADE_IN_MS;   // 150
    private static final long HIDE_MS = 1200L;                          // 与 chat3 默认一致
    private static final long FADE_OUT_MS = ChatScrollbar.FADE_OUT_MS;  // 300

    // ==================== a：dragging=true → 1.0（拖拽中永不隐藏） ====================

    @Test
    public void draggingShouldNeverHide() {
        Assert.assertEquals("拖拽中（活跃帧）opacity=1.0", 1.0f,
                ChatScrollbar.computeOpacity(1000L, 1000L, true, FADE_IN_MS, HIDE_MS, FADE_OUT_MS), 0.0f);
        Assert.assertEquals("拖拽中（静止很久）opacity=1.0", 1.0f,
                ChatScrollbar.computeOpacity(HIDE_MS + FADE_OUT_MS + 5000L, 0L, true,
                        FADE_IN_MS, HIDE_MS, FADE_OUT_MS), 0.0f);
    }

    // ==================== b：now<=lastActive → 1.0（活跃帧满显） ====================

    @Test
    public void activeFrameShouldBeFullOpacity() {
        Assert.assertEquals("now==lastActive opacity=1.0", 1.0f,
                ChatScrollbar.computeOpacity(1234L, 1234L, false, FADE_IN_MS, HIDE_MS, FADE_OUT_MS), 0.0f);
        Assert.assertEquals("now<lastActive opacity=1.0", 1.0f,
                ChatScrollbar.computeOpacity(1000L, 2000L, false, FADE_IN_MS, HIDE_MS, FADE_OUT_MS), 0.0f);
    }

    // ==================== c：淡入段半程 → easeOut(0.5)=0.75 ====================

    @Test
    public void fadeInHalfShouldBeEaseOutMidpoint() {
        // since = 75 = FADE_IN/2 → easeOut(75/150)=easeOut(0.5)=1-(0.5)²=0.75
        Assert.assertEquals("淡入一半 opacity=easeOut(0.5)=0.75", 0.75f,
                ChatScrollbar.computeOpacity(FADE_IN_MS / 2L, 0L, false, FADE_IN_MS, HIDE_MS, FADE_OUT_MS), 0.0001f);
    }

    // ==================== d：保显期 [fadeIn, hide) → 1.0 ====================

    @Test
    public void holdPeriodShouldStayFullOpacity() {
        // since=150==fadeIn（淡入段结束恰入保显）与 since=151 均满显
        Assert.assertEquals("since==fadeIn opacity=1.0", 1.0f,
                ChatScrollbar.computeOpacity(FADE_IN_MS, 0L, false, FADE_IN_MS, HIDE_MS, FADE_OUT_MS), 0.0f);
        Assert.assertEquals("since=fadeIn+1 opacity=1.0", 1.0f,
                ChatScrollbar.computeOpacity(FADE_IN_MS + 1L, 0L, false, FADE_IN_MS, HIDE_MS, FADE_OUT_MS), 0.0f);
    }

    // ==================== e：淡出中段 → 1-easeInQuad(0.5)=0.75 ====================

    @Test
    public void fadeOutHalfShouldEaseInQuadFromOne() {
        // since = 1200+150 = hide+fadeOut/2 → sinceHide=150 → 1-easeInQuad(150/300)=1-0.25=0.75
        Assert.assertEquals("淡出中段 opacity=1-easeInQuad(0.5)=0.75", 0.75f,
                ChatScrollbar.computeOpacity(HIDE_MS + FADE_OUT_MS / 2L, 0L, false,
                        FADE_IN_MS, HIDE_MS, FADE_OUT_MS), 0.0001f);
    }

    // ==================== f：since>=hide+fadeOut → 0.0（淡出完成） ====================

    @Test
    public void fadeOutCompleteShouldBeZero() {
        // since=1500==hide+fadeOut（下界）
        Assert.assertEquals("since==hide+fadeOut opacity=0.0", 0.0f,
                ChatScrollbar.computeOpacity(HIDE_MS + FADE_OUT_MS, 0L, false,
                        FADE_IN_MS, HIDE_MS, FADE_OUT_MS), 0.0f);
        // 超界
        Assert.assertEquals("超界 opacity=0.0", 0.0f,
                ChatScrollbar.computeOpacity(HIDE_MS + FADE_OUT_MS + 500L, 0L, false,
                        FADE_IN_MS, HIDE_MS, FADE_OUT_MS), 0.0f);
    }

    // ==================== g：边界 since==hide → 1.0（淡出未开始） ====================

    @Test
    public void boundarySinceEqualsHideShouldBeFullOpacity() {
        // since==hide → sinceHide=0 → 1-easeInQuad(0)=1.0（保显期结束、淡出尚未推进）
        Assert.assertEquals("since==hide opacity=1.0", 1.0f,
                ChatScrollbar.computeOpacity(HIDE_MS, 0L, false, FADE_IN_MS, HIDE_MS, FADE_OUT_MS), 0.0001f);
    }
}
