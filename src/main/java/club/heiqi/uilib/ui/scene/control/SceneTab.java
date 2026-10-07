package club.heiqi.uilib.ui.scene.control;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceBinder;
import club.heiqi.uilib.ui.scene.theme.SceneSurfaceStyle;
import club.heiqi.uilib.ui.scene.theme.SceneTheme;
import club.heiqi.uilib.ui.scene.theme.SceneThemes;


public final class SceneTab {

    /**
     * 段最小宽（像素）。段宽按标签文本测量后取 {@code max(本值, 文本宽 + 2*内边距)}：
     * 短标签保持等宽节奏，长标签不再被固定宽截断（③ 类「显式尺寸与字号无关」的收口——
     * 字号调大时段宽跟着长，不会出现文字变了框没变）。
     */
    private static final int TAB_WIDTH = 72;
    /** 段内边距（像素） */
    private static final int TAB_PADDING = SceneChromeTokens.PAD_LG;
    /** 各段之间的横向间距（像素） */
    private static final int TAB_GAP = SceneChromeTokens.GAP_SM;
    /** tabBar 与 contentPanel 之间的纵向间距（像素） */
    private static final int ROOT_GAP = 8;
    /**
     * tab 标签默认字号（UI 像素），与 {@link SceneNode} 默认 fontSize 对齐。
     * <p>调用方可用 {@link Props#fontSize()} 覆盖；本常量是未指定时的回落值。
     * <p>仅用于 fill 模式下 tabBar {@code preferredHeight} 计算（照 {@link SceneSegmented} 口径），
     * 非 fill 模式不读——该模式下条高按内容自然高收缩，随标签行高自动变化。</p>
     */
    private static final int TAB_LABEL_FONT_SIZE = 16;


    private SceneTab() {
    }


    @Desugar
    public record Props(
            ReadableSignal<Integer> activeIndex,
            List<String> tabLabels,
            List<Supplier<SceneNode>> tabPanels,
            ReadableSignal<Boolean> enabled,
            Consumer<Integer> onActivate,
            boolean fillContentPanel,
            ReadableSignal<Integer> fontSize
    ) {
        /**
         * 5 参向后兼容重载：{@code fillContentPanel} 默认 {@code false}，保持旧行为（contentPanel 按内容自然高 shrink，
         * 不传导父高）。老调用方零改动即可继续工作。
         *
         * @param activeIndex 当前活动页下标（响应式只读，受控源）
         * @param tabLabels   页签文本列表（构建期固定常量）
         * @param tabPanels   各页内容构建器列表（与 tabLabels 同长度同序）
         * @param enabled     是否启用（响应式只读）
         * @param onActivate  激活回调
         */
        public Props(
                ReadableSignal<Integer> activeIndex,
                List<String> tabLabels,
                List<Supplier<SceneNode>> tabPanels,
                ReadableSignal<Boolean> enabled,
                Consumer<Integer> onActivate
        ) {
            this(activeIndex, tabLabels, tabPanels, enabled, onActivate, false);
        }

        /**
         * 6 参向后兼容重载：{@code fontSize} 不指定（沿用 {@link #TAB_LABEL_FONT_SIZE}）。
         *
         * @param activeIndex      当前活动页下标（响应式只读，受控源）
         * @param tabLabels        页签文本列表（构建期固定常量）
         * @param tabPanels        各页内容构建器列表（与 tabLabels 同长度同序）
         * @param enabled          是否启用（响应式只读）
         * @param onActivate       激活回调
         * @param fillContentPanel 是否把 contentPanel 填满父分配高（构建期常量）
         */
        public Props(
                ReadableSignal<Integer> activeIndex,
                List<String> tabLabels,
                List<Supplier<SceneNode>> tabPanels,
                ReadableSignal<Boolean> enabled,
                Consumer<Integer> onActivate,
                boolean fillContentPanel
        ) {
            this(activeIndex, tabLabels, tabPanels, enabled, onActivate, fillContentPanel, null);
        }

        /**
         * 紧凑构造器：运行期校验 tabLabels 与 tabPanels 同长度同序契约（P1-D 修复）。
         *
         * <p>原本仅 Javadoc 约定，建树循环按 {@code panels.size()} 跑而 tabBar 段按 {@code labels.size()} 建，
         * 若 panels 多于 labels 会 {@code items.get(idx)} 越界抛 {@link IndexOutOfBoundsException}。
         * 此处在构造期 fail-fast 抛 {@link IllegalArgumentException}，把崩溃点前移到调用方。</p>
         *
         * @throws IllegalArgumentException 当 tabLabels/tabPanels 为 null 或两者长度不等时
         */
        public Props {
            if (tabLabels == null || tabPanels == null) {
                throw new IllegalArgumentException(
                        "SceneTab.Props: tabLabels 与 tabPanels 均不可为 null"
                                + "（tabLabels=" + tabLabels + ", tabPanels=" + tabPanels + "）");
            }
            if (tabLabels.size() != tabPanels.size()) {
                throw new IllegalArgumentException(
                        "SceneTab.Props: tabLabels 与 tabPanels 必须同长度同序（labels.size="
                                + tabLabels.size() + ", panels.size=" + tabPanels.size() + "）");
            }
        }
    }


    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            // 字号唯一真值是 root（与 Button/TextInput/TextArea 同口径）。
            // Props.fontSize() 写 root 的层 2 声明（与句柄入口同一槽，后写者胜出）；
            // 未指定时 TAB_LABEL_FONT_SIZE 只作构建期几何的初值，不写成声明（否则遮蔽层 2 声明）。
            // 页签标签是 root 的后代，沿父链继承，不再逐点接线。
            final ReadableSignal<Integer> configuredFontSize = props.fontSize();
            final Integer configuredFontSizeValue =
                    configuredFontSize == null ? null : configuredFontSize.get();
            // ① 建树一次（无副作用）—— 纵向容器：tabBar 在上、contentPanel 在下
            SceneNode root = SceneNode.column();
            root.setGap(ROOT_GAP);
            // 层 4a 回落值：数值与框架层 4b 默认同值（16），层 1/2/3 均优先于它。
            root.setFallbackFontSize(TAB_LABEL_FONT_SIZE);
            if (configuredFontSize != null) {
                if (configuredFontSizeValue != null) {
                    root.setFontScope(configuredFontSizeValue.intValue());
                }
                rt.bind(configuredFontSize, current -> {
                    if (current == null) {
                        root.resetFontScope();
                    } else {
                        root.setFontScope(current.intValue());
                    }
                });
            }
            // 断裂点①（fill 传导）：root 自身 fill，否则在父眼里是"固定容器子"，
            // priorKnownChildHeight 命中容器分支返回 UNCONSTRAINED，父放弃向 root 分配 grow 高。
            if (props.fillContentPanel()) {
                root.setFillParentHeight(true);
            }

