package club.heiqi.uilib.config.modern;

import java.io.File;

import club.heiqi.config.ConfigException;
import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.uilib.MyMod;
import club.heiqi.uilib.font.FontService;
import club.heiqi.uilib.font.config.FontConfig;

/**
 * 启动加载首次回灌：游戏启动阶段把新栈 YAML 配置回灌到 {@code Config}/{@code FontConfig}
 * 静态字段，让运行时读取者（MixinFontRenderer / GlyphPageManager / TextLayoutService 等）
 * 开服就用新栈配置。
 *
 * <h3>职责</h3>
 * <p>封装"启动加载首次回灌"完整逻辑，供 {@link club.heiqi.uilib.CommonProxy#preInit} 调用：</p>
 * <ol>
 *   <li>{@link ConfigManager#bootstrap} 加载新栈 YAML → 权威源 {@link club.heiqi.config.runtime.Authority}</li>
 *   <li>{@link ConfigValueBridge#applyFromAuthority} 全量回灌 {@code Config}/{@code FontConfig} 静态字段</li>
 *   <li>补刀（语义等价迁移自旧栈 Config.applyLoadedFontConfig）：
 *       {@link FontConfig#affectsFontRuntime()} 判断字体运行时是否变化
 *       → 变化时交 {@link FontService#requestReloadIfRenderRuntimeReady(String)}
 *         （侧别判据先于单例，服务端不为此创建 FontService）
 *       → {@link FontConfig#onConfigReload()} 刷 {@code last*} 快照</li>
 * </ol>
 *
 * <h3>语义等价迁移自旧栈 Config.applyLoadedFontConfig</h3>
 * <p>去掉 Forge {@code Configuration} 依赖，换 {@link ConfigValueBridge} 回灌源；
 * reload reason 用 {@code "modern_config_loaded"} 区分旧栈的 {@code "config_loaded"}。
 * 保留"运行时是否已建好"判断——preInit 阶段 {@link FontService} 可能尚未初始化
 * （{@code ClientProxy.preInit} 才 initialize，#71 起渲染 bootstrap 只在客户端做），
 * 此时不应触发 reload（应等 initialize 用新值建运行时）。该判断连同侧别判据一起
 * 收进 {@link FontService#requestReloadIfRenderRuntimeReady(String)}，本类不再直接触碰单例。</p>
 *
 * <h3>异常容错</h3>
 * <p>{@link ConfigManager#bootstrap} 失败（YAML 解析错、IO 错等）抛 {@link ConfigException}。
 * 本方法 catch 后 log + return，<b>不中断启动</b>，回退调用前静态字段值（新栈启动加载未发生）。
 * 新栈配置是实验性并行接入，不应让旧栈启动路径受其故障影响。</p>
 *
 * <h3>调用时机约束（P0 时序铁律）</h3>
 * <p>必须在以下两调用之间执行（{@link club.heiqi.uilib.CommonProxy#preInit} 中）：</p>
 * <ul>
 *   <li><b>在 {@link ModernConfigBootstrap} 之前</b>：无前驱依赖（旧栈 Config.init 已随阶段 E.2 删除），
 *       Bridge 直接从 Authority 拉值回灌，是新栈路径下静态字段的唯一写入点</li>
 *   <li><b>在 {@code FontService.initialize} 之前</b>：让字体系统首次初始化直接用新栈值，
 *       避免后续 reload 补刀（{@code initialize} 会读 {@code FontConfig} 静态字段建立运行时，
 *       若用默认值初始化后需 reload 重建）</li>
 * </ul>
 * <p>同时必须在 {@code NetTransportFactory.create} 之前回灌 {@code Config.netTransport}
 * 新栈值（{@code netTransport} 启动只读一次）。</p>
 *
 * <h3>守 I1</h3>
 * <p>{@link ConfigValueBridge#applyFromAuthority} 写静态字段是配置数据模型层
 * （非 {@code SceneNode} 属性槽，非 UI 状态）。{@link FontService#reload}（仅在
 * {@code affectsFontRuntime} 且运行时已就绪时触发，判据见
 * {@link FontService#requestReloadIfRenderRuntimeReady(String)}）→ {@code invalidateAll}
 * 失效注册表，非命令式改节点。I1 守。</p>
 *
 * <h3>与 {@link ModernConfigEntry#createScreen} 的关系</h3>
 * <p>两者都 {@link ConfigManager#bootstrap} 出新 {@link ConfigManager}，但用途不同：</p>
 * <ul>
 *   <li>启动加载（本类）：一次性回灌，manager 用完即 GC，不挂 listener
 *       （保存回调靠 {@code createScreen} 里 {@code new} 的 manager + {@link ConfigSaveListener}）</li>
 *   <li>配景页（{@link ModernConfigEntry}）：构建屏 + 挂 {@link ConfigSaveListener}，
 *       manager 随屏生命周期存在</li>
 * </ul>
 */
