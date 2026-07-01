package club.heiqi.uilib.ui.scene.control;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
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
     */
    @Desugar
    public record Props(
            ReadableSignal<Integer> activeIndex,
            List<String> tabLabels,
            List<Supplier<SceneNode>> tabPanels,
            ReadableSignal<Boolean> enabled,
            Consumer<Integer> onActivate
    ) {
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
                rt.bind(Computed.create(() -> Boolean.TRUE.equals(handle.selected().get())
                                ? SceneStateColors.selectedBackground(
                                        Boolean.TRUE.equals(props.enabled().get()),
                                        Boolean.TRUE.equals(interaction.hovered().get()),
                                        Boolean.TRUE.equals(interaction.pressed().get()))
                                : SceneStateColors.standardBackground(
                                        Boolean.TRUE.equals(props.enabled().get()),
                                        Boolean.TRUE.equals(interaction.hovered().get()),
                                        Boolean.TRUE.equals(interaction.pressed().get()))),
                        tabSeg::setBackgroundColor);
                rt.bind(Computed.create(() -> SceneStateColors.standardBorder(
                                Boolean.TRUE.equals(props.enabled().get()),
                                Boolean.TRUE.equals(interaction.focused().get()))),
                        tabSeg::setBorderColor);

                // label 文本色：活动白、非活动次要文本（照契约 bind activeIndex==i）
                rt.bind(Computed.create(() -> Boolean.TRUE.equals(handle.selected().get())
                                ? SceneStateColors.standardText(Boolean.TRUE.equals(props.enabled().get()), true)
                                : SceneStateColors.secondaryText(Boolean.TRUE.equals(props.enabled().get()))),
                        handle.label()::setTextColor);

                // cursor 声明式附着：enabled 指针手型、disabled 禁止符号（挂在交互单元 tabSeg 上）
                rt.bind(props.enabled(),
                        e -> tabSeg.setCursor(Boolean.TRUE.equals(e) ? SceneCursor.POINTER : SceneCursor.NOT_ALLOWED));
            }

            // contentPanel：单内容区容器，作 root 下 tabBar 的兄弟；N 个 show 各自把内容挂到此
            SceneNode contentPanel = SceneNode.column();
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
