package club.heiqi.uilib.ui.control;

import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.render.UiRenderContext;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 鼠标事件高频响应测试控件。
 */
public class MouseStressWidget extends Widget {

    private long moveEventCount;
    private long buttonDownCount;
    private long buttonUpCount;
    private long scrollEventCount;
    private long moveEventsInWindow;
    private long moveWindowStartNanos;
    private long peakMoveEventsPerSecond;
    private long lastEventNanos;

    @Override
    protected void drawSelf(UiRenderContext context) {
        int absoluteX = getAbsoluteX();
        int absoluteY = getAbsoluteY();
        int lineHeight = Math.max(18, context.getTextLineHeight() - 2);
        context.fillRect(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), 0xCC161B23);
        context.drawBorder(absoluteX, absoluteY, absoluteX + getWidth(), absoluteY + getHeight(), 0xFF7AA2FF);

        long now = System.nanoTime();
        float idleMs = lastEventNanos == 0L ? -1.0F : (float) ((now - lastEventNanos) / 1_000_000.0D);
        String idleText = idleMs < 0.0F ? "尚未收到鼠标事件" : String.format("距上次鼠标事件 %.2f ms", Float.valueOf(idleMs));

        context.drawText("鼠标极限响应测试区", absoluteX + 12, absoluteY + 12, 0xFFFFFFFF, true);
        context.drawText("移动事件总数: " + moveEventCount, absoluteX + 12, absoluteY + 12 + lineHeight, 0xFFD7E3FF, false);
        context.drawText("按钮按下/抬起: " + buttonDownCount + " / " + buttonUpCount, absoluteX + 12, absoluteY + 12 + lineHeight * 2,
                0xFFD7E3FF, false);
        context.drawText("滚轮事件总数: " + scrollEventCount, absoluteX + 12, absoluteY + 12 + lineHeight * 3, 0xFFD7E3FF, false);
        context.drawText("峰值移动事件速率: " + peakMoveEventsPerSecond + " 次/秒", absoluteX + 12, absoluteY + 12 + lineHeight * 4,
                0xFFF6D78E, false);
        context.drawText(idleText, absoluteX + 12, absoluteY + 12 + lineHeight * 5, 0xFFC8D8F3, false);
        context.drawText("在此区域内快速甩动、连点、滚轮滚动，观察计数是否持续刷新", absoluteX + 12, absoluteY + 12 + lineHeight * 6,
                0xFFB8C2D4, false);

        if (contains(context.getMouseX(), context.getMouseY())) {
            int markerX = clamp(context.getMouseX(), absoluteX, absoluteX + getWidth() - 6);
            int markerY = clamp(context.getMouseY(), absoluteY, absoluteY + getHeight() - 6);
            context.fillRect(markerX - 2, markerY - 2, markerX + 2, markerY + 2, 0xFFFF6B6B);
        }
    }

    @Override
    public void onMouseMove(UiMouseEvent event) {
        moveEventCount++;
        lastEventNanos = event.getTimeNanos();
        updateMoveWindow(event.getTimeNanos());
    }

    @Override
    public void onMouseDown(UiMouseEvent event) {
        buttonDownCount++;
        lastEventNanos = event.getTimeNanos();
    }

    @Override
    public void onMouseUp(UiMouseEvent event) {
        buttonUpCount++;
        lastEventNanos = event.getTimeNanos();
    }

    @Override
    public void onMouseScroll(UiMouseEvent event) {
        scrollEventCount++;
        lastEventNanos = event.getTimeNanos();
    }

    private void updateMoveWindow(long eventTimeNanos) {
        if (moveWindowStartNanos == 0L) {
            moveWindowStartNanos = eventTimeNanos;
            moveEventsInWindow = 1L;
            return;
        }

        if (eventTimeNanos - moveWindowStartNanos > 1_000_000_000L) {
            peakMoveEventsPerSecond = Math.max(peakMoveEventsPerSecond, moveEventsInWindow);
            moveWindowStartNanos = eventTimeNanos;
            moveEventsInWindow = 1L;
            return;
        }
        moveEventsInWindow++;
        peakMoveEventsPerSecond = Math.max(peakMoveEventsPerSecond, moveEventsInWindow);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public int getPreferredWidth() {
        return 320;
    }

    @Override
    public int getPreferredHeight() {
        return 300;
    }
}
