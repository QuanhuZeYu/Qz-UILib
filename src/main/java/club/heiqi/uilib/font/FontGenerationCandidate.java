package club.heiqi.uilib.font;

/** CPU-only generation candidate；不持有 glyph tables 或 GL 资源。 */
final class FontGenerationCandidate {

    private final long desiredSequence;
    private final int baseRuntimeVersion;
    private final int baseTextMeasureEpoch;
    private final int runtimeVersion;
    private final int textMeasureEpoch;
    private final FontRuntimeSettings settings;
    private final FontGenerationRegistry.PreparedCatalog preparedCatalog;
    private final FontRuntimeMetrics metrics;
    private final FontResourceFingerprint resourceFingerprint;

    FontGenerationCandidate(int runtimeVersion, int textMeasureEpoch, FontRuntimeSettings settings,
            FontGenerationRegistry.PreparedCatalog preparedCatalog, FontRuntimeMetrics metrics) {
        this(0L, runtimeVersion - 1, textMeasureEpoch - 1, runtimeVersion, textMeasureEpoch, settings,
                preparedCatalog, metrics, FontResourceFingerprint.unspecified());
    }

    private FontGenerationCandidate(long desiredSequence, int baseRuntimeVersion, int baseTextMeasureEpoch,
            int runtimeVersion, int textMeasureEpoch, FontRuntimeSettings settings,
            FontGenerationRegistry.PreparedCatalog preparedCatalog, FontRuntimeMetrics metrics,
            FontResourceFingerprint resourceFingerprint) {
        this.desiredSequence = desiredSequence;
        this.baseRuntimeVersion = baseRuntimeVersion;
        this.baseTextMeasureEpoch = baseTextMeasureEpoch;
        this.runtimeVersion = runtimeVersion;
        this.textMeasureEpoch = textMeasureEpoch;
        this.settings = settings;
        this.preparedCatalog = preparedCatalog;
        this.metrics = metrics;
        this.resourceFingerprint = resourceFingerprint;
    }

    static FontGenerationCandidate prepare(FontGenerationRegistry fontRegistry,
            FontGenerationBuildRequest request) {
        FontResourceSnapshot resources = FontResourceSnapshot.capture(request);
        FontGenerationRegistry.PreparedCatalog preparedCatalog = fontRegistry.prepare(request.getSettings(),
                resources);
        fontRegistry.validate(preparedCatalog);
        FontResourceSnapshot.assertNotInterrupted();
        FontRuntimeMetrics metrics = FontRuntimeMetrics.prepare(request.getSettings(),
                preparedCatalog.getCatalogSnapshot());
        FontResourceSnapshot.assertNotInterrupted();
        return new FontGenerationCandidate(request.getDesiredSequence(), request.getBaseRuntimeVersion(),
                request.getBaseTextMeasureEpoch(), request.getRuntimeVersion(), request.getTextMeasureEpoch(),
                request.getSettings(), preparedCatalog, metrics, resources.getFingerprint());
    }

    long getDesiredSequence() {
        return desiredSequence;
    }

    int getBaseRuntimeVersion() {
        return baseRuntimeVersion;
    }

    int getBaseTextMeasureEpoch() {
        return baseTextMeasureEpoch;
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

    FontGenerationRegistry.PreparedCatalog getPreparedCatalog() {
        return preparedCatalog;
    }

    FontRuntimeMetrics getMetrics() {
        return metrics;
    }

    FontResourceFingerprint getResourceFingerprint() {
        return resourceFingerprint;
    }

    FontGenerationCandidate withResourceFingerprint(FontResourceFingerprint fingerprint) {
        return new FontGenerationCandidate(desiredSequence, baseRuntimeVersion, baseTextMeasureEpoch,
                runtimeVersion, textMeasureEpoch, settings, preparedCatalog, metrics, fingerprint);
    }

    boolean matchesBuildRequest(FontGenerationBuildRequest request) {
        return request != null && desiredSequence == request.getDesiredSequence()
                && baseRuntimeVersion == request.getBaseRuntimeVersion()
                && baseTextMeasureEpoch == request.getBaseTextMeasureEpoch()
                && runtimeVersion == request.getRuntimeVersion()
                && textMeasureEpoch == request.getTextMeasureEpoch()
                && settings == request.getSettings();
    }
}

interface FontGenerationCandidateFactory {

    FontGenerationCandidate prepare(FontGenerationRegistry fontRegistry, FontGenerationBuildRequest request);
}

enum DefaultFontGenerationCandidateFactory implements FontGenerationCandidateFactory {
    INSTANCE;

    @Override
    public FontGenerationCandidate prepare(FontGenerationRegistry fontRegistry, FontGenerationBuildRequest request) {
        return FontGenerationCandidate.prepare(fontRegistry, request);
    }
}
