package club.heiqi.config.ui.editor;

import club.heiqi.uilib.ui.scene.image.SceneImageSource;

/** 将 editor 当前值投影为平台无关的紧凑展示，不参与值编解码。 */
public interface CurrentValuePresenter {
    /**
     * 生成当前值展示。
     *
     * @param value 当前配置值
     * @return 展示快照；无法展示时返回 null
     */
    Presentation present(Object value);

    /** 不可变的当前值展示快照。 */
    final class Presentation {
        private final String title;
        private final String summary;
        private final SceneImageSource image;

        /** 创建展示快照。 */
        public Presentation(String title, String summary, SceneImageSource image) {
            this.title = title == null ? "" : title;
            this.summary = summary == null ? "" : summary;
            this.image = image;
        }

        /** @return 主标题 */
        public String title() { return title; }
        /** @return 摘要 */
        public String summary() { return summary; }
        /** @return 可选图片 */
        public SceneImageSource image() { return image; }
    }
}
