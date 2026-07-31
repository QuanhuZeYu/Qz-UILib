package club.heiqi.config.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.runtime.SaveOutcome;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.schema.SectionSpec;
import club.heiqi.config.ui.field.FieldRenderer;
import club.heiqi.config.ui.field.FieldRendererRegistry;
import club.heiqi.config.ui.theme.ConfigTheme;
import club.heiqi.uilib.ui.scene.form.FormActionBar;
import club.heiqi.uilib.ui.scene.form.FormPageShell;
import club.heiqi.uilib.ui.scene.form.FormTheme;
import club.heiqi.uilib.ui.scene.host.AbstractSceneHostWidget;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneButtonVariant;
import club.heiqi.uilib.ui.scene.control.SceneNavList;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.Transform;
import club.heiqi.uilib.ui.scene.overlay.SceneOverlayHost;

/**
 * 配置页 UI 骨架，extends {@link AbstractSceneHostWidget}。
 *
 * <p>Material settings page 结构；section 使用专用 Owner 单槽，并在完整布局发布后执行
 * 标题/字段 presentation shell 的满 opacity 级联进入：</p>
 * <pre>
 * root (COLUMN, centered, fillParentHeight, translucent bg=ROOT_BG)
 *   ├ titleBar        schema.title() + modId
 *   ├ statusSummary   dirty/error 状态
 *   ├ bodyRow         多 section 时固定左侧 navigation + tonal viewport
 *   │   ├ navPane     SceneNavList
 *   │   └ scrollContainer
 *   │       ├ viewport → content → active section Setting Rows
 *   │       └ scrollbarColumn
 *   ├ saveFeedbackBar (按状态懒挂载)
 *   └ actionBar       恢复默认 / 取消 / 保存，底部固定
 * </pre>
 *
 * <h3>项2/3 布局语义</h3>
 * <ul>
 *   <li>navPane 固定不参与滚动；0/1 section 时省略。</li>
 *   <li>actionBar 在滚动容器外并固定于页面底部，save/cancel/restore 始终可见。</li>
 *   <li>标题、状态、body 与 action bar 都居中并受页面最大宽度约束。</li>
 * </ul>
 *
 * <h3>关键守不变量</h3>
 * <ul>
 *   <li>I1：导航目标与 saveFeedback 经 signal 驱动；Motion phase 不写事务历史</li>
 *   <li>I3：section 单槽严格先 dispose outgoing Owner 再 mount incoming</li>
 *   <li>I7/I8：未激活 section 子树不参与布局/绘制</li>
 *   <li>I11：导航点击 handler 只 activeSectionSignal.set，不直接改 SceneNode</li>
 * </ul>
 *
 * <h3>按钮回调</h3>
 * <ul>
 *   <li>保存 → {@code mgr.save(draft)} + {@code adapter.afterSaveSync()} + 写 saveFeedbackSignal</li>
 *   <li>取消 → {@code adapter.resetToCurrent()}</li>
 *   <li>恢复默认 → 按 {@link FieldRestorePolicy} 逐字段跳过、自定义或 {@code resetFieldToDefault(path)}</li>
 * </ul>
 *
 * <p>构造器接受可为 null 的 {@link PlatformInputSource}（headless 测试传 null）。
 * 逻辑与渲染解耦：构造器只建 signal 树和 mount，构造末尾 flush 让所有 Computed 物化。</p>
 */
public class ConfigScreen extends AbstractSceneHostWidget {

    /** bodyRow 横向间距 */
    private static final int BODY_ROW_GAP = 12;
    /** 标题与字段 presentation shell 使用 emphasized 级联进入。 */
    private static final int SECTION_REVEAL_MS = ConfigTheme.MOTION_EMPHASIZED_MS;
    /** 从上方明显落位；全程满 opacity，不制造白/透明闪帧。 */
    private static final float SECTION_REVEAL_START_OFFSET_Y = -22.0f;
    /** 相邻标题/卡片的启动间隔。 */
    private static final int SECTION_REVEAL_ITEM_DELAY_MS = 36;
    /** 长 section 的最大级联窗口，避免尾项等待数秒。 */
    private static final int SECTION_REVEAL_MAX_DELAY_MS = 252;
    /** 配置主视口滚轮平滑收敛时长。 */
    private static final int SCROLL_MOTION_MS = ConfigTheme.MOTION_STANDARD_MS;

    /** 配置管理器，保存事务入口 */
    private final ConfigManager manager;
    /** 草稿 signal 适配器 */
    private final DraftSignalAdapter adapter;
    /** 字段渲染器注册表 */
    private final FieldRendererRegistry registry;
    /** 关联的 schema */
    private final ConfigSchema schema;
    /** 恢复默认字段策略，可为 null（全部走默认恢复） */
    private final FieldRestorePolicy restorePolicy;

    /** 场景树根节点 */
    private SceneNode root;
    /** 滚动视口节点 */
    private SceneNode viewport;
    /** 视口内容容器节点（任一时刻仅一个 live panel） */
    private SceneNode content;
    /** 纵向滚动受控源（per-section：每个 section 独立保持滚动位置，切换不丢失） */
    private Signal<Integer>[] sectionScrolls;
    /** 当前 live section 的动态滚动显示源，clamp 到当前 maxScroll 并订阅 layoutDoneSignal。 */
    private ReadableSignal<Integer> activeScroll;
    /** 滚动条/编程式滚动写入回调（clamp 后写 authority 并立即定位）。 */
    private Consumer<Integer> setScroll;
    /** 滚轮平滑滚动的 keyed Motion。 */
    private final Object scrollMotionKey = new Object();
    /** 输入 handler 只递增 intent；同帧 flush 再应用 Motion/节点，守 signal-first。 */
    private final Signal<Integer> scrollIntentRevision = Signal.create(Integer.valueOf(0));
    /** 同帧连续输入合并后的目标。 */
    private int pendingScrollTarget;
    /** 是否有尚未 flush 的滚动意图。 */
    private boolean scrollIntentPending;
    /** pending intent 是否应走平滑滚轮轨道；false 表示 scrollbar 直接接管。 */
    private boolean pendingScrollSmooth;
    /** 单调 intent 序号，避免相同 offset 的直接接管被 Signal 相等合并。 */
    private int scrollIntentRevisionCounter;
    /** 当前是否正在把 viewport 从显示值收敛到滚轮目标。 */
    private boolean smoothScrollRunning;
    /** 当前滚轮动画最终目标，用于忽略 authority bind 对同一目标的立即回写。 */
    private int smoothScrollTarget;
    /** 标题条节点 */
    private SceneNode titleBar;
    /** 状态摘要条节点 */
    private SceneNode statusSummary;
    /** 操作条节点 */
    private SceneNode actionBar;
    /** 固定左侧导航根节点；0/1 section 时为 null。 */
    private SceneNode navRoot;
    /** 多 section 时的 navigation + content 双栏节点。 */
    private SceneNode bodyRow;
    /** 滚动容器（ROW：viewport + scrollbar 列），承载 viewport 并在其右侧叠加滚动条 */
    private SceneNode scrollContainer;
    /** 滚动条列节点（scrollContainer 内 viewport 右侧的独立列） */
    private SceneNode scrollbarColumn;
    /** 滚动条 thumb，滚轮 Motion 与内容使用同一进度同步位置。 */
    private SceneNode scrollbarThumb;

