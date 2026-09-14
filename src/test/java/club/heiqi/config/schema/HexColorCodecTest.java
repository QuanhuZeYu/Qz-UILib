package club.heiqi.config.schema;

import org.junit.Assert;
import org.junit.Test;

/**
 * {@link HexColorCodec} 的形态契约（纯函数，无 scene）：颜色字段「值 ↔ 用户文本」的规则全钉在这里。
 *
 * <p><b>存在理由</b>：这套判读规则决定存量颜色的解释方式（六位纯数字读十进制、越界不夹取、
 * 非法原文交给草稿校验），离线即可覆盖全部形态，可替代一次「改完颜色字段后把每种写法逐个手敲」的
 * 实机验证；样本表含下游存量值的真实形态（{@code 4253439} 与 {@code #40E6FF} 同值）。</p>
 */
public class HexColorCodecTest {

    /** 文档化的四种输入形态（含前缀 / 大小写变体与首尾空白）解出同一个颜色。 */
    @Test
    public void parseAcceptsEveryDocumentedForm() {
        String[] forms = {"#40E6FF", "#40e6ff", "40E6FF", "40e6ff", "0x40E6FF", "0X40e6ff",
                "  #40E6FF  ", "4253439"};
        for (String form : forms) {
            Assert.assertEquals("形态必须解出同一颜色: [" + form + "]",
                    Integer.valueOf(0x40E6FF), HexColorCodec.parse(form));
        }
    }

    /** 六位纯数字判十进制（存量语义不变），要十六进制必须带 # 或 0x；前导零不计位、值域两端可达。 */
    @Test
    public void sixDigitDigitsOnlyTextIsDecimalAndRangeEndsAreReachable() {
        Assert.assertEquals("六位纯数字必须读十进制", Integer.valueOf(123456), HexColorCodec.parse("123456"));
        Assert.assertNotEquals("六位纯数字不得读成十六进制",
                Integer.valueOf(0x123456), HexColorCodec.parse("123456"));
        Assert.assertEquals("带 # 才是十六进制", Integer.valueOf(0x123456), HexColorCodec.parse("#123456"));
        Assert.assertEquals("带 0x 才是十六进制", Integer.valueOf(0x123456), HexColorCodec.parse("0x123456"));
        Assert.assertEquals("无前缀但含十六进制字母读十六进制",
                Integer.valueOf(0xABCDEF), HexColorCodec.parse("abcdef"));
        Assert.assertEquals("前导零不影响判读", Integer.valueOf(255), HexColorCodec.parse("0000255"));
        Assert.assertEquals("全零写法是 0", Integer.valueOf(0), HexColorCodec.parse("00000000"));
        Assert.assertEquals("值域下界", Integer.valueOf(0), HexColorCodec.parse("0"));
        Assert.assertEquals("值域上界",
                Integer.valueOf(HexColorCodec.MAX_RGB), HexColorCodec.parse("16777215"));
    }

    /** 非法输入一律 {@code null}：位数不符、非十六进制字符、符号 / 小数点 / 指数、越界——不抛异常、不夹取。 */
    @Test
    public void parseRejectsMalformedAndOutOfRangeText() {
        String[] rejected = {null, "", "   ", "#", "#12345", "#1234567", "#40E6FG", "#-12345", "-1", "+255",
                "1e5", "255.0", "40E6F", "40E6FFF", "GGGGGG", "0x12345", "0xGGGGGG", "16777216", "99999999",
                "4294967296", "# 123456"};
        for (String text : rejected) {
            Assert.assertNull("必须拒绝: [" + text + "]", HexColorCodec.parse(text));
        }
    }

    /** 规范形态恒为大写 {@code #RRGGBB}；与解析在值域两端及若干样本上互为逆运算（编码只取低 24 位）。 */
    @Test
    public void formatIsUppercaseHashHexAndRoundTrips() {
        Assert.assertEquals("#000000", HexColorCodec.format(0x000000));
        Assert.assertEquals("#0000AB", HexColorCodec.format(0x0000AB));
        Assert.assertEquals("#40E6FF", HexColorCodec.format(0x40E6FF));
        Assert.assertEquals("#FFFFFF", HexColorCodec.format(0xFFFFFF));
        Assert.assertEquals("编码只取低 24 位", "#40E6FF", HexColorCodec.format(0xFF40E6FF));

        int[] values = {0x000000, 0x0000FF, 0x00FF00, 0xFF0000, 0x40E6FF, 0xFFFFFF};
        for (int value : values) {
            Assert.assertEquals("往返必须同值: 0x" + Integer.toHexString(value),
                    Integer.valueOf(value), HexColorCodec.parse(HexColorCodec.format(value)));
        }
    }
}
