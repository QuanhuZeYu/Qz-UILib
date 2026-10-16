package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.settings.SettingsConfiguration;

/** 场景工作台借用当前宿主的正式配置，同域构建共享设置页。 */
@FunctionalInterface
public interface GalleryEnvironment {
    SettingsConfiguration settings();
    /** 未装配媒体的旧入口仍可浏览同一目录。 */
    default club.heiqi.uilib.ui.media.MediaService media() { return null; }
    default club.heiqi.uilib.ui.media.MediaSource resolveSource(String text) {
        return club.heiqi.uilib.ui.media.MediaSource.of(java.net.URI.create(text.trim()));
    }
    /** 取消返回null，页面保留当前来源和播放；系统选择器由宿主提供。 */
    default club.heiqi.uilib.ui.media.MediaSource chooseMediaSource() throws Exception {
        throw new java.io.IOException("当前入口不支持选择本地视频");
    }
    default club.heiqi.uilib.ui.media.MediaSource exampleSource() throws java.io.IOException {
        throw new java.io.IOException("当前入口没有内置示例");
    }
}
