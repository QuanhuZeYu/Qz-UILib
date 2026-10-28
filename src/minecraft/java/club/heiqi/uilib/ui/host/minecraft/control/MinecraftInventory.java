package club.heiqi.uilib.ui.host.minecraft.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import club.heiqi.uilib.host.client.image.MinecraftImageServices;
import club.heiqi.uilib.ui.reactive.Owner;
import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.scene.control.SceneAppearance;
import club.heiqi.uilib.ui.scene.control.SceneImage;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.control.SceneTooltip;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.image.ImageOptions;
import club.heiqi.uilib.ui.scene.image.ImageService;
import club.heiqi.uilib.ui.scene.image.ImageSource;
import club.heiqi.uilib.ui.scene.layout.SceneLayouts;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;

/**
 * 玩家主背包声明：固定槽位承接快照，点击发布业务值，库存裁决归调用方。
 * 槽号 0–8 为快捷栏，9–35 为背包；保持同物品的 image 来源，数量和文案更新便不重新加载图标。
 * 合同见 Minecraft 多版本宿主规格的“背包声明组件”。
 */
public final class MinecraftInventory<T> {
    public static final int COLUMNS = 9, SLOT_COUNT = 36;

    /** 缺席的槽号表达空槽；image 保存物品视觉，quantity 独立覆盖，value 原样交回业务。 */
    public static final class Slot<T> {
        public final int index, quantity;
        public final T value;
        public final ImageSource image;
        public final String name, detail, marker;

        public Slot(int index, T value, ImageSource image, int quantity, String name, String detail, String marker) {
            if (index < 0 || index >= SLOT_COUNT || quantity <= 0) throw new IllegalArgumentException("背包槽号或数量无效");
            this.index = index;
            this.value = Objects.requireNonNull(value, "业务值");
            this.image = Objects.requireNonNull(image, "物品来源");
            this.quantity = quantity;
            this.name = Objects.requireNonNull(name, "物品名称");
            this.detail = Objects.requireNonNull(detail, "物品说明");
            this.marker = Objects.requireNonNull(marker, "角标");
        }
    }

    /** 尺寸以主题 em 为单位；文案由调用方的可追踪语言来源提供。 */
    public static final class Options {
        public final float slotEm, iconEm, gapEm, hotbarGapEm;
        public final String hotbarTitle;
        public final IntFunction<String> emptySlotName;

        private Options(float slotEm, float iconEm, float gapEm, float hotbarGapEm,
                String hotbarTitle, IntFunction<String> emptySlotName) {
            if (!Float.isFinite(slotEm) || !Float.isFinite(iconEm) || !Float.isFinite(gapEm)
                    || !Float.isFinite(hotbarGapEm) || slotEm <= 0 || iconEm <= 0 || iconEm > slotEm
                    || gapEm < 0 || hotbarGapEm < 0) throw new IllegalArgumentException("背包尺寸无效");
            this.slotEm = slotEm;
            this.iconEm = iconEm;
            this.gapEm = gapEm;
            this.hotbarGapEm = hotbarGapEm;
            this.hotbarTitle = Objects.requireNonNull(hotbarTitle, "快捷栏标题");
            this.emptySlotName = Objects.requireNonNull(emptySlotName, "空槽名称");
        }

        public static Options defaults() {
            return new Options(3.5f, 2, 1f / 3, .75f, "", index -> Integer.toString(index + 1));
        }

        public Options labels(String hotbarTitle, IntFunction<String> emptySlotName) {
            return new Options(slotEm, iconEm, gapEm, hotbarGapEm, hotbarTitle, emptySlotName);
        }

        public Options sizing(float slotEm, float iconEm, float gapEm, float hotbarGapEm) {
            return new Options(slotEm, iconEm, gapEm, hotbarGapEm, hotbarTitle, emptySlotName);
        }
    }

    private final SceneNode node;
    private final ReadableSignal<Integer> width;

    private MinecraftInventory(SceneNode node, ReadableSignal<Integer> width) {
        this.node = node;
        this.width = width;
    }

    public SceneNode node() { return node; }
    public ReadableSignal<Integer> width() { return width; }

    public static <T> MinecraftInventory<T> create(ScenePageContext page, ReadableSignal<List<Slot<T>>> slots,
            ReadableSignal<Options> options, ReadableSignal<Boolean> canActivate, Consumer<T> activate) {
        return create(page, MinecraftImageServices.shared().images(), slots, options, page.theme(), canActivate, activate);
    }

