package club.heiqi.uilib.internal.chat3.viewmodel;

import java.util.regex.Pattern;

import org.junit.Assert;
import org.junit.Test;

/**
 * SenderExtractor 契约测试:双前缀提取/失败返回 null(系统消息)/自定义模式。
 */
public class SenderExtractorTest {

    @Test
    public void shouldExtractAngleBracketSender() {
        SenderExtractor.SenderMatch match = SenderExtractor.DEFAULT.extract("<Steve> hello world");
        Assert.assertNotNull(match);
        Assert.assertEquals("Steve", match.getSender());
        Assert.assertEquals("hello world", match.getRest());
    }

    @Test
    public void shouldExtractColonSender() {
        SenderExtractor.SenderMatch match = SenderExtractor.DEFAULT.extract("Alex_: hi there");
        Assert.assertNotNull(match);
        Assert.assertEquals("Alex_", match.getSender());
        Assert.assertEquals("hi there", match.getRest());
    }

    @Test
    public void shouldReturnNullForSystemMessages() {
        Assert.assertNull(SenderExtractor.DEFAULT.extract("保存完成"));
        Assert.assertNull(SenderExtractor.DEFAULT.extract("[广播] 大家好"));
        Assert.assertNull(SenderExtractor.DEFAULT.extract(""));
        Assert.assertNull(SenderExtractor.DEFAULT.extract(null));
        Assert.assertNull(SenderExtractor.DEFAULT.extract("hello"));
    }

    @Test
    public void shouldTreatColonPrefixedAsSender() {
        // "Server: xxx" 匹配 colon 模式,按发送者处理(如实行为;S6 真机按服务器实际格式配置排除/自定义正则)
        SenderExtractor.SenderMatch match = SenderExtractor.DEFAULT.extract("Server: 保存完成");
        Assert.assertNotNull(match);
        Assert.assertEquals("Server", match.getSender());
        Assert.assertEquals("保存完成", match.getRest());
    }

    @Test
    public void shouldRejectOversizedNames() {
        Assert.assertNull(SenderExtractor.DEFAULT.extract("<ThisNameIsWayTooLongForMinecraft> hi"));
    }

    @Test
    public void shouldSupportCustomPatterns() {
        SenderExtractor custom = new SenderExtractor(Pattern.compile("^\\[(.*?)\\] ?(.*)$"));
        SenderExtractor.SenderMatch match = custom.extract("[Admin] 公告内容");
        Assert.assertNotNull(match);
        Assert.assertEquals("Admin", match.getSender());
        Assert.assertEquals("公告内容", match.getRest());
    }
}
