package club.heiqi.uilib.ui.widget;

import java.util.ArrayList;
import java.util.List;

/**
 * 记录一次 UI build attempt 期间新增直接挂接边的事务。
 *
 * <p>该事务只负责撤销 `parent -> child` 这一层直接父子边，
 * 不会递归清空子树，从而避免在首次打开失败时误拆持久复合控件的内部骨架。</p>
 */
public final class WidgetBuildAttachmentTransaction {

    private static final ThreadLocal<WidgetBuildAttachmentTransaction> ACTIVE_TRANSACTION =
            new ThreadLocal<WidgetBuildAttachmentTransaction>();

    private final List<DirectAttachment> directAttachments = new ArrayList<DirectAttachment>();
    private boolean recordingClosed;
    private boolean rolledBack;

    private WidgetBuildAttachmentTransaction() {}

    /**
     * 开启一次 build attempt 级挂接事务。
     *
     * @return 当前事务
     */
    public static WidgetBuildAttachmentTransaction beginBuildAttempt() {
        if (ACTIVE_TRANSACTION.get() != null) {
            throw new IllegalStateException("Widget build attachment transaction is already active.");
        }
        WidgetBuildAttachmentTransaction transaction = new WidgetBuildAttachmentTransaction();
        ACTIVE_TRANSACTION.set(transaction);
        return transaction;
    }

    static void recordDirectAttachment(Widget parent, Widget child) {
        WidgetBuildAttachmentTransaction transaction = ACTIVE_TRANSACTION.get();
        if (transaction == null || transaction.recordingClosed || transaction.rolledBack) {
            return;
        }
        transaction.directAttachments.add(new DirectAttachment(parent, child));
    }

    /**
     * 提交当前事务，结束 build 期间的挂接记录。
     *
     * <p>提交后会停止继续记边，但已记录的直接挂接仍会保留，
     * 以便首次 open 后续步骤失败时执行回滚。</p>
     */
    public void commit() {
        closeRecording();
    }

    /**
     * 按逆序撤销本次事务记录到的全部直接挂接边。
     */
    public void rollback() {
        if (rolledBack) {
            return;
        }
        closeRecording();
        for (int i = directAttachments.size() - 1; i >= 0; i--) {
            directAttachments.get(i).detach();
        }
        directAttachments.clear();
        rolledBack = true;
    }

    private void closeRecording() {
        if (recordingClosed) {
            return;
        }
        WidgetBuildAttachmentTransaction current = ACTIVE_TRANSACTION.get();
        if (current == this) {
            ACTIVE_TRANSACTION.remove();
        }
        recordingClosed = true;
    }

    /**
     * 单条直接父子挂接记录。
     */
    private static final class DirectAttachment {

        private final Widget parent;
        private final Widget child;

        private DirectAttachment(Widget parent, Widget child) {
            this.parent = parent;
            this.child = child;
        }

        private void detach() {
            parent.detachDirectChild(child);
        }
    }
}
