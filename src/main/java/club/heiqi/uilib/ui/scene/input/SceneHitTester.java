package club.heiqi.uilib.ui.scene.input;
import java.util.List;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
/** 从同代几何逆绘制序查找；父自身未命中不阻断未裁剪的子节点。 */
public final class SceneHitTester {
    public SceneNode hit(SceneRuntime runtime, SceneGeometryView geometry, int x, int y) { return hit(runtime, geometry, x, y, false); }
    public SceneNode hitScroll(SceneRuntime runtime, SceneGeometryView geometry, int x, int y) { return hit(runtime, geometry, x, y, true); }
    private SceneNode hit(SceneRuntime runtime, SceneGeometryView geometry, int x, int y, boolean scroll) {
        List<SceneGeometryView.Entry> entries = geometry.ordered();
        for (int i = entries.size() - 1; i >= 0; i--) {
            SceneGeometryView.Entry entry = entries.get(i);
            if (entry.clip != null && !entry.clip.contains(x, y)) continue;
            if (entry.box.contains(x, y) && runtime.interactive(entry.node) && (scroll ? entry.node.scrollAction() != null : entry.node.clickAction() != null)) return entry.node;
        }
        return null;
    }
}
