package io.github.humbleui.skija.paragraph;

import org.jetbrains.annotations.ApiStatus;

public enum Direction {
    RTL,
    LTR;

    @ApiStatus.Internal
    public static final Direction[] _values = values();
}
