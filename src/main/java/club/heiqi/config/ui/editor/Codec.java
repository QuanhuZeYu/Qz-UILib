package club.heiqi.config.ui.editor;

/** 配置值与 editor 选择模型之间的平台无关转换契约。 */
public interface Codec {
    /** 将配置值解码为选择。 */
    SearchPickerData.Selection decode(Object value);

    /** 将选择编码为配置值。 */
    Object encode(SearchPickerData.Selection selection);
}
