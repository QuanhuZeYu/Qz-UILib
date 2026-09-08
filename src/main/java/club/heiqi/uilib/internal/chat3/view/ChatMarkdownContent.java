package club.heiqi.uilib.internal.chat3.view;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.IntFunction;

import club.heiqi.uilib.internal.chat3.ChatMarkdownSettings;
import club.heiqi.uilib.ui.reactive.Computed;
import club.heiqi.uilib.ui.reactive.Signal;
import club.heiqi.uilib.ui.scene.control.SceneScrollbar;
import club.heiqi.uilib.ui.scene.control.SceneSliderPrimitive;
import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.layout.SceneGeometry;
import club.heiqi.uilib.ui.scene.node.SceneNode;
import club.heiqi.uilib.ui.scene.node.TextVerticalAlign;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.runtime.SceneRuntime;
import club.heiqi.uilib.ui.scene.runtime.SceneScrolls;

/** 显式表格消息的 occurrence 私有滚动视图；布局、裁剪、命中与输入仍由 scene 拥有。 */
final class ChatMarkdownContent {
    private ChatMarkdownContent() {}

    static final class Result {
        final SceneNode root;
        final SceneNode viewport;
        final SceneNode body;
        final Signal<Integer> scrollX;
        final Signal<Integer> scrollY;

        Result(SceneNode root, SceneNode viewport, SceneNode body,
                Signal<Integer> scrollX, Signal<Integer> scrollY) {
            this.root = root;
            this.viewport = viewport;
            this.body = body;
            this.scrollX = scrollX;
            this.scrollY = scrollY;
        }
    }

