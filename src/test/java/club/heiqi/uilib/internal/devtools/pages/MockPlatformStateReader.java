package club.heiqi.uilib.internal.devtools.pages;

/**
 * Mock 平台状态读取器 —— 纯沙箱测试用，可注入任意当前态序列。
 *
 * <p>测试用例在每帧之间直接修改公开字段的值模拟平台状态变化，
 * 由 {@link LwjglInputSource#drainFrame()} 通过 {@link PlatformStateReader} 接口读取。</p>
 */
public class MockPlatformStateReader implements PlatformStateReader {

    // ===== 当前态（公开字段，测试直接改） =====

    public int mouseX;
    public int mouseY;
    public boolean buttonLeft;
    public boolean buttonRight;
    public boolean buttonMiddle;
    public boolean button4;
    public boolean button5;
    public double scrollAccum;
    public boolean control;
    public boolean shift;
    public boolean alt;
    public boolean meta;
    public int logicalWidth;
    public int logicalHeight;
    public long nowNanos;

    public MockPlatformStateReader() {
        this.mouseX = 0;
        this.mouseY = 0;
        this.buttonLeft = false;
        this.buttonRight = false;
        this.buttonMiddle = false;
        this.button4 = false;
        this.button5 = false;
        this.scrollAccum = 0.0;
        this.control = false;
        this.shift = false;
        this.alt = false;
        this.meta = false;
        this.logicalWidth = 800;
        this.logicalHeight = 600;
        this.nowNanos = 1_000_000_000L; // 1s
    }

    /** 推进时间戳，避免同一帧重复时间 */
    public void advanceTime() {
        nowNanos += 16_666_667L; // ~16.7ms (60fps)
    }

    // ===== PlatformStateReader 实现 =====

    @Override
    public int mouseX() { return mouseX; }

    @Override
    public int mouseY() { return mouseY; }

    @Override
    public boolean buttonDown(int button) {
        switch (button) {
            case 0: return buttonLeft;
            case 1: return buttonRight;
            case 2: return buttonMiddle;
            case 3: return button4;
            case 4: return button5;
            default: return false;
        }
    }

    @Override
    public double scrollAccum() { return scrollAccum; }

    @Override
    public boolean control() { return control; }

    @Override
    public boolean shift() { return shift; }

    @Override
    public boolean alt() { return alt; }

    @Override
    public boolean meta() { return meta; }

    @Override
    public int logicalWidth() { return logicalWidth; }

    @Override
    public int logicalHeight() { return logicalHeight; }

    @Override
    public long nowNanos() { return nowNanos; }
}
