package club.heiqi.uilib.ui.container.experimental.presentation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Experimental 平台无关展示值；icon 可为 null，其余字段不可变。 */
public final class ItemPresentation<I> {
    private final I icon;
    private final String displayName;
    private final List<String> tooltipLines;
    /** 创建展示值并复制、冻结 tooltip。 */
    public ItemPresentation(I icon, String displayName, List<String> tooltipLines) {
        this.icon = icon;
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(tooltipLines, "tooltipLines");
        List<String> copy = new ArrayList<String>(tooltipLines.size());
        for (String line : tooltipLines) copy.add(Objects.requireNonNull(line, "tooltip line"));
        this.tooltipLines = Collections.unmodifiableList(copy);
    }
    /** 返回 icon，可为 null。 */ public I icon() { return icon; }
    /** 返回非空显示名称。 */ public String displayName() { return displayName; }
    /** 返回不可修改 tooltip。 */ public List<String> tooltipLines() { return tooltipLines; }
    /** 按 icon、displayName 与 tooltip 顺序比较。 */
    @Override public boolean equals(Object other) { return this == other || other instanceof ItemPresentation && Objects.equals(icon, ((ItemPresentation<?>) other).icon) && displayName.equals(((ItemPresentation<?>) other).displayName) && tooltipLines.equals(((ItemPresentation<?>) other).tooltipLines); }
    /** 返回与展示值语义一致的哈希。 */
    @Override public int hashCode() { return Objects.hash(icon, displayName, tooltipLines); }
    /** 返回展示值的安全文本表示。 */ @Override public String toString() { return "ItemPresentation{" + displayName + ", tooltipLines=" + tooltipLines + "}"; }
}
