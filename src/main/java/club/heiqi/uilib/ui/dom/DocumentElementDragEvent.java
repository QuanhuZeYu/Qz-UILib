package club.heiqi.uilib.ui.dom;

/**
 * HTML-like 元素拖拽事件。
 */
public final class DocumentElementDragEvent {

    private final ElementNode target;
    private final ElementNode currentTarget;
    private final int startDocumentX;
    private final int startDocumentY;
    private final int documentX;
    private final int documentY;
    private final int deltaDocumentX;
    private final int deltaDocumentY;
    private final int button;
    private final long timeNanos;
    private final DragPhase phase;

    /**
     * 创建元素拖拽事件。
     *
     * @param target 初始拖拽目标
     * @param currentTarget 当前处理元素
     * @param startDocumentX 拖拽起点 X
     * @param startDocumentY 拖拽起点 Y
     * @param documentX 当前文档局部 X
     * @param documentY 当前文档局部 Y
     * @param deltaDocumentX 相对上次事件的 X 位移
     * @param deltaDocumentY 相对上次事件的 Y 位移
     * @param button 鼠标按钮
     * @param timeNanos 时间戳
     * @param phase 拖拽阶段
     */
    public DocumentElementDragEvent(ElementNode target, ElementNode currentTarget, int startDocumentX,
            int startDocumentY, int documentX, int documentY, int deltaDocumentX, int deltaDocumentY, int button,
            long timeNanos, DragPhase phase) {
        this.target = target;
        this.currentTarget = currentTarget;
        this.startDocumentX = startDocumentX;
        this.startDocumentY = startDocumentY;
        this.documentX = documentX;
        this.documentY = documentY;
        this.deltaDocumentX = deltaDocumentX;
        this.deltaDocumentY = deltaDocumentY;
        this.button = button;
        this.timeNanos = timeNanos;
        this.phase = phase;
    }

    public ElementNode getTarget() {
        return target;
    }

    public ElementNode getCurrentTarget() {
        return currentTarget;
    }

    public int getStartDocumentX() {
        return startDocumentX;
    }

    public int getStartDocumentY() {
        return startDocumentY;
    }

    public int getDocumentX() {
        return documentX;
    }

    public int getDocumentY() {
        return documentY;
    }

    public int getDeltaDocumentX() {
        return deltaDocumentX;
    }

    public int getDeltaDocumentY() {
        return deltaDocumentY;
    }

    public int getButton() {
        return button;
    }

    public long getTimeNanos() {
        return timeNanos;
    }

    public DragPhase getPhase() {
        return phase;
    }

    /**
     * 拖拽阶段。
     */
    public enum DragPhase {
        START,
        DRAG,
        END
    }
}
