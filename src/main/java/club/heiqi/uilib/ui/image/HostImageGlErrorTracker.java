package club.heiqi.uilib.ui.image;

import org.lwjgl.opengl.GL11;

/**
 * 单次 HostImage 围栏调用的 GL 首错记录器。
 *
 * <p>记录器由围栏拥有，使用线程局部状态把生产路径的细粒度操作检查点归入当前阶段；
 * 首个错误锁存后不再消费或覆盖，结束时必须清理。</p>
 */
final class HostImageGlErrorTracker {

    /** 可替换的 GL error 消费入口。 */
    interface ErrorSource { int consumeGlError(); }

    /** 锁存的首错。 */
    static final class FirstError {
        private final String phase;
        private final String operation;
        private final int error;

        private FirstError(String phase, String operation, int error) {
            this.phase = phase;
            this.operation = operation;
            this.error = error;
        }

        String getPhase() { return phase; }
        String getOperation() { return operation; }
        int getError() { return error; }

        String detail() {
            return "phase=" + phase + " operation=" + operation + " gl-error=" + error;
        }
    }

    private static final ThreadLocal<Session> CURRENT = new ThreadLocal<Session>();

    private HostImageGlErrorTracker() { }

    /** 开始一次调用；入口错误必须在此之前检查。 */
    static void begin(ErrorSource errorSource) {
        if (errorSource == null) throw new IllegalArgumentException("errorSource");
        CURRENT.set(new Session(errorSource));
    }

    /** 切换稳定阶段名。 */
    static void enterPhase(String phase) {
        Session session = CURRENT.get();
        if (session != null) session.phase = phase;
    }

    /** 检查当前操作并锁存首个非零 GL error。 */
    static void checkpoint(String operation) {
        Session session = CURRENT.get();
        if (session == null || session.firstError != null) return;
        int error = session.errorSource.consumeGlError();
        if (error != GL11.GL_NO_ERROR) {
            session.firstError = new FirstError(session.phase, operation, error);
        }
    }

    /** @return 当前调用锁存的首错；没有则为 {@code null} */
    static FirstError firstError() {
        Session session = CURRENT.get();
        return session == null ? null : session.firstError;
    }

    /** 清理线程局部调用状态。 */
    static void end() {
        CURRENT.remove();
    }

    /** @return 当前线程是否仍有活动记录器，供测试验证清理 */
    static boolean isActive() {
        return CURRENT.get() != null;
    }

    private static final class Session {
        private final ErrorSource errorSource;
        private String phase = "capture";
        private FirstError firstError;

        private Session(ErrorSource errorSource) { this.errorSource = errorSource; }
    }
}