    /** 导航请求的 section 下标（受控源），selection indicator 立即跟随。 */
    private Signal<Integer> activeSectionSignal;
    /** schema section 快照，供单槽 panel 切换挂载。 */
    private List<SectionSpec> sections;
    /** 单 live section 的专用 Owner；每次切换严格先 dispose outgoing 再 mount incoming。 */
    private Owner sectionOwner;
    /** 当前单槽 panel 的 mount 句柄。 */
    private MountHandle sectionMount;
    /** 当前 live section 下标。 */
    private int displayedSectionIndex;

    /** UI 构造作用域，所有 Computed/Effect 归属此 Owner，dispose 时统一回收 */
    private final Owner uiOwner = new Owner();

    /** 最近一次保存结果，供测试探针 */
    private SaveOutcome lastSaveOutcome;

    /**
     * 创建配置页 UI 骨架。
     *
     * @param input    平台输入源，可为 null（headless 测试）
     * @param manager  配置管理器
     * @param adapter  草稿 signal 适配器
     * @param registry 字段渲染器注册表
     */
    public ConfigScreen(PlatformInputSource input, ConfigManager manager,
                        DraftSignalAdapter adapter, FieldRendererRegistry registry) {
        this(input, manager, adapter, registry, null);
    }

    /**
     * 创建配置页 UI 骨架。
     *
     * @param input         平台输入源，可为 null（headless 测试）
     * @param manager       配置管理器
     * @param adapter       草稿 signal 适配器
     * @param registry      字段渲染器注册表
     * @param restorePolicy 恢复默认字段策略，可为 null（全部走默认恢复）
     */
    public ConfigScreen(PlatformInputSource input, ConfigManager manager,
                        DraftSignalAdapter adapter, FieldRendererRegistry registry,
                        FieldRestorePolicy restorePolicy) {
        super(input);
        if (manager == null) {
            throw new IllegalArgumentException("manager must not be null");
        }
        if (adapter == null) {
            throw new IllegalArgumentException("adapter must not be null");
        }
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }
        DraftBuffer ownedDraft = adapter.draft();
        if (ownedDraft == null || !manager.owns(ownedDraft)) {
            throw new IllegalArgumentException(
                    "ConfigScreen requires manager.owns(adapter.draft()); "
                            + "use the same ConfigManager that opened the draft");
        }
        this.manager = manager;
        this.adapter = adapter;
        this.registry = registry;
        this.schema = adapter.draft().schema();
        this.restorePolicy = restorePolicy;
        runtime.__enableMotion();