    /** 显式服务与主题沿同一组件路径，供独立预览和宿主自有资源组合使用。 */
    public static <T> MinecraftInventory<T> create(ScenePageContext page, ImageService images,
            ReadableSignal<List<Slot<T>>> slots, ReadableSignal<Options> options, ReadableSignal<SceneTheme> theme,
            ReadableSignal<Boolean> canActivate, Consumer<T> activate) {
        Objects.requireNonNull(page, "页面").domain().checkWrite();
        Objects.requireNonNull(images, "图片服务");
        Objects.requireNonNull(slots, "背包快照");
        Objects.requireNonNull(options, "背包选项");
        Objects.requireNonNull(theme, "主题");
        Objects.requireNonNull(canActivate, "操作资格");
        Objects.requireNonNull(activate, "操作意图");
        Owner owner = page.scope().child();
        SceneAppearance style = new SceneAppearance(page, theme);
        ReadableSignal<List<Slot<T>>> indexed = page.domain().computed(owner, () -> index(slots.get()));
        ReadableSignal<Integer> side = page.domain().computed(owner, () -> Math.max(1, pixels(theme, options.get().slotEm)));
        ReadableSignal<Integer> gap = page.domain().computed(owner, () -> pixels(theme, options.get().gapEm));
        ReadableSignal<Integer> width = page.domain().computed(owner, () -> side.get() * COLUMNS + gap.get() * (COLUMNS - 1));
        SceneNode root = SceneAppearance.column(0).size(Size.natural(), Size.natural()).own(owner).own(style.scope)
                .bind(NodeBinding.value(gap, SceneNode::gap));
        for (int start = COLUMNS; start < SLOT_COUNT; start += COLUMNS) {
            root.add(row(page, images, indexed, options, theme, style, owner, side, gap, canActivate, activate, start));
        }
        SceneNode hotbar = SceneAppearance.column(0).size(Size.natural(), Size.natural())
                .bind(NodeBinding.value(() -> pixels(theme, options.get().hotbarGapEm), SceneNode::gap));
        SceneNode title = style.label(() -> options.get().hotbarTitle, 1, false)
                .bind(NodeBinding.value(() -> !options.get().hotbarTitle.isEmpty(), SceneNode::visible));
        hotbar.add(title).add(row(page, images, indexed, options, theme, style, owner, side, gap, canActivate, activate, 0));
        root.add(hotbar);
        return new MinecraftInventory<>(root, width);
    }

    private static <T> List<Slot<T>> index(List<Slot<T>> slots) {
        List<Slot<T>> indexed = new ArrayList<>(Collections.nCopies(SLOT_COUNT, null));
        for (Slot<T> slot : slots) {
            if (indexed.set(slot.index, slot) != null) throw new IllegalArgumentException("背包槽号重复：" + slot.index);
        }
        return indexed;
    }

    private static int pixels(ReadableSignal<SceneTheme> theme, float em) {
        return Math.max(0, Math.round(theme.get().textStyle.logicalEm * em));
    }

    private static <T> SceneNode row(ScenePageContext page, ImageService images, ReadableSignal<List<Slot<T>>> slots,
            ReadableSignal<Options> options, ReadableSignal<SceneTheme> theme, SceneAppearance style, Owner owner,
            ReadableSignal<Integer> side, ReadableSignal<Integer> gap, ReadableSignal<Boolean> canActivate,
            Consumer<T> activate, int start) {
        SceneNode row = SceneAppearance.row(0).size(Size.natural(), Size.natural())
                .bind(NodeBinding.value(gap, SceneNode::gap));
        for (int i = start; i < start + COLUMNS; i++) {
            final int index = i;
            ReadableSignal<Slot<T>> item = page.domain().computed(owner, () -> slots.get().get(index));
            row.add(slot(page, images, item, options, theme, style, side, canActivate, activate, index));
        }
        return row;
    }

    private static <T> SceneNode slot(ScenePageContext page, ImageService images, ReadableSignal<Slot<T>> item,
            ReadableSignal<Options> options, ReadableSignal<SceneTheme> theme, SceneAppearance style,
            ReadableSignal<Integer> side, ReadableSignal<Boolean> canActivate, Consumer<T> activate, int index) {
        ReadableSignal<String> name = () -> item.get() == null ? options.get().emptySlotName.apply(index) : item.get().name;
        SceneNode content = SceneLayouts.overlay(), icon = SceneLayouts.overlay();
        content.add(icon);
        // 只有视觉来源替换才替换图片子树；数量、提示和忙碌均不参与该键。
        icon.bind(NodeBinding.repeat(() -> item.get() == null ? Collections.emptyList() : Collections.singletonList(item.get().image),
                image -> image, source -> SceneLayouts.center(SceneImage.create(page, images, source,
                        () -> {
                            int size = Math.max(1, pixels(theme, options.get().iconEm));
                            return ImageOptions.defaults().size(Size.fixed(size), Size.fixed(size)).alt(name.get());
                        }, theme))));
        SceneNode badges = SceneAppearance.column(0).size(Size.fill(1), Size.fill(1))
                .bind(NodeBinding.value(() -> Math.max(1, pixels(theme, .25f)), SceneNode::padding));
        badges.add(style.label(() -> item.get() == null ? "" : item.get().marker, .8f, true)
                .size(Size.natural(), Size.natural()));
        badges.add(new SceneNode().size(Size.fill(1), Size.fill(1)));
        SceneNode quantity = SceneAppearance.row(0);
        quantity.add(new SceneNode().size(Size.fill(1), Size.natural()));
        quantity.add(style.label(() -> item.get() == null ? "" : Integer.toString(item.get().quantity), .9f, false)
                .size(Size.natural(), Size.natural()));
        badges.add(quantity); content.add(badges);
        // 临时忙碌仅拒绝执行，保留已占用槽的焦点与视觉；空槽结束交互资格。
        SceneNode action = style.action(name, content, () -> {
            Slot<T> current = item.get();
            if (current != null && canActivate.get()) activate.accept(current.value);
        }, SceneAppearance.Role.OUTLINE, () -> false).padding(0)
                .bind(NodeBinding.value(side, (node, size) -> node.size(Size.fixed(Math.max(1, size)), Size.fixed(Math.max(1, size)))))
                .bind(NodeBinding.value(() -> item.get() != null, SceneNode::enabled));
        SceneTooltip.attach(page, action, () -> {
            Slot<T> current = item.get();
            return SceneTooltip.Content.text(current == null || current.detail.isEmpty() ? name.get() : name.get() + "\n" + current.detail);
        });
        return action;
    }
}
