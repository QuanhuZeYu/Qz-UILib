package club.heiqi.uilib.ui.scene.host;
import java.util.Objects;

/** 唯一原生消费者递交ESC历史事件；一段按压只发一次关闭意图，repeat不逐层关页。
 * 失焦/开屏持ESC等待历史耗尽及物理释放后恢复；不持有输入范围或页面权威。 */
public final class SceneEscapeInputAdapter {
    private final Runnable sink;
    private boolean available, suspended = true, held;
    public SceneEscapeInputAdapter(Runnable sink) { this.sink = Objects.requireNonNull(sink,"sink"); }
    public void availability(boolean active) { available = active; if (!active) { suspended = true; held = false; } }
    public void key(boolean down, boolean repeat) {
        if (!down) { held = false; return; }
        if (!available || suspended || held || repeat) return;
        held = true; sink.run();
    }
    public void historyDrained(boolean down) { if (available && !down) { suspended = false; held = false; } }
}
