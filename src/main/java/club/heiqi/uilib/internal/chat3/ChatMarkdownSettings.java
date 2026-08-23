package club.heiqi.uilib.internal.chat3;

/**
 * 聊天系统 3.0 配置(进程级开关;观感参数在 S2/S3 阶段按设计规格补充)。
 *
 * <p>关闭后安装器把原版实例写回 GuiIngame.persistantChatGUI,原版对话框整套回归(逃生舱语义,
 * 用户裁决)。默认开。</p>
 */
public final class ChatMarkdownSettings {

    /** 聊天 3.0 接管总开关(默认开;off = 逃生舱,回退原版整套)。 */
    private static volatile boolean enabled = true;

    private ChatMarkdownSettings() {
    }

    /** @return 聊天 3.0 接管是否启用 */
    public static boolean isEnabled() {
        return enabled;
    }

    /** 设置聊天 3.0 接管开关(下一渲染帧生效)。 */
    public static void setEnabled(boolean value) {
        enabled = value;
    }
}
