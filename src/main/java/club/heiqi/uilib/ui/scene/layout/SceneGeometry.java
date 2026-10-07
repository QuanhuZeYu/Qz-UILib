package club.heiqi.uilib.ui.scene.layout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import club.heiqi.uilib.ui.scene.node.SceneNode;
/** 已发布的几何条目不会被下次求解修改。 */
public final class SceneGeometry implements SceneGeometryView {
    private final List<Entry> entries;
    private final Map<SceneNode, Entry> index;
    public SceneGeometry(List<Entry> entries) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        Map<SceneNode, Entry> map = new IdentityHashMap<>();
        for (Entry entry : entries) map.put(entry.node, entry);
        index = Collections.unmodifiableMap(map);
    }
    @Override public Entry get(SceneNode node) { return index.get(node); }
    @Override public List<Entry> ordered() { return entries; }
}
