package club.heiqi.uilib.ui.hud.api;

import java.util.Objects;

/**
 * 单个 HUD 的相对持久记录（包内实现，不是公共 API）：四角锚点 + 该轴行程百分比 + 缩放百分比。
 *
 * <p><b>只存相对量</b>：{@code fractionX/fractionY ∈ [0,1]} 是「偏移 / 该轴行程」的比值
 * （行程 = 可用空间 − 内容物理盒，口径见 {@link HudLayoutResolver#travelSpan}），
 * 视口或内容尺寸变化后按新行程重算偏移即可还原，绝不落绝对坐标。</p>
 *
 * <p>{@code anchor == null} = 「仅缩放记录」：用户改过缩放但没有位置覆盖（位置仍由默认布局决定），
 * 因此同一条记录也能把缩放持久化而不制造位置覆盖。</p>
 */
final class HudLayoutPreference {

    private final String hudId;
    private final HudAnchor anchor;
    private final double fractionX;
    private final double fractionY;
    private final int scalePercent;

    private HudLayoutPreference(String hudId, HudAnchor anchor, double fractionX, double fractionY,
            int scalePercent) {
        this.hudId = requireId(hudId);
        this.anchor = anchor;
        this.fractionX = requireFraction(fractionX, "fractionX");
        this.fractionY = requireFraction(fractionY, "fractionY");
        this.scalePercent = clampPercent(scalePercent);
    }

    /**
     * 位置 + 缩放记录。
     *
     * @param hudId       目标 HUD id（非空白、不含制表符/换行）
     * @param anchor      四角锚点（不可为 null）
     * @param fractionX   X 轴行程百分比（非有限值抛异常；越界钳到 [0,1]）
     * @param fractionY   Y 轴行程百分比（同上）
     * @param scalePercent 缩放百分比（越界钳到 50–200）
     * @return 相对记录
     */
    static HudLayoutPreference of(String hudId, HudAnchor anchor, double fractionX, double fractionY,
            int scalePercent) {
        return new HudLayoutPreference(hudId, Objects.requireNonNull(anchor, "anchor"), fractionX, fractionY,
                scalePercent);
    }

    /**
     * 仅缩放记录（无位置覆盖）。
     *
     * @param hudId        目标 HUD id
     * @param scalePercent 缩放百分比（越界钳到 50–200）
     * @return 相对记录
     */
    static HudLayoutPreference scaleOnly(String hudId, int scalePercent) {
        return new HudLayoutPreference(hudId, null, 0.0, 0.0, scalePercent);
    }

    /** 覆盖缩放百分比（读当前统一缩放状态时用）。 */
    HudLayoutPreference withScalePercent(int value) {
        return new HudLayoutPreference(hudId, anchor, fractionX, fractionY, value);
    }

    /** @return 是否含位置覆盖（false = 仅缩放记录） */
    boolean hasPlacement() {
        return anchor != null;
    }

    String getHudId() { return hudId; }
    HudAnchor getAnchor() { return anchor; }
    double getFractionX() { return fractionX; }
    double getFractionY() { return fractionY; }
    int getScalePercent() { return scalePercent; }

    private static String requireId(String hudId) {
        if (hudId == null || hudId.trim().isEmpty()) {
            throw new IllegalArgumentException("hudId must not be blank");
        }
        if (hudId.indexOf('\t') >= 0 || hudId.indexOf('\n') >= 0 || hudId.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("hudId must not contain control separators");
        }
        return hudId;
    }

    private static double requireFraction(double value, String name) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        if (value < 0.0) {
            return 0.0;
        }
        return value > 1.0 ? 1.0 : value;
    }

    private static int clampPercent(int value) {
        return Math.max(HudScaleState.MIN_PERCENT, Math.min(HudScaleState.MAX_PERCENT, value));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof HudLayoutPreference)) {
            return false;
        }
        HudLayoutPreference that = (HudLayoutPreference) other;
        return Double.compare(fractionX, that.fractionX) == 0
                && Double.compare(fractionY, that.fractionY) == 0
                && scalePercent == that.scalePercent
                && anchor == that.anchor
                && hudId.equals(that.hudId);
    }

    @Override
    public int hashCode() {
        int result = hudId.hashCode();
        result = result * 31 + (anchor == null ? 0 : anchor.hashCode());
        result = result * 31 + Double.valueOf(fractionX).hashCode();
        result = result * 31 + Double.valueOf(fractionY).hashCode();
        return result * 31 + scalePercent;
    }

    @Override
    public String toString() {
        return "HudLayoutPreference{" + hudId + ", " + (anchor == null ? "scale-only" : anchor)
                + ", fx=" + fractionX + ", fy=" + fractionY + ", scale=" + scalePercent + "%}";
    }
}
