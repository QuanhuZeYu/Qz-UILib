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
            // 页面宿主规格的唯一输入消费者属于关屏边界，不能随树延迟到当前命令结束。
            // session.close 的完成回调和 scope 释放也可能重入开页，届时旧桥必须已退休。
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
