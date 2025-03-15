package io.github.humbleui.skija;

import io.github.humbleui.skija.impl.Library;
import io.github.humbleui.skija.impl.Stats;
import org.jetbrains.annotations.ApiStatus;

import java.io.OutputStream;

public class OutputWStream extends WStream {
    static {
        Library.staticLoad();
    }

    @ApiStatus.Internal
    public final OutputStream _out;

    @ApiStatus.Internal
    public static class _FinalizerHolder {
        public static final long PTR = _nGetFinalizer();
    }

    public OutputWStream(OutputStream out) {
        super(_nMake(out), _FinalizerHolder.PTR);
        Stats.onNativeCall();
        _out = out;
    }

    @ApiStatus.Internal
    public static native long _nGetFinalizer();

    @ApiStatus.Internal
    public static native long _nMake(OutputStream out);
}
