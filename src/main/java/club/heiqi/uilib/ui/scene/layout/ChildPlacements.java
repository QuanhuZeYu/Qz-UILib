package club.heiqi.uilib.ui.scene.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 控件排列端口返回相对区域，结果由框架保存并转换为统一几何。 */
public final class ChildPlacements {
    public static final class Entry {
        public final SceneNode child;
        public final LayoutBox relativeBox;

        public Entry(SceneNode child, LayoutBox relativeBox) {
            this.child = child;
            this.relativeBox = relativeBox;
        }
    }

    private final List<Entry> entries;

    public ChildPlacements(List<Entry> entries) {
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public List<Entry> entries() {
        return entries;
    }
}
