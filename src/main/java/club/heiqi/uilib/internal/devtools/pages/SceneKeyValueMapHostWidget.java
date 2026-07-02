package club.heiqi.uilib.internal.devtools.pages;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.runtime.MountHandle;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;
import club.heiqi.uilib.ui.scene.control.SceneKeyValueMap;
import club.heiqi.uilib.ui.scene.control.SceneKeyValueMap.KeyValueRow;
import club.heiqi.uilib.ui.scene.control.SceneKeyValueMap.ValidationError;
import club.heiqi.uilib.ui.scene.control.SceneKeyValueMap.ValidationErrorType;
import club.heiqi.uilib.ui.scene.control.SceneKeyValueMap.ValueType;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 新栈 ui.scene KeyValueMap demo 宿主 Widget。
 */
public class SceneKeyValueMapHostWidget extends AbstractSceneHostWidget {

    private static final int TITLE_BAR_HEIGHT = 44;
    private static final int STATUS_HEIGHT = 34;
    private static final int MAP_HEIGHT = 255;
    private static final int SCROLL_GAP = 3;

    private final SceneNode root;
    private final SceneNode viewport;
    private final SceneNode scrollContainer;
    private final SceneNode scrollbarColumn;
    private final SceneNode content;
    private final Signal<Integer> scrollSignal;
    private final Signal<List<KeyValueRow>> basicRows;
    private final Signal<List<KeyValueRow>> validationRows;
    private final Signal<List<KeyValueRow>> boundedRows;
    private final Signal<Integer> changeCount;
    private final Signal<String> latestValidationError;

    /**
     * 创建 KeyValueMap demo 宿主 Widget，注入平台输入源。
     *
     * @param inputSource 平台输入源，可为 null（退化模式，无真机滚轮）
     */
    public SceneKeyValueMapHostWidget(PlatformInputSource inputSource) {
        super(inputSource);
        this.basicRows = Signal.create(basicConfigRows());
        this.validationRows = Signal.create(validationRows());
        this.boundedRows = Signal.create(boundedConfigRows());
        this.changeCount = Signal.create(Integer.valueOf(0));
        this.latestValidationError = Signal.create("未触发校验回调");

        this.root = createRoot();
        root.appendChild(createTitleBar());
        this.viewport = createViewport();
        this.content = createContent();
        viewport.appendChild(content);
        this.scrollContainer = createScrollContainer();
        scrollContainer.appendChild(viewport);
        root.appendChild(scrollContainer);
        root.appendChild(createStatusBar());

        content.appendChild(createMapCard("基础用法", "含 STRING / NUMBER / BOOLEAN 三种配置值类型。",
                basicRows, "配置 key", "配置值", 0, 12, false));
        content.appendChild(createMapCard("校验反馈", "初始包含空 key 与重复 key，用于展示标红和校验回调。",
                validationRows, "不得为空或重复", "任意值", 0, 12, true));
        content.appendChild(createMapCard("边界测试", "最少保留 2 行，最多 8 行，用于验证增删按钮边界。",
                boundedRows, "边界 key", "边界值", 2, 8, false));

        this.scrollSignal = SceneScrolls.attach(runtime, viewport);

        // 滚动条叠加在 viewport 右侧（scrollContainer ROW 内独立列），照 ConfigScreen 范式。
        SceneScrollbar.Props sbProps = new SceneScrollbar.Props(
                viewport, scrollSignal, scrollSignal::set,
                SceneScrollbar.DEFAULT_TRACK_COLOR, SceneScrollbar.DEFAULT_THUMB_COLOR,
                SceneScrollbar.DEFAULT_BAR_WIDTH, SceneScrollbar.DEFAULT_MIN_THUMB_HEIGHT);
        SceneScrollbar.Result sb = SceneScrollbar.create(runtime, sbProps);
        this.scrollbarColumn = sb.column();
        scrollContainer.appendChild(scrollbarColumn);

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
        node.setBackgroundColor(SceneDemoTokens.ROOT_BG);
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
        titleBar.appendChild(text("SceneKeyValueMap Demo", SceneDemoTokens.TITLE_COLOR));
        titleBar.appendChild(text("动态键值配置 · 类型切换 · key 校验 · 行内文本输入", SceneDemoTokens.MUTED_COLOR));
        return titleBar;
    }

    /**
     * 创建滚动视口。
     *
     * @return 视口节点
     */
    private SceneNode createViewport() {
        SceneNode node = SceneNode.column();
        node.setFillParentHeight(true);
        node.setFlexGrow(1);
        node.setScrollable(true);
        node.setClipChildren(true);
        node.setPadding(14);
        node.setGap(14);
        node.setBackgroundColor(SceneDemoTokens.VIEWPORT_BG);
        node.setCornerRadius(10);
        return node;
    }

