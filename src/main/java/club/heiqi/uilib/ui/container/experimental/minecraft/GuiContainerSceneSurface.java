package club.heiqi.uilib.ui.container.experimental.minecraft;

import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneMouseButton;
import club.heiqi.uilib.ui.scene.input.ScenePointerAction;
import club.heiqi.uilib.ui.scene.paint.PaintPlan;

/** 平台无关 scene 与 Minecraft `GuiContainer` host 之间的分相合同。 */
public interface GuiContainerSceneSurface {
    /** 只读 DOWN 命中与语义裁决，不提交 intent 或写 signal。 */
    ContainerInputClaim claimDown(int logicalX, int logicalY, SceneMouseButton button,
            boolean shiftDown, boolean controlDown);

    /** 向 DOWN 时保存的唯一 claim 派发 pointer 边沿。 */
    void dispatchClaimedPointer(ContainerInputClaim claim, ScenePointerAction action,
            int logicalX, int logicalY, SceneMouseButton button,
            boolean shiftDown, boolean controlDown);

    /** 只读键盘 activation 裁决。 */
    ContainerInputClaim claimKey(SceneKey key, boolean shiftDown, boolean controlDown);

    /** 派发刚才返回的完整 key claim，不重新命中。 */
    void dispatchClaimedKey(ContainerInputClaim claim, SceneKey key,
            boolean shiftDown, boolean controlDown);

    /** 返回 confirmed request gate，仅供 host 诊断；claim/dispatch 语义仍由 surface 裁决。 */
    boolean isPending();

    /** 驱动 logical-px scene frame 并生成位于 vanilla background 后的自包含主树计划。 */
    PaintPlan paintMain(int logicalWidth, int logicalHeight,
            int logicalMouseX, int logicalMouseY, float partialTicks);

    /** 生成位于 Slot/foreground 后、carried 前的自包含 overlay 计划。 */
    PaintPlan paintOverlay();

    /** host detach 时释放 scene 资源。 */
    void dispose();
}
