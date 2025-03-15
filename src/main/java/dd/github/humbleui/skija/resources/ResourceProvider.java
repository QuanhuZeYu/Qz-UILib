package io.github.humbleui.skija.resources;

import io.github.humbleui.skija.impl.RefCnt;
import org.jetbrains.annotations.*;

public abstract class ResourceProvider extends RefCnt {
    @ApiStatus.Internal
    public ResourceProvider(long ptr) {
        super(ptr);
    }
}
