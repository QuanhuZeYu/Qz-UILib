package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.text.TextLayout;
import club.heiqi.uilib.ui.scene.text.TextPolicy;

import java.util.List;

/** 多行的纯编辑几何；坐标只来自生产 Line/Cluster，方向使用同段落、同视觉行的 ICU 级别。 */
final class SceneTextAreaGeometry {
    final TextLayout text;

    SceneTextAreaGeometry(TextLayout text, TextPolicy policy) {
        this.text = text;
    }

    /** 相同定位可由无缓存/带装饰的测量器返回新对象；对象身份变化本身不是重新排版。 */
    boolean sameGeometry(TextLayout other) {
        if (text == other) return true;
        if (text.generation != other.generation
                || text.width != other.width
                || text.height != other.height
                || !text.logicalText.equals(other.logicalText)
                || !java.util.Objects.equals(text.originalText, other.originalText)
                || !text.style.sameFont(other.style)
                || text.lines().size() != other.lines().size()) return false;
        for (int i = 0; i < text.lines().size(); i++) {
            TextLayout.Line a = text.lines().get(i), b = other.lines().get(i);
            if (a.x != b.x
                    || a.y != b.y
                    || a.logicalStart != b.logicalStart
                    || a.logicalEnd != b.logicalEnd
                    || a.sourceStart != b.sourceStart
                    || a.sourceEnd != b.sourceEnd
                    || a.advance != b.advance
                    || a.baseline != b.baseline
                    || a.ascent != b.ascent
                    || a.descent != b.descent
                    || a.lineHeight != b.lineHeight
                    || a.paragraphRtl != b.paragraphRtl
                    || a.clusters().size() != b.clusters().size()) return false;
            for (int j = 0; j < a.clusters().size(); j++) {
                TextLayout.Cluster c = a.clusters().get(j), d = b.clusters().get(j);
                if (c.logicalStart != d.logicalStart
                        || c.logicalEnd != d.logicalEnd
                        || c.sourceStart != d.sourceStart
                        || c.sourceEnd != d.sourceEnd
                        || c.x != d.x
                        || c.advance != d.advance
                        || c.rtl != d.rtl) return false;
            }
        }
        return true;
    }

    int lineAt(int position, int affinity) {
        List<TextLayout.Line> lines = text.lines();
        if (affinity >= 0 && affinity < lines.size()) {
            TextLayout.Line line = lines.get(affinity);
            if (position >= line.sourceStart && position <= line.sourceEnd) return affinity;
        }
        // 无呈现归属时，软边界固定归后行；硬换行前仍归前行。
        int nearest = -1, distance = Integer.MAX_VALUE;
        for (int i = 0; i < lines.size(); i++) {
            TextLayout.Line line = lines.get(i);
            if (position == line.sourceStart) return i;
            if (position > line.sourceStart && position < line.sourceEnd) return i;
            int delta =
                    position < line.sourceStart
                            ? line.sourceStart - position
                            : Math.max(0, position - line.sourceEnd);
            if (delta < distance) {
                nearest = i;
                distance = delta;
            }
        }
        return nearest;
    }

    int lineAtY(float y) {
        int best = -1;
        float distance = Float.POSITIVE_INFINITY;
        for (int i = 0; i < text.lines().size(); i++) {
            TextLayout.Line line = text.lines().get(i);
            float delta = y < line.y ? line.y - y : Math.max(0, y - line.y - line.lineHeight);
            if (delta < distance) {
                best = i;
                distance = delta;
            }
        }
        return best;
    }

    float edge(int index, TextLayout.Cluster cluster, boolean start) {
        return start ? cluster.leadingX() : cluster.trailingX();
    }

    float caretX(int index, int position) {
        return caretX(index, position, null);
    }

    float caretX(int index, int position, SceneCaretBoundary boundary) {
        return SceneCaretBoundary.caretX(text.lines().get(index), position, boundary);
    }

    SceneCaretBoundary nearest(int index, float x) {
        return SceneCaretBoundary.nearest(text.lines().get(index), x);
    }

    SceneCaretBoundary visualEnd(int index, boolean right) {
        TextLayout.Line line = text.lines().get(index);
        float x = line.x;
        boolean first = true;
        for (TextLayout.Cluster cluster : line.clusters()) {
            float edge = right ? Math.max(cluster.leadingX(), cluster.trailingX())
                    : Math.min(cluster.leadingX(), cluster.trailingX());
            if (first || (right ? edge > x : edge < x)) x = edge;
            first = false;
        }
        return nearest(index, x);
    }

    boolean paragraphRtl(int index) {
        return text.lines().get(index).paragraphRtl;
    }

    /** 统一平移所有绝对位置；advance、墨迹相对边界和源映射保持原生产事实。 */
    static TextLayout translate(TextLayout full, int x, int y) {
        return SceneTextPresentation.translate(full, -x, -y);
    }
}
