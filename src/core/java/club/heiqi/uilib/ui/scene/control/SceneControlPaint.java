package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;

/** 控件共同的内容区域与细边框，直接输出既有PaintCommand。 */
final class SceneControlPaint {
    private SceneControlPaint() {}

    static LayoutBox inner(LayoutBox box, int padding) {
        return new LayoutBox(
                box.x + padding,
                box.y + padding,
                Math.max(0, box.width - padding * 2),
                Math.max(0, box.height - padding * 2));
    }

    static void border(PaintPlanBuilder target, LayoutBox box, LayoutBox clip, int color) {
        if (box.width == 0 || box.height == 0) return;
        target.add(PaintCommand.rect(new LayoutBox(box.x, box.y, box.width, 1), clip, color));
        target.add(
                PaintCommand.rect(
                        new LayoutBox(box.x, box.y + box.height - 1, box.width, 1), clip, color));
        target.add(PaintCommand.rect(new LayoutBox(box.x, box.y, 1, box.height), clip, color));
        target.add(
                PaintCommand.rect(
                        new LayoutBox(box.x + box.width - 1, box.y, 1, box.height), clip, color));
    }
}
