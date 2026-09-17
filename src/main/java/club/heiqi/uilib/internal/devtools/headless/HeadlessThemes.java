package club.heiqi.uilib.internal.devtools.headless;

import club.heiqi.uilib.ui.scene.theme.SceneTheme;

/**
 * headless 主题档：把请求/命令行里的档名映射为 {@link SceneTheme} 值对象。
 *
 * <h3>为什么档名住在设施侧</h3>
 * <p>{@link SceneTheme} 是值对象、{@code SceneThemes} 是安装与解析入口，两者都不提供「按名字取档」——
 * 生产侧不需要（主题来自配置与服务），只有出图矩阵需要一个稳定的档名。故映射表落在设施内，
 * 并在未知档名的报错里列全可选值，让命令行使用者能自助。</p>
 *
 * <h3>为什么主题必须在装配期安装（与本轮字号倍率的差别）</h3>
 * <p>{@code SceneThemes.install} 把主题信号写进 runtime 根作用域，而控件的配方派生在<b>构建期</b>捕获
 * 该信号对象。于是「重复安装同一个信号对象、只改其值」能让已建树的重算，但<b>换一个信号对象</b>只影响
 * 此后构建的控件。结论：主题是<b>装配期</b>环境量——必须早于内容构建安装；而字号倍率经
 * {@code SceneRuntime.setFontScale} 写入，自带失效通道，属「装配后设置」类。两类不可混为一谈。</p>
 *
 * <h3>缺席语义</h3>
 * <p>{@code null} 档名 = <b>不干预</b>：各页面维持自己生产装配的默认（playground 显式
 * {@link club.heiqi.uilib.ui.scene.theme.SceneThemes#DEFAULT}；chat/hud 走按背景滤镜档动态解析的库默认）。
 * 空串按非法处理，不静默视作缺席。</p>
 *
 * <h3>哪些页面真的会因为换档而变色（实测边界）</h3>
 * <p>外观档装到 runtime 根作用域，<b>只有读 {@code SceneThemes} 的树</b>才会受影响：</p>
 * <ul>
 *   <li>{@code playground}：外壳与 9 个演示页都经 {@code SceneThemes} 取配方 ⇒ 换档即变色
 *       （实测默认档 vs 浅色档：685824/921600 像素不同）；</li>
 *   <li>{@code chat} / {@code hud}：chat3 的 HUD 形态配色来自它<b>自己的进程级色板</b>
 *       {@code ChatMarkdownSettings}（气泡底、正文、组头、系统行…），不读 runtime 默认主题
 *       ⇒ 换档出图<b>逐像素相同</b>（实测 0/921600）。这不是本轴的缺陷，而是 chat3 存在两套配色
 *       来源的现状：只有容器形态（输入屏打开时）走 {@code SceneThemes}；</li>
 *   <li>{@code text-probe}：前景色写死，连安装都不做（见 {@code HeadlessSession#createHost}）。</li>
 * </ul>
 *
 * <h3>边界</h3>
 * <p>本表只含三档配色。<b>不含</b> {@code withoutBackdrop()} 那一类「关闭背景滤镜」的派生档：
 * 库默认本就按 {@code BackdropQualityService} 的档位动态解析（{@code OFF} → 实色替代档），
 * 那属于「背景滤镜可用性」这个独立环境事实；在它有自己的请求入口之前，不在这里用主题档名替代表达。</p>
 */
final class HeadlessThemes {

    /** 深色液态玻璃档（库默认配色的显式档名）。 */
    static final String LIQUID_GLASS_DARK = "liquid-glass-dark";
    /** 浅色液态玻璃档。 */
    static final String LIQUID_GLASS_LIGHT = "liquid-glass-light";
    /** 实色深色档（保留的旧实色观感，供显式覆盖与对照）。 */
    static final String SOLID_DARK = "solid-dark";

    private HeadlessThemes() {
    }

    /**
     * 校验档名（请求构建期调用，非法即快速失败）。
     *
     * @param name 档名；{@code null} = 不干预
     * @throws IllegalArgumentException 未知档名或空串
     */
    static void requireValid(String name) {
        if (name == null) {
            return;
        }
        if (lookup(name) == null) {
            throw new IllegalArgumentException("未知主题档：\"" + name + "\"（可选：" + names() + "）");
        }
    }

    /**
     * 档名 → 主题值对象。
     *
     * @param name 已校验的档名；{@code null} = 不干预
     * @return 主题；{@code null} 表示不干预
     */
    static SceneTheme resolve(String name) {
        return name == null ? null : lookup(name);
    }

    /** @return 全部档名（顿号分隔，用于 usage 与报错） */
    static String names() {
        return LIQUID_GLASS_DARK + " / " + LIQUID_GLASS_LIGHT + " / " + SOLID_DARK;
    }

    private static SceneTheme lookup(String name) {
        if (LIQUID_GLASS_DARK.equals(name)) {
            return SceneTheme.liquidGlassDark();
        }
        if (LIQUID_GLASS_LIGHT.equals(name)) {
            return SceneTheme.liquidGlassLight();
        }
        if (SOLID_DARK.equals(name)) {
            return SceneTheme.solidDark();
        }
        return null;
    }
}
