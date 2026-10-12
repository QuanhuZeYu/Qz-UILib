package club.heiqi.uilib.ui.scene.control;

/** 应用级深浅色选择；控件仍可声明属于自己的局部样式。 */
public enum ThemeMode {
    LIGHT, DARK;

    public boolean isDark() { return this == DARK; }
    public SceneTheme sceneTheme() { return isDark() ? SceneTheme.dark() : SceneTheme.light(); }
}
