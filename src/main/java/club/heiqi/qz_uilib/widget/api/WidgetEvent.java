package club.heiqi.qz_uilib.widget.api;

import java.util.Set;

public interface WidgetEvent {
    void onHover(float x, float y);
    void onLeave(float x, float y);
    void onPress(float x, float y, int buttonID);
    void onPressNotInBounds(float x, float y, int buttonID);
    void onRelease(float x, float y, int buttonID);
    void onDragged(float newX, float newY, Set<Integer> clicked);
    void onMouseMoving(float x, float y, Set<Integer> clicked, Set<Integer> hold);
    void onWheel(float x, float y, int dWheel);

    void onType(char typeChar, int code);

    void onResize(float width, float height);
}
