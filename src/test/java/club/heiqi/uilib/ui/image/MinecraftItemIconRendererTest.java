package club.heiqi.uilib.ui.image;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import org.junit.Assert;
import org.junit.Test;

/**
 * Minecraft item icon 当帧直绘测试：2D 判定、2D 等价自绘的 GL 动作序列与恢复、
 * 非 2D 委托原版、宿主能力缺失时的跳过合同。
 */
public class MinecraftItemIconRendererTest {

    @Test
    public void shouldUseVisibleGuiDepthAndRestorePreviousValue() {
        RecordingItemState depth = new RecordingItemState(37.0F, true);

        MinecraftItemIconRenderer.runWithGuiItemDepth(depth,
                () -> Assert.assertEquals(MinecraftItemIconRenderer.GUI_ITEM_Z_LEVEL,
                        depth.get(), 0.0F));

        Assert.assertEquals(37.0F, depth.get(), 0.0F);
    }

    @Test
    public void shouldRestorePreviousDepthWhenItemRenderFails() {
        RecordingItemState depth = new RecordingItemState(-12.0F, true);

        try {
            MinecraftItemIconRenderer.runWithGuiItemDepth(depth,
                    () -> { throw new IllegalStateException("render failed"); });
            Assert.fail("异常应继续传播");
        } catch (IllegalStateException expected) {
            Assert.assertEquals("render failed", expected.getMessage());
        }

        Assert.assertEquals(-12.0F, depth.get(), 0.0F);
    }

    /** 普通 items atlas 物品走 2D 等价自绘。 */
    @Test
    public void plainItemIsPlain2DIcon() {
        Assert.assertTrue(MinecraftItemIconRenderer.isPlain2DIcon(new ItemStack(new Item())));
    }

    /** 带物品图标名的 ItemBlock 走 items atlas（spriteNumber 非 0 短路 3D 判定）。 */
    @Test
    public void itemBlockWithItemIconNameStaysPlain2DIcon() {
        ItemStack stack = new ItemStack(new ItemBlock(new TestBlock(Material.rock) {
            @Override
            public String getItemIconName() {
                return "qzuilib_test:block";
            }
        }));
        Assert.assertTrue(MinecraftItemIconRenderer.isPlain2DIcon(stack));
    }

    /** requiresMultipleRenderPasses 的物品委托原版多 pass 分支。 */
    @Test
    public void multiPassItemIsNotPlain2DIcon() {
        Assert.assertFalse(MinecraftItemIconRenderer.isPlain2DIcon(new ItemStack(new MultiPassItem())));
    }

    /** 2D 图标按原版 2D 分支等价序列自绘，并以 :557-559 结束状态收尾。 */
    @Test
    public void plainIconDrawsVanillaEquivalentSequenceAndRestoresState() {
        RecordingGlOps gl = new RecordingGlOps();
        TextureManager textures = new AtlasTextureManager();
        RecordingItemState itemState = new RecordingItemState(77.0F, true);
        RecordingHost host = new RecordingHost(itemState, textures, true);

        new MinecraftItemIconRenderer().render(new ItemStack(new IconItem()), 12, 34, 32, host, gl);

        Assert.assertEquals(java.util.Arrays.asList(
                "pushMatrix",
                "translate(12.0,34.0,0.0)",
                "scale(2.0,2.0,1.0)",
                "disableLighting",
                "enableBlend",
                "blendFuncSeparate(770,771,1,0)",
                "bindTexture(minecraft:textures/atlas/items.png)",
                "color4f(1.0,1.0,1.0,1.0)",
                "enableAlphaTest",
                "drawIconQuad(z=100.0)",
                "enableLighting",
                "disableAlphaTest",
                "disableBlend",
                "matrixModeModelView",
                "popMatrix"), gl.calls);
        Assert.assertEquals("quad 使用 GUI 可见深度", "100.0", gl.quadZLevels.get(0));
        Assert.assertEquals("itemState zLevel 恢复调用前值", 77.0F, itemState.get(), 0.0F);
        Assert.assertEquals("2D 路径不得委托原版", 0, host.delegated.size());
    }

