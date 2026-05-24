package club.heiqi.uilib.internal.devtools;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.ui.remote.RemoteDocumentPage;
import club.heiqi.uilib.ui.remote.RemoteDocumentResourcePolicy;
import club.heiqi.uilib.ui.remote.RemoteHudOverlay;
import club.heiqi.uilib.ui.remote.RemoteHudOverlays;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;

/**
 * 生日分支专用的远程 HUD 主动推送控制器。
 *
 * <p>该控制器硬编码监听 {@code Quanhu_Zeyu} 进入服务器，并在约 5 秒后由服务端主动下发远程 HUD
 * 浮层。该能力只用于当前实践分支，不作为长期公共入口。</p>
 */
public final class BirthdayRemotePushController {

    private static final BirthdayRemotePushController INSTANCE = new BirthdayRemotePushController();
    private static final String TARGET_PLAYER_NAME = "Quanhu_Zeyu";
    private static final int BIRTHDAY_DELAY_TICKS = 100;

    private final List<ScheduledBirthdayPush> scheduledPushes = new ArrayList<ScheduledBirthdayPush>();

    private BirthdayRemotePushController() {}

    /**
     * 返回生日推送控制器单例。
     *
     * @return 控制器实例
     */
    public static BirthdayRemotePushController getInstance() {
        return INSTANCE;
    }

