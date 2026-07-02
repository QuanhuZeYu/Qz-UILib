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
import club.heiqi.uilib.ui.scene.paint.SceneStateColors;

/**
 * SceneTab —— scene 新栈控件层 Phase 4 批 4 标签页控件（N 选 1 受控头 + 单内容区切换）。
 *
 * <h3>定位：旧栈 {@code DocumentTabControl} 的 strangler 声明式迁移</h3>
 * <p>把旧栈命令式标签页（{@code clearChildren} + {@code mountActiveTab} 命令式挂卸内容）
 * 迁成 scene 声明式范式：</p>
 * <ul>
 *   <li><b>tabBar 完全照 {@link SceneSegmented} R8</b>：N 选 1 受控头，当前页由外部
 *       {@code activeIndex} 只读 signal 唯一驱动；激活某页时<b>只经 {@code onActivate.accept(i)}</b>
 *       上抛期望页下标，控件<b>绝不自维护/修改 activeIndex</b>（R8）。</li>
 *   <li><b>内容区 N 选 1 用 N 个独立 {@code rt.show}</b>（契约 R10）：对每页 i 调一次
 *       {@code rt.show(contentPanel, Computed(activeIndex==i), tabPanels.get(i))}，
 *       由 show 引擎按 condition 挂载/卸载内容（守 I5/I7）。<b>绝不</b>在 create 的 Supplier
 *       体内 {@code activeIndex.get()} 做 if 分支建树（违 R3）。</li>
 * </ul>
 *
 * <h3>结构</h3>
 * <pre>
 * root (COLUMN, gap)
 *   ├─ tabBar (ROW, crossAxisAlign=STRETCH, gap)            ← 照 SceneSegmented R8 范式
 *   │     └─ tabSeg[i] (ROW, main/cross=CENTER, padding, cornerRadius, preferredWidth)  ← 交互单元 hitTestable=true
 *   │           └─ label[i] (text)                          ← 装饰 hitTestable=false
 *   └─ contentPanel (COLUMN, 单内容区容器)                   ← N 个 show 各自挂内容到此（tabBar 的兄弟）
 *           └─ anchor[i] × N + 当前页内容（由 show 引擎管理）
 * </pre>
 *
 * <h3>父高传导（fill 选项，默认关）</h3>
 * <p>默认 {@code fillContentPanel=false}：contentPanel 按各页内容自然高 shrink，整控件随内容长高。
 * 设 {@code fillContentPanel=true} 时打通 4 处断裂点，让 contentPanel 填满父分配高：</p>
 * <ol>
 *   <li>root {@code setFillParentHeight(true)}（否则父视 root 为固定容器子放弃 grow 分配）；</li>
 *   <li>contentPanel {@code setFillParentHeight(true)}（从 root 拿确定高下传）；</li>
 *   <li>tabBar 补 {@code preferredHeight}（隐藏杀手：固定兄弟无 preferredHeight 会令
 *       {@code computeColumnGrowHeights} 早退、root 放弃向 contentPanel 分配，照 {@link SceneSegmented} 口径）；</li>
 *   <li>各页内容 panel 由调用方自行 {@code setFillParentHeight(true)}（控件不可代劳）。</li>
 * </ol>
 * <p>fill 门槛：{@code setFillParentHeight(true)} 只有收到确定高约束才生效，故 ①③ 必须先打通。
 * 读 {@code fillContentPanel} 常量做静态 if 配置（构建期一次性，非 signal 订阅，守 R3）。</p>
 *
 * <h3>本期范围（YAGNI，oracle 裁决排除）</h3>
 * <p>不做：可关闭页签、页签溢出滚动（滚动地基是后续独立步骤，本步零引擎改动）、
 * roving 之外复杂键盘、内容懒加载缓存优化。键盘只保留 ←/→/Home/End/Enter/Space。</p>
 *
 * <h3>契约</h3>
 * <p>R1 纯静态工厂零实例字段 / R2 Props 只读 signal + 常量 + 回调 / R3 组件函数只执行一次 /
 * R4 外观随状态经 bind 派生 / R5 交互态读 interactionState / R6 段内文字命中穿透到段 /
 * R8 多选项单选受控零状态 / R10 内容区切换必须经 show（不得命令式 clearChildren）。</p>
 */
public final class SceneTab {

