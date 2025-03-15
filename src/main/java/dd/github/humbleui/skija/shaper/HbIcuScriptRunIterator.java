package io.github.humbleui.skija.shaper;

import io.github.humbleui.skija.ManagedString;
import io.github.humbleui.skija.impl.Library;
import io.github.humbleui.skija.impl.Native;
import io.github.humbleui.skija.impl.ReferenceUtil;
import io.github.humbleui.skija.impl.Stats;
import org.jetbrains.annotations.*;

public class HbIcuScriptRunIterator extends ManagedRunIterator<ScriptRun> {
    static {
        Library.staticLoad();
    }

    public HbIcuScriptRunIterator(ManagedString text, boolean manageText) {
        super(_nMake(Native.getPtr(text)), text, manageText);
        Stats.onNativeCall();
        ReferenceUtil.reachabilityFence(text);
    }

    public HbIcuScriptRunIterator(String text) {
        this(new ManagedString(text), true);
    }

    @Override
    public ScriptRun next() {
        try {
            _nConsume(_ptr);
            return new ScriptRun(_getEndOfCurrentRun(), _nGetCurrentScriptTag(_ptr));
        } finally {
            ReferenceUtil.reachabilityFence(this);
        }
    }

    @ApiStatus.Internal
    public static native long _nMake(long textPtr);

    @ApiStatus.Internal
    public static native int _nGetCurrentScriptTag(long ptr);
}
