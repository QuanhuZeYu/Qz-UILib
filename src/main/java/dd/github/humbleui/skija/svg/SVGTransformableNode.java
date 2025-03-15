package io.github.humbleui.skija.svg;

import io.github.humbleui.skija.impl.Library;
import org.jetbrains.annotations.*;

public abstract class SVGTransformableNode extends SVGNode {
    static {
        Library.staticLoad();
    }

    @ApiStatus.Internal
    public SVGTransformableNode(long ptr) {
        super(ptr);
    }
}
