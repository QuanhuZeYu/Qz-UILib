package club.heiqi.uilib.ui.host;

import club.heiqi.uilib.ui.base.props.UiCursor;

/**
 * 文档栈系统光标宿主兼容入口。
 *
 * @deprecated 请使用 {@link SystemUiCursorHost}。
 */
@Deprecated
public class SystemDocumentCursorHost extends SystemUiCursorHost implements DocumentCursorHost {

    private static final SystemDocumentCursorHost INSTANCE =
            new SystemDocumentCursorHost(new CompatibilitySingletonBackend(), true);

    private final boolean delegateToSystemUiHost;

    public SystemDocumentCursorHost(NativeCursorBackend backend) {
        this(backend, false);
    }

    private SystemDocumentCursorHost(NativeCursorBackend backend, boolean delegateToSystemUiHost) {
        super(backend);
        this.delegateToSystemUiHost = delegateToSystemUiHost;
    }

    public static SystemDocumentCursorHost getInstance() {
        return INSTANCE;
    }

    @Override
    public void applyCursor(UiCursor cursor) {
        if (delegateToSystemUiHost) {
            SystemUiCursorHost.getInstance().applyCursor(cursor);
            return;
        }
        super.applyCursor(cursor);
    }

    @Override
    public void forceApplyCursor(UiCursor cursor) {
        if (delegateToSystemUiHost) {
            SystemUiCursorHost.getInstance().forceApplyCursor(cursor);
            return;
        }
        super.forceApplyCursor(cursor);
    }

    private static final class CompatibilitySingletonBackend implements NativeCursorBackend {

        @Override
        public boolean isRuntimeAvailable() {
            return false;
        }

        @Override
        public void showCursor() {
        }

        @Override
        public void hideCursor() {
        }

        @Override
        public void applyDefaultCursor() {
        }

        @Override
        public void applySystemCursor(ResolvedCursorKind cursorKind) {
        }
    }
}
