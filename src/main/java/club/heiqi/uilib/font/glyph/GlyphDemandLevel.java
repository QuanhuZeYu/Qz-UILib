package club.heiqi.uilib.font.glyph;

/** 字形异步管线内部使用的四级 demand 语义。 */
enum GlyphDemandLevel {
    WARMUP(0),
    PREFETCH(1),
    FOREGROUND(2),
    VISIBLE(3);

    private final int priorityOrder;

    GlyphDemandLevel(int priorityOrder) {
        this.priorityOrder = priorityOrder;
    }

    int getPriorityOrder() {
        return priorityOrder;
    }

    static GlyphDemandLevel fromLegacyPriority(GlyphGenerationPriority priority) {
        if (priority == GlyphGenerationPriority.HIGH) {
            return VISIBLE;
        }
        if (priority == GlyphGenerationPriority.NORMAL) {
            return FOREGROUND;
        }
        return PREFETCH;
    }

    GlyphGenerationPriority toLegacyPriority() {
        if (this == VISIBLE) {
            return GlyphGenerationPriority.HIGH;
        }
        if (this == FOREGROUND) {
            return GlyphGenerationPriority.NORMAL;
        }
        return GlyphGenerationPriority.LOW;
    }
}
