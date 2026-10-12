package club.heiqi.uilib.ui.scene.host;

import java.util.Objects;
import java.util.function.Consumer;
import club.heiqi.uilib.ui.scene.input.SceneNavigationEvent;

/** 宿主Tab适配：原生消费者依历史顺序递交修饰键与Tab，再确认队列耗尽后的物理状态。
 * 只保存可用性及物理按压段；不拥有逻辑焦点，不消费/轮询任何平台原生队列。
 * 与指针gate独立，持有鼠标按钮不会阻止键盘导航。 */
public final class SceneTabInputAdapter {
    private final Consumer<SceneNavigationEvent> sink;
    private boolean available, suspended = true, owned, leftShift, rightShift;

    public SceneTabInputAdapter(Consumer<SceneNavigationEvent> sink) {
        this.sink = Objects.requireNonNull(sink, "sink");
    }

    /** 失焦立即结束物理段；恢复仍等历史队列消费完和Tab物理释放，不接受旧repeat。 */
    public void availability(boolean active) {
        available = active;
        if (!active) {
            suspended = true;
            owned = false;
            leftShift = false;
            rightShift = false;
        }
    }

    public void shift(boolean left, boolean down) {
        if (left) leftShift = down;
        else rightShift = down;
    }

    /** UP不导航，重复DOWN只延续本适配器已接收的按压段。 */
    public void tab(boolean down, boolean repeat) {
        if (!down) {
            owned = false;
            return;
        }
        if (!available || suspended || (repeat ? !owned : owned)) return;
        owned = true;
        sink.accept(
                leftShift || rightShift
                        ? SceneNavigationEvent.PREVIOUS
                        : SceneNavigationEvent.NEXT);
    }

    /** 唯一原生消费者耗尽历史队列后调用；设备未创建时不得调用以假装释放。 */
    public void historyDrained(boolean tabDown, boolean leftShiftDown, boolean rightShiftDown) {
        if (!available) return;
        leftShift = leftShiftDown;
        rightShift = rightShiftDown;
        if (!tabDown) {
            suspended = false;
            owned = false;
        }
    }
}
