package club.heiqi.uilib.internal.devtools.pages;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneBreadcrumb;
import club.heiqi.uilib.ui.scene.control.SceneCheckbox;
import club.heiqi.uilib.ui.scene.control.SceneRadioGroup;
import club.heiqi.uilib.ui.scene.control.SceneSegmented;
import club.heiqi.uilib.ui.scene.control.SceneSlider;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.control.SceneTab;
import club.heiqi.uilib.ui.scene.control.SceneTextInput;
import club.heiqi.uilib.ui.scene.control.SceneInputType;
import club.heiqi.uilib.ui.scene.control.SceneToggle;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;

/**
 * 新栈 ui.scene 控件 demo 宿主 Widget —— 演示受控双向控件 SceneCheckbox + SceneToggle。
 *
 * <h3>受控双向闭环演示</h3>
 * <p>各控件配一个本地可写 {@link Signal}{@code <Boolean>} 作 checked/on 受控源，
 * onChange 回调里读「期望新值」并 set 回该 signal，形成「外部状态唯一源 → 控件渲染」单向数据流
 * （控件零内部状态，绝不自己缓存/翻转，守契约 R7）。</p>
 *
 * <h3>端到端 pipeline（对照 AbstractSceneHostWidget）</h3>
 * <pre>
 *  drainFrame → layout① → route(queueWrite) → flush(apply+effect)
 *    → layout②(吸收LAYOUT脏) → paint → replay
 * </pre>
 */
public class SceneControlsHostWidget extends AbstractSceneHostWidget {

    private static final int TITLE_BAR_HEIGHT = 38;
    private static final int SCROLL_GAP = 3;

    private final SceneNode root;
    private final SceneNode viewport;
    private final SceneNode scrollContainer;
    private final SceneNode scrollbarColumn;
    private final Signal<Integer> scrollSignal;

    /** Checkbox 受控源（本地唯一状态源），onChange 回调 set 回它 */
    private final Signal<Boolean> checkedSignal;
    /** Toggle 受控源（本地唯一状态源），onChange 回调 set 回它 */
    private final Signal<Boolean> toggleSignal;
    /** Slider 受控源（本地唯一状态源，连续值），onChange 按 committing 策略 set 回它 */
    private final Signal<Double> sliderSignal;
    /** TextInput(TEXT) 受控源（本地唯一状态源，文本），onChange 回调 set 回它 */
    private final Signal<String> textSignal;
    /** TextInput(PASSWORD) 受控源（本地唯一状态源，真实文本，显示掩码） */
    private final Signal<String> passwordSignal;
    /** Tab 受控源（本地唯一状态源，活动页下标），onActivate 回调 set 回它 */
    private final Signal<Integer> activeTabSignal;

