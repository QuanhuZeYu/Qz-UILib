package club.heiqi.uilib.ui.scene.host;
import java.util.Objects;

/** 唯一原生消费者递交单键历史事件；一段按压发一次意图。
 * 失焦/开屏持键等待历史耗尽及物理释放，repeat不重发；不持有范围或页面权威。 */
public class SceneKeyPressInputAdapter {
    private final Runnable sink;
    private boolean available, suspended = true, held;
    public SceneKeyPressInputAdapter(Runnable sink) { this.sink = Objects.requireNonNull(sink,"sink"); }
    public void availability(boolean active) { available = active; if (!active) { suspended = true; held = false; } }
    public void key(boolean down, boolean repeat) {
        if (!down) { held = false; return; }
        if (!available || suspended || held || repeat) return;
        held = true; sink.run();
    }
    public void historyDrained(boolean down) { if (available && !down) { suspended = false; held = false; } }
}