        // 在 uiOwner 作用域内构造，所有 Computed/Effect 归属 uiOwner，dispose 时统一回收
        uiOwner.run(() -> {
            // 用 FormPageShell.build 构建统一口径骨架（root/viewport/scrollContainer），
            // attachScroll=false：shell 不建 scrollSignal/scrollbar（ConfigScreen 自建 per-section）。
            // buildTitleBar=false：跳过 shell 标题条构造（shell 的 text 不设字号，无法满足
            // FONT_TITLE/FONT_SUBTITLE 需求），ConfigScreen 自建 createTitleBar 直接挂 root。
            // shell 复用通用 FormTheme；配置页 root 随后覆盖为专用半透明遮罩。
            // 尺寸与其它 tonal surface 统一取 ConfigTheme。
            FormPageShell.Parts parts = FormPageShell.build(runtime,
                    schema.title(), "modId: " + schema.modId(),
                    ConfigTheme.TITLE_BAR_HEIGHT, ConfigTheme.ROOT_PADDING, ConfigTheme.ROOT_GAP,
                    14, 14, 10,
                    false, false, ConfigTheme.asFormTheme());
            this.root = parts.root();
            this.viewport = parts.viewport();
            this.scrollContainer = parts.scrollContainer();
            root.setBackgroundColor(ConfigTheme.ROOT_BG);
            root.setCrossAxisAlign(CrossAxisAlign.CENTER);
            viewport.setCrossAxisAlign(CrossAxisAlign.CENTER);
            viewport.setCornerRadius(club.heiqi.uilib.ui.scene.paint.SceneChromeTokens.RADIUS_LG);
            scrollContainer.setGap(ConfigTheme.SCROLL_GAP);

            // shell.build 在 buildTitleBar=false 时只挂了 scrollContainer 到 root；ConfigScreen 需特化：
            // 1) titleBar 字号 shell 不支持 → 自建 createTitleBar 挂 root 首位
            // 2) 多 section 要把 scrollContainer 挂进统一侧栏 bodyRow → 摘下重挂
            root.removeChild(scrollContainer);

            this.titleBar = createTitleBar();
            root.appendChild(titleBar);

            // 操作条固定在页面底部，保存始终位于视觉终点且不随内容滚动。
            this.actionBar = createActionBar();

            this.statusSummary = createStatusSummary();
            root.appendChild(statusSummary);

            this.sections = schema.sections();
            this.activeSectionSignal = Signal.create(Integer.valueOf(0));
            this.displayedSectionIndex = 0;
            this.sectionOwner = uiOwner.createChild();

            this.content = createContent();
            viewport.appendChild(content);
            renderFields(sections);
            // 导航请求立即替换单槽；新 Owner 自带 layout-ready 级联，快速重选会自动取消旧序列。
            runtime.bind(activeSectionSignal, this::requestSectionTransition);

            // 滚动容器（ROW：viewport + scrollbar 列），承载 viewport 并在其右侧叠加滚动条。
            // navPane 与底部 actionBar 都在滚动容器外；scrollContainer 仅承载 viewport + scrollbar。
            // viewport 已由 shell.build 挂入 scrollContainer，此处不再重复 appendChild。
            if (sections.size() > 1) {
                // Material settings page：section 数量不再改变导航模式，统一使用固定左侧导航。
                this.bodyRow = SceneNode.row();
                bodyRow.setFillParentHeight(true);
                bodyRow.setFillParentWidth(true);
                bodyRow.setMaxWidth(ConfigTheme.PAGE_MAX_WIDTH);
                bodyRow.setGap(BODY_ROW_GAP);
                this.navRoot = createSidebarNav(bodyRow, sections);
                scrollContainer.setFlexGrow(1);
                bodyRow.appendChild(scrollContainer);
                root.appendChild(bodyRow);
            } else {
                // 0 或 1 section：无需导航，直接挂 scrollContainer
                scrollContainer.setFillParentWidth(true);
                scrollContainer.setMaxWidth(ConfigTheme.CONTENT_MAX_WIDTH);
                root.appendChild(scrollContainer);
            }

            // S4：save 反馈独立行，rt.show 懒挂载（saveFeedbackSignal 非 NONE 时显示，NONE 时隐藏不占高，守 I7）。
            // 挂在 scrollContainer 之后（root COLUMN 内）——反馈靠近底部，actionBar 已在顶部，反馈不挤占操作行视觉。
            rt().show(root,
                    Computed.create(() -> {
                        SaveFeedback fb = adapter.saveFeedbackSignal().get();
                        return Boolean.valueOf(fb != null && !fb.isNone());
                    }),
                    this::createSaveFeedbackBar);

            root.appendChild(actionBar);

            // ===== BUG2 修复：per-section scroll state（section 切换不丢失滚动位置）=====
            // 每个 section 独立持有一个 Signal<Integer>，切换 section 时显示源切到对应 signal，
            // 切回时恢复原滚动位置。动态显示源 clamp 到当前 maxScroll 并订阅 layoutDoneSignal，
            // 写入回调始终写当前 live section 的 signal。
            @SuppressWarnings("unchecked")
            Signal<Integer>[] scrolls = new Signal[sections.size()];
            for (int i = 0; i < sections.size(); i++) {
                scrolls[i] = Signal.create(Integer.valueOf(0));
            }
            this.sectionScrolls = scrolls;

            // 不用 Computed 缓存 displayedSectionIndex：Motion phase 是非 signal 状态，动态 get 可确保
            // 滚轮 handler 与属性 bind 始终读取当前 live panel；layoutDoneSignal 仍负责布局后重跑 clamp。
            this.activeScroll = new ReadableSignal<Integer>() {
                @Override
                public Integer get() {
                    int idx = displayedSectionIndex;
                    if (idx < 0 || idx >= scrolls.length) {
                        return Integer.valueOf(0);
                    }
                    int raw = scrolls[idx].get().intValue();
                    runtime.layoutDoneSignal().get();
                    Object cached = viewport.getCachedLayout();
                    if (!(cached instanceof LayoutBox)) {
                        return Integer.valueOf(Math.max(0, raw));
                    }
                    int maxScroll = SceneGeometry.maxScrollY(viewport);
                    return Integer.valueOf(Math.max(0, Math.min(maxScroll, raw)));
                }
            };
            // 输入 handler 只写 intent signal；flush effect 决定平滑滚轮或 scrollbar 直接定位。
            this.setScroll = this::requestDirectScroll;
            runtime.bind(scrollIntentRevision, ignored -> applyPendingScrollIntent());
            runtime.bind(activeScroll, value -> {
                int target = value.intValue();
                if ((!smoothScrollRunning || target != smoothScrollTarget)
                        && viewport.getScrollOffsetY() != target) {
                    snapScrollOffset(target);
                }
            });
            runtime.on(viewport, SceneEventType.SCROLL, (event, context) -> {
                if (smoothScrollByWheelDelta(event.getWheelDelta())) {
                    context.stopPropagation();
                }
            });
            // 项4：滚动条叠加在 viewport 右侧（scrollContainer ROW 内 viewport 旁的独立列），
            // 反映滚动位置/可滚动范围。几何由 bind 派生（订阅 activeScroll + rt.layoutDoneSignal），
            // 守 I7/I11/I4。P0：scrollbar 内部直接订阅 rt.layoutDoneSignal()——
            // host 在 post-flush 主树与 overlay 完成布局后桥接最终 epoch，scrollbar 同帧 flush
            // 内重跑 effect 读最新 LayoutBox，
            // 零滞后覆盖 section 切换 + 窗口 resize 两种 content 高度变化场景。
            // BUG2：Props 拆 read/write——activeScroll 为动态 authority 目标，
            // setScroll 供 thumb 拖动/track 点击直接定位；滚轮由 viewport handler 单独平滑处理。
            // Scrollbar 的 handler/dragStart 必须读取当前可见 offset，而非滚轮已提前写入的终点 authority。
            // 仍读取 activeScroll 建立响应式依赖；逐帧位置由 applyScrollOffset 同步到 thumb。
            ReadableSignal<Integer> scrollbarDisplayScroll = new ReadableSignal<Integer>() {
                @Override
                public Integer get() {
                    activeScroll.get();
                    return Integer.valueOf(viewport.getScrollOffsetY());
                }
            };
            SceneScrollbar.Props sbProps = new SceneScrollbar.Props(
                    viewport, scrollbarDisplayScroll, setScroll,
                    SceneScrollbar.DEFAULT_TRACK_COLOR, SceneScrollbar.DEFAULT_THUMB_COLOR,
                    SceneScrollbar.DEFAULT_BAR_WIDTH, SceneScrollbar.DEFAULT_MIN_THUMB_HEIGHT,
                    setScroll);
            SceneScrollbar.Result sb = SceneScrollbar.create(runtime, sbProps);
            this.scrollbarColumn = sb.column();
            this.scrollbarThumb = sb.thumb();
            scrollContainer.appendChild(scrollbarColumn);
        });

