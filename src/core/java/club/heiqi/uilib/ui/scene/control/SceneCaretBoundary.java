package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.edit.SceneEditState.Snapshot;
import club.heiqi.uilib.ui.scene.text.TextLayout;

/**
 * 编辑呈现的簇边归属（字体 spec 5.1）：只记来源和方向，不记绝对 x。
 * 命中后保留所选 leading/trailing；重排或滚动后从当前共同 Line/Cluster 重新取位置。
 * 草稿与组字投影换代时旧来源不再适用，逻辑选区仍全部归 SceneEditState。
 */
final class SceneCaretBoundary {
    final int position;
    private final int start, end;
    private final boolean leading, rtl;

    private SceneCaretBoundary(int position, int start, int end, boolean leading, boolean rtl) {
        this.position = position;
        this.start = start;
        this.end = end;
        this.leading = leading;
        this.rtl = rtl;
    }

    private static SceneCaretBoundary of(TextLayout.Cluster cluster, boolean leading) {
        return new SceneCaretBoundary(leading ? cluster.sourceStart : cluster.sourceEnd,
                cluster.sourceStart, cluster.sourceEnd, leading, cluster.rtl);
    }

    static boolean sameProjection(Snapshot before, Snapshot next) {
        return before == null || before.revision == next.revision
                && before.composing == next.composing
                && before.composition.equals(next.composition)
                && (!next.composing || before.anchor == next.anchor && before.caret == next.caret);
    }

    private boolean matches(TextLayout.Cluster cluster) {
        return cluster.sourceStart == start && cluster.sourceEnd == end && cluster.rtl == rtl;
    }

    int lineAt(TextLayout text) {
        for (int i = 0; i < text.lines().size(); i++)
            for (TextLayout.Cluster c : text.lines().get(i).clusters())
                if (matches(c)) return i;
        return -1;
    }

    static float caretX(TextLayout.Line line, int position, SceneCaretBoundary boundary) {
        if (boundary != null && boundary.position == position)
            for (TextLayout.Cluster c : line.clusters())
                if (boundary.matches(c)) return boundary.leading ? c.leadingX() : c.trailingX();
        // 未选视觉边时保留原逻辑导航政策，不在本修复改变左右键行为。
        float best = line.x;
        int distance = Integer.MAX_VALUE;
        for (TextLayout.Cluster c : line.clusters()) {
            if (c.sourceStart == position) return c.leadingX();
            int delta = Math.abs(position - c.sourceStart);
            if (delta < distance) { distance = delta; best = c.leadingX(); }
            delta = Math.abs(position - c.sourceEnd);
            if (delta < distance) { distance = delta; best = c.trailingX(); }
        }
        return best;
    }

    static SceneCaretBoundary nearest(TextLayout.Line line, float x) {
        SceneCaretBoundary best = new SceneCaretBoundary(line.sourceStart, -1, -1, true, false);
        float distance = Float.POSITIVE_INFINITY;
        for (TextLayout.Cluster c : line.clusters()) {
            float delta = Math.abs(c.leadingX() - x);
            if (delta < distance) { distance = delta; best = of(c, true); }
            delta = Math.abs(c.trailingX() - x);
            if (delta < distance) { distance = delta; best = of(c, false); }
        }
        return best;
    }
}
