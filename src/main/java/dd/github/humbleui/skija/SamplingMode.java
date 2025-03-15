package io.github.humbleui.skija;

import org.jetbrains.annotations.*;

/**
 * @see FilterMipmap
 * @see CubicResampler
 */
public interface SamplingMode {
    public static final SamplingMode DEFAULT = new FilterMipmap(FilterMode.NEAREST, MipmapMode.NONE);
    public static final SamplingMode LINEAR = new FilterMipmap(FilterMode.LINEAR, MipmapMode.NONE);
    public static final SamplingMode MITCHELL = new CubicResampler(0.33333334f, 0.33333334f);
    public static final SamplingMode CATMULL_ROM = new CubicResampler(0, 0.5f);

    // 10 + 30-bit float + 32-bit float: CubicResampler
    // 01 + 30-bit zeros + 32-bit int: SamplingModeAnisotropic
    // 00 + 30-bit int + 32-bit int: FilterMipmap
    @ApiStatus.Internal
    long _pack();
}