    /** 2D 图标关闭染色时不得修改 GL color。 */
    @Test
    public void plainIconWithoutRenderWithColorDoesNotTouchColor() {
        RecordingGlOps gl = new RecordingGlOps();
        RecordingHost host = new RecordingHost(new RecordingItemState(0.0F, false),
                new AtlasTextureManager(), true);

        new MinecraftItemIconRenderer().render(new ItemStack(new IconItem()), 0, 0, 16, host, gl);

        Assert.assertFalse(gl.calls.contains("color4f(1.0,1.0,1.0,1.0)"));
        Assert.assertTrue(gl.calls.contains("drawIconQuad(z=100.0)"));
    }

    /** 非 2D（3D block / 多 pass）委托持有的原版 RenderItem 实例，并成对启停标准 GUI 光照。 */
    @Test
    public void nonPlainIconDelegatesToVanillaRenderItemAndEffect() {
        RecordingGlOps gl = new RecordingGlOps();
        RecordingItemState itemState = new RecordingItemState(5.0F, true);
        RecordingHost host = new RecordingHost(itemState, new AtlasTextureManager(), true);
        ItemStack stack = new ItemStack(new MultiPassItem());

        new MinecraftItemIconRenderer().render(stack, 8, 9, 16, host, gl);

        Assert.assertEquals(1, host.delegated.size());
        Assert.assertSame(stack, host.delegated.get(0).stack);
        Assert.assertEquals(0, host.delegated.get(0).x);
        Assert.assertEquals(0, host.delegated.get(0).y);
        int lightingEnable = gl.calls.indexOf("enableGuiStandardItemLighting");
        int lightingDisable = gl.calls.indexOf("disableStandardItemLighting");
        Assert.assertTrue(lightingEnable >= 0);
        Assert.assertTrue(lightingDisable > lightingEnable);
        Assert.assertFalse("非 2D 路径不得自绘 quad", gl.calls.contains("drawIconQuad(z=100.0)"));
        Assert.assertEquals("itemState zLevel 恢复调用前值", 5.0F, itemState.get(), 0.0F);
    }

    /** 宿主无法提供原版委托能力时，非 2D 物品跳过绘制且不触碰 GL。 */
    @Test
    public void nonPlainIconWithoutVanillaCapabilitySkipsWithoutGlCalls() {
        RecordingGlOps gl = new RecordingGlOps();
        RecordingHost host = new RecordingHost(new RecordingItemState(0.0F, true),
                new AtlasTextureManager(), false);

        new MinecraftItemIconRenderer().render(new ItemStack(new MultiPassItem()), 8, 9, 16, host, gl);

        Assert.assertEquals(0, host.delegated.size());
        Assert.assertTrue(gl.calls.isEmpty());
    }

    /** 空能力宿主（无 TextureManager）下纯 2D 物品跳过绘制且不崩溃。 */
    @Test
    public void plainIconWithoutTextureManagerSkipsWithoutGlCalls() {
        RecordingGlOps gl = new RecordingGlOps();
        RecordingHost host = new RecordingHost(new RecordingItemState(0.0F, true), null, true);

        new MinecraftItemIconRenderer().render(new ItemStack(new IconItem()), 0, 0, 16, host, gl);

        Assert.assertTrue(gl.calls.isEmpty());
    }

    /** 缺少 items atlas（getTexture 非 TextureMap）时在触碰 GL 前跳过，不崩溃。 */
    @Test
    public void plainIconWithoutAtlasCapabilitySkipsWithoutGlCalls() {
        RecordingGlOps gl = new RecordingGlOps();
        RecordingHost host = new RecordingHost(new RecordingItemState(0.0F, true),
                new TextureManager(null), true);

        new MinecraftItemIconRenderer().render(new ItemStack(new Item()), 0, 0, 16, host, gl);

        Assert.assertTrue(gl.calls.isEmpty());
    }

