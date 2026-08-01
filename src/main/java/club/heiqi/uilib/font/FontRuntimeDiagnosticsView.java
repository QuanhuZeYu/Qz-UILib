package club.heiqi.uilib.font;

/** 字体 singleton 的只读诊断 facade。 */
public final class FontRuntimeDiagnosticsView {

    private final FontService fontService;

    FontRuntimeDiagnosticsView(FontService fontService) {
        if (fontService == null) {
            throw new IllegalArgumentException("fontService 不得为 null");
        }
        this.fontService = fontService;
    }

    public boolean isInitialized() {
        return fontService.isInitialized();
    }

    public boolean isReloading() {
        return fontService.isReloading();
    }

    public int getRuntimeVersion() {
        return fontService.getRuntimeVersion();
    }

    public int getTextMeasureEpoch() {
        return fontService.getTextMeasureEpoch();
    }

    public FontRuntimeSettings getRuntimeSettings() {
        return fontService.getRuntimeSettings();
    }

    public FontRuntimeStats getRuntimeStats() {
        return fontService.getRuntimeStats();
    }
}
