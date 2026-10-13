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
    private final LayoutBox viewport;

    public SceneGeometry(List<Entry> entries) {
        this(entries, null);
    }

    public SceneGeometry(List<Entry> entries, LayoutBox viewport) {
        this.viewport = viewport;
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        Map<SceneNode, Entry> map = new IdentityHashMap<>();
        for (Entry entry : entries) map.put(entry.node, entry);
        index = Collections.unmodifiableMap(map);
    }

    @Override
    public LayoutBox viewport() {
        return viewport == null ? SceneGeometryView.super.viewport() : viewport;
    }

    @Override
    public Entry get(SceneNode node) {
        return index.get(node);
    }

    /** 导航显露的纯几何运算，不持有滚动状态、不执行布局或交互命令。 */
    private static LayoutBox innerBox(SceneGeometryView.Entry entry) {
        int padding = entry.node.padding();
        return new LayoutBox(
                entry.box.x + padding,
                entry.box.y + padding,
                (int) Math.max(0L, (long) entry.box.width - 2L * padding),
                (int) Math.max(0L, (long) entry.box.height - 2L * padding));
    }

    public static LayoutBox revealRegion(
            SceneNode target, SceneNode ancestor, SceneGeometryView geometry) {
        SceneGeometryView.Entry entry = geometry.get(target);
        if (entry == null) return null;
        LayoutBox region = entry.box;
        LayoutBox window = revealWindow(ancestor, geometry);
        // 能放下的轴仍显露整盒；过大的编辑目标优先使用本次内部位置。
        if (target.control() != null
                && (region.width > window.width || region.height > window.height)) {
            LayoutBox focus = target.control().navigationRegion(target, geometry);
            if (focus != null) {
                boolean oversizedX = region.width > window.width;
                boolean oversizedY = region.height > window.height;
                region = new LayoutBox(
                        oversizedX ? focus.x : region.x,
                        oversizedY ? focus.y : region.y,
                        oversizedX ? focus.width : region.width,
                        oversizedY ? focus.height : region.height);
            }
        }
        // 下层裁剪随当前祖先的内容一起移动，外层只能显露其中尚可见的部分。
        for (SceneNode node = target.parent(); node != ancestor; node = node.parent()) {
            if (node == null) return null;
            SceneGeometryView.Entry clip = geometry.get(node);
            if (clip == null) return null;
            if (node.scrollable()) region = region.intersect(innerBox(clip));
            else if (node.clip()) region = region.intersect(clip.box);
        }
        return region;
    }

    public static LayoutBox revealWindow(SceneNode ancestor, SceneGeometryView geometry) {
        LayoutBox window = innerBox(geometry.get(ancestor));
        boolean movableX = false, movableY = false;
        for (SceneNode node = ancestor.parent(); node != null; node = node.parent()) {
            // 外层只能修复自身滚动轴的遮挡；正交轴仍需约束内层窗口。
            // 某轴可由外层移动后，其自身及更上层当前clip不提前卡住屏外内层。
            if (node.scrollable()) {
                if (node.axis() == SceneNode.Axis.ROW) movableX = true;
                else movableY = true;
            }
            SceneGeometryView.Entry entry = geometry.get(node);
            if (entry != null && (node.clip() || node.scrollable()))
                window =
                        limitWindow(
                                window,
                                node.scrollable() ? innerBox(entry) : entry.box,
                                !movableX,
                                !movableY);
        }
        return limitWindow(window, geometry.viewport(), !movableX, !movableY);
    }

    private static LayoutBox limitWindow(LayoutBox window, LayoutBox clip, boolean x, boolean y) {
        int left = x ? Math.max(window.x, clip.x) : window.x;
        int top = y ? Math.max(window.y, clip.y) : window.y;
        long right =
                x
                        ? Math.min((long) window.x + window.width, (long) clip.x + clip.width)
                        : (long) window.x + window.width;
        long bottom =
                y
                        ? Math.min((long) window.y + window.height, (long) clip.y + clip.height)
                        : (long) window.y + window.height;
        return new LayoutBox(
                left, top, (int) Math.max(0L, right - left), (int) Math.max(0L, bottom - top));
    }

    public static long revealDelta(long start, long size, long windowStart, long windowSize) {
        long end = start + size, windowEnd = windowStart + windowSize;
        // 大目标没有内部焦点位置时显露起始边缘；已经跨越整个视口则保持，
        // 避免反复导航在两端跳动。普通目标仅补足被遮住的一侧。
        if (size > windowSize)
            return start <= windowStart && end >= windowEnd ? 0 : start - windowStart;
        if (start < windowStart) return start - windowStart;
        return end > windowEnd ? end - windowEnd : 0;
    }

    @Override
    public List<Entry> ordered() {
        return entries;
    }
}
