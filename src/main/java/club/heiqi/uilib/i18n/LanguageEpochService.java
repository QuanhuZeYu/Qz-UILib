package club.heiqi.uilib.i18n;

import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import club.heiqi.uilib.resource.ResourceReloadService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.Language;

/**
 * LanguageEpochService —— UILib 通用语言代际通道（进程单例，包名写死 {@code club.heiqi.uilib.i18n}）。
 *
 * <p>契约出处：{@code team/P0-ADR-契约与测量.md} §2.1 #2（S-03 包名 / S-04 主通道 / T-5 兜底）。
 * 现状 UILib 无任何语言事件，但 1.7.10 的语言切换主通道已由源码证实覆盖资源重载：
 * {@code GuiLanguage.setCurrentLanguage} → {@code Minecraft.refreshResources()} →
 * {@code SimpleReloadableResourceManager.reloadResources} → {@code notifyReloadListeners}
 * → 本服务（挂在 {@link ResourceReloadService} 上）比较语言码后推进 {@link #nameEpoch()}。</p>
 *
 * <h3>语义</h3>
 * <ul>
 *   <li>{@link #nameEpoch()}：单调不减的 {@code long}，只在语言码<b>变化</b>时 +1；
 *       资源重载但语言未变时不变（避免「资源包切换 = 文本代际变化」的伪失效）。</li>
 *   <li>有界性：单例 + 1 个 long + 1 个字符串；无每帧成本（读取是 O(1) long 读）。</li>
 *   <li>本服务不是 signal 通道：需要响应语言变化的 UILib 侧消费者按帧/按需比对代际
 *       （候选源经 {@code PickerCandidateSource#onEnvironmentChanged} 下行）。</li>
 * </ul>
 *
 * <h3>过渡态与删除条件（T-5）</h3>
 * <p>语言切换的「语言码兜底探测」（每 20 tick 读一次语言码）<b>本轮不启用</b>：主通道已用 1.7.10
 * 源码证实覆盖 {@code GuiLanguage} 路径。仅当真机验证存在「不触发 reload 的切换路径」
 * （如 {@code I18n} 表热替换）时才启用该兜底，并须同时写明其失效条件；否则本通道即为终态。</p>
 */
public final class LanguageEpochService {

    private static final Logger LOG = LogManager.getLogger("QzUiLib/I18n");

    private static final LanguageEpochService INSTANCE = new LanguageEpochService();

    private volatile long nameEpoch;
    private volatile String lastLanguageCode;
    private volatile boolean installed;
    private volatile Supplier<String> languageCodeSupplier = LanguageEpochService::currentLanguageCode;

    private LanguageEpochService() {
    }

    /** @return 进程单例 */
    public static LanguageEpochService getInstance() {
        return INSTANCE;
    }

    /** @return 当前文本（语言）代际（单调不减；O(1) long 读） */
    public long nameEpoch() {
        return nameEpoch;
    }

    /** @return 是否已挂到资源重载通道 */
    public boolean isInstalled() {
        return installed;
    }

    /**
     * 客户端装配期调用：挂到 {@link ResourceReloadService} 并记录当前语言码（幂等）。
     *
     * @return 本次是否完成安装
     */
    public boolean install() {
        if (installed) {
            return false;
        }
        installed = true;
        lastLanguageCode = languageCodeSupplier.get();
        ResourceReloadService.getInstance().addListener(epoch -> onResourceReload());
        return true;
    }

    /**
     * 重载后比对语言码：变化则推进文本代际。
     *
     * @return 语言码是否发生变化（推进发生）
     */
    public boolean onResourceReload() {
        String current = languageCodeSupplier.get();
        String previous = lastLanguageCode;
        if (current == null) {
            return false;
        }
        if (current.equals(previous)) {
            return false;
        }
        lastLanguageCode = current;
        nameEpoch++;
        return true;
    }

    /** @return 客户端当前语言码；无客户端实例时返回 null（无从判定） */
    public static String currentLanguageCode() {
        try {
            Minecraft minecraft = Minecraft.getMinecraft();
            if (minecraft == null || minecraft.getLanguageManager() == null) {
                return null;
            }
            Language language = minecraft.getLanguageManager().getCurrentLanguage();
            if (language == null) {
                return null;
            }
            String code = language.getLanguageCode();
            if (code == null || code.isEmpty()) {
                LOG.warn("语言码缺失：按未变化处理（不推进文本代际）");
                return null;
            }
            return code;
        } catch (Throwable unavailable) {
            // headless 测试 JVM 下 Minecraft 静态初始化失败（LWJGL 链接缺失）、服务端 JVM 下客户端类缺席：
            // 语言码不可得 = 不推进代际（fail-safe，不伪造语言变化）。
            return null;
        }
    }

    /** 测试用：安装语言码供给器（模拟语言切换；生产路径不调用）。 */
    public void __installLanguageCodeSupplierForTests(Supplier<String> supplier) {
        this.languageCodeSupplier = supplier == null ? LanguageEpochService::currentLanguageCode : supplier;
    }

    /** 测试用：复位代际、安装状态与语言码（不清理 ResourceReloadService）。 */
    public void __resetForTests() {
        nameEpoch = 0L;
        installed = false;
        lastLanguageCode = null;
        languageCodeSupplier = LanguageEpochService::currentLanguageCode;
    }
}
