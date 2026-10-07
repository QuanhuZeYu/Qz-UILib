package club.heiqi.uilib.ui.scene.paint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
public final class PaintPlan {
    private final List<PaintCommand> commands;
    PaintPlan(List<PaintCommand> commands) { this.commands = Collections.unmodifiableList(new ArrayList<>(commands)); }
    public List<PaintCommand> commands() { return commands; }
}