    static Result create(SceneRuntime rt, int heightBudget, boolean interactive,
            IntFunction<ChatMarkdownPipeline.RenderedContent> layouts, BiConsumer<SceneNode, PaintCommand> decorateLeaf) {
        int budget = Math.max(1, heightBudget);
        SceneNode root = SceneNode.column(0).setFillParentWidth(true).setHitTestable(false);
        SceneNode row = SceneNode.row(0).setFillParentWidth(true).setPreferredHeight(budget)
                .setHitTestable(false);
        SceneNode viewport = SceneNode.column(0).setFlexGrow(1).setFillParentHeight(true)
                .setScrollable(true).setScrollableX(true).setClipChildren(true).setHitTestable(interactive);
        SceneNode body = SceneNode.column(0).setHitTestable(false);
        root.appendChild(row);
        row.appendChild(viewport);
        viewport.appendChild(body);
        Signal<Integer> x = Signal.create(0);
        Signal<Integer> y = interactive ? SceneScrolls.attach(rt, viewport) : Signal.create(0);
        Signal<Integer> maxX = Signal.create(0);
        rt.bind(x, offset -> {
            if (viewport.getScrollOffsetX() == offset) return;
            viewport.setScrollOffsetX(offset);
            rt.__requestHoverReconcileAfterScroll();
        });
        if (interactive) {
            SceneScrollbar.Result vertical = SceneScrollbar.create(rt, new SceneScrollbar.Props(
                    viewport, y, y::set, 0, ChatMarkdownSettings.getScrollbarThumbArgb(), 6, 12));
            row.appendChild(vertical.column());
            rt.bind(rt.layoutDoneSignal(), done -> {
                boolean overflow = SceneGeometry.maxScrollY(viewport) > 0;
                vertical.column().setHitTestable(overflow);
                vertical.thumb().setHitTestable(overflow);
            });
            // 现有 scrollbar 仅支持纵向。横向复用受控 slider 的 capture/CANCEL/键盘输入，
            // 不给同一 viewport 再挂第二个 SCROLL handler，避免一次滚轮同时移动两个轴。
            Signal<Boolean> horizontalPressed = Signal.create(false);
            // resize 消除溢出时保留正在捕获的 primitive，等原生 UP/CANCEL 清理后才卸载。
            rt.show(root, Computed.create(() -> maxX.get() > 0 || horizontalPressed.get()), () -> {
            SceneSliderPrimitive.Result horizontal = SceneSliderPrimitive.create(rt,
                    new SceneSliderPrimitive.Props(
                            Computed.create(() -> maxX.get() == 0 ? 0.0 : (double) x.get() / maxX.get()),
                            Computed.create(() -> maxX.get() > 0 || horizontalPressed.get()), 0.0, 1.0, 0.0,
                            (value, committing) -> x.set((int) Math.round(value * maxX.get()))));
            rt.bind(horizontal.interaction().pressed(), horizontalPressed::set);
            rt.bind(maxX, max -> {
                horizontal.root().setOpacity(max > 0 ? 1.0F : 0.0F);
                horizontal.track().setHitTestable(max > 0);
            });
            horizontal.root().setPreferredHeight(8).setFillParentWidth(true);
            horizontal.track().setPreferredHeight(6).setBackgroundColor(ChatMarkdownSettings.getTextSecondaryArgb());
            horizontal.thumb().setPreferredWidth(12).setPreferredHeight(6)
                    .setBackgroundColor(ChatMarkdownSettings.getScrollbarThumbArgb());
            horizontal.fillBox().setPreferredHeight(6);
            rt.bindComputed(() -> {
                rt.layoutDoneSignal().get();
                LayoutBox track = (LayoutBox) horizontal.track().getCachedLayout();
                int trackWidth = track == null ? 1 : track.getWidth();
                return Math.max(1, (int) Math.round(Math.max(0, trackWidth - 12) * horizontal.progress().get()));
            }, horizontal.fillBox()::setPreferredWidth);
            return horizontal.root();
            });
        }
        Signal<List<ChatMarkdownPipeline.PaintLeaf>> leaves = Signal.create(Collections.emptyList());
        rt.forEach(body, leaves, leaf -> leaf, leaf -> {
            SceneNode node = node(leaf);
            if (interactive && decorateLeaf != null) decorateLeaf.accept(node, leaf.command);
            return node;
        });
        int[] width = {0};
        ChatMarkdownPipeline.RenderedContent[] published = {null};
        Runnable refresh = () -> {
            if (width[0] <= 0) return;
            ChatMarkdownPipeline.RenderedContent plan = layouts.apply(width[0]);
            if (published[0] == plan) return;
            published[0] = plan;
            body.setPreferredWidth(Math.max(1, plan.width)).setPreferredHeight(Math.max(1, plan.height));
            row.setPreferredHeight(Math.min(budget, Math.max(1, plan.height)));
            leaves.set(plan.leaves);
        };
        rt.bind(rt.layoutDoneSignal(), done -> {
            LayoutBox box = (LayoutBox) viewport.getCachedLayout();
            if (box == null) return;
            int available = Math.max(0, box.getWidth() - viewport.getPaddingLeft() - viewport.getPaddingRight());
            if (available != width[0]) {
                width[0] = available;
                refresh.run();
            }
            int horizontalMax = SceneGeometry.maxScrollX(viewport);
            maxX.set(horizontalMax);
            x.set(Math.min(x.get(), horizontalMax));
            y.set(Math.min(y.get(), SceneGeometry.maxScrollY(viewport)));
        });
        // 管线命中返回同一计划：帧订阅不解析、不度量、不重建叶子；字体/配色/epoch 改变即刷新。
        rt.bind(rt.__frameTimeNanos(), frame -> refresh.run());
        return new Result(root, viewport, body, x, y);
    }

    static SceneNode node(ChatMarkdownPipeline.PaintLeaf leaf) {
        PaintCommand command = leaf.command;
        SceneNode node = new SceneNode().setHitTestable(false)
                .setPreferredWidth(leaf.width).setPreferredHeight(leaf.height);
        switch (command.getType()) {
            case BACKGROUND:
                node.setBackgroundColor(command.getColor()).setCornerRadius(command.getCornerRadius());
                break;
            case SEGMENTS:
                node.setSegments(command.getSegments()).setFontSize(command.getTextStyle().getFontSize())
                        .setTextVerticalAlign(TextVerticalAlign.TOP);
                break;
            case LINK_REGION:
                // 精确矩形作为透明 scene 节点投放；输入裁剪归框架，不写任何命中缓存。
                break;
            default:
                throw new IllegalArgumentException("Unsupported chat content command: " + command.getType());
        }
        // COLUMN 相邻绘制原点差由 Pipeline 计算，负 margin 保留背景与文字重叠。
        node.setMargin(leaf.top, 0, -leaf.top - leaf.height, command.getLeft());
        return node;
    }
}
