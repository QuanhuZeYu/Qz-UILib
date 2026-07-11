package club.heiqi.uilib.ui.scene.control;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import club.heiqi.config.ui.editor.SearchPickerData;
import club.heiqi.config.ui.editor.VisualAdapter;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.image.SceneImageSource;
import club.heiqi.uilib.ui.scene.input.SceneEventType;
import club.heiqi.uilib.ui.scene.input.SceneKey;
import club.heiqi.uilib.ui.scene.input.SceneKeyAction;
import club.heiqi.uilib.ui.scene.layout.CrossAxisAlign;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.SceneNode.WidthSizing;
import club.heiqi.uilib.ui.scene.overlay.AnchorProvider;
import club.heiqi.uilib.ui.scene.overlay.OverlayDismissPolicy;
import club.heiqi.uilib.ui.scene.paint.SceneChromeTokens;
import club.heiqi.uilib.ui.scene.paint.SceneStateColors;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/** 通用、平台无关的受控搜索选择器。 */
public final class SceneSearchPicker {
    /** 搜索选择器浮层阶段。 */
    public enum State { CLOSED, CANDIDATES, VARIANTS }

    private static final int ICON_SIZE = 18;
    private static final int PLACEHOLDER_COLOR = 0xFF454B54;

    private SceneSearchPicker() { }

    /** 搜索选择器输入契约。 */
    public static final class Props {
        private final ReadableSignal<String> query;
        private final ReadableSignal<SearchPickerData.SearchResult> results;
        private final ReadableSignal<Boolean> enabled;
        private final ReadableSignal<SearchPickerData.Selection> currentSelection;
        private final Consumer<String> onQuery;
        private final Consumer<SearchPickerData.Selection> onSelect;
        private final VisualAdapter visualAdapter;

        /** 创建受控搜索选择器属性。 */
        public Props(ReadableSignal<String> query,
                     ReadableSignal<SearchPickerData.SearchResult> results,
                     ReadableSignal<Boolean> enabled,
                     Consumer<String> onQuery,
                     Consumer<SearchPickerData.Selection> onSelect,
                     VisualAdapter visualAdapter) {
            this.query = Objects.requireNonNull(query, "query");
            this.results = Objects.requireNonNull(results, "results");
            this.enabled = Objects.requireNonNull(enabled, "enabled");
            this.currentSelection = Signal.create(null);
            this.onQuery = Objects.requireNonNull(onQuery, "onQuery");
            this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
            this.visualAdapter = Objects.requireNonNull(visualAdapter, "visualAdapter");
        }

        private Props(Builder builder) {
            query = builder.query; results = builder.results; enabled = builder.enabled;
            onQuery = builder.onQuery; onSelect = builder.onSelect; visualAdapter = builder.visualAdapter;
            currentSelection = builder.currentSelection;
        }

        /** 创建保留旧六参必填项的 builder。 */
        public static Builder builder(ReadableSignal<String> query,
                                      ReadableSignal<SearchPickerData.SearchResult> results,
                                      ReadableSignal<Boolean> enabled, Consumer<String> onQuery,
                                      Consumer<SearchPickerData.Selection> onSelect,
                                      VisualAdapter visualAdapter) {
            return new Builder(query, results, enabled, onQuery, onSelect, visualAdapter);
        }

        /** 搜索选择器可选属性 builder。 */
        public static final class Builder {
            private final ReadableSignal<String> query;
            private final ReadableSignal<SearchPickerData.SearchResult> results;
            private final ReadableSignal<Boolean> enabled;
            private final Consumer<String> onQuery;
            private final Consumer<SearchPickerData.Selection> onSelect;
            private final VisualAdapter visualAdapter;
            private ReadableSignal<SearchPickerData.Selection> currentSelection = Signal.create(null);

            private Builder(ReadableSignal<String> query, ReadableSignal<SearchPickerData.SearchResult> results,
                            ReadableSignal<Boolean> enabled, Consumer<String> onQuery,
                            Consumer<SearchPickerData.Selection> onSelect, VisualAdapter visualAdapter) {
                this.query = Objects.requireNonNull(query, "query");
                this.results = Objects.requireNonNull(results, "results");
                this.enabled = Objects.requireNonNull(enabled, "enabled");
                this.onQuery = Objects.requireNonNull(onQuery, "onQuery");
                this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
                this.visualAdapter = Objects.requireNonNull(visualAdapter, "visualAdapter");
            }