    /** 无效请求参数直接跳过。 */
    @Test
    public void invalidRequestsAreIgnored() {
        RecordingGlOps gl = new RecordingGlOps();
        RecordingHost host = new RecordingHost(new RecordingItemState(0.0F, true),
                new TextureManager(null), true);
        MinecraftItemIconRenderer renderer = new MinecraftItemIconRenderer();

        renderer.render(null, 0, 0, 16, host, gl);
        renderer.render(new ItemStack(new IconItem()), 0, 0, 0, host, gl);
        renderer.render(new ItemStack(new IconItem()), 0, 0, -4, host, gl);
        renderer.render(new ItemStack(new IconItem()), 0, 0, 16, null, gl);
        renderer.render(new ItemStack(new IconItem()), 0, 0, 16, host, null);

        Assert.assertTrue(gl.calls.isEmpty());
    }

    /** 带自定义图标的物品在 bindTexture 前完成图标解析，染色按 renderWithColor 生效。 */
    @Test
    public void plainIconBindsItemsAtlasBeforeQuad() {
        RecordingGlOps gl = new RecordingGlOps();
        RecordingHost host = new RecordingHost(new RecordingItemState(0.0F, true),
                new AtlasTextureManager(), true);

        new MinecraftItemIconRenderer().render(new ItemStack(new IconItem()), 0, 0, 16, host, gl);

        Assert.assertTrue(gl.calls.indexOf("bindTexture(minecraft:textures/atlas/items.png)")
                < gl.calls.indexOf("drawIconQuad(z=100.0)"));
    }

    /** 测试用 TextureManager：返回真实 items atlas 位置但不触碰任何宿主纹理资源。 */
    private static final class AtlasTextureManager extends TextureManager {
        private AtlasTextureManager() {
            super(null);
        }

        @Override
        public ResourceLocation getResourceLocation(int spriteNumber) {
            return net.minecraft.client.renderer.texture.TextureMap.locationItemsTexture;
        }
    }

    /** 固定 16x16 UV 的测试图标。 */
    private static final class IconItem extends Item {
        @Override
        public IIcon getIconIndex(ItemStack stack) {
            return FakeIcon.INSTANCE;
        }
    }

    /** 多 pass 物品假实现。 */
    private static final class MultiPassItem extends Item {
        @Override
        public boolean requiresMultipleRenderPasses() {
            return true;
        }
    }

    /** 可注入 renderType 的最小 Block 子类。 */
    private static class TestBlock extends Block {
        private TestBlock(Material material) {
            super(material);
        }
    }

    /** 记录调用序列与参数的 GL 操作面。 */
    private static final class RecordingGlOps implements MinecraftItemIconRenderer.ItemGlOps {

        private final List<String> calls = new ArrayList<String>();
        private final List<String> quadZLevels = new ArrayList<String>();

        @Override
        public void pushMatrix() {
            calls.add("pushMatrix");
        }

        @Override
        public void popMatrix() {
            calls.add("popMatrix");
        }

        @Override
        public void matrixModeModelView() {
            calls.add("matrixModeModelView");
        }

        @Override
        public void translate(float x, float y, float z) {
            calls.add(String.format(Locale.ROOT, "translate(%s,%s,%s)", x, y, z));
        }

        @Override
        public void scale(float x, float y, float z) {
            calls.add(String.format(Locale.ROOT, "scale(%s,%s,%s)", x, y, z));
        }

        @Override
        public void disableLighting() {
            calls.add("disableLighting");
        }

        @Override
        public void enableLighting() {
            calls.add("enableLighting");
        }

        @Override
        public void enableBlend() {
            calls.add("enableBlend");
        }

        @Override
        public void disableBlend() {
            calls.add("disableBlend");
        }

        @Override
        public void blendFuncSeparate(int sfactorRGB, int dfactorRGB, int sfactorAlpha, int dfactorAlpha) {
            calls.add(String.format(Locale.ROOT, "blendFuncSeparate(%d,%d,%d,%d)",
                    sfactorRGB, dfactorRGB, sfactorAlpha, dfactorAlpha));
        }

