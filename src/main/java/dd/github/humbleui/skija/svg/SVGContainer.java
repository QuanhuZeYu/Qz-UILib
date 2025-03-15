package io.github.humbleui.skija.svg;

import io.github.humbleui.skija.impl.Library;
import org.jetbrains.annotations.ApiStatus;

public abstract class SVGContainer extends SVGTransformableNode {
    static {
        Library.staticLoad();
    }

    @ApiStatus.Internal
    public SVGContainer(long ptr) {
        super(ptr);
    }
}
