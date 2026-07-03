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
import club.heiqi.uilib.ui.scene.form.FormPageShell;
import club.heiqi.uilib.ui.scene.host.AbstractSceneHostWidget;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneButtonVariant;
import club.heiqi.uilib.ui.scene.control.SceneNavList;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.control.SceneSegmented;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 配置页 UI 骨架，extends {@link AbstractSceneHostWidget}。
 *
 * <p>结构（按 section 数量自动选导航形态，守 I3/I7：section 切换用 {@code rt.show} 懒挂卸）：</p>
 * <pre>
 * root (COLUMN, fillParentHeight, padding=12, gap=8, bg=ROOT_BG)
 *   ├ titleBar      (固定高 32)  schema.title() + modId + 【P2占位】搜索框槽
 *   ├ statusSummary (固定高 24)  dirty/error 计数徽标
 *   ├ [≤5 section] navBar (SceneSegmented 横向页签)
 *   │   [>5 section] bodyRow (ROW, gap=12)
 *   │       ├ navPane  (SceneNavList 纵向受控单选，固定宽 160)
 *   │       └ scrollContainer (ROW, gap=3, fillParentHeight)
 *   │           ├ viewport (scrollable, flexGrow=1, fillParentHeight, clip, bg=VIEWPORT_BG, radius=10)
 *   │           │   └ content (COLUMN, gap=14)
 *   │           │       └ 对每个 section i：rt.show(content, activeSection==i, () -> sectionPanel(i))
 *   │           └ scrollbarColumn (SceneScrollbar, 固定宽 8, fillParentHeight, hitTestable=true)  ← M2 滚轮转发
 *   ├ saveFeedbackBar (rt.show 懒挂载，saveFeedbackSignal 非 NONE 时显示)  ← S4 独立行
 *   └ actionBar     (固定高 36)  恢复默认 / spacer / 取消(enabled=isDirty) / 保存(enabled=canSave, primary)
 * </pre>
 *
 * <h3>项2/3 布局语义</h3>
 * <ul>
 *   <li>navBar 固定不参与滚动（root COLUMN 内的固定行，或 bodyRow 内的固定宽列）。</li>
 *   <li>actionBar 在滚动容器外侧底部（root COLUMN 最后一个固定行），save/cancel/restore 始终可见。</li>
 *   <li>scrollContainer 仅在原 viewport 位置外包一层 ROW 容纳 scrollbar，不改变 navBar/actionBar 固定语义。</li>
 * </ul>
 *
 * <h3>关键守不变量</h3>
 * <ul>
 *   <li>I1：activeSectionSignal、saveFeedbackSignal 全部只读受控源，handler 只 signal.set</li>
 *   <li>I3：section 切换不重建树，靠 rt.show 按条件挂卸；Supplier 体内只跑一次建该 section 字段卡片</li>
 *   <li>I7/I8：未激活 section 子树不参与布局/绘制（rt.show 懒挂载）</li>
 *   <li>I11：导航点击 handler 只 activeSectionSignal.set，不直接改 SceneNode</li>
 * </ul>
 *
 * <h3>按钮回调</h3>
 * <ul>
 *   <li>保存 → {@code mgr.save(draft)} + {@code adapter.afterSaveSync()} + 写 saveFeedbackSignal</li>
 *   <li>取消 → {@code adapter.resetToCurrent()}</li>
 *   <li>恢复默认 → 逐字段 {@code adapter.resetFieldToDefault(path)}</li>
 * </ul>
 *
 * <p>构造器接受可为 null 的 {@link PlatformInputSource}（headless 测试传 null）。
 * 逻辑与渲染解耦：构造器只建 signal 树和 mount，构造末尾 flush 让所有 Computed 物化。</p>
 */
public class ConfigScreen extends AbstractSceneHostWidget {

    /** 导航形态切换阈值：≤5 section 用横向 Tab，>5 用左侧侧栏 */
    private static final int NAV_SIDEBAR_THRESHOLD = 5;
    /** 侧栏导航固定宽度（像素） */
    private static final int NAV_PANE_WIDTH = 160;
    /** bodyRow 横向间距 */
    private static final int BODY_ROW_GAP = 12;

    /** 配置管理器，保存事务入口 */
    private final ConfigManager manager;
    /** 草稿 signal 适配器 */
    private final DraftSignalAdapter adapter;
    /** 字段渲染器注册表 */
    private final FieldRendererRegistry registry;
    /** 关联的 schema */
    private final ConfigSchema schema;

