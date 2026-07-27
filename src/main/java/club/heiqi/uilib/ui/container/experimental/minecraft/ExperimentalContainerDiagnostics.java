package club.heiqi.uilib.ui.container.experimental.minecraft;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** P3 真机闭环期间的平台中立临时诊断开关。 */
final class ExperimentalContainerDiagnostics {
    private static final Logger LOG = LogManager.getLogger("QzUiLib/ExperimentalContainer");
    private static final String TEMP_PREFIX = "[QZUILIB-P3-TEMP]";
    private static final boolean DEBUG = "true".equalsIgnoreCase(
            System.getProperty("qzuilib.experimental.container.debug", "false"));

    private ExperimentalContainerDiagnostics() {}

    /** 输出默认关闭的详细诊断；P3 完整验收后必须清理。 */
    static void log(String message, Object... arguments) {
        if (DEBUG) LOG.info(TEMP_PREFIX + " " + message, arguments);
    }
}
