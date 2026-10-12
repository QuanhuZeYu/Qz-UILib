package club.heiqi.uilib.ui.scene.host;

/** 正式页面定义；构造发生在合法状态域，页面只声明状态、内容与策略。 */
@FunctionalInterface
public interface ScenePage {
    void build(ScenePageContext page);
}
