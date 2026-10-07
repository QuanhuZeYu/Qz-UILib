package club.heiqi.uilib.ui.scene.runtime;
/** 挂载期激活，卸载释放，重挂载重新读取声明来源。 */
public interface BindingActivation extends AutoCloseable {
    void refresh();
    @Override void close();
}