    /** 场景树根节点 */
    private SceneNode root;
    /** 滚动视口节点 */
    private SceneNode viewport;
    /** 视口内容容器节点（N 个 rt.show 挂载点） */
    private SceneNode content;
    /** 纵向滚动受控源（per-section：每个 section 独立保持滚动位置，切换不丢失） */
    private Signal<Integer>[] sectionScrolls;
    /** 当前 active section 的滚动偏移只读显示源（派生 Computed，clamp 到当前 maxScroll，订阅 layoutDoneSignal 防滞后） */
    private ReadableSignal<Integer> activeScroll;
    /** 滚动偏移写入回调（写当前 active section 的 signal，不 clamp，显示时 clamp） */
    private Consumer<Integer> setScroll;
    /** 标题条节点 */
    private SceneNode titleBar;
    /** 状态摘要条节点 */
    private SceneNode statusSummary;
    /** 操作条节点 */
    private SceneNode actionBar;
    /** 导航根节点（≤5 时为 SceneSegmented 根，>5 时为 navPane；0/1 section 时为 null） */
    private SceneNode navRoot;
    /** 侧栏形态时的 bodyRow 节点（>5 section 时非 null） */
    private SceneNode bodyRow;
    /** 滚动容器（ROW：viewport + scrollbar 列），承载 viewport 并在其右侧叠加滚动条 */
    private SceneNode scrollContainer;
    /** 滚动条列节点（scrollContainer 内 viewport 右侧的独立列） */
    private SceneNode scrollbarColumn;

    /** 当前活动 section 下标（受控源），导航控件唯一驱动（守 I1/I8） */
    private Signal<Integer> activeSectionSignal;

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
        super(input);
        this.manager = manager;
        this.adapter = adapter;
        this.registry = registry;
        this.schema = adapter.draft().schema();

