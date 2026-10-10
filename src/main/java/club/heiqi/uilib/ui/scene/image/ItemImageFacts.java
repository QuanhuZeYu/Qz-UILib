package club.heiqi.uilib.ui.scene.image;

import java.util.Objects;
import club.heiqi.uilib.ui.scene.text.TextContent;

/** 宿主准备的只读覆盖层/提示事实。格式转换在宿主完成，内容按当前主题派生，不持宿主palette或字号快照。 */
public final class ItemImageFacts {
    public final String quantity;
    public final TextContent tooltip;
    public final boolean durabilityVisible;
    public final double durabilityRemaining;
    public final int durabilityColor;
    public ItemImageFacts(String quantity, String tooltip, boolean durabilityVisible, double durabilityRemaining, int durabilityColor) {
        this(quantity,TextContent.plain(tooltip),durabilityVisible,durabilityRemaining,durabilityColor);
    }
    public ItemImageFacts(String quantity, TextContent tooltip, boolean durabilityVisible, double durabilityRemaining, int durabilityColor) {
        this.quantity = Objects.requireNonNull(quantity, "quantity"); this.tooltip = Objects.requireNonNull(tooltip, "tooltip");
        if (!Double.isFinite(durabilityRemaining) || durabilityRemaining < 0 || durabilityRemaining > 1) throw new IllegalArgumentException("无效耐久事实");
        this.durabilityVisible = durabilityVisible; this.durabilityRemaining = durabilityRemaining; this.durabilityColor = durabilityColor;
    }
}