    /** 固定段宽（像素，scene 无 flex-grow 的等宽退让） */
    private static final int TAB_WIDTH = 72;
    /** 段内边距（像素） */
    private static final int TAB_PADDING = SceneChromeTokens.PAD_LG;
    /** 段圆角（像素） */
    private static final int TAB_RADIUS = SceneChromeTokens.RADIUS_MD;
    /** 各段之间的横向间距（像素） */
    private static final int TAB_GAP = SceneChromeTokens.GAP_SM;
    /** tabBar 与 contentPanel 之间的纵向间距（像素） */
    private static final int ROOT_GAP = 8;
    /**
     * tab 标签默认字号（UI 像素），与 {@link SceneNode} 默认 fontSize 对齐。
     * <p>仅用于 fill 模式下 tabBar {@code preferredHeight} 计算（照 {@link SceneSegmented} 口径），
     * 非 fill 模式不读。</p>
     */
    private static final int TAB_LABEL_FONT_SIZE = 16;

    /** 纯静态工厂，禁止实例化（强制无状态，契约 R1） */
    private SceneTab() {
    }

    /**
     * Tab 输入契约 —— N 选 1 受控头 + 各页内容 builder（契约 R2/R8）。
     *
     * <p>{@code tabLabels} 与 {@code tabPanels} 必须<b>同长度同序</b>：第 i 个页签文本对应第 i 个内容
     * builder。各页 builder 是独立 {@link Supplier}，分别交给第 i 个 show 在 condition 为真时调用一次。
     * 该同长同序契约由紧凑构造器在运行期 fail-fast 校验，违例抛 {@link IllegalArgumentException}
     * （避免建树循环 {@code items.get(idx)} 越界 {@link IndexOutOfBoundsException} 的延迟崩溃）。</p>
     *
     * @param activeIndex 当前活动页下标（响应式只读，受控源），控件绝不自己修改此值
     * @param tabLabels   页签文本列表（构建期固定常量，R2 允许常量）
     * @param tabPanels   各页内容构建器列表（与 tabLabels 同长度同序），各自交独立 show 调用
     * @param enabled     是否启用（响应式只读），false 时禁用点击/键盘并切灰态
     * @param onActivate  激活回调，激活某页时以该页下标调用，由外部 set 回 activeIndex signal
     * @param fillContentPanel 是否把 contentPanel 填满父分配高（构建期常量，非 signal，守 R3）。
     *                         <p>{@code true} 时打通父高传导链：root/contentPanel 各 {@code setFillParentHeight(true)}，
     *                         并给 tabBar 补 {@code preferredHeight}（消除固定容器子无 preferredHeight 导致
     *                         {@code computeColumnGrowHeights} 早退的隐藏杀手，照 {@link SceneSegmented} 口径）。
     *                         调用方需自行让各页内容 panel {@code setFillParentHeight(true)} 才能真正吃到父高。
     *                         默认 {@code false}（走 5 参重载）保持旧行为：contentPanel 按内容自然高 shrink。</p>
     */
    @Desugar
    public record Props(
            ReadableSignal<Integer> activeIndex,
            List<String> tabLabels,
            List<Supplier<SceneNode>> tabPanels,
            ReadableSignal<Boolean> enabled,
            Consumer<Integer> onActivate,
            boolean fillContentPanel
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

    /**
     * 工厂：构建 Tab 组件函数。
     *
     * <p>返回的 {@code Supplier} 体由 {@link SceneRuntime#mount} 执行一次（R3）：
     * 体内建 root(COLUMN) → tabBar(ROW，N 选 1 受控头) + contentPanel(单内容区)，
     * for 循环建 N 个 tab 段（tabLabels 固定，循环建树无副作用、只跑一次，守 I3），
     * 再对每页调一次 {@code rt.show}（N 个独立 show，condition 为 {@code activeIndex==i}）。
     * 动态外观全落 {@code bind(computed(...))}，交互只经 {@code on} 调 {@code onActivate}（R4/R5/R8）。</p>
     *
     * <p><b>内容切换铁律（R10）</b>：N 选 1 内容切换<b>必须</b>落成 N 个 show 的 condition computed，
     * <b>绝不</b>在 Supplier 体内 {@code activeIndex.get()} 做 if 分支建树（违 R3），
     * 也绝不命令式 {@code clearChildren} + 重挂（旧栈老路，违 R10）。</p>
     *
     * @param rt    场景运行时
     * @param props Tab 输入契约
     * @return 组件函数，交 {@code rt.mount(parent, ...)} 挂载
     */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            // ① 建树一次（无副作用，I3）—— 纵向容器：tabBar 在上、contentPanel 在下
            SceneNode root = SceneNode.column();
            root.setGap(ROOT_GAP);
            // 断裂点①（fill 传导）：root 自身 fill，否则在父眼里是"固定容器子"，
            // priorKnownChildHeight 命中容器分支返回 UNCONSTRAINED，父放弃向 root 分配 grow 高。
            // 读 fillContentPanel 常量做静态配置（构建期一次性，非 signal 订阅，守 R3）。
            if (props.fillContentPanel()) {
                root.setFillParentHeight(true);
            }

            // tabBar：横向 N 选 1 受控头复用 SingleSelect primitive，Tab 只挂 chrome（照 SceneSegmented R8）
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
            // 照 SceneSegmented:121-122 口径补 preferredHeight = 标签行高 + 2*段内边距。
            // 非 fill 模式不设（保持旧行为，tabBar 按内容自然高 shrink）。
            if (props.fillContentPanel()) {
                tabBar.setPreferredHeight(rt.lineHeight(TAB_LABEL_FONT_SIZE) + 2 * TAB_PADDING);
            }
            root.appendChild(tabBar);

            for (SceneSingleSelectPrimitive.ItemHandle handle : result.items()) {
                // tabSeg[i]：交互单元（hitTestable 默认 true），ROW + 主/交叉轴 CENTER + 固定段宽
                SceneNode tabSeg = handle.item();
                tabSeg.setFlexDirection(FlexDirection.ROW);
                tabSeg.setMainAxisAlign(MainAxisAlign.CENTER);
                tabSeg.setCrossAxisAlign(CrossAxisAlign.CENTER);
                tabSeg.setPadding(TAB_PADDING);
                tabSeg.setCornerRadius(TAB_RADIUS);
                tabSeg.setPreferredWidth(TAB_WIDTH);
                tabSeg.setBorderWidth(1);
                tabSeg.setBorderColor(SceneChromeTokens.BORDER_DEFAULT);

                // label[i]：段内纯文本装饰子节点，命中穿透到段（契约 R6）
                tabSeg.appendChild(handle.label());

                SceneInteractionState interaction = handle.interaction();

                // ③ 动态外观全走 bind（契约 R4）
                //    tab 段背景：enabled × activeIndex==i × hovered × pressed
                SceneControlChrome.bindSelectableBackground(rt, tabSeg, props.enabled(), handle.selected(), interaction);
                SceneControlChrome.bindStandardBorder(rt, tabSeg, props.enabled(), interaction);

                // label 文本色：活动白、非活动次要文本（照契约 bind activeIndex==i）
                rt.bindComputed(() -> Boolean.TRUE.equals(handle.selected().get())
                                ? SceneStateColors.standardText(Boolean.TRUE.equals(props.enabled().get()), true)
                                : SceneStateColors.secondaryText(Boolean.TRUE.equals(props.enabled().get())),
                        handle.label()::setTextColor);

                // cursor 声明式附着：enabled 指针手型、disabled 禁止符号（挂在交互单元 tabSeg 上）
                SceneControlChrome.bindCursor(rt, tabSeg, props.enabled(), SceneCursor.POINTER, SceneCursor.NOT_ALLOWED);
            }

            // contentPanel：单内容区容器，作 root 下 tabBar 的兄弟；N 个 show 各自把内容挂到此
            SceneNode contentPanel = SceneNode.column();
            // 断裂点②（fill 传导）：contentPanel 自身 fill，从 root 拿确定高约束下传给各页内容。
            // fill 门槛（SizingCalculator）：setFillParentHeight(true) 只有收到确定高约束才生效，
            // 故①③必须先打通，否则这里回退 shrink（守失效级别不破）。
            if (props.fillContentPanel()) {
                contentPanel.setFillParentHeight(true);
            }
            contentPanel.setBackgroundColor(SceneChromeTokens.BG_PRESSED);
            contentPanel.setCornerRadius(SceneChromeTokens.RADIUS_LG);
            root.appendChild(contentPanel);

            // ⑤ 内容区 N 选 1（契约 R10）：对每页 i 调一次独立 show，condition 用 handle.selected()
            //    与 tabBar 同源（primitive 的 normalizeIndex 语义），确保 null/越界 activeIndex 时
            //    tabBar 高亮与 contentPanel 挂载语义一致。绝不在本 Supplier 体内做 if 建树（违 R3）。
            final List<Supplier<SceneNode>> panels = props.tabPanels();
            List<SceneSingleSelectPrimitive.ItemHandle> items = result.items();
            for (int idx = 0; idx < panels.size(); idx++) {
                rt.show(contentPanel, items.get(idx).selected(), panels.get(idx));
            }

            return root;
        };
    }
}
