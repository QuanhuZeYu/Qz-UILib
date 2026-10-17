package club.heiqi.uilib.testing.gallery;

import club.heiqi.uilib.ui.reactive.ReadableSignal;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneGrid;
import club.heiqi.uilib.ui.scene.control.SceneScrollArea;
import club.heiqi.uilib.ui.scene.control.SceneScrollArea.Axes;
import club.heiqi.uilib.ui.scene.control.SceneScrollArea.Options;
import club.heiqi.uilib.ui.scene.control.SceneTheme;
import club.heiqi.uilib.ui.scene.host.ScenePageContext;
import club.heiqi.uilib.ui.scene.layout.Size;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.runtime.NodeBinding;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** SimpleBar 交互参考页；内容与配置由信号声明，范围、裁剪和输入归正式滚动区。 */
public final class ScrollAreaPage {
    private ScrollAreaPage() {}

    public static SceneNode create(ScenePageContext context, ReadableSignal<SceneTheme> theme, Runnable leave) {
        SceneNode content = column().padding(28).gap(20);
        content.add(GalleryStyle.header(context,theme,"滚动区","滚动文字、浏览卡片，在嵌套与动态内容中试用滚动条。",leave));
        content.add(GalleryStyle.label(context, () -> "参考：SimpleBar / Examples", theme, 13, true));
        SceneNode examples = GalleryStyle.grid(context, theme);
        examples.add(vertical(context, theme));
        examples.add(horizontal(context, theme));
        examples.add(both(context, theme));
        examples.add(configurable(context, theme));
        examples.add(forceVisible(context, theme));
        examples.add(nested(context, theme));
        examples.add(dynamic(context, theme));
        examples.add(hidden(context, theme));
        content.add(examples);
        content.add(GalleryStyle.label(context, () -> "拖动滑块可移出轨道后释放；Tab 可以到达内容按钮，聚焦滚动条后可用方向键、PageUp / PageDown、Home / End。", theme, 14, true));
        return SceneScrollArea.create(context, content, () -> new Options(), theme).node().size(Size.fill(1), Size.fill(1));
    }

    private static SceneNode vertical(ScenePageContext context, ReadableSignal<SceneTheme> theme) {
        Signal<Integer> clicks = context.domain().signal(0);
        SceneNode card = section(context, theme, "默认纵向", "进入或滚动时显示，离开后淡出；按钮随正文一起滚动。");
        card.add(GalleryStyle.label(context, () -> "正文按钮点击：" + clicks.get(), theme, 13, true));
        card.add(viewport(context, theme, article(context, theme, clicks), () -> new Options(), 250));
        return GalleryStyle.frame(card, theme);
    }

