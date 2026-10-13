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

        public Entry(SceneNode node, LayoutBox box, LayoutBox clip, TextLayout text) {
            this(node, box, clip, text, 0, 0);
        }

        public Entry(
                SceneNode node,
                LayoutBox box,
                LayoutBox clip,
                TextLayout text,
                int scrollOffset,
                int scrollMaximum) {
            this.node = node;
            this.box = box;
            this.clip = clip;
            this.text = text;
            this.scrollOffset = scrollOffset;
            this.scrollMaximum = scrollMaximum;
        }
    }

    /** 同代宿主可用区域；被动呈现以此限界，不读取平台或布局后回写。 */
    default LayoutBox viewport() {
        int width = 0, height = 0;
        for (Entry entry : ordered())
            if (entry.node.parent() == null) {
                width = Math.max(width, entry.box.x + entry.box.width);
                height = Math.max(height, entry.box.y + entry.box.height);
            }
        return new LayoutBox(0, 0, width, height);
    }

    /** 同批呈现子树片段；每组独立闭合 paintAfterChildren 栈。 */
    final class Group {
        public final SceneNode root, layer, scope;
        public final club.heiqi.uilib.ui.scene.control.SceneLayer.Policy policy;
        public final List<Entry> entries;
        public Group(SceneNode root, SceneNode layer, SceneNode scope,
                club.heiqi.uilib.ui.scene.control.SceneLayer.Policy policy, List<Entry> entries) {
            this.root = root; this.layer = layer; this.scope = scope; this.policy = policy;
            this.entries = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(entries));
        }
    }
    default List<Group> groups() {
        return java.util.Collections.singletonList(new Group(null, null, null, null, ordered()));
    }
    /** 空值表示普通呈现路径；结果与 ordered 同代。 */
    default SceneNode layerBoundary(SceneNode node) { return null; }
    default SceneNode logicalScope(SceneNode node) { return null; }
    default club.heiqi.uilib.ui.scene.control.SceneLayer.Policy layerPolicy(SceneNode node) { return null; }
    default boolean layerSourceAvailable(SceneNode layer) { return false; }
    default SceneNode activeModal() { return null; }

    Entry get(SceneNode node);

    List<Entry> ordered();

    /** 同批原排列顺序，仅用于完整内容捕获；条目与 get 同一，输入/主绘制层序仍消费 ordered/groups。 */
    default List<Entry> sourceOrdered() { return ordered(); }
}
