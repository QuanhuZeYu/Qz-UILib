package club.heiqi.uilib.ui.hud.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** provider 每帧返回的不可变 HUD 内容快照。 */
public final class HudSnapshot {
    public static final HudSnapshot EMPTY = new HudSnapshot(Collections.<HudLine>emptyList());
    private final List<HudLine> lines;

    private HudSnapshot(List<HudLine> lines) {
        ArrayList<HudLine> copy = new ArrayList<HudLine>(lines);
        Set<String> ids = new HashSet<String>();
        for (HudLine line : copy) {
            if (line == null) throw new NullPointerException("line");
            if (!ids.add(line.getId())) throw new IllegalArgumentException("duplicate line id: " + line.getId());
        }
        this.lines = Collections.unmodifiableList(copy);
    }

    /** 从行列表防御性复制快照。 */
    public static HudSnapshot of(List<HudLine> lines) { return new HudSnapshot(lines); }
    /** 从若干行创建快照。 */
    public static HudSnapshot of(HudLine... lines) {
        ArrayList<HudLine> values = new ArrayList<HudLine>();
        Collections.addAll(values, lines);
        return new HudSnapshot(values);
    }
    public List<HudLine> getLines() { return lines; }
    public boolean isEmpty() { return lines.isEmpty(); }
}
