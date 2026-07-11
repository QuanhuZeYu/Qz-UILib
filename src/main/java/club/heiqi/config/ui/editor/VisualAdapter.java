package club.heiqi.config.ui.editor;

import club.heiqi.uilib.ui.scene.image.SceneImageSource;

/** editor 领域对象到纯展示数据的适配契约。 */
public interface VisualAdapter {
    /** 返回候选图片；默认无图片。 */
    default SceneImageSource candidateImage(SearchPickerData.Candidate candidate) { return null; }

    /** 返回变体图片；默认无图片。 */
    default SceneImageSource variantImage(SearchPickerData.Variant variant) { return null; }
    /** 返回候选展示文本。 */
    String candidateLabel(SearchPickerData.Candidate candidate);

    /** 返回变体展示文本。 */
    String variantLabel(SearchPickerData.Variant variant);
}
