package club.heiqi.uilib.ui.hud.api;

/** HUD 的标准可见性策略。 */
public enum HudVisibility {
    /** 仅在世界内且没有打开普通 GuiScreen 时显示。 */
    GAMEPLAY_ONLY,
    /** 在世界内显示，包括打开普通 GuiScreen 时。 */
    IN_WORLD
}
