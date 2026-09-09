package club.heiqi.uilib.ui.scene.control.search;

import java.util.Objects;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * PickerInfoBar —— 搜索面板底部的信息横条（常驻单行文本提示）。
 *
 * <h3>定位</h3>
 * <p>自带底表面的条带类容器，仅承载一行次要前景文本（12px），文本由受控信号驱动，
 * 外壳不负责省略（派生文本由调用方负责）。常驻挂载：文本为空时显示空串而非卸载。</p>
 *
 * <h3>外观归属（液态玻璃迁移，G13 搜索配件族口径）</h3>
 * <p>外壳 background/border/borderWidth/cornerRadius/surfaceElevation/backdrop 六项由
 * {@link SceneSurfaceBinder} 独占，消费来源主题 {@link SceneTheme.Role#TOOLBAR} 配方
 * （条带类容器取 FormActionBar 已验收先例；GROUP 是网格/内容底座口径，不适用）。
 * 文本取主题 {@code mutedForeground} 语义前景（旧 {@code TEXT_SECONDARY} 的迁移落点，
 * 与 MemberGrid 副文本同口径）。{@code hitTestable(false)} 纯展示：hover/pressed/focus
 * 永不触发，不预声明交互信号、不绑定 motionRoot。信息条常驻隐式启用（与迁移前一致，
 * {@link Props#enabled()} 仅保留宿主统一接线），故表面绑定恒传启用信号。
 * 内边距/固定高为布局常量（契约 §4.2「尺寸/间距常量继续使用」）。主题切换只重派生外观，
 * 不重建节点；全部绑定注册在 create() 调用者 Owner 作用域内，卸载随组件回收。</p>
 *
 * <h3>语义</h3>
 * <ul>
 *   <li>外壳固定高 {@link #INFO_BAR_HEIGHT}，上下内边距 {@link SceneChromeTokens#PAD_SM}
 *       （沿用原布局实参 {@code setPadding(PAD_SM, 0, PAD_SM, 0)}，语义与数值零改动）。</li>
 *   <li>{@code clipChildren(true)}、{@code hitTestable(false)}：纯展示，不参与命中。</li>
 *   <li>内部文本子节点经 {@link SceneRuntime#bindText} 绑定 text 信号，随组件卸载一并回收。</li>
 * </ul>
 */
public final class PickerInfoBar {

    /** 信息条高度（像素）。 */
    public static final int INFO_BAR_HEIGHT = 24;
    /** 信息文本字号（像素）。 */
    public static final int FONT_SIZE = 12;

    /** 条体表面恒启用：本组件常驻隐式启用（enabled 仅供宿主统一接线，与迁移前语义一致）。 */
    private static final ReadableSignal<Boolean> ALWAYS_ENABLED = () -> Boolean.TRUE;

    /** 纯静态组件工厂，禁止实例化。 */
    private PickerInfoBar() { }

    /** 信息条输入契约。 */
    @Desugar
    public record Props(ReadableSignal<String> text, ReadableSignal<Boolean> enabled) {

        /** 显式校验构造器：text / enabled 非 null（enabled 供外壳统一接线保留，本组件为常驻隐式启用）。 */
        public Props {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(enabled, "enabled");
        }
    }

    /**
     * 创建常驻信息条：单外壳节点 + 内部受控文本子节点。
     *
     * <p>须在组件构建作用域（mount/show/forEach/portal 的 builder 内）调用：
     * 主题配方与前景信号在此捕获来源作用域，此后主题切换只重派生外观、不重建节点。
     * 所有 bind 均注册在 create() 调用者 Owner 作用域内，卸载随组件回收。</p>
     *
     * @param rt    场景运行时
     * @param props 信息条属性
     * @return 信息条根节点
     */
    public static SceneNode create(SceneRuntime rt, Props props) {
        Objects.requireNonNull(rt, "rt");
        Objects.requireNonNull(props, "props");

        SceneNode bar = SceneNode.row();
        bar.setPreferredHeight(INFO_BAR_HEIGHT);
        bar.setPadding(SceneChromeTokens.PAD_SM, 0, SceneChromeTokens.PAD_SM, 0);
        bar.setClipChildren(true);
        bar.setHitTestable(false);

        // 条带表面：六项外观归表面绑定器独占（TOOLBAR 配方，构建期捕获来源主题）。
        // hitTestable(false) 纯展示，恒启用 + 交互态恒 idle，不叠第二层玻璃。
        ReadableSignal<SceneSurfaceStyle> surface = SceneThemes.surface(rt, SceneTheme.Role.TOOLBAR);
        SceneInteractionState interaction = rt.interactionState(bar);
        SceneSurfaceBinder.bind(rt, bar, surface, ALWAYS_ENABLED, interaction);

        SceneNode label = new SceneNode();
        label.setFontSize(FONT_SIZE);
        label.setHitTestable(false);
        // 文本次要前景跟随来源主题（mutedForeground 派生，主题切换只重算色值、不重建节点）。
        rt.bind(SceneThemes.mutedForeground(rt), label::setTextColor);
        rt.bindText(label, props.text());
        bar.appendChild(label);

        return bar;
    }
}
