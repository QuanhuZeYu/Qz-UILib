/**
 * UI 环境端口（宿主 → 框架的只读环境事实注入面）。
 *
 * <p>本包只放<b>端口</b>与其缺席实现，以及一个把 UILib 既有进程级环境量适配进来的生产适配器
 * （{@link club.heiqi.uilib.ui.env.ProcessUiEnvironment}）。写入侧（配置回灌、资源重载、
 * 语言切换、headless 命令行参数）留在各自宿主，不进本包。</p>
 *
 * @see club.heiqi.uilib.ui.env.UiEnvironment
 */
package club.heiqi.uilib.ui.env;
