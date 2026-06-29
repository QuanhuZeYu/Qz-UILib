package club.heiqi.config.ui;

import java.util.ArrayList;
import java.util.List;

import club.heiqi.config.runtime.ConfigManager;
import club.heiqi.config.runtime.DraftBuffer;
import club.heiqi.config.runtime.SaveOutcome;
import club.heiqi.config.schema.ConfigSchema;
import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.schema.SectionSpec;
import club.heiqi.config.ui.field.FieldRenderer;
import club.heiqi.config.ui.field.FieldRendererRegistry;
import club.heiqi.config.ui.theme.ConfigTheme;
import club.heiqi.uilib.internal.devtools.pages.AbstractSceneHostWidget;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.component.MountHandle;
import club.heiqi.uilib.ui.scene.component.SceneRuntime;
import club.heiqi.uilib.ui.scene.component.SceneScrolls;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneButtonVariant;
import club.heiqi.uilib.ui.scene.control.SceneNavList;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.control.SceneSegmented;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 配置页 UI 骨架，extends {@link AbstractSceneHostWidget}。
 *
 * <p>结构（按 section 数量自动选导航形态，守 I3/I7：section 切换用 {@code rt.show} 懒挂卸）：</p>
 * <pre>
 * root (COLUMN, fillParentHeight, padding=20, gap=12, bg=ROOT_BG)
 *   ├ titleBar      (固定高 44)  标题 + modId + 【P2占位】搜索框槽
 *   ├ statusSummary (固定高 34)  dirty/error 计数徽标 + save 反馈条
 *   ├ [≤5 section] navBar (SceneSegmented 横向页签)
 *   │   [>5 section] bodyRow (ROW, gap=12)
 *   │       ├ navPane  (SceneNavList 纵向受控单选，固定宽 160)
 *   │       └ scrollContainer (ROW, gap=0, fillParentHeight)
 *   │           ├ viewport (scrollable, flexGrow=1, fillParentHeight, clip, bg=VIEWPORT_BG, radius=10)
 *   │           │   └ content (COLUMN, gap=14)
 *   │           │       └ 对每个 section i：rt.show(content, activeSection==i, () -> sectionPanel(i))
 *   │           └ scrollbarColumn (SceneScrollbar, 固定宽 4, fillParentHeight)  ← 项4 滚动条
 *   └ actionBar     (固定高 46)  恢复默认 / 取消(enabled=isDirty) / 保存(enabled=canSave, primary variant)
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
    /** 纵向滚动受控源 */
    private Signal<Integer> scrollSignal;
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
            this.root = createRoot();
            this.titleBar = createTitleBar();
            root.appendChild(titleBar);
            this.statusSummary = createStatusSummary();
            root.appendChild(statusSummary);

            List<SectionSpec> sections = schema.sections();
            this.activeSectionSignal = Signal.create(Integer.valueOf(0));

            this.viewport = createViewport();
            this.content = createContent();
            viewport.appendChild(content);
            renderFields(sections);

            // 滚动容器（ROW：viewport + scrollbar 列），承载 viewport 并在其右侧叠加滚动条。
            // 项2/3：navBar 固定不滚动、actionBar 在滚动容器外底部——现状已满足，scrollContainer
            // 仅在原 viewport 位置外包一层 ROW 容纳 scrollbar，不改变 navBar/actionBar 的固定语义。
            this.scrollContainer = createScrollContainer();
            scrollContainer.appendChild(viewport);

            if (sections.size() > 1) {
                if (sections.size() <= NAV_SIDEBAR_THRESHOLD) {
                    // ≤5 section：横向 SceneSegmented 导航头，mount 到 root（已 append），放 statusSummary 与 scrollContainer 之间
                    this.navRoot = createTabNav(sections);
                    root.appendChild(scrollContainer);
                } else {
                    // >5 section：左侧 navPane + scrollContainer 双栏 bodyRow
                    this.bodyRow = new SceneNode();
                    bodyRow.setFlexDirection(FlexDirection.ROW);
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

            this.actionBar = createActionBar();
            root.appendChild(actionBar);

            this.scrollSignal = SceneScrolls.attach(runtime, viewport);
            // 项4：滚动条叠加在 viewport 右侧（scrollContainer ROW 内 viewport 旁的独立列），
            // 反映滚动位置/可滚动范围。几何由 bind 派生（订阅 scrollSignal + activeSectionSignal），
            // 守 I7/I11/I4。activeSectionSignal 在 section 切换时 bump，驱动 scrollbar 重算 thumb 几何。
            SceneScrollbar.Props sbProps = new SceneScrollbar.Props(
                    viewport, scrollSignal, activeSectionSignal,
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
     * 创建根容器。
     *
     * @return 根节点
     */
    private SceneNode createRoot() {
        SceneNode node = new SceneNode();
        node.setFillParentHeight(true);
        node.setFlexDirection(FlexDirection.COLUMN);
        node.setPadding(20);
        node.setGap(12);
        node.setBackgroundColor(ConfigTheme.ROOT_BG);
        return node;
    }

    /**
     * 创建固定标题条。
     *
     * @return 标题条节点
     */
    private SceneNode createTitleBar() {
        SceneNode bar = new SceneNode();
        bar.setFlexDirection(FlexDirection.COLUMN);
        bar.setPreferredHeight(ConfigTheme.TITLE_BAR_HEIGHT);
        bar.setGap(4);
        bar.setHitTestable(false);
        bar.appendChild(text("配置编辑器", ConfigTheme.TITLE_COLOR));
        bar.appendChild(text("modId: " + schema.modId(), ConfigTheme.MUTED_COLOR));
        return bar;
    }

    /**
     * 创建固定状态摘要条：dirty 计数徽标 + error 计数徽标 + save 反馈条。
     *
     * @return 状态摘要节点
     */
    private SceneNode createStatusSummary() {
        SceneNode row = new SceneNode();
        row.setFlexDirection(FlexDirection.ROW);
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

        // save 反馈条：文本 + 颜色由 saveFeedbackSignal 派生（LAYOUT 级文本、PAINT 级色）
        SceneNode feedback = text("", ConfigTheme.MUTED_COLOR);
        runtime.bind(Invalidation.LAYOUT,
                Computed.create(() -> {
                    SaveFeedback fb = adapter.saveFeedbackSignal().get();
                    return fb == null ? "" : fb.message();
                }),
                feedback::setText);
        runtime.bind(Invalidation.PAINT,
                Computed.create(() -> {
                    SaveFeedback fb = adapter.saveFeedbackSignal().get();
                    if (fb == null || fb.isNone()) {
                        return ConfigTheme.MUTED_COLOR;
                    }
                    return fb.isError() ? ConfigTheme.ERROR_COLOR : ConfigTheme.OK_COLOR;
                }),
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
                idx -> activeSectionSignal.set(Integer.valueOf(idx)));
        MountHandle handle = runtime.mount(parent, SceneNavList.create(runtime, props));
        return handle.getRoot();
    }

    /**
     * 创建滚动视口。
     *
     * @return 视口节点
     */
    private SceneNode createViewport() {
        SceneNode node = new SceneNode();
        node.setFlexDirection(FlexDirection.COLUMN);
        node.setFillParentHeight(true);
        node.setFlexGrow(1);
        node.setScrollable(true);
        node.setClipChildren(true);
        node.setPadding(14);
        node.setGap(14);
        node.setBackgroundColor(ConfigTheme.VIEWPORT_BG);
        node.setCornerRadius(10);
        return node;
    }

    /**
     * 创建滚动容器（ROW：viewport + scrollbar 列）。
     *
     * <p>外包一层 ROW 容纳 viewport 与右侧 scrollbar 独立列，scrollContainer 挂在原 viewport
     * 的位置（root COLUMN 或 bodyRow ROW）。fillParentHeight 使其填满父容器主轴剩余高度，
     * viewport 在其内 flexGrow=1 占剩余宽，scrollbar 固定宽 4px 列。</p>
     *
     * @return 滚动容器节点
     */
    private SceneNode createScrollContainer() {
        SceneNode node = new SceneNode();
        node.setFlexDirection(FlexDirection.ROW);
        node.setFillParentHeight(true);
        node.setGap(0);
        return node;
    }

    /**
     * 创建视口内容容器。
     *
     * @return 内容节点
     */
    private SceneNode createContent() {
        SceneNode node = new SceneNode();
        node.setFlexDirection(FlexDirection.COLUMN);
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
        SceneNode sectionNode = new SceneNode();
        sectionNode.setFlexDirection(FlexDirection.COLUMN);
        sectionNode.setGap(ConfigTheme.FIELD_GAP);
        SceneNode sectionTitle = text(section.title(), ConfigTheme.TITLE_COLOR);
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
     * 创建固定操作条：恢复默认 / 取消 / 保存（primary variant）。
     *
     * @return 操作条节点
     */
    private SceneNode createActionBar() {
        SceneNode row = new SceneNode();
        row.setFlexDirection(FlexDirection.ROW);
        row.setPreferredHeight(ConfigTheme.ACTION_BAR_HEIGHT);
        row.setGap(10);
        mountButton(row, "恢复默认", Signal.create(Boolean.TRUE), this::restoreDefaults, false);
        mountButton(row, "取消更改", adapter.isDirtySignal(), this::cancelChanges, false);
        // 保存为主按钮：primary variant（ACCENT 蓝底白字）
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
     * @param label 文案源
     * @param color 颜色源
     * @return 徽标节点
     */
    private SceneNode badge(ReadableSignal<String> label, ReadableSignal<Integer> color) {
        SceneNode node = new SceneNode();
        node.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        node.setPadding(8);
        node.setCornerRadius(999);
        node.setHitTestable(false);
        SceneNode textNode = text("", 0xFFFFFFFF);
        node.appendChild(textNode);
        runtime.bind(Invalidation.LAYOUT, label, textNode::setText);
        runtime.bind(Invalidation.PAINT, color, node::setBorderColor);
        runtime.bind(Invalidation.PAINT, color, textNode::setTextColor);
        node.setBorderWidth(1);
        node.setBackgroundColor(ConfigTheme.READOUT_BG);
        return node;
    }

    /**
     * 创建文字节点。
     *
     * @param value 文本
     * @param color 颜色
     * @return 文字节点
     */
    private SceneNode text(String value, int color) {
        SceneNode node = new SceneNode();
        node.setText(value);
        node.setTextColor(color);
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

    /** @return 纵向滚动受控源 */
    Signal<Integer> __getScrollSignal() {
        return scrollSignal;
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
