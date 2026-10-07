package club.heiqi.uilib.ui.scene.layout;
import java.util.List;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.text.TextLayout;
public interface SceneGeometryView {
    final class Entry {
        public final SceneNode node;
        public final LayoutBox box, clip;
        public final TextLayout text;
        public Entry(SceneNode node, LayoutBox box, LayoutBox clip, TextLayout text) { this.node = node; this.box = box; this.clip = clip; this.text = text; }
    }
    Entry get(SceneNode node);
    List<Entry> ordered();
}
