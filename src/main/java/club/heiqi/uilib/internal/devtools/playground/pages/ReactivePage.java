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
import club.heiqi.uilib.ui.scene.theme.SceneThemes;

/**
 * 响应式底层能力演示页（Signal / Computed / show / forEach）。
 *
 * <p>覆盖 scene 数据层四个高频原语：{@code Signal} 受控源、{@code Computed} 记忆化派生、
 * {@code rt.show} 条件渲染（0/1 轻挂载）、{@code rt.forEach} keyed 列表协调（增删不动稳定项）。
 * 全部交互只经 signal 写、输出经 bind 派生，验证「UI 变化以 state/signal 驱动」的信条。</p>
 *
 * <p><b>外观归属（G16/ReactivePage）</b>：普通读数文字（计数读数、派生读数、标签项文本、
 * 列表统计）取<b>来源主题</b>正文/次要前景（{@link SceneThemes#foreground}/{@link
 * SceneThemes#mutedForeground} 经公共构件 {@code PlaygroundKit.text(rt, ...)} 绑定，构建期不读值，
 * 主题切换只重派生颜色、不重建节点）；节标题/说明复用已主题化的公共构件。
 * {@code rt.show} 的三枚<b>状态徽标</b>属契约 §7.3「状态徽标」不迁移清单，其正/负/零语义色
 * 由参数显式传入并刻意保留（见 {@link #badge(String, int)} 内注释），不为「匹配主题」刷色。</p>
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

            // 计数读数是普通正文：取来源主题正文前景（构建期捕获信号，主题切换只重派生颜色）。
            SceneNode countReadout = PlaygroundKit.text(rt, "", SceneThemes.foreground(rt), 14);
            counterCard.appendChild(countReadout);
            rt.bind(Computed.create(() -> "计数：" + count.get()), countReadout::setText);

            // 派生读数与统计是次要说明：取来源主题次要前景（同上，只改取色路径不改内容）。
            SceneNode derivedReadout = PlaygroundKit.text(rt, "", SceneThemes.mutedForeground(rt), 12);
            counterCard.appendChild(derivedReadout);
            rt.bind(Computed.create(() -> {
                int v = count.get().intValue();
                return "派生：平方 = " + (v * v) + "　奇偶 = " + (v % 2 == 0 ? "偶" : "奇");
            }), derivedReadout::setText);
            counterCard.appendChild(PlaygroundKit.hint("修改 count 时，所有读取它的 Computed 记忆化重算并同步写节点。"));

            // ===== 卡片2：rt.show 条件渲染 =====
            SceneNode showCard = PlaygroundKit.card();
            showCard.appendChild(PlaygroundKit.title("rt.show 条件渲染（按信号轻挂载/卸载内容）"));
            // 保留的显式样本（契约 §7.3「状态徽标」不迁移清单）：三枚徽标的底色是正/负/零计数
            // 的<b>状态语义色</b>，由参数显式传入（ACCENT=正向、DANGER=负向、MUTED=中性档），
            // 刻意不被主题接管——本卡演示的是 show 的挂载/卸载协调，徽标色是读数的语义编码，
            // 为「匹配主题」刷色会破坏诊断价值（任务单 G16 禁止项）。见 badge() 内注释。
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
                // 标签项文本是普通正文：在 forEach 项 builder 内取来源主题正文前景
                // （builder 于项 Owner 作用域执行，可安全解析主题；主题切换只重派生颜色，
                // 不与按 key 协调的增删行为冲突）。
                row.appendChild(PlaygroundKit.text(rt, tag.label(), SceneThemes.foreground(rt), 13));
                row.appendChild(PlaygroundKit.hint("（key=" + tag.id() + "）"));
                return row;
            });
            SceneNode listStats = PlaygroundKit.text(rt, "", SceneThemes.mutedForeground(rt), 12);
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
     * <p><b>保留的显式样本（契约 §7.3「状态徽标」不迁移清单 + 任务单 G16 禁止项）</b>：
     * 底色就是调用点传入的<b>状态语义色</b>（正计数=强调、负计数=危险、零计数=中性次要档），
     * 刻意不取 {@code SceneThemes.accent/danger} 也不随主题切换——主题 danger 档
     * （{@code SceneTheme#danger()} 的暗红）与本演示的警示红并不同值，改取主题会静默改变
     * 样本观感；徽标文字同样保持显式 {@code PlaygroundKit.TEXT}（近白），因为三档底色均为
     * 高饱和深色底，浅色主题正文色在其上不可读。本实例按口径「徽标语义色已由参数传入则沿用」
     * 执行，只做说明、不改写。</p>
     *
     * <p>{@code setCornerRadius(999)} 是<b>胶囊几何</b>（半径≥半高即全圆角），不是材质色板——
     * 契约 §4.2 允许尺寸/几何常量继续由页面自持；徽标不装玻璃、不参与表面绑定，
     * {@code setBackgroundColor(color)} 是本节点唯一且静态的底色写入者（语义色参数）。</p>
     *
     * @param label 文本
     * @param color 状态语义色（显式样本，不被主题接管）
     * @return 徽标节点
     */
    private static SceneNode badge(String label, int color) {
        SceneNode node = new SceneNode();
        node.setWidthSizing(SceneNode.WidthSizing.SHRINK);
        node.setPadding(8);
        // 999 = 胶囊全圆角几何（非材质半径，见上注释）。
        node.setCornerRadius(999);
        node.setHitTestable(false);
        // 底色 = 徽标状态语义色参数（保留显式，见上注释）。
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
