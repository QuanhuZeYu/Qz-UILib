package club.heiqi.uilib.font.page;

import club.heiqi.uilib.font.glyph.GlyphRequestToken;

/** Sparse lifecycle entry, accessed only while the page manager owner lock is held. */
final class MathGlyphRecord {
    GlyphRequestToken token;
    byte state = GlyphRuntimeTables.STATE_ABSENT;
    int location = GlyphRuntimeTables.LOCATION_NOT_READY;
    boolean pressure;
    MathGlyphSlot slot;

    void clearResidency() {
        location = GlyphRuntimeTables.LOCATION_NOT_READY;
        slot = null;
    }
}
