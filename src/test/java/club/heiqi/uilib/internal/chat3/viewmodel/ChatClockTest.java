package club.heiqi.uilib.internal.chat3.viewmodel;

import java.text.SimpleDateFormat;
import java.util.Locale;

import org.junit.Assert;
import org.junit.Test;

/**
 * ChatClock 契约测试:HH:mm 本地时间格式(与 Locale.ROOT 同口径)。
 */
public class ChatClockTest {

    @Test
    public void shouldFormatAsLocalHhMm() {
        long millis = 1780000000123L;
        String expected = new SimpleDateFormat("HH:mm", Locale.ROOT).format(new java.util.Date(millis));
        Assert.assertEquals(expected, ChatClock.formatTime(millis));
        Assert.assertEquals(5, ChatClock.formatTime(millis).length());
    }
}
