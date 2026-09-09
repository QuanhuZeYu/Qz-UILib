package club.heiqi.uilib.internal.devtools.playground;

import java.util.Objects;

import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneButtonVariant;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * 测试场地页面通用装配小工具。
 *
 * <p>只做「构建期一次性建树」的静态装配：返回的节点已设好不可命中、宽度尺寸等静态样式，
 * 动态随状态变化的外观一律由页面在 {@code build} 内用 {@code rt.bind/bindComputed} 派生
 * （守 scene 信条 R3/R4：组件函数只执行一次、外观随状态经 bind 派生）。</p>
 *
 * <p><b>公共构件默认消费主题</b>：{@link #card()} 取 {@link SceneTheme.Role#GROUP} 配方，
 * {@link #button}/{@link #primaryButton} 复用已主题化的 {@link SceneButton}（标准/主操作角色），
 * 文本前景经 {@link #text(SceneRuntime, String, ReadableSignal, int)} 随主题更新。
 * 无 {@code rt} 形参的默认文本构件（{@link #text(String)}/{@link #title}/{@link #hint}/
 * {@link #strongHint}）经同一 Owner 作用域接缝（{@link #installRuntime}）解析来源主题，
 * 默认取 {@link SceneThemes#foreground}/{@link SceneThemes#mutedForeground}；不在宿主上下文内时
 * 回落迁移前的静态 {@link #TEXT}/{@link #MUTED}（独立像素夹具与未接入宿主像素不变）。
 * 诊断页的显式样本材质与语义色（
 * {@code text(value, color, fontSize)} 的显式色、页面自建对照面板）保持原样，不被统一刷成主题。</p>
 *
 * <p>本类位于 {@code internal.devtools} 下，属内部调试设施，不构成公共 API 承诺。</p>
 */
public final class PlaygroundKit {

    /** 页面根底色（比控件禁用底更深的暗色，衬托面板高差）。 */
    public static final int ROOT_BG = 0xFF17151B;
    /** 面板底色（与 scene 控件默认底同源）。 */
    public static final int PANEL_BG = SceneChromeTokens.BG_DEFAULT;
    /** 面板边框色。 */
    public static final int BORDER = SceneChromeTokens.BORDER_DEFAULT;
    /** 主文本色。 */
    public static final int TEXT = SceneChromeTokens.TEXT_PRIMARY;
    /** 次要/说明文本色。 */
    public static final int MUTED = SceneChromeTokens.TEXT_SECONDARY;
    /** 强调色（选中/主操作）。 */
    public static final int ACCENT = SceneChromeTokens.ACCENT;
    /** 危险操作色（与 SceneDialog DANGER 同源观感）。 */
    public static final int DANGER = 0xFFB3261E;

    /** 页面骨架最大内容宽（UI 像素）。 */
    public static final int MAX_CONTENT_WIDTH = 860;

    /** 常开 enabled 信号：公共构件表面不可禁用（表面绑定器只关心恒真）。 */
    private static final ReadableSignal<Boolean> ALWAYS_ENABLED = () -> Boolean.TRUE;

    /** Owner 作用域键：构建上下文所属 runtime（供无 {@code rt} 形参的公共构件解析主题来源）。 */
    private static final Object RUNTIME_KEY = new Object();

    private PlaygroundKit() {
    }

    /**
     * 把 runtime 登记到它自己的根 Owner 作用域。
     *
     * <p>{@link #card()} 是页面普遍调用、却无法补 {@code rt} 形参的装配入口（页面清单只读），
     * 因此由宿主在构造期把 runtime 挂上 Owner 链；页面构建期（mount/show/forEach/portal 的
     * builder 内）沿父链即可解析到它，从而用同一套 {@link SceneThemes} 解析主题。</p>
     *
     * @param rt 场景运行时，不可为 null
     */
    static void installRuntime(SceneRuntime rt) {
        Objects.requireNonNull(rt, "rt");
        rt.__runRoot(() -> {
            Owner owner = Owner.current();
            if (owner != null) {
                owner.setScope(RUNTIME_KEY, rt);
            }
        });
    }

    /** @return 当前构建上下文所属 runtime；不在任何 Playground 宿主上下文内时为 null */
    private static SceneRuntime activeRuntime() {
        Owner owner = Owner.current();
        return owner == null ? null : owner.findScope(RUNTIME_KEY, SceneRuntime.class);
    }

    /**
     * 创建默认样式文本节点（16px，不可命中）。
     *
     * <p><b>宿主上下文内</b>（{@link #installRuntime} 已登记）取来源主题正文前景
     * {@link SceneThemes#foreground}，主题切换后自动更新且不重建节点；<b>无宿主上下文</b>时回落
     * 静态 {@link #TEXT}（外观与迁移前一致）。</p>
     *
     * @param value 文本
     * @return 文本节点
     */
    public static SceneNode text(String value) {
        return defaultText(value, 16, false);
    }

    /**
     * 创建指定样式文本节点（不可命中）。
     *
     * <p><b>显式色重载，语义恒定</b>：颜色就是 {@code color}，不随主题变化，也不受宿主上下文
     * 影响（诊断样本、刻意色差仍走本重载）。</p>
     *
     * @param value    文本
     * @param color    文本色
     * @param fontSize 字号（UI 像素）
     * @return 文本节点
     */
    public static SceneNode text(String value, int color, int fontSize) {
        SceneNode node = new SceneNode();
        node.setText(value);
        node.setTextColor(color);
        node.setFontSize(fontSize);
        node.setHitTestable(false);
        return node;
    }

    /**
     * 创建主题前景文本节点（不可命中）：颜色由 {@code color} 信号驱动，构建期不读值，
     * 主题切换后自动更新（供外壳标题等「跟随主题」文字使用；显式样本色仍走
     * {@link #text(String, int, int)}）。
     *
     * @param rt       场景运行时
     * @param value    文本
     * @param color    前景色信号（如 {@link SceneThemes#foreground}/{@link SceneThemes#mutedForeground} 的派生）
     * @param fontSize 字号（UI 像素）
     * @return 文本节点
     */
    public static SceneNode text(SceneRuntime rt, String value, ReadableSignal<Integer> color, int fontSize) {
        SceneNode node = new SceneNode();
        node.setText(value);
        node.setFontSize(fontSize);
        node.setHitTestable(false);
        rt.bindComputed(color::get, node::setTextColor);
        return node;
    }

    /**
     * 页面节标题（16px，宿主内跟随主题正文前景）。
     *
     * @param value 标题文本
     * @return 标题节点
     */
    public static SceneNode title(String value) {
        return defaultText(value, 16, false);
    }

    /**
     * 次级说明文本（12px，宿主内跟随主题次要前景）。
     *
     * @param value 说明文本
     * @return 说明节点
     */
    public static SceneNode hint(String value) {
        return defaultText(value, 12, true);
    }

    /**
     * 强调说明文本（12px，宿主内跟随主题正文前景）。
     *
     * @param value 说明文本
     * @return 说明节点
     */
    public static SceneNode strongHint(String value) {
        return defaultText(value, 12, false);
    }

    /**
     * 无 {@code rt} 形参的默认文本构件统一取色路径。
     *
     * <p><b>宿主内</b>：沿既有 {@link #RUNTIME_KEY} Owner 作用域接缝解析所属 runtime，再按
     * {@link SceneThemes#resolve} 取<b>来源主题</b>（局部 {@code withTheme} 优先于 runtime 默认），
     * 经 {@link #text(SceneRuntime, String, ReadableSignal, int)} 用 {@code bindComputed} 绑定前景：
     * 构建期不读值、主题切换只重派生不重建节点。</p>
     *
     * <p><b>无宿主上下文</b>（{@code Owner.current() == null} 或该作用域未登记 runtime）：回落
     * {@link #text(String, int, int)} 静态取色，不抛异常、不产生响应式绑定，像素与迁移前一致。</p>
     *
     * @param value    文本
     * @param fontSize 字号（UI 像素）
     * @param muted    true 取次要前景，false 取正文前景
     * @return 文本节点
     */
    private static SceneNode defaultText(String value, int fontSize, boolean muted) {
        SceneRuntime rt = activeRuntime();
        if (rt == null) {
            return text(value, muted ? MUTED : TEXT, fontSize);
        }
        ReadableSignal<Integer> color = muted
                ? SceneThemes.mutedForeground(rt)
                : SceneThemes.foreground(rt);
        return text(rt, value, color, fontSize);
    }

    /**
     * 创建标准面板卡片：主题 {@link SceneTheme.Role#GROUP} 配方 + 内边距 + 纵向间距，
     * 宽度填满父轴最大宽。
     *
     * <p><b>宿主内（{@link #installRuntime} 已登记）</b>走 {@link SceneSurfaceBinder}：
     * background/border/borderWidth/cornerRadius/backdrop/surfaceElevation 全由绑定器从 GROUP
     * 配方派生，主题切换只重派生、不重建节点。卡片自身不是交互单元（交互在子控件上），退出
     * 「叶命中目标」资格，避免整卡随指针变色；子控件仍可命中。</p>
     *
     * <p><b>无宿主上下文</b>时（独立像素夹具、尚未迁移的其他宿主）保留旧静态底色，外观与既有
     * 断言不变；这类调用方接入 {@link #installRuntime} 后即自动消费主题。</p>
     *
     * @return 卡片根节点（COLUMN）
     */
    public static SceneNode card() {
        SceneNode card = SceneNode.column();
        card.setFillParentWidth(true);
        card.setMaxWidth(MAX_CONTENT_WIDTH);
        card.setPadding(SceneChromeTokens.PAD_LG);
        card.setGap(SceneChromeTokens.GAP_MD);
        card.setHitTestable(false);
        SceneRuntime rt = activeRuntime();
        if (rt == null) {
            card.setBackgroundColor(PANEL_BG);
            card.setBorderWidth(1);
            card.setBorderColor(BORDER);
            card.setCornerRadius(SceneChromeTokens.RADIUS_MD);
            return card;
        }
        SceneInteractionState interaction = rt.interactionState(card);
        // 时序契约：Router 的 writeHovered/writePressed/writeFocused 对未创建的 signal 短路，
        // 构建期先声明关心（卡片非命中目标，实际写入恒 FALSE，配方停在 idle 档）。
        interaction.hovered();
        interaction.pressed();
        interaction.focused();
        SceneSurfaceBinder.bind(rt, card, SceneThemes.surface(rt, SceneTheme.Role.GROUP),
                ALWAYS_ENABLED, interaction);
        return card;
    }

    /**
     * 创建横向行容器（gap=10）。
     *
     * <p>行默认 FILL 填满父级下传的可用宽（与引擎默认 {@code WidthSizing} 一致），
     * 按钮等行内控件按内容宽 SHRINK 排布，从主轴起点 START 依次排列。</p>
     *
     * @return 行节点
     */
    public static SceneNode row(int gap) {
        SceneNode row = SceneNode.row(gap);
        row.setHitTestable(false);
        return row;
    }

    /**
     * 把按钮根设为按内容宽排布（{@link SceneNode.WidthSizing#SHRINK}）。
     *
     * <p>按钮根节点默认 {@code WidthSizing.FILL}（引擎容器默认），放进 ROW 行后会把
     * 整行可用宽拉满、同行的后续按钮被挤出容器右缘（真机症状：按钮跑到最右侧、
     * 只露圆角边缘）。既有控件（SceneDialog / VariantChooser / SceneSimpleList /
     * SceneKeyValueMap 等）的 ROW 内按钮均显式 SHRINK，本场地统一在此收口。</p>
     *
     * @param root 按钮根节点（可为 null，防御挂载失败）
     * @return 原节点
     */
    private static SceneNode applyButtonSizing(SceneNode root) {
        if (root != null) {
            root.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        }
        return root;
    }

    /**
     * 创建并挂载标准按钮（STANDARD 变体）。
     *
     * <p>组件经 {@code rt.mount(parent, ...)} 挂到父节点并返回其根节点（调用一次即完成装配，
     * 静态样式固化，动态交互走 bind/on）。外观不传配方 → {@link SceneButton} 默认路径取主题
     * {@link SceneTheme.Role#BUTTON_STANDARD} 角色（四态、前景、滤镜全由表面绑定器派生）。</p>
     *
     * @param rt      场景运行时
     * @param parent  挂载父节点
     * @param label   按钮文本
     * @param onClick 点击回调（经 CLICK 事件触发）
     * @return 按钮根节点（已挂入 parent）
     */
    public static SceneNode button(SceneRuntime rt, SceneNode parent, String label, Runnable onClick) {
        return applyButtonSizing(rt.mount(parent, SceneButton.create(rt, new SceneButton.Props(
                Signal.create(label), Signal.create(Boolean.TRUE), onClick, SceneButtonVariant.STANDARD))).getRoot());
    }

    /**
     * 创建并挂载主操作按钮（PRIMARY 变体）。
     *
     * <p>外观同 {@link #button}：不传配方 → 主题 {@link SceneTheme.Role#BUTTON_PRIMARY} 角色。</p>
     *
     * @param rt      场景运行时
     * @param parent  挂载父节点
     * @param label   按钮文本
     * @param onClick 点击回调
     * @return 按钮根节点（已挂入 parent）
     */
    public static SceneNode primaryButton(SceneRuntime rt, SceneNode parent, String label, Runnable onClick) {
        return applyButtonSizing(rt.mount(parent, SceneButton.create(rt, new SceneButton.Props(
                Signal.create(label), Signal.create(Boolean.TRUE), onClick, SceneButtonVariant.PRIMARY))).getRoot());
    }

    /**
     * 占位 spacer（flexGrow=1 的不可见节点），用于 ROW 布局撑开两侧间距。
     *
     * @return 占位节点
     */
    public static SceneNode spacer() {
        SceneNode spacer = new SceneNode();
        spacer.setFlexGrow(1);
        spacer.setHitTestable(false);
        return spacer;
    }
}