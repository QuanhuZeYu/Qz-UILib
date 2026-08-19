package club.heiqi.uilib.client.hud;

import club.heiqi.uilib.ui.hud.api.HudAnchor;
import club.heiqi.uilib.ui.hud.api.HudInsets;
import club.heiqi.uilib.ui.hud.api.HudSpec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;

/** 四角语义锚定、稳定堆叠与视口 clamp 的纯数学布局器。 */
final class HudLayoutEngine {
    /** 根据预先测得的内容尺寸布局一帧 HUD。 */
    List<PlacedHud> layout(List<MeasuredHud> measured, int width, int height, HudInsets safeInsets) {
        width = Math.max(1, width); height = Math.max(1, height);
        final HudInsets safe = safeInsets == null ? HudInsets.NONE : safeInsets;
        ArrayList<MeasuredHud> sorted = new ArrayList<MeasuredHud>(measured);
        sorted.sort(Comparator.comparing((MeasuredHud item) -> item.entry.spec.getAnchor())
                .thenComparingInt(item -> item.entry.spec.getStackOrder())
                .thenComparingLong(item -> item.entry.registrationOrder));
        EnumMap<HudAnchor, Integer> offsets = new EnumMap<HudAnchor, Integer>(HudAnchor.class);
        ArrayList<PlacedHud> result = new ArrayList<PlacedHud>();
        for (MeasuredHud item : sorted) {
            HudSpec spec = item.entry.spec;
            HudAnchor anchor = spec.getAnchor();
            int offset = offsets.containsKey(anchor) ? offsets.get(anchor) : 0;
            int margin = spec.getMargin();
            int boxWidth = Math.min(item.width, Math.max(1, width - safe.getLeft() - safe.getRight() - margin * 2));
            int boxHeight = Math.min(item.height, Math.max(1, height - safe.getTop() - safe.getBottom() - margin * 2));
            boolean right = anchor == HudAnchor.TOP_RIGHT || anchor == HudAnchor.BOTTOM_RIGHT;
            boolean bottom = anchor == HudAnchor.BOTTOM_LEFT || anchor == HudAnchor.BOTTOM_RIGHT;
            int x = right ? width - safe.getRight() - margin - boxWidth : safe.getLeft() + margin;
            int y = bottom ? height - safe.getBottom() - margin - offset - boxHeight : safe.getTop() + margin + offset;
            x = clamp(x, 0, Math.max(0, width - boxWidth));
            y = clamp(y, 0, Math.max(0, height - boxHeight));
            result.add(new PlacedHud(item.entry, x, y, boxWidth, boxHeight));
            offsets.put(anchor, offset + boxHeight + HudTokens.STACK_GAP);
        }
        return Collections.unmodifiableList(result);
    }

    static int lineHeight(HudSpec spec) { return HudTokens.NORMAL.lineHeight; }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    static final class MeasuredHud {
        final HudRegistry.Entry entry; final int width; final int height;
        MeasuredHud(HudRegistry.Entry entry, int width, int height) {
            this.entry = entry; this.width = Math.max(1, width); this.height = Math.max(1, height);
        }
    }
    static final class PlacedHud {
        final HudRegistry.Entry entry; final int x; final int y; final int width; final int height;
        PlacedHud(HudRegistry.Entry entry, int x, int y, int width, int height) {
            this.entry = entry; this.x = x; this.y = y; this.width = width; this.height = height;
        }
    }
}
