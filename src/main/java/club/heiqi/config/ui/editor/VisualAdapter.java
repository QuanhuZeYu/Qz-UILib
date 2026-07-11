package club.heiqi.config.ui.editor;

/** editor 领域对象到纯展示数据的适配契约。 */
public interface VisualAdapter {
    /** 返回候选展示文本。 */
    String candidateLabel(SearchPickerData.Candidate candidate);

    /** 返回变体展示文本。 */
    String variantLabel(SearchPickerData.Variant variant);
}
