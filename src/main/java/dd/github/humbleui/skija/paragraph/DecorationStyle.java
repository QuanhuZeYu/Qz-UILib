// Generated by delombok at Thu Feb 13 14:20:21 UTC 2025
package io.github.humbleui.skija.paragraph;

import org.jetbrains.annotations.*;

public class DecorationStyle {
    public static final DecorationStyle NONE = new DecorationStyle(false, false, false, true, -16777216,
            DecorationLineStyle.SOLID, 1.0F);
    public final boolean _underline;
    public final boolean _overline;
    public final boolean _lineThrough;
    public final boolean _gaps;
    public final int _color;
    public final DecorationLineStyle _lineStyle;
    public final float _thicknessMultiplier;

    @ApiStatus.Internal
    public DecorationStyle(boolean underline, boolean overline, boolean lineThrough, boolean gaps, int color,
            int lineStyle, float thicknessMultiplier) {
        this(underline, overline, lineThrough, gaps, color, DecorationLineStyle._values[lineStyle],
                thicknessMultiplier);
    }

    public boolean hasUnderline() {
        return _underline;
    }

    public boolean hasOverline() {
        return _overline;
    }

    public boolean hasLineThrough() {
        return _lineThrough;
    }

    public boolean hasGaps() {
        return _gaps;
    }

    @SuppressWarnings("all")
    public DecorationStyle(final boolean underline, final boolean overline, final boolean lineThrough,
            final boolean gaps, final int color, final DecorationLineStyle lineStyle, final float thicknessMultiplier) {
        this._underline = underline;
        this._overline = overline;
        this._lineThrough = lineThrough;
        this._gaps = gaps;
        this._color = color;
        this._lineStyle = lineStyle;
        this._thicknessMultiplier = thicknessMultiplier;
    }

    @SuppressWarnings("all")
    public int getColor() {
        return this._color;
    }

    @SuppressWarnings("all")
    public DecorationLineStyle getLineStyle() {
        return this._lineStyle;
    }

    @SuppressWarnings("all")
    public float getThicknessMultiplier() {
        return this._thicknessMultiplier;
    }

    @Override
    @SuppressWarnings("all")
    public boolean equals(final Object o) {
        if (o == this)
            return true;
        if (!(o instanceof DecorationStyle))
            return false;
        final DecorationStyle other = (DecorationStyle) o;
        if (!other.canEqual((Object) this))
            return false;
        if (this._underline != other._underline)
            return false;
        if (this._overline != other._overline)
            return false;
        if (this._lineThrough != other._lineThrough)
            return false;
        if (this._gaps != other._gaps)
            return false;
        if (this.getColor() != other.getColor())
            return false;
        if (Float.compare(this.getThicknessMultiplier(), other.getThicknessMultiplier()) != 0)
            return false;
        final Object this$_lineStyle = this.getLineStyle();
        final Object other$_lineStyle = other.getLineStyle();
        if (this$_lineStyle == null ? other$_lineStyle != null : !this$_lineStyle.equals(other$_lineStyle))
            return false;
        return true;
    }

    @SuppressWarnings("all")
    protected boolean canEqual(final Object other) {
        return other instanceof DecorationStyle;
    }

    @Override
    @SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + (this._underline ? 79 : 97);
        result = result * PRIME + (this._overline ? 79 : 97);
        result = result * PRIME + (this._lineThrough ? 79 : 97);
        result = result * PRIME + (this._gaps ? 79 : 97);
        result = result * PRIME + this.getColor();
        result = result * PRIME + Float.floatToIntBits(this.getThicknessMultiplier());
        final Object $_lineStyle = this.getLineStyle();
        result = result * PRIME + ($_lineStyle == null ? 43 : $_lineStyle.hashCode());
        return result;
    }

    @Override
    @SuppressWarnings("all")
    public String toString() {
        return "DecorationStyle(_underline=" + this._underline + ", _overline=" + this._overline + ", _lineThrough="
                + this._lineThrough + ", _gaps=" + this._gaps + ", _color=" + this.getColor() + ", _lineStyle="
                + this.getLineStyle() + ", _thicknessMultiplier=" + this.getThicknessMultiplier() + ")";
    }

    /**
     * @return {@code this}.
     */
    @SuppressWarnings("all")
    public DecorationStyle withUnderline(final boolean _underline) {
        return this._underline == _underline ? this
                : new DecorationStyle(_underline, this._overline, this._lineThrough, this._gaps, this._color,
                        this._lineStyle, this._thicknessMultiplier);
    }

    /**
     * @return {@code this}.
     */
    @SuppressWarnings("all")
    public DecorationStyle withOverline(final boolean _overline) {
        return this._overline == _overline ? this
                : new DecorationStyle(this._underline, _overline, this._lineThrough, this._gaps, this._color,
                        this._lineStyle, this._thicknessMultiplier);
    }

    /**
     * @return {@code this}.
     */
    @SuppressWarnings("all")
    public DecorationStyle withLineThrough(final boolean _lineThrough) {
        return this._lineThrough == _lineThrough ? this
                : new DecorationStyle(this._underline, this._overline, _lineThrough, this._gaps, this._color,
                        this._lineStyle, this._thicknessMultiplier);
    }

    /**
     * @return {@code this}.
     */
    @SuppressWarnings("all")
    public DecorationStyle withGaps(final boolean _gaps) {
        return this._gaps == _gaps ? this
                : new DecorationStyle(this._underline, this._overline, this._lineThrough, _gaps, this._color,
                        this._lineStyle, this._thicknessMultiplier);
    }

    /**
     * @return {@code this}.
     */
    @SuppressWarnings("all")
    public DecorationStyle withColor(final int _color) {
        return this._color == _color ? this
                : new DecorationStyle(this._underline, this._overline, this._lineThrough, this._gaps, _color,
                        this._lineStyle, this._thicknessMultiplier);
    }

    /**
     * @return {@code this}.
     */
    @SuppressWarnings("all")
    public DecorationStyle withLineStyle(final DecorationLineStyle _lineStyle) {
        return this._lineStyle == _lineStyle ? this
                : new DecorationStyle(this._underline, this._overline, this._lineThrough, this._gaps, this._color,
                        _lineStyle, this._thicknessMultiplier);
    }

    /**
     * @return {@code this}.
     */
    @SuppressWarnings("all")
    public DecorationStyle withThicknessMultiplier(final float _thicknessMultiplier) {
        return this._thicknessMultiplier == _thicknessMultiplier ? this
                : new DecorationStyle(this._underline, this._overline, this._lineThrough, this._gaps, this._color,
                        this._lineStyle, _thicknessMultiplier);
    }
}
