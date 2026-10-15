package club.heiqi.uilib.host.font.freetype;

import club.heiqi.uilib.ui.scene.text.font.nativefont.FontSession;
import club.heiqi.uilib.ui.scene.text.font.nativefont.FontFace;
import club.heiqi.uilib.ui.scene.text.font.nativefont.ShapeContext;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.Configuration;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FreeType;

/** 原生字体资源作用域。所有操作（含关闭）仅限创建线程，不创建线程池。 */
public final class NativeFontService implements FontSession {
    private final Thread thread = Thread.currentThread();
    private final Set<NativeFontFace> faces = new LinkedHashSet<NativeFontFace>();
    private long library;
    // 一次整形作用域只有一个完整上下文；face LRU 不拥有也不关闭它。
    private NativeShapeContext context;

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
        return openFace(source, faceIndex, emSize, java.util.Collections.<String, Float>emptyMap());
    }

    @Override
    public boolean supportsVariations() {
        return true;
    }

    @Override
    public FontFace openFace(
            ByteBuffer source, long faceIndex, float emSize, java.util.Map<String, Float> axes) {
        checkOpen();
        NativeFontFace face = new NativeFontFace(this, library, source, faceIndex, emSize, axes);
        try {
            faces.add(face);
            return face;
        } catch (RuntimeException | Error failure) {
            try {
                face.close();
            } catch (RuntimeException | Error cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    @Override
    public ShapeContext prepareShapeContext(String text) {
        checkOpen();
        if (context != null) throw new IllegalStateException("当前整形上下文尚未关闭");
        context = new NativeShapeContext(this, text);
        return context;
    }

    static final class NativeShapeContext extends ShapeContext {
        private final NativeFontService owner;
        private ByteBuffer utf16;

        NativeShapeContext(NativeFontService owner, String text) {
            super(text);
            this.owner = owner;
            // 不使用 MemoryStack 编码长上下文；每个完整行/段落只编码一次。
            utf16 = MemoryUtil.memUTF16(text, false);
        }

        @Override
        public String text() {
            owner.checkOpen();
            return super.text();
        }

        ByteBuffer utf16(NativeFontService session) {
            if (session != owner) throw new IllegalArgumentException("整形上下文不属于当前字体 session");
            text();
            return utf16;
        }

        @Override
        public void close() {
            owner.checkThread();
            if (utf16 == null) return;
            try {
                MemoryUtil.memFree(utf16);
            } finally {
                utf16 = null;
                super.close();
                if (owner.context == this) owner.context = null;
            }
        }
    }

    void checkThread() {
        if (Thread.currentThread() != thread)
            throw new IllegalStateException("Native font service belongs to its creating thread");
    }

    void checkOpen() {
        checkThread();
        if (library == 0) throw new IllegalStateException("Native font service is closed");
    }

    void forget(NativeFontFace face) {
        faces.remove(face);
    }

    static void check(int error, String operation) {
        if (error != 0)
            throw new IllegalStateException(operation + " failed: FreeType error " + error);
    }

    @Override
    public void close() {
        checkThread();
        if (library == 0) return;
        Throwable failure = null;
        for (NativeFontFace face : new ArrayList<NativeFontFace>(faces)) {
            try {
                face.close();
            } catch (RuntimeException | Error error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (context != null) {
            try {
                context.close();
            } catch (RuntimeException | Error error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        long owned = library;
        library = 0;
        try {
            check(FreeType.FT_Done_FreeType(owned), "FT_Done_FreeType");
        } catch (RuntimeException | Error error) {
            if (failure == null) failure = error;
            else failure.addSuppressed(error);
        }
        if (failure instanceof Error) throw (Error) failure;
        if (failure != null) throw (RuntimeException) failure;
    }
}
