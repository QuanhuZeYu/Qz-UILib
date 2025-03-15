package io.github.humbleui.skija.paragraph;

import io.github.humbleui.skija.FontMgr;
import io.github.humbleui.skija.Typeface;
import io.github.humbleui.skija.impl.Library;
import io.github.humbleui.skija.impl.Native;
import io.github.humbleui.skija.impl.ReferenceUtil;
import io.github.humbleui.skija.impl.Stats;

public class TypefaceFontProvider extends FontMgr {
    static {
        Library.staticLoad();
    }

    public TypefaceFontProvider() {
        super(_nMake());
        Stats.onNativeCall();
    }

    public TypefaceFontProvider registerTypeface(Typeface typeface) {
        return registerTypeface(typeface, null);
    }

    public TypefaceFontProvider registerTypeface(Typeface typeface, String alias) {
        try {
            Stats.onNativeCall();
            _nRegisterTypeface(_ptr, Native.getPtr(typeface), alias);
            return this;
        } finally {
            ReferenceUtil.reachabilityFence(typeface);
        }
    }

    public static native long _nMake();

    public static native long _nRegisterTypeface(long ptr, long typefacePtr, String alias);
}