        @Override
        public void bindTexture(TextureManager textureManager, ResourceLocation texture) {
            calls.add("bindTexture(" + texture + ")");
        }

        @Override
        public void enableAlphaTest() {
            calls.add("enableAlphaTest");
        }

        @Override
        public void disableAlphaTest() {
            calls.add("disableAlphaTest");
        }

        @Override
        public void color4f(float red, float green, float blue, float alpha) {
            calls.add(String.format(Locale.ROOT, "color4f(%s,%s,%s,%s)", red, green, blue, alpha));
        }

        @Override
        public void drawIconQuad(IIcon icon, float zLevel) {
            quadZLevels.add(String.format(Locale.ROOT, "%s", zLevel));
            calls.add("drawIconQuad(z=" + zLevel + ")");
        }

        @Override
        public void enableGuiStandardItemLighting() {
            calls.add("enableGuiStandardItemLighting");
        }

        @Override
        public void disableStandardItemLighting() {
            calls.add("disableStandardItemLighting");
        }
    }

    /** 记录委托调用并模拟能力缺失的宿主能力面。 */
    private static final class RecordingHost implements MinecraftItemIconRenderer.ItemRenderHost {

        private final MinecraftItemIconRenderer.ItemState itemState;
        private final TextureManager textureManager;
        private final boolean canRenderItemAndEffect;
        private final List<DelegatedCall> delegated = new ArrayList<DelegatedCall>();

        private RecordingHost(MinecraftItemIconRenderer.ItemState itemState, TextureManager textureManager,
                boolean canRenderItemAndEffect) {
            this.itemState = itemState;
            this.textureManager = textureManager;
            this.canRenderItemAndEffect = canRenderItemAndEffect;
        }

        @Override
        public MinecraftItemIconRenderer.ItemState getItemState() {
            return itemState;
        }

        @Override
        public TextureManager getTextureManager() {
            return textureManager;
        }

        @Override
        public boolean canRenderItemAndEffect() {
            return canRenderItemAndEffect;
        }

        @Override
        public void renderItemAndEffectIntoGUI(ItemStack itemStack, int x, int y) {
            delegated.add(new DelegatedCall(itemStack, x, y));
        }
    }

    /** 原版委托调用记录。 */
    private static final class DelegatedCall {
        private final ItemStack stack;
        private final int x;
        private final int y;

        private DelegatedCall(ItemStack stack, int x, int y) {
            this.stack = stack;
            this.x = x;
            this.y = y;
        }
    }

    /** 纯数值 zLevel / renderWithColor 状态访问缝。 */
    private static final class RecordingItemState implements MinecraftItemIconRenderer.ItemState {
        private float zLevel;
        private final boolean renderWithColor;

        private RecordingItemState(float zLevel, boolean renderWithColor) {
            this.zLevel = zLevel;
            this.renderWithColor = renderWithColor;
        }

        @Override
        public float get() {
            return zLevel;
        }

        @Override
        public void set(float zLevel) {
            this.zLevel = zLevel;
        }

        @Override
        public boolean getRenderWithColor() {
            return renderWithColor;
        }
    }

    /** 固定 16x16 全幅 UV 图标。 */
    private static final class FakeIcon implements IIcon {
        private static final FakeIcon INSTANCE = new FakeIcon();

        @Override
        public int getIconWidth() {
            return 16;
        }

        @Override
        public int getIconHeight() {
            return 16;
        }

        @Override
        public float getMinU() {
            return 0.0F;
        }

        @Override
        public float getMaxU() {
            return 1.0F;
        }

        @Override
        public float getMinV() {
            return 0.0F;
        }

        @Override
        public float getMaxV() {
            return 1.0F;
        }

        @Override
        public float getInterpolatedU(double u) {
            return (float) u;
        }

        @Override
        public float getInterpolatedV(double v) {
            return (float) v;
        }

        @Override
        public String getIconName() {
            return "fake";
        }
    }
}
