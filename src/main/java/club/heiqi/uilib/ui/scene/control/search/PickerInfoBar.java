package club.heiqi.uilib.ui.scene.control.search;

import java.util.Objects;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * PickerInfoBar —— 搜索面板底部的信息横条（常驻单行文本提示）。
 *
 * <h3>定位</h3>
 * <p>实底圆角外壳，仅承载一行 {@code TEXT_SECONDARY} 文本（12px），文本由受控信号驱动，
 * 外壳不负责省略（派生文本由调用方负责）。常驻挂载：文本为空时显示空串而非卸载。</p>
 *
 * <h3>语义</h3>
 * <ul>
 *   <li>外壳固定高 {@link #INFO_BAR_HEIGHT}，背景 {@link SceneChromeTokens#BG_DEFAULT}，
 *       圆角 {@link SceneChromeTokens#RADIUS_SM}，水平内边距 {@link SceneChromeTokens#PAD_SM}。</li>
 *   <li>{@code clipChildren(true)}、{@code hitTestable(false)}：纯展示，不参与命中。</li>
 *   <li>内部文本子节点经 {@link SceneRuntime#bindText} 绑定 text 信号，随组件卸载一并回收。</li>
 * </ul>
 */
public final class PickerInfoBar {

    /** 信息条高度（像素）。 */
    public static final int INFO_BAR_HEIGHT = 24;
    /** 信息文本字号（像素）。 */
    public static final int FONT_SIZE = 12;

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
     * <p>所有 bind 均注册在 create() 调用者 Owner 作用域内，卸载随组件回收。</p>
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
        bar.setBackgroundColor(SceneChromeTokens.BG_DEFAULT);
        bar.setCornerRadius(SceneChromeTokens.RADIUS_SM);
        bar.setPadding(SceneChromeTokens.PAD_SM, 0, SceneChromeTokens.PAD_SM, 0);
        bar.setClipChildren(true);
        bar.setHitTestable(false);

        SceneNode label = new SceneNode();
        label.setFontSize(FONT_SIZE);
        label.setTextColor(SceneChromeTokens.TEXT_SECONDARY);
        label.setHitTestable(false);
        rt.bindText(label, props.text());
        bar.appendChild(label);

        return bar;
    }
}
