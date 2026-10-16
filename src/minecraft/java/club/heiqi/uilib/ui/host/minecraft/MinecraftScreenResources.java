package club.heiqi.uilib.ui.host.minecraft;

import club.heiqi.uilib.ui.scene.host.ScenePageSession;

/** 一次MC接入按依赖顺序释放session、原生输入桥；清理失败不能覆盖首错。 */
final class MinecraftScreenResources implements AutoCloseable {
    private ScenePageSession session;
    private AutoCloseable inputs;

    MinecraftScreenResources(ScenePageSession session, AutoCloseable inputs) {
        this.session = session;
        this.inputs = inputs;
    }

    @Override public void close() {
        ScenePageSession opened = session;
        AutoCloseable closing = inputs;
        session = null;
        inputs = null;
        Throwable failure = null;
        try {
            if (opened != null) opened.close();
        } catch (RuntimeException | Error next) {
            failure = next;
        }
        try {
            if (closing != null) {
                if (opened == null) closeInput(closing);
                // 域内close只结束输入队列，session.release延至当前操作结束。
                // 原生桥排在同一cleanup队列的后面，不能凭ended标志抢先释放。
                else opened.domain().deferCleanup(() -> closeInput(closing));
            }
        } catch (RuntimeException | Error next) {
            if (failure == null) failure = next;
            else if (next != failure) failure.addSuppressed(next);
        }
        if (failure instanceof Error) throw (Error) failure;
        if (failure instanceof RuntimeException) throw (RuntimeException) failure;
    }

    private static void closeInput(AutoCloseable input) {
        try { input.close(); }
        catch (RuntimeException | Error failure) { throw failure; }
        catch (Exception failure) { throw new IllegalStateException("MC输入资源释放失败", failure); }
    }
}
