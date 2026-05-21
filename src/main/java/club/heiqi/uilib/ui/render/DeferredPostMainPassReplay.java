package club.heiqi.uilib.ui.render;

/**
 * 主 UI FBO 完成后的补充回放动作。
 */
public interface DeferredPostMainPassReplay {

    /**
     * 在第二个 FBO 中回放当前动作。
     */
    void replay();
}
