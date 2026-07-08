package club.heiqi.config.ui.field;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import club.heiqi.config.schema.FieldSpec;
import club.heiqi.config.ui.DraftSignalAdapter;
import club.heiqi.config.ui.theme.ConfigTheme;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.form.FormTheme;
import club.heiqi.uilib.ui.scene.input.SceneCursor;
import club.heiqi.uilib.ui.scene.input.SceneEvent;
import club.heiqi.uilib.ui.scene.input.SceneEventContext;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneInteractionState;
import club.heiqi.uilib.ui.scene.layout.AnchorRect;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.layout.MainAxisAlign;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;

/**
 * fontSort 专用只读排序渲染器。
 *
 * <p>字段真值仍是 {@code List<String>}，本类只负责把它渲染为可拖拽排序的字体名列表：
 * 行内只有拖拽把手与字体名文本，不提供输入框、添加按钮或删除按钮。发现态预填充语义沿用
 * {@link SimpleListFieldRenderer}：draft 为空且发现源非空时经
 * {@link DraftSignalAdapter#seedFieldBaseline} 同时写入 draft/current，保持 dirty=false；
 * 用户拖拽调序后才写回 draft 并标脏。</p>
 */
public final class FontSortFieldRenderer implements FieldRenderer {

    /** 控件根纵向间距。 */
    private static final int ROOT_GAP = 6;
    /** 列表行间距。 */
    private static final int LIST_GAP = 6;
    /** 行内控件间距。 */
    private static final int ROW_GAP = 6;
    /** 字体行卡片固定高度。 */
    private static final int ROW_CARD_HEIGHT = 36;
    /** 字体行卡片 idle 背景色。 */
    private static final int ROW_CARD_BG_IDLE = 0xFF152238;
    /** 字体行卡片 hover 背景色。 */
    private static final int ROW_CARD_BG_HOVER = 0xFF1E2E4A;
    /** 字体行卡片边框色。 */
    private static final int ROW_CARD_BORDER = 0xFF2F4D87;
    /** 拖拽把手固定宽度。 */
    private static final int HANDLE_WIDTH = 24;
    /** 拖拽把手图标。 */
    private static final String HANDLE_ICON = "\u2261";
    /** 拖拽把手 idle 背景色。 */
    private static final int HANDLE_BG_IDLE = 0x00000000;
    /** 拖拽把手 hover 背景色。 */
    private static final int HANDLE_BG_HOVER = SceneChromeTokens.BG_HOVER;
    /** 拖拽把手 pressed 背景色。 */
    private static final int HANDLE_BG_PRESSED = SceneChromeTokens.BG_PRESSED;
    /** 行 id 分配器，用于 keyed 列表稳定身份。 */
    private static final AtomicLong NEXT_ITEM_ID = new AtomicLong(1L);

    /** 发现态预填充源，null 表示不预填充。 */
    private final Supplier<List<String>> prefillWhenEmpty;

    /**
     * 创建无预填充源的 fontSort 渲染器。
     */
    public FontSortFieldRenderer() {
        this(null);
    }

    /**
     * 创建带发现态预填充源的 fontSort 渲染器。
     *
     * @param prefillWhenEmpty 预填充源；null 表示不预填充
     */
    public FontSortFieldRenderer(Supplier<List<String>> prefillWhenEmpty) {
        this.prefillWhenEmpty = prefillWhenEmpty;
    }

    @Override
    public SceneNode render(SceneRuntime rt, FieldSpec spec, DraftSignalAdapter adapter) {
        final String path = spec.path();
        final ReadableSignal<Object> draftSig = adapter.draftSignal(path);
        final FormTheme theme = ConfigTheme.asFormTheme();

        List<String> initial = toDraftList(draftSig.get());
        if (initial.isEmpty() && prefillWhenEmpty != null) {
            List<String> prefill = prefillWhenEmpty.get();
            if (prefill != null && !prefill.isEmpty()) {
                adapter.seedFieldBaseline(path, new ArrayList<String>(prefill));
                initial = new ArrayList<String>(prefill);
            }
        }

        final Signal<List<FontSortItem>> localItems = Signal.create(toItems(initial));
        rt.bind(draftSig, draftValue -> {
            List<String> incoming = toDraftList(draftValue);
            List<String> currentProjection = projectValues(localItems.get());
            if (!incoming.equals(currentProjection)) {
                localItems.set(toItems(incoming));
            }
        });

        return FieldShellBinder.build(rt, spec, adapter,
                () -> buildControl(rt, localItems, path, adapter, theme),
                theme, theme.listHeight());
    }

