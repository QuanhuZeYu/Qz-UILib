package club.heiqi.uilib.api.chat;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 聊天动作注册句柄：{@link #close()} 幂等注销，跨聊天开关保持注册。
 *
 * <p>候选 API（规划《聊天工具栏与HUD布局编辑》P1）。</p>
 */
public final class ChatActionRegistration {
    private final Runnable closer;
    private final AtomicBoolean closed = new AtomicBoolean();

    ChatActionRegistration(Runnable closer) {
        this.closer = closer;
    }

    /** 注销动作（重复调用幂等）。 */
    public void close() {
        if (closed.compareAndSet(false, true)) {
            closer.run();
        }
    }

    /** @return 是否已注销 */
    public boolean isClosed() {
        return closed.get();
    }
}