            SceneSingleSelectPrimitive.Props primitiveProps = new SceneSingleSelectPrimitive.Props(
                    props.activeIndex(),
                    props.tabLabels(),
                    props.enabled(),
                    props.onActivate(),
                    SceneSingleSelectPrimitive.Orientation.HORIZONTAL);
            SceneSingleSelectPrimitive.Result result = SceneSingleSelectPrimitive.create(rt, primitiveProps);

            SceneNode tabBar = result.root();
            tabBar.setCrossAxisAlign(CrossAxisAlign.STRETCH);
            tabBar.setGap(TAB_GAP);
            // 断裂点③（隐藏杀手，fill 模式必备）：tabBar 是 contentPanel 的固定兄弟 + ROW 容器，
            // 若无 preferredHeight，priorKnownChildHeight(tabBar) 命中容器分支返回 UNCONSTRAINED，
            // 导致 computeColumnGrowHeights 早退、root 放弃向 contentPanel 分配 grow 高（fill 失效）。
            // 照 SceneSegmented#create 内置默认高口径补 preferredHeight = 标签行高 + 2*段内边距。
            // 非 fill 模式不设（保持旧行为，tabBar 按内容自然高 shrink）。
            // S5 收口：条高由字号派生，setFontSizeMetric 登记即算一次、其后仅在生效字号变化时重算
            // （同字号去重），不再手写 layoutDone 重算 effect。
            if (props.fillContentPanel()) {
                tabBar.setFontSizeMetric((node, fontSizePx) ->
                        node.setPreferredHeight(rt.lineHeight(fontSizePx) + 2 * TAB_PADDING));
            }
            root.appendChild(tabBar);

