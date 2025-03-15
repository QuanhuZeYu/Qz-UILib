// Generated by delombok at Thu Feb 13 14:20:21 UTC 2025
package io.github.humbleui.skija;

import org.jetbrains.annotations.*;

public class EncodeJPEGOptions {
    public static final EncodeJPEGOptions DEFAULT = new EncodeJPEGOptions(100, EncodeJPEGDownsampleMode.DS_420,
            EncodeJPEGAlphaMode.IGNORE);
    @ApiStatus.Internal
    public final int _quality;
    @ApiStatus.Internal
    public final EncodeJPEGDownsampleMode _downsampleMode;
    @ApiStatus.Internal
    public final EncodeJPEGAlphaMode _alphaMode;

    @SuppressWarnings("all")
    public EncodeJPEGOptions(final int quality, final EncodeJPEGDownsampleMode downsampleMode,
            final EncodeJPEGAlphaMode alphaMode) {
        this._quality = quality;
        this._downsampleMode = downsampleMode;
        this._alphaMode = alphaMode;
    }

    @SuppressWarnings("all")
    public int getQuality() {
        return this._quality;
    }

    @SuppressWarnings("all")
    public EncodeJPEGDownsampleMode getDownsampleMode() {
        return this._downsampleMode;
    }

    @SuppressWarnings("all")
    public EncodeJPEGAlphaMode getAlphaMode() {
        return this._alphaMode;
    }

    @Override
    @SuppressWarnings("all")
    public boolean equals(final Object o) {
        if (o == this)
            return true;
        if (!(o instanceof EncodeJPEGOptions))
            return false;
        final EncodeJPEGOptions other = (EncodeJPEGOptions) o;
        if (!other.canEqual((Object) this))
            return false;
        if (this.getQuality() != other.getQuality())
            return false;
        final Object this$_downsampleMode = this.getDownsampleMode();
        final Object other$_downsampleMode = other.getDownsampleMode();
        if (this$_downsampleMode == null ? other$_downsampleMode != null
                : !this$_downsampleMode.equals(other$_downsampleMode))
            return false;
        final Object this$_alphaMode = this.getAlphaMode();
        final Object other$_alphaMode = other.getAlphaMode();
        if (this$_alphaMode == null ? other$_alphaMode != null : !this$_alphaMode.equals(other$_alphaMode))
            return false;
        return true;
    }

    @SuppressWarnings("all")
    protected boolean canEqual(final Object other) {
        return other instanceof EncodeJPEGOptions;
    }

    @Override
    @SuppressWarnings("all")
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + this.getQuality();
        final Object $_downsampleMode = this.getDownsampleMode();
        result = result * PRIME + ($_downsampleMode == null ? 43 : $_downsampleMode.hashCode());
        final Object $_alphaMode = this.getAlphaMode();
        result = result * PRIME + ($_alphaMode == null ? 43 : $_alphaMode.hashCode());
        return result;
    }

    @Override
    @SuppressWarnings("all")
    public String toString() {
        return "EncodeJPEGOptions(_quality=" + this.getQuality() + ", _downsampleMode=" + this.getDownsampleMode()
                + ", _alphaMode=" + this.getAlphaMode() + ")";
    }

    /**
     * @return {@code this}.
     */
    @SuppressWarnings("all")
    public EncodeJPEGOptions withQuality(final int _quality) {
        return this._quality == _quality ? this
                : new EncodeJPEGOptions(_quality, this._downsampleMode, this._alphaMode);
    }

    /**
     * @return {@code this}.
     */
    @SuppressWarnings("all")
    public EncodeJPEGOptions withDownsampleMode(final EncodeJPEGDownsampleMode _downsampleMode) {
        return this._downsampleMode == _downsampleMode ? this
                : new EncodeJPEGOptions(this._quality, _downsampleMode, this._alphaMode);
    }

    /**
     * @return {@code this}.
     */
    @SuppressWarnings("all")
    public EncodeJPEGOptions withAlphaMode(final EncodeJPEGAlphaMode _alphaMode) {
        return this._alphaMode == _alphaMode ? this
                : new EncodeJPEGOptions(this._quality, this._downsampleMode, _alphaMode);
    }
}
