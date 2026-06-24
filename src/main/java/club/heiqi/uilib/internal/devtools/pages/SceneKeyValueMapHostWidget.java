package club.heiqi.uilib.internal.devtools.pages;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.component.MountHandle;
import club.heiqi.uilib.ui.scene.component.SceneScrolls;
import club.heiqi.uilib.ui.scene.control.SceneKeyValueMap;
import club.heiqi.uilib.ui.scene.control.SceneKeyValueMap.KeyValueRow;
import club.heiqi.uilib.ui.scene.control.SceneKeyValueMap.ValidationError;
import club.heiqi.uilib.ui.scene.control.SceneKeyValueMap.ValidationErrorType;
import club.heiqi.uilib.ui.scene.control.SceneKeyValueMap.ValueType;
import club.heiqi.uilib.ui.scene.input.PlatformInputSource;
import club.heiqi.uilib.ui.scene.layout.FlexDirection;
import club.heiqi.uilib.ui.scene.node.Invalidation;
import club.heiqi.uilib.ui.scene.node.SceneNode;

/**
 * 新栈 ui.scene KeyValueMap demo 宿主 Widget。
 */
public class SceneKeyValueMapHostWidget extends AbstractSceneHostWidget {

    private static final int ROOT_BG = 0xFF0B1424;
    private static final int VIEWPORT_BG = 0xFF081120;
    private static final int CARD_BG = 0xFF0D1728;
    private static final int CARD_BORDER = 0xFF2F4D87;
    private static final int TITLE_COLOR = 0xFFC9D8F8;
    private static final int TEXT_COLOR = 0xFFEAF1FF;
    private static final int MUTED_COLOR = 0xFF8AA0C8;
    private static final int ERROR_COLOR = 0xFFF87171;
    private static final int READOUT_BG = 0xFF1E293B;
    private static final int OK_COLOR = 0xFF34D399;
    private static final int DIRTY_COLOR = 0xFF60A5FA;
    private static final int TITLE_BAR_HEIGHT = 44;
    private static final int STATUS_HEIGHT = 34;
    private static final int MAP_HEIGHT = 255;

    private final SceneNode root;
    private final SceneNode viewport;
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
        root.appendChild(viewport);
        root.appendChild(createStatusBar());

        content.appendChild(createMapCard("基础用法", "含 STRING / NUMBER / BOOLEAN 三种配置值类型。",
                basicRows, "配置 key", "配置值", 0, 12, false));
        content.appendChild(createMapCard("校验反馈", "初始包含空 key 与重复 key，用于展示标红和校验回调。",
                validationRows, "不得为空或重复", "任意值", 0, 12, true));
        content.appendChild(createMapCard("边界测试", "最少保留 2 行，最多 8 行，用于验证增删按钮边界。",
                boundedRows, "边界 key", "边界值", 2, 8, false));

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
        SceneNode titleBar = new SceneNode();
        titleBar.setFlexDirection(FlexDirection.COLUMN);
        titleBar.setPreferredHeight(TITLE_BAR_HEIGHT);
        titleBar.setGap(4);
        titleBar.setHitTestable(false);
        titleBar.appendChild(text("SceneKeyValueMap Demo", TITLE_COLOR));
        titleBar.appendChild(text("动态键值配置 · 类型切换 · key 校验 · 行内文本输入", MUTED_COLOR));
        return titleBar;
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
        node.setBackgroundColor(VIEWPORT_BG);
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
     * 创建底部状态条。
     *
     * @return 状态条节点
     */
    private SceneNode createStatusBar() {
        SceneNode row = new SceneNode();
        row.setFlexDirection(FlexDirection.ROW);
        row.setPreferredHeight(STATUS_HEIGHT);
        row.setGap(10);
        row.appendChild(badge(Computed.create(() -> "当前行数：" + totalCount()), OK_COLOR));
        row.appendChild(badge(Computed.create(() -> "onRowsChanged：" + changeCount.get()), DIRTY_COLOR));
        row.appendChild(badge(Computed.create(() -> "最近校验：" + latestValidationError.get()), ERROR_COLOR));
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
        SceneNode card = new SceneNode();
        card.setFlexDirection(FlexDirection.COLUMN);
        card.setBackgroundColor(CARD_BG);
        card.setBorderWidth(1);
        card.setBorderColor(CARD_BORDER);
        card.setCornerRadius(10);
        card.setPadding(12);
        card.setGap(8);
        card.appendChild(text(title, TEXT_COLOR));
        card.appendChild(text(helper, MUTED_COLOR));
        return card;
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
        node.setBackgroundColor(READOUT_BG);
        node.setHitTestable(false);
        SceneNode textNode = text("", color);
        node.appendChild(textNode);
        runtime.bind(Invalidation.LAYOUT, label, textNode::setText);
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
}