            /** 设置受控当前选择。 */
            public Builder currentSelection(ReadableSignal<SearchPickerData.Selection> value) {
                currentSelection = Objects.requireNonNull(value, "currentSelection"); return this;
            }

            /** 构建不可变属性。 */
            public Props build() { return new Props(this); }
        }
    }

    /** 构建搜索选择器组件。 */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            Signal<Boolean> candidatesOpen = Signal.create(Boolean.FALSE);
            Signal<Boolean> variantsOpen = Signal.create(Boolean.FALSE);
            Signal<Integer> highlighted = Signal.create(Integer.valueOf(0));
            Signal<SearchPickerData.Candidate> activeCandidate = Signal.create(null);
            Signal<SearchPickerData.SelectionMode> mode = Signal.create(SearchPickerData.SelectionMode.ALL);
            Signal<List<String>> selectedKeys = Signal.create(Collections.<String>emptyList());

            SceneNode root = SceneNode.column();
            SceneNode input = SceneTextInput.create(rt, SceneTextInput.Props.builder(props.query)
                    .enabled(props.enabled).placeholder("Search").onChange(value -> {
                        props.onQuery.accept(value);
                        highlighted.set(Integer.valueOf(0));
                        candidatesOpen.set(Boolean.TRUE);
                        variantsOpen.set(Boolean.FALSE);
                    }).build()).get();
            root.appendChild(input);

            rt.on(input, SceneEventType.CLICK, (ev, ctx) -> {
                if (Boolean.TRUE.equals(props.enabled.get())) {
                    candidatesOpen.set(Boolean.TRUE);
                    variantsOpen.set(Boolean.FALSE);
                }
            });
            rt.on(input, SceneEventType.KEY_DOWN, (ev, ctx) -> {
                if (!Boolean.TRUE.equals(props.enabled.get()) || ev.getKeyAction() != SceneKeyAction.PRESSED
                        || ev.isRepeat()) return;
                List<SearchPickerData.Candidate> values = safeResults(props).candidates();
                SearchPickerData.Candidate active = activeCandidate.get();
                List<SearchPickerData.Variant> variants = active == null
                        ? Collections.<SearchPickerData.Variant>emptyList() : active.variants();
                if (ev.getKey() == SceneKey.ARROW_DOWN || ev.getKey() == SceneKey.ARROW_UP) {
                    int delta = ev.getKey() == SceneKey.ARROW_DOWN ? 1 : -1;
                    if (Boolean.TRUE.equals(variantsOpen.get())) {
                        highlighted.set(Integer.valueOf(clamp(highlighted.get().intValue() + delta, variants.size())));
                    } else {
                        candidatesOpen.set(Boolean.TRUE);
                        variantsOpen.set(Boolean.FALSE);
                        highlighted.set(Integer.valueOf(clamp(highlighted.get().intValue() + delta, values.size())));
                    }
                    ctx.stopPropagation();
                } else if (ev.getKey() == SceneKey.ENTER && Boolean.TRUE.equals(variantsOpen.get())
                        && active != null && canConfirm(mode.get(), selectedKeys.get())) {
                    props.onSelect.accept(new SearchPickerData.Selection(active.key(), mode.get(),
                            orderedKeys(variants, selectedKeys.get())));
                    close(candidatesOpen, variantsOpen);
                    ctx.stopPropagation();
                } else if (ev.getKey() == SceneKey.SPACE && Boolean.TRUE.equals(variantsOpen.get())
                        && active != null && !variants.isEmpty()) {
                    String key = variants.get(clamp(highlighted.get().intValue(), variants.size())).key();
                    updateVariant(mode.get(), selectedKeys, key, !selectedKeys.get().contains(key));
                    ctx.stopPropagation();
                } else if (ev.getKey() == SceneKey.ENTER && Boolean.TRUE.equals(candidatesOpen.get()) && !values.isEmpty()) {
                    chooseCandidate(values.get(clamp(highlighted.get().intValue(), values.size())), props,
                            activeCandidate, candidatesOpen, variantsOpen, highlighted, mode, selectedKeys);
                    ctx.stopPropagation();
                } else if (ev.getKey() == SceneKey.ESCAPE
                        && (Boolean.TRUE.equals(candidatesOpen.get()) || Boolean.TRUE.equals(variantsOpen.get()))) {
                    close(candidatesOpen, variantsOpen);
                    ctx.stopPropagation();
                }
            });

