package club.heiqi.config.ui.editor;

/**
 * 列表成员与 picker 选择模型之间的平台无关转换契约。
 *
 * <p>列表成员绑定必须显式实现本接口，不会退回到整组值的静默转换。</p>
 */
public interface ListMemberCodec extends Codec {
    /**
     * 解码单个原始列表成员。
     *
     * @param rawMember 原始列表成员
     * @return 当前选择；成员格式错误时返回 null
     */
    SearchPickerData.Selection decodeMember(Object rawMember);

    /**
     * 基于确认瞬间的原始成员编码新成员。
     *
     * @param currentRawMember 确认瞬间的原始列表成员
     * @param selection 经强校验的不可变选择
     * @return 编码后的成员，null 表示拒绝写入
     */
    Object encodeMember(Object currentRawMember, SearchPickerData.Selection selection);
}