        // 在 uiOwner 作用域内构造，所有 Computed/Effect 归属 uiOwner，dispose 时统一回收
        uiOwner.run(() -> {
            // 用 FormPageShell.build 构建统一口径骨架（root/viewport/scrollContainer），
            // attachScroll=false：shell 不建 scrollSignal/scrollbar（ConfigScreen 自建 per-section）。
            // buildTitleBar=false：跳过 shell 标题条构造（shell 的 text 不设字号，无法满足
            // FONT_TITLE=22/FONT_SUBTITLE=12 需求），ConfigScreen 自建 createTitleBar 直接挂 root。
            // 主题走 ConfigTheme.asFormTheme()（rootBg/viewportBg/titleColor 已对齐）。
            // 参数取 ConfigTheme 压缩档（titleBarHeight=32/rootPadding=12/rootGap=8）与 viewport 原值（14/14/10）。
            FormPageShell.Parts parts = FormPageShell.build(runtime,
                    schema.title(), "modId: " + schema.modId(),
                    ConfigTheme.TITLE_BAR_HEIGHT, ConfigTheme.ROOT_PADDING, ConfigTheme.ROOT_GAP,
                    14, 14, 10,
                    false, false, ConfigTheme.asFormTheme());
            this.root = parts.root();
            this.viewport = parts.viewport();
            this.scrollContainer = parts.scrollContainer();

            // shell.build 在 buildTitleBar=false 时只挂了 scrollContainer 到 root；ConfigScreen 需特化：
            // 1) titleBar 字号（FONT_TITLE=22/FONT_SUBTITLE=12）shell 不支持 → 自建 createTitleBar 挂 root 首位
            // 2) scrollContainer 挂载位置需按 section 数量决定（≤5 挂 root，>5 挂 bodyRow）→ 摘下重挂
            root.removeChild(scrollContainer);

            this.titleBar = createTitleBar();
            root.appendChild(titleBar);
            this.statusSummary = createStatusSummary();
            root.appendChild(statusSummary);

            List<SectionSpec> sections = schema.sections();
            this.activeSectionSignal = Signal.create(Integer.valueOf(0));

            this.content = createContent();
            viewport.appendChild(content);
            renderFields(sections);

            // 滚动容器（ROW：viewport + scrollbar 列），承载 viewport 并在其右侧叠加滚动条。
            // 项2/3：navBar 固定不滚动、actionBar 在滚动容器外底部——现状已满足，scrollContainer
            // 仅在原 viewport 位置外包一层 ROW 容纳 scrollbar，不改变 navBar/actionBar 的固定语义。
            // viewport 已由 shell.build 挂入 scrollContainer，此处不再重复 appendChild。
            if (sections.size() > 1) {
                if (sections.size() <= NAV_SIDEBAR_THRESHOLD) {
                    // ≤5 section：横向 SceneSegmented 导航头，mount 到 root（已 append），放 statusSummary 与 scrollContainer 之间
                    this.navRoot = createTabNav(sections);
                    // navRoot 默认高已由 SceneSegmented 内置（标签行高 + 2*段内边距），
                    // 此处不再手动设 preferredHeight，依赖组件默认高（ConfigScreenTest 断言 navRoot.getPreferredHeight() > 0 作为回归保护）。
                    root.appendChild(scrollContainer);
                } else {
                    // >5 section：左侧 navPane + scrollContainer 双栏 bodyRow
                    this.bodyRow = SceneNode.row();
                    bodyRow.setFillParentHeight(true);
                    bodyRow.setGap(BODY_ROW_GAP);
                    this.navRoot = createSidebarNav(bodyRow, sections);
                    navRoot.setPreferredWidth(NAV_PANE_WIDTH);
                    scrollContainer.setFlexGrow(1);
                    bodyRow.appendChild(scrollContainer);
                    root.appendChild(bodyRow);
                }
            } else {
                // 0 或 1 section：无需导航，直接挂 scrollContainer
                root.appendChild(scrollContainer);
            }

            // S4：save 反馈独立行，rt.show 懒挂载（saveFeedbackSignal 非 NONE 时显示，NONE 时隐藏不占高，守 I7）。
            // 挂在 scrollContainer 之后、actionBar 之前（root COLUMN 内）。
            rt().show(root,
                    Computed.create(() -> {
                        SaveFeedback fb = adapter.saveFeedbackSignal().get();
                        return Boolean.valueOf(fb != null && !fb.isNone());
                    }),
                    this::createSaveFeedbackBar);

            this.actionBar = createActionBar();
            root.appendChild(actionBar);

            // ===== BUG2 修复：per-section scroll state（section 切换不丢失滚动位置）=====
            // 每个 section 独立持有一个 Signal<Integer>，切换 section 时显示源切到对应 signal，
            // 切回时恢复原滚动位置。显示源为派生 Computed（clamp 到当前 maxScroll，订阅
            // layoutDoneSignal 防滞后），写入回调写当前 active section 的 signal（不 clamp，显示时 clamp）。
            @SuppressWarnings("unchecked")
            Signal<Integer>[] scrolls = new Signal[sections.size()];
            for (int i = 0; i < sections.size(); i++) {
                scrolls[i] = Signal.create(Integer.valueOf(0));
            }
            this.sectionScrolls = scrolls;

            // 派生显示源：当前 active section 的 scroll，clamp 到当前 maxScroll。
            // 订阅 layoutDoneSignal() 防止 section 切换后 maxScroll 滞后一帧（rt.show 懒挂卸
            // 导致 content 高度变化，layoutDoneSignal bump 后同帧 flush 内重算）。
            this.activeScroll = Computed.create(() -> {
                int idx = activeSectionSignal.get().intValue();
                if (idx < 0 || idx >= scrolls.length) {
                    return Integer.valueOf(0);
                }
                int raw = scrolls[idx].get().intValue();
                runtime.layoutDoneSignal().get(); // 订阅 layout 完成
                Object cached = viewport.getCachedLayout();
                if (!(cached instanceof LayoutBox)) {
                    return Integer.valueOf(Math.max(0, raw)); // flush 前 layout 未跑时兜底
                }
                int maxScroll = SceneGeometry.maxScrollY(viewport);
                return Integer.valueOf(Math.max(0, Math.min(maxScroll, raw)));
            });
            // 写入回调：写当前 active section 的 signal（不 clamp，显示时 clamp）
            this.setScroll = v -> {
                int idx = activeSectionSignal.get().intValue();
                if (idx >= 0 && idx < scrolls.length) {
                    scrolls[idx].set(v);
                }
            };

            SceneScrolls.attach(runtime, viewport, activeScroll, setScroll);
            // 项4：滚动条叠加在 viewport 右侧（scrollContainer ROW 内 viewport 旁的独立列），
            // 反映滚动位置/可滚动范围。几何由 bind 派生（订阅 activeScroll + rt.layoutDoneSignal），
            // 守 I7/I11/I4。P0：scrollbar 内部直接订阅 rt.layoutDoneSignal()——
            // host 在第一次 layout 后桥接 set epoch，scrollbar 同帧 flush 内重跑 effect 读最新 LayoutBox，
            // 零滞后覆盖 section 切换 + 窗口 resize 两种 content 高度变化场景。
            // BUG2：Props 拆 read/write——activeScroll 为只读显示源（派生 Computed），
            // setScroll 为写入回调（写当前 active section 的 signal）。
            SceneScrollbar.Props sbProps = new SceneScrollbar.Props(
                    viewport, activeScroll, setScroll,
                    SceneScrollbar.DEFAULT_TRACK_COLOR, SceneScrollbar.DEFAULT_THUMB_COLOR,
                    SceneScrollbar.DEFAULT_BAR_WIDTH, SceneScrollbar.DEFAULT_MIN_THUMB_HEIGHT);
            SceneScrollbar.Result sb = SceneScrollbar.create(runtime, sbProps);
            this.scrollbarColumn = sb.column();
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
     * <p>挂在 scrollContainer 与 actionBar 之间（root COLUMN 内），由调用方在构造期
     * 通过 {@code rt.show} 挂到 root。</p>
     *
     * @return save 反馈条节点（condition 为 true 时显示）
     */
    private SceneNode createSaveFeedbackBar() {
        SceneNode row = SceneNode.row();
        row.setGap(8);
        row.setHitTestable(false);
        // 显示态同样须设 preferredHeight：该行作为 root COLUMN 内固定子，未设则
        // grow 求解器命中容器分支 UNCONSTRAINED 早退，viewport 收不到固定高约束。
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
        return row;
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

    /**
     * 创建横向 SceneSegmented 导航头（≤5 section 形态）。
     *
     * @param sections section 列表
     * @return 导航头根节点（已 mount）
     */
    private SceneNode createTabNav(List<SectionSpec> sections) {
        List<String> titles = new ArrayList<String>();
        for (SectionSpec s : sections) {
            titles.add(s.title());
        }
        SceneSegmented.Props props = new SceneSegmented.Props(
                activeSectionSignal,
                titles,
                Signal.create(Boolean.TRUE),
                idx -> activeSectionSignal.set(Integer.valueOf(idx)));
        MountHandle handle = runtime.mount(root, SceneSegmented.create(runtime, props));
        return handle.getRoot();
    }

    /**
     * 创建纵向 SceneNavList 侧栏导航（>5 section 形态），直接 mount 到 bodyRow。
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
        return handle.getRoot();
    }

    /**
     * 创建视口内容容器。
     *
     * @return 内容节点
     */
    private SceneNode createContent() {
        SceneNode node = SceneNode.column();
        node.setGap(14);
        return node;
    }

    /**
     * 渲染所有 section：对每个 section i 调一次 {@code rt.show}，condition 为
     * {@code activeSection==i}，Supplier 体内只跑一次建该 section 字段卡片（守 I3/I7）。
     *
     * <p>铁律（照 SceneTab R10 范式）：绝不在 Supplier 体内 {@code activeSection.get()} 做 if 建树，
     * 绝不命令式 clearChildren 重挂。N 个独立 rt.show 各自管理挂卸。</p>
     *
     * @param sections section 列表
     */
    private void renderFields(List<SectionSpec> sections) {
        for (int i = 0; i < sections.size(); i++) {
            final int idx = i;
            final SectionSpec section = sections.get(i);
            rt().show(content,
                    Computed.create(() -> Boolean.valueOf(
                            Integer.valueOf(idx).equals(activeSectionSignal.get()))),
                    () -> buildSectionPanel(section));
        }
    }

    /**
     * 构建单个 section 面板：sectionTitle + 遍历 fields 调 registry.render 挂卡片。
     *
     * <p>由 {@code rt.show} 在 condition 首次为 true 时调用一次（I3），体内无 if 分支建树。</p>
     *
     * @param section section 元数据
     * @return section 面板节点
     */
    private SceneNode buildSectionPanel(SectionSpec section) {
        SceneNode sectionNode = SceneNode.column();
        sectionNode.setGap(ConfigTheme.FIELD_GAP);
        SceneNode sectionTitle = text(section.title(), ConfigTheme.TITLE_COLOR, ConfigTheme.FONT_SECTION);
        sectionNode.appendChild(sectionTitle);
        for (FieldSpec field : section.fields()) {
            FieldRenderer renderer = registry.resolve(field);
            if (renderer != null) {
                SceneNode card = renderer.render(runtime, field, adapter);
                sectionNode.appendChild(card);
            }
        }
        return sectionNode;
    }

    /**
     * 取场景运行时（供 renderFields 内 rt.show 调用，等价 {@code runtime}）。
     *
     * @return 场景运行时
     */
    private SceneRuntime rt() {
        return runtime;
    }

    /**
     * 创建固定操作条：恢复默认（左）/ spacer / 取消 + 保存（右，primary variant）。
     *
     * <p>S3：左右分区——恢复默认置最左（弱化低频破坏性操作），取消+保存置最右，
     * 保存在最右末位（主操作落在视线终点）。中间插 flexGrow=1 的 spacer 节点撑开剩余宽度
     * （scene MainAxisAlign 无 SPACE_BETWEEN，用 spacer 方案）。</p>
     *
     * @return 操作条节点
     */
    private SceneNode createActionBar() {
        SceneNode row = SceneNode.row();
        row.setPreferredHeight(ConfigTheme.ACTION_BAR_HEIGHT);
        row.setGap(10);
        // 左：恢复默认
        mountButton(row, "恢复默认", Signal.create(Boolean.TRUE), this::restoreDefaults, false);
        // 中：spacer 撑开剩余宽度（flexGrow=1 占满主轴剩余空间）
        SceneNode spacer = new SceneNode();
        spacer.setFlexGrow(1);
        spacer.setHitTestable(false);
        row.appendChild(spacer);
        // 右：取消 + 保存（保存最右末位，primary variant）
        mountButton(row, "取消更改", adapter.isDirtySignal(), this::cancelChanges, false);
        mountButton(row, "保存", adapter.canSaveSignal(), this::saveChanges, true);
        return row;
    }

    /**
     * 保存：mgr.save(draft) + adapter.afterSaveSync() + 写 saveFeedbackSignal（成功/失败反馈）。
     */
    private void saveChanges() {
        DraftBuffer draft = adapter.draft();
        lastSaveOutcome = manager.save(draft);
        if (lastSaveOutcome.isSuccess()) {
            adapter.afterSaveSync();
            adapter.setSaveFeedback(new SaveFeedback(SaveFeedback.Status.OK, "已保存"));
        } else {
            // 失败原因：IO_FAILED 用 errorMessage，INVALID 用校验摘要
            String reason = lastSaveOutcome.errorMessage();
            if (reason == null || reason.isEmpty()) {
                reason = lastSaveOutcome.status() == SaveOutcome.Status.INVALID
                        ? "校验未通过" : "保存失败";
            }
            SaveFeedback.Status fbStatus = lastSaveOutcome.status() == SaveOutcome.Status.IO_FAILED
                    ? SaveFeedback.Status.IO_FAILED : SaveFeedback.Status.INVALID;
            adapter.setSaveFeedback(new SaveFeedback(fbStatus, "保存失败：" + reason));
        }
    }

    /**
     * 取消：adapter.resetToCurrent()。
     */
    private void cancelChanges() {
        adapter.resetToCurrent();
    }

    /**
     * 恢复默认：逐字段 adapter.resetFieldToDefault(path)。
     */
    private void restoreDefaults() {
        for (FieldSpec field : schema.allFields()) {
            adapter.resetFieldToDefault(field.path());
        }
    }

    /**
     * 挂载按钮到父节点。
     *
     * @param parent  父节点
     * @param label   按钮文案
     * @param enabled enabled 派生
     * @param onClick 点击回调
     * @param primary 是否主按钮（primary variant，ACCENT 蓝底白字）
     */
    private void mountButton(SceneNode parent, String label, ReadableSignal<Boolean> enabled,
                             Runnable onClick, boolean primary) {
        SceneButton.Props props = new SceneButton.Props(
                Signal.create(label), enabled, onClick,
                primary ? SceneButtonVariant.PRIMARY : SceneButtonVariant.STANDARD);
        MountHandle handle = runtime.mount(parent, SceneButton.create(runtime, props));
        handle.getRoot().setPreferredWidth(ConfigTheme.BUTTON_WIDTH);
        handle.getRoot().setPreferredHeight(ConfigTheme.BUTTON_HEIGHT);
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
        node.setBorderWidth(1);
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

    /** @return 导航根节点（≤5 时为 SceneSegmented 根，>5 时为 navPane；0/1 section 时为 null） */
    SceneNode __getNavRoot() {
        return navRoot;
    }

    /** @return 侧栏形态 bodyRow 节点（>5 section 时非 null，否则 null） */
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

    /** @return 当前 active section 的滚动偏移只读显示源（派生 Computed，clamp 到当前 maxScroll） */
    ReadableSignal<Integer> __getActiveScroll() {
        return activeScroll;
    }

    /** @return 滚动偏移写入回调（写当前 active section 的 signal） */
    Consumer<Integer> __getSetScroll() {
        return setScroll;
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
        adapter.dispose();
        uiOwner.dispose();
        super.dispose();
    }
}
