package club.heiqi.uilib.ui.scene.layout;

import club.heiqi.uilib.ui.scene.node.SceneNode;

/** 框架求解后提供的当前分配；目标求解不消费该投影。 */
public interface SceneLayoutPresentation {
    Size size(SceneNode node, SceneNode.Axis axis);
    LayoutBox place(SceneNode node, LayoutBox allocated);
}
