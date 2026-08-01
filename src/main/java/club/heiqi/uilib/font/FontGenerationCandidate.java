package club.heiqi.uilib.font;

import club.heiqi.uilib.font.util.FontRegistry;

/** CPU-only generation candidate；不持有 glyph tables 或 GL 资源。 */
final class FontGenerationCandidate {

    private final int runtimeVersion;
    private final int textMeasureEpoch;
    private final FontRuntimeSettings settings;
    private final FontRegistry.PreparedCatalog preparedCatalog;
    private final FontRuntimeMetrics metrics;

    FontGenerationCandidate(int runtimeVersion, int textMeasureEpoch, FontRuntimeSettings settings,
            FontRegistry.PreparedCatalog preparedCatalog, FontRuntimeMetrics metrics) {
        this.runtimeVersion = runtimeVersion;
        this.textMeasureEpoch = textMeasureEpoch;
        this.settings = settings;
        this.preparedCatalog = preparedCatalog;
        this.metrics = metrics;
    }

    static FontGenerationCandidate prepare(FontRegistry fontRegistry, int runtimeVersion, int textMeasureEpoch) {
        FontRuntimeSettings settings = FontRuntimeSettings.capture();
        FontRegistry.PreparedCatalog preparedCatalog = fontRegistry.prepare(settings);
        fontRegistry.validate(preparedCatalog);
        FontRuntimeMetrics metrics = FontRuntimeMetrics.prepare(settings, preparedCatalog.getCatalogSnapshot());
        return new FontGenerationCandidate(runtimeVersion, textMeasureEpoch, settings, preparedCatalog, metrics);
    }

    int getRuntimeVersion() {
        return runtimeVersion;
    }

    int getTextMeasureEpoch() {
        return textMeasureEpoch;
    }

    FontRuntimeSettings getSettings() {
        return settings;
    }

    FontRegistry.PreparedCatalog getPreparedCatalog() {
        return preparedCatalog;
    }

    FontRuntimeMetrics getMetrics() {
        return metrics;
    }
}

interface FontGenerationCandidateFactory {

    FontGenerationCandidate prepare(FontRegistry fontRegistry, int runtimeVersion, int textMeasureEpoch);
}

enum DefaultFontGenerationCandidateFactory implements FontGenerationCandidateFactory {
    INSTANCE;

    @Override
    public FontGenerationCandidate prepare(FontRegistry fontRegistry, int runtimeVersion, int textMeasureEpoch) {
        return FontGenerationCandidate.prepare(fontRegistry, runtimeVersion, textMeasureEpoch);
    }
}
