package club.heiqi.config.ui.editor;

/** 一个可注册的配置值 editor 契约集合。 */
public interface ValueEditorProvider {
    /** @return namespaced editor id */
    String id();

    /** @return 值转换器 */
    Codec codec();

    /** @return 纯展示适配器 */
    VisualAdapter visualAdapter();
}
