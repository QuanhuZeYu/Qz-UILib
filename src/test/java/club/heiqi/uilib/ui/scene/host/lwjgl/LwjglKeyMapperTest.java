package club.heiqi.uilib.ui.scene.host.lwjgl;


import club.heiqi.uilib.ui.scene.input.SceneKey;
import org.junit.Assert;
import org.junit.Test;

/**
 * LwjglKeyMapper 映射表单元测试 —— 核验 native→SceneKey 关键键映射与 UNKNOWN 兜底。
 */
public class LwjglKeyMapperTest {

    // ==================== 组 A：控制/编辑键 ====================

    @Test
    public void a1_mapEnter() {
        Assert.assertEquals("28→ENTER", SceneKey.ENTER, LwjglKeyMapper.map(28));
    }

    @Test
    public void a2_mapBackspace() {
        Assert.assertEquals("14→BACKSPACE", SceneKey.BACKSPACE, LwjglKeyMapper.map(14));
    }

    @Test
    public void a3_mapTab() {
        Assert.assertEquals("15→TAB", SceneKey.TAB, LwjglKeyMapper.map(15));
    }

    @Test
    public void a4_mapEscape() {
        Assert.assertEquals("1→ESCAPE", SceneKey.ESCAPE, LwjglKeyMapper.map(1));
    }

    @Test
    public void a5_mapSpace() {
        Assert.assertEquals("57→SPACE", SceneKey.SPACE, LwjglKeyMapper.map(57));
    }

    @Test
    public void a6_mapDelete() {
        Assert.assertEquals("211→DELETE", SceneKey.DELETE, LwjglKeyMapper.map(211));
    }

    @Test
    public void a7_mapHome() {
        Assert.assertEquals("199→HOME", SceneKey.HOME, LwjglKeyMapper.map(199));
    }

    @Test
    public void a8_mapEnd() {
        Assert.assertEquals("207→END", SceneKey.END, LwjglKeyMapper.map(207));
    }

    @Test
    public void a9_mapPageUp() {
        Assert.assertEquals("201→PAGE_UP", SceneKey.PAGE_UP, LwjglKeyMapper.map(201));
    }

    @Test
    public void a10_mapPageDown() {
        Assert.assertEquals("209→PAGE_DOWN", SceneKey.PAGE_DOWN, LwjglKeyMapper.map(209));
    }

    @Test
    public void a11_mapInsert() {
        Assert.assertEquals("210→INSERT", SceneKey.INSERT, LwjglKeyMapper.map(210));
    }

    @Test
    public void a12_mapCapsLock() {
        Assert.assertEquals("58→CAPS_LOCK", SceneKey.CAPS_LOCK, LwjglKeyMapper.map(58));
    }

    @Test
    public void a13_mapPrintScreen() {
        Assert.assertEquals("183→PRINT_SCREEN", SceneKey.PRINT_SCREEN, LwjglKeyMapper.map(183));
    }

    @Test
    public void a14_mapScrollLock() {
        Assert.assertEquals("70→SCROLL_LOCK", SceneKey.SCROLL_LOCK, LwjglKeyMapper.map(70));
    }

    @Test
    public void a15_mapPause() {
        Assert.assertEquals("197→PAUSE", SceneKey.PAUSE, LwjglKeyMapper.map(197));
    }

    @Test
    public void a16_mapNumLock() {
        Assert.assertEquals("69→NUM_LOCK", SceneKey.NUM_LOCK, LwjglKeyMapper.map(69));
    }

    @Test
    public void a17_mapMenu() {
        Assert.assertEquals("221→MENU", SceneKey.MENU, LwjglKeyMapper.map(221));
    }

    // ==================== 组 B：方向键 ====================

    @Test
    public void b1_mapArrowUp() {
        Assert.assertEquals("200→ARROW_UP", SceneKey.ARROW_UP, LwjglKeyMapper.map(200));
    }

    @Test
    public void b2_mapArrowDown() {
        Assert.assertEquals("208→ARROW_DOWN", SceneKey.ARROW_DOWN, LwjglKeyMapper.map(208));
    }

    @Test
    public void b3_mapArrowLeft() {
        Assert.assertEquals("203→ARROW_LEFT", SceneKey.ARROW_LEFT, LwjglKeyMapper.map(203));
    }

    @Test
    public void b4_mapArrowRight() {
        Assert.assertEquals("205→ARROW_RIGHT", SceneKey.ARROW_RIGHT, LwjglKeyMapper.map(205));
    }

    // ==================== 组 C：字母键（抽样） ====================

