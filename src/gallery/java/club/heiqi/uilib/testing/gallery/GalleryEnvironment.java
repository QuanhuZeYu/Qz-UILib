package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.settings.SettingsConfiguration;

/** 场景工作台借用当前宿主的正式配置，同域构建共享设置页。 */
@FunctionalInterface
public interface GalleryEnvironment {
    SettingsConfiguration settings();
}
