package club.heiqi.uilib.font.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 首次启动时使用的系统字体优先级提示。
 */
public final class DefaultFontOrderHints {

    private static final String[] WINDOWS_FONT_HINTS = new String[] {
            "Microsoft YaHei UI",
            "Microsoft YaHei",
            "Microsoft JhengHei UI",
            "Microsoft JhengHei",
            "Malgun Gothic",
            "Meiryo UI",
            "Meiryo",
            "Yu Gothic UI",
            "Yu Gothic",
            "Segoe UI Emoji",
            "Segoe UI Symbol",
            "Arial Unicode MS"
    };

    private static final String[] MACOS_FONT_HINTS = new String[] {
            "PingFang SC",
            "PingFang TC",
            "Hiragino Sans GB",
            "Hiragino Sans",
            "Apple SD Gothic Neo",
            "Helvetica Neue",
            "Apple Color Emoji",
            "Arial Unicode MS"
    };

    private static final String[] LINUX_FONT_HINTS = new String[] {
            "Noto Sans CJK SC",
            "Noto Sans CJK TC",
            "Noto Sans CJK JP",
            "Noto Sans CJK KR",
            "Noto Sans SC",
            "Noto Sans TC",
            "Noto Sans JP",
            "Noto Sans KR",
            "Source Han Sans SC",
            "Source Han Sans CN",
            "WenQuanYi Micro Hei",
            "WenQuanYi Zen Hei",
            "DejaVu Sans",
            "Noto Color Emoji",
            "Noto Sans Symbols2"
    };

    private static final String[] COMMON_FONT_HINTS = new String[] {
            "Noto Sans CJK",
            "Noto Sans",
            "Source Han Sans",
            "Arial Unicode MS",
            "Dialog"
    };

    private DefaultFontOrderHints() {}

    /**
     * 返回当前平台的首启字体优先级提示。
     *
     * @return 字体名称提示数组
     */
    public static String[] resolveForCurrentPlatform() {
        return resolveForOsName(System.getProperty("os.name"));
    }

    /**
     * 根据操作系统名称返回首启字体优先级提示。
     *
     * @param osName 操作系统名称
     * @return 字体名称提示数组
     */
    static String[] resolveForOsName(String osName) {
        List<String> hints = new ArrayList<String>();
        String normalized = osName == null ? "" : osName.toLowerCase(Locale.ENGLISH);
        if (normalized.contains("win")) {
            appendAll(hints, WINDOWS_FONT_HINTS);
        } else if (normalized.contains("mac") || normalized.contains("darwin")) {
            appendAll(hints, MACOS_FONT_HINTS);
        } else {
            appendAll(hints, LINUX_FONT_HINTS);
        }
        appendAll(hints, COMMON_FONT_HINTS);
        return hints.toArray(new String[hints.size()]);
    }

    private static void appendAll(List<String> target, String[] values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (value != null && !target.contains(value)) {
                target.add(value);
            }
        }
    }
}
