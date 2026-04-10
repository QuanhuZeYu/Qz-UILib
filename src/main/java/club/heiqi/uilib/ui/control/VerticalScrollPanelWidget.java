package club.heiqi.uilib.ui.control;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.layout.UiLayoutSpec;
import club.heiqi.uilib.ui.layout.UiLength;
import club.heiqi.uilib.ui.widget.Widget;

/**
 * 纵向滚动面板。
 */
public class VerticalScrollPanelWidget extends ResponsivePanelWidget {

    private final VerticalStackWidget content = new VerticalStackWidget();

    private int scrollOffset;
    private int maxScrollOffset;
    private int scrollStep = 36;

    public VerticalScrollPanelWidget() {
        setClampChildrenInside(false);
        setClipChildren(true);
        content.setPadding(0).setSpacing(12).setClampChildrenInside(false);
        addChild(content);
    }

    @Override
    public VerticalScrollPanelWidget setPadding(int padding) {
        super.setPadding(padding);
        return this;
    }

    @Override
    public VerticalScrollPanelWidget setPadding(int left, int top, int right, int bottom) {
        super.setPadding(left, top, right, bottom);
        return this;
    }

    public VerticalStackWidget getContent() {
        return content;
    }

    public VerticalScrollPanelWidget setScrollStep(int scrollStep) {
        this.scrollStep = Math.max(8, scrollStep);
        return this;
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public int getMaxScrollOffset() {
        return maxScrollOffset;
    }

    public int getVisibleContentHeight() {
        return Math.max(0, getHeight() - getPaddingTop() - getPaddingBottom());
    }

    public int getContentHeight() {
        return content.getHeight();
    }

    public int getContentY() {
        return content.getY();
    }

    @Override
    protected int[] getChildClipRect() {
        return new int[] {
                getAbsoluteX() + getPaddingLeft(),
                getAbsoluteY() + getPaddingTop(),
                getAbsoluteX() + getWidth() - getPaddingRight(),
                getAbsoluteY() + getHeight() - getPaddingBottom()
        };
    }

    @Override
    public void render(club.heiqi.uilib.ui.render.UiRenderContext context) {
        updateContentBounds();
        super.render(context);
    }

    @Override
    protected void drawSelf(club.heiqi.uilib.ui.render.UiRenderContext context) {
        super.drawSelf(context);
        if (maxScrollOffset <= 0) {
            return;
        }

        int absoluteX = getAbsoluteX();
        int absoluteY = getAbsoluteY();
        int trackLeft = absoluteX + getWidth() - getPaddingRight() + 2;
        int trackRight = trackLeft + 6;
        int trackTop = absoluteY + getPaddingTop();
        int trackBottom = absoluteY + getHeight() - getPaddingBottom();
        int trackHeight = Math.max(1, trackBottom - trackTop);
        int thumbHeight = Math.max(24, Math.round(trackHeight * ((float) (trackHeight) / (float) Math.max(trackHeight, content.getHeight()))));
        int thumbTop = trackTop + Math.round((trackHeight - thumbHeight) * (scrollOffset / (float) Math.max(1, maxScrollOffset)));

        context.fillRect(trackLeft, trackTop, trackRight, trackBottom, 0x552B3647);
        context.fillRect(trackLeft, thumbTop, trackRight, thumbTop + thumbHeight, 0xFF8FB3FF);
    }

    @Override
    public void onMouseScroll(UiMouseEvent event) {
        if (maxScrollOffset <= 0) {
            return;
        }
        if (event.getWheelDelta() > 0) {
            scrollOffset = Math.max(0, scrollOffset - scrollStep);
        } else if (event.getWheelDelta() < 0) {
            scrollOffset = Math.min(maxScrollOffset, scrollOffset + scrollStep);
        }
    }

    private void updateContentBounds() {
        int contentWidth = Math.max(0, getWidth() - getPaddingLeft() - getPaddingRight());
        int visibleHeight = Math.max(0, getHeight() - getPaddingTop() - getPaddingBottom());
        int contentHeight = Math.max(visibleHeight, content.getPreferredHeightForWidth(contentWidth));
        maxScrollOffset = Math.max(0, contentHeight - visibleHeight);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
        content.setBounds(getPaddingLeft(), getPaddingTop() - scrollOffset, contentWidth, contentHeight);
    }
}
