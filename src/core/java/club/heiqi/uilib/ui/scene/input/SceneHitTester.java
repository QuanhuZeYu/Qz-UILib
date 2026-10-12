package club.heiqi.uilib.ui.scene.input;

import java.util.List;
import club.heiqi.uilib.ui.scene.layout.SceneGeometryView;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/** 从同代几何逆绘制序查找；父自身未命中不阻断未裁剪的子节点。 */
public final class SceneHitTester {
    public SceneNode hit(SceneRuntime runtime, SceneGeometryView geometry, int x, int y) {
        return hit(runtime, geometry, x, y, false);
    }

    public SceneNode hitScroll(SceneRuntime runtime, SceneGeometryView geometry, int x, int y) {
        return hit(runtime, geometry, x, y, true);
    }

    public SceneNode passive(SceneRuntime runtime, SceneGeometryView geometry, int x, int y) {
        List<SceneGeometryView.Entry> entries = geometry.ordered();
        for (int i = entries.size() - 1; i >= 0; i--) {
            SceneGeometryView.Entry entry = entries.get(i);
            if (entry.clip != null && !entry.clip.contains(x, y)) continue;
            if (entry.box.contains(x, y)
                    && runtime.interactive(entry.node)
                    && (entry.node.clickAction() != null
                            || entry.node.control() != null && entry.node.control().passiveHover()))
                return entry.node;
        }
        return null;
    }

    public static final class Target {
        public final SceneNode node;
        public final Object identity;

        Target(SceneNode node, Object identity) {
            this.node = node;
            this.identity = identity;
        }
    }

    /** 内部目标与普通 click 共用绘制序、有效范围及裁剪。 */
    public Target target(SceneRuntime runtime, SceneGeometryView geometry, int x, int y) {
        List<SceneGeometryView.Entry> entries = geometry.ordered();
        for (int i = entries.size() - 1; i >= 0; i--) {
            SceneGeometryView.Entry entry = entries.get(i);
            if (!runtime.interactive(entry.node)
                    || entry.clip != null && !entry.clip.contains(x, y)) continue;
            Object internal =
                    entry.node.control() == null
                            ? null
                            : entry.node.control().pointerTarget(entry.node, geometry, x, y);
            if (internal != null) return new Target(entry.node, internal);
            if (entry.box.contains(x, y) && entry.node.clickAction() != null)
                return new Target(entry.node, entry.node);
        }
        return null;
    }

    private SceneNode hit(
            SceneRuntime runtime, SceneGeometryView geometry, int x, int y, boolean scroll) {
        if (!scroll) {
            Target target = target(runtime, geometry, x, y);
            return target == null ? null : target.node;
        }
        List<SceneGeometryView.Entry> entries = geometry.ordered();
        for (int i = entries.size() - 1; i >= 0; i--) {
            SceneGeometryView.Entry entry = entries.get(i);
            if (entry.clip != null && !entry.clip.contains(x, y)) continue;
            if (entry.box.contains(x, y)
                    && runtime.interactive(entry.node)
                    && (scroll
                            ? entry.node.scrollable()
                                    || entry.node.scrollAction() != null
                                    || entry.node.control() != null
                                            && entry.node.control().scrollableContent()
                            : entry.node.clickAction() != null)) return entry.node;
        }
        return null;
    }
}
