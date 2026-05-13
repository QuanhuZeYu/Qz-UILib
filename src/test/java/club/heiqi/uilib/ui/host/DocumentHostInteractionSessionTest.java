package club.heiqi.uilib.ui.host;

import java.util.Collections;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.input.UiInputFrame;
import club.heiqi.uilib.ui.event.UiKeyEvent;
import club.heiqi.uilib.ui.event.UiMouseEvent;
import club.heiqi.uilib.ui.event.UiTextInputEvent;
import club.heiqi.uilib.ui.widget.Widget;
import club.heiqi.uilib.ui.widget.ViewportWidget;

/**
 * `DocumentHostInteractionSession` 的共享交互会话契约测试。
 */
public class DocumentHostInteractionSessionTest {

    /**
     * 验证交互会话会记录最近鼠标位置并复用路由器焦点状态。
     */
    @Test
    public void shouldTrackPointerAndFocusStateFromSharedSession() {
        DocumentHostInteractionSession session = new DocumentHostInteractionSession();
        ViewportWidget root = new ViewportWidget();
        FocusableWidget child = new FocusableWidget();
        child.applyLayoutBounds(0, 0, 40, 20);
        root.applyLayoutBounds(0, 0, 100, 60);
        root.addChild(child);

        session.route("test_host", root, mouseFrame(10, 10, 0L));

        Assert.assertEquals(10, session.getLatestMouseX());
        Assert.assertEquals(10, session.getLatestMouseY());
        Assert.assertTrue(session.hasFocusedWidget());

        session.clearInteractionState();

        Assert.assertFalse(session.hasFocusedWidget());
    }

    /**
     * 验证仅记录指针时不会要求必须路由组件树。
     */
    @Test
    public void shouldRecordPointerWithoutRoutingRootWidget() {
        DocumentHostInteractionSession session = new DocumentHostInteractionSession();

        session.recordPointer(new UiInputFrame(24, 36, Collections.<UiMouseEvent>emptyList(),
                Collections.<UiKeyEvent>emptyList(), Collections.<UiTextInputEvent>emptyList()));

        Assert.assertEquals(24, session.getLatestMouseX());
        Assert.assertEquals(36, session.getLatestMouseY());
    }

    private static UiInputFrame mouseFrame(int mouseX, int mouseY, long timeNanos) {
        UiMouseEvent event = new UiMouseEvent(UiMouseEvent.Action.BUTTON_DOWN, mouseX, mouseY, 0, 0, 0, 0,
                timeNanos);
        return new UiInputFrame(mouseX, mouseY, Collections.singletonList(event),
                Collections.<UiKeyEvent>emptyList(), Collections.<UiTextInputEvent>emptyList());
    }

    /**
     * 最小可聚焦测试组件。
     */
    private static final class FocusableWidget extends Widget {

        @Override
        public boolean isFocusable() {
            return true;
        }
    }
}
