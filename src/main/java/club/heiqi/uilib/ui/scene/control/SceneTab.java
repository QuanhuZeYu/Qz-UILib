package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;

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

    // ==================== tab 段背景配色（enabled × active × pressed 三态，无 hover，照 Segmented） ====================

    /** 非活动 + 默认态段背景（深灰） */
    private static final int TAB_INACTIVE_ENABLED = 0xFF3A3A3A;
    /** 非活动 + pressed 态段背景（更暗） */
    private static final int TAB_INACTIVE_PRESSED = 0xFF2A2A2A;
    /** 活动 + 默认态段背景（亮蓝实心） */
    private static final int TAB_ACTIVE_ENABLED = 0xFF4A90D9;
    /** 活动 + pressed 态段背景（暗蓝） */
    private static final int TAB_ACTIVE_PRESSED = 0xFF3A7BC8;
    /** disabled 态段背景（灰，活动与否同色） */
    private static final int TAB_DISABLED = 0xFF2F2F2F;

    /** 活动段文本色（白） */
    private static final int TEXT_ACTIVE = 0xFFFFFFFF;
    /** 非活动段文本色（暗灰） */
    private static final int TEXT_INACTIVE = 0xFFB0B0B0;

    /** 固定段宽（像素，scene 无 flex-grow 的等宽退让） */
    private static final int TAB_WIDTH = 72;
    /** 段内边距（像素） */
    private static final int TAB_PADDING = 6;
    /** 段圆角（像素） */
    private static final int TAB_RADIUS = 4;
    /** 各段之间的横向间距（像素） */
    private static final int TAB_GAP = 4;
    /** tabBar 与 contentPanel 之间的纵向间距（像素） */
    private static final int ROOT_GAP = 8;

    /** 纯静态工厂，禁止实例化（强制无状态，契约 R1） */
    private SceneTab() {
    }

    /**
     * Tab 输入契约 —— N 选 1 受控头 + 各页内容 builder（契约 R2/R8）。
     *
     * <p>{@code tabLabels} 与 {@code tabPanels} 必须<b>同长度同序</b>：第 i 个页签文本对应第 i 个内容
     * builder。各页 builder 是独立 {@link Supplier}，分别交给第 i 个 show 在 condition 为真时调用一次。</p>
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
            SceneNode root = new SceneNode();
            root.setFlexDirection(FlexDirection.COLUMN);
            root.setGap(ROOT_GAP);

            // tabBar：横向 N 选 1 受控头，子段交叉轴拉伸等高（照 SceneSegmented R8）
            SceneNode tabBar = new SceneNode();
            tabBar.setFlexDirection(FlexDirection.ROW);
            tabBar.setCrossAxisAlign(CrossAxisAlign.STRETCH);
            tabBar.setGap(TAB_GAP);
            root.appendChild(tabBar);

            // contentPanel：单内容区容器，作 root 下 tabBar 的兄弟；N 个 show 各自把内容挂到此
            SceneNode contentPanel = new SceneNode();
            contentPanel.setFlexDirection(FlexDirection.COLUMN);
            root.appendChild(contentPanel);

            final List<String> labels = props.tabLabels();
            final int count = labels.size();

            // 缓存各 tab 段节点引用，供方向键 requestFocus 用
            final List<SceneNode> tabNodes = new ArrayList<>(count);

            for (int idx = 0; idx < count; idx++) {
                final int i = idx; // final 局部副本供 lambda 捕获

                // tabSeg[i]：交互单元（hitTestable 默认 true），ROW + 主/交叉轴 CENTER + 固定段宽
                SceneNode tabSeg = new SceneNode();
                tabSeg.setFlexDirection(FlexDirection.ROW);
                tabSeg.setMainAxisAlign(MainAxisAlign.CENTER);
                tabSeg.setCrossAxisAlign(CrossAxisAlign.CENTER);
                tabSeg.setPadding(TAB_PADDING);
                tabSeg.setCornerRadius(TAB_RADIUS);
                tabSeg.setPreferredWidth(TAB_WIDTH);
                tabBar.appendChild(tabSeg);
                tabNodes.add(tabSeg);

                // label[i]：段内纯文本装饰子节点，命中穿透到段（契约 R6）
                SceneNode labelNode = new SceneNode();
                labelNode.setHitTestable(false);
                labelNode.setText(labels.get(i));
                tabSeg.appendChild(labelNode);

                // ② 各 tab 段各取自己的 interactionState（契约 R5）
                SceneInteractionState is = rt.interactionState(tabSeg);

                // ③ 动态外观全走 bind（契约 R4）
                //    tab 段背景：enabled × activeIndex==i × pressed（无 hover，照契约）
                rt.bind(Invalidation.PAINT,
                        Computed.create(() -> resolveTabBackground(
                                props.enabled().get(),
                                isActive(props.activeIndex().get(), i),
                                is.pressed().get())),
                        tabSeg::setBackgroundColor);

                // label 文本色：活动白、非活动暗灰（照契约 bind activeIndex==i）
                rt.bind(Invalidation.PAINT,
                        Computed.create(() -> isActive(props.activeIndex().get(), i)),
                        act -> labelNode.setTextColor(Boolean.TRUE.equals(act) ? TEXT_ACTIVE : TEXT_INACTIVE));

                // cursor 声明式附着：enabled 指针手型、disabled 禁止符号（挂在交互单元 tabSeg 上）
                rt.bind(Invalidation.PAINT, props.enabled(),
                        e -> tabSeg.setCursor(Boolean.TRUE.equals(e) ? SceneCursor.POINTER : SceneCursor.NOT_ALLOWED));

                // ④ 交互经 on → 只调 onActivate 上抛期望页下标（受控 R8，绝不自改 activeIndex）
                rt.on(tabSeg, SceneEventType.CLICK, (ev, ctx) -> {
                    if (Boolean.TRUE.equals(props.enabled().get())) {
                        props.onActivate().accept(i);
                    }
                });

                // 键盘可达：登记进 Tab 焦点环
                rt.focusable(tabSeg);
                rt.on(tabSeg, SceneEventType.KEY_DOWN, (ev, ctx) -> {
                    if (!Boolean.TRUE.equals(props.enabled().get())) {
                        return;
                    }
                    SceneKey key = ev.getKey();
                    if (key == SceneKey.ENTER || key == SceneKey.SPACE) {
                        // Enter/Space 激活当前 tab 段
                        props.onActivate().accept(i);
                    } else if (key == SceneKey.ARROW_LEFT || key == SceneKey.ARROW_RIGHT
                            || key == SceneKey.HOME || key == SceneKey.END) {
                        // 方向键导航（水平）：读当前 activeIndex 算 nextIndex（读 signal 合法 I11），
                        // 上抛 + 焦点移动（requestFocus 是受控逃生舱合法）
                        Integer curObj = props.activeIndex().get();
                        int cur = (curObj == null) ? 0 : curObj.intValue();
                        int next = resolveNextIndex(key, cur, count);
                        props.onActivate().accept(next);
                        rt.requestFocus(tabNodes.get(next));
                    }
                });
            }

            // ⑤ 内容区 N 选 1（契约 R10）：对每页 i 调一次独立 show，condition 为 activeIndex==i。
            //    绝不在本 Supplier 体内 activeIndex.get() 做 if 建树（违 R3），也绝不命令式 clearChildren。
            //    N 个 show 各自独立，各页 builder 是 Props 传入的独立 Supplier，不合并。
            final List<Supplier<SceneNode>> panels = props.tabPanels();
            for (int idx = 0; idx < count; idx++) {
                final int i = idx; // final 局部副本供 condition lambda 捕获
                rt.show(contentPanel,
                        Computed.create(() -> isActive(props.activeIndex().get(), i)),
                        panels.get(i));
            }

            return root;
        };
    }

    /**
     * 判断指定下标是否为当前活动页（null 安全）。
     *
     * @param active 当前活动下标（可能为 null）
     * @param i      待判定下标
     * @return true 表示 i 是当前活动页
     */
    private static boolean isActive(Integer active, int i) {
        return active != null && active.intValue() == i;
    }

    /**
     * 解析方向键导航的目标下标（纯函数，无副作用）。
     *
     * <p>←：cur-1；→：cur+1；Home：首页 0；End：末页 count-1。←/→ 结果裁剪到 [0, count-1]。</p>
     *
     * @param key   方向键（ARROW_LEFT/ARROW_RIGHT/HOME/END 之一）
     * @param cur   当前活动下标
     * @param count 页签总数
     * @return 目标下标（已裁剪到合法范围）
     */
    private static int resolveNextIndex(SceneKey key, int cur, int count) {
        int next;
        if (key == SceneKey.HOME) {
            next = 0;
        } else if (key == SceneKey.END) {
            next = count - 1;
        } else if (key == SceneKey.ARROW_LEFT) {
            next = cur - 1;
        } else {
            next = cur + 1;
        }
        if (next < 0) {
            next = 0;
        } else if (next > count - 1) {
            next = count - 1;
        }
        return next;
    }

    /**
     * 解析 tab 段背景色（纯函数，无副作用）。
     *
     * <p>优先级：disabled &gt; pressed &gt; default（照契约无 hover 态）；
     * 同一态下活动与非活动用不同色系区分（活动亮蓝、非活动深灰）。</p>
     *
     * @param enabled 是否启用
     * @param active  是否为当前活动页
     * @param pressed 是否按压中
     * @return 当前态对应的 ARGB 背景色
     */
    private static int resolveTabBackground(Boolean enabled, boolean active, Boolean pressed) {
        if (!Boolean.TRUE.equals(enabled)) {
            return TAB_DISABLED;
        }
        if (Boolean.TRUE.equals(pressed)) {
            return active ? TAB_ACTIVE_PRESSED : TAB_INACTIVE_PRESSED;
        }
        return active ? TAB_ACTIVE_ENABLED : TAB_INACTIVE_ENABLED;
    }
}
