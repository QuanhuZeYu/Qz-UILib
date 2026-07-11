package club.heiqi.uilib.ui.scene.control;

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
            this.onQuery = Objects.requireNonNull(onQuery, "onQuery");
            this.onSelect = Objects.requireNonNull(onSelect, "onSelect");
            this.visualAdapter = Objects.requireNonNull(visualAdapter, "visualAdapter");
        }
    }

    /** 构建搜索选择器组件。 */
    public static Supplier<SceneNode> create(SceneRuntime rt, Props props) {
        return () -> {
            Signal<Boolean> candidatesOpen = Signal.create(Boolean.FALSE);
            Signal<Boolean> variantsOpen = Signal.create(Boolean.FALSE);
            Signal<Integer> highlighted = Signal.create(Integer.valueOf(0));
            Signal<SearchPickerData.Candidate> activeCandidate = Signal.create(null);

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
                        && active != null && !variants.isEmpty()) {
                    SearchPickerData.Variant variant = variants.get(clamp(highlighted.get().intValue(), variants.size()));
                    props.onSelect.accept(new SearchPickerData.Selection(active.key(), variant.key()));
                    close(candidatesOpen, variantsOpen);
                    ctx.stopPropagation();
                } else if (ev.getKey() == SceneKey.ENTER && Boolean.TRUE.equals(candidatesOpen.get()) && !values.isEmpty()) {
                    chooseCandidate(values.get(clamp(highlighted.get().intValue(), values.size())), props,
                            activeCandidate, candidatesOpen, variantsOpen, highlighted);
                    ctx.stopPropagation();
                } else if (ev.getKey() == SceneKey.ESCAPE
                        && (Boolean.TRUE.equals(candidatesOpen.get()) || Boolean.TRUE.equals(variantsOpen.get()))) {
                    close(candidatesOpen, variantsOpen);
                    ctx.stopPropagation();
                }
            });

            AnchorProvider anchor = AnchorProvider.forNode(input);
            rt.portalAnchored(candidatesOpen,
                    () -> candidatePortal(rt, props, activeCandidate, candidatesOpen, variantsOpen, highlighted),
                    OverlayDismissPolicy.DEFAULT, () -> close(candidatesOpen, variantsOpen), anchor);
            rt.portalAnchored(variantsOpen,
                    () -> variantPortal(rt, props, activeCandidate, candidatesOpen, variantsOpen, highlighted),
                    OverlayDismissPolicy.DEFAULT, () -> close(candidatesOpen, variantsOpen), anchor);
            return root;
        };
    }

    private static SceneNode candidatePortal(SceneRuntime rt, Props props,
                                              Signal<SearchPickerData.Candidate> activeCandidate,
                                              Signal<Boolean> candidatesOpen, Signal<Boolean> variantsOpen,
                                              Signal<Integer> highlighted) {
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
                        chooseCandidate(candidate, props, activeCandidate, candidatesOpen, variantsOpen, highlighted)));
        rt.show(footerContainer, Computed.create(() -> Boolean.valueOf(safeResults(props).truncated())),
                () -> text("Results truncated"));
        return list;
    }

    private static SceneNode variantPortal(SceneRuntime rt, Props props,
                                            Signal<SearchPickerData.Candidate> activeCandidate,
                                            Signal<Boolean> candidatesOpen, Signal<Boolean> variantsOpen,
                                            Signal<Integer> highlighted) {
        SceneNode list = portalRoot();
        SceneNode itemsContainer = SceneNode.column();
        itemsContainer.setWidthSizing(WidthSizing.SHRINK);
        list.appendChild(itemsContainer);
        ReadableSignal<List<SearchPickerData.Variant>> items = Computed.create(() -> {
            SearchPickerData.Candidate candidate = activeCandidate.get();
            return candidate == null ? Collections.<SearchPickerData.Variant>emptyList() : candidate.variants();
        });
        rt.forEach(itemsContainer, items, SearchPickerData.Variant::key, variant -> item(rt,
                props.visualAdapter.variantImage(variant), props.visualAdapter.variantLabel(variant), () -> {
                    SearchPickerData.Candidate candidate = activeCandidate.get();
                    if (candidate != null) props.onSelect.accept(new SearchPickerData.Selection(candidate.key(), variant.key()));
                    close(candidatesOpen, variantsOpen);
                }));
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
                                        Signal<Integer> highlighted) {
        if (candidate.variants().isEmpty()) {
            props.onSelect.accept(new SearchPickerData.Selection(candidate.key(), null));
            close(candidatesOpen, variantsOpen);
        } else {
            activeCandidate.set(candidate);
            highlighted.set(Integer.valueOf(0));
            candidatesOpen.set(Boolean.FALSE);
            variantsOpen.set(Boolean.TRUE);
        }
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
