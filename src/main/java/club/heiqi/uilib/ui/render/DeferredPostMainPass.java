package club.heiqi.uilib.ui.render;

/**
 * 延迟到主渲染完成后再执行的回放记录。
 *
 * <p>这里保留 clip/scissor 快照，让宿主能够把同一批补充绘制
 * 回放到第二个 FBO，再按既有 alpha 合成契约贴回主 UI FBO。</p>
 */
public final class DeferredPostMainPass {

    private final DeferredPostMainPassReplay replay;
    private final ClipSnapshot clipSnapshot;

    DeferredPostMainPass(DeferredPostMainPassReplay replay, ClipSnapshot clipSnapshot) {
        this.replay = replay;
        this.clipSnapshot = clipSnapshot;
    }

    public void replay() {
        replay.replay();
    }

    public ClipSnapshot getClipSnapshot() {
        return clipSnapshot;
    }
}
