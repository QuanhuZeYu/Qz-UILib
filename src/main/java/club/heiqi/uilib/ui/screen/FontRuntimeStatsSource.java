package club.heiqi.uilib.ui.screen;

import club.heiqi.uilib.font.FontRuntimeStats;

/**
 * 为文档页控制器提供字体运行时统计读取入口。
 */
interface FontRuntimeStatsSource {

    /**
     * 读取当前字体运行时统计快照。
     *
     * @return 字体运行时统计
     */
    FontRuntimeStats getRuntimeStats();
}
