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
    /** 宿主只提供文件事实入口；选择界面由共享页面的正式 SceneFilePicker 承担。 */
    default java.nio.file.Path initialMediaDirectory() {
        return java.nio.file.Paths.get(System.getProperty("user.home", ".")).toAbsolutePath().normalize();
    }
    default club.heiqi.uilib.ui.media.MediaSource exampleSource() throws java.io.IOException {
        throw new java.io.IOException("当前入口没有内置示例");
    }
}