    private static SceneNode horizontal(ScenePageContext context, ReadableSignal<SceneTheme> theme) {
        Signal<String> selected = context.domain().signal("还没有选择卡片");
        SceneNode card = section(context, theme, "横向卡片", "使用普通滚轮或拖动底部滑块，浏览横向排列的卡片。");
        SceneNode cards = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.natural(), Size.natural()).padding(16).gap(12);
        String[] names = {"山间晨光", "海边步道", "城市傍晚", "雨后森林", "夜空营地"};
        String[] descriptions = {"沿着山脊等待日出，把清晨留给慢慢前行。", "听潮水经过礁石，沿步道寻找下一处风景。", "在街角停下来，记录建筑与行人的片刻。", "穿过清新的林间小径，观察叶片上的雨滴。", "搭好帐篷，等待远处的灯光和头顶的星星。"};
        for (int i = 0; i < names.length; i++) {
            String name = names[i], description = descriptions[i];
            SceneNode item = column().size(Size.fixed(230), Size.natural()).padding(16).gap(12);
            item.add(GalleryStyle.label(context, () -> name, theme, 20, false));
            item.add(GalleryStyle.label(context, () -> description, theme, 15, true));
            item.add(GalleryStyle.button(context, () -> "选择这张卡片", theme, () -> selected.set("已选择：" + name)));
            cards.add(GalleryStyle.frame(item, theme).size(Size.natural(), Size.natural()));
        }
        card.add(GalleryStyle.label(context, selected, theme, 13, true));
        card.add(viewport(context, theme, cards, () -> new Options().axes(Axes.HORIZONTAL), 240));
        return GalleryStyle.frame(card, theme);
    }

    private static SceneNode both(ScenePageContext context, ReadableSignal<SceneTheme> theme) {
        Signal<String> selected = context.domain().signal("从左上角出发，滚动到右下角");
        SceneNode card = section(context, theme, "双轴与 padding", "画布保留内边距，滚动区自身也有外部留白；拖动两条滑块查看各个角落。");
        SceneNode canvas = new SceneNode().size(Size.natural(), Size.natural()).padding(24).gap(16);
        String[] rows = {"准备", "进行中", "已完成"};
        for (String row : rows) {
            SceneNode line = new SceneNode().axis(SceneNode.Axis.ROW).size(Size.natural(), Size.natural()).gap(16);
            for (int i = 1; i <= 4; i++) {
                String title = row + " · 任务 " + i;
                SceneNode task = column().size(Size.fixed(230), Size.natural()).padding(16).gap(12);
                task.add(GalleryStyle.label(context, () -> title, theme, 18, false));
                task.add(GalleryStyle.label(context, () -> "整理素材、确认细节，再与团队分享进展。", theme, 15, true));
                task.add(GalleryStyle.button(context, () -> "查看任务", theme, () -> selected.set("已打开：" + title)));
                line.add(GalleryStyle.frame(task, theme).size(Size.natural(), Size.natural()));
            }
            canvas.add(line);
        }
        card.add(GalleryStyle.label(context, selected, theme, 13, true));
        SceneNode area = SceneScrollArea.create(context, canvas, () -> new Options().axes(Axes.BOTH).autoHide(false), theme)
                .node().size(Size.fill(1), Size.fixed(280)).padding(8);
        card.add(GalleryStyle.frame(area, theme));
        return GalleryStyle.frame(card, theme);
    }

    private static SceneNode configurable(ScenePageContext context, ReadableSignal<SceneTheme> theme) {
        Signal<Boolean> autoHide = context.domain().signal(false);
        Signal<Boolean> trackClick = context.domain().signal(false);
        Signal<Boolean> accent = context.domain().signal(false);
        Signal<Integer> clicks = context.domain().signal(0);
        ReadableSignal<Options> options = context.domain().computed(context.scope(), () -> {
            Options value = new Options().autoHide(autoHide.get()).clickOnTrack(trackClick.get());
            return accent.get() ? value.colors(0x183b82f6, 0xff60a5fa, 0xff2563eb).dimensions(14, 32, 3).timing(900, 300) : value;
        });
        SceneNode card = section(context, theme, "常显与轨道点击", "初始保持滚动条可见，并关闭轨道翻页。切换选项后立即试用同一内容。");
        SceneNode actions = actions(context, theme);
        actions.add(GalleryStyle.button(context, () -> autoHide.get() ? "自动隐藏：开" : "自动隐藏：关", theme, () -> autoHide.set(!autoHide.get())));
        actions.add(GalleryStyle.button(context, () -> trackClick.get() ? "轨道翻页：开" : "轨道翻页：关", theme, () -> trackClick.set(!trackClick.get())));
        actions.add(GalleryStyle.button(context, () -> accent.get() ? "恢复主题样式" : "强调滚动条", theme, () -> accent.set(!accent.get())));
        card.add(actions);
        card.add(viewport(context, theme, article(context, theme, clicks), options, 230));
        card.add(GalleryStyle.label(context, () -> "正文按钮点击：" + clicks.get(), theme, 13, true));
        return GalleryStyle.frame(card, theme);
    }

    private static SceneNode forceVisible(ScenePageContext context, ReadableSignal<SceneTheme> theme) {
        Signal<Boolean> force = context.domain().signal(true);
        Signal<Integer> clicks = context.domain().signal(0);
        SceneNode card = section(context, theme, "无溢出时强制轨道", "内容很短，强制保留两个方向的轨道；没有可滚动范围时不出现滑块。");
        card.add(GalleryStyle.button(context, () -> force.get() ? "关闭强制轨道" : "显示强制轨道", theme, () -> force.set(!force.get())));
        SceneNode shortContent = column().padding(16).gap(12);
        shortContent.add(GalleryStyle.label(context, () -> "内容已全部显示。", theme, 15, false));
        shortContent.add(GalleryStyle.button(context, () -> "打个招呼 · " + clicks.get(), theme, () -> clicks.set(clicks.get() + 1)));
        card.add(viewport(context, theme, shortContent,
                () -> new Options().axes(Axes.BOTH).autoHide(false).forceVisible(force.get() ? Axes.BOTH : Axes.NONE), 210));
        return GalleryStyle.frame(card, theme);
    }

    private static SceneNode nested(ScenePageContext context, ReadableSignal<SceneTheme> theme) {
        Signal<Integer> clicks = context.domain().signal(0);
        SceneNode card = section(context, theme, "嵌套滚动区", "先在内层滚动；到达边界后，继续滚动会交给外层。外层末尾还有一个按钮。");
        SceneNode outer = column().padding(16).gap(16);
        outer.add(GalleryStyle.label(context, () -> "外层 · 项目笔记", theme, 18, false));
        outer.add(GalleryStyle.label(context, () -> "把鼠标移到下方内层文章，连续向下滚动。", theme, 15, true));
        outer.add(viewport(context, theme, article(context, theme, clicks), () -> new Options().autoHide(false), 170));
        outer.add(GalleryStyle.label(context, () -> "内层结束后，外层继续", theme, 18, false));
        outer.add(GalleryStyle.label(context, () -> "现在可以阅读这段后续内容。两层滚动位置独立，滚轮根据命中位置与剩余范围继续传递。", theme, 15, false));
        outer.add(GalleryStyle.label(context, () -> "继续向下到达外层末尾，再向上滚动，观察内外层的接续。", theme, 15, true));
        outer.add(GalleryStyle.button(context, () -> "外层末尾按钮 · " + clicks.get(), theme, () -> clicks.set(clicks.get() + 1)));
        card.add(viewport(context, theme, outer, () -> new Options().autoHide(false), 300));
        return GalleryStyle.frame(card, theme);
    }

    private static SceneNode dynamic(ScenePageContext context, ReadableSignal<SceneTheme> theme) {
        List<Integer> initial = Arrays.asList(1, 2, 3, 4, 5);
        Signal<List<Integer>> entries = context.domain().signal(initial);
        Signal<Integer> nextId = context.domain().signal(6);
        Signal<String> result = context.domain().signal("滚动到末尾，再删除条目，观察范围随内容收缩。");
        SceneNode card = section(context, theme, "动态增删内容", "保留现有条目，新增或移除真实内容；清空后仍可重新添加。");
        SceneNode actions = actions(context, theme);
        SceneNode add = GalleryStyle.button(context, () -> "添加一条", theme, () -> {
            List<Integer> next = new ArrayList<>(entries.get());
            next.add(nextId.get()); nextId.set(nextId.get() + 1); entries.set(Collections.unmodifiableList(next));
        });
        add.bind(NodeBinding.value(() -> entries.get().size() < 12, SceneNode::enabled));
        SceneNode remove = GalleryStyle.button(context, () -> "删除末条", theme, () -> {
            List<Integer> next = new ArrayList<>(entries.get()); next.remove(next.size() - 1);
            entries.set(Collections.unmodifiableList(next));
        });
        remove.bind(NodeBinding.value(() -> !entries.get().isEmpty(), SceneNode::enabled));
        actions.add(add).add(remove);
        actions.add(GalleryStyle.button(context, () -> "清空内容", theme, () -> entries.set(Collections.emptyList())));
        actions.add(GalleryStyle.button(context, () -> "重置内容", theme, () -> { entries.set(initial); nextId.set(6); }));
        card.add(actions);
        card.add(GalleryStyle.label(context, () -> "当前条目：" + entries.get().size() + " / 12", theme, 13, true));
        SceneNode items = column().padding(16).gap(12);
        items.add(GalleryStyle.label(context, () -> "内容为空，点击添加一条。", theme, 15, true)
                .bind(NodeBinding.value(() -> entries.get().isEmpty(), SceneNode::visible)));
        SceneNode list = column().gap(12).bind(NodeBinding.repeat(entries, value -> value, value -> {
            SceneNode item = column().padding(12).gap(8);
            item.add(GalleryStyle.label(context, () -> "笔记 " + value.get(), theme, 17, false));
            item.add(GalleryStyle.label(context, () -> "内容变化会更新可滚动范围，剩余条目继续使用原来的节点。", theme, 14, true));
            item.add(GalleryStyle.button(context, () -> "阅读笔记 " + value.get(), theme, () -> result.set("已阅读笔记 " + value.get())));
            return GalleryStyle.frame(item, theme);
        }));
        items.add(list);
        card.add(viewport(context, theme, items, () -> new Options().autoHide(false), 260));
        card.add(GalleryStyle.label(context, result, theme, 13, true));
        return GalleryStyle.frame(card, theme);
    }

    private static SceneNode hidden(ScenePageContext context, ReadableSignal<SceneTheme> theme) {
        Signal<Boolean> shown = context.domain().signal(false);
        Signal<Integer> clicks = context.domain().signal(0);
        SceneNode card = section(context, theme, "隐藏初始化", "滚动区从隐藏状态创建。显示后可直接滚动，再次隐藏与显示也保留同一份内容。");
        card.add(GalleryStyle.button(context, () -> shown.get() ? "隐藏滚动区" : "显示滚动区", theme, () -> shown.set(!shown.get())));
        card.add(GalleryStyle.label(context, () -> shown.get() ? "滚动区已显示 · 正文按钮点击：" + clicks.get() : "滚动区初始隐藏，点击上方按钮查看。", theme, 13, true));
        SceneNode area = viewport(context, theme, article(context, theme, clicks), () -> new Options(), 250);
        area.bind(NodeBinding.value(shown, SceneNode::visible));
        card.add(area);
        return GalleryStyle.frame(card, theme);
    }

    private static SceneNode article(ScenePageContext context, ReadableSignal<SceneTheme> theme, Signal<Integer> clicks) {
        SceneNode content = column().padding(16).gap(14);
        content.add(GalleryStyle.label(context, () -> "把日常留在笔记里", theme, 20, false));
        String[] paragraphs = {
                "早晨从一杯热茶开始。整理桌面、打开窗户，给今天留下清晰的起点。",
                "沿着熟悉的路散步，也会遇到新的细节：一片树叶、一面招牌，或朋友分享的近况。",
                "把想法写下来，不必急着完成每一句。文字自然排列，按钮和段落一起留在这份笔记中。",
                "午后留出一段专注的时间。将复杂的事情分成小步，完成后再回头看看走过的路。",
                "傍晚整理今天的记录，把值得记住的事留下，也为明天保留一点空白。"
        };
        for (int i = 0; i < paragraphs.length; i++) {
            String paragraph = paragraphs[i];
            content.add(GalleryStyle.label(context, () -> paragraph, theme, 15, false));
            if (i == 1) content.add(GalleryStyle.button(context, () -> "收藏这一段", theme, () -> clicks.set(clicks.get() + 1)));
        }
        content.add(GalleryStyle.button(context, () -> "读到末尾 · " + clicks.get(), theme, () -> clicks.set(clicks.get() + 1)));
        return content;
    }

    private static SceneNode section(ScenePageContext context, ReadableSignal<SceneTheme> theme, String title, String description) {
        SceneNode card = GalleryStyle.card(theme).gap(14);
        card.add(GalleryStyle.label(context, () -> title, theme, 21, false));
        card.add(GalleryStyle.label(context, () -> description, theme, 14, true));
        return card;
    }

    private static SceneNode viewport(ScenePageContext context, ReadableSignal<SceneTheme> theme, SceneNode content,
            ReadableSignal<Options> options, int height) {
        SceneNode area = SceneScrollArea.create(context, content, options, theme).node().size(Size.fill(1), Size.fixed(height));
        return GalleryStyle.frame(area, theme);
    }

    private static SceneNode actions(ScenePageContext context, ReadableSignal<SceneTheme> theme) {
        return SceneGrid.create(context, () -> new SceneGrid.Policy(
                Math.max(1, Math.round(theme.get().textStyle.logicalEm * 10)), 8, 8));
    }

    private static SceneNode column() {
        return new SceneNode().size(Size.fill(1), Size.natural());
    }
}
