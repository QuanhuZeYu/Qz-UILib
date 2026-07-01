package club.heiqi.uilib.internal.devtools.pages;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.scene.control.SceneButton;
import club.heiqi.uilib.ui.scene.control.SceneInputType;
import club.heiqi.uilib.ui.scene.control.SceneTextInput;
import club.heiqi.uilib.ui.scene.control.SceneToggle;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.layout.SceneLayoutEngine;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 新栈 ui.scene 硬编码隔离配置表单 demo 宿主 Widget。
 *
 * <p>本页不连接真实 Forge Configuration，不复用旧 PropertyBinding，也不扩展旧 DOM 业务逻辑。
 * 表单状态以 current/draft 双副本 signal 表达，错误、dirty 与按钮可用性全部由 computed 派生，
 * UI 外观仅通过 {@link SceneRuntime#bind(ReadableSignal, java.util.function.Consumer)} 消费。</p>
 */
public class SceneFormHostWidget extends AbstractSceneHostWidget {

    private static final String DEFAULT_NAME = "Steve";
    private static final String DEFAULT_DISTANCE = "8";
    private static final boolean DEFAULT_FANCY = false;

    private static final int ROOT_BG = 0xFF0B1424;
    private static final int CARD_BG = 0xFF0D1728;
    private static final int CARD_BORDER = 0xFF2F4D87;
    private static final int CARD_BORDER_DIRTY = 0xFF3B5BA5;
    private static final int CARD_BORDER_ERROR = 0xFFF87171;
    private static final int TITLE_COLOR = 0xFFC9D8F8;
    private static final int TEXT_COLOR = 0xFFEAF1FF;
    private static final int MUTED_COLOR = 0xFF8AA0C8;
    private static final int READOUT_BG = 0xFF1E293B;
    private static final int ERROR_COLOR = 0xFFF87171;
    private static final int OK_COLOR = 0xFF34D399;
    private static final int DIRTY_COLOR = 0xFF60A5FA;
    private static final int TITLE_BAR_HEIGHT = 44;
    private static final int STATUS_HEIGHT = 34;
    private static final int ACTION_BAR_HEIGHT = 46;

    private final SceneNode root;
    private final SceneNode viewport;
    private final SceneNode content;
    private final Signal<Integer> scrollSignal;
    private final Signal<String> nameCurrent;
    private final Signal<String> distanceCurrent;
    private final Signal<Boolean> fancyCurrent;
    private final Signal<String> nameDraft;
    private final Signal<String> distanceDraft;
    private final Signal<Boolean> fancyDraft;
    private final Computed<String> nameError;
    private final Computed<String> distanceError;
    private final Computed<Boolean> nameDirty;
    private final Computed<Boolean> distanceDirty;
    private final Computed<Boolean> fancyDirty;
    private final Computed<Boolean> isDirty;
    private final Computed<Boolean> hasError;
    private final Computed<Boolean> canSave;
    private final Computed<Boolean> canCancel;

    /**
     * 创建配置表单 demo 宿主 Widget，注入平台输入源。
     *
     * @param inputSource 平台输入源，可为 null（退化模式，无真机滚轮）
     */
    public SceneFormHostWidget(PlatformInputSource inputSource) {
        super(inputSource);

        this.nameCurrent = Signal.create(DEFAULT_NAME);
        this.distanceCurrent = Signal.create(DEFAULT_DISTANCE);
        this.fancyCurrent = Signal.create(Boolean.valueOf(DEFAULT_FANCY));
        this.nameDraft = Signal.create(DEFAULT_NAME);
        this.distanceDraft = Signal.create(DEFAULT_DISTANCE);
        this.fancyDraft = Signal.create(Boolean.valueOf(DEFAULT_FANCY));
        this.nameError = Computed.create(() -> validateName(nameDraft.get()));
        this.distanceError = Computed.create(() -> validateDistance(distanceDraft.get(), fancyDraft.get()));
        this.nameDirty = Computed.create(() -> !safe(nameDraft.get()).equals(safe(nameCurrent.get())));
        this.distanceDirty = Computed.create(() -> !safe(distanceDraft.get()).equals(safe(distanceCurrent.get())));
        this.fancyDirty = Computed.create(() -> !fancyDraft.get().equals(fancyCurrent.get()));
        this.isDirty = Computed.create(() -> Boolean.TRUE.equals(nameDirty.get())
                || Boolean.TRUE.equals(distanceDirty.get()) || Boolean.TRUE.equals(fancyDirty.get()));
        this.hasError = Computed.create(() -> !safe(nameError.get()).isEmpty() || !safe(distanceError.get()).isEmpty());
        this.canSave = Computed.create(() -> Boolean.TRUE.equals(isDirty.get()) && !Boolean.TRUE.equals(hasError.get()));
        this.canCancel = Computed.create(() -> Boolean.TRUE.equals(isDirty.get()));

        this.root = createRoot();
        root.appendChild(createTitleBar());
        root.appendChild(createStatusSummary());
        this.viewport = createViewport();
        this.content = createContent();
        viewport.appendChild(content);
        root.appendChild(viewport);
        root.appendChild(createActionBar());

        content.appendChild(createTextFieldCard("玩家名称", "1–16 字符，仅字母数字下划线", "请输入名称",
                16, nameDraft, nameError, nameDirty));
        content.appendChild(createTextFieldCard("渲染距离", "整数，范围 2–32 chunk", "2–32",
                4, distanceDraft, distanceError, distanceDirty));
        content.appendChild(createToggleFieldCard());

        this.scrollSignal = SceneScrolls.attach(runtime, viewport);

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
        node.setBackgroundColor(ROOT_BG);
        return node;
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
        titleBar.appendChild(text("Scene 配置表单 demo", TITLE_COLOR));
        titleBar.appendChild(text("草稿 / 当前双副本 · 脏标记 · 字段校验 · 保存恢复", MUTED_COLOR));
        return titleBar;
    }

    /**
     * 创建固定状态摘要条。
     *
     * @return 状态摘要节点
     */
    private SceneNode createStatusSummary() {
        SceneNode row = SceneNode.row();
        row.setPreferredHeight(STATUS_HEIGHT);
        row.setGap(10);
        row.appendChild(badge(Computed.create(() -> Boolean.TRUE.equals(isDirty.get()) ? "有未保存更改" : "无未保存更改"),
                Computed.create(() -> Boolean.TRUE.equals(isDirty.get()) ? DIRTY_COLOR : OK_COLOR)));
        row.appendChild(badge(Computed.create(() -> Boolean.TRUE.equals(hasError.get()) ? "存在校验错误" : "校验通过"),
                Computed.create(() -> Boolean.TRUE.equals(hasError.get()) ? ERROR_COLOR : OK_COLOR)));
        return row;
    }

    /**
     * 创建滚动视口。
     *
     * @return 视口节点
     */
    private SceneNode createViewport() {
        SceneNode node = SceneNode.column();
        node.setFillParentHeight(true);
        node.setScrollable(true);
        node.setClipChildren(true);
        node.setPadding(14);
        node.setGap(14);
        node.setBackgroundColor(0xFF081120);
        node.setCornerRadius(10);
        return node;
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
     * 创建固定按钮区。
     *
     * @return 按钮区节点
     */
    private SceneNode createActionBar() {
        SceneNode row = SceneNode.row();
        row.setPreferredHeight(ACTION_BAR_HEIGHT);
        row.setGap(10);
        mountButton(row, "恢复默认", Signal.create(Boolean.TRUE), this::restoreDefaults);
        mountButton(row, "取消更改", canCancel, this::cancelChanges);
        mountButton(row, "保存", canSave, this::saveChanges);
        return row;
    }

    /**
     * 创建文本字段卡片。
     *
     * @param label 字段标签
     * @param helper 帮助文本
     * @param placeholder 输入占位文本
     * @param maxLength 最大长度
     * @param draft 草稿 signal
     * @param error 错误派生
     * @param dirty 脏标记派生
     * @return 字段卡片节点
     */
    private SceneNode createTextFieldCard(String label, String helper, String placeholder, int maxLength,
            Signal<String> draft, ReadableSignal<String> error, ReadableSignal<Boolean> dirty) {
        SceneNode card = createFieldShell(label, helper, error, dirty);
        SceneTextInput.Props props = new SceneTextInput.Props(
                draft,
                Signal.create(Boolean.TRUE),
                Signal.create(Boolean.FALSE),
                placeholder,
                maxLength,
                SceneInputType.TEXT,
                draft::set);
        MountHandle handle = runtime.mount(card, SceneTextInput.create(runtime, props));
        handle.getRoot().setPreferredHeight(32);
        appendErrorText(card, error);
        return card;
    }

    /**
     * 创建 Toggle 字段卡片。
     *
     * @return 字段卡片节点
     */
    private SceneNode createToggleFieldCard() {
        SceneNode card = createFieldShell("花哨画质", "开启后渲染距离下限抬高到 8",
                Signal.create(""), fancyDirty);
        SceneToggle.Props props = new SceneToggle.Props(
                fancyDraft,
                Signal.create("启用花哨画质"),
                Signal.create(Boolean.TRUE),
                fancyDraft::set);
        runtime.mount(card, SceneToggle.create(runtime, props));
        appendErrorText(card, Signal.create(""));
        return card;
    }

    /**
     * 创建字段卡片通用外壳。
     *
     * @param label 字段标签
     * @param helper 帮助文本
     * @param error 错误派生
     * @param dirty 脏标记派生
     * @return 字段卡片节点
     */
    private SceneNode createFieldShell(String label, String helper, ReadableSignal<String> error,
            ReadableSignal<Boolean> dirty) {
        SceneNode card = SceneNode.column();
        card.setBackgroundColor(CARD_BG);
        card.setBorderWidth(1);
        card.setCornerRadius(10);
        card.setPadding(12);
        card.setGap(8);
        runtime.bind(Computed.create(() -> resolveCardBorder(error.get(), dirty.get())),
                card::setBorderColor);

        SceneNode header = SceneNode.row();
        header.setGap(8);
        SceneNode dot = text("●", CARD_BORDER);
        runtime.bind(Computed.create(() -> !safe(error.get()).isEmpty() ? ERROR_COLOR
                        : Boolean.TRUE.equals(dirty.get()) ? DIRTY_COLOR : MUTED_COLOR),
                dot::setTextColor);
        SceneNode title = text(label, TEXT_COLOR);
        header.appendChild(dot);
        header.appendChild(title);
        card.appendChild(header);
        card.appendChild(text(helper, MUTED_COLOR));
        return card;
    }

    /**
     * 追加字段错误文本，位于控件之后。
     *
     * @param card 字段卡片
     * @param error 错误派生
     */
    private void appendErrorText(SceneNode card, ReadableSignal<String> error) {
        SceneNode errorNode = text("", ERROR_COLOR);
        runtime.bind(error, errorNode::setText);
        runtime.bind(Computed.create(() -> safe(error.get()).isEmpty() ? MUTED_COLOR : ERROR_COLOR),
                errorNode::setTextColor);
        card.appendChild(errorNode);
    }

    /** 保存：current 写入 draft。 */
    private void saveChanges() {
        nameCurrent.set(nameDraft.get());
        distanceCurrent.set(distanceDraft.get());
        fancyCurrent.set(fancyDraft.get());
    }

    /** 取消：draft 回滚 current。 */
    private void cancelChanges() {
        nameDraft.set(nameCurrent.get());
        distanceDraft.set(distanceCurrent.get());
        fancyDraft.set(fancyCurrent.get());
    }

    /** 恢复默认：只写 draft，不直接写 current。 */
    private void restoreDefaults() {
        nameDraft.set(DEFAULT_NAME);
        distanceDraft.set(DEFAULT_DISTANCE);
        fancyDraft.set(Boolean.valueOf(DEFAULT_FANCY));
    }

    /**
     * 挂载按钮。
     *
     * @param parent 父节点
     * @param label 按钮文案
     * @param enabled enabled 派生
     * @param onClick 点击回调
     */
    private void mountButton(SceneNode parent, String label, ReadableSignal<Boolean> enabled, Runnable onClick) {
        SceneButton.Props props = new SceneButton.Props(Signal.create(label), enabled, onClick);
        MountHandle handle = runtime.mount(parent, SceneButton.create(runtime, props));
        handle.getRoot().setPreferredWidth(110);
        handle.getRoot().setPreferredHeight(36);
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
        runtime.bind(label, textNode::setText);
        runtime.bind(color, node::setBorderColor);
        runtime.bind(color, textNode::setTextColor);
        node.setBorderWidth(1);
        node.setBackgroundColor(READOUT_BG);
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

    /**
     * 校验玩家名称。
     *
     * @param value 名称草稿
     * @return 错误文案；空串表示通过
     */
    private static String validateName(String value) {
        String name = safe(value);
        if (name.isEmpty()) {
            return "名称不能为空";
        }
        if (!name.matches("[A-Za-z0-9_]{1,16}")) {
            return "仅限字母数字下划线，最长 16";
        }
        return "";
    }

    /**
     * 校验渲染距离。
     *
     * @param value 距离草稿
     * @param fancy 是否启用花哨画质
     * @return 错误文案；空串表示通过
     */
    private static String validateDistance(String value, Boolean fancy) {
        int distance;
        try {
            distance = Integer.parseInt(safe(value));
        } catch (NumberFormatException ex) {
            return "请输入整数";
        }
        if (distance < 2 || distance > 32) {
            return "取值范围 2–32";
        }
        if (Boolean.TRUE.equals(fancy) && distance < 8) {
            return "花哨画质下渲染距离至少 8";
        }
        return "";
    }

    /**
     * 解析卡片边框色。
     *
     * @param error 错误文案
     * @param dirty 是否脏
     * @return 边框色
     */
    private static int resolveCardBorder(String error, Boolean dirty) {
        if (!safe(error).isEmpty()) {
            return CARD_BORDER_ERROR;
        }
        if (Boolean.TRUE.equals(dirty)) {
            return CARD_BORDER_DIRTY;
        }
        return CARD_BORDER;
    }

    /**
     * null 安全文本。
     *
     * @param value 文本
     * @return 非 null 文本
     */
    private static String safe(String value) {
        return value == null ? "" : value;
    }

    @Override
    protected SceneNode getRoot() {
        return root;
    }

    /** @return 内部场景运行时 */
    SceneRuntime __getRuntime() {
        return runtime;
    }

    /** @return 内部布局引擎 */
    SceneLayoutEngine __getLayoutEngine() {
        return layoutEngine;
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

    /** @return 纵向滚动受控源 */
    Signal<Integer> __getScrollSignal() {
        return scrollSignal;
    }

    /** @return 名称草稿 signal */
    Signal<String> __getNameDraft() {
        return nameDraft;
    }

    /** @return 名称当前值 signal */
    Signal<String> __getNameCurrent() {
        return nameCurrent;
    }

    /** @return 渲染距离草稿 signal */
    Signal<String> __getDistanceDraft() {
        return distanceDraft;
    }

    /** @return 渲染距离当前值 signal */
    Signal<String> __getDistanceCurrent() {
        return distanceCurrent;
    }

    /** @return 花哨画质草稿 signal */
    Signal<Boolean> __getFancyDraft() {
        return fancyDraft;
    }

    /** @return 花哨画质当前值 signal */
    Signal<Boolean> __getFancyCurrent() {
        return fancyCurrent;
    }

    /** @return 名称错误派生 */
    Computed<String> __getNameError() {
        return nameError;
    }

    /** @return 渲染距离错误派生 */
    Computed<String> __getDistanceError() {
        return distanceError;
    }

    /** @return 是否可保存派生 */
    Computed<Boolean> __getCanSave() {
        return canSave;
    }

    /** 测试探针：触发保存。 */
    void __saveChanges() {
        saveChanges();
    }

    /** 测试探针：触发取消。 */
    void __cancelChanges() {
        cancelChanges();
    }

    /** 测试探针：触发恢复默认。 */
    void __restoreDefaults() {
        restoreDefaults();
    }
}