            // 导航条底座：TOOLBAR 角色配方是唯一外观写入者（独占 background/border/borderWidth/
            // cornerRadius/backdrop/surfaceElevation）。构造期捕获来源主题信号，effect 内不 resolve。
            ReadableSignal<SceneSurfaceStyle> barSurface = SceneThemes.surface(rt, SceneTheme.Role.TOOLBAR);
            SceneSurfaceBinder.bind(rt, tabBar, barSurface, props.enabled(), rt.interactionState(tabBar));

            // 主题语义色派生在构造期捕获一次（此时 Owner.current() 是来源作用域），循环内所有 tab 项共享。
            ReadableSignal<Integer> onAccentForeground = SceneThemes.onAccentForeground(rt);
            ReadableSignal<Integer> foreground = SceneThemes.foreground(rt);
            ReadableSignal<Integer> disabledForeground = SceneThemes.disabledForeground(rt);

            for (SceneSingleSelectPrimitive.ItemHandle handle : result.items()) {
                // tabSeg[i]：交互单元（hitTestable 默认 true），ROW + 主/交叉轴 CENTER + SHRINK 段宽。
                // 圆角/边框宽/边框色/染色/滤镜/实体高度全由表面绑定器从 INDICATOR 选中配方派生：
                // 构造期不再静态 setCornerRadius/setBorderWidth/setBorderColor（同一属性只留一个写入者）。
                SceneNode tabSeg = handle.item();
                tabSeg.setFlexDirection(FlexDirection.ROW);
                tabSeg.setMainAxisAlign(MainAxisAlign.CENTER);
                tabSeg.setCrossAxisAlign(CrossAxisAlign.CENTER);
                tabSeg.setPadding(TAB_PADDING);
                // 段宽 = max(TAB_WIDTH, 标签文本宽 + 2*内边距)，由字号派生声明给出（S5 收口）：
                // TAB_WIDTH 既作 setMinWidth 声明（下限语义），也写进派生值（显式 preferredWidth 分支
                // 不参与 min/max 钳制，下限必须在值里表达）；标签文本取构建期常量表，
                // 与 primitive 的 rt.bindText 落值时序解耦，首帧即正确。
                final String title = props.tabLabels().get(handle.index());
                tabSeg.setWidthSizing(SceneNode.WidthSizing.SHRINK);
                tabSeg.setMinWidth(TAB_WIDTH);
                tabSeg.setFontSizeMetric((node, fontSizePx) -> node.setPreferredWidth(
                        Math.max(TAB_WIDTH, rt.measureTextWidth(title, fontSizePx) + 2 * TAB_PADDING)));

                tabSeg.appendChild(handle.label());

                // tab 项表面：INDICATOR 角色配方 + selected 派生。选中只替换 tint 的 RGB（保留 0x59 alpha，
                // 故选中不只靠透明度区分），edge/elevation/lens/圆角保持角色配方，禁用仍走角色禁用档。
                // 每项一次绑定，项内不嵌套第二层玻璃节点（配方自带滤镜一颗表面只采样一次）。
                ReadableSignal<SceneSurfaceStyle> surface =
                        SceneThemes.selectableSurface(rt, SceneTheme.Role.INDICATOR, handle.selected());
                SceneInteractionState interaction = handle.interaction();
                SceneSurfaceBinder.bind(rt, tabSeg, surface, props.enabled(), interaction);

                // label 文本色：禁用取主题禁用前景色；选中在「强调底前景」与「正文色」间按对比度择一
                // （选中 tint 只有 0x59 半透明，实际底色是强调 tint 叠在导航条 TOOLBAR 基础 tint 之上）；
                // 未选中取主题正文色。
                rt.bindComputed(() -> {
                    if (!Boolean.TRUE.equals(props.enabled().get())) {
                        return disabledForeground.get();
                    }
                    if (Boolean.TRUE.equals(handle.selected().get())) {
                        return selectedForeground(surface.get().getIdle().getTint(),
                                barSurface.get().getIdle().getTint(),
                                onAccentForeground.get(), foreground.get());
                    }
                    return foreground.get();
                }, handle.label()::setTextColor);

                // cursor 声明式附着：enabled 指针手型、disabled 禁止符号（挂在交互单元 tabSeg 上）
                SceneControlChrome.bindCursor(rt, tabSeg, props.enabled(), SceneCursor.POINTER, SceneCursor.NOT_ALLOWED);
            }

