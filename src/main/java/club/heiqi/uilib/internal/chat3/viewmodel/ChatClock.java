package club.heiqi.uilib.internal.chat3.viewmodel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 聊天 3.0 时钟:组头时间戳格式化(HH:mm,Locale.ROOT)。
 *
 * <p>SimpleDateFormat 非线程安全,按线程隔离(渲染线程与任意 append 线程互不干扰)。</p>
 */
public final class ChatClock {

    private static final ThreadLocal<SimpleDateFormat> FORMAT = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("HH:mm", Locale.ROOT);
        }
    };

    private ChatClock() {
    }

    /**
     * @param wallMillis 到达时刻(System.currentTimeMillis 口径)
     * @return "HH:mm" 本地时间
     */
    public static String formatTime(long wallMillis) {
        return FORMAT.get().format(new Date(wallMillis));
    }
}
