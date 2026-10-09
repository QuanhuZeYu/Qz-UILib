package club.heiqi.uilib.ui.scene.layout;
import java.util.List;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.text.TextLayout;
public interface SceneGeometryView {
    final class Entry {
        public final SceneNode node;
        public final LayoutBox box, clip;
        public final TextLayout text;
        public final int scrollOffset, scrollMaximum;
        public Entry(SceneNode node, LayoutBox box, LayoutBox clip, TextLayout text) { this(node, box, clip, text, 0, 0); }
        public Entry(SceneNode node, LayoutBox box, LayoutBox clip, TextLayout text, int scrollOffset, int scrollMaximum) {
            this.node = node; this.box = box; this.clip = clip; this.text = text;
            this.scrollOffset = scrollOffset; this.scrollMaximum = scrollMaximum;
        }
    }
    /** 同代宿主可用区域；被动呈现以此限界，不读取平台或布局后回写。 */
    default LayoutBox viewport() {
        int width = 0, height = 0;
        for (Entry entry : ordered()) if (entry.node.parent() == null) {
            width = Math.max(width, entry.box.x + entry.box.width); height = Math.max(height, entry.box.y + entry.box.height);
        }
        return new LayoutBox(0, 0, width, height);
    }
    Entry get(SceneNode node);
    List<Entry> ordered();
}