            AnchorProvider anchor = AnchorProvider.forNode(input);
            rt.portalAnchored(candidatesOpen,
                    () -> candidatePortal(rt, props, activeCandidate, candidatesOpen, variantsOpen, highlighted,
                            mode, selectedKeys),
                    OverlayDismissPolicy.DEFAULT, () -> close(candidatesOpen, variantsOpen), anchor);
            rt.portalAnchored(variantsOpen,
                    () -> variantPortal(rt, props, activeCandidate, candidatesOpen, variantsOpen, highlighted,
                            mode, selectedKeys),
                    OverlayDismissPolicy.DEFAULT, () -> close(candidatesOpen, variantsOpen), anchor);
            return root;
        };
    }

    private static SceneNode candidatePortal(SceneRuntime rt, Props props,
                                               Signal<SearchPickerData.Candidate> activeCandidate,
                                               Signal<Boolean> candidatesOpen, Signal<Boolean> variantsOpen,
                                               Signal<Integer> highlighted,
                                               Signal<SearchPickerData.SelectionMode> mode,
                                               Signal<List<String>> selectedKeys) {
        SceneNode list = portalRoot();
        SceneNode itemsContainer = SceneNode.column();
        SceneNode footerContainer = SceneNode.column();
        itemsContainer.setWidthSizing(WidthSizing.SHRINK);
        footerContainer.setWidthSizing(WidthSizing.SHRINK);
        list.appendChild(itemsContainer);
        list.appendChild(footerContainer);
        ReadableSignal<List<SearchPickerData.Candidate>> items = Computed.create(() -> safeResults(props).candidates());
        rt.forEach(itemsContainer, items, SearchPickerData.Candidate::key, candidate -> item(rt,
                props.visualAdapter.candidateImage(candidate), props.visualAdapter.candidateLabel(candidate), () ->
                        chooseCandidate(candidate, props, activeCandidate, candidatesOpen, variantsOpen, highlighted,
                                mode, selectedKeys)));
        rt.show(footerContainer, Computed.create(() -> Boolean.valueOf(safeResults(props).truncated())),
                () -> text("Results truncated"));
        return list;
    }

    private static SceneNode variantPortal(SceneRuntime rt, Props props,
                                            Signal<SearchPickerData.Candidate> activeCandidate,
                                            Signal<Boolean> candidatesOpen, Signal<Boolean> variantsOpen,
                                             Signal<Integer> highlighted,
                                             Signal<SearchPickerData.SelectionMode> mode,
                                             Signal<List<String>> selectedKeys) {
        SceneNode list = portalRoot();
        SceneNode modes = SceneSegmented.create(rt, new SceneSegmented.Props(
                Computed.create(() -> Integer.valueOf(mode.get().ordinal())),
                Arrays.asList("All", "Single", "Multiple"), props.enabled, index -> {
                    SearchPickerData.Candidate candidate = activeCandidate.get();
                    if (candidate == null) return;
                    SearchPickerData.SelectionMode next = SearchPickerData.SelectionMode.values()[index.intValue()];
                    mode.set(next);
                    selectedKeys.set(defaultKeys(next, selectedKeys.get(), candidate.variants()));
                })).get();
        list.appendChild(modes);
        SceneNode itemsContainer = SceneNode.column();
        itemsContainer.setWidthSizing(WidthSizing.SHRINK);
        list.appendChild(itemsContainer);
        ReadableSignal<List<SearchPickerData.Variant>> items = Computed.create(() -> {
            SearchPickerData.Candidate candidate = activeCandidate.get();
            return candidate == null ? Collections.<SearchPickerData.Variant>emptyList() : candidate.variants();
        });
        rt.forEach(itemsContainer, items, SearchPickerData.Variant::key, variant ->
                variantItem(props.visualAdapter.variantImage(variant),
                        SceneCheckbox.create(rt, new SceneCheckbox.Props(
                                Computed.create(() -> Boolean.valueOf(selectedKeys.get().contains(variant.key()))),
                                Signal.create(props.visualAdapter.variantLabel(variant)),
                                Computed.create(() -> Boolean.valueOf(
                                        mode.get() != SearchPickerData.SelectionMode.ALL)),
                                checked -> updateVariant(mode.get(), selectedKeys, variant.key(), checked))).get()));
        SceneNode actions = SceneNode.row();
        actions.setGap(SceneChromeTokens.GAP_MD);
        SceneNode cancel = SceneButton.create(rt, new SceneButton.Props(Signal.create("Cancel"),
                Signal.create(Boolean.TRUE), () -> close(candidatesOpen, variantsOpen))).get();
        cancel.setWidthSizing(WidthSizing.SHRINK);
        actions.appendChild(cancel);
        SceneNode confirm = SceneButton.create(rt, new SceneButton.Props(Signal.create("Confirm"),
                Computed.create(() -> Boolean.valueOf(canConfirm(mode.get(), selectedKeys.get()))), () -> {
                    SearchPickerData.Candidate candidate = activeCandidate.get();
                    if (candidate != null) props.onSelect.accept(new SearchPickerData.Selection(
                            candidate.key(), mode.get(), orderedKeys(candidate.variants(), selectedKeys.get())));
                    close(candidatesOpen, variantsOpen);
                })).get();
        confirm.setWidthSizing(WidthSizing.SHRINK);
        actions.appendChild(confirm);
        list.appendChild(actions);
        return list;
    }

    private static SceneNode item(SceneRuntime rt, SceneImageSource image, String label, Runnable activate) {
        SceneNode item = SceneNode.row();
        item.setWidthSizing(WidthSizing.SHRINK);
        item.setCrossAxisAlign(CrossAxisAlign.CENTER);
        item.setGap(SceneChromeTokens.GAP_MD);
        item.setPadding(SceneChromeTokens.PAD_MD);
        SceneNode icon = new SceneNode();
        icon.setPreferredWidth(ICON_SIZE).setPreferredHeight(ICON_SIZE).setHitTestable(false);
        if (image == null) icon.setBackgroundColor(PLACEHOLDER_COLOR); else icon.setImageSource(image);
        SceneNode text = text(label);
        item.appendChild(icon);
        item.appendChild(text);
        rt.on(item, SceneEventType.CLICK, (ev, ctx) -> { activate.run(); ctx.stopPropagation(); });
        return item;
    }

    /** 创建由 checkbox 根承接命中的变体图片行。 */
    private static SceneNode variantItem(SceneImageSource image, SceneNode checkbox) {
        SceneNode row = SceneNode.row();
        row.setWidthSizing(WidthSizing.SHRINK);
        row.setCrossAxisAlign(CrossAxisAlign.CENTER);
        row.setGap(SceneChromeTokens.GAP_MD);
        SceneNode icon = new SceneNode();
        icon.setPreferredWidth(ICON_SIZE).setPreferredHeight(ICON_SIZE).setHitTestable(false);
        if (image == null) icon.setBackgroundColor(PLACEHOLDER_COLOR); else icon.setImageSource(image);
        row.appendChild(icon);
        row.appendChild(checkbox);
        return row;
    }

    private static SceneNode portalRoot() {
        SceneNode list = SceneNode.column();
        list.setWidthSizing(WidthSizing.SHRINK);
        list.setBackgroundColor(SceneStateColors.inputBackground(true));
        list.setBorderWidth(1);
        list.setBorderColor(SceneChromeTokens.BORDER_DEFAULT);
        return list;
    }

    private static SceneNode text(String value) {
        SceneNode text = new SceneNode();
        text.setText(value == null ? "" : value);
        text.setHitTestable(false);
        return text;
    }

    private static void chooseCandidate(SearchPickerData.Candidate candidate, Props props,
                                        Signal<SearchPickerData.Candidate> activeCandidate,
                                        Signal<Boolean> candidatesOpen, Signal<Boolean> variantsOpen,
                                        Signal<Integer> highlighted,
                                        Signal<SearchPickerData.SelectionMode> mode,
                                        Signal<List<String>> selectedKeys) {
        if (candidate.variants().isEmpty()) {
            props.onSelect.accept(new SearchPickerData.Selection(candidate.key(), SearchPickerData.SelectionMode.ALL,
                    Collections.<String>emptyList()));
            close(candidatesOpen, variantsOpen);
        } else {
            SearchPickerData.Selection current = props.currentSelection.get();
            boolean restore = current != null && candidate.key().equals(current.candidateKey())
                    && containsOnly(candidate.variants(), current.variantKeys());
            mode.set(restore ? current.mode() : SearchPickerData.SelectionMode.ALL);
            selectedKeys.set(restore ? orderedKeys(candidate.variants(), current.variantKeys())
                    : Collections.<String>emptyList());
            activeCandidate.set(candidate);
            highlighted.set(Integer.valueOf(0));
            candidatesOpen.set(Boolean.FALSE);
            variantsOpen.set(Boolean.TRUE);
        }
    }

    private static boolean containsOnly(List<SearchPickerData.Variant> variants, List<String> keys) {
        ArrayList<String> available = new ArrayList<String>();
        for (SearchPickerData.Variant variant : variants) available.add(variant.key());
        return available.containsAll(keys);
    }

    private static List<String> defaultKeys(SearchPickerData.SelectionMode mode, List<String> current,
                                            List<SearchPickerData.Variant> variants) {
        if (mode == SearchPickerData.SelectionMode.ALL) return Collections.emptyList();
        if (mode == SearchPickerData.SelectionMode.MULTIPLE) {
            ArrayList<String> all = new ArrayList<String>();
            for (SearchPickerData.Variant variant : variants) all.add(variant.key());
            return Collections.unmodifiableList(all);
        }
        String key = current.isEmpty() ? variants.get(0).key() : current.get(0);
        return Collections.singletonList(key);
    }

    private static void updateVariant(SearchPickerData.SelectionMode mode, Signal<List<String>> keys,
                                      String key, Boolean checked) {
        if (mode == SearchPickerData.SelectionMode.ALL) return;
        if (mode == SearchPickerData.SelectionMode.SINGLE) {
            if (Boolean.TRUE.equals(checked)) keys.set(Collections.singletonList(key));
            return;
        }
        ArrayList<String> next = new ArrayList<String>(keys.get());
        if (Boolean.TRUE.equals(checked)) { if (!next.contains(key)) next.add(key); } else next.remove(key);
        keys.set(Collections.unmodifiableList(next));
    }

    private static boolean canConfirm(SearchPickerData.SelectionMode mode, List<String> keys) {
        return mode == SearchPickerData.SelectionMode.ALL
                || mode == SearchPickerData.SelectionMode.SINGLE && keys.size() == 1
                || mode == SearchPickerData.SelectionMode.MULTIPLE && keys.size() >= 2;
    }

    private static List<String> orderedKeys(List<SearchPickerData.Variant> variants, List<String> keys) {
        ArrayList<String> ordered = new ArrayList<String>();
        for (SearchPickerData.Variant variant : variants) if (keys.contains(variant.key())) ordered.add(variant.key());
        return ordered;
    }

    private static SearchPickerData.SearchResult safeResults(Props props) {
        SearchPickerData.SearchResult value = props.results.get();
        return value == null ? SearchPickerData.SearchResult.empty() : value;
    }

    private static int clamp(int value, int size) {
        return size == 0 ? 0 : Math.max(0, Math.min(size - 1, value));
    }

    private static void close(Signal<Boolean> candidatesOpen, Signal<Boolean> variantsOpen) {
        candidatesOpen.set(Boolean.FALSE);
        variantsOpen.set(Boolean.FALSE);
    }
}
