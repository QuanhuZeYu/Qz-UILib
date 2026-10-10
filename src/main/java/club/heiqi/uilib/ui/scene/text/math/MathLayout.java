package club.heiqi.uilib.ui.scene.text.math;

import club.heiqi.uilib.ui.scene.text.TextLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 不可变公式几何，所有坐标以公式局部顶部为原点，字形仍使用既有 TextLayout。 */
public final class MathLayout {
    public final float width, height, baseline;
    public final List<TextPiece> pieces;
    public final List<Rule> rules;
    public final List<Embed> embeds;
    public final List<Diagnostic> diagnostics;
    public final boolean fallback;

    /** 降级时完整原文的正常文本布局；也同时作为一个 TextPiece 发布。 */
    public final TextLayout sourceFallback;

    MathLayout(
            float width,
            float height,
            float baseline,
            List<TextPiece> pieces,
            List<Rule> rules,
            List<Embed> embeds,
            List<Diagnostic> diagnostics,
            boolean fallback,
            TextLayout sourceFallback) {
        this.width = width;
        this.height = height;
        this.baseline = baseline;
        this.pieces = immutable(pieces);
        this.rules = immutable(rules);
        this.embeds = immutable(embeds);
        this.diagnostics = immutable(diagnostics);
        this.fallback = fallback;
        this.sourceFallback = sourceFallback;
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<T>(values));
    }

    public static final class TextPiece {
        public final TextLayout text;
        public final float x, y;
        public final int sourceStart, sourceEnd;

        TextPiece(TextLayout text, float x, float y, int sourceStart, int sourceEnd) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.sourceStart = sourceStart;
            this.sourceEnd = sourceEnd;
        }
    }

    public static final class Rule {
        public final float x, y, width, height;

        Rule(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public static final class Embed {
        public final String id;
        public final float x, y, width, height, baseline;
        public final int sourceStart, sourceEnd;

        Embed(
                String id,
                float x,
                float y,
                float width,
                float height,
                float baseline,
                int sourceStart,
                int sourceEnd) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.baseline = baseline;
            this.sourceStart = sourceStart;
            this.sourceEnd = sourceEnd;
        }
    }

    public static final class Diagnostic {
        public final String stage, message;
        public final int start, end;

        Diagnostic(String stage, int start, int end, String message) {
            this.stage = stage;
            this.start = start;
            this.end = end;
            this.message = message;
        }
    }
}
