package club.heiqi.uilib.ui.scene.layout;

import club.heiqi.uilib.ui.scene.node.SceneNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** 内容定位完成后将原生条插入宿主子树末尾；唯一Entry同时供绘制和命中。 */
final class SceneScrollDecorationLayout {
    private SceneScrollDecorationLayout() {}
    static List<SceneGeometryView.Entry> project(List<SceneGeometryView.Entry> content, LayoutBox viewport) {
        SceneGeometryView facts = new SceneGeometry(content, viewport);
        List<SceneGeometryView.Entry> result = new ArrayList<>();
        ArrayDeque<SceneGeometryView.Entry> open = new ArrayDeque<>();
        for (SceneGeometryView.Entry entry : content) {
            while (!open.isEmpty() && !within(entry.node, open.peek().node)) append(open.pop(), facts, result);
            result.add(entry); open.push(entry);
        }
        while (!open.isEmpty()) append(open.pop(), facts, result);
        return result;
    }
    private static void append(SceneGeometryView.Entry host, SceneGeometryView facts, List<SceneGeometryView.Entry> entries) {
        if (host.node.runtime() == null) return;
        for (SceneNode bar : host.node.runtime().scrollDecorations(host.node)) {
            LayoutBox box = bar.control().placeScrollDecoration(bar, facts);
            LayoutBox clip = host.clip == null ? host.box : host.clip.intersect(host.box);
            entries.add(new SceneGeometryView.Entry(bar, box, clip, null));
        }
    }
    private static boolean within(SceneNode node, SceneNode ancestor) {
        for (SceneNode cursor = node; cursor != null; cursor = cursor.parent()) if (cursor == ancestor) return true;
        return false;
    }
}
