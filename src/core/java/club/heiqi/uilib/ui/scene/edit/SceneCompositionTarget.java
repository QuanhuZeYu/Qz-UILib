package club.heiqi.uilib.ui.scene.edit;

/** 编辑控件向唯一输入路由声明其业务持有的状态，不创建第二草稿或焦点。 */
public interface SceneCompositionTarget {
    SceneEditState compositionState();
}
