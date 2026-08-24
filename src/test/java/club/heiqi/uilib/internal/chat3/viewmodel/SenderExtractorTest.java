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

    // ==================== K3 修复:冒号模式负向排除 scheme ====================

    @Test
    public void shouldNotTreatUrlSchemesAsSender() {
        // 真机实锤:"https://..." 被冒号模式误判 sender="https"/rest="//..."(K3 缺陷 3)
        Assert.assertNull(SenderExtractor.DEFAULT.extract("https://example.com/path"));
        Assert.assertNull(SenderExtractor.DEFAULT.extract("http://example.com"));
        Assert.assertNull(SenderExtractor.DEFAULT.extract("ftp://files.example.com/a.zip"));
        Assert.assertNull(SenderExtractor.DEFAULT.extract("file:///C:/notes.txt"));
        Assert.assertNull(SenderExtractor.DEFAULT.extract("sftp://host/x"));
        // 大小写不敏感:大写 scheme 同样按 URL 处理
        Assert.assertNull(SenderExtractor.DEFAULT.extract("HTTPS://example.com"));
        // 已知 scheme 词 + 冒号(即使后随空格而非 //)同样排除
        Assert.assertNull(SenderExtractor.DEFAULT.extract("https: 请看"));
        // 未知 scheme 的 scheme:// 形态:冒号后紧跟 // 一律排除
        Assert.assertNull(SenderExtractor.DEFAULT.extract("bob://x"));
    }

    @Test
    public void colonSenderStillExtractsAroundUrls() {
        // 正常 "名字: 内容" 不受负向排除影响
        SenderExtractor.SenderMatch match = SenderExtractor.DEFAULT.extract("steve: 你好");
        Assert.assertNotNull(match);
        Assert.assertEquals("steve", match.getSender());
        Assert.assertEquals("你好", match.getRest());

        // 无空格 "名字:内容" 保持原语义(可选空格)
        SenderExtractor.SenderMatch tight = SenderExtractor.DEFAULT.extract("alice:hi");
        Assert.assertNotNull(tight);
        Assert.assertEquals("alice", tight.getSender());
        Assert.assertEquals("hi", tight.getRest());

        // 消息正文含完整 URL:提取发送者,URL 留在 rest 内不被吞
        SenderExtractor.SenderMatch withUrl = SenderExtractor.DEFAULT.extract(
                "steve: 看这个 https://example.com/a");
        Assert.assertNotNull(withUrl);
        Assert.assertEquals("steve", withUrl.getSender());
        Assert.assertEquals("看这个 https://example.com/a", withUrl.getRest());

        // 冒号后紧跟双斜杠的普通消息(非 scheme 词)也按非发送者处理(负向排除紧邻 //)
        Assert.assertNull(SenderExtractor.DEFAULT.extract("carol: //注释"));
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
