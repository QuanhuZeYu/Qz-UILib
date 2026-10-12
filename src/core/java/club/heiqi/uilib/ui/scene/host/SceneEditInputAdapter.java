package club.heiqi.uilib.ui.scene.host;

import java.util.HashSet;
import java.util.Set;

/** 编辑键允许真实repeat；开屏/失焦后的旧按压尾部须实际释放后才重新进入。
 * 宿主按历史顺序维护修饰键；物理快照只在历史耗尽后确认释放。 */
public final class SceneEditInputAdapter {
    private final Set<Integer> held = new HashSet<>(), blocked = new HashSet<>();
    private boolean available = true, ready;

    public void availability(boolean value) {
        if (available && !value) {
            blocked.addAll(held);
            ready = false;
        }
        available = value;
    }

    public boolean key(int key, boolean down, boolean repeat) {
        if (!down) {
            held.remove(key);
            blocked.remove(key);
            return false;
        }
        held.add(key);
        if (!available || !ready) {
            blocked.add(key);
            return false;
        }
        return !blocked.contains(key);
    }

    public void historyDrained(Set<Integer> physical) {
        blocked.retainAll(physical);
        held.retainAll(physical);
        if (available) {
            if (!ready) blocked.addAll(physical);
            ready = true;
        }
    }

    public boolean available() {
        return available && ready && blocked.isEmpty();
    }
}
