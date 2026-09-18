package club.heiqi.uilib.client;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Angelica HUD 缓存抑制器（宿主兼容层）：让宿主的 HUD 回到原版渲染路径。
 *
 * <h3>要解决的问题</h3>
 * <p>Angelica 的 HUD caching 会把整个 HUD 渲染进<b>独立的 HUD framebuffer</b>，并按
 * {@code hudCachingFPS}（默认 20）复用像素。而 UILib 的背景滤镜（液态玻璃）需要把「当前主层画面」
 * 抓成快照再模糊合成——{@code UiBackdropFilterRenderer} 经
 * {@code UiRenderContext#getCurrentBackdropReadFramebufferId()} 取源，无隔离层时回落到
 * <b>当前 read framebuffer</b>（{@code UiMainLayerSnapshotService#resolveReadFramebufferId}）。
 * HUD 缓存 FBO 里没有世界画面 ⇒ 玻璃采样到空/黑，HUD 卡片呈黑底；而打开 GUI 时缓存路径不生效，
 * 同一张卡片正常透明。真机 GTNH 2.8.4 + Angelica {@code 1.0.0-beta66b} 已复现：把
 * {@code config/angelica-modules.cfg} 的 {@code enableHudCaching} 改 false 后恢复正常，
 * 故本类把这个动作自动化并强制化，不再依赖用户改配置文件。</p>
 *
 * <h3>为什么改运行期字段而不是配置</h3>
 * <ul>
 *   <li><b>Angelica ≥ 2.1.x</b>：{@code AngelicaConfig.hudCachingActive} 是宿主自带的<b>运行时</b>
 *       开关，{@code HUDCaching.renderCachedHud} 首段读它，为 false 时直接走原版
 *       {@code GuiIngame.renderGameOverlay}（渲染到当前 framebuffer）；</li>
 *   <li><b>Angelica 1.0.0-betaXX</b>（GTNH 2.8.0 / 2.8.4）：没有运行时开关，
 *       {@code enableHudCaching} 只在启动期决定 mixin / event transformer 是否加载，运行期改它
 *       无效；但 {@code HUDCaching.renderCachedHud} 入口带 {@code framebuffer != null} 降级分支
 *       （为 null 即走原版路径），故置 {@code HUDCaching.framebuffer = null}。该字段只有
 *       {@code HUDCaching} 与 {@code MixinFramebuffer_HUDCaching} 读写，后者仅在
 *       {@code renderingCacheOverride} 为真（缓存渲染块内）时解引用，而缓存渲染块在
 *       framebuffer 为 null 时根本不会进入。</li>
 * </ul>
 *
 * <p>探测只看字段是否存在、不看版本号：字段缺失即不干预，未来版本重构时既不会崩也不会误改；
 * 反射一律 {@code initialize=false}，不主动触发宿主类初始化；所有失败都收敛为一次性日志。</p>
 *
 * <p><b>已知代价</b>：HUD 不再按 {@code hudCachingFPS} 复用像素，改为每帧重绘——用宿主的一项性能
 * 优化换 UILib 玻璃材质的正确性（强制、不提供配置开关）。</p>
 */
public final class AngelicaHudCachingSuppressor {

    private static final Logger LOG = LogManager.getLogger("QzUiLib/AngelicaHudCachingSuppressor");
    private static final String CONFIG_CLASS_NAME = "com.gtnewhorizons.angelica.config.AngelicaConfig";
    private static final String HUD_CACHING_CLASS_NAME = "com.gtnewhorizons.angelica.hudcaching.HUDCaching";
    private static final String RUNTIME_ACTIVE_FIELD = "hudCachingActive";
    private static final String CACHE_FRAMEBUFFER_FIELD = "framebuffer";

    private boolean probed;
    private boolean reported;
    private boolean stopped;
    private Field runtimeActiveField;
    private Field cacheFramebufferField;

    /**
     * 每个客户端 tick 守卫一次。
     *
     * <p>必须可重复施加：宿主在进世界（{@code WorldEvent.Load}）时会重建 HUD 缓存 framebuffer，
     * 而配置型开关也可能被宿主自身的选项界面改回去。</p>
     *
     * @param event 客户端 tick 事件
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.END) {
            return;
        }
        suppress();
    }

    /**
     * 让 Angelica 的 HUD 缓存失效（幂等；未安装 Angelica 时零动作）。
     */
    void suppress() {
        if (stopped) {
            return;
        }
        if (!probed) {
            probe();
        }
        try {
            if (runtimeActiveField != null) {
                if (runtimeActiveField.getBoolean(null)) {
                    runtimeActiveField.setBoolean(null, false);
                    reportSuppressed(RUNTIME_ACTIVE_FIELD);
                }
                return;
            }
            if (cacheFramebufferField != null) {
                if (cacheFramebufferField.get(null) != null) {
                    cacheFramebufferField.set(null, null);
                    reportSuppressed(CACHE_FRAMEBUFFER_FIELD);
                }
                return;
            }
            reportAbsent();
            stopped = true;
        } catch (Throwable failure) {
            stopped = true;
            LOG.warn("[宿主兼容] Angelica HUD 缓存抑制失败，已停止尝试（HUD 玻璃可能回退为黑底）：{}",
                    String.valueOf(failure));
        }
    }

    /** 一次性探测宿主入口：只看字段存在性，不初始化宿主类。 */
    private void probe() {
        probed = true;
        ClassLoader loader = AngelicaHudCachingSuppressor.class.getClassLoader();
        Class<?> configClass = loadClass(CONFIG_CLASS_NAME, loader);
        if (configClass != null) {
            runtimeActiveField = staticField(configClass, RUNTIME_ACTIVE_FIELD, boolean.class);
        }
        Class<?> hudCachingClass = loadClass(HUD_CACHING_CLASS_NAME, loader);
        if (hudCachingClass != null) {
            cacheFramebufferField = staticField(hudCachingClass, CACHE_FRAMEBUFFER_FIELD, null);
        }
    }

    private static Class<?> loadClass(String name, ClassLoader loader) {
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException | LinkageError absent) {
            return null;
        }
    }

    private static Field staticField(Class<?> owner, String name, Class<?> expectedType) {
        try {
            Field field = owner.getDeclaredField(name);
            if (!Modifier.isStatic(field.getModifiers())) {
                return null;
            }
            if (expectedType != null && field.getType() != expectedType) {
                return null;
            }
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException | RuntimeException absent) {
            return null;
        }
    }

    private void reportSuppressed(String fieldName) {
        if (reported) {
            return;
        }
        reported = true;
        LOG.info("[宿主兼容] 已停用 Angelica HUD 缓存（{} = 关闭）：HUD 缓存会把 HUD 渲染进独立 "
                        + "framebuffer，UILib 背景滤镜在其中采样不到世界画面（液态玻璃呈黑底）；"
                        + "现回到原版 HUD 渲染路径，代价是 HUD 每帧重绘",
                fieldName);
    }

    private void reportAbsent() {
        if (reported) {
            return;
        }
        reported = true;
        if (runtimeActiveField == null && cacheFramebufferField == null
                && loadClass(HUD_CACHING_CLASS_NAME, AngelicaHudCachingSuppressor.class.getClassLoader()) != null) {
            LOG.warn("[宿主兼容] 检测到 Angelica，但未找到可停用的 HUD 缓存入口（{}.{} / {}.{} 均缺失）；"
                            + "若 HUD 玻璃呈黑底，说明该宿主版本需要新的抑制手段",
                    CONFIG_CLASS_NAME, RUNTIME_ACTIVE_FIELD, HUD_CACHING_CLASS_NAME, CACHE_FRAMEBUFFER_FIELD);
        } else {
            LOG.debug("[宿主兼容] 未检测到 Angelica HUD 缓存，不干预 HUD 渲染路径");
        }
    }
}
