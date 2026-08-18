package club.heiqi.uilib.internal.devtools.playground.pages;

import java.util.function.Supplier;

import club.heiqi.uilib.internal.devtools.playground.PlaygroundKit;
import club.heiqi.uilib.internal.devtools.playground.PlaygroundPage;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneTextArea;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;

/**
 * 多行文本输入演示页（soft wrap / Undo / 跨行选区）。
 *
 * <p>覆盖：TextArea 多行输入（含 \n）、soft wrap（按视口可用宽软换行、↑/↓ 跨视觉行、
 * Home/End 视觉行级）、跨视觉行拖选/Shift 扩展/双击选词/三击选逻辑行、Ctrl+A 全选、
 * 剪贴板与 Undo/Redo、纵向滚动跟随。页面提供行数/码点统计与示例文本快捷操作。</p>
 */
public final class TextAreaPage implements PlaygroundPage {

    /** 受控值：正文（含 \n 换行）。 */
    private final Signal<String> body = Signal.create(SAMPLE_TEXT);

    /** 示例长文本：多行 + 超长行触发 soft wrap。 */
    private static final String SAMPLE_TEXT = 
            "第一行：欢迎使用 Qz UILib 测试场地。\n"
            + "第二行（超长行，用于观察 soft wrap）：这一行文本故意写得非常长，超过 TextArea 视口可用宽度后会按视觉行软换行，"
            + "此时 ↑/↓ 在视觉行之间移动、Home/End 落在视觉行首尾，跨视觉行的拖选高亮按视觉行块状绘制。\n"
            + "第三行：试试 Ctrl+Z 撤销刚才删掉的字、Ctrl+Y 或 Ctrl+Shift+Z 重做。\n"
            + "第四行：双击选词、三击选整个逻辑行、Shift+方向键扩展选区、Ctrl+A 全选、Ctrl+C/X/V 剪贴板。\n"
            + "第五行：光标移到最后一行的结尾，输入更多内容观察纵向跟随滚动。";

    @Override
    public String id() {
        return "text-area";
    }

    @Override
    public String title() {
        return "多行文本";
    }

    @Override
    public String description() {
        return "SceneTextArea：soft wrap 视觉行、跨行选区、Undo/Redo、纵向跟随";
    }

    @Override
    public Supplier<SceneNode> build(final SceneRuntime rt) {
        return () -> {
            SceneNode root = SceneNode.column();
            root.setFillParentWidth(true);
            root.setGap(10);

            SceneNode areaCard = PlaygroundKit.card();
            areaCard.appendChild(PlaygroundKit.title("多行输入（soft wrap + 跨行选区）"));
            SceneTextArea.Props props = SceneTextArea.Props.builder(body)
                    .placeholder("在此输入多行文本…")
                    .viewportHeight(260)
                    .onChange(next -> body.set(next == null ? "" : next))
                    .build();
            rt.mount(areaCard, SceneTextArea.create(rt, props));
            areaCard.appendChild(PlaygroundKit.hint(
                    "soft wrap：超宽行按视口可用宽软换行；↑/↓ 在视觉行间移动、Home/End 视觉行级、"
                    + "拖选/Shift 跨视觉行块状高亮、双击选词/三击选逻辑行。"));
            SceneNode stats = PlaygroundKit.text("", PlaygroundKit.MUTED, 12);
            areaCard.appendChild(stats);
            rt.bind(Computed.create(() -> formatStats(body.get())), stats::setText);

            SceneNode opsCard = PlaygroundKit.card();
            opsCard.appendChild(PlaygroundKit.title("快捷操作"));
            SceneNode opsRow = PlaygroundKit.row(8);
            PlaygroundKit.button(rt, opsRow, "填充示例", () -> body.set(SAMPLE_TEXT));
            PlaygroundKit.button(rt, opsRow, "清空", () -> body.set(""));
            PlaygroundKit.button(rt, opsRow, "换行计数", () -> body.set(addMarkerLine(body.get())));
            opsCard.appendChild(opsRow);
            opsCard.appendChild(PlaygroundKit.hint(
                    "Undo/Redo：Ctrl+Z 撤销 / Ctrl+Y 或 Ctrl+Shift+Z 重做（连续输入 500ms 内合并为一条历史，上限 100 条）。"));

            root.appendChild(areaCard);
            root.appendChild(opsCard);
            return root;
        };
    }

    /** 追加一行标记（演示把历史压栈：追加后 Ctrl+Z 可整行撤销）。 */
    private static String addMarkerLine(String current) {
        String safe = current == null ? "" : current;
        return safe.isEmpty() ? "第 1 行（演示标记）" : safe + "\n第 " + (safe.split("\n", -1).length + 1) + " 行（演示标记）";
    }

    private static String formatStats(String value) {
        String safe = value == null ? "" : value;
        int lines = safe.isEmpty() ? 0 : safe.split("\n", -1).length;
        int codepoints = safe.codePointCount(0, safe.length());
        return "逻辑行：" + lines + " 行　码点：" + codepoints + "　（视觉行数由 soft wrap 决定，随视口宽度变化）";
    }
}