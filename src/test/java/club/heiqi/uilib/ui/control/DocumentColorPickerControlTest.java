package club.heiqi.uilib.ui.control;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Assert;
import org.junit.Test;

import club.heiqi.uilib.ui.dom.UiDocument;

/**
 * `DocumentColorPickerControl` 的契约测试，覆盖 ARGB/HEX/RGB 双向同步、非法输入与事件触发。
 */
public class DocumentColorPickerControlTest {

    /**
     * 验证初始颜色为不透明黑。
     */
    @Test
    public void shouldInitializeWithOpaqueBlack() {
        DocumentColorPickerControl picker = new DocumentColorPickerControl(UiDocument.create());

        Assert.assertEquals(0xFF000000, picker.getColor());
        Assert.assertEquals("#FF000000", picker.getHex());
    }

    /**
     * 验证 setColor 同步 HEX 与 RGB 输入框。
     */
    @Test
    public void shouldSyncHexAndRgbOnSetColor() {
        DocumentColorPickerControl picker = new DocumentColorPickerControl(UiDocument.create());

        picker.setColor(0xFF112233);

        Assert.assertEquals("#FF112233", picker.getHex());
        Assert.assertArrayEquals(new int[] {0x11, 0x22, 0x33}, picker.getRgb());
        Assert.assertEquals("#FF112233", picker.getHexInput().getText().toUpperCase());
        Assert.assertEquals("17", picker.getRedInput().getText());
        Assert.assertEquals("34", picker.getGreenInput().getText());
        Assert.assertEquals("51", picker.getBlueInput().getText());
    }

    /**
     * 验证 setHex(RRGGBB) 会把 alpha 设为 0xFF 并同步 RGB。
     */
    @Test
    public void shouldAcceptSixDigitHexAndForceOpaqueAlpha() {
        DocumentColorPickerControl picker = new DocumentColorPickerControl(UiDocument.create());

        picker.setHex("#8090A0");

        Assert.assertEquals(0xFF8090A0, picker.getColor());
        Assert.assertArrayEquals(new int[] {0x80, 0x90, 0xA0}, picker.getRgb());
    }

    /**
     * 验证 setHex(AARRGGBB) 会保留自定义 alpha。
     */
    @Test
    public void shouldAcceptEightDigitHexWithCustomAlpha() {
        DocumentColorPickerControl picker = new DocumentColorPickerControl(UiDocument.create());

        picker.setHex("#80808080");

        Assert.assertEquals(0x80808080, picker.getColor());
    }

    /**
     * 验证 setHex 接受不带 # 的形式。
     */
    @Test
    public void shouldAcceptHexWithoutHash() {
        DocumentColorPickerControl picker = new DocumentColorPickerControl(UiDocument.create());

        picker.setHex("FFAABB");

        Assert.assertEquals(0xFFFFAABB, picker.getColor());
    }

    /**
     * 验证非法 HEX 文本会显示错误且保留旧值。
     */
    @Test
    public void shouldShowErrorAndKeepOldColorWhenHexInvalid() {
        DocumentColorPickerControl picker = new DocumentColorPickerControl(UiDocument.create());
        picker.setColor(0xFF112233);

        picker.setHex("not-a-color");

        Assert.assertEquals(0xFF112233, picker.getColor());
        Assert.assertFalse("应显示错误提示", picker.getError().isEmpty());
    }

    /**
     * 验证 setRgb 同步 HEX 与内部 ARGB。
     */
    @Test
    public void shouldSyncHexWhenRgbChanged() {
        DocumentColorPickerControl picker = new DocumentColorPickerControl(UiDocument.create());

        picker.setRgb(10, 20, 30);

        Assert.assertEquals(0xFF0A141E, picker.getColor());
        Assert.assertEquals("#FF0A141E", picker.getHex());
    }

    /**
     * 验证超界 RGB 显示错误并保留旧值。
     */
    @Test
    public void shouldShowErrorWhenRgbOutOfRange() {
        DocumentColorPickerControl picker = new DocumentColorPickerControl(UiDocument.create());
        picker.setColor(0xFF112233);

        picker.setRgb(0, 0, 999);

        Assert.assertEquals(0xFF112233, picker.getColor());
        Assert.assertFalse(picker.getError().isEmpty());
    }

    /**
     * 验证 setColor 触发 changeHandler 一次。
     */
    @Test
    public void shouldFireChangeHandlerOnSetColor() {
        final AtomicInteger firedCount = new AtomicInteger(0);
        final int[] capturedArgb = new int[] {-1};
        DocumentColorPickerControl picker = new DocumentColorPickerControl(UiDocument.create());
        picker.setChangeHandler(new DocumentColorPickerChangeHandler() {
            @Override
            public void onColorChanged(DocumentColorPickerChangeEvent event) {
                firedCount.incrementAndGet();
                capturedArgb[0] = event.getArgb();
            }
        });

        picker.setColor(0xFFAABBCC);

        Assert.assertEquals(1, firedCount.get());
        Assert.assertEquals(0xFFAABBCC, capturedArgb[0]);
    }

