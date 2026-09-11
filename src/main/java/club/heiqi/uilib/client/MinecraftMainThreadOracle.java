package club.heiqi.uilib.client;

import club.heiqi.config.ui.field.PickerSourceGuard;
import net.minecraft.client.Minecraft;

/**
 * MinecraftMainThreadOracle —— 候选源线程断言的客户端判定源（客户端装配期注入）。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §1.3。判定口径与
 * {@code club.heiqi.uilib.client.hud.ClientHudServiceImpl.assertClientThread} 同源：
 * 1.7.10 原版「当前线程 == 客户端线程」判定。该 API 在 MCP stable 12 的 srg→mcp 映射里
 * <b>未映射</b>（srg-mcp.srg 中 func_152345_ab → 自身），故源码按 SRG 名调用——这也是 reobf
 * 期望的形态；O(1)、无分配。</p>
 *
 * <p>无客户端实例（headless 单测/服务端 JVM）时返回「是主线程」：此时无从判定，
 * 与 {@link PickerSourceGuard} 的降级语义一致；生产路径恒有客户端实例。</p>
 */
public final class MinecraftMainThreadOracle implements PickerSourceGuard.ThreadOracle {

    @Override
    public boolean isMainThread() {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft == null) {
                return true;
            }
            return minecraft.func_152345_ab();
        } catch (Throwable unavailable) {
            // 无客户端运行时（headless 测试 JVM / 服务端 JVM）：无从判定 → 与守卫的降级语义一致地放行。
            // 生产客户端路径恒有可用实例，故该兜底不会掩盖真实误用。
            return true;
        }
    }

    @Override
    public String describe() {
        return "Minecraft.func_152345_ab (isCallingFromMinecraftThread)";
    }
}