    @Test
    public void c1_mapKeyA() {
        Assert.assertEquals("30→KEY_A", SceneKey.KEY_A, LwjglKeyMapper.map(30));
    }

    @Test
    public void c2_mapKeyZ() {
        Assert.assertEquals("44→KEY_Z", SceneKey.KEY_Z, LwjglKeyMapper.map(44));
    }

    @Test
    public void c3_mapKeyQ() {
        Assert.assertEquals("16→KEY_Q", SceneKey.KEY_Q, LwjglKeyMapper.map(16));
    }

    @Test
    public void c4_mapKeyM() {
        Assert.assertEquals("50→KEY_M", SceneKey.KEY_M, LwjglKeyMapper.map(50));
    }

    @Test
    public void c5_mapKeyT() {
        Assert.assertEquals("20→KEY_T", SceneKey.KEY_T, LwjglKeyMapper.map(20));
    }

    // ==================== 组 D：主键盘数字 ====================

    @Test
    public void d1_mapDigit1() {
        Assert.assertEquals("2→DIGIT_1", SceneKey.DIGIT_1, LwjglKeyMapper.map(2));
    }

    @Test
    public void d2_mapDigit9() {
        Assert.assertEquals("10→DIGIT_9", SceneKey.DIGIT_9, LwjglKeyMapper.map(10));
    }

    @Test
    public void d3_mapDigit0() {
        Assert.assertEquals("11→DIGIT_0", SceneKey.DIGIT_0, LwjglKeyMapper.map(11));
    }

    // ==================== 组 E：修饰键（左右分立） ====================

    @Test
    public void e1_mapShiftLeft() {
        Assert.assertEquals("42→SHIFT_LEFT", SceneKey.SHIFT_LEFT, LwjglKeyMapper.map(42));
    }

    @Test
    public void e2_mapShiftRight() {
        Assert.assertEquals("54→SHIFT_RIGHT", SceneKey.SHIFT_RIGHT, LwjglKeyMapper.map(54));
    }

    @Test
    public void e3_mapControlLeft() {
        Assert.assertEquals("29→CONTROL_LEFT", SceneKey.CONTROL_LEFT, LwjglKeyMapper.map(29));
    }

    @Test
    public void e4_mapControlRight() {
        Assert.assertEquals("157→CONTROL_RIGHT", SceneKey.CONTROL_RIGHT, LwjglKeyMapper.map(157));
    }

    @Test
    public void e5_mapAltLeft() {
        Assert.assertEquals("56→ALT_LEFT", SceneKey.ALT_LEFT, LwjglKeyMapper.map(56));
    }

    @Test
    public void e6_mapAltRight() {
        Assert.assertEquals("184→ALT_RIGHT", SceneKey.ALT_RIGHT, LwjglKeyMapper.map(184));
    }

    @Test
    public void e7_mapMetaLeft() {
        Assert.assertEquals("219→META_LEFT", SceneKey.META_LEFT, LwjglKeyMapper.map(219));
    }

    @Test
    public void e8_mapMetaRight() {
        Assert.assertEquals("220→META_RIGHT", SceneKey.META_RIGHT, LwjglKeyMapper.map(220));
    }

    // ==================== 组 F：功能键 ====================

    @Test
    public void f1_mapF1() {
        Assert.assertEquals("59→F1", SceneKey.F1, LwjglKeyMapper.map(59));
    }

    @Test
    public void f2_mapF12() {
        Assert.assertEquals("88→F12", SceneKey.F12, LwjglKeyMapper.map(88));
    }

    // ==================== 组 G：小键盘 ====================

    @Test
    public void g1_mapNumpadEnter() {
        Assert.assertEquals("156→NUMPAD_ENTER", SceneKey.NUMPAD_ENTER, LwjglKeyMapper.map(156));
    }

    @Test
    public void g2_mapNumpad0() {
        Assert.assertEquals("82→NUMPAD_0", SceneKey.NUMPAD_0, LwjglKeyMapper.map(82));
    }

    @Test
    public void g3_mapNumpad9() {
        Assert.assertEquals("73→NUMPAD_9", SceneKey.NUMPAD_9, LwjglKeyMapper.map(73));
    }

    @Test
    public void g4_mapNumpadAdd() {
        Assert.assertEquals("78→NUMPAD_ADD", SceneKey.NUMPAD_ADD, LwjglKeyMapper.map(78));
    }

    @Test
    public void g5_mapNumpadSubtract() {
        Assert.assertEquals("74→NUMPAD_SUBTRACT", SceneKey.NUMPAD_SUBTRACT, LwjglKeyMapper.map(74));
    }