    /**
     * 构建只读字体排序控件。
     *
     * @param rt         场景运行时
     * @param localItems 本地字体行列表
     * @param path       字段路径
     * @param adapter    草稿适配器
     * @param theme      主题 token
     * @return 控件根节点
     */
    private static SceneNode buildControl(SceneRuntime rt,
                                          Signal<List<FontSortItem>> localItems,
                                          String path,
                                          DraftSignalAdapter adapter,
                                          FormTheme theme) {
        SceneNode root = SceneNode.column();
        root.setGap(ROOT_GAP);

        SceneNode viewport = SceneNode.column();
        viewport.setGap(LIST_GAP);
        viewport.setScrollable(true);
        viewport.setClipChildren(true);
        viewport.setFillParentHeight(true);
        viewport.setFlexGrow(1);

        SceneNode stackHost = SceneNode.row();
        stackHost.setFillParentHeight(true);
        stackHost.appendChild(viewport);

        Signal<Integer> scrollSignal = SceneScrolls.attach(rt, viewport);
        SceneScrollbar.Result scrollbar = SceneScrollbar.createDefault(rt, viewport, scrollSignal);
        stackHost.appendChild(scrollbar.column());
        root.appendChild(stackHost);

        Computed<List<FontSortItem>> itemsComputed = Computed.create(() -> safeItems(localItems.get()));
        rt.forEach(viewport, itemsComputed, FontSortItem::getId,
                row -> buildRow(rt, localItems, path, adapter, viewport, row, theme));
        return root;
    }

    /**
     * 构建字体名只读行。
     */
    private static SceneNode buildRow(SceneRuntime rt,
                                      Signal<List<FontSortItem>> localItems,
                                      String path,
                                      DraftSignalAdapter adapter,
                                      SceneNode viewport,
                                      FontSortItem row,
                                      FormTheme theme) {
        SceneNode line = SceneNode.row();
        line.setCrossAxisAlign(CrossAxisAlign.CENTER);
        line.setGap(ROW_GAP);
        line.setPreferredHeight(ROW_CARD_HEIGHT);
        line.setPadding(0, SceneChromeTokens.PAD_MD, 0, SceneChromeTokens.PAD_MD);
        line.setBackgroundColor(ROW_CARD_BG_IDLE);
        line.setBorderWidth(1);
        line.setBorderColor(ROW_CARD_BORDER);
        line.setCornerRadius(SceneChromeTokens.RADIUS_MD);

        SceneNode handle = buildDragHandle(rt, localItems, path, adapter, viewport, row);
        SceneInteractionState lineInteraction = rt.interactionState(line);
        SceneInteractionState handleInteraction = rt.interactionState(handle);
        rt.bindComputed(() -> {
            boolean lineHovered = Boolean.TRUE.equals(lineInteraction.hovered().get());
            boolean handleHovered = Boolean.TRUE.equals(handleInteraction.hovered().get());
            boolean handlePressed = Boolean.TRUE.equals(handleInteraction.pressed().get());
            return lineHovered || handleHovered || handlePressed ? ROW_CARD_BG_HOVER : ROW_CARD_BG_IDLE;
        }, line::setBackgroundColor);

        line.appendChild(handle);

        SceneNode label = new SceneNode();
        label.setHitTestable(false);
        label.setText(row.getValue());
        label.setTextColor(theme.textColor());
        label.setFontSize(theme.fontLabel());
        line.appendChild(label);
        return line;
    }

    /**
     * 构建拖拽把手并注册排序事件。
     */
    private static SceneNode buildDragHandle(SceneRuntime rt,
                                             Signal<List<FontSortItem>> localItems,
                                             String path,
                                             DraftSignalAdapter adapter,
                                             SceneNode viewport,
                                             FontSortItem row) {
        final long dragId = row.getId();
        final boolean[] dragging = {false};

        SceneNode handle = SceneNode.row();
        handle.setMainAxisAlign(MainAxisAlign.CENTER);
        handle.setCrossAxisAlign(CrossAxisAlign.CENTER);
        handle.setPreferredWidth(HANDLE_WIDTH);
        handle.setPreferredHeight(SceneChromeTokens.INPUT_HEIGHT);
        handle.setCornerRadius(SceneChromeTokens.RADIUS_MD);
        handle.setCursor(SceneCursor.GRAB);
        handle.setBackgroundColor(HANDLE_BG_IDLE);

        SceneNode icon = new SceneNode();
        icon.setHitTestable(false);
        icon.setText(HANDLE_ICON);
        icon.setTextColor(SceneChromeTokens.TEXT_SECONDARY);
        handle.appendChild(icon);

        SceneInteractionState interaction = rt.interactionState(handle);
        rt.bindComputed(() -> {
            boolean hovered = Boolean.TRUE.equals(interaction.hovered().get());
            boolean pressed = Boolean.TRUE.equals(interaction.pressed().get());
            if (pressed) {
                return HANDLE_BG_PRESSED;
            }
            if (hovered) {
                return HANDLE_BG_HOVER;
            }
            return HANDLE_BG_IDLE;
        }, handle::setBackgroundColor);

        rt.on(handle, SceneEventType.POINTER_DOWN, (SceneEvent ev, SceneEventContext ctx) -> {
            dragging[0] = true;
            ctx.requestPointerCapture();
            ctx.stopPropagation();
        });
        rt.on(handle, SceneEventType.POINTER_MOVE, (SceneEvent ev, SceneEventContext ctx) -> {
            if (!dragging[0]) {
                return;
            }
            int targetIndex = pointerToRowIndex(viewport, handle, ctx.getRawPointerY(), ctx.getLocalPointerY());
            if (targetIndex >= 0) {
                moveItem(localItems, path, adapter, dragId, targetIndex);
            }
            ctx.stopPropagation();
        });
        rt.on(handle, SceneEventType.POINTER_UP, (SceneEvent ev, SceneEventContext ctx) -> {
            dragging[0] = false;
            ctx.stopPropagation();
        });
        rt.on(handle, SceneEventType.POINTER_CANCEL, (SceneEvent ev, SceneEventContext ctx) -> {
            dragging[0] = false;
        });
        return handle;
    }

