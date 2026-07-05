package club.heiqi.uilib;

/**
 * uilib 全局配置静态字段载体。
 *
 * <p>本类仅持有运行时读取者所需的 4 个 public static 字段：
 * {@code useDebug} / {@code uiDebug} / {@code fontRuntimeDebug} / {@code netTransport}。
 * 字段值由新栈 {@link club.heiqi.uilib.config.modern.ConfigValueBridge#applyFromAuthority}
 * 在启动加载（C3 {@link club.heiqi.uilib.config.modern.ModernConfigBootstrap}）与
 * 保存回调（C2 {@link club.heiqi.uilib.config.modern.ConfigSaveListener}）路径回灌。</p>
 *
 * <h3>历史</h3>
 * <p>本类曾是旧栈 Forge Configuration 的入口（init/load/saveAndReload/registerEvents/
 * onConfigChangeEvent/applyLoadedFontConfig/configuration 等），阶段 D+E 删除旧栈 UI 整支 +
 * 网络同步整支后，这些方法 / 字段已无引用，于阶段 E.2（本 commit）统收删除。
 * 保留的 4 字段被 13+ 处运行时读取者引用，不删。</p>
 */
public class Config {

    private Config() {
    }

    public static boolean useDebug = false;
    public static boolean uiDebug = false;
    public static boolean fontRuntimeDebug = false;
    public static String netTransport = "vanilla";
}
