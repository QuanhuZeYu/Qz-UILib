package club.heiqi.uilib.internal.devtools.playground.pages;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import club.heiqi.uilib.internal.devtools.playground.PlaygroundKit;
import club.heiqi.uilib.internal.devtools.playground.PlaygroundPage;
import club.heiqi.uilib.ui.reactive.Computed;
import com.github.bsideup.jabel.Desugar;

import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 响应式底层能力演示页（Signal / Computed / show / forEach）。
 *
 * <p>覆盖 scene 数据层四个高频原语：{@code Signal} 受控源、{@code Computed} 记忆化派生、
 * {@code rt.show} 条件渲染（0/1 轻挂载）、{@code rt.forEach} keyed 列表协调（增删不动稳定项）。
 * 全部交互只经 signal 写、输出经 bind 派生，验证「UI 变化以 state/signal 驱动」的信条。</p>
 */
public final class ReactivePage implements PlaygroundPage {

    /** 计数器（受控源）。 */
    private final Signal<Integer> count = Signal.create(Integer.valueOf(0));
    /** 标签列表（keyed 列表演示）。 */
    private final Signal<List<Tag>> tags = Signal.create(new ArrayList<Tag>());
    /** 标签序号（生成唯一 key）。 */
    private final AtomicInteger tagCounter = new AtomicInteger();

    @Override
    public String id() {
        return "reactive";
    }

    @Override
    public String title() {
        return "响应式";
    }

    @Override
    public String description() {
        return "Signal/Computed/rt.show/rt.forEach：数据驱动 UI 的底层原语演示";
    }

    @Override
    public Supplier<SceneNode> build(final SceneRuntime rt) {
        return () -> {
            SceneNode root = SceneNode.column();
            root.setFillParentWidth(true);
            root.setGap(10);

            // ===== 卡片1：Signal + Computed 派生 =====
            SceneNode counterCard = PlaygroundKit.card();
            counterCard.appendChild(PlaygroundKit.title("Signal → Computed → bind（派生同步）"));
            SceneNode opsRow = PlaygroundKit.row(8);
            PlaygroundKit.button(rt, opsRow, "−1", () -> count.set(Integer.valueOf(count.get().intValue() - 1)));
            PlaygroundKit.button(rt, opsRow, "+1", () -> count.set(Integer.valueOf(count.get().intValue() + 1)));
            PlaygroundKit.button(rt, opsRow, "重置", () -> count.set(Integer.valueOf(0)));
            counterCard.appendChild(opsRow);

            SceneNode countReadout = PlaygroundKit.text("", PlaygroundKit.TEXT, 14);
            counterCard.appendChild(countReadout);
            rt.bind(Computed.create(() -> "计数：" + count.get()), countReadout::setText);

            SceneNode derivedReadout = PlaygroundKit.text("", PlaygroundKit.MUTED, 12);
            counterCard.appendChild(derivedReadout);
            rt.bind(Computed.create(() -> {
                int v = count.get().intValue();
                return "派生：平方 = " + (v * v) + "　奇偶 = " + (v % 2 == 0 ? "偶" : "奇");
            }), derivedReadout::setText);
            counterCard.appendChild(PlaygroundKit.hint("修改 count 时，所有读取它的 Computed 记忆化重算并同步写节点。"));

            // ===== 卡片2：rt.show 条件渲染 =====
            SceneNode showCard = PlaygroundKit.card();
            showCard.appendChild(PlaygroundKit.title("rt.show 条件渲染（按信号轻挂载/卸载内容）"));
            rt.show(showCard, Computed.create(() -> Boolean.valueOf(count.get().intValue() > 0)),
                    () -> badge("当前为正计数", PlaygroundKit.ACCENT));
            rt.show(showCard, Computed.create(() -> Boolean.valueOf(count.get().intValue() < 0)),
                    () -> badge("当前为负计数", PlaygroundKit.DANGER));
            rt.show(showCard, Computed.create(() -> Boolean.valueOf(count.get().intValue() == 0)),
                    () -> badge("计数为零", PlaygroundKit.MUTED));
            showCard.appendChild(PlaygroundKit.hint("三个 show 各自订阅派生布尔；true 时挂载、false 时卸载（零尺寸 anchor 占位保序）。"));

            // ===== 卡片3：rt.forEach keyed 列表 =====
            SceneNode listCard = PlaygroundKit.card();
            listCard.appendChild(PlaygroundKit.title("rt.forEach keyed 列表（增删按 key 协调）"));
            SceneNode listOps = PlaygroundKit.row(8);
            PlaygroundKit.button(rt, listOps, "新增标签", this::addTag);
            PlaygroundKit.button(rt, listOps, "移除末尾", this::removeLastTag);
            listCard.appendChild(listOps);
            SceneNode listContainer = SceneNode.column();
            listContainer.setFillParentWidth(true);
            listContainer.setGap(4);
            listCard.appendChild(listContainer);
            rt.forEach(listContainer, tags, tag -> tag.id(), tag -> {
                SceneNode row = SceneNode.row(8);
                row.setHitTestable(false);
                row.appendChild(PlaygroundKit.text(tag.label(), PlaygroundKit.TEXT, 13));
                row.appendChild(PlaygroundKit.hint("（key=" + tag.id() + "）"));
                return row;
            });
            SceneNode listStats = PlaygroundKit.text("", PlaygroundKit.MUTED, 12);
            listCard.appendChild(listStats);
            rt.bind(Computed.create(() -> "标签数：" + tags.get().size() + "　（增删只重协调受影响项，稳定项零重建）"), listStats::setText);

            root.appendChild(counterCard);
            root.appendChild(showCard);
            root.appendChild(listCard);
            return root;
        };
    }

    /**
     * 徽标节点（条件渲染内容）。
     *
     * @param label 文本
     * @param color 强调色
     * @return 徽标节点
     */
    private static SceneNode badge(String label, int color) {
        SceneNode node = new SceneNode();
        node.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        node.setPadding(8);
        node.setCornerRadius(999);
        node.setHitTestable(false);
        node.setBackgroundColor(color);
        SceneNode text = PlaygroundKit.text(label, PlaygroundKit.TEXT, 13);
        node.appendChild(text);
        return node;
    }

    /** 新增一个标签（唯一 id 作为 forEach key）。 */
    private void addTag() {
        int n = tagCounter.incrementAndGet();
        List<Tag> next = new ArrayList<Tag>(tags.get());
        next.add(new Tag("t-" + n, "标签#" + n));
        tags.set(next);
    }

    /** 移除末尾标签。 */
    private void removeLastTag() {
        List<Tag> current = tags.get();
        if (current.isEmpty()) {
            return;
        }
        List<Tag> next = new ArrayList<Tag>(current);
        next.remove(next.size() - 1);
        tags.set(next);
    }

    /**
     * 列表项（身份语义：不重写 equals/hashCode，id 供 forEach keyFn 使用）。
     *
     * @param id    唯一 key
     * @param label 显示文本
     */
    @Desugar
    public record Tag(String id, String label) {
    }
}
