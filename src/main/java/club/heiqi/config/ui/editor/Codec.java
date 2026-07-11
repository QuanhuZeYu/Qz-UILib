package club.heiqi.config.ui.editor;

/** 配置值与 editor 选择模型之间的平台无关转换契约。 */
public interface Codec {
    /** 将配置值解码为当前选择；返回 null 表示配置值无法表达为有效选择。 */
    SearchPickerData.Selection decode(Object value);

    /**
     * 根据确认瞬间的当前配置值，将经强校验的不可变选择编码为新配置值。
     * 实现不得依赖 codec 内的业务可变状态。
     *
     * @param currentValue 确认瞬间的当前配置值
     * @param selection 经强校验的不可变选择
     * @return 编码后的配置值，null 表示拒绝写入
     */
    default Object encode(Object currentValue, SearchPickerData.Selection selection) {
        return encode(selection);
    }

    /**
     * 将经强校验的不可变选择编码为配置值。
     *
     * @param selection 经强校验的不可变选择
     * @return 编码后的配置值，null 表示拒绝写入
     * @deprecated 新实现应实现 {@link #encode(Object, SearchPickerData.Selection)}，显式接收当前配置值
     */
    @Deprecated
    default Object encode(SearchPickerData.Selection selection) {
        throw new UnsupportedOperationException("single-argument encode is not implemented");
    }
}
