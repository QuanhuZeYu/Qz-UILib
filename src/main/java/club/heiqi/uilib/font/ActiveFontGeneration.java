package club.heiqi.uilib.font;

import java.util.concurrent.atomic.AtomicReference;

import club.heiqi.uilib.font.page.GlyphRuntimeTables;
import club.heiqi.uilib.font.util.DerivedFontCache;
import club.heiqi.uilib.font.util.FontCatalog;

/**
 * 当前字体代际的单一发布 envelope。
 *
 * <p>settings 与 catalog 是不可变快照；runtime tables 是仅由当前 lifecycle 使用的代内可变存储。
 * 普通换代会在 render barrier 内把同一份大型 table storage 转移给新 envelope。</p>
 */
public final class ActiveFontGeneration {

    /** generation 生命周期。 */
    public enum Lifecycle {
        ACTIVE,
        RETIRED
    }

    private final int runtimeVersion;
    private final int textMeasureEpoch;
    private final FontRuntimeSettings settings;
    private final FontCatalog.Snapshot catalogSnapshot;
    private final String[] publishedFontOrder;
    private final GlyphRuntimeTables runtimeTables;
    private final FontRuntimeMetrics metrics;
    private final DerivedFontCache derivedFontCache;
    private final AtomicReference<Lifecycle> lifecycle =
            new AtomicReference<Lifecycle>(Lifecycle.ACTIVE);

    ActiveFontGeneration(int runtimeVersion, int textMeasureEpoch, FontRuntimeSettings settings,
            FontCatalog.Snapshot catalogSnapshot, String[] publishedFontOrder, GlyphRuntimeTables runtimeTables,
            FontRuntimeMetrics metrics) {
        if (runtimeVersion <= 0) {
            throw new IllegalArgumentException("runtimeVersion 必须大于 0");
        }
        if (settings == null || catalogSnapshot == null || runtimeTables == null || metrics == null) {
            throw new IllegalArgumentException("generation 成员不得为 null");
        }
        this.runtimeVersion = runtimeVersion;
        this.textMeasureEpoch = textMeasureEpoch;
        this.settings = settings;
        this.catalogSnapshot = catalogSnapshot;
        this.publishedFontOrder = publishedFontOrder == null ? new String[0] : publishedFontOrder.clone();
        this.runtimeTables = runtimeTables;
        this.metrics = metrics;
        this.derivedFontCache = new DerivedFontCache(catalogSnapshot);
    }

    public int getRuntimeVersion() {
        return runtimeVersion;
    }

    public int getTextMeasureEpoch() {
        return textMeasureEpoch;
    }

    public FontRuntimeSettings getSettings() {
        return settings;
    }

    public FontCatalog.Snapshot getCatalogSnapshot() {
        return catalogSnapshot;
    }

    GlyphRuntimeTables getRuntimeTables() {
        return runtimeTables;
    }

    DerivedFontCache getDerivedFontCache() {
        return derivedFontCache;
    }

    public FontRuntimeMetrics getMetrics() {
        return metrics;
    }

    boolean matchesDesiredSettings(FontRuntimeSettings desiredSettings) {
        return settings.hasSameRuntimeSemantics(desiredSettings, publishedFontOrder);
    }

    public Lifecycle getLifecycle() {
        return lifecycle.get();
    }

    public boolean isActive() {
        return lifecycle.get() == Lifecycle.ACTIVE;
    }

    void retire() {
        lifecycle.compareAndSet(Lifecycle.ACTIVE, Lifecycle.RETIRED);
    }
}
