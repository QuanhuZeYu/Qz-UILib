package club.heiqi.config.ui.editor;

/** 配置值与 editor 选择模型之间的平台无关转换契约。 */
public interface Codec {
    /** 将配置值解码为当前选择；返回 null 表示配置值无法表达为有效选择。 */
    SearchPickerData.Selection decode(Object value);

    /** 将经强校验的不可变选择编码为配置值；实现可拒绝不支持的选择模式。 */
    Object encode(SearchPickerData.Selection selection);
}
