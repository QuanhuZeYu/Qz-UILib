package club.heiqi.uilib.host.client.localization;

import java.util.Locale;
import club.heiqi.uilib.ui.scene.localization.LocalizationSnapshot;
import club.heiqi.uilib.ui.scene.localization.LocalizationSource;
import net.minecraft.client.Minecraft;

/** 只消费MC已完成的语言加载，不注册另一reload listener争夺顺序，不逐帧轮询。
 * client init捕获启动期已加载表，之后LanguageManager TAIL捕获，MC重载/tick出口交付；退出释放共享订阅。 */
public final class MinecraftLocalizationServices {
    private static MinecraftLocalizationSource shared;
    private static Thread thread;
    private static boolean installed;
    private MinecraftLocalizationServices() {}
    public static void installShared() {
        if (installed) { checkThread(); if (shared == null) throw new IllegalStateException("客户端语言owner已退出"); return; }
        thread = Thread.currentThread();
        shared = new MinecraftLocalizationSource(capture((LanguageManagerAccess) Minecraft.getMinecraft().getLanguageManager()));
        installed = true;
    }
    /** 原版Locale加载及Forge合并均完成后调用；启动期尚未install和退出后的回调不建立新owner。 */
    public static void loaded(LanguageManagerAccess manager) {
        if (shared == null) return;
        checkThread(); shared.loaded(manager);
    }
    /** 原版资源恢复catch外的正常生命周期入口；不依赖页面frame。 */
    public static void dispatchPending() {
        if (shared == null) return;
        checkThread(); shared.dispatchPending();
    }
    /** 服务及完成事件共用快照入口；format locale采用原版String.format所在的Java格式环境。 */
    public static LocalizationSnapshot capture(LanguageManagerAccess manager) {
        return new LocalizationSnapshot(manager.qz$languageCode(), manager.qz$translations(), Locale.getDefault(Locale.Category.FORMAT));
    }
    public static LocalizationSource shared() {
        checkThread(); if (shared == null) throw new IllegalStateException("客户端语言资料尚未安装/已关闭"); return shared;
    }
    private static void checkThread() { if (Thread.currentThread() != thread) throw new IllegalStateException("MC语言资料仅限客户端主线程"); }
    public static void closeShared() {
        if (shared == null) return;
        checkThread(); MinecraftLocalizationSource owner = shared; shared = null; owner.close();
    }
}