    @Test
    public void g6_mapNumpadMultiply() {
        Assert.assertEquals("55→NUMPAD_MULTIPLY", SceneKey.NUMPAD_MULTIPLY, LwjglKeyMapper.map(55));
    }

    @Test
    public void g7_mapNumpadDivide() {
        Assert.assertEquals("181→NUMPAD_DIVIDE", SceneKey.NUMPAD_DIVIDE, LwjglKeyMapper.map(181));
    }

    @Test
    public void g8_mapNumpadDecimal() {
        Assert.assertEquals("83→NUMPAD_DECIMAL", SceneKey.NUMPAD_DECIMAL, LwjglKeyMapper.map(83));
    }

    // ==================== 组 H：主键盘符号 ====================

    @Test
    public void h1_mapMinus() {
        Assert.assertEquals("12→MINUS", SceneKey.MINUS, LwjglKeyMapper.map(12));
    }

    @Test
    public void h2_mapEquals() {
        Assert.assertEquals("13→EQUALS", SceneKey.EQUALS, LwjglKeyMapper.map(13));
    }

    @Test
    public void h3_mapBracketLeft() {
        Assert.assertEquals("26→BRACKET_LEFT", SceneKey.BRACKET_LEFT, LwjglKeyMapper.map(26));
    }

    @Test
    public void h4_mapBracketRight() {
        Assert.assertEquals("27→BRACKET_RIGHT", SceneKey.BRACKET_RIGHT, LwjglKeyMapper.map(27));
    }

    @Test
    public void h5_mapSemicolon() {
        Assert.assertEquals("39→SEMICOLON", SceneKey.SEMICOLON, LwjglKeyMapper.map(39));
    }

    @Test
    public void h6_mapApostrophe() {
        Assert.assertEquals("40→APOSTROPHE", SceneKey.APOSTROPHE, LwjglKeyMapper.map(40));
    }

    @Test
    public void h7_mapComma() {
        Assert.assertEquals("51→COMMA", SceneKey.COMMA, LwjglKeyMapper.map(51));
    }

    @Test
    public void h8_mapPeriod() {
        Assert.assertEquals("52→PERIOD", SceneKey.PERIOD, LwjglKeyMapper.map(52));
    }

    @Test
    public void h9_mapSlash() {
        Assert.assertEquals("53→SLASH", SceneKey.SLASH, LwjglKeyMapper.map(53));
    }

    // ==================== 组 Z：UNKNOWN 兜底 ====================

    @Test
    public void z1_unknownKeyCodeReturnsUnknown() {
        Assert.assertEquals("-1→UNKNOWN", SceneKey.UNKNOWN, LwjglKeyMapper.map(-1));
    }

    @Test
    public void z2_largeKeyCodeReturnsUnknown() {
        Assert.assertEquals("999→UNKNOWN", SceneKey.UNKNOWN, LwjglKeyMapper.map(999));
    }

    @Test
    public void z3_zeroKeyCodeReturnsUnknown() {
        // KEY_NONE = 0，不是有效按键
        Assert.assertEquals("0→UNKNOWN", SceneKey.UNKNOWN, LwjglKeyMapper.map(0));
    }

    @Test
    public void z4_multimediaKeyReturnsUnknown() {
        // KEY_NEXTTRACK 等多媒体键落 UNKNOWN
        Assert.assertEquals("多媒体键→UNKNOWN", SceneKey.UNKNOWN, LwjglKeyMapper.map(0x99));
    }

    // ==================== 组 I：isPrintable ====================

    @Test
    public void i1_letterIsPrintable() {
        Assert.assertTrue("'a' 可打印", LwjglInputSource.isPrintable('a'));
    }

    @Test
    public void i2_digitIsPrintable() {
        Assert.assertTrue("'1' 可打印", LwjglInputSource.isPrintable('1'));
    }

    @Test
    public void i3_spaceIsPrintable() {
        Assert.assertTrue("' ' 可打印", LwjglInputSource.isPrintable(' '));
    }

    @Test
    public void i4_nullCharNotPrintable() {
        Assert.assertFalse("'\\0' 不可打印", LwjglInputSource.isPrintable('\0'));
    }

    @Test
    public void i5_delNotPrintable() {
        Assert.assertFalse("DEL (0x7F) 不可打印", LwjglInputSource.isPrintable((char) 0x7F));
    }

    @Test
    public void i6_controlCharNotPrintable() {
        Assert.assertFalse("0x01 不可打印", LwjglInputSource.isPrintable((char) 0x01));
    }

    @Test
    public void i7_tabNotPrintable() {
        Assert.assertFalse("\\t 不可打印", LwjglInputSource.isPrintable('\t'));
    }
}
