package club.heiqi.uilib.ui.scene.layout;

import club.heiqi.uilib.ui.scene.node.SceneNode;

/** scene布局批次的直属子项测量接缝；独立字体服务不持节点或参与scene生命周期。 每个子项使用自己的同代测量请求缓存，避免父子交替请求使公式在纯滚动中重排。 */
public interface SceneChildMeasurer {
    MeasuredSize measureChild(SceneNode child, Constraints constraints);
}