public final class ModernConfigBootstrap {

    /** reload reason，区分旧栈 "config_loaded" 与 C2 listener 的 "modern_config_saved"。 */
    private static final String RELOAD_REASON = "modern_config_loaded";

    private ModernConfigBootstrap() {
    }

    /**
     * 启动加载首次回灌：{@link ConfigManager#bootstrap} 新栈 ConfigManager
     * → {@link ConfigValueBridge#applyFromAuthority} 回灌静态字段
     * → {@code applyLoadedFontConfig} 等价补刀（{@link FontConfig#affectsFontRuntime()}
     * + {@link FontService#requestReloadIfRenderRuntimeReady(String)} +
     * {@link FontConfig#onConfigReload()}）。侧别判据先于单例，服务端不会为这次判断
     * 创建 {@link FontService}。
     *
     * <p>语义等价迁移自旧栈 {@code Config.applyLoadedFontConfig}（去 Forge cfg 依赖，
     * 换 Bridge 回灌源）。异常容错：bootstrap 失败（YAML 解析错）log + return，
     * 不中断启动，回退调用前静态字段值。</p>
     *
     * <p><b>调用时机约束</b>：必须在新栈启动加载路径下调用，且在
     * {@code FontService.initialize}（首次建字体运行时）之前调用，让新栈值成为最终态
     * 且 {@link FontService} 直接用新值初始化。</p>
     *
     * @param configFile 新栈 YAML 配置文件（{@code <mcDir>/config/qzuilib-modern.yaml}）
     */
    public static void bootstrapAndApply(File configFile) {
        MyMod.LOG.info("新栈配置启动加载开始: {}", configFile.getAbsolutePath());
        final ConfigSchema schema = QzUiLibModernSchema.create();
        final ConfigManager manager;
        try {
            manager = ConfigManager.bootstrap(configFile, schema);
        } catch (ConfigException e) {
            // bootstrap 失败不中断启动，回退调用前静态字段值（新栈值未回灌）
            MyMod.LOG.error("新栈配置 bootstrap 失败，回退调用前静态字段值: {}", configFile.getAbsolutePath(), e);
            return;
        }
        MyMod.LOG.info("新栈配置 bootstrap 成功");
        // 1. 全量回灌静态字段（C1 Bridge）
        ConfigValueBridge.applyFromAuthority(manager.authority());
        MyMod.LOG.debug("Bridge 值回灌完成: fontSort.length={}, fontSortConfigured={}",
                Integer.valueOf(FontConfig.fontSort.length),
                Boolean.valueOf(FontConfig.fontSortConfigured));
        // 2. 补刀（语义等价迁移自旧栈 Config.applyLoadedFontConfig）
        //    判据顺序是刻意的：先问静态侧别，再谈单例。旧写法直接 getInstance().isInitialized()
        //    会让专用服务端为一句恒 false 的判断付出单例类初始化——实测约 150 MiB 只服务渲染的
        //    按码点直索引表（#71 同族审计 C1）。
        boolean fontRuntimeChanged = FontConfig.affectsFontRuntime();
        MyMod.LOG.debug("affectsFontRuntime={}", Boolean.valueOf(fontRuntimeChanged));
        if (fontRuntimeChanged) {
            FontService.requestReloadIfRenderRuntimeReady(RELOAD_REASON);
        }
        FontConfig.onConfigReload();
        MyMod.LOG.debug("启动加载首次回灌完成");
    }
}