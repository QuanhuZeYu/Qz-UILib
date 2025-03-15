package io.github.humbleui.skija.shaper;

import io.github.humbleui.skija.ManagedString;
import io.github.humbleui.skija.impl.Library;
import io.github.humbleui.skija.impl.Native;
import io.github.humbleui.skija.impl.ReferenceUtil;
import io.github.humbleui.skija.impl.Stats;
import org.jetbrains.annotations.ApiStatus;

public class IcuBidiRunIterator extends ManagedRunIterator<BidiRun> {
    static {
        Library.staticLoad();
    }

    public IcuBidiRunIterator(ManagedString text, boolean manageText, int bidiLevel) {
        super(_nMake(Native.getPtr(text), bidiLevel), text, manageText);
        Stats.onNativeCall();
        ReferenceUtil.reachabilityFence(text);
    }

    public IcuBidiRunIterator(String text, int bidiLevel) {
        this(new ManagedString(text), true, bidiLevel);
    }

    @Override
    public BidiRun next() {
        try {
            _nConsume(_ptr);
            return new BidiRun(_getEndOfCurrentRun(), _nGetCurrentLevel(_ptr));
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    @ApiStatus.Internal
    public static native long _nMake(long textPtr, int bidiLevel);

    @ApiStatus.Internal
    public static native int _nGetCurrentLevel(long ptr);
}
