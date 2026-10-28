package club.heiqi.uilib.ui.host.minecraft;

import club.heiqi.uilib.ui.scene.host.ScenePageSession;

/** 一次 MC 接入先退休宿主输入，再结束会话；树与 GPU 资源仍由会话在安全边界释放。 */
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
            // 关屏先同步退休唯一输入消费者，使 close 回调或 scope 释放可安全重入开页。
            if (closing != null) closeInput(closing);
        } catch (RuntimeException | Error next) {
            failure = next;
        }
        try {
            if (opened != null) opened.close();
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
