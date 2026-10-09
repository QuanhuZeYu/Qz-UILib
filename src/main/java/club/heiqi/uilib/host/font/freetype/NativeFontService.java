package club.heiqi.uilib.host.font.freetype;

import club.heiqi.uilib.ui.scene.text.font.nativefont.FontSession;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontFace;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.freetype.FreeType;

/** 原生字体资源作用域。所有操作（含关闭）仅限创建线程，不创建线程池。 */
public final class NativeFontService implements FontSession {
    private final Thread thread = Thread.currentThread();
    private final Set<NativeFontFace> faces = new LinkedHashSet<NativeFontFace>();
    private long library;

    public NativeFontService() {
        // 当前分发的 FreeType native 同时导出 HarfBuzz；必须在首次加载 HB 前设置。
        Configuration.HARFBUZZ_LIBRARY_NAME.set("freetype");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer out = stack.mallocPointer(1);
            check(FreeType.FT_Init_FreeType(out), "FT_Init_FreeType");
            library = out.get(0);
        }
    }

    /** 拷贝 source 的 remaining 字节；调用方可立即释放/修改自己的缓冲。 */
    public FontFace openFace(ByteBuffer source, long faceIndex, float emSize) {
        return openFace(source,faceIndex,emSize,java.util.Collections.<String,Float>emptyMap());
    }
    @Override public boolean supportsVariations(){return true;}
    @Override public FontFace openFace(ByteBuffer source,long faceIndex,float emSize,java.util.Map<String,Float> axes) {
        checkOpen();
        NativeFontFace face = new NativeFontFace(this, library, source, faceIndex, emSize, axes);
        try {
            faces.add(face);
            return face;
        } catch (RuntimeException | Error failure) {
            try { face.close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    void checkThread() {
        if (Thread.currentThread() != thread) throw new IllegalStateException("Native font service belongs to its creating thread");
    }

    void checkOpen() {
        checkThread();
        if (library == 0) throw new IllegalStateException("Native font service is closed");
    }

    void forget(NativeFontFace face) { faces.remove(face); }

    static void check(int error, String operation) {
        if (error != 0) throw new IllegalStateException(operation + " failed: FreeType error " + error);
    }

    @Override public void close() {
        checkThread();
        if (library == 0) return;
        Throwable failure = null;
        for (NativeFontFace face : new ArrayList<NativeFontFace>(faces)) {
            try { face.close(); } catch (RuntimeException | Error error) {
                if (failure == null) failure = error; else failure.addSuppressed(error);
            }
        }
        long owned = library;
        library = 0;
        try { check(FreeType.FT_Done_FreeType(owned), "FT_Done_FreeType"); }
        catch (RuntimeException | Error error) {
            if (failure == null) failure = error; else failure.addSuppressed(error);
        }
        if (failure instanceof Error) throw (Error) failure;
        if (failure != null) throw (RuntimeException) failure;
    }
}