            // contentPanel：单内容区容器，作 root 下 tabBar 的兄弟；N 个 show 各自把内容挂到此。
            // 保持透明（不装表面）：底座玻璃已由 tabBar 承担，内容区再装一层会挡死底座。
            SceneNode contentPanel = SceneNode.column();
            // 断裂点②（fill 传导）：contentPanel 自身 fill，从 root 拿确定高约束下传给各页内容。
            // fill 门槛（SizingCalculator）：setFillParentHeight(true) 只有收到确定高约束才生效，
            // 故①③必须先打通，否则这里回退 shrink（守失效级别不破）。
            if (props.fillContentPanel()) {
                contentPanel.setFillParentHeight(true);
            }
            root.appendChild(contentPanel);

            //    与 tabBar 同源（primitive 的 normalizeIndex 语义），确保 null/越界 activeIndex 时
            final List<Supplier<SceneNode>> panels = props.tabPanels();
            List<SceneSingleSelectPrimitive.ItemHandle> items = result.items();
            for (int idx = 0; idx < panels.size(); idx++) {
                rt.show(contentPanel, items.get(idx).selected(), panels.get(idx));
            }

            return root;
        };
    }

    // ==================== 选中项文字对比度择色 ====================

    /**
     * 选中项文字前景：在主题「强调底前景」与「正文色」之间取与选中底色对比度更高者。
     *
     * <p><b>为何不能只按纯强调色判定</b>：选中 tint 只有 0x59 半透明（{@code SceneThemes} 统一
     * 选中强度），实际底色是「强调 tint 叠在导航条 TOOLBAR 基础 tint 之上」；若拿纯强调色算对比度，
     * 浅色主题会选出白色文字（纯强调色上 6.44:1），而它叠在浅色玻璃上只剩 2.66:1，读不出来。
     * 故先按 alpha 合成近似底色，再算 WCAG 对比度择一。</p>
     *
     * @param selectedTint       选中项实际写入的 tint（0x59 半透明强调色，来自 INDICATOR 选中配方）
     * @param barTint            导航条 TOOLBAR 配方 idle tint（选中项的紧邻底色）
     * @param onAccentForeground 主题强调底前景色候选
     * @param foreground         主题正文色候选
     * @return 对比度更高的候选色（相等时取 onAccentForeground）
     */
    private static int selectedForeground(int selectedTint, int barTint,
            int onAccentForeground, int foreground) {
        int background = over(selectedTint, barTint);
        return contrastRatio(background, onAccentForeground) >= contrastRatio(background, foreground)
                ? onAccentForeground : foreground;
    }

    /**
     * alpha「over」合成：source 叠在 base 上，base 半透明时按其 alpha 折算覆盖率。
     *
     * <p>结果按不透明处理——只用于文字对比度判定，不写回任何节点属性。</p>
     *
     * @param source 上层色（ARGB）
     * @param base   下层色（ARGB）
     * @return 近似不透明合成色（ARGB）
     */
    private static int over(int source, int base) {
        int sourceAlpha = (source >>> 24) & 0xFF;
        int baseAlpha = (base >>> 24) & 0xFF;
        int restAlpha = (0xFF - sourceAlpha) * baseAlpha / 0xFF;
        int outAlpha = sourceAlpha + restAlpha;
        if (outAlpha == 0) {
            return 0;
        }
        int out = 0xFF000000;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int value = (((source >>> shift) & 0xFF) * sourceAlpha
                    + ((base >>> shift) & 0xFF) * restAlpha) / outAlpha;
            out |= Math.max(0, Math.min(0xFF, value)) << shift;
        }
        return out;
    }

    /** WCAG 相对对比度：(L亮 + 0.05) / (L暗 + 0.05)。 */
    private static double contrastRatio(int first, int second) {
        double firstLuminance = relativeLuminance(first);
        double secondLuminance = relativeLuminance(second);
        double brighter = Math.max(firstLuminance, secondLuminance);
        double darker = Math.min(firstLuminance, secondLuminance);
        return (brighter + 0.05) / (darker + 0.05);
    }

    /** WCAG 相对亮度（sRGB 线性化后加权，忽略 alpha）。 */
    private static double relativeLuminance(int argb) {
        return 0.2126 * linearize((argb >>> 16) & 0xFF)
                + 0.7152 * linearize((argb >>> 8) & 0xFF)
                + 0.0722 * linearize(argb & 0xFF);
    }

    /** sRGB 通道线性化。 */
    private static double linearize(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }
}
