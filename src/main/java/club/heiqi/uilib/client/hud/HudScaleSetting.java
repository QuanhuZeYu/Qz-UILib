package club.heiqi.uilib.client.hud;

/** HUD 宿主独立缩放设置；不读取或跟随 Minecraft GUI scale。 */
public final class HudScaleSetting {
    private static final float[] ALLOWED = { 1F, 1.25F, 1.5F, 1.75F, 2F };
    private float scale = 1F;

    /** 返回当前 HUD 缩放倍率。 */
    public float get() { return scale; }

    /** 设置受支持的 HUD 缩放倍率。 */
    public void set(float value) {
        for (float allowed : ALLOWED) {
            if (Float.compare(value, allowed) == 0) { scale = value; return; }
        }
        throw new IllegalArgumentException("HUD scale must be 1, 1.25, 1.5, 1.75, or 2");
    }
}