        // 首帧 flush：让所有 Computed 物化（flush 前返回 null）
        runtime.flush();
    }

    /**
     * 创建固定标题条。
     *
     * <p>m2：主标题用 {@link ConfigSchema#title()}（人类可读，缺省回退 modId），
     * 副标题显示 modId 技术标识。</p>
     *
     * <p>注：root/viewport/scrollContainer 骨架由 {@link FormPageShell#build} 统一构建，
     * titleBar 因字号需求（FONT_TITLE=22/FONT_SUBTITLE=12，shell 的 text 不设字号）保留自建——
     * 构造期已传 buildTitleBar=false 跳过 shell 标题条构造，此处直接挂自建标题条到 root。</p>
     *
     * @return 标题条节点
     */
    private SceneNode createTitleBar() {
        SceneNode bar = SceneNode.column();
        bar.setPreferredHeight(ConfigTheme.TITLE_BAR_HEIGHT);
        bar.setFillParentWidth(true);
        bar.setMaxWidth(ConfigTheme.PAGE_MAX_WIDTH);
        bar.setGap(2);
        bar.setHitTestable(false);
        bar.appendChild(text(schema.title(), ConfigTheme.TITLE_COLOR, ConfigTheme.FONT_TITLE));
        bar.appendChild(text("modId: " + schema.modId(), ConfigTheme.MUTED_COLOR, ConfigTheme.FONT_SUBTITLE));
        return bar;
    }

    /**
     * 创建固定状态摘要条：dirty 计数徽标 + error 计数徽标。
     *
     * <p>S4：save 反馈已拆出为独立行（{@link #createSaveFeedbackBar}），不再挤在本行。</p>
     *
     * @return 状态摘要节点
     */
    private SceneNode createStatusSummary() {
        SceneNode row = SceneNode.row();
        row.setPreferredHeight(ConfigTheme.STATUS_HEIGHT);
        row.setFillParentWidth(true);
        row.setMaxWidth(ConfigTheme.PAGE_MAX_WIDTH);
        row.setGap(10);

        // 脏字段计数徽标：「N 项未保存」/「无未保存更改」
        row.appendChild(badge(
                Computed.create(() -> {
                    int n = safeCount(adapter.dirtyCountSignal().get());
                    return n > 0 ? n + " 项未保存" : "无未保存更改";
                }),
                Computed.create(() -> {
                    int n = safeCount(adapter.dirtyCountSignal().get());
                    return n > 0 ? ConfigTheme.DIRTY_COLOR : ConfigTheme.OK_COLOR;
                })));
        // 错误字段计数徽标：「N 项校验错误」/「校验通过」
        row.appendChild(badge(
                Computed.create(() -> {
                    int n = safeCount(adapter.errorCountSignal().get());
                    return n > 0 ? n + " 项校验错误" : "校验通过";
                }),
                Computed.create(() -> {
                    int n = safeCount(adapter.errorCountSignal().get());
                    return n > 0 ? ConfigTheme.ERROR_COLOR : ConfigTheme.OK_COLOR;
                })));

        return row;
    }

    /**
     * 创建 save 反馈独立行（S4）：仅在 {@code saveFeedbackSignal} 非 NONE 时挂载，
     * NONE 时隐藏不占高（守 I7，rt.show 懒挂载）。
     *
     * <p>requiresReload 冲突时额外挂「丢弃编辑并重新加载」按钮行（组件只建一次，
     * 显隐由 Signal/Computed + rt.show 驱动，守 I1/I3/I9；不自动 reload/merge）。</p>
     *
     * @return save 反馈条节点（condition 为 true 时显示）
     */
    private SceneNode createSaveFeedbackBar() {
        SceneNode col = SceneNode.column();
        col.setGap(6);
        col.setHitTestable(true);
        col.setFillParentWidth(true);
        col.setMaxWidth(ConfigTheme.PAGE_MAX_WIDTH);
        col.setBackgroundColor(ConfigTheme.SURFACE_CONTAINER);
        col.setCornerRadius(club.heiqi.uilib.ui.scene.paint.SceneChromeTokens.RADIUS_MD);
        col.setPadding(8);
        // 固定 preferredHeight：作为 root COLUMN 内固定子，未设则 grow 求解器 UNCONSTRAINED 早退。
        // 预留 reload 按钮行高度（即使当前不显示，高度略余可接受；冲突态可完整显示按钮）。
        col.setPreferredHeight(ConfigTheme.SAVE_FEEDBACK_HEIGHT + 6 + ConfigTheme.BUTTON_HEIGHT);

        SceneNode row = SceneNode.row();
        row.setGap(8);
        row.setHitTestable(false);
        row.setPreferredHeight(ConfigTheme.SAVE_FEEDBACK_HEIGHT);
        SceneNode feedback = text("", ConfigTheme.MUTED_COLOR, ConfigTheme.FONT_ERROR);
        runtime.bindComputed(() -> {
                    SaveFeedback fb = adapter.saveFeedbackSignal().get();
                    return fb == null ? "" : fb.message();
                },
                feedback::setText);
        runtime.bindComputed(() -> {
                    SaveFeedback fb = adapter.saveFeedbackSignal().get();
                    if (fb == null || fb.isNone()) {
                        return ConfigTheme.MUTED_COLOR;
                    }
                    return fb.isError() ? ConfigTheme.ERROR_COLOR : ConfigTheme.OK_COLOR;
                },
                feedback::setTextColor);
        row.appendChild(feedback);
        col.appendChild(row);

        // reload 按钮：condition = requiresReload；Supplier 只跑一次建按钮（I3）
        rt().show(col,
                Computed.create(() -> Boolean.valueOf(adapter.requiresReload())),
                this::createReloadButtonRow);

        return col;
    }

    /**
     * 创建「丢弃编辑并重新加载」按钮行（仅 requiresReload 时由 rt.show 挂载一次）。
     *
     * @return 按钮行节点
     */
    private SceneNode createReloadButtonRow() {
        SceneNode row = SceneNode.row();
        row.setGap(8);
        row.setPreferredHeight(ConfigTheme.BUTTON_HEIGHT);
        SceneButton.Props props = new SceneButton.Props(
                Signal.create("丢弃编辑并重新加载"),
                Signal.create(Boolean.TRUE),
                this::discardEditsAndReload,
                SceneButtonVariant.STANDARD);
        MountHandle handle = runtime.mount(row, SceneButton.create(runtime, props));
        SceneNode btnRoot = handle.getRoot();
        if (btnRoot != null) {
            btnRoot.setPreferredWidth(ConfigTheme.BUTTON_WIDTH + 40);
            btnRoot.setPreferredHeight(ConfigTheme.BUTTON_HEIGHT);
        }
        return row;
    }

    /**
     * 丢弃当前编辑并从磁盘重新加载（{@link ConfigManager#reloadDraftFromDisk}），
     * 经 {@link DraftSignalAdapter#replaceDraft} 保持 Signal identity；恢复可保存。
     * 不得自动 merge / 静默覆盖 Authority。
     *
     * <p>IO / 校验 / 冲突失败时：按 {@link ConfigReloadException.Reason} 结构化显示
     *（禁止英文匹配）；失败保留编辑 / requiresReload 冲突态；不静默折叠 NUMBER 为 0.0、
     * 不推进 Authority。</p>
     */
    private void discardEditsAndReload() {
        try {
            DraftBuffer fresh = manager.reloadDraftFromDisk();
            adapter.replaceDraft(fresh);
        } catch (club.heiqi.config.runtime.ConfigConflictException e) {
            // 通知期等：保留冲突与编辑
            adapter.setSaveFeedback(SaveFeedback.forConflict(e.conflictType()));
        } catch (club.heiqi.config.runtime.ConfigReloadException e) {
            SaveFeedback.Status status;
            String prefix;
            switch (e.reason()) {
                case VALIDATION:
                    status = SaveFeedback.Status.INVALID;
                    prefix = "重新加载校验失败";
                    break;
                case CONFLICT:
                    status = SaveFeedback.Status.CONFLICT;
                    if (e.conflictType() != null
                            && e.conflictType() != club.heiqi.config.runtime.SaveOutcome.ConflictType.NONE) {
                        adapter.setSaveFeedback(SaveFeedback.forConflict(e.conflictType()));
                        return;
                    }
                    prefix = "重新加载冲突";
                    break;
                case IO:
                default:
                    status = SaveFeedback.Status.IO_FAILED;
                    prefix = "重新加载失败";
                    break;
            }
            adapter.setSaveFeedback(new SaveFeedback(
                    status,
                    prefix + "。当前编辑已保留。"));
        } catch (club.heiqi.config.ConfigException e) {
            // 兼容其它 ConfigException：按 IO 友好反馈，不匹配英文
            adapter.setSaveFeedback(new SaveFeedback(
                    SaveFeedback.Status.IO_FAILED,
                    "重新加载失败。当前编辑已保留。"));
        }
    }


    /**
     * null 安全计数读取（flush 前 Computed 可能返回 null）。
     *
     * @param v 计数 signal 值
     * @return 非 null 计数
     */
    private static int safeCount(Integer v) {
        return v == null ? 0 : v.intValue();
    }

    /** 滚轮目标合并到当前 section authority，并从当前显示位置平滑收敛。 */
    private boolean smoothScrollByWheelDelta(int wheelDelta) {
        // Signal.set 在 flush 前不可见；运行中或同帧 pending 目标承担连续事件累计基准。
        int currentTarget = scrollIntentPending
                ? pendingScrollTarget
                : (smoothScrollRunning ? smoothScrollTarget : activeScroll.get().intValue());
        int target = clampScrollOffset(currentTarget - wheelDelta);
        if (target == currentTarget) {
            return false;
        }
        requestScrollIntent(target, true);
        return true;
    }

    /** 滚动条拖动、track 点击与编程式写入请求在本帧 flush 直接定位。 */
    private void requestDirectScroll(Integer requested) {
        int target = clampScrollOffset(requested == null ? 0 : requested.intValue());
        requestScrollIntent(target, false);
    }

    /** handler 的唯一写入口：合并最新目标并递增 intent signal，不触碰节点或 Motion。 */
    private void requestScrollIntent(int target, boolean smooth) {
        pendingScrollTarget = target;
        pendingScrollSmooth = smooth;
        scrollIntentPending = true;
        scrollIntentRevision.set(Integer.valueOf(++scrollIntentRevisionCounter));
    }

    /** flush effect 消费最终 intent；Motion 初始 applier 与直接 snap 都已脱离输入调用栈。 */
    private void applyPendingScrollIntent() {
        if (!scrollIntentPending) {
            return;
        }
        int target = pendingScrollTarget;
        boolean smooth = pendingScrollSmooth;
        scrollIntentPending = false;
        if (smooth) {
            startSmoothScroll(target);
        } else {
            snapScrollOffset(target);
        }
        writeActiveSectionScroll(target);
    }

    /** 写当前 live section 的滚动 authority。 */
    private void writeActiveSectionScroll(int target) {
        int idx = displayedSectionIndex;
        if (sectionScrolls != null && idx >= 0 && idx < sectionScrolls.length) {
            sectionScrolls[idx].set(Integer.valueOf(target));
        }
    }

    /** 把目标限制到当前 viewport 可滚范围。 */
    private int clampScrollOffset(int target) {
        return Math.max(0, Math.min(SceneGeometry.maxScrollY(viewport), target));
    }

    /** 启动滚轮轨道；运行期间的新目标先累计，由下一次 sample 从当时显示值重定向。 */
    private void startSmoothScroll(int target) {
        int to = clampScrollOffset(target);
        if (smoothScrollRunning) {
            int current = viewport.getScrollOffsetY();
            long oldDelta = (long) smoothScrollTarget - current;
            long newDelta = (long) to - current;
            smoothScrollTarget = to;
            if (newDelta == 0L) {
                snapScrollOffset(to);
            } else if (oldDelta * newDelta < 0L) {
                // 反向滚轮立即截断旧方向；同向连续输入则保留到本帧 sample 后无停顿重定向。
                startSmoothScrollSegment(to);
            }
            return;
        }
        smoothScrollTarget = to;
        if (viewport.getScrollOffsetY() == to) {
            snapScrollOffset(to);
            return;
        }
        smoothScrollRunning = true;
        startSmoothScrollSegment(to);
    }

    /**
     * 跑一个从当前显示值到快照目标的段。若 route 在本帧写入了更新目标，先采样旧段一次，
     * 再在同一 sample 边界从刚得到的显示值重建轨道，避免持续滚轮每帧重启在 progress=0。
     */
    private void startSmoothScrollSegment(int segmentTarget) {
        int from = viewport.getScrollOffsetY();
        int to = clampScrollOffset(segmentTarget);
        // 动态内容收缩可能让旧目标越界；同步收敛字段，避免按旧值重复重建同一 clamp 段。
        smoothScrollTarget = to;
        if (from == to) {
            runtime.__cancelMotion(scrollMotionKey);
            smoothScrollRunning = false;
            return;
        }
        runtime.__startEaseOutMotion(scrollMotionKey, SCROLL_MOTION_MS,
                progress -> {
                    applyScrollOffset(Math.round(from + (to - from) * progress.floatValue()));
                    if (smoothScrollRunning && smoothScrollTarget != to) {
                        startSmoothScrollSegment(smoothScrollTarget);
                    }
                },
                () -> {
                    applyScrollOffset(to);
                    if (smoothScrollTarget != to) {
                        startSmoothScrollSegment(smoothScrollTarget);
                    } else {
                        smoothScrollRunning = false;
                    }
                });
    }

    /** 立即定位并取消尚未完成的滚轮轨道；用于 section 切换和 scrollbar 拖动。 */
    private void snapScrollOffset(int target) {
        boolean needsApply = smoothScrollRunning || viewport.getScrollOffsetY() != target;
        runtime.__cancelMotion(scrollMotionKey);
        smoothScrollRunning = false;
        smoothScrollTarget = target;
        if (needsApply) {
            applyScrollOffset(target);
        }
    }

    /** 同帧更新内容 geometry、thumb composite，并请求按新滚动几何重算 hover。 */
    private void applyScrollOffset(int offset) {
        boolean geometryChanged = viewport.getScrollOffsetY() != offset;
        viewport.setScrollOffsetY(offset);
        syncScrollbarThumb(offset);
        if (geometryChanged) {
            runtime.__requestHoverReconcileAfterScroll();
        }
    }

    /** 让 thumb 与平滑内容使用同一 offset，避免 authority 已到终点而 thumb 先跳过去。 */
    private void syncScrollbarThumb(int offset) {
        if (scrollbarThumb == null) {
            return;
        }
        Object viewportLayout = viewport.getCachedLayout();
        Object thumbLayout = scrollbarThumb.getCachedLayout();
        if (!(viewportLayout instanceof LayoutBox) || !(thumbLayout instanceof LayoutBox)) {
            return;
        }
        int maxScroll = SceneGeometry.maxScrollY(viewport);
        if (maxScroll <= 0) {
            scrollbarThumb.setTransform(Transform.translate(0.0f, 0.0f));
            return;
        }
        int trackRange = Math.max(0,
                ((LayoutBox) viewportLayout).getHeight() - ((LayoutBox) thumbLayout).getHeight());
        float thumbY = (float) trackRange * Math.max(0, Math.min(maxScroll, offset)) / maxScroll;
        scrollbarThumb.setTransform(Transform.translate(0.0f, thumbY));
    }

    /**
     * 创建纵向 SceneNavList 侧栏导航，直接 mount 到 bodyRow。
     *
     * @param parent   bodyRow 父节点
     * @param sections section 列表
     * @return navPane 根节点（已 mount 到 parent）
     */
    private SceneNode createSidebarNav(SceneNode parent, List<SectionSpec> sections) {
        List<String> titles = new ArrayList<String>();
        for (SectionSpec s : sections) {
            titles.add(s.title());
        }
        SceneNavList.Props props = new SceneNavList.Props(
                activeSectionSignal,
                titles,
                Signal.create(Boolean.TRUE),
                idx -> activeSectionSignal.set(Integer.valueOf(idx)),
                null); // preferredHeight 不设，由布局链决定（NavList 高度随项数变化）
        MountHandle handle = runtime.mount(parent, SceneNavList.create(runtime, props));
        SceneNode nav = handle.getRoot();
        if (nav != null) {
            nav.setPreferredWidth(ConfigTheme.NAV_PANE_WIDTH);
            nav.setFillParentHeight(true);
            nav.setBackgroundColor(ConfigTheme.SURFACE_CONTAINER);
            nav.setCornerRadius(club.heiqi.uilib.ui.scene.paint.SceneChromeTokens.RADIUS_LG);
            nav.setPadding(8);
        }
        return nav;
    }

    /**
     * 创建视口内容容器。
     *
     * @return 内容节点
     */
    private SceneNode createContent() {
        SceneNode node = SceneNode.column();
        node.setGap(14);
        node.setFillParentWidth(true);
        node.setMaxWidth(ConfigTheme.CONTENT_MAX_WIDTH);
        return node;
    }

    /** 初始挂载单槽 section panel；后续切换沿用同一个 sectionOwner。 */
    private void renderFields(List<SectionSpec> sections) {
        if (!sections.isEmpty()) {
            mountSectionPanel(0);
        }
    }

    /** 接收导航目标；立即切换单槽，outgoing Owner 负责取消尚未完成的 reveal。 */
    private void requestSectionTransition(Integer requested) {
        int target = normalizeSectionIndex(requested);
        if (target < 0 || target == displayedSectionIndex) {
            return;
        }
        requestDismissOutgoingOverlays();
        switchSectionPanel(target);
    }

    /** 请求所有 active Config overlay 走各自受控 dismiss 回调；实际摘除仍由 portal signal 派生。 */
    private void requestDismissOutgoingOverlays() {
        for (SceneOverlayHost.Entry entry : runtime.getOverlayHost().topFirst()) {
            entry.requestDismiss();
        }
    }

    /** 把外部 section 下标收敛到 schema 范围；空 schema 返回 -1。 */
    private int normalizeSectionIndex(Integer requested) {
        int count = sections.size();
        if (count <= 0) {
            return -1;
        }
        int raw = requested == null ? 0 : requested.intValue();
        return Math.max(0, Math.min(count - 1, raw));
    }

    /**
     * 构建单个 section 面板：sectionTitle + 遍历 fields 调 registry.render 挂卡片。
     *
     * @param section section 元数据
     * @return section 面板节点
     */
    private SceneNode buildSectionPanel(SectionSpec section) {
        SceneNode sectionNode = SceneNode.column();
        sectionNode.setGap(ConfigTheme.FIELD_GAP);
        sectionNode.setFillParentWidth(true);
        SceneNode sectionTitle = text(section.title(), ConfigTheme.TITLE_COLOR, ConfigTheme.FONT_SECTION);
        sectionNode.appendChild(sectionTitle);
        List<SceneNode> revealTargets = new ArrayList<SceneNode>();
        revealTargets.add(sectionTitle);
        for (FieldSpec field : section.fields()) {
            FieldRenderer renderer = registry.resolve(field);
            if (renderer != null) {
                SceneNode card = renderer.render(runtime, field, adapter);
                // Presentation shell 独占页面级位移；字段卡片本身不承担 reveal 状态。
                SceneNode presentation = SceneNode.column();
                presentation.setFillParentWidth(true);
                presentation.setHitTestable(false);
                presentation.appendChild(card);
                sectionNode.appendChild(presentation);
                revealTargets.add(presentation);
            }
        }
        runtime.__staggeredReveal(revealTargets,
                SECTION_REVEAL_START_OFFSET_Y,
                SECTION_REVEAL_MS,
                SECTION_REVEAL_ITEM_DELAY_MS,
                SECTION_REVEAL_MAX_DELAY_MS);
        return sectionNode;
    }

    /** 在 sectionOwner 下挂载指定 panel，确保动态切换仍归 ConfigScreen 生命周期。 */
    private SceneNode mountSectionPanel(int sectionIndex) {
        MountHandle[] next = new MountHandle[1];
        SectionSpec section = sections.get(sectionIndex);
        sectionOwner.run(() -> next[0] = runtime.mount(content, () -> buildSectionPanel(section)));
        sectionMount = next[0];
        displayedSectionIndex = sectionIndex;
        return sectionMount.getRoot();
    }

    /** 单槽切换：先完整回收 outgoing Owner，再构建 incoming，任何时点都不并存两个 live panel。 */
    private SceneNode switchSectionPanel(int sectionIndex) {
        if (sectionMount != null) {
            sectionMount.dispose();
            sectionMount = null;
        }
        SceneNode incoming = mountSectionPanel(sectionIndex);
        if (sectionScrolls != null && sectionIndex >= 0 && sectionIndex < sectionScrolls.length) {
            snapScrollOffset(Math.max(0, sectionScrolls[sectionIndex].get().intValue()));
        }
        // 即使两个 section 的 offset 相同，旧 hoveredNode 也已随 outgoing 子树卸载。
        runtime.__requestHoverReconcileAfterScroll();
        return incoming;
    }

    /**
     * 取场景运行时（等价 {@code runtime}）。
     *
     * @return 场景运行时
     */
    private SceneRuntime rt() {
        return runtime;
    }

    /**
     * 创建固定操作条：委托 {@link FormActionBar}（恢复默认 / spacer / 取消 + 保存 primary）。
     *
     * <p>S3：左右分区——恢复默认置最左（弱化低频破坏性操作），取消+保存置最右，
     * 保存在最右末位（主操作落在视线终点）。中间插 flexGrow=1 的 spacer 节点撑开剩余宽度
     * （scene MainAxisAlign 无 SPACE_BETWEEN，用 spacer 方案）。</p>
     *
     * <p>该固定行位于 root 末尾，save/cancel/restore 始终可见、不随内容滚动。</p>
     *
     * @return 操作条节点
     */
    private SceneNode createActionBar() {
        FormTheme theme = ConfigTheme.asFormTheme();
        SceneNode bar = FormActionBar.build(runtime,
                Signal.create(Boolean.TRUE), this::restoreDefaults,
                adapter.isDirtySignal(), this::cancelChanges,
                adapter.canSaveSignal(), this::saveChanges,
                theme,
                ConfigTheme.ACTION_BAR_HEIGHT, 10,
                ConfigTheme.BUTTON_WIDTH, ConfigTheme.BUTTON_HEIGHT);
        bar.setFillParentWidth(true);
        bar.setMaxWidth(ConfigTheme.PAGE_MAX_WIDTH);
        bar.setBackgroundColor(ConfigTheme.SURFACE_CONTAINER);
        bar.setCornerRadius(club.heiqi.uilib.ui.scene.paint.SceneChromeTokens.RADIUS_LG);
        bar.setPadding(6);
        return bar;
    }

    /**
     * 保存：mgr.save(draft) + 结构化冲突/校验接入 + saveFeedbackSignal。
     *
     * <p>冲突走 {@link DraftSignalAdapter#applySaveFailure}（读 conflictType，禁止英文匹配）；
     * requiresReload 时不注入字段 error/errorCount，保存保持禁用；不自动 reload/重试/覆盖。</p>
     */
    private void saveChanges() {
        DraftBuffer draft = adapter.draft();
        lastSaveOutcome = manager.save(draft);
        if (lastSaveOutcome.isSuccess()) {
            adapter.afterSaveSync();
            adapter.setSaveFeedback(new SaveFeedback(SaveFeedback.Status.OK, "已保存"));
            return;
        }
        adapter.applySaveFailure(lastSaveOutcome);
    }

    /**
     * 取消：adapter.resetToCurrent()。
     */
    private void cancelChanges() {
        adapter.resetToCurrent();
    }

    /**
     * 恢复默认：按策略逐字段跳过、自定义或 adapter.resetFieldToDefault(path)。
     */
    private void restoreDefaults() {
        for (FieldSpec field : schema.allFields()) {
            String path = field.path();
            if (restorePolicy != null && restorePolicy.isSkipped(path)) {
                continue;
            }
            Consumer<DraftSignalAdapter> custom = restorePolicy != null ? restorePolicy.getCustom(path) : null;
            if (custom != null) {
                custom.accept(adapter);
            } else {
                adapter.resetFieldToDefault(path);
            }
        }
    }

    /**
     * 创建徽标节点。
     *
     * <p>S1：徽标文本用 {@link ConfigTheme#FONT_BADGE} 字号。
     * M4：文本色固定浅色（TEXT_COLOR），仅边框用状态色，拉开对比度。</p>
     *
     * @param label 文案源
     * @param color 颜色源（用于边框）
     * @return 徽标节点
     */
    private SceneNode badge(ReadableSignal<String> label, ReadableSignal<Integer> color) {
        SceneNode node = new SceneNode();
        node.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        node.setPadding(8);
        node.setCornerRadius(999);
        node.setHitTestable(false);
        SceneNode textNode = text("", ConfigTheme.TEXT_COLOR, ConfigTheme.FONT_BADGE);
        node.appendChild(textNode);
        runtime.bind(label, textNode::setText);
        runtime.bind(color, node::setBorderColor);
        node.setBorderWidth(2);
        node.setBackgroundColor(ConfigTheme.READOUT_BG);
        return node;
    }

    /**
     * 创建文字节点（默认字号）。
     *
     * @param value 文本
     * @param color 颜色
     * @return 文字节点
     */
    private SceneNode text(String value, int color) {
        return text(value, color, 16);
    }

    /**
     * 创建文字节点（指定字号）。
     *
     * @param value     文本
     * @param color     颜色
     * @param fontSize  字号（UI 像素）
     * @return 文字节点
     */
    private SceneNode text(String value, int color, int fontSize) {
        SceneNode node = new SceneNode();
        node.setText(value);
        node.setTextColor(color);
        node.setFontSize(fontSize);
        node.setHitTestable(false);
        return node;
    }

    // ==================== 测试探针访问器（包级） ====================

    /** @return 内部场景运行时 */
    SceneRuntime __getRuntime() {
        return runtime;
    }

    /** @return 场景树根节点 */
    SceneNode __getRoot() {
        return root;
    }

    /** @return 滚动视口节点 */
    SceneNode __getViewport() {
        return viewport;
    }

    /** @return 视口内容容器节点 */
    SceneNode __getContent() {
        return content;
    }

    /** @return 标题条节点 */
    SceneNode __getTitleBar() {
        return titleBar;
    }

    /** @return 状态摘要条节点 */
    SceneNode __getStatusSummary() {
        return statusSummary;
    }

    /** @return 操作条节点 */
    SceneNode __getActionBar() {
        return actionBar;
    }

    /** @return 固定左侧导航根节点；0/1 section 时为 null */
    SceneNode __getNavRoot() {
        return navRoot;
    }

    /** @return 多 section 的双栏 bodyRow，否则 null */
    SceneNode __getBodyRow() {
        return bodyRow;
    }

    /** @return 滚动容器节点（ROW：viewport + scrollbar 列） */
    SceneNode __getScrollContainer() {
        return scrollContainer;
    }

    /** @return 滚动条列节点（scrollContainer 内 viewport 右侧独立列） */
    SceneNode __getScrollbarColumn() {
        return scrollbarColumn;
    }

    /** @return 当前活动 section 下标受控源 */
    Signal<Integer> __getActiveSectionSignal() {
        return activeSectionSignal;
    }

    /** @return 当前实际挂载的 section 下标 */
    int __getDisplayedSectionIndex() {
        return displayedSectionIndex;
    }

    /** @return 当前 live section 的动态滚动偏移只读显示源（clamp 到当前 maxScroll） */
    ReadableSignal<Integer> __getActiveScroll() {
        return activeScroll;
    }

    /** @return 滚动偏移直接写入回调（写 authority 并立即定位，用于 scrollbar/测试） */
    Consumer<Integer> __getSetScroll() {
        return setScroll;
    }

    /** 测试探针：注入滚轮 delta，返回是否产生了新滚动目标。 */
    boolean __scrollByWheelDeltaForTest(int wheelDelta) {
        return smoothScrollByWheelDelta(wheelDelta);
    }

    /** @return per-section scroll signals（每个 section 独立保持滚动位置） */
    Signal<Integer>[] __getSectionScrolls() {
        return sectionScrolls;
    }

    /** @return 草稿适配器 */
    DraftSignalAdapter __getAdapter() {
        return adapter;
    }

    /** @return 最近一次保存结果 */
    SaveOutcome __getLastSaveOutcome() {
        return lastSaveOutcome;
    }

    /** 测试探针：触发保存 */
    void __saveChanges() {
        saveChanges();
    }

    /** 测试探针：触发取消 */
    void __cancelChanges() {
        cancelChanges();
    }

    /** 测试探针：触发恢复默认 */
    void __restoreDefaults() {
        restoreDefaults();
    }

    /** 测试探针：触发丢弃编辑并重新加载 */
    void __discardEditsAndReload() {
        discardEditsAndReload();
    }

    @Override
    protected SceneNode getRoot() {
        return root;
    }

    /**
     * 销毁配置页：先回收 adapter 的 Computed，再回收 uiOwner 作用域内所有 Computed/Effect，
     * 最后销毁 scene runtime。
     */
    @Override
    public void dispose() {
        try {
            runtime.__cancelMotion(scrollMotionKey);
            try {
                adapter.dispose();
            } finally {
                uiOwner.dispose();
            }
        } finally {
            super.dispose();
        }
    }
}