    /**
     * 创建滚动容器（ROW：viewport + scrollbar 列），照 ConfigScreen 范式。
     *
     * @return 滚动容器节点
     */
    private SceneNode createScrollContainer() {
        SceneNode node = SceneNode.row();
        node.setFillParentHeight(true);
        node.setGap(SCROLL_GAP);
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
     * 创建底部状态条。
     *
     * @return 状态条节点
     */
    private SceneNode createStatusBar() {
        SceneNode row = SceneNode.row();
        row.setPreferredHeight(STATUS_HEIGHT);
        row.setGap(10);
        row.appendChild(badge(Computed.create(() -> "当前行数：" + totalCount()), SceneDemoTokens.OK_COLOR));
        row.appendChild(badge(Computed.create(() -> "onRowsChanged：" + changeCount.get()), SceneDemoTokens.DIRTY_COLOR));
        row.appendChild(badge(Computed.create(() -> "最近校验：" + latestValidationError.get()), SceneDemoTokens.ERROR_COLOR));
        return row;
    }

    /**
     * 创建 KeyValueMap 卡片。
     *
     * @param title 卡片标题
     * @param helper 帮助说明
     * @param rows 行列表 signal
     * @param keyPlaceholder key 占位文本
     * @param valuePlaceholder value 占位文本
     * @param minRows 最小行数
     * @param maxRows 最大行数
     * @param trackValidation 是否跟踪校验回调
     * @return 卡片节点
     */
    private SceneNode createMapCard(String title, String helper, Signal<List<KeyValueRow>> rows,
            String keyPlaceholder, String valuePlaceholder, int minRows, int maxRows, boolean trackValidation) {
        SceneNode card = createCardShell(title, helper);
        SceneKeyValueMap.Props.Builder builder = SceneKeyValueMap.Props.builder(rows)
                .label(title + "配置")
                .keyPlaceholder(keyPlaceholder)
                .valuePlaceholder(valuePlaceholder)
                .minRows(minRows)
                .maxRows(maxRows)
                .onRowsChanged(next -> incrementChangeCount());
        if (trackValidation) {
            builder.onValidationError(this::updateValidationError);
        }
        MountHandle handle = runtime.mount(card, SceneKeyValueMap.create(runtime, builder.build()));
        handle.getRoot().setPreferredHeight(MAP_HEIGHT);
        return card;
    }

    /**
     * 创建卡片外壳。
     *
     * @param title 卡片标题
     * @param helper 帮助说明
     * @return 卡片节点
     */
    private SceneNode createCardShell(String title, String helper) {
        return SceneDemoCards.cardShell(title, helper);
    }

    /**
     * 创建徽标节点。
     *
     * @param label 文案源
     * @param color 颜色
     * @return 徽标节点
     */
    private SceneNode badge(Computed<String> label, int color) {
        SceneNode node = new SceneNode();
        node.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        node.setPadding(8);
        node.setCornerRadius(999);
        node.setBorderWidth(1);
        node.setBorderColor(color);
        node.setBackgroundColor(SceneDemoTokens.READOUT_BG);
        node.setHitTestable(false);
        SceneNode textNode = text("", color);
        node.appendChild(textNode);
        runtime.bind(label, textNode::setText);
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

    /** 递增行变更计数。 */
    private void incrementChangeCount() {
        changeCount.set(Integer.valueOf(changeCount.get().intValue() + 1));
    }

    /**
     * 更新最近校验错误。
     *
     * @param error 校验错误载荷
     */
    private void updateValidationError(ValidationError error) {
        if (error == null || error.getType() == ValidationErrorType.NONE) {
            latestValidationError.set("通过");
            return;
        }
        latestValidationError.set(error.getType() + " @ row " + error.getRowIndex());
    }

    /**
     * 获取三组配置总行数。
     *
     * @return 总行数
     */
    private int totalCount() {
        return safeSize(basicRows.get()) + safeSize(validationRows.get()) + safeSize(boundedRows.get());
    }

    /**
     * 获取安全列表长度。
     *
     * @param rows 行列表
     * @return 行数
     */
    private static int safeSize(List<KeyValueRow> rows) {
        return rows == null ? 0 : rows.size();
    }

    /**
     * 创建基础配置示例数据。
     *
     * @return 配置行列表
     */
    private static List<KeyValueRow> basicConfigRows() {
        return Collections.unmodifiableList(Arrays.asList(
                new KeyValueRow("profileName", "default", ValueType.STRING),
                new KeyValueRow("renderDistance", "12", ValueType.NUMBER),
                new KeyValueRow("debugOverlay", "false", ValueType.BOOLEAN),
                new KeyValueRow("themeAccent", "blue", ValueType.STRING),
                new KeyValueRow("cacheSizeMb", "128", ValueType.NUMBER)));
    }

    /**
     * 创建校验反馈示例数据。
     *
     * @return 配置行列表
     */
    private static List<KeyValueRow> validationRows() {
        return Collections.unmodifiableList(Arrays.asList(
                new KeyValueRow("", "missing key", ValueType.STRING),
                new KeyValueRow("serverPort", "25565", ValueType.NUMBER),
                new KeyValueRow("serverPort", "25566", ValueType.NUMBER),
                new KeyValueRow("feature.safeMode", "true", ValueType.BOOLEAN),
                new KeyValueRow("displayName", "Scene Demo", ValueType.STRING)));
    }

    /**
     * 创建边界配置示例数据。
     *
     * @return 配置行列表
     */
    private static List<KeyValueRow> boundedConfigRows() {
        return Collections.unmodifiableList(Arrays.asList(
                new KeyValueRow("requiredAlpha", "enabled", ValueType.STRING),
                new KeyValueRow("requiredBeta", "42", ValueType.NUMBER),
                new KeyValueRow("optionalGamma", "true", ValueType.BOOLEAN),
                new KeyValueRow("optionalDelta", "compact", ValueType.STRING)));
    }

    @Override
    protected SceneNode getRoot() {
        return root;
    }

    /** @return 滚动容器节点（ROW：viewport + scrollbarColumn） */
    SceneNode __getScrollContainer() {
        return scrollContainer;
    }

    /** @return 滚动条列节点（scrollContainer 内 viewport 右侧独立列） */
    SceneNode __getScrollbarColumn() {
        return scrollbarColumn;
    }
}
