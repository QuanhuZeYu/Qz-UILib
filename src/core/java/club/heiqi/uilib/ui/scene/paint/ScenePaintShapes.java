package club.heiqi.uilib.ui.scene.paint;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;

/** 只展开当前几何的纯装饰算法；颜色与布局策略仍归控件，宿主消费正式RECT。 */
public final class ScenePaintShapes {
    private ScenePaintShapes() {}

    public static void rounded(PaintPlanBuilder paint, LayoutBox box, LayoutBox clip, int color, int requested) {
        if (box.width <= 0 || box.height <= 0) return;
        int radius = Math.min(requested, Math.min(box.width, box.height) / 2);
        paint.add(PaintCommand.rect(new LayoutBox(box.x, box.y + radius, box.width, box.height - radius * 2), clip, color));
        for (int y = 0; y < radius; y++) {
            double dy = radius - y - 0.5;
            int inset = (int) Math.ceil(radius - Math.sqrt(radius * radius - dy * dy));
            paint.add(PaintCommand.rect(new LayoutBox(box.x + inset, box.y + y, box.width - inset * 2, 1), clip, color));
            paint.add(PaintCommand.rect(new LayoutBox(box.x + inset, box.y + box.height - y - 1, box.width - inset * 2, 1), clip, color));
        }
    }
}
