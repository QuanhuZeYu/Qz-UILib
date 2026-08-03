package club.heiqi.uilib.font;

import java.io.File;

import club.heiqi.uilib.font.util.DefaultFontOrderHints;

/** Render owner 冻结后交给 generation builder 的不可变输入。 */
final class FontGenerationBuildRequest {

    private final long desiredSequence;
    private final int baseRuntimeVersion;
    private final int baseTextMeasureEpoch;
    private final int runtimeVersion;
    private final int textMeasureEpoch;
    private final FontRuntimeSettings settings;
    private final File fontDirectory;
    private final String[] defaultOrderHints;
    private final boolean createFontDirectoryIfMissing;

    FontGenerationBuildRequest(long desiredSequence, int baseRuntimeVersion, int baseTextMeasureEpoch,
            int runtimeVersion, int textMeasureEpoch, FontRuntimeSettings settings, File fontDirectory,
            String[] defaultOrderHints) {
        this(desiredSequence, baseRuntimeVersion, baseTextMeasureEpoch, runtimeVersion, textMeasureEpoch,
                settings, fontDirectory, defaultOrderHints, false);
    }

    private FontGenerationBuildRequest(long desiredSequence, int baseRuntimeVersion, int baseTextMeasureEpoch,
            int runtimeVersion, int textMeasureEpoch, FontRuntimeSettings settings, File fontDirectory,
            String[] defaultOrderHints, boolean createFontDirectoryIfMissing) {
        if (desiredSequence < 0L || baseRuntimeVersion < 0 || baseTextMeasureEpoch < 0
                || runtimeVersion != baseRuntimeVersion + 1 || textMeasureEpoch != baseTextMeasureEpoch + 1
                || settings == null || fontDirectory == null) {
            throw new IllegalArgumentException("generation build request 无效");
        }
        this.desiredSequence = desiredSequence;
        this.baseRuntimeVersion = baseRuntimeVersion;
        this.baseTextMeasureEpoch = baseTextMeasureEpoch;
        this.runtimeVersion = runtimeVersion;
        this.textMeasureEpoch = textMeasureEpoch;
        this.settings = settings;
        this.fontDirectory = fontDirectory.getAbsoluteFile();
        this.defaultOrderHints = defaultOrderHints == null ? new String[0] : defaultOrderHints.clone();
        this.createFontDirectoryIfMissing = createFontDirectoryIfMissing;
    }

    static FontGenerationBuildRequest capture(long desiredSequence, ActiveFontGeneration current) {
        return capture(desiredSequence, current, resolveGameRootDirectory());
    }

    static FontGenerationBuildRequest capture(long desiredSequence, ActiveFontGeneration current,
            File gameRootDirectory) {
        if (gameRootDirectory == null) {
            throw new IllegalArgumentException("游戏根目录不得为 null");
        }
        int baseRuntimeVersion = current == null ? 0 : current.getRuntimeVersion();
        int baseTextMeasureEpoch = current == null ? 0 : current.getTextMeasureEpoch();
        if (baseRuntimeVersion == Integer.MAX_VALUE || baseTextMeasureEpoch == Integer.MAX_VALUE) {
            throw new IllegalStateException("字体 generation version/epoch 已耗尽");
        }
        File fontDirectory = new File(gameRootDirectory, "fonts");
        return new FontGenerationBuildRequest(desiredSequence, baseRuntimeVersion, baseTextMeasureEpoch,
                baseRuntimeVersion + 1, baseTextMeasureEpoch + 1, FontRuntimeSettings.capture(), fontDirectory,
                DefaultFontOrderHints.resolveForCurrentPlatform(), current == null);
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

    File getFontDirectory() {
        return fontDirectory;
    }

    String[] getDefaultOrderHints() {
        return defaultOrderHints.clone();
    }

    boolean shouldCreateFontDirectoryIfMissing() {
        return createFontDirectoryIfMissing;
    }

    private static File resolveGameRootDirectory() {
        try {
            Class<?> minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            Object minecraft = minecraftClass.getMethod("getMinecraft").invoke(null);
            if (minecraft != null) {
                Object dataDir = minecraftClass.getField("mcDataDir").get(minecraft);
                if (dataDir instanceof File) {
                    return (File) dataDir;
                }
            }
        } catch (Throwable ignored) {
            // Dedicated server、测试和早期启动均使用原有 user.dir fallback。
        }
        return new File(safeSystemProperty("user.dir"));
    }

    private static String safeSystemProperty(String name) {
        try {
            String value = System.getProperty(name);
            return value == null ? "" : value;
        } catch (SecurityException ignored) {
            return "";
        }
    }
}
