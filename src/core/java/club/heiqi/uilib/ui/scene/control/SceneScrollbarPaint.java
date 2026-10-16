package club.heiqi.uilib.ui.scene.control;

import club.heiqi.uilib.ui.scene.layout.LayoutBox;
import club.heiqi.uilib.ui.scene.paint.PaintCommand;
import club.heiqi.uilib.ui.scene.paint.PaintPlanBuilder;

/** 滚动条只发布既有PaintCommand，所有宿主共用当前主题和同一几何。 */
final class SceneScrollbarPaint {
    private SceneScrollbarPaint() {}
    static void paint(SceneScrollbarGeometry geometry, SceneScrollbar.Options options, SceneTheme theme,
            boolean active, float opacity, PaintPlanBuilder target) {
        if (!geometry.visible()) return;
        target.add(PaintCommand.rect(geometry.track, geometry.clip, options.trackColor == null ? 0 : options.trackColor));
        if (!geometry.scrollable()) return;
        int color = color(options, theme, active);
        int alpha = Math.round((color >>> 24) * (active ? 1f : opacity));
        rounded(target, geometry.thumb, geometry.clip, (color & 0x00ffffff) | alpha << 24);
    }
    private static int color(SceneScrollbar.Options options, SceneTheme theme, boolean active) {
        if (active) return options.activeColor == null ? theme.focus : options.activeColor;
        return options.thumbColor == null ? (theme.textStyle.color & 0x00ffffff) | 0x80000000 : options.thumbColor;
    }
    private static void rounded(PaintPlanBuilder target, LayoutBox box, LayoutBox clip, int color) {
        if ((color >>> 24) == 0 || box.width == 0 || box.height == 0) return;
        int radius = Math.min(3, Math.min(box.width, box.height) / 2);
        target.add(PaintCommand.rect(new LayoutBox(box.x, box.y + radius, box.width, box.height - radius * 2), clip, color));
        for (int y = 0; y < radius; y++) {
            double dy = radius - y - .5;
            int inset = (int) Math.ceil(radius - Math.sqrt(radius * radius - dy * dy));
            target.add(PaintCommand.rect(new LayoutBox(box.x + inset, box.y + y, box.width - inset * 2, 1), clip, color));
            target.add(PaintCommand.rect(new LayoutBox(box.x + inset, box.y + box.height - y - 1, box.width - inset * 2, 1), clip, color));
        }
    }
}