    /**
     * 创建控件 demo 宿主 Widget，注入平台输入源。
     *
     * @param inputSource 平台输入源，可为 null（退化模式）
     */
    public SceneControlsHostWidget(PlatformInputSource inputSource) {
        super(inputSource);
        // root 为纵向容器，铺满 host 全高：固定标题条 + 滚动容器（viewport + scrollbar 兄弟列）
        this.root = new SceneNode();
        root.setFillParentHeight(true);
        root.setFlexDirection(FlexDirection.COLUMN);
        root.setGap(12);
        root.setPadding(20);
        root.setBackgroundColor(SceneDemoTokens.ROOT_BG);
        root.appendChild(createTitleBar());

        // viewport 承载 6 控件，scrollable+clip+fillParentHeight+flexGrow=1 吃满 root 剩余高
        this.viewport = SceneNode.column();
        viewport.setFillParentHeight(true);
        viewport.setFlexGrow(1);
        viewport.setScrollable(true);
        viewport.setClipChildren(true);
        viewport.setGap(20);
        viewport.setPadding(14);
        viewport.setBackgroundColor(SceneDemoTokens.VIEWPORT_BG);
        viewport.setCornerRadius(10);

        // scrollContainer 外包 ROW：viewport + scrollbar 兄弟列，照 ConfigScreen 范式
        this.scrollContainer = SceneNode.row();
        scrollContainer.setFillParentHeight(true);
        scrollContainer.setGap(SCROLL_GAP);
        scrollContainer.appendChild(viewport);
        root.appendChild(scrollContainer);

        // ===== Checkbox 受控双向闭环 =====
        this.checkedSignal = Signal.create(Boolean.FALSE);
        SceneCheckbox.Props checkboxProps = new SceneCheckbox.Props(
                checkedSignal,
                Signal.create("启用音效"),
                Signal.create(Boolean.TRUE),
                // 受控：把期望新值 set 回本地唯一源（控件不自己翻转）
                next -> checkedSignal.set(next));
        runtime.mount(viewport, SceneCheckbox.create(runtime, checkboxProps));

        // ===== Toggle 受控双向闭环 =====
        this.toggleSignal = Signal.create(Boolean.FALSE);
        SceneToggle.Props toggleProps = new SceneToggle.Props(
                toggleSignal,
                Signal.create("夜间模式"),
                Signal.create(Boolean.TRUE),
                next -> toggleSignal.set(next));
        runtime.mount(viewport, SceneToggle.create(runtime, toggleProps));

        // ===== Slider 受控连续闭环 =====
        // 受控源初值 30（范围 [0,100]，step=5），onChange 按 committing 写回策略：
        // 本 demo 选「committing=true/false 都写回」做实时预览联动——拖拽中每次预览也 set 回 sliderSignal，
        // 使 fill/thumb 实时跟手（受控闭环：onChange→外部 signal→effectiveValue 回落外部值仍正确）。
        // 另一种实现是仅 committing=true 才写回（拖拽中靠控件内部 draggingValue 接管预览），二选一，此处取实时联动。
        this.sliderSignal = Signal.create(30.0D);
        SceneSlider.Props sliderProps = new SceneSlider.Props(
                sliderSignal,
                Signal.create(Boolean.TRUE),
                0.0D, 100.0D, 5.0D,
                (value, committing) -> sliderSignal.set(value));
        runtime.mount(viewport, SceneSlider.create(runtime, sliderProps));

        // ===== TextInput(TEXT) 受控文本闭环 =====
        // 本地 Signal<String> 作受控唯一源，onChange 把期望新值真实 String set 回它形成单向数据流
        // （控件零内部状态，绝不自缓存/自改 value，守 R9）。
        this.textSignal = Signal.create("");
        SceneTextInput.Props textProps = new SceneTextInput.Props(
                textSignal,
                Signal.create(Boolean.TRUE),
                Signal.create(Boolean.FALSE),
                "请输入名称",
                32,
                SceneInputType.TEXT,
                next -> textSignal.set(next));
        runtime.mount(viewport, SceneTextInput.create(runtime, textProps));

        // ===== TextInput(PASSWORD) 密码掩码演示 =====
        // 真实值由 passwordSignal 唯一驱动；显示层 displayText 把真实值按码点数掩成等量圆点，
        // onChange 始终上抛真实值（掩码只影响显示不影响回调，守 R9）。
        this.passwordSignal = Signal.create("");
        SceneTextInput.Props passwordProps = new SceneTextInput.Props(
                passwordSignal,
                Signal.create(Boolean.TRUE),
                Signal.create(Boolean.FALSE),
                "请输入密码",
                32,
                SceneInputType.PASSWORD,
                next -> passwordSignal.set(next));
        runtime.mount(viewport, SceneTextInput.create(runtime, passwordProps));

        // ===== Tab 受控页切换闭环（N 选 1 受控头 + N 个独立 show 内容区，契约 R8/R10）=====
        // 本地 Signal<Integer> 作活动页受控唯一源，onActivate 把期望页下标 set 回它形成单向数据流
        // （控件零内部状态，绝不自维护/自改 activeIndex，守 R8）。
        // 各页内容用不同文本/背景色的 panel 区分，能直观看出切页；3 个页签 builder 独立。
        this.activeTabSignal = Signal.create(Integer.valueOf(0));
        List<String> tabLabels = Arrays.asList("常规", "外观", "高级");
        List<Supplier<SceneNode>> tabPanels = Arrays.asList(
                makeTabPanel("常规设置内容", 0xFF243B53),
                makeTabPanel("外观设置内容", 0xFF3B2D52),
                makeTabPanel("高级设置内容", 0xFF1F3D2E));
        SceneTab.Props tabProps = new SceneTab.Props(
                activeTabSignal,
                tabLabels,
                tabPanels,
                Signal.create(Boolean.TRUE),
                next -> activeTabSignal.set(next));
        runtime.mount(viewport, SceneTab.create(runtime, tabProps));

        // 滚动 attach + scrollbar 兄弟列，照 ConfigScreen 范式
        this.scrollSignal = SceneScrolls.attach(runtime, viewport);
        SceneScrollbar.Result sb = SceneScrollbar.createDefault(runtime, viewport, scrollSignal);
        this.scrollbarColumn = sb.column();
        scrollContainer.appendChild(scrollbarColumn);

        // 首次 flush，确保首帧有初始值
        runtime.flush();
    }

    /**
     * 创建固定标题条。
     *
     * @return 标题条节点
     */
    private SceneNode createTitleBar() {
        SceneNode titleBar = SceneNode.column();
        titleBar.setPreferredHeight(TITLE_BAR_HEIGHT);
        titleBar.setGap(4);
        titleBar.setHitTestable(false);
        titleBar.appendChild(SceneDemoCards.text("Scene Controls demo", SceneDemoTokens.TITLE_COLOR));
        titleBar.appendChild(SceneDemoCards.text("受控双向控件 · Checkbox/Toggle/Slider/TextInput/Tab", SceneDemoTokens.MUTED_COLOR));
        return titleBar;
    }

    @Override
    protected SceneNode getRoot() {
        return root;
    }

    /** @return 滚动视口节点 */
    SceneNode __getViewport() {
        return viewport;
    }

    /** @return 滚动容器节点（ROW：viewport + scrollbarColumn） */
    SceneNode __getScrollContainer() {
        return scrollContainer;
    }

    /** @return 滚动条列节点（scrollContainer 内 viewport 右侧独立列） */
    SceneNode __getScrollbarColumn() {
        return scrollbarColumn;
    }

    /**
     * 构建一个 Tab 内容页 builder（独立 {@link Supplier}，交第 i 个 show 在 condition 为真时调用一次）。
     *
     * <p>各页用不同背景色 + 文本区分，便于真机直观看出切页；纯静态建树，无 signal 读取（守 R3/I3）。</p>
     *
     * @param text 页内文本
     * @param bg   页背景色（ARGB）
     * @return 内容页构建函数
     */
    private Supplier<SceneNode> makeTabPanel(String text, int bg) {
        return () -> {
            SceneNode panel = SceneNode.column();
            panel.setPadding(12);
            panel.setCornerRadius(4);
            panel.setPreferredHeight(48);
            panel.setBackgroundColor(bg);

            SceneNode label = new SceneNode();
            label.setHitTestable(false);
            label.setText(text);
            label.setTextColor(0xFFFFFFFF);
            panel.appendChild(label);
            return panel;
        };
    }

}