    /**
     * 验证颜色未变化时不触发 changeHandler。
     */
    @Test
    public void shouldNotFireWhenColorUnchanged() {
        final AtomicInteger firedCount = new AtomicInteger(0);
        DocumentColorPickerControl picker = new DocumentColorPickerControl(UiDocument.create());
        picker.setColor(0xFFAABBCC);
        picker.setChangeHandler(new DocumentColorPickerChangeHandler() {
            @Override
            public void onColorChanged(DocumentColorPickerChangeEvent event) {
                firedCount.incrementAndGet();
            }
        });

        picker.setColor(0xFFAABBCC);

        Assert.assertEquals(0, firedCount.get());
    }

    /**
     * 验证 commitNow 触发 confirmHandler。
     */
    @Test
    public void shouldFireConfirmOnCommitNow() {
        final AtomicInteger firedCount = new AtomicInteger(0);
        final int[] captured = new int[] {-1};
        final boolean[] capturedValid = new boolean[] {false};
        DocumentColorPickerControl picker = new DocumentColorPickerControl(UiDocument.create());
        picker.setColor(0xFF778899);
        picker.setConfirmHandler(new DocumentColorPickerConfirmHandler() {
            @Override
            public void onColorConfirmed(DocumentColorPickerConfirmEvent event) {
                firedCount.incrementAndGet();
                captured[0] = event.getArgb();
                capturedValid[0] = event.isValid();
            }
        });

        picker.commitNow();

        Assert.assertEquals(1, firedCount.get());
        Assert.assertEquals(0xFF778899, captured[0]);
        Assert.assertTrue(capturedValid[0]);
    }

    /**
     * 验证 setHex 合法后再 commit，事件 isValid 为 true。
     */
    @Test
    public void shouldReportValidWhenHexLegal() {
        final boolean[] captured = new boolean[] {false};
        DocumentColorPickerControl picker = new DocumentColorPickerControl(UiDocument.create());
        picker.setConfirmHandler(new DocumentColorPickerConfirmHandler() {
            @Override
            public void onColorConfirmed(DocumentColorPickerConfirmEvent event) {
                captured[0] = event.isValid();
            }
        });

        picker.setHex("#AABBCC");
        picker.commitNow();

        Assert.assertTrue(captured[0]);
        Assert.assertTrue(picker.getError().isEmpty());
    }

    /**
     * 验证非法输入后再 commit，事件 isValid 为 false。
     */
    @Test
    public void shouldReportInvalidWhenHexIllegal() {
        final boolean[] captured = new boolean[] {true};
        DocumentColorPickerControl picker = new DocumentColorPickerControl(UiDocument.create());
        picker.setConfirmHandler(new DocumentColorPickerConfirmHandler() {
            @Override
            public void onColorConfirmed(DocumentColorPickerConfirmEvent event) {
                captured[0] = event.isValid();
            }
        });

        picker.setHex("xyz");
        picker.commitNow();

        Assert.assertFalse(captured[0]);
    }

    /**
     * 验证 setRgb 触发 changeHandler，且相同 RGB 不重复触发。
     */
    @Test
    public void shouldFireChangeHandlerOnSetRgbAndSkipDuplicate() {
        final AtomicInteger firedCount = new AtomicInteger(0);
        DocumentColorPickerControl picker = new DocumentColorPickerControl(UiDocument.create());
        picker.setChangeHandler(new DocumentColorPickerChangeHandler() {
            @Override
            public void onColorChanged(DocumentColorPickerChangeEvent event) {
                firedCount.incrementAndGet();
            }
        });

        picker.setRgb(100, 150, 200);
        Assert.assertEquals(1, firedCount.get());
        Assert.assertEquals(0xFF6496C8, picker.getColor());

        // 相同 RGB 不重复触发
        picker.setRgb(100, 150, 200);
        Assert.assertEquals(1, firedCount.get());
    }

    /**
     * 验证 setColor 成功后清除既有错误提示。
     */
    @Test
    public void shouldClearErrorOnSuccessfulSetColor() {
        DocumentColorPickerControl picker = new DocumentColorPickerControl(UiDocument.create());
        // 先制造错误
        picker.setHex("not-a-color");
        Assert.assertFalse(picker.getError().isEmpty());

        // 合法 setColor 后错误应清除
        picker.setColor(0xFFAABBCC);
        Assert.assertTrue(picker.getError().isEmpty());
    }
}
