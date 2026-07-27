package club.heiqi.uilib.ui.container.experimental.storage;

import java.util.Objects;
import club.heiqi.uilib.ui.container.experimental.model.LongContainerSnapshot;

/** Experimental storage 结果值；snapshot 是操作完成后的状态。 */
public final class TransferResult {
    private final TransferStatus status;
    private final long moved;
    private final LongContainerSnapshot snapshot;

    /** 创建结果并校验 status 与 moved 的状态不变量。 */
    public TransferResult(TransferStatus status, long moved, LongContainerSnapshot snapshot) {
        this.status = Objects.requireNonNull(status, "status");
        this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
        if (moved < 0) throw new IllegalArgumentException("moved must not be negative");
        if ((status == TransferStatus.COMPLETED || status == TransferStatus.PARTIAL) && moved == 0) throw new IllegalArgumentException("successful transfer must move a positive amount");
        if ((status == TransferStatus.NO_CHANGE || status == TransferStatus.NOT_FOUND) && moved != 0) throw new IllegalArgumentException("non-changing transfer must move zero");
        this.moved = moved;
    }
    /** 返回状态。 */ public TransferStatus status() { return status; }
    /** 返回实际移动量。 */ public long moved() { return moved; }
    /** 返回操作后的快照。 */ public LongContainerSnapshot snapshot() { return snapshot; }
}
