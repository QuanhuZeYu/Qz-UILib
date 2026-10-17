package club.heiqi.uilib.ui.scene.runtime;

import club.heiqi.uilib.ui.scene.paint.PaintPlan;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 框架捕获的被动片段，显式保留所属层逃离来源裁剪的事实；不持输入或重新激活层。 */
public final class PresencePaint {
    public static final class Part {
        public final PaintPlan paint;
        public final boolean viewportClipped;
        public Part(PaintPlan paint, boolean viewportClipped) { this.paint = paint; this.viewportClipped = viewportClipped; }
    }
    public final List<Part> parts;
    public PresencePaint(List<Part> parts) { this.parts = Collections.unmodifiableList(new ArrayList<>(parts)); }
}