    /**
     * 玩家登录后，如果命中硬编码昵称，则排队等待 5 秒后推送生日 HUD。
     *
     * @param event 玩家登录事件
     */
    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event == null || !isTargetPlayer(event.player)) {
            return;
        }
        scheduledPushes.add(new ScheduledBirthdayPush(event.player, BIRTHDAY_DELAY_TICKS));
        MyMod.LOG.info("已为 {} 排队生日远程 HUD 推送，延迟 {} tick。", TARGET_PLAYER_NAME,
                Integer.valueOf(BIRTHDAY_DELAY_TICKS));
    }

    /**
     * 服务端 tick 末尾推进倒计时，倒计时结束后在服务端主线程下发远程 HUD。
     *
     * @param event 服务端 tick 事件
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || scheduledPushes.isEmpty()) {
            return;
        }
        for (int index = scheduledPushes.size() - 1; index >= 0; index--) {
            ScheduledBirthdayPush push = scheduledPushes.get(index);
            push.remainingTicks--;
            if (push.remainingTicks <= 0) {
                scheduledPushes.remove(index);
                openBirthdayOverlays(push.player);
            }
        }
    }

    /**
     * 判断玩家是否为本分支硬编码的祝福目标。
     *
     * @param player 玩家对象
     * @return 是否命中目标昵称
     */
    private boolean isTargetPlayer(EntityPlayer player) {
        return player != null && TARGET_PLAYER_NAME.equalsIgnoreCase(player.getCommandSenderName());
    }

    /**
     * 向目标玩家推送生日对话框、提示和弹幕。
     *
     * @param player 目标玩家
     */
    private void openBirthdayOverlays(EntityPlayer player) {
        if (!isTargetPlayer(player)) {
            return;
        }
        try {
            RemoteHudOverlays.open(player, buildBirthdayDialogOverlay(), null);
            RemoteHudOverlays.open(player, buildBirthdayToastOverlay(), null);
            RemoteHudOverlays.open(player, buildBirthdayDanmakuOverlay("qz-birthday-danmaku-a",
                    "Happy Birthday, Quanhu_Zeyu. 服务器远程 HUD 已准时送达。"), null);
            RemoteHudOverlays.open(player, buildBirthdayDanmakuOverlay("qz-birthday-danmaku-b",
                    "愿今天的合成表全是好运，生日快乐。"), null);
            MyMod.LOG.info("已向 {} 推送生日远程 HUD。", TARGET_PLAYER_NAME);
        } catch (IllegalArgumentException exception) {
            MyMod.LOG.warn("生日远程 HUD 参数异常", exception);
        } catch (IllegalStateException exception) {
            MyMod.LOG.warn("生日远程 HUD 当前不可用", exception);
        }
    }

    /**
     * 创建生日主对话框浮层。
     *
     * @return 生日对话框浮层
     */
    private RemoteHudOverlay buildBirthdayDialogOverlay() {
        return RemoteHudOverlay.dialog("qz-birthday-dialog", buildBirthdayDialogPage())
                .closeButtonLabel("收下祝福")
                .metadata("target", TARGET_PLAYER_NAME)
                .build();
    }

    /**
     * 创建生日角落提示浮层。
     *
     * @return 生日提示浮层
     */
    private RemoteHudOverlay buildBirthdayToastOverlay() {
        return RemoteHudOverlay.toast("qz-birthday-toast", RemoteDocumentPage.builder("qz-birthday-toast-page")
                .title("生日提示")
                .resourcePolicy(RemoteDocumentResourcePolicy.LOCAL_RESOURCES_ONLY)
                .metadata("target", TARGET_PLAYER_NAME)
                .html("<div style=\"box-sizing:border-box;padding:8px 12px;background-color:#073344;"
                        + "color:#e0f2fe;border:1px solid #22d3ee;border-radius:6px;\">"
                        + "生日远程 HUD 已由服务器主动送达。</div>")
                .build())
                .durationMillis(6500L)
                .build();
    }

    /**
     * 创建生日弹幕浮层。
     *
     * @param overlayId 浮层标识
     * @param message 弹幕文案
     * @return 生日弹幕浮层
     */
    private RemoteHudOverlay buildBirthdayDanmakuOverlay(String overlayId, String message) {
        return RemoteHudOverlay.danmaku(overlayId, RemoteDocumentPage.builder(overlayId + "-page")
                .title("生日弹幕")
                .resourcePolicy(RemoteDocumentResourcePolicy.LOCAL_RESOURCES_ONLY)
                .metadata("target", TARGET_PLAYER_NAME)
                .html("<span style=\"box-sizing:border-box;padding:6px 10px;background-color:#3b1f14;"
                        + "color:#ffedd5;border:1px solid #fb923c;border-radius:6px;\">"
                        + escapeHtml(message) + "</span>")
                .build())
                .durationMillis(9000L)
                .build();
    }

    /**
     * 创建生日主对话框页面。
     *
     * @return 生日页面
     */
    private RemoteDocumentPage buildBirthdayDialogPage() {
        return RemoteDocumentPage.builder("qz-birthday-dialog-page")
                .title("Quanhu_Zeyu 生日快乐")
                .resourcePolicy(RemoteDocumentResourcePolicy.LOCAL_RESOURCES_ONLY)
                .metadata("target", TARGET_PLAYER_NAME)
                .html(buildBirthdayDialogHtml())
                .build();
    }

    /**
     * 生成生日主对话框 HTML。
     *
     * @return HTML 文本
     */
    private String buildBirthdayDialogHtml() {
        return "<html><head><title>Quanhu_Zeyu 生日快乐</title><style>"
                + ".card{box-sizing:border-box;width:100%;background-color:#101820;color:#f8fafc;"
                + "border:1px solid #f59e0b;border-radius:8px;overflow:hidden;}"
                + ".drag{box-sizing:border-box;width:100%;padding:10px 56px 10px 14px;"
                + "background-color:#0f766e;color:#ecfeff;cursor:move;}"
                + ".body{box-sizing:border-box;padding:16px;background-color:#172033;}"
                + ".title{margin:0 0 8px 0;color:#fde68a;}"
                + ".line{margin:6px 0;color:#e2e8f0;}"
                + ".chips{display:flex;flex-direction:row;gap:8px;margin-top:12px;}"
                + ".chip{padding:6px 8px;border:1px solid #fb7185;border-radius:6px;color:#ffe4e6;}"
                + ".note{margin-top:12px;padding:10px;background-color:#1f2937;color:#c7d2fe;"
                + "border:1px solid #818cf8;border-radius:6px;}"
                + "</style></head><body><div class=\"card\">"
                + "<div class=\"drag\" data-qz-hud-drag-handle=\"true\">Qz UILib 远程 HUD · 生日派送</div>"
                + "<div class=\"body\"><h1 class=\"title\">生日快乐, Quanhu_Zeyu</h1>"
                + "<p class=\"line\">服务器在你进入世界 5 秒后主动送来了这份祝福。</p>"
                + "<p class=\"line\">这不是客户端脚本，也不是浏览器嵌入；它来自 Qz UILib 的安全 HTML 子集、"
                + "远程 Stream 拉取和 HUD top-layer 渲染。</p>"
                + "<div class=\"chips\"><span class=\"chip\">Remote HUD</span>"
                + "<span class=\"chip\">Server Push</span><span class=\"chip\">Birthday Build</span></div>"
                + "<p class=\"note\">愿今天的矿脉更近一点，机器更听话一点，快乐直接满格。</p>"
                + "</div></div></body></html>";
    }

    /**
     * HTML 转义普通文本。
     *
     * @param value 原始文本
     * @return 转义后的文本
     */
    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * 等待推送的玩家与剩余 tick 计数。
     */
    private static final class ScheduledBirthdayPush {

        private final EntityPlayer player;
        private int remainingTicks;

        private ScheduledBirthdayPush(EntityPlayer player, int remainingTicks) {
            this.player = player;
            this.remainingTicks = remainingTicks;
        }
    }
}
