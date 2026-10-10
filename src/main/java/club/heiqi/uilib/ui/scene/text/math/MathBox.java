package club.heiqi.uilib.ui.scene.text.math;

import java.util.ArrayList;
import java.util.List;

/** 临时数学盒只保存基线坐标与有序绘制事实，不成为第二棵布局树。 */
final class MathBox {
    enum Atom {
        ORD,
        OP,
        BIN,
        REL,
        OPEN,
        CLOSE,
        PUNCT,
        INNER,
        NONE
    }

    float width, ascent, descent, italic;
    Atom atom = Atom.ORD;
    final List<MathLayout.Entry> entries = new ArrayList<MathLayout.Entry>();

    void entry(MathLayout.Entry entry) {
        entries.add(entry);
    }

    void piece(MathLayout.TextPiece piece) {
        entry(MathLayout.Entry.text(piece));
    }

    void embed(MathLayout.Embed embed) {
        entry(MathLayout.Entry.embed(embed));
    }

    List<MathLayout.TextPiece> pieces() {
        List<MathLayout.TextPiece> result = new ArrayList<MathLayout.TextPiece>();
        for (MathLayout.Entry entry : entries) if (entry.piece != null) result.add(entry.piece);
        return result;
    }

    void add(MathBox child, float x, float y) {
        ascent = Math.max(ascent, child.ascent - y);
        descent = Math.max(descent, child.descent + y);
        for (MathLayout.Entry entry : child.entries) entries.add(entry.translated(x, y));
    }

    void append(MathBox child, float x, float y) {
        add(child, x, y);
    }

    void rule(float x, float y, float width, float height, int color, int start, int end) {
        entry(MathLayout.Entry.fill(new MathLayout.Rule(x, y, width, height, color, start, end)));
        ascent = Math.max(ascent, -y);
        descent = Math.max(descent, y + height);
    }

    void stroke(
            float x1, float y1, float x2, float y2, float width, int color, int start, int end) {
        entry(
                MathLayout.Entry.stroke(
                        new MathLayout.Stroke(x1, y1, x2, y2, width, color, start, end)));
        ascent = Math.max(ascent, -Math.min(y1, y2) + width / 2);
        descent = Math.max(descent, Math.max(y1, y2) + width / 2);
    }

    MathBox shifted(float x, float y, float width) {
        MathBox result = new MathBox();
        result.add(this, x, y);
        result.width = width;
        result.atom = atom;
        result.italic = italic;
        return result;
    }

    MathLayout finish(List<MathLayout.Diagnostic> diagnostics) {
        return finish(diagnostics, 1);
    }

    MathLayout finish(List<MathLayout.Diagnostic> diagnostics, int nextEquationNumber) {
        List<MathLayout.Entry> result = new ArrayList<MathLayout.Entry>();
        for (MathLayout.Entry entry : entries) result.add(entry.translated(0, ascent));
        return new MathLayout(
                Math.max(0, width),
                ascent + descent,
                ascent,
                result,
                diagnostics,
                false,
                null,
                nextEquationNumber);
    }
}
