// Generated by delombok at Thu Feb 13 14:20:21 UTC 2025
package io.github.humbleui.skija.shaper;

import io.github.humbleui.skija.Font;
import io.github.humbleui.types.Point;

public class RunInfo {
    public long _fontPtr;
    public final int _bidiLevel;
    public final float _advanceX;
    public final float _advanceY;
    public final long _glyphCount;
    /**
     * WARN does not work in Shaper.makeCoreText
     * https://bugs.chromium.org/p/skia/issues/detail?id=10899
     */
    public final int _rangeBegin;
    /**
     * WARN does not work in Shaper.makeCoreText
     * https://bugs.chromium.org/p/skia/issues/detail?id=10899
     */
    public final int _rangeSize;

    public RunInfo(long fontPtr, int biDiLevel, float advanceX, float advanceY, long glyphCount, int rangeBegin,
            int rangeSize) {
        _fontPtr = fontPtr;
        _bidiLevel = biDiLevel;
        _advanceX = advanceX;
        _advanceY = advanceY;
        _glyphCount = glyphCount;
        _rangeBegin = rangeBegin;
        _rangeSize = rangeSize;
    }

    public Point getAdvance() {
        return new Point(_advanceX, _advanceY);
    }

    /**
     * WARN does not work in Shaper.makeCoreText
     * https://bugs.chromium.org/p/skia/issues/detail?id=10899
     */
    public int getRangeEnd() {
        return _rangeBegin + _rangeSize;
    }

    public Font getFont() {
        if (_fontPtr == 0)
            throw new IllegalStateException("getFont() is only valid inside RunHandler callbacks");
        return Font.makeClone(_fontPtr);
    }

    @SuppressWarnings("all")
    public int getBidiLevel() {
        return this._bidiLevel;
    }

    @SuppressWarnings("all")
    public float getAdvanceX() {
        return this._advanceX;
    }

    @SuppressWarnings("all")
    public float getAdvanceY() {
        return this._advanceY;
    }

    @SuppressWarnings("all")
    public long getGlyphCount() {
        return this._glyphCount;
    }

    /**
     * WARN does not work in Shaper.makeCoreText
     * https://bugs.chromium.org/p/skia/issues/detail?id=10899
     */
    @SuppressWarnings("all")
    public int getRangeBegin() {
        return this._rangeBegin;
    }

    /**
     * WARN does not work in Shaper.makeCoreText
     * https://bugs.chromium.org/p/skia/issues/detail?id=10899
     */
    @SuppressWarnings("all")
    public int getRangeSize() {
        return this._rangeSize;
    }

    /**
     * @return {@code this}.
     */
    @SuppressWarnings("all")
    public RunInfo setFontPtr(final long _fontPtr) {
        this._fontPtr = _fontPtr;
        return this;
    }

    @Override
    @SuppressWarnings("all")
    public boolean equals(final Object o) {
        if (o == this)
            return true;
        if (!(o instanceof RunInfo))
            return false;
        final RunInfo other = (RunInfo) o;
        if (!other.canEqual((Object) this))
            return false;
        if (this._fontPtr != other._fontPtr)
            return false;
        if (this.getBidiLevel() != other.getBidiLevel())
            return false;
        if (Float.compare(this.getAdvanceX(), other.getAdvanceX()) != 0)
            return false;
        if (Float.compare(this.getAdvanceY(), other.getAdvanceY()) != 0)
            return false;
        if (this.getGlyphCount() != other.getGlyphCount())
            return false;
        if (this.getRangeBegin() != other.getRangeBegin())
            return false;
        if (this.getRangeSize() != other.getRangeSize())
            return false;
        return true;
    }

    @SuppressWarnings("all")
    protected boolean canEqual(final Object other) {
        return other instanceof RunInfo;
    }

    @Override
    @SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final long $_fontPtr = this._fontPtr;
        result = result * PRIME + (int) ($_fontPtr >>> 32 ^ $_fontPtr);
        result = result * PRIME + this.getBidiLevel();
        result = result * PRIME + Float.floatToIntBits(this.getAdvanceX());
        result = result * PRIME + Float.floatToIntBits(this.getAdvanceY());
        final long $_glyphCount = this.getGlyphCount();
        result = result * PRIME + (int) ($_glyphCount >>> 32 ^ $_glyphCount);
        result = result * PRIME + this.getRangeBegin();
        result = result * PRIME + this.getRangeSize();
        return result;
    }

    @Override
    @SuppressWarnings("all")
    public String toString() {
        return "RunInfo(_fontPtr=" + this._fontPtr + ", _bidiLevel=" + this.getBidiLevel() + ", _advanceX="
                + this.getAdvanceX() + ", _advanceY=" + this.getAdvanceY() + ", _glyphCount=" + this.getGlyphCount()
                + ", _rangeBegin=" + this.getRangeBegin() + ", _rangeSize=" + this.getRangeSize() + ")";
    }
}
