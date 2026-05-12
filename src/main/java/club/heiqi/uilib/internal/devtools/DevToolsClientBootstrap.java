package club.heiqi.uilib.internal.devtools;

import net.minecraftforge.client.ClientCommandHandler;

/**
 * 内部开发工具客户端装配入口。
 */
public final class DevToolsClientBootstrap {

    private static final QzUiLibClientCommand QZ_UI_LIB_CLIENT_COMMAND = new QzUiLibClientCommand();

    private DevToolsClientBootstrap() {}

    /**
     * 注册内部开发工具所需的客户端能力。
     */
    public static void registerClientDevTools() {
        ClientCommandHandler.instance.registerCommand(QZ_UI_LIB_CLIENT_COMMAND);
    }
}