    /**
     * 按指针 Y 计算拖拽目标行 index。
     */
    private static int pointerToRowIndex(SceneNode viewport, SceneNode handle, int rawPointerY, int handleLocalY) {
        List<SceneNode> children = viewport.__getChildren();
        if (children.isEmpty()) {
            return -1;
        }
        int handleLayoutY = SceneGeometry.absoluteBox(handle, 0, 0).getY();
        int treeRootAbsY = (rawPointerY - handleLocalY) - handleLayoutY;
        int lastIndex = children.size() - 1;
        for (int i = 0; i <= lastIndex; i++) {
            AnchorRect box = SceneGeometry.absoluteBox(children.get(i), 0, 0);
            int screenTop = box.getY() + treeRootAbsY;
            int center = screenTop + box.getHeight() / 2;
            if (rawPointerY < center) {
                return i;
            }
        }
        return lastIndex;
    }

    /**
     * 移动指定字体行并写回 draft。
     */
    private static void moveItem(Signal<List<FontSortItem>> localItems,
                                 String path,
                                 DraftSignalAdapter adapter,
                                 long fromId,
                                 int toIndex) {
        List<FontSortItem> current = safeItems(localItems.get());
        int fromIndex = -1;
        for (int i = 0; i < current.size(); i++) {
            if (current.get(i).getId() == fromId) {
                fromIndex = i;
                break;
            }
        }
        if (fromIndex < 0 || toIndex < 0 || toIndex >= current.size() || fromIndex == toIndex) {
            return;
        }
        List<FontSortItem> next = new ArrayList<FontSortItem>(current);
        FontSortItem moved = next.remove(fromIndex);
        next.add(toIndex, moved);
        commit(localItems, path, adapter, next);
    }

    /**
     * 提交排序变更。
     */
    private static void commit(Signal<List<FontSortItem>> localItems,
                               String path,
                               DraftSignalAdapter adapter,
                               List<FontSortItem> next) {
        List<FontSortItem> immutable = Collections.unmodifiableList(new ArrayList<FontSortItem>(next));
        localItems.set(immutable);
        adapter.onFieldEdit(path, projectValues(immutable));
    }

    @SuppressWarnings("unchecked")
    private static List<String> toDraftList(Object value) {
        if (value instanceof List) {
            List<String> out = new ArrayList<String>(((List<Object>) value).size());
            for (Object item : (List<Object>) value) {
                out.add(item == null ? "" : String.valueOf(item));
            }
            return out;
        }
        return new ArrayList<String>();
    }

    private static List<FontSortItem> toItems(List<String> values) {
        List<FontSortItem> out = new ArrayList<FontSortItem>(values.size());
        for (String value : values) {
            out.add(new FontSortItem(value));
        }
        return Collections.unmodifiableList(out);
    }

    private static List<String> projectValues(List<FontSortItem> items) {
        List<FontSortItem> safe = safeItems(items);
        List<String> out = new ArrayList<String>(safe.size());
        for (FontSortItem item : safe) {
            out.add(item.getValue());
        }
        return out;
    }

    private static List<FontSortItem> safeItems(List<FontSortItem> items) {
        return items == null ? Collections.<FontSortItem>emptyList() : items;
    }

    /** fontSort 行数据，id 稳定用于 keyed diff。 */
    private static final class FontSortItem {

        private final long id;
        private final String value;

        private FontSortItem(String value) {
            this.id = NEXT_ITEM_ID.getAndIncrement();
            this.value = value == null ? "" : value;
        }

        private long getId() {
            return id;
        }

        private String getValue() {
            return value;
        }
    }
}
