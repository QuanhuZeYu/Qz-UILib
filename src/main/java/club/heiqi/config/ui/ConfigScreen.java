package club.heiqi.config.ui;

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
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 配置页 UI 骨架，extends {@link AbstractSceneHostWidget}。
 *
 * <p>结构：</p>
 * <pre>
 * root (COLUMN, fillParentHeight)
 *   ├ titleBar (固定)：标题 + modId
 *   ├ statusSummary (固定)：dirty / error 徽标
 *   ├ viewport (scrollable, fillParentHeight)
 *   │   └ content (COLUMN, 遍历 section → 遍历 field → registry.render)
 *   └ actionBar (固定)：恢复默认 / 取消(enabled=isDirty) / 保存(enabled=canSave)
 * </pre>
 *
 * <h3>按钮回调</h3>
 * <ul>
 *   <li>保存 → {@code mgr.save(draft)} + {@code adapter.afterSaveSync()}</li>
 *   <li>取消 → {@code adapter.resetToCurrent()}</li>
 *   <li>恢复默认 → 逐字段 {@code adapter.resetFieldToDefault(path)}</li>
 * </ul>
 *
 * <p>构造器接受可为 null 的 {@link PlatformInputSource}（headless 测试传 null）。
 * 逻辑与渲染解耦：构造器只建 signal 树和 mount，构造末尾 flush 让所有 Computed 物化。</p>
 */
public class ConfigScreen extends AbstractSceneHostWidget {

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
    /** 视口内容容器节点 */
    private SceneNode content;
    /** 纵向滚动受控源 */
    private Signal<Integer> scrollSignal;
    /** 标题条节点 */
    private SceneNode titleBar;
    /** 状态摘要条节点 */
    private SceneNode statusSummary;
    /** 操作条节点 */
    private SceneNode actionBar;

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
            this.viewport = createViewport();
            this.content = createContent();
            viewport.appendChild(content);
            root.appendChild(viewport);
            renderFields();
            this.actionBar = createActionBar();
            root.appendChild(actionBar);

            this.scrollSignal = SceneScrolls.attach(runtime, viewport);
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
     * 创建固定状态摘要条（dirty / error 徽标）。
     *
     * @return 状态摘要节点
     */
    private SceneNode createStatusSummary() {
        SceneNode row = new SceneNode();
        row.setFlexDirection(FlexDirection.ROW);
        row.setPreferredHeight(ConfigTheme.STATUS_HEIGHT);
        row.setGap(10);
        row.appendChild(badge(
                Computed.create(() -> Boolean.TRUE.equals(adapter.isDirtySignal().get())
                        ? "有未保存更改" : "无未保存更改"),
                Computed.create(() -> Boolean.TRUE.equals(adapter.isDirtySignal().get())
                        ? ConfigTheme.DIRTY_COLOR : ConfigTheme.OK_COLOR)));
        row.appendChild(badge(
                Computed.create(() -> Boolean.TRUE.equals(adapter.hasErrorSignal().get())
                        ? "存在校验错误" : "校验通过"),
                Computed.create(() -> Boolean.TRUE.equals(adapter.hasErrorSignal().get())
                        ? ConfigTheme.ERROR_COLOR : ConfigTheme.OK_COLOR)));
        return row;
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
        node.setScrollable(true);
        node.setClipChildren(true);
        node.setPadding(14);
        node.setGap(14);
        node.setBackgroundColor(ConfigTheme.VIEWPORT_BG);
        node.setCornerRadius(10);
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
     * 渲染所有 section 与字段：遍历 schema.sections()，每个 section 建容器，
     * 遍历 section.fields() 调 registry.render 挂载字段卡片。
     */
    private void renderFields() {
        for (SectionSpec section : schema.sections()) {
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
            content.appendChild(sectionNode);
        }
    }

    /**
     * 创建固定操作条：恢复默认 / 取消 / 保存。
     *
     * @return 操作条节点
     */
    private SceneNode createActionBar() {
        SceneNode row = new SceneNode();
        row.setFlexDirection(FlexDirection.ROW);
        row.setPreferredHeight(ConfigTheme.ACTION_BAR_HEIGHT);
        row.setGap(10);
        mountButton(row, "恢复默认", Signal.create(Boolean.TRUE), this::restoreDefaults);
        mountButton(row, "取消更改", adapter.isDirtySignal(), this::cancelChanges);
        mountButton(row, "保存", adapter.canSaveSignal(), this::saveChanges);
        return row;
    }

    /**
     * 保存：mgr.save(draft) + adapter.afterSaveSync()。
     */
    private void saveChanges() {
        DraftBuffer draft = adapter.draft();
        lastSaveOutcome = manager.save(draft);
        if (lastSaveOutcome.isSuccess()) {
            adapter.afterSaveSync();
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
     */
    private void mountButton(SceneNode parent, String label, ReadableSignal<Boolean> enabled, Runnable onClick) {
        SceneButton.Props props = new SceneButton.Props(Signal.create(label), enabled, onClick);
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
